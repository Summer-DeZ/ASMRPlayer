package io.github.summerdez.asmrplayer.presentation

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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SleepTimerUiState(
    val active: Boolean = false,
    val atEndOfTrack: Boolean = false,
    val remainingMs: Long = 0L,
    val minutes: Int = 0,
)

class SleepTimerViewModel(
    private val playbackCommands: PlaybackCommandClient,
) : ViewModel() {
    private val _state = MutableStateFlow(SleepTimerUiState())
    val state: StateFlow<SleepTimerUiState> = _state.asStateFlow()

    init {
        playbackCommands.connect()
        viewModelScope.launch {
            PlaybackServiceState.snapshots.collect { snapshot ->
                _state.value = snapshot.toSleepTimerUiState()
            }
        }
    }

    fun refresh() {
        _state.value = PlaybackServiceState.snapshots.value.toSleepTimerUiState()
    }

    fun setMinutes(minutes: Int): Boolean {
        val scheduled = playbackCommands.setSleepMinutes(minutes)
        refresh()
        return scheduled
    }

    fun setAtEndOfTrack(): Boolean {
        val scheduled = playbackCommands.setSleepAtEndOfTrack()
        refresh()
        return scheduled
    }

    fun cancel() {
        playbackCommands.cancelSleepTimer()
        refresh()
    }

    private fun PlaybackServiceSnapshot.toSleepTimerUiState(): SleepTimerUiState {
        if (!connected || !sleepTimerActive) {
            return SleepTimerUiState()
        }
        if (sleepTimerAtEndOfTrack) {
            return SleepTimerUiState(active = true, atEndOfTrack = true)
        }
        return SleepTimerUiState(
            active = sleepTimerRemainingMs > 0L,
            atEndOfTrack = false,
            remainingMs = sleepTimerRemainingMs,
            minutes = sleepTimerMinutes,
        )
    }
}
