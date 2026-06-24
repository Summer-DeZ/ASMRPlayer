package io.github.summerdez.asmrplayer.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.summerdez.asmrplayer.playback.PlaybackCommandClient
import io.github.summerdez.asmrplayer.playback.PlaybackControllerSnapshot
import io.github.summerdez.asmrplayer.playback.PlaybackServiceSnapshot
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
            playbackCommands.snapshots.collect { snapshot ->
                _state.value = snapshot.toSleepTimerUiState()
            }
        }
    }

    fun refresh() {
        _state.value = playbackCommands.snapshots.value.toSleepTimerUiState()
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

    private fun PlaybackControllerSnapshot.toSleepTimerUiState(): SleepTimerUiState {
        if (!connected) {
            return SleepTimerUiState()
        }
        return serviceSnapshot.toSleepTimerUiState()
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
