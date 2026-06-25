package io.github.summerdez.asmrplayer.data.ai

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import io.github.summerdez.asmrplayer.data.LibraryRepository
import io.github.summerdez.asmrplayer.data.SettingsRepository
import io.github.summerdez.asmrplayer.di.AppGraph
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleSettings
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleStage
import io.github.summerdez.asmrplayer.domain.model.AiTranscriptionBackend
import io.github.summerdez.asmrplayer.domain.model.SubtitleGenerationTarget
import io.github.summerdez.asmrplayer.domain.model.SubtitleLine
import io.github.summerdez.asmrplayer.domain.model.WhisperModelSpec
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AiSubtitleGenerationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val jobsLock = Any()
    private val notificationState = AiSubtitleNotificationState()
    private val translator = OpenAiCompatibleTranslator()
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var libraryRepository: LibraryRepository
    private lateinit var aiSubtitleTaskStateStore: AiSubtitleTaskStateStore

    override fun onCreate() {
        super.onCreate()
        val dependencies = AppGraph.container(this).aiSubtitleGenerationServiceDependencies
        settingsRepository = dependencies.settingsRepository
        libraryRepository = dependencies.libraryRepository
        aiSubtitleTaskStateStore = dependencies.aiSubtitleTaskStateStore
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            AiSubtitleGenerationIntents.ACTION_START -> startGeneration(intent)
            AiSubtitleGenerationIntents.ACTION_CANCEL -> {
                cancelGeneration(AiSubtitleGenerationIntents.trackIdFrom(intent), paused = false)
            }
            AiSubtitleGenerationIntents.ACTION_PAUSE -> {
                cancelGeneration(AiSubtitleGenerationIntents.trackIdFrom(intent), paused = true)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startGeneration(intent: Intent) {
        val target = AiSubtitleGenerationIntents.targetFrom(intent)
        val forceRegenerate = AiSubtitleGenerationIntents.forceRegenerateFrom(intent)
        val forceRetranslate = AiSubtitleGenerationIntents.forceRetranslateFrom(intent)
        if (target.playlistId.isBlank() || target.trackId.isBlank() || target.audioUri.isBlank()) {
            stopSelf()
            return
        }
        synchronized(jobsLock) {
            if (jobs[target.trackId]?.isActive == true) {
                return
            }
            aiSubtitleTaskStateStore.publish(target, AiSubtitleStage.TRANSCRIBING)
            val task = aiSubtitleTaskStateStore.taskFor(target.trackId) ?: return
            startForeground(
                AiSubtitleNotifications.NOTIFICATION_ID,
                AiSubtitleNotifications.build(this, task),
            )
            lateinit var job: Job
            job = scope.launch(start = CoroutineStart.LAZY) {
                runGeneration(target, forceRegenerate, forceRetranslate, job)
            }
            jobs[target.trackId] = job
            job.start()
        }
    }

    private suspend fun runGeneration(
        target: SubtitleGenerationTarget,
        forceRegenerate: Boolean,
        forceRetranslate: Boolean,
        job: Job,
    ) {
        try {
            val settings = settingsRepository.aiSubtitleSettings()
            val modelSpec = WhisperModelSpec.byId(settings.whisperModelId)
            val transcriptionCacheKey = settings.transcriptionCacheKey
            val segmentCache = AiSubtitleSegmentCache(this)
            val translationCache = AiSubtitleTranslationCache(this)
            val sceneContextBuilder = SceneContextBuilder(this, translator)
            aiSubtitleTaskStateStore.publishTranscriptionDetails(
                target = target,
                title = aiSubtitleTranscriptionTitle(settings),
            )
            if (forceRegenerate) {
                clearAiSubtitleCaches(target.trackId, segmentCache, translationCache, sceneContextBuilder)
            } else if (forceRetranslate) {
                // 保留转写缓存，只清翻译与场景上下文，让流程跳过转写、仅重跑翻译。
                translationCache.clear(target.trackId)
                sceneContextBuilder.clear(target.trackId)
            }
            val sourceLines = segmentCache.load(target, transcriptionCacheKey)
                ?.also { cachedLines ->
                    aiSubtitleTaskStateStore.publishTranscribing(
                        target = target,
                        progress = 1f,
                        preview = cachedLines.takeLast(TRANSLATION_PREVIEW_SIZE),
                    )
                    updateNotification(target, force = true)
                }
                ?: transcribeAndCache(target, settings, modelSpec, transcriptionCacheKey, segmentCache)
            val monoFile = writeAiSubtitleFile(target, suffix = "ja", body = AiSubtitleVtt.mono(sourceLines))
            libraryRepository.setTrackSubtitle(
                target.playlistId,
                target.trackId,
                Uri.fromFile(monoFile).toString(),
                monoFile.name,
            )
            val translatedLines = translate(
                target = target,
                settings = settings,
                whisperModelId = transcriptionCacheKey,
                sourceLines = sourceLines,
                translationCache = translationCache,
                sceneContextBuilder = sceneContextBuilder,
            )
            aiSubtitleTaskStateStore.publish(target, AiSubtitleStage.BINDING)
            updateNotification(target)
            val translatedFile = writeAiSubtitleFile(
                target = target,
                suffix = "zh",
                body = AiSubtitleVtt.translated(translatedLines),
            )
            libraryRepository.setTrackSubtitle(
                target.playlistId,
                target.trackId,
                Uri.fromFile(translatedFile).toString(),
                translatedFile.name,
            )
            aiSubtitleTaskStateStore.publishCompleted(
                target = target,
                subtitlePath = translatedFile.absolutePath,
                preview = translatedLines,
                warning = aiSubtitleQualityWarning(translatedLines),
            )
            updateNotification(target, force = true)
        } catch (error: Throwable) {
            if (error is CancellationException) {
                throw error
            }
            aiSubtitleTaskStateStore.publishFailed(target, error.message ?: "AI 字幕生成失败")
            updateNotification(target, force = true)
        } finally {
            synchronized(jobsLock) {
                val removedJob = jobs.remove(target.trackId, job)
                if (removedJob) {
                    notificationState.clear(target.trackId)
                    if (jobs.isEmpty()) {
                        stopForeground(STOP_FOREGROUND_DETACH)
                        stopSelf()
                    }
                }
            }
        }
    }

    private suspend fun transcribeAndCache(
        target: SubtitleGenerationTarget,
        settings: AiSubtitleSettings,
        modelSpec: WhisperModelSpec,
        transcriptionCacheKey: String,
        segmentCache: AiSubtitleSegmentCache,
    ): List<SubtitleLine> {
        val sourceLines = when (settings.transcriptionBackend) {
            AiTranscriptionBackend.LOCAL -> {
                val modelState = WhisperModelRepository(this).state(modelSpec)
                val transcriber = WhisperRuntimeTranscriber()
                transcriber.transcribeJapanese(
                    context = this,
                    target = target,
                    modelState = modelState,
                ) { progress, preview ->
                    aiSubtitleTaskStateStore.publishTranscribing(target, progress, preview)
                    updateNotification(target)
                }
            }
            AiTranscriptionBackend.REMOTE -> {
                val transcriber = RemoteWhisperTranscriber()
                transcriber.transcribeJapanese(
                    context = this,
                    target = target,
                    settings = settings,
                ) { progress, preview, remoteProgress ->
                    aiSubtitleTaskStateStore.publishTranscribing(
                        target = target,
                        progress = progress,
                        preview = preview,
                        processedMs = remoteProgress?.processedMs,
                        durationMs = remoteProgress?.durationMs,
                        detailText = remoteProgress?.detailText.orEmpty(),
                    )
                    updateNotification(target)
                }
            }
        }
        segmentCache.save(target, transcriptionCacheKey, sourceLines)
        return sourceLines
    }

    private suspend fun translate(
        target: SubtitleGenerationTarget,
        settings: AiSubtitleSettings,
        whisperModelId: String,
        sourceLines: List<SubtitleLine>,
        translationCache: AiSubtitleTranslationCache,
        sceneContextBuilder: SceneContextBuilder = SceneContextBuilder(this, translator),
    ): List<SubtitleLine> {
        if (sourceLines.isEmpty()) {
            return emptyList()
        }
        val sceneContext = sceneContextBuilder.loadOrBuild(settings, target, sourceLines)
        val translatedById = translationCache
            .load(
                target = target,
                whisperModelId = whisperModelId,
                settings = settings,
                sourceLines = sourceLines,
                sceneContext = sceneContext,
            )
            .orEmpty()
            .associateBy { it.id }
            .toMutableMap()
        val translated = sourceLines.mapNotNull { translatedById[it.id] }.toMutableList()
        val batches = sourceLines.chunked(TRANSLATION_BATCH_SIZE)
        aiSubtitleTaskStateStore.publishTranslating(
            target = target,
            progress = completedBatchProgress(sourceLines, translatedById, batches),
            preview = if (translated.isEmpty()) {
                sourceLines.takeLast(TRANSLATION_PREVIEW_SIZE)
            } else {
                translated
            },
        )
        updateNotification(target, force = true)
        batches.forEachIndexed { index, batch ->
            if (batch.all { translatedById[it.id]?.translatedText?.isNotBlank() == true }) {
                aiSubtitleTaskStateStore.publishTranslating(
                    target = target,
                    progress = ((index + 1).toFloat() / batches.size),
                    preview = sourceLines.mapNotNull { translatedById[it.id] },
                )
                updateNotification(target)
                return@forEachIndexed
            }
            val result = translator.translate(
                settings = settings,
                batch = buildTranslationBatch(
                    sourceLines = sourceLines,
                    batchLines = batch,
                    batchIndex = index,
                    totalBatches = batches.size,
                    translatedById = translatedById,
                    contextTitle = target.contextTitle,
                    sceneContext = sceneContext,
                    contextWindowSize = TRANSLATION_CONTEXT_WINDOW_SIZE,
                ),
            )
            result.forEach { line ->
                translatedById[line.id] = line
            }
            translated.clear()
            translated += sourceLines.mapNotNull { translatedById[it.id] }
            translationCache.save(
                target = target,
                whisperModelId = whisperModelId,
                settings = settings,
                sourceLines = sourceLines,
                translatedLines = translated,
                sceneContext = sceneContext,
            )
            aiSubtitleTaskStateStore.publishTranslating(
                target = target,
                progress = ((index + 1).toFloat() / batches.size),
                preview = translated,
            )
            updateNotification(target)
        }
        return sourceLines.map { source ->
            val translatedLine = translatedById[source.id]
            if (translatedLine == null || translatedLine.translatedText.isBlank()) {
                throw IOException("翻译结果缺少第 ${source.id} 句，请重试")
            }
            translatedLine
        }
    }

    private fun cancelGeneration(trackId: String, paused: Boolean) {
        val target = aiSubtitleTaskStateStore.taskFor(trackId)?.target ?: return
        synchronized(jobsLock) {
            jobs.remove(trackId)?.cancel()
        }
        if (paused) {
            aiSubtitleTaskStateStore.publishPaused(target)
            updateNotification(target, force = true)
        } else {
            clearAiSubtitleCaches(trackId)
            aiSubtitleTaskStateStore.remove(trackId)
        }
        notificationState.clear(trackId)
        synchronized(jobsLock) {
            if (jobs.isEmpty()) {
                stopSelf()
            }
        }
    }

    private fun updateNotification(target: SubtitleGenerationTarget, force: Boolean = false) {
        val state = aiSubtitleTaskStateStore.taskFor(target.trackId) ?: return
        if (!force && notificationState.shouldSkip(target.trackId, state)) {
            return
        }
        notificationState.remember(target.trackId, state)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(AiSubtitleNotifications.NOTIFICATION_ID, AiSubtitleNotifications.build(this, state))
    }

    private fun clearAiSubtitleCaches(
        trackId: String,
        segmentCache: AiSubtitleSegmentCache = AiSubtitleSegmentCache(this),
        translationCache: AiSubtitleTranslationCache = AiSubtitleTranslationCache(this),
        sceneContextBuilder: SceneContextBuilder = SceneContextBuilder(this, translator),
    ) {
        segmentCache.clear(trackId)
        translationCache.clear(trackId)
        sceneContextBuilder.clear(trackId)
    }

    companion object {
        private const val TRANSLATION_BATCH_SIZE = 80
        private const val TRANSLATION_CONTEXT_WINDOW_SIZE = 4
        private const val TRANSLATION_PREVIEW_SIZE = 4

        fun startIntent(
            context: Context,
            target: SubtitleGenerationTarget,
            forceRegenerate: Boolean = false,
            forceRetranslate: Boolean = false,
        ): Intent {
            return AiSubtitleGenerationIntents.startIntent(context, target, forceRegenerate, forceRetranslate)
        }

        fun pauseIntent(context: Context, trackId: String): Intent {
            return AiSubtitleGenerationIntents.pauseIntent(context, trackId)
        }

        fun cancelIntent(context: Context, trackId: String): Intent {
            return AiSubtitleGenerationIntents.cancelIntent(context, trackId)
        }
    }
}
