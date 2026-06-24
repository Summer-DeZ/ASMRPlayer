package io.github.summerdez.asmrplayer.presentation

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.summerdez.asmrplayer.data.ai.RemoteWhisperTranscriber
import io.github.summerdez.asmrplayer.data.ai.WhisperModelRepository
import io.github.summerdez.asmrplayer.data.ai.WhisperModelState
import io.github.summerdez.asmrplayer.data.SettingsRepository
import io.github.summerdez.asmrplayer.data.update.AppUpdateCheckResult
import io.github.summerdez.asmrplayer.data.update.AppUpdateDownloadService
import io.github.summerdez.asmrplayer.data.update.AppUpdateDownloadState
import io.github.summerdez.asmrplayer.data.update.AppUpdateDownloadStateBus
import io.github.summerdez.asmrplayer.data.update.AppUpdateDownloadStateStatus
import io.github.summerdez.asmrplayer.data.update.AppUpdateRelease
import io.github.summerdez.asmrplayer.data.update.AppUpdateRepository
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleSettings
import io.github.summerdez.asmrplayer.domain.model.AiTranscriptionBackend
import io.github.summerdez.asmrplayer.domain.model.AiTranslationEngine
import io.github.summerdez.asmrplayer.domain.model.WhisperModelSpec
import io.github.summerdez.asmrplayer.domain.model.remoteTranscriptionBaseUrl
import io.github.summerdez.asmrplayer.playback.PlaybackCommandClient
import io.github.summerdez.asmrplayer.playback.PlaybackServiceSnapshot
import io.github.summerdez.asmrplayer.playback.PlaybackServiceState
import io.github.summerdez.asmrplayer.ui.theme.AppThemeMode
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val overlayPermissionGranted: Boolean = false,
    val overlayRequested: Boolean = false,
    val overlayLocked: Boolean = false,
    val notificationGranted: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val currentVersionName: String = "未知",
    val updateStatus: AppUpdateStatus = AppUpdateStatus.Idle,
    val updateDownloadStatus: AppUpdateDownloadStatus = AppUpdateDownloadStatus.Idle,
    val updateDialogRelease: AppUpdateRelease? = null,
    val installPromptRelease: AppUpdateRelease? = null,
    val aiSubtitleSettings: AiSubtitleSettings = AiSubtitleSettings(),
    val remoteWhisperTestStatus: RemoteWhisperTestStatus = RemoteWhisperTestStatus.Idle,
    val whisperModelState: WhisperModelState = WhisperModelState(
        spec = WhisperModelSpec.BASE,
        downloaded = false,
    ),
)

sealed class RemoteWhisperTestStatus {
    object Idle : RemoteWhisperTestStatus()
    object Checking : RemoteWhisperTestStatus()
    data class Success(val message: String) : RemoteWhisperTestStatus()
    data class Failed(val message: String) : RemoteWhisperTestStatus()
}

sealed class AppUpdateStatus {
    object Idle : AppUpdateStatus()
    object Checking : AppUpdateStatus()
    object UpToDate : AppUpdateStatus()
    data class Available(val release: AppUpdateRelease) : AppUpdateStatus()
    data class Failed(val message: String) : AppUpdateStatus()
}

sealed class AppUpdateDownloadStatus {
    object Idle : AppUpdateDownloadStatus()
    data class Downloading(
        val release: AppUpdateRelease,
        val bytesDownloaded: Long = 0L,
        val totalBytes: Long = release.apkSizeBytes,
        val speedBytesPerSecond: Long = 0L,
    ) : AppUpdateDownloadStatus()

    data class Failed(
        val release: AppUpdateRelease,
        val message: String,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speedBytesPerSecond: Long = 0L,
    ) : AppUpdateDownloadStatus()

    data class Downloaded(
        val release: AppUpdateRelease,
        val apkPath: String,
        val totalBytes: Long,
    ) : AppUpdateDownloadStatus()
}

sealed class SettingsEvent {
    data class Message(val text: String) : SettingsEvent()
    data class InstallUpdate(val apkPath: String) : SettingsEvent()
}

class SettingsViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val playbackCommands: PlaybackCommandClient,
    private val updateRepository: AppUpdateRepository,
) : AndroidViewModel(application) {
    private val whisperModelRepository = WhisperModelRepository(application)
    private val _state = MutableStateFlow(
        SettingsUiState(
            themeMode = settingsRepository.themeMode(),
            currentVersionName = application.installedVersionName(),
            aiSubtitleSettings = settingsRepository.aiSubtitleSettings(),
            whisperModelState = WhisperModelRepository(application)
                .state(WhisperModelSpec.byId(settingsRepository.aiSubtitleSettings().whisperModelId)),
        ),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()
    private var whisperModelDownloadJob: Job? = null
    private val remoteWhisperTranscriber = RemoteWhisperTranscriber()
    private var promptedInstallApkPath: String = ""

    init {
        playbackCommands.connect()
        viewModelScope.launch {
            PlaybackServiceState.snapshots.collect { snapshot ->
                updateState(snapshot)
            }
        }
        viewModelScope.launch {
            settingsRepository.aiSubtitleSettingsFlow.collect { settings ->
                _state.update {
                    it.copy(
                        aiSubtitleSettings = settings,
                        whisperModelState = whisperModelRepository.state(WhisperModelSpec.byId(settings.whisperModelId)),
                    )
                }
            }
        }
        viewModelScope.launch {
            AppUpdateDownloadStateBus.state.collect { downloadState ->
                applyUpdateDownloadState(downloadState)
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
        _state.update { current ->
            current.copy(
                overlayPermissionGranted = Settings.canDrawOverlays(context),
                overlayRequested = snapshot.connected && snapshot.overlayRequested,
                overlayLocked = snapshot.connected && snapshot.overlayLocked,
                notificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED,
                themeMode = settingsRepository.themeMode(),
                currentVersionName = context.installedVersionName(),
            )
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        settingsRepository.setThemeMode(mode)
        refresh()
    }

    fun checkForUpdates() {
        if (_state.value.updateStatus is AppUpdateStatus.Checking) {
            return
        }
        viewModelScope.launch {
            val currentVersionName = getApplication<Application>().installedVersionName()
            _state.update {
                it.copy(
                    updateStatus = AppUpdateStatus.Checking,
                    updateDialogRelease = null,
                )
            }
            try {
                when (val result = updateRepository.checkLatestRelease(currentVersionName)) {
                    is AppUpdateCheckResult.Available -> {
                        _state.update {
                            it.copy(
                                updateStatus = AppUpdateStatus.Available(result.release),
                                updateDialogRelease = result.release,
                            )
                        }
                    }
                    AppUpdateCheckResult.UpToDate -> {
                        _state.update {
                            it.copy(updateStatus = AppUpdateStatus.UpToDate)
                        }
                    }
                }
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    throw error
                }
                _state.update {
                    it.copy(updateStatus = AppUpdateStatus.Failed(error.message ?: "检查更新失败"))
                }
            }
        }
    }

    fun showUpdateDetails() {
        val release = when (val status = _state.value.updateStatus) {
            is AppUpdateStatus.Available -> status.release
            else -> null
        } ?: return
        _state.update { it.copy(updateDialogRelease = release) }
    }

    fun dismissUpdateDetails() {
        _state.update { it.copy(updateDialogRelease = null) }
    }

    fun downloadAvailableUpdate() {
        val release = _state.value.updateDialogRelease
            ?: (_state.value.updateStatus as? AppUpdateStatus.Available)?.release
            ?: (_state.value.updateDownloadStatus as? AppUpdateDownloadStatus.Failed)?.release
            ?: return
        if (_state.value.updateDownloadStatus is AppUpdateDownloadStatus.Downloading) {
            return
        }
        val context = getApplication<Application>()
        _state.update {
            it.copy(
                updateDialogRelease = null,
                updateDownloadStatus = AppUpdateDownloadStatus.Downloading(release),
            )
        }
        AppUpdateDownloadStateBus.publishDownloading(
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
            AppUpdateDownloadStateBus.publishFailed(
                release = release,
                message = error.message ?: "启动更新下载失败",
            )
        }
    }

    fun cancelUpdateDownload() {
        val context = getApplication<Application>()
        try {
            context.startService(AppUpdateDownloadService.cancelIntent(context))
        } catch (_: Throwable) {
            AppUpdateDownloadStateBus.publishCanceled()
            _state.update { it.copy(updateDownloadStatus = AppUpdateDownloadStatus.Idle) }
        }
    }

    fun retryUpdateDownload() {
        if (_state.value.updateDownloadStatus is AppUpdateDownloadStatus.Failed) {
            downloadAvailableUpdate()
        }
    }

    fun dismissInstallPrompt() {
        _state.update { it.copy(installPromptRelease = null) }
    }

    fun installDownloadedUpdate() {
        val downloaded = _state.value.updateDownloadStatus as? AppUpdateDownloadStatus.Downloaded
        if (downloaded == null) {
            viewModelScope.launch {
                _events.emit(SettingsEvent.Message("更新包不存在，请重新下载"))
            }
            return
        }
        _state.update { it.copy(installPromptRelease = null) }
        viewModelScope.launch {
            _events.emit(SettingsEvent.InstallUpdate(downloaded.apkPath))
        }
    }

    fun setAiTranslationEngine(engine: AiTranslationEngine) {
        settingsRepository.setAiTranslationEngine(engine)
    }

    fun setAiTranscriptionBackend(backend: AiTranscriptionBackend) {
        settingsRepository.setAiTranscriptionBackend(backend)
        resetRemoteWhisperTestStatus()
    }

    fun setAiOllamaBaseUrl(value: String) {
        settingsRepository.setAiOllamaBaseUrl(value)
    }

    fun setAiOllamaModel(value: String) {
        settingsRepository.setAiOllamaModel(value)
    }

    fun setAiDeepSeekBaseUrl(value: String) {
        settingsRepository.setAiDeepSeekBaseUrl(value)
    }

    fun setAiDeepSeekModel(value: String) {
        settingsRepository.setAiDeepSeekModel(value)
    }

    fun setAiDeepSeekApiKey(value: String) {
        settingsRepository.setAiDeepSeekApiKey(value)
    }

    fun setAiWhisperModelId(value: String) {
        settingsRepository.setAiWhisperModelId(value)
    }

    fun setAiRemoteWhisperBaseUrl(value: String) {
        settingsRepository.setAiRemoteWhisperBaseUrl(value)
        resetRemoteWhisperTestStatus()
    }

    fun setAiRemoteTranscriptionAddress(value: String) {
        val current = _state.value.aiSubtitleSettings
        settingsRepository.setAiRemoteWhisperBaseUrl(
            remoteTranscriptionBaseUrl(value, current.remoteTranscriptionPort),
        )
        resetRemoteWhisperTestStatus()
    }

    fun setAiRemoteTranscriptionPort(value: String) {
        val current = _state.value.aiSubtitleSettings
        settingsRepository.setAiRemoteWhisperBaseUrl(
            remoteTranscriptionBaseUrl(current.remoteTranscriptionAddress, value),
        )
        resetRemoteWhisperTestStatus()
    }

    fun setAiRemoteWhisperModel(value: String) {
        settingsRepository.setAiRemoteWhisperModel(value)
        resetRemoteWhisperTestStatus()
    }

    fun setAiRemoteWhisperToken(value: String) {
        settingsRepository.setAiRemoteWhisperToken(value)
        resetRemoteWhisperTestStatus()
    }

    fun testRemoteWhisperConnection() {
        if (_state.value.remoteWhisperTestStatus is RemoteWhisperTestStatus.Checking) {
            return
        }
        viewModelScope.launch {
            val settings = _state.value.aiSubtitleSettings
            _state.update { it.copy(remoteWhisperTestStatus = RemoteWhisperTestStatus.Checking) }
            try {
                val health = remoteWhisperTranscriber.checkHealth(settings)
                if (!health.modelsReady) {
                    val message = "服务可达，模型未就绪：${health.displaySummary}"
                    _state.update { it.copy(remoteWhisperTestStatus = RemoteWhisperTestStatus.Failed(message)) }
                    _events.emit(SettingsEvent.Message(message))
                    return@launch
                }
                val message = "OK"
                _state.update { it.copy(remoteWhisperTestStatus = RemoteWhisperTestStatus.Success(message)) }
                _events.emit(SettingsEvent.Message(message))
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    _state.update { it.copy(remoteWhisperTestStatus = RemoteWhisperTestStatus.Idle) }
                    throw error
                }
                val message = error.message ?: "远程转写服务连接失败"
                _state.update { it.copy(remoteWhisperTestStatus = RemoteWhisperTestStatus.Failed(message)) }
                _events.emit(SettingsEvent.Message(message))
            }
        }
    }

    private fun resetRemoteWhisperTestStatus() {
        _state.update { it.copy(remoteWhisperTestStatus = RemoteWhisperTestStatus.Idle) }
    }

    private fun applyUpdateDownloadState(downloadState: AppUpdateDownloadState) {
        val uiStatus = appUpdateDownloadUiStatus(downloadState)
        _state.update { current ->
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

    fun setAiAdultContentTranslationAllowed(value: Boolean) {
        settingsRepository.setAiAdultContentTranslationAllowed(value)
    }

    fun downloadWhisperModel() {
        val spec = WhisperModelSpec.byId(_state.value.aiSubtitleSettings.whisperModelId)
        if (_state.value.whisperModelState.downloading || _state.value.whisperModelState.downloaded) {
            return
        }
        whisperModelDownloadJob?.cancel()
        whisperModelDownloadJob = viewModelScope.launch {
            try {
                _state.update {
                    it.copy(whisperModelState = whisperModelRepository.state(spec).copy(downloading = true))
                }
                val state = whisperModelRepository.download(spec) { progress ->
                    _state.update { it.copy(whisperModelState = progress) }
                }
                _state.update { it.copy(whisperModelState = state) }
                _events.emit(SettingsEvent.Message("${spec.label} 已下载"))
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    _state.update { it.copy(whisperModelState = whisperModelRepository.state(spec)) }
                    return@launch
                }
                _state.update {
                    it.copy(
                        whisperModelState = whisperModelRepository.state(spec).copy(
                            error = error.message ?: "模型下载失败",
                        ),
                    )
                }
            }
        }
    }

    fun cancelWhisperModelDownload() {
        whisperModelDownloadJob?.cancel()
        whisperModelDownloadJob = null
        val spec = WhisperModelSpec.byId(_state.value.aiSubtitleSettings.whisperModelId)
        _state.update { it.copy(whisperModelState = whisperModelRepository.state(spec)) }
    }

    fun deleteWhisperModel() {
        val spec = WhisperModelSpec.byId(_state.value.aiSubtitleSettings.whisperModelId)
        whisperModelRepository.delete(spec)
        _state.update { it.copy(whisperModelState = whisperModelRepository.state(spec)) }
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

private fun Context.installedVersionName(): String {
    val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
    }
    return packageInfo.versionName ?: "未知"
}
