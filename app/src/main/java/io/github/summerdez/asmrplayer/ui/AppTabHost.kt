package io.github.summerdez.asmrplayer.ui

import androidx.compose.runtime.Composable
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleTaskState
import io.github.summerdez.asmrplayer.domain.model.AiTranscriptionBackend
import io.github.summerdez.asmrplayer.domain.model.AiTranslationEngine
import io.github.summerdez.asmrplayer.domain.model.AppThemeMode
import io.github.summerdez.asmrplayer.domain.model.DlsiteContent
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import io.github.summerdez.asmrplayer.domain.model.Playlist
import io.github.summerdez.asmrplayer.domain.model.TrackItem
import io.github.summerdez.asmrplayer.presentation.DlsiteUiState
import io.github.summerdez.asmrplayer.presentation.LibraryUiState
import io.github.summerdez.asmrplayer.presentation.MainTab
import io.github.summerdez.asmrplayer.presentation.PlaybackUiState
import io.github.summerdez.asmrplayer.presentation.SettingsUiState
import io.github.summerdez.asmrplayer.presentation.SleepTimerUiState
import io.github.summerdez.asmrplayer.ui.screens.DlsiteTab
import io.github.summerdez.asmrplayer.ui.screens.LibraryTab
import io.github.summerdez.asmrplayer.ui.screens.SettingsTab
import io.github.summerdez.asmrplayer.ui.screens.SleepTab

@Composable
fun AppTabHost(
    selectedTab: MainTab,
    libraryState: LibraryUiState,
    playbackState: PlaybackUiState,
    settingsState: SettingsUiState,
    sleepState: SleepTimerUiState,
    dlsiteState: DlsiteUiState,
    aiSubtitleTasks: Map<String, AiSubtitleTaskState>,
    onPlaylistClicked: (Playlist) -> Unit,
    onCoverClicked: (Playlist) -> Unit,
    onRenamePlaylist: (Playlist) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    onTrackClicked: (Playlist, Int) -> Unit,
    onTrackSubtitleClicked: (Playlist, TrackItem) -> Unit,
    onGenerateSubtitle: (Playlist, TrackItem) -> Unit,
    onOpenSubtitleGeneration: (String) -> Unit,
    onRenameTrack: (Playlist, TrackItem) -> Unit,
    onDeleteTrack: (Playlist, TrackItem, Int) -> Unit,
    onMoveTrack: (Playlist, TrackItem) -> Unit,
    onToggleOverlay: () -> Unit,
    onUnlockOverlay: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onBinauralEnhancedChange: (Boolean) -> Unit,
    onCrossfadeEnabledChange: (Boolean) -> Unit,
    onWifiOnlyDownloadsChange: (Boolean) -> Unit,
    onRefreshStorageUsage: () -> Unit,
    onThemeSelected: (AppThemeMode) -> Unit,
    onCheckForUpdates: () -> Unit,
    onShowUpdateDetails: () -> Unit,
    onCancelUpdateDownload: () -> Unit,
    onRetryUpdateDownload: () -> Unit,
    onAiTranscriptionBackendSelected: (AiTranscriptionBackend) -> Unit,
    onAiEngineSelected: (AiTranslationEngine) -> Unit,
    onEditAiOllamaBaseUrl: () -> Unit,
    onEditAiOllamaModel: () -> Unit,
    onEditAiDeepSeekBaseUrl: () -> Unit,
    onEditAiDeepSeekModel: () -> Unit,
    onEditAiDeepSeekApiKey: () -> Unit,
    onAiAdultContentTranslationAllowedChange: (Boolean) -> Unit,
    onAiWhisperModelSelected: (String) -> Unit,
    onEditRemoteTranscriptionAddress: () -> Unit,
    onEditRemoteTranscriptionPort: () -> Unit,
    onEditRemoteWhisperModel: () -> Unit,
    onEditRemoteWhisperToken: () -> Unit,
    onTestRemoteWhisperConnection: () -> Unit,
    onDownloadWhisperModel: () -> Unit,
    onCancelWhisperModelDownload: () -> Unit,
    onDeleteWhisperModel: () -> Unit,
    settingsAiOpen: Boolean,
    onSettingsAiOpenChange: (Boolean) -> Unit,
    onSetMinutes: (Int) -> Unit,
    onSetEndOfTrack: () -> Unit,
    onCustom: () -> Unit,
    onCancel: () -> Unit,
    onFadeBeforeEndChange: (Boolean) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onSync: () -> Unit,
    onDownload: (DlsiteWork) -> Unit,
    onDownloadContent: (DlsiteWork, DlsiteContent) -> Unit,
    onDeleteContent: (DlsiteWork, DlsiteContent) -> Unit,
    onPause: (DlsiteWork) -> Unit,
    onResume: (DlsiteWork) -> Unit,
    onDelete: (DlsiteWork) -> Unit,
    onOpenDownloadManager: () -> Unit,
) {
    when (selectedTab) {
        MainTab.MEDIA -> LibraryTab(
            state = libraryState,
            playbackState = playbackState,
            onPlaylistClicked = onPlaylistClicked,
            onCoverClicked = onCoverClicked,
            onRenamePlaylist = onRenamePlaylist,
            onDeletePlaylist = onDeletePlaylist,
            onTrackClicked = onTrackClicked,
            onTrackSubtitleClicked = onTrackSubtitleClicked,
            onGenerateSubtitle = onGenerateSubtitle,
            onOpenSubtitleGeneration = onOpenSubtitleGeneration,
            onRenameTrack = onRenameTrack,
            onDeleteTrack = onDeleteTrack,
            onMoveTrack = onMoveTrack,
            subtitleTasks = aiSubtitleTasks,
        )
        MainTab.SETTINGS -> SettingsTab(
            state = settingsState,
            onToggleOverlay = onToggleOverlay,
            onUnlockOverlay = onUnlockOverlay,
            onOpenOverlaySettings = onOpenOverlaySettings,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onBinauralEnhancedChange = onBinauralEnhancedChange,
            onCrossfadeEnabledChange = onCrossfadeEnabledChange,
            onWifiOnlyDownloadsChange = onWifiOnlyDownloadsChange,
            onRefreshStorageUsage = onRefreshStorageUsage,
            onThemeSelected = onThemeSelected,
            onCheckForUpdates = onCheckForUpdates,
            onShowUpdateDetails = onShowUpdateDetails,
            onCancelUpdateDownload = onCancelUpdateDownload,
            onRetryUpdateDownload = onRetryUpdateDownload,
            onAiTranscriptionBackendSelected = onAiTranscriptionBackendSelected,
            onAiEngineSelected = onAiEngineSelected,
            onEditAiOllamaBaseUrl = onEditAiOllamaBaseUrl,
            onEditAiOllamaModel = onEditAiOllamaModel,
            onEditAiDeepSeekBaseUrl = onEditAiDeepSeekBaseUrl,
            onEditAiDeepSeekModel = onEditAiDeepSeekModel,
            onEditAiDeepSeekApiKey = onEditAiDeepSeekApiKey,
            onAiAdultContentTranslationAllowedChange = onAiAdultContentTranslationAllowedChange,
            onAiWhisperModelSelected = onAiWhisperModelSelected,
            onEditRemoteTranscriptionAddress = onEditRemoteTranscriptionAddress,
            onEditRemoteTranscriptionPort = onEditRemoteTranscriptionPort,
            onEditRemoteWhisperModel = onEditRemoteWhisperModel,
            onEditRemoteWhisperToken = onEditRemoteWhisperToken,
            onTestRemoteWhisperConnection = onTestRemoteWhisperConnection,
            onDownloadWhisperModel = onDownloadWhisperModel,
            onCancelWhisperModelDownload = onCancelWhisperModelDownload,
            onDeleteWhisperModel = onDeleteWhisperModel,
            aiSettingsOpen = settingsAiOpen,
            onAiSettingsOpenChange = onSettingsAiOpenChange,
        )
        MainTab.SLEEP -> SleepTab(
            state = sleepState,
            onSetMinutes = onSetMinutes,
            onSetEndOfTrack = onSetEndOfTrack,
            onCustom = onCustom,
            onCancel = onCancel,
            onFadeBeforeEndChange = onFadeBeforeEndChange,
        )
        MainTab.DLSITE -> DlsiteTab(
            state = dlsiteState,
            onLogin = onLogin,
            onLogout = onLogout,
            onSync = onSync,
            onDownload = onDownload,
            onDownloadContent = onDownloadContent,
            onDeleteContent = onDeleteContent,
            onPause = onPause,
            onResume = onResume,
            onDelete = onDelete,
            onOpenDownloadManager = onOpenDownloadManager,
        )
    }
}
