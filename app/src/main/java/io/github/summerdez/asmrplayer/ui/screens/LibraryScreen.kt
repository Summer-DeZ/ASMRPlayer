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
    val tokens = LocalAmberTokens.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Text(
                "播放列表",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = tokens.label,
                modifier = Modifier.padding(bottom = 8.dp),
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
    Surface(
        color = Color.Transparent,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPlaylistClicked)
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverBox(playlist.coverUri, Modifier.size(54.dp).clickable(onClick = onCoverClicked))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        playlist.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = animateColorAsState(
                            targetValue = if (selected && expanded) tokens.accent else tokens.label,
                            animationSpec = tween(220),
                            label = "playlistName",
                        ).value,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "播放列表 · ${playlist.tracks.size} 首",
                        color = tokens.label2,
                        fontSize = 13.sp,
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
            HorizontalDivider(color = tokens.separator, modifier = Modifier.padding(start = 68.dp))
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(260)) + fadeIn(animationSpec = tween(200)),
                exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(animationSpec = tween(150)),
            ) {
                Column {
                    if (playlist.tracks.isEmpty()) {
                        Text(
                            "这个播放列表还没有音频",
                            color = tokens.label2,
                            modifier = Modifier.padding(start = 14.dp, top = 16.dp, bottom = 16.dp),
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
                            )
                        }
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
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val tokens = LocalAmberTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 4.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = if (active) tokens.accent else tokens.label,
            )
            Text(
                aiSubtitleTask?.let { aiSubtitleStatusText(it) }
                    ?: subtitle
                    ?: if (track.subtitleTitle.isEmpty()) "未绑定字幕" else track.subtitleTitle,
                color = if (aiSubtitleTask?.stage == AiSubtitleStage.FAILED) tokens.accent2 else tokens.label2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
            )
            if (aiSubtitleTask != null && aiSubtitleTask.stage != AiSubtitleStage.COMPLETED) {
                Box(
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(tokens.label3.copy(alpha = 0.22f), RoundedCornerShape(3.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(aiSubtitleTask.overallProgress)
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
            Spacer(Modifier.width(6.dp))
        }
        IconButton(onClick = onSubtitle) {
            Icon(Icons.Default.Subtitles, contentDescription = "字幕", tint = tokens.label3)
        }
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

@Composable
fun AiSubtitleGenerationSheet(
    task: AiSubtitleTaskState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("生成字幕", color = tokens.label, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(task.target.trackTitle, color = tokens.label2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onDismiss) {
                Text("关闭", color = tokens.accent)
            }
        }
        Spacer(Modifier.height(14.dp))
        AiSubtitleStageCard(
            index = 1,
            title = "本机转写（Whisper）",
            active = task.stage == AiSubtitleStage.TRANSCRIBING,
            done = task.transcribeProgress >= 1f,
            progress = task.transcribeProgress,
            meta = "音频不上传",
        )
        if (task.backendNotice.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(task.backendNotice, color = tokens.label2, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        AiSubtitleStageCard(
            index = 2,
            title = "上下文翻译",
            active = task.stage == AiSubtitleStage.TRANSLATING,
            done = task.translateProgress >= 1f,
            progress = task.translateProgress,
            meta = "逐句 JSON 对齐生成中文字幕",
        )
        if (task.error.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(task.error, color = tokens.accent2, fontSize = 13.sp)
        }
        if (task.previewLines.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Surface(color = tokens.card, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
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
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            when (task.stage) {
                AiSubtitleStage.FAILED -> {
                    TextButton(onClick = onRetry) {
                        val label = if (task.transcribeProgress >= 1f) "重试翻译" else "重试生成"
                        Text(label, color = tokens.accent)
                    }
                    TextButton(onClick = onCancel) {
                        Text("取消", color = tokens.accent2)
                    }
                }
                AiSubtitleStage.COMPLETED -> {
                    TextButton(onClick = onDismiss) {
                        Text("完成", color = tokens.accent)
                    }
                }
                else -> {
                    TextButton(onClick = if (task.stage == AiSubtitleStage.PAUSED) onResume else onPause) {
                        Text(if (task.stage == AiSubtitleStage.PAUSED) "继续生成" else "暂停生成", color = tokens.accent)
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
    progress: Float,
    meta: String,
) {
    val tokens = LocalAmberTokens.current
    Surface(color = tokens.card, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(
                            if (done) tokens.accent else tokens.label3.copy(alpha = 0.22f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (done) "✓" else index.toString(), color = if (done) tokens.bg else tokens.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(11.dp))
                Text(title, color = tokens.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(if (active || done) "${(progress * 100).toInt()}%" else "等待", color = if (active || done) tokens.accent else tokens.label3, fontSize = 13.sp)
            }
            Box(
                modifier = Modifier
                    .padding(top = 11.dp)
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(tokens.label3.copy(alpha = 0.22f), RoundedCornerShape(3.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .background(tokens.accent, RoundedCornerShape(3.dp)),
                )
            }
            Text(meta, color = tokens.label2, fontSize = 13.sp, modifier = Modifier.padding(top = 9.dp))
        }
    }
}

private fun aiSubtitleStatusText(task: AiSubtitleTaskState): String {
    return when (task.stage) {
        AiSubtitleStage.TRANSCRIBING -> "AI 字幕 · 本机转写 ${(task.transcribeProgress * 100).toInt()}%"
        AiSubtitleStage.TRANSLATING -> "AI 字幕 · 翻译 ${(task.translateProgress * 100).toInt()}%"
        AiSubtitleStage.BINDING -> "AI 字幕 · 正在绑定"
        AiSubtitleStage.COMPLETED -> "AI 字幕已生成"
        AiSubtitleStage.PAUSED -> "AI 字幕已暂停"
        AiSubtitleStage.FAILED -> "AI 字幕失败"
        AiSubtitleStage.CANCELED -> "AI 字幕已取消"
    }
}
