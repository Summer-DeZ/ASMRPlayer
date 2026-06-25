package io.github.summerdez.asmrplayer.presentation

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.summerdez.asmrplayer.data.SettingsRepository
import io.github.summerdez.asmrplayer.data.ai.WhisperModelRepository
import io.github.summerdez.asmrplayer.data.ai.WhisperModelState
import io.github.summerdez.asmrplayer.data.update.AppUpdateDownloadStateStore
import io.github.summerdez.asmrplayer.data.update.AppUpdateRelease
import io.github.summerdez.asmrplayer.data.update.AppUpdateRepository
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleSettings
import io.github.summerdez.asmrplayer.domain.model.AiTranscriptionBackend
import io.github.summerdez.asmrplayer.domain.model.AiTranslationEngine
import io.github.summerdez.asmrplayer.domain.model.AppThemeMode
import io.github.summerdez.asmrplayer.domain.model.WhisperModelSpec
import io.github.summerdez.asmrplayer.playback.PlaybackCommandClient
import io.github.summerdez.asmrplayer.playback.PlaybackControllerSnapshot
import io.github.summerdez.asmrplayer.playback.activeServiceSnapshotOrNull
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class SettingsUiState(
    val overlayPermissionGranted: Boolean = false,
    val overlayRequested: Boolean = false,
    val overlayLocked: Boolean = false,
    val notificationGranted: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val currentVersionName: String = "未知",
    val binauralEnhanced: Boolean = true,
    val crossfadeEnabled: Boolean = true,
    val wifiOnlyDownloads: Boolean = true,
    val storageUsedText: String = "计算中",
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
    private val appUpdateDownloadStateStore: AppUpdateDownloadStateStore,
) : AndroidViewModel(application) {
    private val whisperModelRepository = WhisperModelRepository(application)
    private val _state = MutableStateFlow(
        SettingsUiState(
            currentVersionName = application.installedVersionName(),
            whisperModelState = whisperModelRepository.state(WhisperModelSpec.BASE),
        ),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    private val updateHandler = SettingsUpdateHandler(
        context = application,
        updateRepository = updateRepository,
        appUpdateDownloadStateStore = appUpdateDownloadStateStore,
        state = _state,
        events = _events,
        scope = viewModelScope,
    )
    private val aiSettingsHandler = SettingsAiSettingsHandler(
        settingsRepository = settingsRepository,
        state = _state,
        scope = viewModelScope,
    )
    private val remoteWhisperTester = SettingsRemoteWhisperTester(
        state = _state,
        events = _events,
        scope = viewModelScope,
    )
    private val whisperModelActions = SettingsWhisperModelDownloadHandler(
        whisperModelRepository = whisperModelRepository,
        state = _state,
        events = _events,
        scope = viewModelScope,
    )

    init {
        playbackCommands.connect()
        viewModelScope.launch {
            settingsRepository.themeModeFlow.collect { mode ->
                _state.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            settingsRepository.appBehaviorSettingsFlow.collect { settings ->
                _state.update {
                    it.copy(
                        binauralEnhanced = settings.binauralEnhanced,
                        crossfadeEnabled = settings.crossfadeEnabled,
                        wifiOnlyDownloads = settings.wifiOnlyDownloads,
                    )
                }
            }
        }
        viewModelScope.launch {
            playbackCommands.snapshots.collect { snapshot ->
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
            appUpdateDownloadStateStore.state.collect { downloadState ->
                updateHandler.applyDownloadState(downloadState)
            }
        }
        refreshStorageUsage()
    }

    fun refresh() {
        updateState(playbackCommands.snapshots.value)
    }

    fun toggleOverlay() {
        val snapshot = playbackCommands.snapshots.value
        playbackCommands.setOverlayVisible(!currentOverlayRequested(snapshot))
        refresh()
    }

    fun unlockOverlay() {
        playbackCommands.unlockOverlay()
        refresh()
    }

    private fun updateState(snapshot: PlaybackControllerSnapshot) {
        val context = getApplication<Application>()
        val serviceSnapshot = snapshot.activeServiceSnapshotOrNull()
        _state.update { current ->
            current.copy(
                overlayPermissionGranted = Settings.canDrawOverlays(context),
                overlayRequested = serviceSnapshot?.overlayRequested == true,
                overlayLocked = serviceSnapshot?.overlayLocked == true,
                notificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED,
                currentVersionName = context.installedVersionName(),
            )
        }
    }

    private fun currentOverlayRequested(snapshot: PlaybackControllerSnapshot): Boolean {
        return snapshot.activeServiceSnapshotOrNull()?.overlayRequested ?: _state.value.overlayRequested
    }

    fun setThemeMode(mode: AppThemeMode) {
        _state.update { it.copy(themeMode = mode) }
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setBinauralEnhanced(value: Boolean) {
        _state.update { it.copy(binauralEnhanced = value) }
        viewModelScope.launch {
            settingsRepository.setBinauralEnhanced(value)
        }
    }

    fun setCrossfadeEnabled(value: Boolean) {
        _state.update { it.copy(crossfadeEnabled = value) }
        viewModelScope.launch {
            settingsRepository.setCrossfadeEnabled(value)
        }
    }

    fun setWifiOnlyDownloads(value: Boolean) {
        _state.update { it.copy(wifiOnlyDownloads = value) }
        viewModelScope.launch {
            settingsRepository.setWifiOnlyDownloads(value)
        }
    }

    fun refreshStorageUsage(showMessage: Boolean = false) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                appCacheStorageRoots(getApplication()).sumOf { it.sizeBytes() }
            }
            val text = formatStorageBytes(bytes)
            _state.update { it.copy(storageUsedText = text) }
            if (showMessage) {
                _events.emit(SettingsEvent.Message("已重新计算存储：$text"))
            }
        }
    }

    fun checkForUpdates() {
        updateHandler.checkForUpdates()
    }

    fun showUpdateDetails() {
        updateHandler.showUpdateDetails()
    }

    fun dismissUpdateDetails() {
        updateHandler.dismissUpdateDetails()
    }

    fun downloadAvailableUpdate() {
        updateHandler.downloadAvailableUpdate()
    }

    fun cancelUpdateDownload() {
        updateHandler.cancelUpdateDownload()
    }

    fun retryUpdateDownload() {
        updateHandler.retryUpdateDownload()
    }

    fun dismissInstallPrompt() {
        updateHandler.dismissInstallPrompt()
    }

    fun installDownloadedUpdate() {
        updateHandler.installDownloadedUpdate()
    }

    fun setAiTranslationEngine(engine: AiTranslationEngine) {
        aiSettingsHandler.setAiTranslationEngine(engine)
    }

    fun setAiTranscriptionBackend(backend: AiTranscriptionBackend) {
        aiSettingsHandler.setAiTranscriptionBackend(backend)
    }

    fun setAiOllamaBaseUrl(value: String) {
        aiSettingsHandler.setAiOllamaBaseUrl(value)
    }

    fun setAiOllamaModel(value: String) {
        aiSettingsHandler.setAiOllamaModel(value)
    }

    fun setAiDeepSeekBaseUrl(value: String) {
        aiSettingsHandler.setAiDeepSeekBaseUrl(value)
    }

    fun setAiDeepSeekModel(value: String) {
        aiSettingsHandler.setAiDeepSeekModel(value)
    }

    fun setAiDeepSeekApiKey(value: String) {
        aiSettingsHandler.setAiDeepSeekApiKey(value)
    }

    fun setAiWhisperModelId(value: String) {
        aiSettingsHandler.setAiWhisperModelId(value)
    }

    fun setAiRemoteWhisperBaseUrl(value: String) {
        aiSettingsHandler.setAiRemoteWhisperBaseUrl(value)
    }

    fun setAiRemoteTranscriptionAddress(value: String) {
        aiSettingsHandler.setAiRemoteTranscriptionAddress(value)
    }

    fun setAiRemoteTranscriptionPort(value: String) {
        aiSettingsHandler.setAiRemoteTranscriptionPort(value)
    }

    fun setAiRemoteWhisperModel(value: String) {
        aiSettingsHandler.setAiRemoteWhisperModel(value)
    }

    fun setAiRemoteWhisperToken(value: String) {
        aiSettingsHandler.setAiRemoteWhisperToken(value)
    }

    fun testRemoteWhisperConnection() {
        remoteWhisperTester.testConnection()
    }

    fun setAiAdultContentTranslationAllowed(value: Boolean) {
        aiSettingsHandler.setAiAdultContentTranslationAllowed(value)
    }

    fun downloadWhisperModel() {
        whisperModelActions.downloadWhisperModel()
    }

    fun cancelWhisperModelDownload() {
        whisperModelActions.cancelWhisperModelDownload()
    }

    fun deleteWhisperModel() {
        whisperModelActions.deleteWhisperModel()
    }
}

private fun appCacheStorageRoots(context: Application): List<File> {
    return listOf(
        File(context.filesDir, "dlsite"),
        File(context.filesDir, "ai-subtitles"),
        File(context.cacheDir, "updates"),
    )
}

private fun File.sizeBytes(): Long {
    if (!exists()) return 0L
    if (isFile) return length()
    return listFiles().orEmpty().sumOf { it.sizeBytes() }
}

private fun formatStorageBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return if (unitIndex == 0) {
        "${bytes.coerceAtLeast(0L)} ${units[unitIndex]}"
    } else {
        "%.1f %s".format(value, units[unitIndex])
    }
}
