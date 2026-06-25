package io.github.summerdez.asmrplayer.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import io.github.summerdez.asmrplayer.domain.model.*
import io.github.summerdez.asmrplayer.presentation.PlaybackUiState
import io.github.summerdez.asmrplayer.ui.components.CoverBox
import io.github.summerdez.asmrplayer.ui.theme.LocalAmberTokens
import io.github.summerdez.asmrplayer.ui.uiProbe
import io.github.summerdez.asmrplayer.ui.util.formatDuration
import kotlin.math.sin

@Composable
internal fun LibrarySearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalAmberTokens.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.uiProbe("library.search", "资料库搜索框", "LibraryRows.kt").fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
        leadingIcon = { Icon(Icons.Default.Search, null, tint = tokens.label3, modifier = Modifier.size(18.dp)) },
        placeholder = { Text("搜索作品名...", color = tokens.label3, fontSize = 14.sp) },
        colors = OutlinedTextFieldDefaults.colors(
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
internal fun LibraryDlsiteEmptyState() {
    val tokens = LocalAmberTokens.current
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth().uiProbe("library.empty-state", "资料库空态卡片", "LibraryRows.kt").padding(start = 6.dp, end = 6.dp, top = 10.dp),
        shape = RoundedCornerShape(28.dp),
        color = tokens.card,
        border = BorderStroke(1.dp, tokens.separator),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(Modifier.size(72.dp), CircleShape, tokens.accent2Soft, border = BorderStroke(1.dp, tokens.accent2.copy(alpha = 0.20f))) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.LibraryMusic, null, tint = tokens.accent2, modifier = Modifier.size(32.dp)) }
            }
            Text(
                "资料库还是空的",
                color = tokens.label,
                fontSize = 26.sp,
                lineHeight = 28.sp,
                fontFamily = FontFamily.Serif,
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
                onClick = { Toast.makeText(context, "请切换到底部 DLsite 页连接账户", Toast.LENGTH_SHORT).show() },
                modifier = Modifier.padding(top = 6.dp).height(48.dp),
            ) {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
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
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(820), RepeatMode.Restart), label = "libraryAudioPhase")
    Canvas(modifier) {
        val barCount = 4
        val gap = size.width * 0.10f
        val barWidth = (size.width - gap * (barCount - 1)) / barCount
        repeat(barCount) { index ->
            val pulse = ((sin((phase * 6.28318f + index * 1.35f).toDouble()).toFloat() + 1f) / 2f)
            val height = size.height * (0.34f + pulse * 0.58f)
            val x = index * (barWidth + gap)
            drawRoundRect(tokens.accent, Offset(x, size.height - height), Size(barWidth, height), androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f))
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text = text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LocalAmberTokens.current.label, modifier = modifier)
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
    Surface(modifier.fillMaxWidth().height(150.dp).clickable(onClick = onClick), shape, Color.Transparent, border = BorderStroke(1.dp, tokens.separator), shadowElevation = 6.dp) {
        Box(Modifier.clip(shape).background(Brush.linearGradient(listOf(tokens.cardTop, tokens.cardBottom))).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                CoverBox(playlist.coverUri, Modifier.size(98.dp))
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text("继续收听", color = tokens.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        playlist.name,
                        color = tokens.label,
                        fontSize = 26.sp,
                        lineHeight = 29.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 9.dp)) {
                        LibraryAnimatedAudioLines(Modifier.size(width = 24.dp, height = 18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("第 ${trackIndex + 1} 首 · ${formatDuration(track.durationMs)}", color = tokens.label2, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Surface(Modifier.size(70.dp), CircleShape, tokens.accent, shadowElevation = 8.dp) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "继续播放", tint = tokens.bg, modifier = Modifier.size(36.dp))
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
    val workActive = playbackState.playlistId == playlist.id && playbackState.playlistIndex in playlist.tracks.indices
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, tween(220), label = "playlistChevron")
    Surface(
        modifier = Modifier.fillMaxWidth().uiProbe(
            id = "library.playlist-row:${playlist.id}",
            label = "播放列表卡片：${playlist.name}",
            sourceHint = "LibraryRows.kt",
            metadata = mapOf("playlistId" to playlist.id, "trackCount" to playlist.tracks.size.toString(), "expanded" to expanded.toString()),
        ),
        color = Color.Transparent,
        shape = shape,
        border = BorderStroke(1.dp, when { workActive -> tokens.accent.copy(alpha = 0.35f); selected -> tokens.accent.copy(alpha = 0.18f); else -> tokens.separator }),
        shadowElevation = if (workActive) 6.dp else 0.dp,
    ) {
        Column(Modifier.clip(shape).background(Brush.linearGradient(listOf(tokens.cardTop, tokens.cardBottom)))) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onPlaylistClicked).padding(start = 14.dp, end = 8.dp, top = 13.dp, bottom = 13.dp),
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
                            color = animateColorAsState(if (workActive) tokens.accent else tokens.label, tween(220), label = "playlistName").value,
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
                        Text("播放列表", color = tokens.label2, fontSize = 12.sp, maxLines = 1)
                        Text(" · ${playlist.tracks.size} 段", color = tokens.label2, fontSize = 12.sp, maxLines = 1)
                        if (workActive) Text(" · 正在播放", color = tokens.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.MoreVert, "播放列表操作", tint = tokens.label3, modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(menuExpanded, { menuExpanded = false }) {
                        DropdownMenuItem({ Text("更换封面") }, leadingIcon = { Icon(Icons.Default.MusicNote, null) }, onClick = { menuExpanded = false; onCoverClicked() })
                        DropdownMenuItem({ Text("重命名") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuExpanded = false; onRenamePlaylist() })
                        DropdownMenuItem({ Text("删除") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menuExpanded = false; onDeletePlaylist() })
                    }
                }
                Box(Modifier.size(26.dp).graphicsLayer { rotationZ = chevronRotation }, Alignment.Center) {
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = tokens.label3, modifier = Modifier.size(18.dp))
                }
            }
            AnimatedVisibility(
                expanded,
                enter = expandVertically(tween(260)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(220)) + fadeOut(tween(150)),
            ) {
                Column {
                    HorizontalDivider(color = tokens.separator)
                    if (playlist.tracks.isEmpty()) {
                        Text("这个作品还没有音频段", color = tokens.label2, modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 16.dp))
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
    val tokens = LocalAmberTokens.current
    Column(
        Modifier.fillMaxWidth().uiProbe(
            id = "library.segment-row:${track.id}",
            label = "展开曲目行：${track.title}",
            sourceHint = "LibraryRows.kt",
            metadata = mapOf("trackId" to track.id, "index" to index.toString(), "active" to active.toString()),
        ).background(if (active) tokens.accentSoft.copy(alpha = 0.38f) else Color.Transparent),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 18.dp, end = 6.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(26.dp), Alignment.Center) {
                if (active) LibraryAnimatedAudioLines(Modifier.size(width = 17.dp, height = 14.dp)) else Text("%02d".format(index + 1), color = tokens.label3, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.5.sp, lineHeight = 20.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, color = if (active) tokens.accent else tokens.label)
                aiSubtitleTask?.let { task ->
                    Text(aiSubtitleStatusText(task), color = if (task.stage == AiSubtitleStage.FAILED) tokens.accent2 else tokens.label3, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.5.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(formatDuration(track.durationMs), color = tokens.label3, fontSize = 12.sp, maxLines = 1)
            IconButton(onSubtitle, Modifier.size(36.dp)) { Icon(Icons.Default.Subtitles, "字幕", tint = tokens.label3, modifier = Modifier.size(18.dp)) }
            TrackActionMenu(aiSubtitleTask, onGenerateSubtitle, onOpenSubtitleGeneration, onRename, onDelete, onMove, Modifier.size(36.dp), 18.dp)
        }
        if (!isLast) HorizontalDivider(color = tokens.separator, modifier = Modifier.padding(start = 57.dp))
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
    val tokens = LocalAmberTokens.current
    val rowShape = RoundedCornerShape(18.dp)
    Surface(
        modifier = modifier.fillMaxWidth().uiProbe(
            id = "library.track-row:${track.id}",
            label = "曲目行：${track.title}",
            sourceHint = "LibraryRows.kt",
            metadata = mapOf("trackId" to track.id, "active" to active.toString()),
        ).padding(vertical = if (elevated) 6.dp else 2.dp),
        color = if (active || elevated) tokens.accentSoft.copy(alpha = if (active) 0.62f else 0.28f) else Color.Transparent,
        shape = rowShape,
        border = if (active || elevated) BorderStroke(1.dp, tokens.separator) else null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = if (showArtwork) 10.dp else 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showArtwork) {
                CoverBox("", Modifier.size(if (active || elevated) 58.dp else 52.dp))
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = if (showArtwork) 19.sp else 17.sp, lineHeight = if (showArtwork) 24.sp else 22.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, color = if (active) tokens.accent else tokens.label)
                Text(
                    aiSubtitleTask?.let { aiSubtitleStatusText(it) } ?: subtitle ?: if (track.subtitleTitle.isEmpty()) "未绑定字幕" else track.subtitleTitle,
                    color = if (aiSubtitleTask?.stage == AiSubtitleStage.FAILED) tokens.accent2 else tokens.label2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = if (showArtwork) 15.sp else 13.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
                if (aiSubtitleTask != null && aiSubtitleTask.stage != AiSubtitleStage.COMPLETED) ProgressRail(aiSubtitleTask.overallProgress, aiSubtitleTask.stage == AiSubtitleStage.FAILED)
            }
            if (active) {
                LibraryAnimatedAudioLines(Modifier.size(width = 24.dp, height = 18.dp))
                Spacer(Modifier.width(8.dp))
            }
            IconButton(onSubtitle) { Icon(Icons.Default.Subtitles, "字幕", tint = tokens.label3) }
            if (showMenu) TrackActionMenu(aiSubtitleTask, onGenerateSubtitle, onOpenSubtitleGeneration, onRename, onDelete, onMove)
        }
    }
}

@Composable
private fun TrackActionMenu(
    aiSubtitleTask: AiSubtitleTaskState?,
    onGenerateSubtitle: () -> Unit,
    onOpenSubtitleGeneration: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    iconModifier: Modifier = Modifier,
    iconSize: Dp? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val tokens = LocalAmberTokens.current
    Box {
        IconButton(onClick = { menuExpanded = true }, modifier = iconModifier) {
            Icon(Icons.Default.MoreVert, "曲目操作", tint = tokens.label3, modifier = iconSize?.let { Modifier.size(it) } ?: Modifier)
        }
        DropdownMenu(menuExpanded, { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(if (aiSubtitleTask == null) "自动生成字幕" else "查看生成进度") },
                leadingIcon = { Icon(Icons.Default.Subtitles, null) },
                onClick = { menuExpanded = false; if (aiSubtitleTask == null) onGenerateSubtitle() else onOpenSubtitleGeneration() },
            )
            DropdownMenuItem({ Text("重命名") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuExpanded = false; onRename() })
            DropdownMenuItem({ Text("移动到...") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) }, onClick = { menuExpanded = false; onMove() })
            DropdownMenuItem({ Text("删除") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menuExpanded = false; onDelete() })
        }
    }
}

@Composable
private fun ProgressRail(progress: Float, failed: Boolean) {
    val tokens = LocalAmberTokens.current
    Box(Modifier.padding(top = 7.dp).fillMaxWidth().height(4.dp).background(tokens.label3.copy(alpha = 0.22f), RoundedCornerShape(3.dp))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(if (failed) tokens.accent2 else tokens.accent, RoundedCornerShape(3.dp)))
    }
}

private fun aiSubtitleStatusText(task: AiSubtitleTaskState): String = when (task.stage) {
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
