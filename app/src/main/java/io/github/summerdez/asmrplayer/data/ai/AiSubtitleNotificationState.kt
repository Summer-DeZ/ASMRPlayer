package io.github.summerdez.asmrplayer.data.ai

import android.os.SystemClock
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleStage
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleTaskState
import java.util.concurrent.ConcurrentHashMap

internal class AiSubtitleNotificationState {
    private val lastNotificationAt = ConcurrentHashMap<String, Long>()
    private val lastNotificationProgress = ConcurrentHashMap<String, Int>()
    private val lastNotificationProgressSecond = ConcurrentHashMap<String, Long>()
    private val lastNotificationStage = ConcurrentHashMap<String, AiSubtitleStage>()

    fun shouldSkip(trackId: String, state: AiSubtitleTaskState): Boolean {
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

    fun remember(trackId: String, state: AiSubtitleTaskState) {
        lastNotificationAt[trackId] = SystemClock.elapsedRealtime()
        lastNotificationProgress[trackId] = progressPercent(state.overallProgress)
        aiSubtitleNotificationProgressSecond(state)?.let { second ->
            lastNotificationProgressSecond[trackId] = second
        } ?: lastNotificationProgressSecond.remove(trackId)
        lastNotificationStage[trackId] = state.stage
    }

    fun clear(trackId: String) {
        lastNotificationAt.remove(trackId)
        lastNotificationProgress.remove(trackId)
        lastNotificationProgressSecond.remove(trackId)
        lastNotificationStage.remove(trackId)
    }
}

internal fun aiSubtitleNotificationProgressSecond(state: AiSubtitleTaskState): Long? {
    if (state.stage != AiSubtitleStage.TRANSCRIBING) {
        return null
    }
    return state.processedMs?.coerceAtLeast(0L)?.div(1_000L)
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

private const val NOTIFICATION_MIN_INTERVAL_MS = 1_000L
private const val NOTIFICATION_STALE_INTERVAL_MS = 5_000L
