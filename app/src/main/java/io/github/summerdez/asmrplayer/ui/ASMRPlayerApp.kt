package io.github.summerdez.asmrplayer.ui

import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.summerdez.asmrplayer.data.ai.AiSubtitleGenerationService
import io.github.summerdez.asmrplayer.domain.PlaylistQueries
import io.github.summerdez.asmrplayer.domain.model.SubtitleGenerationTarget
import io.github.summerdez.asmrplayer.presentation.AiSubtitleTaskViewModel
import io.github.summerdez.asmrplayer.presentation.DlsiteEvent
import io.github.summerdez.asmrplayer.presentation.DlsiteViewModel
import io.github.summerdez.asmrplayer.presentation.LibraryEvent
import io.github.summerdez.asmrplayer.presentation.LibraryViewModel
import io.github.summerdez.asmrplayer.presentation.MainTab
import io.github.summerdez.asmrplayer.presentation.MainViewModel
import io.github.summerdez.asmrplayer.presentation.PlaybackViewModel
import io.github.summerdez.asmrplayer.presentation.SettingsEvent
import io.github.summerdez.asmrplayer.presentation.SettingsViewModel
import io.github.summerdez.asmrplayer.presentation.SleepTimerViewModel
import io.github.summerdez.asmrplayer.ui.theme.ASMRPlayerTheme
import io.github.summerdez.asmrplayer.ui.theme.AppUi

@Composable
fun ASMRPlayerApp(
    mainViewModel: MainViewModel,
    libraryViewModel: LibraryViewModel,
    playbackViewModel: PlaybackViewModel,
    settingsViewModel: SettingsViewModel,
    sleepTimerViewModel: SleepTimerViewModel,
    dlsiteViewModel: DlsiteViewModel,
    aiSubtitleTaskViewModel: AiSubtitleTaskViewModel,
    onOpenOverlaySettings: () -> Unit,
    onToggleOverlay: () -> Unit,
    onUnlockOverlay: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onInstallUpdate: (String) -> Unit,
    onApplySystemBars: () -> Unit,
) {
    val context = LocalContext.current
    val selectedTab by mainViewModel.selectedTab.collectAsStateWithLifecycle()
    val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()
    val playbackState by playbackViewModel.state.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
    val sleepState by sleepTimerViewModel.state.collectAsStateWithLifecycle()
    val dlsiteState by dlsiteViewModel.state.collectAsStateWithLifecycle()
    val aiSubtitleTasks by aiSubtitleTaskViewModel.tasks.collectAsStateWithLifecycle()
    val toast: (String) -> Unit = { message ->
        if (message.isNotEmpty()) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(context, settingsState.themeMode) {
        AppUi.setThemeMode(context, settingsState.themeMode)
        onApplySystemBars()
    }
    LaunchedEffect(dlsiteViewModel) {
        dlsiteViewModel.events.collect { event ->
            when (event) {
                is DlsiteEvent.Message -> toast(event.text)
            }
        }
    }
    LaunchedEffect(libraryViewModel, playbackViewModel) {
        libraryViewModel.events.collect { event ->
            when (event) {
                is LibraryEvent.AudioUrisImported -> {
                    val count = event.count
                    toast(if (count == 0) "未选择音频" else "已添加 $count 首音频")
                }
                is LibraryEvent.FolderImported -> {
                    val result = event.result
                    toast(
                        if (result.audioCount == 0) {
                            "文件夹中没有可导入的音频"
                        } else {
                            "已导入 ${result.audioCount} 首音频，匹配 ${result.subtitleCount} 个字幕"
                        },
                    )
                }
                is LibraryEvent.SubtitleBound -> {
                    val binding = event.binding
                    playbackViewModel.setSubtitleForCurrentTrack(
                        binding.subtitleUri,
                        binding.subtitleTitle,
                        binding.trackId,
                    )
                    toast("字幕已绑定")
                }
                is LibraryEvent.TrackMoved -> {
                    playbackViewModel.onTrackMoved(event.result)
                    toast("已移动到 ${event.targetPlaylistName}")
                }
            }
        }
    }
    LaunchedEffect(settingsViewModel) {
        settingsViewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.Message -> toast(event.text)
                is SettingsEvent.InstallUpdate -> onInstallUpdate(event.apkPath)
            }
        }
    }

    val appLaunchers = rememberAppActivityLaunchers(
        context = context,
        libraryViewModel = libraryViewModel,
        dlsiteViewModel = dlsiteViewModel,
        toast = toast,
    )

    ASMRPlayerTheme(settingsState.themeMode) {
        val rootState = rememberAppRootState()
        val currentPlaybackTrackId = playbackViewModel.currentTrackId()
        val currentPlaybackPlaylist = PlaylistQueries.findById(libraryState.playlists, playbackState.playlistId)
            ?: libraryState.playlists.firstOrNull { playlist ->
                playlist.tracks.any { track -> track.id == currentPlaybackTrackId }
            }
            ?: libraryState.selectedPlaylist
        val currentPlaybackTrack = PlaylistQueries.trackAt(currentPlaybackPlaylist, playbackState.playlistIndex)
            ?.takeIf { track -> currentPlaybackTrackId.isBlank() || track.id == currentPlaybackTrackId }
            ?: currentPlaybackPlaylist?.tracks?.firstOrNull { track -> track.id == currentPlaybackTrackId }
        val currentAiSubtitleTask = currentPlaybackTrack?.let { track -> aiSubtitleTasks[track.id] }
        val currentTrackIsLocal = currentPlaybackTrack?.uri?.startsWith("file:") == true
        val startAiSubtitleForCurrentTrack: () -> Unit = {
            val playlist = currentPlaybackPlaylist
            val track = currentPlaybackTrack
            if (playlist == null || track == null || track.uri.isBlank()) {
                toast("请先选择音频")
            } else {
                val existingTask = aiSubtitleTasks[track.id]
                if (existingTask != null) {
                    rootState.activeAiSubtitleTrackId = track.id
                } else {
                    val target = SubtitleGenerationTarget(
                        playlistId = playlist.id,
                        trackId = track.id,
                        trackTitle = track.title,
                        audioUri = track.uri,
                        contextTitle = playlist.name,
                    )
                    ContextCompat.startForegroundService(
                        context,
                        AiSubtitleGenerationService.startIntent(context, target),
                    )
                    rootState.activeAiSubtitleTrackId = track.id
                }
            }
        }
        val openAiSubtitleProgressForCurrentTrack: () -> Unit = {
            val trackId = currentPlaybackTrack?.id
            if (trackId != null && aiSubtitleTasks.containsKey(trackId)) {
                rootState.activeAiSubtitleTrackId = trackId
            } else {
                toast("当前没有 AI 字幕生成任务")
            }
        }

        AppRootBackHandler(rootState)

        AppScaffold(
            selectedTab = selectedTab,
            playbackState = playbackState,
            showLibraryMenu = rootState.showLibraryMenu,
            playerOpen = rootState.playerOpen,
            queueOpen = rootState.queueOpen,
            downloadManagerOpen = rootState.downloadManagerOpen,
            activeAiSubtitleTrackId = rootState.activeAiSubtitleTrackId,
            updateDialogOpen = settingsState.updateDialogRelease != null,
            installPromptOpen = settingsState.installPromptRelease != null,
            onLibraryMenuExpandedChange = { rootState.showLibraryMenu = it },
            onCreatePlaylist = { rootState.createPlaylistDialog = true },
            onImportAudio = appLaunchers::importAudio,
            onImportFolder = appLaunchers::importFolder,
            onOpenPlayer = { rootState.playerOpen = true },
            onPlayClicked = { playbackViewModel.onPlayClicked()?.let(toast) },
            onNextClicked = { playbackViewModel.playRelativeTrack(1)?.let(toast) },
            onTabSelected = { tab ->
                if (tab != MainTab.SETTINGS) {
                    rootState.settingsAiOpen = false
                }
                mainViewModel.selectTab(tab)
            },
            showHeader = selectedTab != MainTab.SETTINGS,
            content = {
                AppTabHost(
                    selectedTab = selectedTab,
                    libraryState = libraryState,
                    playbackState = playbackState,
                    settingsState = settingsState,
                    sleepState = sleepState,
                    dlsiteState = dlsiteState,
                    aiSubtitleTasks = aiSubtitleTasks,
                    onPlaylistClicked = libraryViewModel::handlePlaylistClick,
                    onCoverClicked = appLaunchers::pickCover,
                    onRenamePlaylist = { rootState.renamePlaylist = it },
                    onDeletePlaylist = { rootState.deletePlaylist = it },
                    onTrackClicked = { playlist, index ->
                        libraryViewModel.selectPlaylist(playlist)
                        playbackViewModel.playPlaylistTrack(playlist, index)
                    },
                    onTrackSubtitleClicked = appLaunchers::pickSubtitle,
                    onGenerateSubtitle = { playlist, track ->
                        val target = SubtitleGenerationTarget(
                            playlistId = playlist.id,
                            trackId = track.id,
                            trackTitle = track.title,
                            audioUri = track.uri,
                            contextTitle = playlist.name,
                        )
                        ContextCompat.startForegroundService(
                            context,
                            AiSubtitleGenerationService.startIntent(context, target),
                        )
                        rootState.activeAiSubtitleTrackId = track.id
                    },
                    onOpenSubtitleGeneration = { trackId -> rootState.activeAiSubtitleTrackId = trackId },
                    onRenameTrack = { playlist, track -> rootState.renameTrack = playlist to track },
                    onDeleteTrack = { playlist, track, index -> rootState.deleteTrack = Triple(playlist, track, index) },
                    onMoveTrack = { playlist, track -> rootState.moveTrack = playlist to track },
                    onToggleOverlay = onToggleOverlay,
                    onUnlockOverlay = onUnlockOverlay,
                    onOpenOverlaySettings = onOpenOverlaySettings,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onBinauralEnhancedChange = settingsViewModel::setBinauralEnhanced,
                    onCrossfadeEnabledChange = settingsViewModel::setCrossfadeEnabled,
                    onWifiOnlyDownloadsChange = settingsViewModel::setWifiOnlyDownloads,
                    onRefreshStorageUsage = { settingsViewModel.refreshStorageUsage(showMessage = true) },
                    onThemeSelected = { mode ->
                        settingsViewModel.setThemeMode(mode)
                    },
                    onCheckForUpdates = settingsViewModel::checkForUpdates,
                    onShowUpdateDetails = settingsViewModel::showUpdateDetails,
                    onCancelUpdateDownload = settingsViewModel::cancelUpdateDownload,
                    onRetryUpdateDownload = settingsViewModel::retryUpdateDownload,
                    onAiTranscriptionBackendSelected = settingsViewModel::setAiTranscriptionBackend,
                    onAiEngineSelected = settingsViewModel::setAiTranslationEngine,
                    onEditAiOllamaBaseUrl = { rootState.aiSettingField = AiSettingField.OLLAMA_BASE_URL },
                    onEditAiOllamaModel = { rootState.aiSettingField = AiSettingField.OLLAMA_MODEL },
                    onEditAiDeepSeekBaseUrl = { rootState.aiSettingField = AiSettingField.DEEPSEEK_BASE_URL },
                    onEditAiDeepSeekModel = { rootState.aiSettingField = AiSettingField.DEEPSEEK_MODEL },
                    onEditAiDeepSeekApiKey = { rootState.aiSettingField = AiSettingField.DEEPSEEK_API_KEY },
                    onAiAdultContentTranslationAllowedChange = settingsViewModel::setAiAdultContentTranslationAllowed,
                    onAiWhisperModelSelected = settingsViewModel::setAiWhisperModelId,
                    onEditRemoteTranscriptionAddress = {
                        rootState.aiSettingField = AiSettingField.REMOTE_TRANSCRIPTION_ADDRESS
                    },
                    onEditRemoteTranscriptionPort = {
                        rootState.aiSettingField = AiSettingField.REMOTE_TRANSCRIPTION_PORT
                    },
                    onEditRemoteWhisperModel = { rootState.aiSettingField = AiSettingField.REMOTE_WHISPER_MODEL },
                    onEditRemoteWhisperToken = { rootState.aiSettingField = AiSettingField.REMOTE_WHISPER_TOKEN },
                    onTestRemoteWhisperConnection = settingsViewModel::testRemoteWhisperConnection,
                    onDownloadWhisperModel = settingsViewModel::downloadWhisperModel,
                    onCancelWhisperModelDownload = settingsViewModel::cancelWhisperModelDownload,
                    onDeleteWhisperModel = settingsViewModel::deleteWhisperModel,
                    settingsAiOpen = rootState.settingsAiOpen,
                    onSettingsAiOpenChange = { rootState.settingsAiOpen = it },
                    onSetMinutes = { minutes ->
                        if (sleepTimerViewModel.setMinutes(minutes)) {
                            toast("睡眠定时已设置为 $minutes 分钟")
                        } else {
                            toast("播放器尚未连接")
                        }
                    },
                    onSetEndOfTrack = {
                        if (!sleepTimerViewModel.setAtEndOfTrack()) {
                            toast("播放器尚未连接")
                        }
                    },
                    onCustom = { rootState.customSleepDialog = true },
                    onCancel = { sleepTimerViewModel.cancel() },
                    onFadeBeforeEndChange = sleepTimerViewModel::setFadeBeforeEndEnabled,
                    onLogin = appLaunchers::login,
                    onLogout = { dlsiteViewModel.logout() },
                    onSync = { dlsiteViewModel.syncWorks() },
                    onDownload = { dlsiteViewModel.requestDownloadOptions(it) },
                    onDownloadContent = { work, content -> dlsiteViewModel.startDownload(work, content) },
                    onDeleteContent = { work, content ->
                        dlsiteViewModel.deleteContent(
                            work = work,
                            content = content,
                            onLibraryChanged = {},
                        )
                    },
                    onPause = { dlsiteViewModel.pauseDownload(it) },
                    onResume = { dlsiteViewModel.resumeDownload(it) },
                    onDelete = { work ->
                        dlsiteViewModel.deleteCache(
                            work = work,
                            onLibraryChanged = {
                                if (work.playlistId.isNotEmpty()) {
                                    playbackViewModel.onPlaylistDeleted(work.playlistId)
                                }
                            },
                        )
                    },
                    onOpenDownloadManager = { rootState.downloadManagerOpen = true },
                )
            },
            dialogHost = {
                AppDialogHost(
                    playerOpen = rootState.playerOpen,
                    onPlayerOpenChange = { rootState.playerOpen = it },
                    queueOpen = rootState.queueOpen,
                    onQueueOpenChange = { rootState.queueOpen = it },
                    downloadManagerOpen = rootState.downloadManagerOpen,
                    onDownloadManagerOpenChange = { rootState.downloadManagerOpen = it },
                    activeAiSubtitleTrackId = rootState.activeAiSubtitleTrackId,
                    onActiveAiSubtitleTrackIdChange = { rootState.activeAiSubtitleTrackId = it },
                    createPlaylistDialog = rootState.createPlaylistDialog,
                    onCreatePlaylistDialogChange = { rootState.createPlaylistDialog = it },
                    renamePlaylist = rootState.renamePlaylist,
                    onRenamePlaylistChange = { rootState.renamePlaylist = it },
                    deletePlaylist = rootState.deletePlaylist,
                    onDeletePlaylistChange = { rootState.deletePlaylist = it },
                    renameTrack = rootState.renameTrack,
                    onRenameTrackChange = { rootState.renameTrack = it },
                    deleteTrack = rootState.deleteTrack,
                    onDeleteTrackChange = { rootState.deleteTrack = it },
                    moveTrack = rootState.moveTrack,
                    onMoveTrackChange = { rootState.moveTrack = it },
                    aiSettingField = rootState.aiSettingField,
                    onAiSettingFieldChange = { rootState.aiSettingField = it },
                    customSleepDialog = rootState.customSleepDialog,
                    onCustomSleepDialogChange = { rootState.customSleepDialog = it },
                    libraryState = libraryState,
                    playbackState = playbackState,
                    settingsState = settingsState,
                    dlsiteState = dlsiteState,
                    aiSubtitleTasks = aiSubtitleTasks,
                    currentAiSubtitleTask = currentAiSubtitleTask,
                    currentTrackIsLocal = currentTrackIsLocal,
                    libraryViewModel = libraryViewModel,
                    playbackViewModel = playbackViewModel,
                    settingsViewModel = settingsViewModel,
                    sleepTimerViewModel = sleepTimerViewModel,
                    dlsiteViewModel = dlsiteViewModel,
                    startAiSubtitleForCurrentTrack = startAiSubtitleForCurrentTrack,
                    openAiSubtitleProgressForCurrentTrack = openAiSubtitleProgressForCurrentTrack,
                    toast = toast,
                )
            },
        )
    }
}
