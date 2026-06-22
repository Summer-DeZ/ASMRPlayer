package io.github.summerdez.asmrplayer.ui.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import io.github.summerdez.asmrplayer.di.AppGraph
import io.github.summerdez.asmrplayer.presentation.DlsiteViewModel
import io.github.summerdez.asmrplayer.presentation.LibraryViewModel
import io.github.summerdez.asmrplayer.presentation.MainViewModel
import io.github.summerdez.asmrplayer.presentation.PlaybackViewModel
import io.github.summerdez.asmrplayer.presentation.SettingsViewModel
import io.github.summerdez.asmrplayer.presentation.SleepTimerViewModel
import io.github.summerdez.asmrplayer.ui.ASMRPlayerApp
import io.github.summerdez.asmrplayer.ui.theme.AppUi
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModelFactory by lazy { AppGraph.container(this).viewModelFactory }
    private val mainViewModel: MainViewModel by viewModels { viewModelFactory }
    private val libraryViewModel: LibraryViewModel by viewModels { viewModelFactory }
    private val playbackViewModel: PlaybackViewModel by viewModels { viewModelFactory }
    private val settingsViewModel: SettingsViewModel by viewModels { viewModelFactory }
    private val sleepTimerViewModel: SleepTimerViewModel by viewModels { viewModelFactory }
    private val dlsiteViewModel: DlsiteViewModel by viewModels { viewModelFactory }
    private var pendingInstallApkPath: String? = null

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
                onInstallUpdate = ::installUpdateApk,
                onApplySystemBars = { AppUi.applySystemBars(this) },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        settingsViewModel.refresh()
        val pendingPath = pendingInstallApkPath
        if (pendingPath != null && canRequestPackageInstalls()) {
            pendingInstallApkPath = null
            installUpdateApk(pendingPath)
        }
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

    private fun installUpdateApk(path: String) {
        val apkFile = File(path)
        if (!apkFile.exists()) {
            Toast.makeText(this, "更新包不存在，请重新下载", Toast.LENGTH_SHORT).show()
            return
        }
        if (!canRequestPackageInstalls()) {
            pendingInstallApkPath = path
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName"),
                    ),
                )
                Toast.makeText(this, "请允许安装未知应用后返回继续安装", Toast.LENGTH_LONG).show()
            }
            return
        }

        val apkUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(apkUri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(installIntent)
    }

    private fun canRequestPackageInstalls(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()
    }

    companion object {
        const val TAB_MEDIA = 0
        const val TAB_SETTINGS = 1
        const val TAB_SLEEP = 2
        const val TAB_DLSITE = 3
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val REQUEST_NOTIFICATIONS = 103
    }
}
