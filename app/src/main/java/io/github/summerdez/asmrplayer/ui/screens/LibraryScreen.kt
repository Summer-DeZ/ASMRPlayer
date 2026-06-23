package io.github.summerdez.asmrplayer.ui.screens

import io.github.summerdez.asmrplayer.R
import io.github.summerdez.asmrplayer.data.*
import io.github.summerdez.asmrplayer.data.remote.*
import io.github.summerdez.asmrplayer.data.download.*
import io.github.summerdez.asmrplayer.data.files.*
import io.github.summerdez.asmrplayer.domain.*
import io.github.summerdez.asmrplayer.domain.model.*
import io.github.summerdez.asmrplayer.playback.*
import io.github.summerdez.asmrplayer.presentation.*
import io.github.summerdez.asmrplayer.ui.*
import io.github.summerdez.asmrplayer.ui.activity.*
import io.github.summerdez.asmrplayer.ui.components.*
import io.github.summerdez.asmrplayer.ui.screens.*
import io.github.summerdez.asmrplayer.ui.theme.*
import io.github.summerdez.asmrplayer.ui.util.*
import io.github.summerdez.asmrplayer.di.*
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date
import kotlin.math.max

@Composable
fun LibraryTab(
    state: LibraryUiState,
    playbackState: PlaybackUiState,
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
    subtitleTasks: Map<String, AiSubtitleTaskState> = emptyMap(),
) {
    val listeningPlaylist = state.playlists.firstOrNull { it.id == playbackState.playlistId }
    val listeningIndex = playbackState.playlistIndex
    val listeningTrack = listeningPlaylist?.tracks?.getOrNull(listeningIndex)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (listeningPlaylist != null && listeningTrack != null) {
            item(key = "continue-listening") {
                ContinueListeningCard(
                    playlist = listeningPlaylist,
                    track = listeningTrack,
                    trackIndex = listeningIndex,
                    playbackState = playbackState,
                    onClick = { onTrackClicked(listeningPlaylist, listeningIndex) },
                    modifier = Modifier.padding(bottom = 26.dp),
                )
            }
        }
        item(key = "playlist-section") {
            SectionTitle(
                text = "播放列表",
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        items(state.playlists, key = { it.id }) { playlist ->
            val selected = state.selectedPlaylist?.id == playlist.id
            val expanded = selected && state.collapsedSelectedPlaylistId != playlist.id
            PlaylistRow(
                playlist = playlist,
                selected = selected,
                expanded = expanded,
                playbackState = playbackState,
                onPlaylistClicked = { onPlaylistClicked(playlist) },
                onCoverClicked = { onCoverClicked(playlist) },
                onRenamePlaylist = { onRenamePlaylist(playlist) },
                onDeletePlaylist = { onDeletePlaylist(playlist) },
                onTrackClicked = { index -> onTrackClicked(playlist, index) },
                onTrackSubtitleClicked = { track -> onTrackSubtitleClicked(playlist, track) },
                onGenerateSubtitle = { track -> onGenerateSubtitle(playlist, track) },
                onOpenSubtitleGeneration = onOpenSubtitleGeneration,
                onRenameTrack = { track -> onRenameTrack(playlist, track) },
                onDeleteTrack = { track, index -> onDeleteTrack(playlist, track, index) },
                onMoveTrack = { track -> onMoveTrack(playlist, track) },
                subtitleTasks = subtitleTasks,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val tokens = LocalAmberTokens.current
    Text(
        text = text,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = tokens.label2,
        modifier = modifier,
    )
}

@Composable
private fun ContinueListeningCard(
    playlist: Playlist,
    track: TrackItem,
    trackIndex: Int,
    playbackState: PlaybackUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val shape = RoundedCornerShape(24.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(154.dp)
            .clickable(onClick = onClick),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, tokens.separator),
        shadowElevation = 6.dp,
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(Brush.linearGradient(listOf(tokens.cardTop, tokens.cardBottom)))
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                CoverBox(playlist.coverUri, Modifier.size(98.dp))
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(
                        "继续收听",
                        color = tokens.accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        playlist.name,
                        color = tokens.label,
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 9.dp)) {
                        EqualizerIcon(Modifier.size(width = 24.dp, height = 18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "第 ${trackIndex + 1} 首 · ${formatDuration(track.durationMs)}",
                            color = tokens.label2,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Surface(
                    modifier = Modifier.size(70.dp),
                    shape = CircleShape,
                    color = tokens.accent,
                    shadowElevation = 8.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "继续播放",
                            tint = tokens.bg,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistRow(
    playlist: Playlist,
    selected: Boolean,
    expanded: Boolean,
    playbackState: PlaybackUiState,
    onPlaylistClicked: () -> Unit,
    onCoverClicked: () -> Unit,
    onRenamePlaylist: () -> Unit,
    onDeletePlaylist: () -> Unit,
    onTrackClicked: (Int) -> Unit,
    onTrackSubtitleClicked: (TrackItem) -> Unit,
    onGenerateSubtitle: (TrackItem) -> Unit,
    onOpenSubtitleGeneration: (String) -> Unit,
    onRenameTrack: (TrackItem) -> Unit,
    onDeleteTrack: (TrackItem, Int) -> Unit,
    onMoveTrack: (TrackItem) -> Unit,
    subtitleTasks: Map<String, AiSubtitleTaskState> = emptyMap(),
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val tokens = LocalAmberTokens.current
    val shape = RoundedCornerShape(22.dp)
    Surface(
        modifier = Modifier.padding(bottom = 12.dp),
        color = Color.Transparent,
        shape = shape,
        border = BorderStroke(1.dp, tokens.separator),
        shadowElevation = if (selected) 4.dp else 1.dp,
    ) {
        Column(
            Modifier
                .clip(shape)
                .background(Brush.linearGradient(listOf(tokens.cardTop, tokens.cardBottom)))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPlaylistClicked)
                    .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverBox(playlist.coverUri, Modifier.size(70.dp).clickable(onClick = onCoverClicked))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        playlist.name,
                        fontSize = 19.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = animateColorAsState(
                            targetValue = if (selected && expanded) tokens.accent else tokens.label,
                            animationSpec = tween(220),
                            label = "playlistName",
                        ).value,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${playlist.tracks.size} 首" + if (selected && playbackState.playlistId == playlist.id) " · 正在播放" else "",
                        color = tokens.label2,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "播放列表操作")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("更换封面") },
                            leadingIcon = { Icon(Icons.Default.MusicNote, null) },
                            onClick = {
                                menuExpanded = false
                                onCoverClicked()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = {
                                menuExpanded = false
                                onRenamePlaylist()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = {
                                menuExpanded = false
                                onDeletePlaylist()
                            },
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(260)) + fadeIn(animationSpec = tween(200)),
                exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(animationSpec = tween(150)),
            ) {
                Column {
                    HorizontalDivider(color = tokens.separator, modifier = Modifier.padding(start = 102.dp))
                    if (playlist.tracks.isEmpty()) {
                        Text(
                            "这个播放列表还没有音频",
                            color = tokens.label2,
                            modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 16.dp),
                        )
                    } else {
                        playlist.tracks.forEachIndexed { index, track ->
                            TrackRow(
                                track = track,
                                subtitle = formatDuration(track.durationMs),
                                active = playbackState.playlistId == playlist.id && playbackState.playlistIndex == index,
                                onClick = { onTrackClicked(index) },
                                onSubtitle = { onTrackSubtitleClicked(track) },
                                onGenerateSubtitle = { onGenerateSubtitle(track) },
                                onOpenSubtitleGeneration = { onOpenSubtitleGeneration(track.id) },
                                onRename = { onRenameTrack(track) },
                                onDelete = { onDeleteTrack(track, index) },
                                onMove = { onMoveTrack(track) },
                                aiSubtitleTask = subtitleTasks[track.id],
                                modifier = Modifier.padding(start = 6.dp, end = 4.dp),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun TrackRow(
    track: TrackItem,
    subtitle: String? = null,
    active: Boolean,
    onClick: () -> Unit,
    onSubtitle: () -> Unit,
    onGenerateSubtitle: () -> Unit = {},
    onOpenSubtitleGeneration: () -> Unit = {},
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit = {},
    aiSubtitleTask: AiSubtitleTaskState? = null,
    modifier: Modifier = Modifier,
    showArtwork: Boolean = false,
    showMenu: Boolean = true,
    showDragHandle: Boolean = false,
    elevated: Boolean = false,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val tokens = LocalAmberTokens.current
    val rowShape = RoundedCornerShape(18.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (elevated) 6.dp else 2.dp),
        color = if (active || elevated) tokens.accentSoft.copy(alpha = if (active) 0.62f else 0.28f) else Color.Transparent,
        shape = rowShape,
        border = if (active || elevated) BorderStroke(1.dp, tokens.separator) else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = if (showArtwork) 10.dp else 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showArtwork) {
                CoverBox("", Modifier.size(if (active || elevated) 58.dp else 52.dp))
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = if (showArtwork) 19.sp else 17.sp,
                    lineHeight = if (showArtwork) 24.sp else 22.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) tokens.accent else tokens.label,
                )
                Text(
                    aiSubtitleTask?.let { aiSubtitleStatusText(it) }
                        ?: subtitle
                        ?: if (track.subtitleTitle.isEmpty()) "未绑定字幕" else track.subtitleTitle,
                    color = if (aiSubtitleTask?.stage == AiSubtitleStage.FAILED) tokens.accent2 else tokens.label2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = if (showArtwork) 15.sp else 13.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
                if (aiSubtitleTask != null && aiSubtitleTask.stage != AiSubtitleStage.COMPLETED) {
                    Box(
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(tokens.label3.copy(alpha = 0.22f), RoundedCornerShape(3.dp)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(aiSubtitleTask.overallProgress.coerceIn(0f, 1f))
                                .background(
                                    if (aiSubtitleTask.stage == AiSubtitleStage.FAILED) tokens.accent2 else tokens.accent,
                                    RoundedCornerShape(3.dp),
                                ),
                        )
                    }
                }
            }
            if (active) {
                EqualizerIcon(Modifier.size(width = 24.dp, height = 18.dp))
                Spacer(Modifier.width(8.dp))
            }
            IconButton(onClick = onSubtitle) {
                Icon(Icons.Default.Subtitles, contentDescription = "字幕", tint = tokens.label3)
            }
            if (showDragHandle) {
                DragHandleIcon(Modifier.padding(end = 10.dp).size(width = 28.dp, height = 22.dp))
            }
            if (showMenu) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "曲目操作", tint = tokens.label3)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(if (aiSubtitleTask == null) "自动生成字幕" else "查看生成进度") },
                            leadingIcon = { Icon(Icons.Default.Subtitles, null) },
                            onClick = {
                                menuExpanded = false
                                if (aiSubtitleTask == null) {
                                    onGenerateSubtitle()
                                } else {
                                    onOpenSubtitleGeneration()
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("移动到...") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                            onClick = {
                                menuExpanded = false
                                onMove()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DragHandleIcon(modifier: Modifier = Modifier) {
    val tokens = LocalAmberTokens.current
    Canvas(modifier) {
        val stroke = 2.4.dp.toPx()
        val startX = size.width * 0.18f
        val endX = size.width * 0.82f
        val y1 = size.height * 0.36f
        val y2 = size.height * 0.64f
        drawLine(tokens.label3, Offset(startX, y1), Offset(endX, y1), stroke, StrokeCap.Round)
        drawLine(tokens.label3, Offset(startX, y2), Offset(endX, y2), stroke, StrokeCap.Round)
    }
}

@Composable
fun AiSubtitleGenerationSheet(
    task: AiSubtitleTaskState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onRegenerate: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 18.dp),
    ) {
        Box(
            Modifier
                .padding(bottom = 18.dp)
                .size(width = 44.dp, height = 5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(tokens.label3.copy(alpha = 0.55f))
                .align(Alignment.CenterHorizontally),
        )
        Row(verticalAlignment = Alignment.Top) {
            Text("生成字幕", color = tokens.label, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Surface(
                modifier = Modifier.size(48.dp).clickable(onClick = onDismiss),
                shape = CircleShape,
                color = tokens.label.copy(alpha = 0.08f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", tint = tokens.label2, modifier = Modifier.size(26.dp))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 22.dp, bottom = 20.dp)) {
            CoverBox("", Modifier.size(56.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.target.trackTitle,
                    color = tokens.label,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    task.target.contextTitle.ifBlank { "当前曲目" },
                    color = tokens.label2,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        AiSubtitleStageCard(
            index = 1,
            title = task.transcriptionTitle,
            active = task.stage == AiSubtitleStage.TRANSCRIBING,
            done = task.transcribeProgress >= 1f,
            failed = task.stage == AiSubtitleStage.FAILED && task.transcribeProgress < 1f,
            progress = task.transcribeProgress,
            meta = task.transcriptionMeta,
        )
        Spacer(Modifier.height(12.dp))
        AiSubtitleStageCard(
            index = 2,
            title = "上下文翻译",
            active = task.stage == AiSubtitleStage.TRANSLATING,
            done = task.translateProgress >= 1f,
            failed = task.stage == AiSubtitleStage.FAILED && task.transcribeProgress >= 1f,
            progress = task.translateProgress,
            meta = "逐句 JSON 对齐生成中文字幕",
        )
        if (task.error.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Surface(color = tokens.accent2Soft, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    task.error,
                    color = tokens.accent2,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
        if (task.previewLines.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Surface(color = tokens.card, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Icon(Icons.Default.Subtitles, contentDescription = null, tint = tokens.accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(10.dp))
                    task.previewLines.takeLast(2).forEach { line ->
                        Text(
                            line.translatedText.ifBlank { line.sourceText },
                            color = tokens.label,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            when (task.stage) {
                AiSubtitleStage.FAILED -> {
                    val label = if (task.transcribeProgress >= 1f) "重试翻译" else "重试生成"
                    PrimarySheetAction(label, onRetry)
                    Row(horizontalArrangement = Arrangement.spacedBy(46.dp), modifier = Modifier.padding(top = 16.dp)) {
                        TextButton(onClick = onRegenerate) {
                            Text("重新分片", color = tokens.label3)
                        }
                        TextButton(onClick = onCancel) {
                            Text("取消", color = tokens.accent2)
                        }
                    }
                }
                AiSubtitleStage.COMPLETED -> {
                    PrimarySheetAction("完成", onDismiss)
                    TextButton(onClick = onRegenerate, modifier = Modifier.padding(top = 10.dp)) {
                        Text("重新分片", color = tokens.label3)
                    }
                }
                else -> {
                    TextButton(onClick = if (task.stage == AiSubtitleStage.PAUSED) onResume else onPause) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                if (task.stage == AiSubtitleStage.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null,
                                tint = tokens.accent2,
                                modifier = Modifier.size(34.dp),
                            )
                            Text(if (task.stage == AiSubtitleStage.PAUSED) "继续" else "暂停", color = tokens.accent2, fontSize = 16.sp)
                        }
                    }
                    TextButton(onClick = onCancel) {
                        Text("取消", color = tokens.accent2)
                    }
                }
            }
        }
    }
}

@Composable
private fun AiSubtitleStageCard(
    index: Int,
    title: String,
    active: Boolean,
    done: Boolean,
    failed: Boolean,
    progress: Float,
    meta: String,
) {
    val tokens = LocalAmberTokens.current
    Surface(color = tokens.card, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            when {
                                failed -> tokens.accent2Soft
                                done -> tokens.accent
                                active -> tokens.accentSoft
                                else -> tokens.label3.copy(alpha = 0.12f)
                            },
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        when {
                            done -> "✓"
                            failed -> "!"
                            else -> index.toString()
                        },
                        color = when {
                            done -> tokens.bg
                            failed -> tokens.accent2
                            else -> tokens.accent
                        },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Text(title, color = tokens.label, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    when {
                        failed -> "失败"
                        active || done -> "${(progress * 100).toInt()}%"
                        else -> "等待"
                    },
                    color = when {
                        failed -> tokens.accent2
                        active || done -> tokens.accent
                        else -> tokens.label3
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Box(
                modifier = Modifier
                    .padding(top = 18.dp)
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(tokens.label3.copy(alpha = 0.22f), RoundedCornerShape(3.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .background(if (failed) tokens.accent2 else tokens.accent, RoundedCornerShape(3.dp)),
                )
            }
            Text(meta, color = tokens.label2, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun PrimarySheetAction(text: String, onClick: () -> Unit) {
    val tokens = LocalAmberTokens.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = tokens.accent,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = tokens.bg, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun aiSubtitleStatusText(task: AiSubtitleTaskState): String {
    return when (task.stage) {
        AiSubtitleStage.TRANSCRIBING -> "AI 字幕 · ${task.transcriptionTitle} ${(task.transcribeProgress * 100).toInt()}%"
        AiSubtitleStage.TRANSLATING -> "AI 字幕 · 翻译 ${(task.translateProgress * 100).toInt()}%"
        AiSubtitleStage.BINDING -> "AI 字幕 · 正在绑定"
        AiSubtitleStage.COMPLETED -> "AI 字幕已生成"
        AiSubtitleStage.PAUSED -> "AI 字幕已暂停"
        AiSubtitleStage.FAILED -> "AI 字幕失败"
        AiSubtitleStage.CANCELED -> "AI 字幕已取消"
    }
}
