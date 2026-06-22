package io.github.summerdez.asmrplayer.domain

import io.github.summerdez.asmrplayer.R
import io.github.summerdez.asmrplayer.data.*
import io.github.summerdez.asmrplayer.data.remote.*
import io.github.summerdez.asmrplayer.data.download.*
import io.github.summerdez.asmrplayer.data.files.*
import io.github.summerdez.asmrplayer.domain.*
import io.github.summerdez.asmrplayer.domain.model.*
import io.github.summerdez.asmrplayer.playback.*
import io.github.summerdez.asmrplayer.presentation.*
import io.github.summerdez.asmrplayer.ui.*
import io.github.summerdez.asmrplayer.ui.activity.*
import io.github.summerdez.asmrplayer.ui.components.*
import io.github.summerdez.asmrplayer.ui.screens.*
import io.github.summerdez.asmrplayer.ui.theme.*
import io.github.summerdez.asmrplayer.ui.util.*
import io.github.summerdez.asmrplayer.di.*
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

    private companion object {
        const val MINUTE_MS = 60_000L
    }
}
