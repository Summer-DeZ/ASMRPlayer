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
import io.github.summerdez.asmrplayer.presentation.PlaybackUiState
import io.github.summerdez.asmrplayer.ui.components.CoverBox
import io.github.summerdez.asmrplayer.ui.theme.LocalAmberTokens
import io.github.summerdez.asmrplayer.ui.util.formatTime
import io.github.summerdez.asmrplayer.ui.util.splitSubtitle
import java.text.DateFormat
import java.util.Date
import kotlin.math.max

@Composable
fun SubtitlePlayerScreen(
    playbackState: PlaybackUiState,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onPlay: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Int) -> Unit,
    onOverlay: () -> Unit,
    onQueue: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    val currentLines = splitSubtitle(playbackState.currentSubtitle.ifEmpty { playbackState.subtitleEmptyText })
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.playerBase)
            .background(
                Brush.radialGradient(
                    colors = listOf(tokens.accent.copy(alpha = 0.28f), Color.Transparent),
                    center = Offset(180f, 140f),
                    radius = 520f,
                ),
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(tokens.accent2.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(880f, 260f),
                    radius = 640f,
                ),
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                CoverBox(playbackState.coverUri, Modifier.size(54.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        playbackState.audioTitle,
                        color = tokens.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        playbackState.contextTitle,
                        color = tokens.label3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 15.sp,
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.background(tokens.label.copy(alpha = 0.10f), CircleShape),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", tint = tokens.label2)
                }
            }
            // 整列字幕滚动：渲染完整字幕列表，按当前行号把整列平滑滚动到中间偏上。所有行排版一致，
            // 焦点只用颜色亮暗 + 绘制层缩放区分。整列一起移动 → 离开顶部的句子自然滑出，不会某行原地淡出
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val lines = playbackState.subtitleLines
                val current = playbackState.subtitleIndex
                if (lines.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            playbackState.subtitleEmptyText,
                            color = tokens.label3,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else {
                    val listState = rememberLazyListState()
                    val centerPad = maxHeight * 0.40f
                    LaunchedEffect(current, lines.size) {
                        if (current in lines.indices) {
                            listState.animateScrollToItem(current)
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds(),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = centerPad),
                        userScrollEnabled = false,
                    ) {
                        itemsIndexed(lines, key = { index, _ -> index }) { index, raw ->
                            val active = index == current
                            val parts = splitSubtitle(raw)
                            val color by animateColorAsState(
                                targetValue = if (active) tokens.label else tokens.label3,
                                animationSpec = tween(380),
                                label = "lyricColor",
                            )
                            val scale by animateFloatAsState(
                                targetValue = if (active) 1f else 0.86f,
                                animationSpec = tween(380),
                                label = "lyricScale",
                            )
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        transformOrigin = TransformOrigin(0f, 0.5f)
                                    }
                                    .padding(vertical = 14.dp),
                            ) {
                                Text(
                                    parts.first,
                                    color = color,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 36.sp,
                                )
                                if (active && parts.second.isNotEmpty()) {
                                    Text(
                                        parts.second,
                                        color = tokens.label2,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 19.sp,
                                        modifier = Modifier.padding(top = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            PlaybackProgressLine(playbackState, onSeek)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious, modifier = Modifier.size(54.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "上一首", modifier = Modifier.size(34.dp), tint = tokens.label)
                }
                IconButton(onClick = onPlay, modifier = Modifier.size(68.dp)) {
                    Icon(
                        if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "播放",
                        tint = tokens.label,
                        modifier = Modifier.size(44.dp),
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.size(54.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = "下一首", modifier = Modifier.size(34.dp), tint = tokens.label)
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = onOverlay) { Text(if (playbackState.overlayRequested) "悬浮字幕" else "悬浮字幕", color = if (playbackState.overlayRequested) tokens.accent else tokens.label3) }
                Spacer(Modifier.width(26.dp))
                TextButton(onClick = onQueue) { Text("播放队列", color = tokens.label3) }
            }
        }
    }
}

@Composable
fun PlaybackProgressLine(playbackState: PlaybackUiState, onSeek: (Int) -> Unit) {
    val tokens = LocalAmberTokens.current
    val canSeek = playbackState.durationMs > 0
    val duration = max(1, playbackState.durationMs)
    val position = playbackState.positionMs.coerceIn(0, duration)
    val fraction = position.toFloat() / duration.toFloat()
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(duration, canSeek) {
                    if (!canSeek) {
                        return@pointerInput
                    }
                    detectTapGestures { offset ->
                        seekToProgressOffset(offset.x, size.width, duration, onSeek)
                    }
                }
                .pointerInput(duration, canSeek) {
                    if (!canSeek) {
                        return@pointerInput
                    }
                    detectDragGestures(
                        onDragStart = { offset ->
                            seekToProgressOffset(offset.x, size.width, duration, onSeek)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            seekToProgressOffset(change.position.x, size.width, duration, onSeek)
                        },
                    )
                },
        ) {
            val trackHeight = 5.dp.toPx()
            val thumbRadius = 7.dp.toPx()
            val thumbRingRadius = 10.dp.toPx()
            val trackTop = center.y - trackHeight / 2f
            val trackSize = Size(size.width, trackHeight)
            val progressWidth = size.width * fraction
            val thumbCenter = Offset(progressWidth.coerceIn(thumbRingRadius, size.width - thumbRingRadius), center.y)

            drawRoundRect(
                color = tokens.label.copy(alpha = 0.16f),
                topLeft = Offset(0f, trackTop),
                size = trackSize,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f, trackHeight / 2f),
            )
            if (progressWidth > 0f) {
                drawRoundRect(
                    color = tokens.accent,
                    topLeft = Offset(0f, trackTop),
                    size = Size(progressWidth, trackHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f, trackHeight / 2f),
                )
            }
            drawCircle(color = tokens.playerBase, radius = thumbRingRadius, center = thumbCenter)
            drawCircle(color = tokens.accent, radius = thumbRadius, center = thumbCenter)
        }
        Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), color = tokens.label3, fontSize = 12.sp)
            Text("-${formatTime((duration - position).coerceAtLeast(0))}", color = tokens.label3, fontSize = 12.sp)
        }
    }
}

fun seekToProgressOffset(x: Float, width: Int, duration: Int, onSeek: (Int) -> Unit) {
    if (width > 0 && duration > 0) {
        val progress = (x / width.toFloat()).coerceIn(0f, 1f)
        onSeek((duration * progress).toInt())
    }
}
