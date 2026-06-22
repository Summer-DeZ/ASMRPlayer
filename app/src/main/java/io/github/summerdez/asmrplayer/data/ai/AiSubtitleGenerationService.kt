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
import io.github.summerdez.asmrplayer.domain.model.SubtitleGenerationTarget
import io.github.summerdez.asmrplayer.domain.model.SubtitleLine
import io.github.summerdez.asmrplayer.domain.model.WhisperModelSpec
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
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
            runGeneration(target)
        }
    }

    private suspend fun runGeneration(target: SubtitleGenerationTarget) {
        try {
            val container = AppGraph.container(this)
            val settings = container.settingsRepository.aiSubtitleSettings()
            val modelSpec = WhisperModelSpec.byId(settings.whisperModelId)
            val segmentCache = AiSubtitleSegmentCache(this)
            val sourceLines = segmentCache.load(target, modelSpec.id)
                ?.also { cachedLines ->
                    AiSubtitleTaskStateBus.publishTranscribing(
                        target = target,
                        progress = 1f,
                        preview = cachedLines.takeLast(TRANSLATION_PREVIEW_SIZE),
                    )
                    updateNotification(target, force = true)
                }
                ?: transcribeAndCache(target, modelSpec, segmentCache)
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
                whisperModelId = modelSpec.id,
                sourceLines = sourceLines,
                translationCache = AiSubtitleTranslationCache(this),
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
        modelSpec: WhisperModelSpec,
        segmentCache: AiSubtitleSegmentCache,
    ): List<SubtitleLine> {
        val modelState = WhisperModelRepository(this).state(modelSpec)
        val transcriber = WhisperRuntimeTranscriber()
        val sourceLines = transcriber.transcribeJapanese(
            context = this,
            target = target,
            modelState = modelState,
        ) { progress, preview ->
            AiSubtitleTaskStateBus.publishTranscribing(target, progress, preview)
            updateNotification(target)
        }
        segmentCache.save(target, modelSpec.id, sourceLines)
        return sourceLines
    }

    private suspend fun translate(
        target: SubtitleGenerationTarget,
        settings: AiSubtitleSettings,
        whisperModelId: String,
        sourceLines: List<SubtitleLine>,
        translationCache: AiSubtitleTranslationCache,
    ): List<SubtitleLine> {
        if (sourceLines.isEmpty()) {
            return emptyList()
        }
        val translatedById = translationCache
            .load(target, whisperModelId, settings, sourceLines)
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
                batch = TranslationBatch(lines = batch, contextTitle = target.contextTitle),
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
            AiSubtitleSegmentCache(this).clear(trackId)
            AiSubtitleTranslationCache(this).clear(trackId)
            AiSubtitleTaskStateBus.remove(trackId)
        }
        clearNotificationState(trackId)
        if (jobs.isEmpty()) {
            stopSelf()
        }
    }

    private fun updateNotification(target: SubtitleGenerationTarget, force: Boolean = false) {
        val state = AiSubtitleTaskStateBus.taskFor(target.trackId) ?: return
        if (!force && shouldSkipNotification(target.trackId, state.stage, state.overallProgress)) {
            return
        }
        rememberNotificationState(target.trackId, state.stage, state.overallProgress)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(AiSubtitleNotifications.NOTIFICATION_ID, AiSubtitleNotifications.build(this, state))
    }

    private fun shouldSkipNotification(trackId: String, stage: AiSubtitleStage, progress: Float): Boolean {
        val progressPercent = progressPercent(progress)
        val now = SystemClock.elapsedRealtime()
        val lastAt = lastNotificationAt[trackId] ?: 0L
        val stageChanged = lastNotificationStage[trackId] != stage
        val progressChanged = lastNotificationProgress[trackId] != progressPercent
        if (stageChanged || isTerminalStage(stage)) {
            return false
        }
        if (!progressChanged && now - lastAt < NOTIFICATION_STALE_INTERVAL_MS) {
            return true
        }
        return now - lastAt < NOTIFICATION_MIN_INTERVAL_MS
    }

    private fun rememberNotificationState(trackId: String, stage: AiSubtitleStage, progress: Float) {
        lastNotificationAt[trackId] = SystemClock.elapsedRealtime()
        lastNotificationProgress[trackId] = progressPercent(progress)
        lastNotificationStage[trackId] = stage
    }

    private fun clearNotificationState(trackId: String) {
        lastNotificationAt.remove(trackId)
        lastNotificationProgress.remove(trackId)
        lastNotificationStage.remove(trackId)
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
        val safeTitle = target.trackTitle
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .trim('-')
            .ifBlank { target.trackId }
            .take(48)
        val file = File(dir, "$safeTitle-$suffix.vtt")
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
        private const val TRANSLATION_BATCH_SIZE = 40
        private const val TRANSLATION_PREVIEW_SIZE = 4
        private const val NOTIFICATION_MIN_INTERVAL_MS = 1_000L
        private const val NOTIFICATION_STALE_INTERVAL_MS = 5_000L

        fun startIntent(context: Context, target: SubtitleGenerationTarget): Intent {
            return Intent(context, AiSubtitleGenerationService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PLAYLIST_ID, target.playlistId)
                .putExtra(EXTRA_TRACK_ID, target.trackId)
                .putExtra(EXTRA_TRACK_TITLE, target.trackTitle)
                .putExtra(EXTRA_AUDIO_URI, target.audioUri)
                .putExtra(EXTRA_CONTEXT_TITLE, target.contextTitle)
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
