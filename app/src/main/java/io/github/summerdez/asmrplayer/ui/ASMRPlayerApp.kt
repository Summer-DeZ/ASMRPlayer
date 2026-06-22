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
import io.github.summerdez.asmrplayer.data.files.DocumentFiles
import io.github.summerdez.asmrplayer.data.ai.AiSubtitleGenerationService
import io.github.summerdez.asmrplayer.data.ai.AiSubtitleTaskStateBus
import io.github.summerdez.asmrplayer.domain.PlaylistQueries
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleStage
import io.github.summerdez.asmrplayer.domain.model.Playlist
import io.github.summerdez.asmrplayer.domain.model.SubtitleGenerationTarget
import io.github.summerdez.asmrplayer.domain.model.TrackItem
import io.github.summerdez.asmrplayer.presentation.DlsiteEvent
import io.github.summerdez.asmrplayer.presentation.DlsiteViewModel
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
import io.github.summerdez.asmrplayer.ui.screens.DownloadContentsSheet
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
    val aiSubtitleTasks by AiSubtitleTaskStateBus.tasks.collectAsStateWithLifecycle()
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
        val count = libraryViewModel.addAudioUris(context, DocumentFiles.urisFromResult(result.data))
        toast(if (count == 0) "未选择音频" else "已添加 $count 首音频")
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val uri = data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) {
            toast("未选择文件夹")
            return@rememberLauncherForActivityResult
        }
        val importResult = libraryViewModel.importFolder(context, data, uri)
        toast(
            if (importResult.audioCount == 0) {
                "文件夹中没有可导入的音频"
            } else {
                "已导入 ${importResult.audioCount} 首音频，匹配 ${importResult.subtitleCount} 个字幕"
            },
        )
    }
    val coverLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            DocumentFiles.persistReadPermission(context, uri)
            libraryViewModel.handleCoverUri(uri)
            toast("封面已设置")
        } else {
            libraryViewModel.clearCoverPicker()
        }
    }
    val subtitleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            val binding = libraryViewModel.handleSubtitleUri(context, uri)
            if (binding != null) {
                playbackViewModel.setSubtitleForCurrentTrack(binding.subtitleUri, binding.subtitleTitle, binding.trackId)
                toast("字幕已绑定")
            }
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
        Box(Modifier.fillMaxSize().background(pageBackground)) {
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
                                onAiEngineSelected = settingsViewModel::setAiTranslationEngine,
                                onEditAiOllamaBaseUrl = { aiSettingField = AiSettingField.OLLAMA_BASE_URL },
                                onEditAiOllamaModel = { aiSettingField = AiSettingField.OLLAMA_MODEL },
                                onEditAiDeepSeekBaseUrl = { aiSettingField = AiSettingField.DEEPSEEK_BASE_URL },
                                onEditAiDeepSeekModel = { aiSettingField = AiSettingField.DEEPSEEK_MODEL },
                                onEditAiDeepSeekApiKey = { aiSettingField = AiSettingField.DEEPSEEK_API_KEY },
                                onAiWhisperModelSelected = settingsViewModel::setAiWhisperModelId,
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
                    onClose = { playerOpen = false },
                    onPrevious = { playbackViewModel.playRelativeTrack(-1)?.let(toast) },
                    onPlay = { playbackViewModel.onPlayClicked()?.let(toast) },
                    onNext = { playbackViewModel.playRelativeTrack(1)?.let(toast) },
                    onSeek = playbackViewModel::seekTo,
                    onOverlay = onToggleOverlay,
                    onQueue = { queueOpen = true },
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
                        onTrackClicked = { playlist, index ->
                            libraryViewModel.selectPlaylist(playlist)
                            playbackViewModel.playPlaylistTrack(playlist, index)
                        },
                    )
                }
            }

            dlsiteState.optionWork?.let { optionWork ->
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = dlsiteViewModel::dismissDownloadOptions,
                    sheetState = sheetState,
                    containerColor = tokens.sheet,
                    dragHandle = null,
                ) {
                    DownloadContentsSheet(
                        work = optionWork,
                        options = dlsiteState.downloadOptions,
                        contents = dlsiteState.contentsByWork[optionWork.workId].orEmpty(),
                        onDismiss = dlsiteViewModel::dismissDownloadOptions,
                        onStart = { options -> dlsiteViewModel.startDownload(optionWork, options) },
                        onDeleteContent = { content ->
                            dlsiteViewModel.deleteContent(optionWork, content) {}
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
                        val result = libraryViewModel.moveTrack(target.first, target.second, targetPlaylist)
                        playbackViewModel.onTrackMoved(result)
                        moveTrack = null
                        toast("已移动到 ${targetPlaylist.name}")
                    },
                )
            }
            aiSettingField?.let { field ->
                val aiSettings = settingsState.aiSubtitleSettings
                TextInputDialog(
                    title = when (field) {
                        AiSettingField.OLLAMA_BASE_URL -> "Ollama 接口地址"
                        AiSettingField.OLLAMA_MODEL -> "Ollama 模型"
                        AiSettingField.DEEPSEEK_BASE_URL -> "DeepSeek 接口地址"
                        AiSettingField.DEEPSEEK_MODEL -> "DeepSeek 模型"
                        AiSettingField.DEEPSEEK_API_KEY -> "DeepSeek API Key"
                    },
                    initialValue = when (field) {
                        AiSettingField.OLLAMA_BASE_URL -> aiSettings.ollamaBaseUrl
                        AiSettingField.OLLAMA_MODEL -> aiSettings.ollamaModel
                        AiSettingField.DEEPSEEK_BASE_URL -> aiSettings.deepSeekBaseUrl
                        AiSettingField.DEEPSEEK_MODEL -> aiSettings.deepSeekModel
                        AiSettingField.DEEPSEEK_API_KEY -> aiSettings.deepSeekApiKey
                    },
                    confirmText = "保存",
                    onDismiss = { aiSettingField = null },
                    onConfirm = { value ->
                        when (field) {
                            AiSettingField.OLLAMA_BASE_URL -> settingsViewModel.setAiOllamaBaseUrl(value)
                            AiSettingField.OLLAMA_MODEL -> settingsViewModel.setAiOllamaModel(value)
                            AiSettingField.DEEPSEEK_BASE_URL -> settingsViewModel.setAiDeepSeekBaseUrl(value)
                            AiSettingField.DEEPSEEK_MODEL -> settingsViewModel.setAiDeepSeekModel(value)
                            AiSettingField.DEEPSEEK_API_KEY -> settingsViewModel.setAiDeepSeekApiKey(value)
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
