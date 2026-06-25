package io.github.summerdez.asmrplayer.data.ai

import java.util.concurrent.TimeUnit

internal data class RemoteProgressSnapshot(
    val status: String,
    val stage: String,
    val progress: Float?,
    val processedMs: Long?,
    val updatedAt: String,
) {
    companion object {
        fun from(status: RemoteTranscriptionStatus): RemoteProgressSnapshot {
            return RemoteProgressSnapshot(
                status = status.normalizedStatus,
                stage = status.normalizedStage,
                progress = status.progress,
                processedMs = status.processedMs,
                updatedAt = status.updatedAt,
            )
        }
    }
}

internal fun remoteStatusProgress(status: RemoteTranscriptionStatus): Float {
    val processed = status.processedMs
    val duration = status.durationMs
    val remoteProgress = if (processed != null && duration != null && duration > 0L) {
        (processed.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        status.progress
    }
    return remoteProgress
        ?.let { (0.35f + it * 0.6f).coerceIn(0.35f, 0.95f) }
        ?: 0.35f
}

internal fun remoteStatusDetail(status: RemoteTranscriptionStatus): String {
    if (status.message.isNotBlank()) {
        return status.message
    }
    if (status.isCompleted) {
        return "转写完成"
    }
    if (status.isFailed) {
        return "转写失败"
    }
    return when (status.normalizedStage) {
        "queued" -> "等待远程转写"
        "asr" -> "正在语音识别"
        "aligning" -> "正在对齐字幕"
        "finalizing" -> "正在写出结果"
        else -> when (status.normalizedStatus) {
            "queued" -> "等待远程转写"
            else -> "正在语音识别"
        }
    }
}

internal fun elapsedMillisSince(startNanoTime: Long): Long {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanoTime)
}
