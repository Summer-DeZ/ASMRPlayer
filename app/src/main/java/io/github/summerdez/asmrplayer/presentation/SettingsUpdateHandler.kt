package io.github.summerdez.asmrplayer.presentation

import android.content.Context
import androidx.core.content.ContextCompat
import io.github.summerdez.asmrplayer.data.update.AppUpdateCheckResult
import io.github.summerdez.asmrplayer.data.update.AppUpdateDownloadService
import io.github.summerdez.asmrplayer.data.update.AppUpdateDownloadState
import io.github.summerdez.asmrplayer.data.update.AppUpdateDownloadStateStatus
import io.github.summerdez.asmrplayer.data.update.AppUpdateDownloadStateStore
import io.github.summerdez.asmrplayer.data.update.AppUpdateRepository
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class SettingsUpdateHandler(
    private val context: Context,
    private val updateRepository: AppUpdateRepository,
    private val appUpdateDownloadStateStore: AppUpdateDownloadStateStore,
    private val state: MutableStateFlow<SettingsUiState>,
    private val events: MutableSharedFlow<SettingsEvent>,
    private val scope: CoroutineScope,
) {
    private var promptedInstallApkPath: String = ""

    fun checkForUpdates() {
        if (state.value.updateStatus is AppUpdateStatus.Checking) {
            return
        }
        scope.launch {
            val currentVersionName = context.installedVersionName()
            state.update {
                it.copy(
                    updateStatus = AppUpdateStatus.Checking,
                    updateDialogRelease = null,
                )
            }
            try {
                when (val result = updateRepository.checkLatestRelease(currentVersionName)) {
                    is AppUpdateCheckResult.Available -> {
                        state.update {
                            it.copy(
                                updateStatus = AppUpdateStatus.Available(result.release),
                                updateDialogRelease = result.release,
                            )
                        }
                    }
                    AppUpdateCheckResult.UpToDate -> {
                        state.update {
                            it.copy(updateStatus = AppUpdateStatus.UpToDate)
                        }
                    }
                }
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    throw error
                }
                state.update {
                    it.copy(updateStatus = AppUpdateStatus.Failed(error.message ?: "检查更新失败"))
                }
            }
        }
    }

    fun showUpdateDetails() {
        val release = when (val status = state.value.updateStatus) {
            is AppUpdateStatus.Available -> status.release
            else -> null
        } ?: return
        state.update { it.copy(updateDialogRelease = release) }
    }

    fun dismissUpdateDetails() {
        state.update { it.copy(updateDialogRelease = null) }
    }

    fun downloadAvailableUpdate() {
        val release = state.value.updateDialogRelease
            ?: (state.value.updateStatus as? AppUpdateStatus.Available)?.release
            ?: (state.value.updateDownloadStatus as? AppUpdateDownloadStatus.Failed)?.release
            ?: return
        if (state.value.updateDownloadStatus is AppUpdateDownloadStatus.Downloading) {
            return
        }
        state.update {
            it.copy(
                updateDialogRelease = null,
                updateDownloadStatus = AppUpdateDownloadStatus.Downloading(release),
            )
        }
        appUpdateDownloadStateStore.publishDownloading(
            release = release,
            bytesDownloaded = 0L,
            totalBytes = release.apkSizeBytes,
        )
        try {
            ContextCompat.startForegroundService(
                context,
                AppUpdateDownloadService.downloadIntent(context, release),
            )
        } catch (error: Throwable) {
            appUpdateDownloadStateStore.publishFailed(
                release = release,
                message = error.message ?: "启动更新下载失败",
            )
        }
    }

    fun cancelUpdateDownload() {
        try {
            context.startService(AppUpdateDownloadService.cancelIntent(context))
        } catch (_: Throwable) {
            appUpdateDownloadStateStore.publishCanceled()
            state.update { it.copy(updateDownloadStatus = AppUpdateDownloadStatus.Idle) }
        }
    }

    fun retryUpdateDownload() {
        if (state.value.updateDownloadStatus is AppUpdateDownloadStatus.Failed) {
            downloadAvailableUpdate()
        }
    }

    fun dismissInstallPrompt() {
        state.update { it.copy(installPromptRelease = null) }
    }

    fun installDownloadedUpdate() {
        val downloaded = state.value.updateDownloadStatus as? AppUpdateDownloadStatus.Downloaded
        if (downloaded == null) {
            scope.launch {
                events.emit(SettingsEvent.Message("更新包不存在，请重新下载"))
            }
            return
        }
        state.update { it.copy(installPromptRelease = null) }
        scope.launch {
            events.emit(SettingsEvent.InstallUpdate(downloaded.apkPath))
        }
    }

    fun applyDownloadState(downloadState: AppUpdateDownloadState) {
        val uiStatus = appUpdateDownloadUiStatus(downloadState)
        state.update { current ->
            val installPromptRelease = when {
                uiStatus is AppUpdateDownloadStatus.Downloaded &&
                    uiStatus.apkPath != promptedInstallApkPath -> {
                    promptedInstallApkPath = uiStatus.apkPath
                    uiStatus.release
                }
                uiStatus is AppUpdateDownloadStatus.Downloading -> {
                    promptedInstallApkPath = ""
                    current.installPromptRelease
                }
                else -> current.installPromptRelease
            }
            current.copy(
                updateDownloadStatus = uiStatus,
                installPromptRelease = installPromptRelease,
            )
        }
    }
}

internal fun appUpdateDownloadUiStatus(downloadState: AppUpdateDownloadState): AppUpdateDownloadStatus {
    val release = downloadState.release ?: return AppUpdateDownloadStatus.Idle
    return when (downloadState.status) {
        AppUpdateDownloadStateStatus.DOWNLOADING -> AppUpdateDownloadStatus.Downloading(
            release = release,
            bytesDownloaded = downloadState.bytesDownloaded,
            totalBytes = downloadState.totalBytes,
            speedBytesPerSecond = downloadState.speedBytesPerSecond,
        )
        AppUpdateDownloadStateStatus.DOWNLOADED -> {
            if (downloadState.apkPath.isBlank()) {
                AppUpdateDownloadStatus.Idle
            } else {
                AppUpdateDownloadStatus.Downloaded(
                    release = release,
                    apkPath = downloadState.apkPath,
                    totalBytes = downloadState.totalBytes,
                )
            }
        }
        AppUpdateDownloadStateStatus.FAILED -> AppUpdateDownloadStatus.Failed(
            release = release,
            message = downloadState.error.ifBlank { "下载更新失败" },
            bytesDownloaded = downloadState.bytesDownloaded,
            totalBytes = downloadState.totalBytes,
            speedBytesPerSecond = downloadState.speedBytesPerSecond,
        )
        AppUpdateDownloadStateStatus.IDLE,
        AppUpdateDownloadStateStatus.CANCELED,
        -> AppUpdateDownloadStatus.Idle
    }
}
