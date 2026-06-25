package io.github.summerdez.asmrplayer.ui

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.summerdez.asmrplayer.data.files.DocumentFiles
import io.github.summerdez.asmrplayer.data.ai.AiSubtitleGenerationService
import io.github.summerdez.asmrplayer.domain.PlaylistQueries
import io.github.summerdez.asmrplayer.domain.model.Playlist
import io.github.summerdez.asmrplayer.domain.model.SubtitleGenerationTarget
import io.github.summerdez.asmrplayer.domain.model.TrackItem
import io.github.summerdez.asmrplayer.presentation.AiSubtitleTaskViewModel
import io.github.summerdez.asmrplayer.presentation.DlsiteEvent
import io.github.summerdez.asmrplayer.presentation.DlsiteViewModel
import io.github.summerdez.asmrplayer.presentation.LibraryEvent
import io.github.summerdez.asmrplayer.presentation.LibraryViewModel
import io.github.summerdez.asmrplayer.presentation.MainViewModel
import io.github.summerdez.asmrplayer.presentation.PlaybackViewModel
import io.github.summerdez.asmrplayer.presentation.SettingsEvent
import io.github.summerdez.asmrplayer.presentation.SettingsViewModel
import io.github.summerdez.asmrplayer.presentation.SleepTimerViewModel
import io.github.summerdez.asmrplayer.ui.activity.DlsiteLoginActivity
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

    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            return@rememberLauncherForActivityResult
        }
        libraryViewModel.addAudioUris(context, DocumentFiles.urisFromResult(result.data))
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val uri = data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) {
            toast("未选择文件夹")
            return@rememberLauncherForActivityResult
        }
        libraryViewModel.importFolder(context, data, uri)
    }
    val coverLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            libraryViewModel.handleCoverUri(context, uri)
            toast("封面已设置")
        } else {
            libraryViewModel.clearCoverPicker()
        }
    }
    val subtitleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            libraryViewModel.handleSubtitleUri(context, uri)
        } else {
            libraryViewModel.clearSubtitlePicker()
        }
    }
    val loginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        dlsiteViewModel.refresh()
        if (dlsiteViewModel.state.value.loggedIn) {
            dlsiteViewModel.syncWorks()
        }
    }

    ASMRPlayerTheme(settingsState.themeMode) {
        var showLibraryMenu by remember { mutableStateOf(false) }
        var playerOpen by remember { mutableStateOf(false) }
        var queueOpen by remember { mutableStateOf(false) }
        var createPlaylistDialog by remember { mutableStateOf(false) }
        var renamePlaylist by remember { mutableStateOf<Playlist?>(null) }
        var deletePlaylist by remember { mutableStateOf<Playlist?>(null) }
        var renameTrack by remember { mutableStateOf<Pair<Playlist, TrackItem>?>(null) }
        var deleteTrack by remember { mutableStateOf<Triple<Playlist, TrackItem, Int>?>(null) }
        var moveTrack by remember { mutableStateOf<Pair<Playlist, TrackItem>?>(null) }
        var customSleepDialog by remember { mutableStateOf(false) }
        var downloadManagerOpen by remember { mutableStateOf(false) }
        var aiSettingField by remember { mutableStateOf<AiSettingField?>(null) }
        var activeAiSubtitleTrackId by remember { mutableStateOf<String?>(null) }
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
                    activeAiSubtitleTrackId = track.id
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
                    activeAiSubtitleTrackId = track.id
                }
            }
        }
        val openAiSubtitleProgressForCurrentTrack: () -> Unit = {
            val trackId = currentPlaybackTrack?.id
            if (trackId != null && aiSubtitleTasks.containsKey(trackId)) {
                activeAiSubtitleTrackId = trackId
            } else {
                toast("当前没有 AI 字幕生成任务")
            }
        }

        BackHandler(enabled = playerOpen || queueOpen) {
            if (queueOpen) {
                queueOpen = false
            } else {
                playerOpen = false
            }
        }

        AppScaffold(
            selectedTab = selectedTab,
            playbackState = playbackState,
            showLibraryMenu = showLibraryMenu,
            playerOpen = playerOpen,
            queueOpen = queueOpen,
            downloadManagerOpen = downloadManagerOpen,
            activeAiSubtitleTrackId = activeAiSubtitleTrackId,
            updateDialogOpen = settingsState.updateDialogRelease != null,
            installPromptOpen = settingsState.installPromptRelease != null,
            onLibraryMenuExpandedChange = { showLibraryMenu = it },
            onCreatePlaylist = { createPlaylistDialog = true },
            onImportAudio = { audioLauncher.launch(DocumentFiles.audioPickerIntent(true)) },
            onImportFolder = { folderLauncher.launch(DocumentFiles.folderPickerIntent()) },
            onOpenPlayer = { playerOpen = true },
            onPlayClicked = { playbackViewModel.onPlayClicked()?.let(toast) },
            onNextClicked = { playbackViewModel.playRelativeTrack(1)?.let(toast) },
            onTabSelected = mainViewModel::selectTab,
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
                    onCoverClicked = { playlist ->
                        libraryViewModel.startCoverPicker(playlist)
                        coverLauncher.launch(DocumentFiles.imagePickerIntent())
                    },
                    onRenamePlaylist = { renamePlaylist = it },
                    onDeletePlaylist = { deletePlaylist = it },
                    onTrackClicked = { playlist, index ->
                        libraryViewModel.selectPlaylist(playlist)
                        playbackViewModel.playPlaylistTrack(playlist, index)
                    },
                    onTrackSubtitleClicked = { playlist, track ->
                        libraryViewModel.startSubtitlePicker(playlist, track)
                        subtitleLauncher.launch(DocumentFiles.subtitlePickerIntent())
                    },
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
                        activeAiSubtitleTrackId = track.id
                    },
                    onOpenSubtitleGeneration = { trackId -> activeAiSubtitleTrackId = trackId },
                    onRenameTrack = { playlist, track -> renameTrack = playlist to track },
                    onDeleteTrack = { playlist, track, index -> deleteTrack = Triple(playlist, track, index) },
                    onMoveTrack = { playlist, track -> moveTrack = playlist to track },
                    onToggleOverlay = onToggleOverlay,
                    onUnlockOverlay = onUnlockOverlay,
                    onOpenOverlaySettings = onOpenOverlaySettings,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onThemeSelected = { mode ->
                        settingsViewModel.setThemeMode(mode)
                    },
                    onCheckForUpdates = settingsViewModel::checkForUpdates,
                    onShowUpdateDetails = settingsViewModel::showUpdateDetails,
                    onCancelUpdateDownload = settingsViewModel::cancelUpdateDownload,
                    onRetryUpdateDownload = settingsViewModel::retryUpdateDownload,
                    onAiTranscriptionBackendSelected = settingsViewModel::setAiTranscriptionBackend,
                    onAiEngineSelected = settingsViewModel::setAiTranslationEngine,
                    onEditAiOllamaBaseUrl = { aiSettingField = AiSettingField.OLLAMA_BASE_URL },
                    onEditAiOllamaModel = { aiSettingField = AiSettingField.OLLAMA_MODEL },
                    onEditAiDeepSeekBaseUrl = { aiSettingField = AiSettingField.DEEPSEEK_BASE_URL },
                    onEditAiDeepSeekModel = { aiSettingField = AiSettingField.DEEPSEEK_MODEL },
                    onEditAiDeepSeekApiKey = { aiSettingField = AiSettingField.DEEPSEEK_API_KEY },
                    onAiAdultContentTranslationAllowedChange = settingsViewModel::setAiAdultContentTranslationAllowed,
                    onAiWhisperModelSelected = settingsViewModel::setAiWhisperModelId,
                    onEditRemoteTranscriptionAddress = {
                        aiSettingField = AiSettingField.REMOTE_TRANSCRIPTION_ADDRESS
                    },
                    onEditRemoteTranscriptionPort = {
                        aiSettingField = AiSettingField.REMOTE_TRANSCRIPTION_PORT
                    },
                    onEditRemoteWhisperModel = { aiSettingField = AiSettingField.REMOTE_WHISPER_MODEL },
                    onEditRemoteWhisperToken = { aiSettingField = AiSettingField.REMOTE_WHISPER_TOKEN },
                    onTestRemoteWhisperConnection = settingsViewModel::testRemoteWhisperConnection,
                    onDownloadWhisperModel = settingsViewModel::downloadWhisperModel,
                    onCancelWhisperModelDownload = settingsViewModel::cancelWhisperModelDownload,
                    onDeleteWhisperModel = settingsViewModel::deleteWhisperModel,
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
                    onCustom = { customSleepDialog = true },
                    onCancel = { sleepTimerViewModel.cancel() },
                    onLogin = { loginLauncher.launch(Intent(context, DlsiteLoginActivity::class.java)) },
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
                    onPauseAll = dlsiteViewModel::pauseAllDownloads,
                    onResumeAll = dlsiteViewModel::resumeAllDownloads,
                    onOpenDownloadManager = { downloadManagerOpen = true },
                )
            },
            dialogHost = {
                AppDialogHost(
                    playerOpen = playerOpen,
                    onPlayerOpenChange = { playerOpen = it },
                    queueOpen = queueOpen,
                    onQueueOpenChange = { queueOpen = it },
                    downloadManagerOpen = downloadManagerOpen,
                    onDownloadManagerOpenChange = { downloadManagerOpen = it },
                    activeAiSubtitleTrackId = activeAiSubtitleTrackId,
                    onActiveAiSubtitleTrackIdChange = { activeAiSubtitleTrackId = it },
                    createPlaylistDialog = createPlaylistDialog,
                    onCreatePlaylistDialogChange = { createPlaylistDialog = it },
                    renamePlaylist = renamePlaylist,
                    onRenamePlaylistChange = { renamePlaylist = it },
                    deletePlaylist = deletePlaylist,
                    onDeletePlaylistChange = { deletePlaylist = it },
                    renameTrack = renameTrack,
                    onRenameTrackChange = { renameTrack = it },
                    deleteTrack = deleteTrack,
                    onDeleteTrackChange = { deleteTrack = it },
                    moveTrack = moveTrack,
                    onMoveTrackChange = { moveTrack = it },
                    aiSettingField = aiSettingField,
                    onAiSettingFieldChange = { aiSettingField = it },
                    customSleepDialog = customSleepDialog,
                    onCustomSleepDialogChange = { customSleepDialog = it },
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
