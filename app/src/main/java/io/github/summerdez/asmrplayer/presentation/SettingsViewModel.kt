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
import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val overlayPermissionGranted: Boolean = false,
    val overlayRequested: Boolean = false,
    val overlayLocked: Boolean = false,
    val notificationGranted: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
)

class SettingsViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val playbackCommands: PlaybackCommandClient,
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(SettingsUiState(themeMode = settingsRepository.themeMode()))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        playbackCommands.connect()
        viewModelScope.launch {
            PlaybackServiceState.snapshots.collect { snapshot ->
                updateState(snapshot)
            }
        }
    }

    fun refresh() {
        updateState(PlaybackServiceState.snapshots.value)
    }

    fun toggleOverlay() {
        val snapshot = PlaybackServiceState.snapshots.value
        playbackCommands.setOverlayVisible(!snapshot.overlayRequested)
        refresh()
    }

    fun unlockOverlay() {
        playbackCommands.unlockOverlay()
        refresh()
    }

    private fun updateState(snapshot: PlaybackServiceSnapshot) {
        val context = getApplication<Application>()
        _state.value = SettingsUiState(
            overlayPermissionGranted = Settings.canDrawOverlays(context),
            overlayRequested = snapshot.connected && snapshot.overlayRequested,
            overlayLocked = snapshot.connected && snapshot.overlayLocked,
            notificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED,
            themeMode = settingsRepository.themeMode(),
        )
    }

    fun setThemeMode(mode: AppThemeMode) {
        settingsRepository.setThemeMode(mode)
        refresh()
    }
}
