package io.github.summerdez.asmrplayer.ui.activity

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
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val viewModelFactory by lazy { AppGraph.container(this).viewModelFactory }
    private val mainViewModel: MainViewModel by viewModels { viewModelFactory }
    private val libraryViewModel: LibraryViewModel by viewModels { viewModelFactory }
    private val playbackViewModel: PlaybackViewModel by viewModels { viewModelFactory }
    private val settingsViewModel: SettingsViewModel by viewModels { viewModelFactory }
    private val sleepTimerViewModel: SleepTimerViewModel by viewModels { viewModelFactory }
    private val dlsiteViewModel: DlsiteViewModel by viewModels { viewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppUi.refreshTheme(this)
        AppUi.applySystemBars(this)
        dlsiteViewModel.refresh()
        requestNotificationPermissionIfNeeded()
        setContent {
            ASMRPlayerApp(
                mainViewModel = mainViewModel,
                libraryViewModel = libraryViewModel,
                playbackViewModel = playbackViewModel,
                settingsViewModel = settingsViewModel,
                sleepTimerViewModel = sleepTimerViewModel,
                dlsiteViewModel = dlsiteViewModel,
                onOpenOverlaySettings = ::openOverlaySettings,
                onToggleOverlay = ::toggleOverlay,
                onUnlockOverlay = ::unlockOverlay,
                onRequestNotificationPermission = ::requestNotificationPermissionIfNeeded,
                onApplySystemBars = { AppUi.applySystemBars(this) },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        settingsViewModel.refresh()
    }

    private fun toggleOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }
        settingsViewModel.toggleOverlay()
        playbackViewModel.refresh()
    }

    private fun unlockOverlay() {
        settingsViewModel.unlockOverlay()
        playbackViewModel.refresh()
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            settingsViewModel.refresh()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            settingsViewModel.refresh()
            return
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_NOTIFICATIONS,
        )
    }

    companion object {
        const val TAB_MEDIA = 0
        const val TAB_SETTINGS = 1
        const val TAB_SLEEP = 2
        const val TAB_DLSITE = 3
        private const val REQUEST_NOTIFICATIONS = 103
    }
}
