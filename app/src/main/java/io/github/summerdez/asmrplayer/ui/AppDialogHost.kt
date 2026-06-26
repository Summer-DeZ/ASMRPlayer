package io.github.summerdez.asmrplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.summerdez.asmrplayer.data.ai.AiSubtitleGenerationService
import io.github.summerdez.asmrplayer.domain.PlaylistQueries
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleTaskState
import io.github.summerdez.asmrplayer.domain.model.Playlist
import io.github.summerdez.asmrplayer.domain.model.TrackItem
import io.github.summerdez.asmrplayer.presentation.DlsiteUiState
import io.github.summerdez.asmrplayer.presentation.DlsiteViewModel
import io.github.summerdez.asmrplayer.presentation.LibraryUiState
import io.github.summerdez.asmrplayer.presentation.LibraryViewModel
import io.github.summerdez.asmrplayer.presentation.PlaybackUiState
import io.github.summerdez.asmrplayer.presentation.PlaybackViewModel
import io.github.summerdez.asmrplayer.presentation.SettingsUiState
import io.github.summerdez.asmrplayer.presentation.SettingsViewModel
import io.github.summerdez.asmrplayer.presentation.SleepTimerViewModel
import io.github.summerdez.asmrplayer.ui.components.ConfirmDialog
import io.github.summerdez.asmrplayer.ui.components.InstallUpdateDialog
import io.github.summerdez.asmrplayer.ui.components.MoveTrackDialog
import io.github.summerdez.asmrplayer.ui.components.TextInputDialog
import io.github.summerdez.asmrplayer.ui.components.UpdateDetailsDialog
import io.github.summerdez.asmrplayer.ui.screens.AiSubtitleGenerationSheet
import io.github.summerdez.asmrplayer.ui.screens.DownloadManagerSheet
import io.github.summerdez.asmrplayer.ui.screens.QueueContent
import io.github.summerdez.asmrplayer.ui.screens.SubtitlePlayerScreen
import io.github.summerdez.asmrplayer.ui.theme.LocalAmberTokens

internal enum class AiSettingField {
    OLLAMA_BASE_URL,
    OLLAMA_MODEL,
    DEEPSEEK_BASE_URL,
    DEEPSEEK_MODEL,
    DEEPSEEK_API_KEY,
    REMOTE_TRANSCRIPTION_ADDRESS,
    REMOTE_TRANSCRIPTION_PORT,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppDialogHost(
    playerOpen: Boolean,
    onPlayerOpenChange: (Boolean) -> Unit,
    queueOpen: Boolean,
    onQueueOpenChange: (Boolean) -> Unit,
    downloadManagerOpen: Boolean,
    onDownloadManagerOpenChange: (Boolean) -> Unit,
    activeAiSubtitleTrackId: String?,
    onActiveAiSubtitleTrackIdChange: (String?) -> Unit,
    createPlaylistDialog: Boolean,
    onCreatePlaylistDialogChange: (Boolean) -> Unit,
    renamePlaylist: Playlist?,
    onRenamePlaylistChange: (Playlist?) -> Unit,
    deletePlaylist: Playlist?,
    onDeletePlaylistChange: (Playlist?) -> Unit,
    renameTrack: Pair<Playlist, TrackItem>?,
    onRenameTrackChange: (Pair<Playlist, TrackItem>?) -> Unit,
    deleteTrack: Triple<Playlist, TrackItem, Int>?,
    onDeleteTrackChange: (Triple<Playlist, TrackItem, Int>?) -> Unit,
    moveTrack: Pair<Playlist, TrackItem>?,
    onMoveTrackChange: (Pair<Playlist, TrackItem>?) -> Unit,
    aiSettingField: AiSettingField?,
    onAiSettingFieldChange: (AiSettingField?) -> Unit,
    customSleepDialog: Boolean,
    onCustomSleepDialogChange: (Boolean) -> Unit,
    libraryState: LibraryUiState,
    playbackState: PlaybackUiState,
    settingsState: SettingsUiState,
    dlsiteState: DlsiteUiState,
    aiSubtitleTasks: Map<String, AiSubtitleTaskState>,
    currentAiSubtitleTask: AiSubtitleTaskState?,
    currentTrackIsLocal: Boolean,
    libraryViewModel: LibraryViewModel,
    playbackViewModel: PlaybackViewModel,
    settingsViewModel: SettingsViewModel,
    sleepTimerViewModel: SleepTimerViewModel,
    dlsiteViewModel: DlsiteViewModel,
    startAiSubtitleForCurrentTrack: () -> Unit,
    openAiSubtitleProgressForCurrentTrack: () -> Unit,
    toast: (String) -> Unit,
) {
    val context = LocalContext.current
    val tokens = LocalAmberTokens.current
    val miniPlayerHeightPx = with(LocalDensity.current) { 64.dp.roundToPx() }
    val playerTransformEasing = CubicBezierEasing(0.20f, 0f, 0f, 1f)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = playerOpen,
            enter = expandVertically(
                animationSpec = tween(durationMillis = 460, easing = playerTransformEasing),
                expandFrom = Alignment.Bottom,
                initialHeight = { miniPlayerHeightPx },
                clip = true,
            ) + slideInVertically(
                animationSpec = tween(durationMillis = 460, easing = playerTransformEasing),
                initialOffsetY = { it / 5 },
            ),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = 340, easing = playerTransformEasing),
                shrinkTowards = Alignment.Bottom,
                targetHeight = { miniPlayerHeightPx },
                clip = true,
            ) + slideOutVertically(
                animationSpec = tween(durationMillis = 340, easing = playerTransformEasing),
                targetOffsetY = { it / 5 },
            ),
        ) {
            SubtitlePlayerScreen(
                playbackState = playbackState,
                aiSubtitleTask = currentAiSubtitleTask,
                currentTrackIsLocal = currentTrackIsLocal,
                onClose = { onPlayerOpenChange(false) },
                onPrevious = { playbackViewModel.playRelativeTrack(-1)?.let(toast) },
                onPlay = { playbackViewModel.onPlayClicked()?.let(toast) },
                onNext = { playbackViewModel.playRelativeTrack(1)?.let(toast) },
                onSeek = playbackViewModel::seekTo,
                onQueue = { onQueueOpenChange(true) },
                onGenerateAiSubtitle = startAiSubtitleForCurrentTrack,
                onOpenAiSubtitleProgress = openAiSubtitleProgressForCurrentTrack,
                onSleepTimer = { onCustomSleepDialogChange(true) },
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
    }

    if (queueOpen) {
        val queuePlaylist = PlaylistQueries.findById(libraryState.playlists, playbackState.playlistId)
            ?: libraryState.selectedPlaylist
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { onQueueOpenChange(false) },
            sheetState = sheetState,
            containerColor = tokens.sheet,
            dragHandle = null,
        ) {
            QueueContent(
                playlist = queuePlaylist,
                playbackState = playbackState,
                onDismissRequest = { onQueueOpenChange(false) },
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
            onDismissRequest = { onDownloadManagerOpenChange(false) },
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
                onDismissRequest = { onActiveAiSubtitleTrackIdChange(null) },
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
                    onRetranslate = {
                        ContextCompat.startForegroundService(
                            context,
                            AiSubtitleGenerationService.startIntent(
                                context = context,
                                target = task.target,
                                forceRetranslate = true,
                            ),
                        )
                    },
                    onCancel = {
                        context.startService(AiSubtitleGenerationService.cancelIntent(context, trackId))
                        onActiveAiSubtitleTrackIdChange(null)
                    },
                    onDismiss = { onActiveAiSubtitleTrackIdChange(null) },
                )
            }
        } else if (activeAiSubtitleTrackId != null) {
            onActiveAiSubtitleTrackIdChange(null)
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
            onDismiss = { onCreatePlaylistDialogChange(false) },
            onConfirm = { name ->
                libraryViewModel.createPlaylist(name)
                onCreatePlaylistDialogChange(false)
            },
        )
    }
    renamePlaylist?.let { playlist ->
        TextInputDialog(
            title = "重命名播放列表",
            initialValue = playlist.name,
            confirmText = "保存",
            onDismiss = { onRenamePlaylistChange(null) },
            onConfirm = { name ->
                libraryViewModel.renamePlaylist(playlist, name)
                onRenamePlaylistChange(null)
            },
        )
    }
    deletePlaylist?.let { playlist ->
        ConfirmDialog(
            title = "删除播放列表",
            message = "删除“${playlist.name}”及其中的曲目记录？",
            onDismiss = { onDeletePlaylistChange(null) },
            onConfirm = {
                playbackViewModel.onPlaylistDeleted(playlist.id)
                libraryViewModel.deletePlaylist(playlist)
                onDeletePlaylistChange(null)
            },
        )
    }
    renameTrack?.let { target ->
        TextInputDialog(
            title = "重命名曲目",
            initialValue = target.second.title,
            confirmText = "保存",
            onDismiss = { onRenameTrackChange(null) },
            onConfirm = { name ->
                libraryViewModel.renameTrack(target.first, target.second, name)
                playbackViewModel.onTrackRenamed(target.second.id, name)
                onRenameTrackChange(null)
            },
        )
    }
    deleteTrack?.let { target ->
        ConfirmDialog(
            title = "删除曲目",
            message = "从“${target.first.name}”移除“${target.second.title}”？",
            onDismiss = { onDeleteTrackChange(null) },
            onConfirm = {
                playbackViewModel.onTrackRemoved(target.first.id, target.third)
                libraryViewModel.removeTrack(target.first, target.second)
                onDeleteTrackChange(null)
            },
        )
    }
    moveTrack?.let { target ->
        MoveTrackDialog(
            sourcePlaylist = target.first,
            track = target.second,
            playlists = libraryState.playlists,
            onDismiss = { onMoveTrackChange(null) },
            onChoose = { targetPlaylist ->
                libraryViewModel.moveTrack(target.first, target.second, targetPlaylist)
                onMoveTrackChange(null)
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
            },
            initialValue = when (field) {
                AiSettingField.OLLAMA_BASE_URL -> aiSettings.ollamaBaseUrl
                AiSettingField.OLLAMA_MODEL -> aiSettings.ollamaModel
                AiSettingField.DEEPSEEK_BASE_URL -> aiSettings.deepSeekBaseUrl
                AiSettingField.DEEPSEEK_MODEL -> aiSettings.deepSeekModel
                AiSettingField.DEEPSEEK_API_KEY -> aiSettings.deepSeekApiKey
                AiSettingField.REMOTE_TRANSCRIPTION_ADDRESS -> aiSettings.remoteTranscriptionAddress
                AiSettingField.REMOTE_TRANSCRIPTION_PORT -> aiSettings.remoteTranscriptionPort
            },
            confirmText = "保存",
            numeric = field == AiSettingField.REMOTE_TRANSCRIPTION_PORT,
            required = when (field) {
                AiSettingField.REMOTE_TRANSCRIPTION_ADDRESS,
                AiSettingField.REMOTE_TRANSCRIPTION_PORT,
                AiSettingField.DEEPSEEK_API_KEY,
                -> false
                else -> true
            },
            message = "",
            onDismiss = { onAiSettingFieldChange(null) },
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
                }
                onAiSettingFieldChange(null)
            },
        )
    }
    if (customSleepDialog) {
        TextInputDialog(
            title = "自定义睡眠定时",
            initialValue = "30",
            confirmText = "设置",
            numeric = true,
            onDismiss = { onCustomSleepDialogChange(false) },
            onConfirm = { value ->
                val minutes = value.toIntOrNull()?.coerceIn(1, 600) ?: 30
                if (sleepTimerViewModel.setMinutes(minutes)) {
                    toast("睡眠定时已设置为 $minutes 分钟")
                } else {
                    toast("播放器尚未连接")
                }
                onCustomSleepDialogChange(false)
            },
        )
    }
}
