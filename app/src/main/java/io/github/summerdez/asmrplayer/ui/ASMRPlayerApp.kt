package io.github.summerdez.asmrplayer.ui

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.summerdez.asmrplayer.BuildConfig
import io.github.summerdez.asmrplayer.data.files.DocumentFiles
import io.github.summerdez.asmrplayer.data.ai.AiSubtitleGenerationService
import io.github.summerdez.asmrplayer.domain.PlaylistQueries
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleStage
import io.github.summerdez.asmrplayer.domain.model.Playlist
import io.github.summerdez.asmrplayer.domain.model.SubtitleGenerationTarget
import io.github.summerdez.asmrplayer.domain.model.TrackItem
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
import io.github.summerdez.asmrplayer.ui.activity.DlsiteLoginActivity
import io.github.summerdez.asmrplayer.ui.components.BottomPlaybackArea
import io.github.summerdez.asmrplayer.ui.components.ConfirmDialog
import io.github.summerdez.asmrplayer.ui.components.InstallUpdateDialog
import io.github.summerdez.asmrplayer.ui.components.MoveTrackDialog
import io.github.summerdez.asmrplayer.ui.components.PageHeader
import io.github.summerdez.asmrplayer.ui.components.TextInputDialog
import io.github.summerdez.asmrplayer.ui.components.UpdateDetailsDialog
import io.github.summerdez.asmrplayer.ui.screens.DownloadManagerSheet
import io.github.summerdez.asmrplayer.ui.screens.DlsiteTab
import io.github.summerdez.asmrplayer.ui.screens.AiSubtitleGenerationSheet
import io.github.summerdez.asmrplayer.ui.screens.LibraryTab
import io.github.summerdez.asmrplayer.ui.screens.QueueContent
import io.github.summerdez.asmrplayer.ui.screens.SettingsTab
import io.github.summerdez.asmrplayer.ui.screens.SleepTab
import io.github.summerdez.asmrplayer.ui.screens.SubtitlePlayerScreen
import io.github.summerdez.asmrplayer.ui.theme.ASMRPlayerTheme
import io.github.summerdez.asmrplayer.ui.theme.LocalAmberTokens

private enum class AiSettingField {
    OLLAMA_BASE_URL,
    OLLAMA_MODEL,
    DEEPSEEK_BASE_URL,
    DEEPSEEK_MODEL,
    DEEPSEEK_API_KEY,
    REMOTE_TRANSCRIPTION_ADDRESS,
    REMOTE_TRANSCRIPTION_PORT,
    REMOTE_WHISPER_MODEL,
    REMOTE_WHISPER_TOKEN,
}

@OptIn(ExperimentalMaterial3Api::class)

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
        val tokens = LocalAmberTokens.current
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

        val pageBackground = if (selectedTab == MainTab.SETTINGS || selectedTab == MainTab.SLEEP) {
            tokens.grouped
        } else {
            tokens.bg
        }
        val uiProbeScreen = buildList {
            add(selectedTab.title)
            if (playerOpen) add("全屏播放器")
            if (queueOpen) add("播放队列")
            if (downloadManagerOpen) add("下载管理")
            if (activeAiSubtitleTrackId != null) add("AI 字幕进度")
            if (settingsState.updateDialogRelease != null) add("更新详情")
            if (settingsState.installPromptRelease != null) add("安装更新")
        }.joinToString(" / ")
        UiProbeHost(enabled = BuildConfig.UI_PROBE_ENABLED, screen = uiProbeScreen) {
            Box(Modifier.fillMaxSize().background(pageBackground).uiProbe("app.root", "应用根容器", "ASMRPlayerApp.kt")) {
            Scaffold(
                containerColor = pageBackground,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    BottomPlaybackArea(
                        playbackState = playbackState,
                        selectedTab = selectedTab,
                        onOpenPlayer = { playerOpen = true },
                        onPlayClicked = { playbackViewModel.onPlayClicked()?.let(toast) },
                        onNextClicked = { playbackViewModel.playRelativeTrack(1)?.let(toast) },
                        onTabSelected = mainViewModel::selectTab,
                    )
                },
            ) { innerPadding ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(pageBackground),
                ) {
                    PageHeader(
                        title = selectedTab.title,
                        showMenu = selectedTab == MainTab.MEDIA,
                        menuExpanded = showLibraryMenu,
                        onMenuExpandedChange = { showLibraryMenu = it },
                        onCreatePlaylist = { createPlaylistDialog = true },
                        onImportAudio = { audioLauncher.launch(DocumentFiles.audioPickerIntent(true)) },
                        onImportFolder = { folderLauncher.launch(DocumentFiles.folderPickerIntent()) },
                    )
                    Box(Modifier.weight(1f)) {
                        when (selectedTab) {
                            MainTab.MEDIA -> LibraryTab(
                                state = libraryState,
                                playbackState = playbackState,
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
                                subtitleTasks = aiSubtitleTasks,
                            )
                            MainTab.SETTINGS -> SettingsTab(
                                state = settingsState,
                                onToggleOverlay = onToggleOverlay,
                                onUnlockOverlay = onUnlockOverlay,
                                onOpenOverlaySettings = onOpenOverlaySettings,
                                onRequestNotificationPermission = onRequestNotificationPermission,
                                onThemeSelected = { mode ->
                                    settingsViewModel.setThemeMode(mode)
                                    onApplySystemBars()
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
                            )
                            MainTab.SLEEP -> SleepTab(
                                state = sleepState,
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
                            )
                            MainTab.DLSITE -> DlsiteTab(
                                state = dlsiteState,
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
                        }
                    }
                }
            }

            if (playerOpen) {
                SubtitlePlayerScreen(
                    playbackState = playbackState,
                    aiSubtitleTask = currentAiSubtitleTask,
                    currentTrackIsLocal = currentTrackIsLocal,
                    onClose = { playerOpen = false },
                    onPrevious = { playbackViewModel.playRelativeTrack(-1)?.let(toast) },
                    onPlay = { playbackViewModel.onPlayClicked()?.let(toast) },
                    onNext = { playbackViewModel.playRelativeTrack(1)?.let(toast) },
                    onSeek = playbackViewModel::seekTo,
                    onQueue = { queueOpen = true },
                    onGenerateAiSubtitle = startAiSubtitleForCurrentTrack,
                    onOpenAiSubtitleProgress = openAiSubtitleProgressForCurrentTrack,
                    onSleepTimer = { customSleepDialog = true },
                    onMixLayers = { toast("混音层叠暂未实现") },
                    onDownloadTrack = {
                        if (currentTrackIsLocal) {
                            toast("当前已在本地")
                        } else {
                            toast("下载到本地暂未实现")
                        }
                    },
                    onRemoveFromQueue = { toast("从队列中移除暂未实现") },
                )
            }

            if (queueOpen) {
                val queuePlaylist = PlaylistQueries.findById(libraryState.playlists, playbackState.playlistId)
                    ?: libraryState.selectedPlaylist
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { queueOpen = false },
                    sheetState = sheetState,
                    containerColor = tokens.sheet,
                    dragHandle = null,
                ) {
                    QueueContent(
                        playlist = queuePlaylist,
                        playbackState = playbackState,
                        onDismissRequest = { queueOpen = false },
                        onTrackClicked = { playlist, index ->
                            libraryViewModel.selectPlaylist(playlist)
                            playbackViewModel.playPlaylistTrack(playlist, index)
                        },
                    )
                }
            }

            if (downloadManagerOpen) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { downloadManagerOpen = false },
                    sheetState = sheetState,
                    containerColor = tokens.sheet,
                    dragHandle = null,
                ) {
                    DownloadManagerSheet(
                        state = dlsiteState,
                        onPauseAll = dlsiteViewModel::pauseAllDownloads,
                        onResumeAll = dlsiteViewModel::resumeAllDownloads,
                        onClearCompleted = dlsiteViewModel::clearCompletedDownloadTasks,
                        onPause = dlsiteViewModel::pauseDownload,
                        onResume = dlsiteViewModel::resumeDownload,
                        onCancel = dlsiteViewModel::cancelDownload,
                    )
                }
            }
            activeAiSubtitleTrackId?.let { trackId ->
                val task = aiSubtitleTasks[trackId]
                if (task != null) {
                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    ModalBottomSheet(
                        onDismissRequest = { activeAiSubtitleTrackId = null },
                        sheetState = sheetState,
                        containerColor = tokens.sheet,
                        dragHandle = null,
                    ) {
                        AiSubtitleGenerationSheet(
                            task = task,
                            onPause = {
                                context.startService(AiSubtitleGenerationService.pauseIntent(context, trackId))
                            },
                            onResume = {
                                ContextCompat.startForegroundService(
                                    context,
                                    AiSubtitleGenerationService.startIntent(context, task.target),
                                )
                            },
                            onRetry = {
                                ContextCompat.startForegroundService(
                                    context,
                                    AiSubtitleGenerationService.startIntent(context, task.target),
                                )
                            },
                            onRegenerate = {
                                ContextCompat.startForegroundService(
                                    context,
                                    AiSubtitleGenerationService.startIntent(
                                        context = context,
                                        target = task.target,
                                        forceRegenerate = true,
                                    ),
                                )
                            },
                            onCancel = {
                                context.startService(AiSubtitleGenerationService.cancelIntent(context, trackId))
                                activeAiSubtitleTrackId = null
                            },
                            onDismiss = { activeAiSubtitleTrackId = null },
                        )
                    }
                } else if (activeAiSubtitleTrackId != null) {
                    activeAiSubtitleTrackId = null
                }
            }
            settingsState.updateDialogRelease?.let { release ->
                UpdateDetailsDialog(
                    release = release,
                    currentVersionName = settingsState.currentVersionName,
                    onDismiss = settingsViewModel::dismissUpdateDetails,
                    onDownload = settingsViewModel::downloadAvailableUpdate,
                )
            }
            settingsState.installPromptRelease?.let { release ->
                InstallUpdateDialog(
                    release = release,
                    onDismiss = settingsViewModel::dismissInstallPrompt,
                    onInstall = settingsViewModel::installDownloadedUpdate,
                )
            }

            if (createPlaylistDialog) {
                TextInputDialog(
                    title = "新建播放列表",
                    initialValue = "",
                    confirmText = "创建",
                    onDismiss = { createPlaylistDialog = false },
                    onConfirm = { name ->
                        libraryViewModel.createPlaylist(name)
                        createPlaylistDialog = false
                    },
                )
            }
            renamePlaylist?.let { playlist ->
                TextInputDialog(
                    title = "重命名播放列表",
                    initialValue = playlist.name,
                    confirmText = "保存",
                    onDismiss = { renamePlaylist = null },
                    onConfirm = { name ->
                        libraryViewModel.renamePlaylist(playlist, name)
                        renamePlaylist = null
                    },
                )
            }
            deletePlaylist?.let { playlist ->
                ConfirmDialog(
                    title = "删除播放列表",
                    message = "删除“${playlist.name}”及其中的曲目记录？",
                    onDismiss = { deletePlaylist = null },
                    onConfirm = {
                        playbackViewModel.onPlaylistDeleted(playlist.id)
                        libraryViewModel.deletePlaylist(playlist)
                        deletePlaylist = null
                    },
                )
            }
            renameTrack?.let { target ->
                TextInputDialog(
                    title = "重命名曲目",
                    initialValue = target.second.title,
                    confirmText = "保存",
                    onDismiss = { renameTrack = null },
                    onConfirm = { name ->
                        libraryViewModel.renameTrack(target.first, target.second, name)
                        playbackViewModel.onTrackRenamed(target.second.id, name)
                        renameTrack = null
                    },
                )
            }
            deleteTrack?.let { target ->
                ConfirmDialog(
                    title = "删除曲目",
                    message = "从“${target.first.name}”移除“${target.second.title}”？",
                    onDismiss = { deleteTrack = null },
                    onConfirm = {
                        playbackViewModel.onTrackRemoved(target.first.id, target.third)
                        libraryViewModel.removeTrack(target.first, target.second)
                        deleteTrack = null
                    },
                )
            }
            moveTrack?.let { target ->
                MoveTrackDialog(
                    sourcePlaylist = target.first,
                    track = target.second,
                    playlists = libraryState.playlists,
                    onDismiss = { moveTrack = null },
                    onChoose = { targetPlaylist ->
                        libraryViewModel.moveTrack(target.first, target.second, targetPlaylist)
                        moveTrack = null
                    },
                )
            }
            aiSettingField?.let { field ->
                val aiSettings = settingsState.aiSubtitleSettings
                TextInputDialog(
                    title = when (field) {
                        AiSettingField.OLLAMA_BASE_URL -> "Ollama 接口地址"
                        AiSettingField.OLLAMA_MODEL -> "Ollama 模型"
                        AiSettingField.DEEPSEEK_BASE_URL -> "OpenAI 兼容接口地址"
                        AiSettingField.DEEPSEEK_MODEL -> "OpenAI 兼容模型"
                        AiSettingField.DEEPSEEK_API_KEY -> "OpenAI 兼容 API Key"
                        AiSettingField.REMOTE_TRANSCRIPTION_ADDRESS -> "远程转写地址"
                        AiSettingField.REMOTE_TRANSCRIPTION_PORT -> "远程转写端口"
                        AiSettingField.REMOTE_WHISPER_MODEL -> "转写模型"
                        AiSettingField.REMOTE_WHISPER_TOKEN -> "远程转写 Token"
                    },
                    initialValue = when (field) {
                        AiSettingField.OLLAMA_BASE_URL -> aiSettings.ollamaBaseUrl
                        AiSettingField.OLLAMA_MODEL -> aiSettings.ollamaModel
                        AiSettingField.DEEPSEEK_BASE_URL -> aiSettings.deepSeekBaseUrl
                        AiSettingField.DEEPSEEK_MODEL -> aiSettings.deepSeekModel
                        AiSettingField.DEEPSEEK_API_KEY -> aiSettings.deepSeekApiKey
                        AiSettingField.REMOTE_TRANSCRIPTION_ADDRESS -> aiSettings.remoteTranscriptionAddress
                        AiSettingField.REMOTE_TRANSCRIPTION_PORT -> aiSettings.remoteTranscriptionPort
                        AiSettingField.REMOTE_WHISPER_MODEL -> aiSettings.activeRemoteWhisperModel
                        AiSettingField.REMOTE_WHISPER_TOKEN -> aiSettings.remoteWhisperToken
                    },
                    confirmText = "保存",
                    numeric = field == AiSettingField.REMOTE_TRANSCRIPTION_PORT,
                    required = when (field) {
                        AiSettingField.REMOTE_TRANSCRIPTION_ADDRESS,
                        AiSettingField.REMOTE_TRANSCRIPTION_PORT,
                        AiSettingField.REMOTE_WHISPER_MODEL,
                        AiSettingField.REMOTE_WHISPER_TOKEN,
                        AiSettingField.DEEPSEEK_API_KEY,
                        -> false
                        else -> true
                    },
                    message = "",
                    onDismiss = { aiSettingField = null },
                    onConfirm = { value ->
                        when (field) {
                            AiSettingField.OLLAMA_BASE_URL -> settingsViewModel.setAiOllamaBaseUrl(value)
                            AiSettingField.OLLAMA_MODEL -> settingsViewModel.setAiOllamaModel(value)
                            AiSettingField.DEEPSEEK_BASE_URL -> settingsViewModel.setAiDeepSeekBaseUrl(value)
                            AiSettingField.DEEPSEEK_MODEL -> settingsViewModel.setAiDeepSeekModel(value)
                            AiSettingField.DEEPSEEK_API_KEY -> settingsViewModel.setAiDeepSeekApiKey(value)
                            AiSettingField.REMOTE_TRANSCRIPTION_ADDRESS ->
                                settingsViewModel.setAiRemoteTranscriptionAddress(value)
                            AiSettingField.REMOTE_TRANSCRIPTION_PORT ->
                                settingsViewModel.setAiRemoteTranscriptionPort(value)
                            AiSettingField.REMOTE_WHISPER_MODEL -> settingsViewModel.setAiRemoteWhisperModel(value)
                            AiSettingField.REMOTE_WHISPER_TOKEN -> settingsViewModel.setAiRemoteWhisperToken(value)
                        }
                        aiSettingField = null
                    },
                )
            }
            if (customSleepDialog) {
                TextInputDialog(
                    title = "自定义睡眠定时",
                    initialValue = "30",
                    confirmText = "设置",
                    numeric = true,
                    onDismiss = { customSleepDialog = false },
                    onConfirm = { value ->
                        val minutes = value.toIntOrNull()?.coerceIn(1, 600) ?: 30
                        if (sleepTimerViewModel.setMinutes(minutes)) {
                            toast("睡眠定时已设置为 $minutes 分钟")
                        } else {
                            toast("播放器尚未连接")
                        }
                        customSleepDialog = false
                    },
                )
            }
        }
        }
    }
}
