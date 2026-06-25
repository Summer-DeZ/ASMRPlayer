package io.github.summerdez.asmrplayer.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.summerdez.asmrplayer.data.SettingsRepository
import io.github.summerdez.asmrplayer.playback.PlaybackCommandClient
import io.github.summerdez.asmrplayer.playback.PlaybackControllerSnapshot
import io.github.summerdez.asmrplayer.playback.PlaybackServiceSnapshot
import io.github.summerdez.asmrplayer.playback.activeServiceSnapshotOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SleepTimerUiState(
    val active: Boolean = false,
    val atEndOfTrack: Boolean = false,
    val remainingMs: Long = 0L,
    val minutes: Int = 0,
    val fadeBeforeEndEnabled: Boolean = true,
)

class SleepTimerViewModel(
    private val playbackCommands: PlaybackCommandClient,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SleepTimerUiState())
    val state: StateFlow<SleepTimerUiState> = _state.asStateFlow()

    init {
        playbackCommands.connect()
        viewModelScope.launch {
            playbackCommands.snapshots.collect { snapshot ->
                _state.value = snapshot.toSleepTimerUiState(_state.value.fadeBeforeEndEnabled)
            }
        }
        viewModelScope.launch {
            settingsRepository.appBehaviorSettingsFlow.collect { settings ->
                _state.update { it.copy(fadeBeforeEndEnabled = settings.sleepFadeBeforeEndEnabled) }
            }
        }
    }

    fun refresh() {
        _state.value = playbackCommands.snapshots.value.toSleepTimerUiState(_state.value.fadeBeforeEndEnabled)
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

    fun setFadeBeforeEndEnabled(value: Boolean) {
        _state.update { it.copy(fadeBeforeEndEnabled = value) }
        viewModelScope.launch {
            settingsRepository.setSleepFadeBeforeEndEnabled(value)
        }
    }

    private fun PlaybackControllerSnapshot.toSleepTimerUiState(fadeBeforeEndEnabled: Boolean): SleepTimerUiState {
        return activeServiceSnapshotOrNull()?.toSleepTimerUiState(fadeBeforeEndEnabled)
            ?: SleepTimerUiState(fadeBeforeEndEnabled = fadeBeforeEndEnabled)
    }

    private fun PlaybackServiceSnapshot.toSleepTimerUiState(fadeBeforeEndEnabled: Boolean): SleepTimerUiState {
        if (!connected || !sleepTimerActive) {
            return SleepTimerUiState(fadeBeforeEndEnabled = fadeBeforeEndEnabled)
        }
        if (sleepTimerAtEndOfTrack) {
            return SleepTimerUiState(active = true, atEndOfTrack = true, fadeBeforeEndEnabled = fadeBeforeEndEnabled)
        }
        return SleepTimerUiState(
            active = sleepTimerRemainingMs > 0L,
            atEndOfTrack = false,
            remainingMs = sleepTimerRemainingMs,
            minutes = sleepTimerMinutes,
            fadeBeforeEndEnabled = fadeBeforeEndEnabled,
        )
    }
}
