package io.github.summerdez.asmrplayer.ui.screens

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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleStage
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleTaskState
import io.github.summerdez.asmrplayer.domain.model.Playlist
import io.github.summerdez.asmrplayer.domain.model.TrackItem
import io.github.summerdez.asmrplayer.domain.model.transcriptionDetailLabel
import io.github.summerdez.asmrplayer.presentation.LibraryUiState
import io.github.summerdez.asmrplayer.presentation.PlaybackUiState
import io.github.summerdez.asmrplayer.ui.components.CoverBox
import io.github.summerdez.asmrplayer.ui.theme.LocalAmberTokens
import io.github.summerdez.asmrplayer.ui.uiProbe
import io.github.summerdez.asmrplayer.ui.util.formatDuration
import java.text.DateFormat
import java.util.Date
import kotlin.math.max
import kotlin.math.sin

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
    var query by remember { mutableStateOf("") }
    val visiblePlaylists = remember(state.playlists, query) {
        val q = query.trim()
        if (q.isEmpty()) {
            state.playlists
        } else {
            state.playlists.filter { playlist ->
                playlist.name.contains(q, ignoreCase = true) ||
                playlist.tracks.any { it.title.contains(q, ignoreCase = true) }
            }
        }
    }
    val initialExpandedId = state.selectedPlaylist?.id
        ?: playbackState.playlistId.takeIf { it.isNotEmpty() }
        ?: state.playlists.firstOrNull()?.id
    var expandedPlaylistId by remember { mutableStateOf(initialExpandedId) }
    LaunchedEffect(state.selectedPlaylist?.id) {
        state.selectedPlaylist?.id?.let { expandedPlaylistId = it }
    }
    LaunchedEffect(playbackState.playlistId) {
        if (playbackState.playlistId.isNotEmpty()) {
            expandedPlaylistId = playbackState.playlistId
        }
    }
    LaunchedEffect(visiblePlaylists.map { it.id }) {
        if (expandedPlaylistId != null && visiblePlaylists.none { it.id == expandedPlaylistId }) {
            expandedPlaylistId = visiblePlaylists.firstOrNull()?.id
        }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .uiProbe("library.root", "资料库列表", "LibraryScreen.kt"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "library-search") {
            LibrarySearchField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
            )
        }
        if (state.playlists.isEmpty()) {
            item(key = "library-dlsite-empty") {
                LibraryDlsiteEmptyState()
            }
        } else {
            items(visiblePlaylists, key = { it.id }) { playlist ->
                val selected = state.selectedPlaylist?.id == playlist.id
                val expanded = expandedPlaylistId == playlist.id
                PlaylistRow(
                    playlist = playlist,
                    selected = selected,
                    expanded = expanded,
                    playbackState = playbackState,
                    onPlaylistClicked = {
                        expandedPlaylistId = if (expandedPlaylistId == playlist.id) null else playlist.id
                        onPlaylistClicked(playlist)
                    },
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
            if (visiblePlaylists.isEmpty()) {
                item(key = "library-empty-search") {
                    Text(
                        "没有找到匹配的作品",
                        color = LocalAmberTokens.current.label3,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 28.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LibrarySearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalAmberTokens.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .uiProbe("library.search", "资料库搜索框", "LibraryScreen.kt")
            .fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = tokens.label3, modifier = Modifier.size(18.dp))
        },
        placeholder = { Text("搜索作品名...", color = tokens.label3, fontSize = 14.sp) },
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedTextColor = tokens.label,
            unfocusedTextColor = tokens.label,
            cursorColor = tokens.accent,
            focusedBorderColor = tokens.separator,
            unfocusedBorderColor = tokens.separator,
            focusedContainerColor = tokens.card,
            unfocusedContainerColor = tokens.card,
        ),
    )
}

@Composable
private fun LibraryDlsiteEmptyState() {
    val tokens = LocalAmberTokens.current
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .uiProbe("library.empty-state", "资料库空态卡片", "LibraryScreen.kt")
            .padding(start = 6.dp, end = 6.dp, top = 10.dp),
        shape = RoundedCornerShape(28.dp),
        color = tokens.card,
        border = BorderStroke(1.dp, tokens.separator),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = tokens.accent2Soft,
                border = BorderStroke(1.dp, tokens.accent2.copy(alpha = 0.20f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.LibraryMusic,
                        contentDescription = null,
                        tint = tokens.accent2,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Text(
                "资料库还是空的",
                color = tokens.label,
                fontSize = 26.sp,
                lineHeight = 28.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
            )
            Text(
                "连接你的 DLsite 账户，把已购买的音声作品同步到这里；也可以继续用右上角菜单导入本地音频。",
                color = tokens.label2,
                fontSize = 14.5.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.90f),
            )
            Button(
                onClick = {
                    Toast.makeText(context, "请切换到底部 DLsite 页连接账户", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .padding(top = 6.dp)
                    .height(48.dp),
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("连接 DLsite", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LibraryAnimatedAudioLines(modifier: Modifier = Modifier) {
    val tokens = LocalAmberTokens.current
    val transition = rememberInfiniteTransition(label = "libraryAudioLines")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 820),
            repeatMode = RepeatMode.Restart,
        ),
        label = "libraryAudioPhase",
    )
    Canvas(modifier) {
        val barCount = 4
        val gap = size.width * 0.10f
        val barWidth = (size.width - gap * (barCount - 1)) / barCount
        repeat(barCount) { index ->
            val pulse = ((sin((phase * 6.28318f + index * 1.35f).toDouble()).toFloat() + 1f) / 2f)
            val height = size.height * (0.34f + pulse * 0.58f)
            val x = index * (barWidth + gap)
            drawRoundRect(
                color = tokens.accent,
                topLeft = Offset(x, size.height - height),
                size = Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val tokens = LocalAmberTokens.current
    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = tokens.label,
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
    val shape = RoundedCornerShape(28.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
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
                        fontSize = 26.sp,
                        lineHeight = 29.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        fontWeight = FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 9.dp)) {
                        LibraryAnimatedAudioLines(Modifier.size(width = 24.dp, height = 18.dp))
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
    val shape = RoundedCornerShape(18.dp)
    val workActive = playbackState.playlistId == playlist.id &&
        playbackState.playlistIndex in playlist.tracks.indices
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220),
        label = "playlistChevron",
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .uiProbe(
                id = "library.playlist-row:${playlist.id}",
                label = "播放列表卡片：${playlist.name}",
                sourceHint = "LibraryScreen.kt",
                metadata = mapOf(
                    "playlistId" to playlist.id,
                    "trackCount" to playlist.tracks.size.toString(),
                    "expanded" to expanded.toString(),
                ),
            ),
        color = Color.Transparent,
        shape = shape,
        border = BorderStroke(
            1.dp,
            when {
                workActive -> tokens.accent.copy(alpha = 0.35f)
                selected -> tokens.accent.copy(alpha = 0.18f)
                else -> tokens.separator
            },
        ),
        shadowElevation = if (workActive) 6.dp else 0.dp,
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
                    .padding(start = 14.dp, end = 8.dp, top = 13.dp, bottom = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverBox(playlist.coverUri, Modifier.size(52.dp).clickable(onClick = onCoverClicked))
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            playlist.name,
                            fontSize = 15.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = animateColorAsState(
                                targetValue = if (workActive) tokens.accent else tokens.label,
                                animationSpec = tween(220),
                                label = "playlistName",
                            ).value,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (workActive) {
                            Spacer(Modifier.width(7.dp))
                            LibraryAnimatedAudioLines(Modifier.size(width = 18.dp, height = 14.dp))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Text(
                            "播放列表",
                            color = tokens.label2,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                        Text(
                            " · ${playlist.tracks.size} 段",
                            color = tokens.label2,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                        if (workActive) {
                            Text(
                                " · 正在播放",
                                color = tokens.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "播放列表操作", tint = tokens.label3, modifier = Modifier.size(20.dp))
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
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .graphicsLayer { rotationZ = chevronRotation },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = tokens.label3, modifier = Modifier.size(18.dp))
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(260)) + fadeIn(animationSpec = tween(200)),
                exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(animationSpec = tween(150)),
            ) {
                Column {
                    HorizontalDivider(color = tokens.separator)
                    if (playlist.tracks.isEmpty()) {
                        Text(
                            "这个作品还没有音频段",
                            color = tokens.label2,
                            modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 16.dp),
                        )
                    } else {
                        playlist.tracks.forEachIndexed { index, track ->
                            LibrarySegmentRow(
                                track = track,
                                index = index,
                                active = playbackState.playlistId == playlist.id && playbackState.playlistIndex == index,
                                onClick = { onTrackClicked(index) },
                                onSubtitle = { onTrackSubtitleClicked(track) },
                                onGenerateSubtitle = { onGenerateSubtitle(track) },
                                onOpenSubtitleGeneration = { onOpenSubtitleGeneration(track.id) },
                                onRename = { onRenameTrack(track) },
                                onDelete = { onDeleteTrack(track, index) },
                                onMove = { onMoveTrack(track) },
                                aiSubtitleTask = subtitleTasks[track.id],
                                isLast = index == playlist.tracks.lastIndex,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySegmentRow(
    track: TrackItem,
    index: Int,
    active: Boolean,
    onClick: () -> Unit,
    onSubtitle: () -> Unit,
    onGenerateSubtitle: () -> Unit,
    onOpenSubtitleGeneration: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    aiSubtitleTask: AiSubtitleTaskState?,
    isLast: Boolean,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val tokens = LocalAmberTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .uiProbe(
                id = "library.segment-row:${track.id}",
                label = "展开曲目行：${track.title}",
                sourceHint = "LibraryScreen.kt",
                metadata = mapOf(
                    "trackId" to track.id,
                    "index" to index.toString(),
                    "active" to active.toString(),
                ),
            )
            .background(if (active) tokens.accentSoft.copy(alpha = 0.38f) else Color.Transparent),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 18.dp, end = 6.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(26.dp), contentAlignment = Alignment.Center) {
                if (active) {
                    LibraryAnimatedAudioLines(Modifier.size(width = 17.dp, height = 14.dp))
                } else {
                    Text(
                        "%02d".format(index + 1),
                        color = tokens.label3,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.5.sp,
                    lineHeight = 20.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) tokens.accent else tokens.label,
                )
                aiSubtitleTask?.let { task ->
                    Text(
                        aiSubtitleStatusText(task),
                        color = if (task.stage == AiSubtitleStage.FAILED) tokens.accent2 else tokens.label3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                formatDuration(track.durationMs),
                color = tokens.label3,
                fontSize = 12.sp,
                maxLines = 1,
            )
            IconButton(onClick = onSubtitle, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Subtitles, contentDescription = "字幕", tint = tokens.label3, modifier = Modifier.size(18.dp))
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "曲目操作", tint = tokens.label3, modifier = Modifier.size(18.dp))
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
        if (!isLast) {
            HorizontalDivider(color = tokens.separator, modifier = Modifier.padding(start = 57.dp))
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
    elevated: Boolean = false,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val tokens = LocalAmberTokens.current
    val rowShape = RoundedCornerShape(18.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .uiProbe(
                id = "library.track-row:${track.id}",
                label = "曲目行：${track.title}",
                sourceHint = "LibraryScreen.kt",
                metadata = mapOf(
                    "trackId" to track.id,
                    "active" to active.toString(),
                ),
            )
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
                LibraryAnimatedAudioLines(Modifier.size(width = 24.dp, height = 18.dp))
                Spacer(Modifier.width(8.dp))
            }
            IconButton(onClick = onSubtitle) {
                Icon(Icons.Default.Subtitles, contentDescription = "字幕", tint = tokens.label3)
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
            .uiProbe(
                id = "ai-subtitle.sheet:${task.target.trackId}",
                label = "AI 字幕生成弹层：${task.target.trackTitle}",
                sourceHint = "LibraryScreen.kt",
                metadata = mapOf(
                    "trackId" to task.target.trackId,
                    "playlistId" to task.target.playlistId,
                    "stage" to task.stage.name,
                    "transcribePercent" to (task.transcribeProgress * 100f).toInt().toString(),
                    "translatePercent" to (task.translateProgress * 100f).toInt().toString(),
                ),
            )
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
                modifier = Modifier
                    .size(48.dp)
                    .uiProbe("ai-subtitle.close", "关闭 AI 字幕生成弹层", "LibraryScreen.kt")
                    .clickable(onClick = onDismiss),
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
            detail = task.transcriptionDetailLabel(),
            active = task.stage == AiSubtitleStage.TRANSCRIBING,
            done = task.transcribeProgress >= 1f,
            failed = task.stage == AiSubtitleStage.FAILED && task.transcribeProgress < 1f,
            progress = task.transcribeProgress,
        )
        Spacer(Modifier.height(12.dp))
        AiSubtitleStageCard(
            index = 2,
            title = "上下文翻译",
            active = task.stage == AiSubtitleStage.TRANSLATING,
            done = task.translateProgress >= 1f,
            failed = task.stage == AiSubtitleStage.FAILED && task.transcribeProgress >= 1f,
            progress = task.translateProgress,
        )
        if (task.error.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Surface(
                color = tokens.accent2Soft,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .uiProbe(
                        id = "ai-subtitle.error",
                        label = "AI 字幕错误提示",
                        sourceHint = "LibraryScreen.kt",
                        metadata = mapOf("message" to task.error),
                    ),
            ) {
                Text(
                    task.error,
                    color = tokens.accent2,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
        if (task.warning.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Surface(
                color = tokens.accentSoft,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .uiProbe(
                        id = "ai-subtitle.warning",
                        label = "AI 字幕警告提示",
                        sourceHint = "LibraryScreen.kt",
                        metadata = mapOf("message" to task.warning),
                    ),
            ) {
                Text(
                    task.warning,
                    color = tokens.accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
        if (task.previewLines.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Surface(
                color = tokens.card,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .uiProbe(
                        id = "ai-subtitle.preview",
                        label = "AI 字幕预览卡片",
                        sourceHint = "LibraryScreen.kt",
                        metadata = mapOf("lineCount" to task.previewLines.size.toString()),
                    ),
            ) {
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
                        TextButton(
                            onClick = onRegenerate,
                            modifier = Modifier.uiProbe("ai-subtitle.regenerate", "AI 字幕重新分片按钮", "LibraryScreen.kt"),
                        ) {
                            Text("重新分片", color = tokens.label3)
                        }
                        TextButton(
                            onClick = onCancel,
                            modifier = Modifier.uiProbe("ai-subtitle.cancel", "取消 AI 字幕生成按钮", "LibraryScreen.kt"),
                        ) {
                            Text("取消", color = tokens.accent2)
                        }
                    }
                }
                AiSubtitleStage.COMPLETED -> {
                    PrimarySheetAction("完成", onDismiss)
                    TextButton(
                        onClick = onRegenerate,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .uiProbe("ai-subtitle.regenerate", "AI 字幕重新分片按钮", "LibraryScreen.kt"),
                    ) {
                        Text("重新分片", color = tokens.label3)
                    }
                }
                else -> {
                    TextButton(
                        onClick = if (task.stage == AiSubtitleStage.PAUSED) onResume else onPause,
                        modifier = Modifier.uiProbe(
                            id = "ai-subtitle.pause-resume",
                            label = if (task.stage == AiSubtitleStage.PAUSED) "继续 AI 字幕生成按钮" else "暂停 AI 字幕生成按钮",
                            sourceHint = "LibraryScreen.kt",
                            metadata = mapOf("stage" to task.stage.name),
                        ),
                    ) {
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
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.uiProbe("ai-subtitle.cancel", "取消 AI 字幕生成按钮", "LibraryScreen.kt"),
                    ) {
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
    detail: String = "",
    active: Boolean,
    done: Boolean,
    failed: Boolean,
    progress: Float,
) {
    val tokens = LocalAmberTokens.current
    Surface(
        color = tokens.card,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .uiProbe(
                id = "ai-subtitle.stage-card:$index",
                label = "AI 字幕阶段卡：$title",
                sourceHint = "LibraryScreen.kt",
                metadata = mapOf(
                    "index" to index.toString(),
                    "title" to title,
                    "detail" to detail,
                    "active" to active.toString(),
                    "done" to done.toString(),
                    "failed" to failed.toString(),
                    "progressPercent" to (progress * 100f).toInt().toString(),
                ),
            ),
    ) {
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
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = tokens.label,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (detail.isNotBlank()) {
                        Text(
                            detail,
                            color = tokens.label2,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
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
            .uiProbe(
                id = "sheet.primary-action:$text",
                label = "弹层主按钮：$text",
                sourceHint = "LibraryScreen.kt",
            )
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
        AiSubtitleStage.TRANSCRIBING -> {
            val detail = task.transcriptionDetailLabel()
            if (detail.isBlank()) {
                "AI 字幕 · ${task.transcriptionTitle} ${(task.transcribeProgress * 100).toInt()}%"
            } else {
                "AI 字幕 · $detail"
            }
        }
        AiSubtitleStage.TRANSLATING -> "AI 字幕 · 翻译 ${(task.translateProgress * 100).toInt()}%"
        AiSubtitleStage.BINDING -> "AI 字幕 · 正在绑定"
        AiSubtitleStage.COMPLETED -> if (task.warning.isBlank()) "AI 字幕已生成" else "AI 字幕已生成 · 需检查片假名"
        AiSubtitleStage.PAUSED -> "AI 字幕已暂停"
        AiSubtitleStage.FAILED -> "AI 字幕失败"
        AiSubtitleStage.CANCELED -> "AI 字幕已取消"
    }
}
