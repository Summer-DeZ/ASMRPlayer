package io.github.summerdez.asmrplayer.playback

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackServiceSnapshot(
    val connected: Boolean = false,
    val playlistId: String = "",
    val playlistIndex: Int = -1,
    val audioUri: String = "",
    val isPlaying: Boolean = false,
    val durationMs: Int = 0,
    val positionMs: Int = 0,
    val previousSubtitle: String = "",
    val currentSubtitle: String = "",
    val nextSubtitle: String = "",
    val subtitleLines: List<String> = emptyList(),
    val subtitleIndex: Int = -1,
    val subtitleCount: Int = 0,
    val overlayRequested: Boolean = false,
    val overlayLocked: Boolean = false,
    val error: PlaybackError = PlaybackError.None,
    val sleepTimerActive: Boolean = false,
    val sleepTimerAtEndOfTrack: Boolean = false,
    val sleepTimerRemainingMs: Long = 0L,
    val sleepTimerMinutes: Int = 0,
)

sealed interface PlaybackError {
    val message: String

    data object None : PlaybackError {
        override val message: String = ""
    }

    data class Service(override val message: String) : PlaybackError

    companion object {
        fun fromMessage(message: String?): PlaybackError {
            return if (message.isNullOrEmpty()) None else Service(message)
        }
    }
}

object PlaybackServiceState {
    private val _snapshots = MutableStateFlow(PlaybackServiceSnapshot())
    val snapshots: StateFlow<PlaybackServiceSnapshot> = _snapshots.asStateFlow()

    @JvmStatic
    fun publish(
        playlistId: String?,
        playlistIndex: Int,
        audioUri: String?,
        isPlaying: Boolean,
        durationMs: Int,
        positionMs: Int,
        previousSubtitle: String?,
        currentSubtitle: String?,
        nextSubtitle: String?,
        subtitleLines: List<String>?,
        subtitleIndex: Int,
        subtitleCount: Int,
        overlayRequested: Boolean,
        overlayLocked: Boolean,
        lastError: String?,
        sleepTimerActive: Boolean,
        sleepTimerAtEndOfTrack: Boolean,
        sleepTimerRemainingMs: Long,
        sleepTimerMinutes: Int,
    ) {
        _snapshots.value = PlaybackServiceSnapshot(
            connected = true,
            playlistId = playlistId.orEmpty(),
            playlistIndex = playlistIndex,
            audioUri = audioUri.orEmpty(),
            isPlaying = isPlaying,
            durationMs = durationMs,
            positionMs = positionMs,
            previousSubtitle = previousSubtitle.orEmpty(),
            currentSubtitle = currentSubtitle.orEmpty(),
            nextSubtitle = nextSubtitle.orEmpty(),
            subtitleLines = subtitleLines.orEmpty(),
            subtitleIndex = subtitleIndex,
            subtitleCount = subtitleCount,
            overlayRequested = overlayRequested,
            overlayLocked = overlayLocked,
            error = PlaybackError.fromMessage(lastError),
            sleepTimerActive = sleepTimerActive,
            sleepTimerAtEndOfTrack = sleepTimerAtEndOfTrack,
            sleepTimerRemainingMs = sleepTimerRemainingMs,
            sleepTimerMinutes = sleepTimerMinutes,
        )
    }

    @JvmStatic
    fun disconnect() {
        _snapshots.value = PlaybackServiceSnapshot()
    }
}
