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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.ui.zIndex
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
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleStage
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleTaskState
import io.github.summerdez.asmrplayer.domain.model.transcriptionDetailLabel
import io.github.summerdez.asmrplayer.presentation.PlaybackUiState
import io.github.summerdez.asmrplayer.ui.components.CoverBox
import io.github.summerdez.asmrplayer.ui.components.EqualizerIcon
import io.github.summerdez.asmrplayer.ui.theme.LocalAmberTokens
import io.github.summerdez.asmrplayer.ui.util.formatTime
import io.github.summerdez.asmrplayer.ui.util.splitSubtitle
import java.text.DateFormat
import java.util.Date
import kotlin.math.max

@Composable
fun SubtitlePlayerScreen(
    playbackState: PlaybackUiState,
    aiSubtitleTask: AiSubtitleTaskState?,
    currentTrackIsLocal: Boolean,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onPlay: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Int) -> Unit,
    onQueue: () -> Unit,
    onGenerateAiSubtitle: () -> Unit,
    onOpenAiSubtitleProgress: () -> Unit,
    onSleepTimer: () -> Unit,
    onMixLayers: () -> Unit,
    onDownloadTrack: () -> Unit,
    onRemoveFromQueue: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    var menuOpen by remember { mutableStateOf(false) }
    var subtitlesVisible by remember { mutableStateOf(true) }
    LaunchedEffect(playbackState.playlistId, playbackState.playlistIndex, playbackState.subtitleTitle) {
        subtitlesVisible = true
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.playerBase)
            .background(
                Brush.radialGradient(
                    colors = listOf(tokens.accent.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(180f, 140f),
                    radius = 520f,
                ),
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(tokens.accent2.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(880f, 260f),
                    radius = 640f,
                ),
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 26.dp, end = 26.dp, top = 14.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(onClick = onClose),
                    shape = CircleShape,
                    color = tokens.glass,
                    border = BorderStroke(0.5.dp, tokens.separator),
                    shadowElevation = 0.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "关闭", tint = tokens.label, modifier = Modifier.size(28.dp))
                    }
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "正在播放",
                        color = tokens.label3,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.6.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        playbackState.contextTitle,
                        color = tokens.label2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Surface(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(onClick = { menuOpen = true }),
                    shape = CircleShape,
                    color = tokens.glass,
                    border = BorderStroke(0.5.dp, tokens.separator),
                    shadowElevation = 0.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多", tint = tokens.label, modifier = Modifier.size(22.dp))
                    }
                }
            }
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 10.dp),
            ) {
                val lines = playbackState.subtitleLines
                val current = playbackState.subtitleIndex
                if (!subtitlesVisible) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.SubtitlesOff,
                                contentDescription = null,
                                tint = tokens.labelFaint,
                                modifier = Modifier.size(34.dp),
                            )
                            Text(
                                "字幕已关闭",
                                color = tokens.label3,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                } else if (lines.isEmpty()) {
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
                                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                                    }
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (active) tokens.label.copy(alpha = 0.05f) else Color.Transparent)
                                    .padding(horizontal = if (active) 16.dp else 0.dp)
                                    .padding(vertical = if (active) 12.dp else 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    parts.first,
                                    color = color,
                                    fontSize = if (active) 22.sp else 16.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                                    fontWeight = FontWeight.Normal,
                                    lineHeight = if (active) 32.sp else 25.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (active && parts.second.isNotEmpty()) {
                                    Text(
                                        parts.second,
                                        color = tokens.accent,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 24.sp,
                                        modifier = Modifier.padding(top = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(60.dp)
                            .background(Brush.verticalGradient(listOf(tokens.playerBase, Color.Transparent))),
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(60.dp)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, tokens.playerBase))),
                    )
                    EqualizerIcon(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .size(width = 22.dp, height = 16.dp),
                    )
                }
            }
            PlayerTrackMeta(playbackState)
            Spacer(Modifier.height(18.dp))
            PlaybackProgressLine(playbackState, onSeek)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = CircleShape, color = tokens.card, border = BorderStroke(1.dp, tokens.separator)) {
                    IconButton(onClick = onPrevious, modifier = Modifier.size(58.dp)) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "上一首", modifier = Modifier.size(30.dp), tint = tokens.label)
                    }
                }
                Surface(shape = CircleShape, color = tokens.accent, shadowElevation = 12.dp) {
                    IconButton(onClick = onPlay, modifier = Modifier.size(78.dp)) {
                        Icon(
                            if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "播放",
                            tint = tokens.bg,
                            modifier = Modifier.size(42.dp),
                        )
                    }
                }
                Surface(shape = CircleShape, color = tokens.card, border = BorderStroke(1.dp, tokens.separator)) {
                    IconButton(onClick = onNext, modifier = Modifier.size(58.dp)) {
                        Icon(Icons.Default.SkipNext, contentDescription = "下一首", modifier = Modifier.size(30.dp), tint = tokens.label)
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                PlayerActionButton(
                    icon = Icons.Default.Bedtime,
                    label = "睡眠",
                    selected = false,
                    onClick = onSleepTimer,
                )
                PlayerActionButton(
                    icon = Icons.Default.Layers,
                    label = "混音",
                    selected = false,
                    onClick = onMixLayers,
                )
                PlayerActionButton(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    label = "队列",
                    selected = false,
                    onClick = onQueue,
                )
            }
        }
        if (menuOpen) {
            PlayerMoreMenuSheet(
                playbackState = playbackState,
                aiSubtitleTask = aiSubtitleTask,
                subtitlesVisible = subtitlesVisible,
                currentTrackIsLocal = currentTrackIsLocal,
                onToggleSubtitles = { subtitlesVisible = !subtitlesVisible },
                onGenerateAiSubtitle = onGenerateAiSubtitle,
                onOpenAiSubtitleProgress = onOpenAiSubtitleProgress,
                onSleepTimer = {
                    menuOpen = false
                    onSleepTimer()
                },
                onMixLayers = {
                    menuOpen = false
                    onMixLayers()
                },
                onDownloadTrack = {
                    menuOpen = false
                    onDownloadTrack()
                },
                onRemoveFromQueue = {
                    menuOpen = false
                    onRemoveFromQueue()
                },
                onClose = { menuOpen = false },
            )
        }
    }
}

@Composable
private fun PlayerMoreMenuSheet(
    playbackState: PlaybackUiState,
    aiSubtitleTask: AiSubtitleTaskState?,
    subtitlesVisible: Boolean,
    currentTrackIsLocal: Boolean,
    onToggleSubtitles: () -> Unit,
    onGenerateAiSubtitle: () -> Unit,
    onOpenAiSubtitleProgress: () -> Unit,
    onSleepTimer: () -> Unit,
    onMixLayers: () -> Unit,
    onDownloadTrack: () -> Unit,
    onRemoveFromQueue: () -> Unit,
    onClose: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    val hasSubtitle = playbackState.subtitleLines.isNotEmpty() ||
        (playbackState.subtitleTitle.isNotBlank() && playbackState.subtitleTitle != "未选择字幕")
    val hasActiveAiTask = aiSubtitleTask != null && aiSubtitleTask.stage != AiSubtitleStage.COMPLETED
    val subtitlesEnabled = hasSubtitle && subtitlesVisible
    val aiIcon = when {
        hasActiveAiTask -> Icons.Default.Sync
        subtitlesEnabled -> Icons.Default.SubtitlesOff
        else -> Icons.Default.Subtitles
    }
    val aiLabel = when {
        hasActiveAiTask -> "AI 字幕生成中"
        subtitlesEnabled -> "AI 字幕已开启"
        else -> "AI 识别生成字幕"
    }
    val aiSub = when {
        hasActiveAiTask -> aiSubtitleTask.let(::playerAiSubtitleStatusText)
        subtitlesEnabled -> "点击关闭字幕"
        hasSubtitle -> "点击重新显示字幕"
        else -> "使用 Whisper 实时识别语音"
    }
    val onAiClick = {
        when {
            hasActiveAiTask -> {
                onClose()
                onOpenAiSubtitleProgress()
            }
            hasSubtitle -> onToggleSubtitles()
            else -> {
                onClose()
                onGenerateAiSubtitle()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(8f),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClose,
                ),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = tokens.sheet,
            border = BorderStroke(1.dp, tokens.separator),
            shadowElevation = 18.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 18.dp),
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 14.dp, bottom = 6.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(tokens.label3.copy(alpha = 0.65f))
                        .align(Alignment.CenterHorizontally),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverBox(playbackState.coverUri, Modifier.size(44.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            playbackState.audioTitle,
                            color = tokens.label,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            playbackState.contextTitle.ifBlank { "当前曲目" },
                            color = tokens.label2,
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                HorizontalDivider(color = tokens.separator)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    PlayerMenuActionRow(
                        icon = aiIcon,
                        label = aiLabel,
                        sub = aiSub,
                        onClick = onAiClick,
                        trailing = {
                            if (hasActiveAiTask) {
                                PlayerMenuStatusPill(playerAiSubtitleProgressLabel(aiSubtitleTask))
                            } else {
                                Switch(
                                    checked = subtitlesEnabled,
                                    onCheckedChange = { checked ->
                                        if (hasSubtitle) {
                                            onToggleSubtitles()
                                        } else if (checked) {
                                            onClose()
                                            onGenerateAiSubtitle()
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = tokens.bg,
                                        checkedTrackColor = tokens.accent,
                                        uncheckedThumbColor = tokens.label3,
                                        uncheckedTrackColor = tokens.gray5,
                                    ),
                                )
                            }
                        },
                    )
                    PlayerMenuActionRow(
                        icon = Icons.Default.Timer,
                        label = "睡眠定时",
                        sub = "设置自动停止时间",
                        onClick = onSleepTimer,
                    )
                    PlayerMenuActionRow(
                        icon = Icons.Default.Layers,
                        label = "混音层叠",
                        sub = "将此段叠加到环境音轨",
                        onClick = onMixLayers,
                    )
                    PlayerMenuActionRow(
                        icon = Icons.Default.Download,
                        label = if (currentTrackIsLocal) "已下载到本地" else "下载到本地",
                        sub = if (currentTrackIsLocal) "已可离线播放" else "需要网络连接",
                        onClick = onDownloadTrack,
                        trailing = if (currentTrackIsLocal) {
                            { PlayerMenuStatusPill("本地") }
                        } else {
                            null
                        },
                    )
                    PlayerMenuActionRow(
                        icon = Icons.Default.PlaylistRemove,
                        label = "从队列中移除",
                        sub = "移除当前段落，播放下一段",
                        danger = true,
                        onClick = onRemoveFromQueue,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerMenuActionRow(
    icon: ImageVector,
    label: String,
    sub: String,
    danger: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (danger) Color(0xFFE86A78) else tokens.label3,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = if (danger) Color(0xFFE86A78) else tokens.label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (sub.isNotBlank()) {
                Text(
                    sub,
                    color = tokens.labelFaint,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                ),
            ) {
                trailing()
            }
        }
    }
}

@Composable
private fun PlayerMenuStatusPill(text: String) {
    val tokens = LocalAmberTokens.current
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = tokens.accentSoft,
        border = BorderStroke(1.dp, tokens.accent.copy(alpha = 0.24f)),
    ) {
        Text(
            text,
            color = tokens.accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            maxLines = 1,
        )
    }
}

private fun playerAiSubtitleStatusText(task: AiSubtitleTaskState): String {
    return when (task.stage) {
        AiSubtitleStage.TRANSCRIBING -> task.transcriptionDetailLabel().ifBlank {
            "${task.transcriptionTitle} ${(task.transcribeProgress * 100).toInt()}%"
        }
        AiSubtitleStage.TRANSLATING -> "正在翻译 ${(task.translateProgress * 100).toInt()}%"
        AiSubtitleStage.BINDING -> "正在绑定字幕"
        AiSubtitleStage.COMPLETED -> if (task.warning.isBlank()) "AI 字幕已生成" else "AI 字幕已生成，需检查片假名"
        AiSubtitleStage.PAUSED -> "AI 字幕已暂停，点击查看"
        AiSubtitleStage.FAILED -> "AI 字幕生成失败，点击查看"
        AiSubtitleStage.CANCELED -> "AI 字幕已取消"
    }
}

private fun playerAiSubtitleProgressLabel(task: AiSubtitleTaskState?): String {
    if (task == null) {
        return ""
    }
    return when (task.stage) {
        AiSubtitleStage.PAUSED -> "暂停"
        AiSubtitleStage.FAILED -> "失败"
        AiSubtitleStage.CANCELED -> "已取消"
        else -> "${(task.overallProgress.coerceIn(0f, 1f) * 100).toInt()}%"
    }
}

@Composable
private fun PlayerTrackMeta(playbackState: PlaybackUiState) {
    val tokens = LocalAmberTokens.current
    val meta = listOf(playbackState.contextTitle, playbackState.subtitleTitle)
        .filter { it.isNotBlank() && it != "未选择字幕" && it != "未选择音频" }
        .distinct()
        .joinToString(" · ")
        .ifBlank { "当前曲目" }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        CoverBox(playbackState.coverUri, Modifier.size(52.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                playbackState.audioTitle,
                color = tokens.label,
                fontSize = 25.sp,
                lineHeight = 29.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                fontWeight = FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                meta,
                color = tokens.label2,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (playbackState.overlayRequested) {
            Spacer(Modifier.width(10.dp))
            Surface(
                shape = CircleShape,
                color = tokens.accentSoft,
                border = BorderStroke(1.dp, tokens.accent.copy(alpha = 0.24f)),
            ) {
                Text(
                    "字幕",
                    color = tokens.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun PlayerActionButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    TextButton(onClick = onClick, enabled = enabled) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                tint = when {
                    selected -> tokens.accent
                    enabled -> tokens.label3
                    else -> tokens.label3.copy(alpha = 0.45f)
                },
                modifier = Modifier.size(30.dp),
            )
            Text(
                label,
                color = when {
                    selected -> tokens.accent
                    enabled -> tokens.label3
                    else -> tokens.label3.copy(alpha = 0.45f)
                },
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
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
                .height(30.dp)
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
            val trackHeight = 4.5.dp.toPx()
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
            drawCircle(color = tokens.label, radius = thumbRadius, center = thumbCenter)
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
