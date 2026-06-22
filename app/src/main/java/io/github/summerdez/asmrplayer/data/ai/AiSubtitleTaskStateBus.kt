package io.github.summerdez.asmrplayer.data.ai

import io.github.summerdez.asmrplayer.domain.model.AiSubtitleStage
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleTaskState
import io.github.summerdez.asmrplayer.domain.model.SubtitleGenerationTarget
import io.github.summerdez.asmrplayer.domain.model.SubtitleLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object AiSubtitleTaskStateBus {
    private val _tasks = MutableStateFlow<Map<String, AiSubtitleTaskState>>(emptyMap())
    val tasks: StateFlow<Map<String, AiSubtitleTaskState>> = _tasks.asStateFlow()

    fun publish(target: SubtitleGenerationTarget, stage: AiSubtitleStage) {
        publish(
            taskFor(target.trackId) ?: AiSubtitleTaskState(target = target),
        ) { it.copy(stage = stage, error = "") }
    }

    fun publishTranscribing(target: SubtitleGenerationTarget, progress: Float, preview: List<SubtitleLine>) {
        publish(
            taskFor(target.trackId) ?: AiSubtitleTaskState(target = target),
        ) {
            it.copy(
                stage = AiSubtitleStage.TRANSCRIBING,
                transcribeProgress = progress.coerceIn(0f, 1f),
                previewLines = preview.takeLast(PREVIEW_LIMIT),
                error = "",
            )
        }
    }

    fun publishTranslating(target: SubtitleGenerationTarget, progress: Float, preview: List<SubtitleLine>) {
        publish(
            taskFor(target.trackId) ?: AiSubtitleTaskState(target = target),
        ) {
            it.copy(
                stage = AiSubtitleStage.TRANSLATING,
                translateProgress = progress.coerceIn(0f, 1f),
                previewLines = preview.takeLast(PREVIEW_LIMIT),
                error = "",
            )
        }
    }

    fun publishCompleted(target: SubtitleGenerationTarget, subtitlePath: String, preview: List<SubtitleLine>) {
        publish(
            taskFor(target.trackId) ?: AiSubtitleTaskState(target = target),
        ) {
            it.copy(
                stage = AiSubtitleStage.COMPLETED,
                transcribeProgress = 1f,
                translateProgress = 1f,
                subtitlePath = subtitlePath,
                previewLines = preview.takeLast(PREVIEW_LIMIT),
                error = "",
            )
        }
    }

    fun publishFailed(target: SubtitleGenerationTarget, message: String) {
        publish(
            taskFor(target.trackId) ?: AiSubtitleTaskState(target = target),
        ) {
            it.copy(stage = AiSubtitleStage.FAILED, error = message)
        }
    }

    fun publishBackendNotice(target: SubtitleGenerationTarget, message: String) {
        if (message.isBlank()) {
            return
        }
        publish(
            taskFor(target.trackId) ?: AiSubtitleTaskState(target = target),
        ) {
            it.copy(backendNotice = message)
        }
    }

    fun publishPaused(target: SubtitleGenerationTarget) {
        publish(
            taskFor(target.trackId) ?: AiSubtitleTaskState(target = target),
        ) {
            it.copy(stage = AiSubtitleStage.PAUSED)
        }
    }

    fun remove(trackId: String) {
        _tasks.update { it - trackId }
    }

    fun taskFor(trackId: String): AiSubtitleTaskState? = _tasks.value[trackId]

    private fun publish(
        current: AiSubtitleTaskState,
        update: (AiSubtitleTaskState) -> AiSubtitleTaskState,
    ) {
        val updated = update(current)
        _tasks.update { tasks -> tasks + (updated.target.trackId to updated) }
    }

    private const val PREVIEW_LIMIT = 4
}
