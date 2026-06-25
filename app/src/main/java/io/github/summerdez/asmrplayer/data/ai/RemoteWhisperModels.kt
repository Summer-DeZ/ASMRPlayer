package io.github.summerdez.asmrplayer.data.ai

import io.github.summerdez.asmrplayer.domain.model.SubtitleLine

data class RemoteWhisperHealth(
    val status: String,
    val service: String,
    val version: String,
    val device: String,
    val defaultModel: String,
    val modelsReady: Boolean,
) {
    val displaySummary: String
        get() = listOf(
            status.ifBlank { "unknown" },
            device.ifBlank { "auto" },
            defaultModel.ifBlank { "default" },
        ).joinToString(" · ")
}

data class RemoteTranscriptionJob(
    val jobId: String,
)

data class RemoteTranscriptionStatus(
    val status: String,
    val stage: String,
    val processedMs: Long?,
    val durationMs: Long?,
    val progress: Float?,
    val message: String,
    val updatedAt: String,
    val previewLines: List<SubtitleLine> = emptyList(),
) {
    val normalizedStatus: String
        get() = status.trim().lowercase()

    val normalizedStage: String
        get() = stage.trim().lowercase()

    val isCompleted: Boolean
        get() = normalizedStatus in COMPLETED_REMOTE_STATUSES ||
            normalizedStage in COMPLETED_REMOTE_STATUSES

    val isFailed: Boolean
        get() = normalizedStatus in FAILED_REMOTE_STATUSES ||
            normalizedStage in FAILED_REMOTE_STATUSES
}

data class RemoteTranscriptionProgress(
    val processedMs: Long? = null,
    val durationMs: Long? = null,
    val detailText: String = "",
)

private val COMPLETED_REMOTE_STATUSES = setOf("succeeded", "done")
private val FAILED_REMOTE_STATUSES = setOf("failed", "error", "canceled", "cancelled")
