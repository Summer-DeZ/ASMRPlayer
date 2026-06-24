package io.github.summerdez.asmrplayer.data.ai

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.os.SystemClock
import io.github.summerdez.asmrplayer.di.AppGraph
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleSettings
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleStage
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleTaskState
import io.github.summerdez.asmrplayer.domain.model.AiTranscriptionBackend
import io.github.summerdez.asmrplayer.domain.model.SubtitleGenerationTarget
import io.github.summerdez.asmrplayer.domain.model.SubtitleLine
import io.github.summerdez.asmrplayer.domain.model.WhisperModelSpec
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiSubtitleGenerationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    private val lastNotificationAt = ConcurrentHashMap<String, Long>()
    private val lastNotificationProgress = ConcurrentHashMap<String, Int>()
    private val lastNotificationProgressSecond = ConcurrentHashMap<String, Long>()
    private val lastNotificationStage = ConcurrentHashMap<String, AiSubtitleStage>()
    private val translator = OpenAiCompatibleTranslator()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startGeneration(intent)
            ACTION_CANCEL -> cancelGeneration(intent.getStringExtra(EXTRA_TRACK_ID).orEmpty(), paused = false)
            ACTION_PAUSE -> cancelGeneration(intent.getStringExtra(EXTRA_TRACK_ID).orEmpty(), paused = true)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startGeneration(intent: Intent) {
        val target = SubtitleGenerationTarget(
            playlistId = intent.getStringExtra(EXTRA_PLAYLIST_ID).orEmpty(),
            trackId = intent.getStringExtra(EXTRA_TRACK_ID).orEmpty(),
            trackTitle = intent.getStringExtra(EXTRA_TRACK_TITLE).orEmpty(),
            audioUri = intent.getStringExtra(EXTRA_AUDIO_URI).orEmpty(),
            contextTitle = intent.getStringExtra(EXTRA_CONTEXT_TITLE).orEmpty(),
        )
        val forceRegenerate = intent.getBooleanExtra(EXTRA_FORCE_REGENERATE, false)
        if (target.playlistId.isBlank() || target.trackId.isBlank() || target.audioUri.isBlank()) {
            stopSelf()
            return
        }
        if (jobs[target.trackId]?.isActive == true) {
            return
        }
        AiSubtitleTaskStateBus.publish(target, AiSubtitleStage.TRANSCRIBING)
        startForeground(
            AiSubtitleNotifications.NOTIFICATION_ID,
            AiSubtitleNotifications.build(this, AiSubtitleTaskStateBus.taskFor(target.trackId)!!),
        )
        jobs[target.trackId] = scope.launch {
            runGeneration(target, forceRegenerate)
        }
    }

    private suspend fun runGeneration(target: SubtitleGenerationTarget, forceRegenerate: Boolean) {
        try {
            val container = AppGraph.container(this)
            val settings = container.settingsRepository.aiSubtitleSettings()
            val modelSpec = WhisperModelSpec.byId(settings.whisperModelId)
            val transcriptionCacheKey = settings.transcriptionCacheKey
            val segmentCache = AiSubtitleSegmentCache(this)
            val translationCache = AiSubtitleTranslationCache(this)
            val sceneContextBuilder = SceneContextBuilder(this, translator)
            AiSubtitleTaskStateBus.publishTranscriptionDetails(
                target = target,
                title = transcriptionTitle(settings),
            )
            if (forceRegenerate) {
                clearAiSubtitleCaches(target.trackId, segmentCache, translationCache, sceneContextBuilder)
            }
            val sourceLines = segmentCache.load(target, transcriptionCacheKey)
                ?.also { cachedLines ->
                    AiSubtitleTaskStateBus.publishTranscribing(
                        target = target,
                        progress = 1f,
                        preview = cachedLines.takeLast(TRANSLATION_PREVIEW_SIZE),
                    )
                    updateNotification(target, force = true)
                }
                ?: transcribeAndCache(target, settings, modelSpec, transcriptionCacheKey, segmentCache)
            val monoFile = writeSubtitleFile(target, suffix = "ja", body = AiSubtitleVtt.mono(sourceLines))
            withContext(Dispatchers.Main) {
                container.libraryRepository.setTrackSubtitle(
                    target.playlistId,
                    target.trackId,
                    Uri.fromFile(monoFile).toString(),
                    monoFile.name,
                )
            }
            val translatedLines = translate(
                target = target,
                settings = settings,
                whisperModelId = transcriptionCacheKey,
                sourceLines = sourceLines,
                translationCache = translationCache,
                sceneContextBuilder = sceneContextBuilder,
            )
            AiSubtitleTaskStateBus.publish(target, AiSubtitleStage.BINDING)
            updateNotification(target)
            val translatedFile = writeSubtitleFile(
                target = target,
                suffix = "zh",
                body = AiSubtitleVtt.translated(translatedLines),
            )
            withContext(Dispatchers.Main) {
                container.libraryRepository.setTrackSubtitle(
                    target.playlistId,
                    target.trackId,
                    Uri.fromFile(translatedFile).toString(),
                    translatedFile.name,
                )
            }
            AiSubtitleTaskStateBus.publishCompleted(target, translatedFile.absolutePath, translatedLines)
            updateNotification(target, force = true)
        } catch (error: Throwable) {
            if (error is CancellationException) {
                throw error
            }
            AiSubtitleTaskStateBus.publishFailed(target, error.message ?: "AI 字幕生成失败")
            updateNotification(target, force = true)
        } finally {
            jobs.remove(target.trackId)
            clearNotificationState(target.trackId)
            if (jobs.isEmpty()) {
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
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
                    AiSubtitleTaskStateBus.publishTranscribing(target, progress, preview)
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
                    AiSubtitleTaskStateBus.publishTranscribing(
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

    private fun transcriptionTitle(settings: AiSubtitleSettings): String {
        return when (settings.transcriptionBackend) {
            AiTranscriptionBackend.LOCAL -> "本机转写"
            AiTranscriptionBackend.REMOTE -> "远程转写"
        }
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
        AiSubtitleTaskStateBus.publishTranslating(
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
                AiSubtitleTaskStateBus.publishTranslating(
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
            AiSubtitleTaskStateBus.publishTranslating(
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
        val target = AiSubtitleTaskStateBus.taskFor(trackId)?.target ?: return
        jobs.remove(trackId)?.cancel()
        if (paused) {
            AiSubtitleTaskStateBus.publishPaused(target)
            updateNotification(target, force = true)
        } else {
            clearAiSubtitleCaches(trackId)
            AiSubtitleTaskStateBus.remove(trackId)
        }
        clearNotificationState(trackId)
        if (jobs.isEmpty()) {
            stopSelf()
        }
    }

    private fun updateNotification(target: SubtitleGenerationTarget, force: Boolean = false) {
        val state = AiSubtitleTaskStateBus.taskFor(target.trackId) ?: return
        if (!force && shouldSkipNotification(target.trackId, state)) {
            return
        }
        rememberNotificationState(target.trackId, state)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(AiSubtitleNotifications.NOTIFICATION_ID, AiSubtitleNotifications.build(this, state))
    }

    private fun shouldSkipNotification(trackId: String, state: AiSubtitleTaskState): Boolean {
        val progressPercent = progressPercent(state.overallProgress)
        val progressSecond = aiSubtitleNotificationProgressSecond(state)
        val now = SystemClock.elapsedRealtime()
        val lastAt = lastNotificationAt[trackId] ?: 0L
        val stageChanged = lastNotificationStage[trackId] != state.stage
        val progressChanged = lastNotificationProgress[trackId] != progressPercent ||
            lastNotificationProgressSecond[trackId] != progressSecond
        if (stageChanged || isTerminalStage(state.stage)) {
            return false
        }
        if (!progressChanged && now - lastAt < NOTIFICATION_STALE_INTERVAL_MS) {
            return true
        }
        return now - lastAt < NOTIFICATION_MIN_INTERVAL_MS
    }

    private fun rememberNotificationState(trackId: String, state: AiSubtitleTaskState) {
        lastNotificationAt[trackId] = SystemClock.elapsedRealtime()
        lastNotificationProgress[trackId] = progressPercent(state.overallProgress)
        aiSubtitleNotificationProgressSecond(state)?.let { second ->
            lastNotificationProgressSecond[trackId] = second
        } ?: lastNotificationProgressSecond.remove(trackId)
        lastNotificationStage[trackId] = state.stage
    }

    private fun clearNotificationState(trackId: String) {
        lastNotificationAt.remove(trackId)
        lastNotificationProgress.remove(trackId)
        lastNotificationProgressSecond.remove(trackId)
        lastNotificationStage.remove(trackId)
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

    private fun progressPercent(progress: Float): Int {
        return (progress * 100).toInt().coerceIn(0, 100)
    }

    private fun isTerminalStage(stage: AiSubtitleStage): Boolean {
        return stage == AiSubtitleStage.COMPLETED ||
            stage == AiSubtitleStage.FAILED ||
            stage == AiSubtitleStage.PAUSED ||
            stage == AiSubtitleStage.CANCELED
    }

    private fun completedBatchProgress(
        sourceLines: List<SubtitleLine>,
        translatedById: Map<String, SubtitleLine>,
        batches: List<List<SubtitleLine>>,
    ): Float {
        if (sourceLines.isEmpty() || batches.isEmpty()) {
            return 0f
        }
        val completedBatches = batches.count { batch ->
            batch.all { translatedById[it.id]?.translatedText?.isNotBlank() == true }
        }
        return (completedBatches.toFloat() / batches.size).coerceIn(0f, 1f)
    }

    private fun writeSubtitleFile(target: SubtitleGenerationTarget, suffix: String, body: String): File {
        val dir = File(filesDir, "ai-subtitles/generated").apply { mkdirs() }
        val file = File(dir, generatedSubtitleFileName(target, suffix))
        file.writeText(body, Charsets.UTF_8)
        return file
    }

    companion object {
        private const val ACTION_START = "io.github.summerdez.asmrplayer.ai.START"
        private const val ACTION_PAUSE = "io.github.summerdez.asmrplayer.ai.PAUSE"
        private const val ACTION_CANCEL = "io.github.summerdez.asmrplayer.ai.CANCEL"
        private const val EXTRA_PLAYLIST_ID = "playlistId"
        private const val EXTRA_TRACK_ID = "trackId"
        private const val EXTRA_TRACK_TITLE = "trackTitle"
        private const val EXTRA_AUDIO_URI = "audioUri"
        private const val EXTRA_CONTEXT_TITLE = "contextTitle"
        private const val EXTRA_FORCE_REGENERATE = "forceRegenerate"
        private const val TRANSLATION_BATCH_SIZE = 80
        private const val TRANSLATION_CONTEXT_WINDOW_SIZE = 4
        private const val TRANSLATION_PREVIEW_SIZE = 4
        private const val NOTIFICATION_MIN_INTERVAL_MS = 1_000L
        private const val NOTIFICATION_STALE_INTERVAL_MS = 5_000L

        fun startIntent(
            context: Context,
            target: SubtitleGenerationTarget,
            forceRegenerate: Boolean = false,
        ): Intent {
            return Intent(context, AiSubtitleGenerationService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PLAYLIST_ID, target.playlistId)
                .putExtra(EXTRA_TRACK_ID, target.trackId)
                .putExtra(EXTRA_TRACK_TITLE, target.trackTitle)
                .putExtra(EXTRA_AUDIO_URI, target.audioUri)
                .putExtra(EXTRA_CONTEXT_TITLE, target.contextTitle)
                .putExtra(EXTRA_FORCE_REGENERATE, forceRegenerate)
        }

        fun pauseIntent(context: Context, trackId: String): Intent {
            return Intent(context, AiSubtitleGenerationService::class.java)
                .setAction(ACTION_PAUSE)
                .putExtra(EXTRA_TRACK_ID, trackId)
        }

        fun cancelIntent(context: Context, trackId: String): Intent {
            return Intent(context, AiSubtitleGenerationService::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_TRACK_ID, trackId)
        }
    }
}

internal fun generatedSubtitleFileName(target: SubtitleGenerationTarget, suffix: String): String {
    val safeTrackId = subtitleFileSegment(target.trackId)
        .ifBlank { "track" }
        .take(64)
    val safeTitle = subtitleFileSegment(target.trackTitle)
        .ifBlank { "audio" }
        .take(48)
    val safeSuffix = subtitleFileSegment(suffix)
        .ifBlank { "subtitle" }
        .take(16)
    return "$safeTrackId-$safeTitle-$safeSuffix.vtt"
}

internal fun aiSubtitleNotificationProgressSecond(state: AiSubtitleTaskState): Long? {
    if (state.stage != AiSubtitleStage.TRANSCRIBING) {
        return null
    }
    return state.processedMs?.coerceAtLeast(0L)?.div(1_000L)
}

private fun subtitleFileSegment(value: String): String {
    return value
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9._-]+"), "-")
        .trim('-', '.', '_')
}

internal fun buildTranslationBatch(
    sourceLines: List<SubtitleLine>,
    batchLines: List<SubtitleLine>,
    batchIndex: Int,
    totalBatches: Int,
    translatedById: Map<String, SubtitleLine>,
    contextTitle: String,
    sceneContext: SceneContext,
    contextWindowSize: Int,
): TranslationBatch {
    val batchStart = sourceLines.indexOfFirst { it.id == batchLines.firstOrNull()?.id }
        .takeIf { it >= 0 }
        ?: 0
    val batchEndExclusive = (batchStart + batchLines.size).coerceAtMost(sourceLines.size)
    val previousStart = max(0, batchStart - contextWindowSize)
    val nextEnd = min(sourceLines.size, batchEndExclusive + contextWindowSize)
    val previousContext = sourceLines
        .subList(previousStart, batchStart)
        .map { line ->
            TranslationContextLine(
                id = line.id,
                ja = line.sourceText,
                zh = translatedById[line.id]?.translatedText.orEmpty(),
            )
        }
    val nextContext = sourceLines
        .subList(batchEndExclusive, nextEnd)
        .map { line -> TranslationContextLine(id = line.id, ja = line.sourceText) }
    val globalSourceContext = sourceLines
        .map { line -> TranslationContextLine(id = line.id, ja = line.sourceText) }
    return TranslationBatch(
        lines = batchLines,
        contextTitle = contextTitle,
        sceneContext = sceneContext,
        globalSourceContext = globalSourceContext,
        previousContext = previousContext,
        nextContext = nextContext,
        batchIndex = batchIndex,
        totalBatches = totalBatches,
    )
}
