package io.github.summerdez.asmrplayer.domain

import kotlin.math.max

class SleepTimerState {
    private var endMs: Long = 0L
    private var minutes: Int = 0
    private var atEndOfTrack: Boolean = false

    fun setMinutes(requestedMinutes: Int, nowMs: Long): Long {
        minutes = max(1, requestedMinutes)
        atEndOfTrack = false
        val durationMs = minutes * MINUTE_MS
        endMs = nowMs + durationMs
        return durationMs
    }

    fun setAtEndOfTrack() {
        endMs = 0L
        minutes = 0
        atEndOfTrack = true
    }

    fun cancel() {
        endMs = 0L
        minutes = 0
        atEndOfTrack = false
    }

    fun consumeAtEndOfTrack(): Boolean {
        if (!atEndOfTrack) {
            return false
        }
        cancel()
        return true
    }

    fun remainingMs(nowMs: Long): Long {
        if (endMs <= 0L) {
            return 0L
        }
        return max(0L, endMs - nowMs)
    }

    fun minutes(): Int = minutes

    fun isAtEndOfTrack(): Boolean = atEndOfTrack

    fun endElapsedRealtimeMs(): Long = endMs

    private companion object {
        const val MINUTE_MS = 60_000L
    }
}
