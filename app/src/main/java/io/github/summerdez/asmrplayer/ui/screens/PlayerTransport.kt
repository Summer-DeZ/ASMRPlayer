package io.github.summerdez.asmrplayer.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.summerdez.asmrplayer.presentation.PlaybackUiState
import io.github.summerdez.asmrplayer.ui.theme.LocalAmberTokens
import io.github.summerdez.asmrplayer.ui.uiProbe
import io.github.summerdez.asmrplayer.ui.util.formatTime
import kotlin.math.max

@Composable
fun PlaybackProgressLine(playbackState: PlaybackUiState, onSeek: (Int) -> Unit) {
    val tokens = LocalAmberTokens.current
    val canSeek = playbackState.durationMs > 0
    val duration = max(1, playbackState.durationMs)
    val position = playbackState.positionMs.coerceIn(0, duration)
    val fraction = position.toFloat() / duration.toFloat()
    Column(
        Modifier
            .fillMaxWidth()
            .uiProbe(
                id = "player.progress.scrubber",
                label = "播放器进度条",
                sourceHint = "PlayerTransport.kt",
                metadata = mapOf(
                    "positionMs" to playbackState.positionMs.toString(),
                    "durationMs" to playbackState.durationMs.toString(),
                    "canSeek" to canSeek.toString(),
                ),
            )
            .padding(horizontal = 6.dp),
    ) {
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

@Composable
internal fun PlayerTransportControls(
    playbackState: PlaybackUiState,
    onPrevious: () -> Unit,
    onPlay: () -> Unit,
    onNext: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .uiProbe("player.transport-controls", "播放器主控制区", "PlayerTransport.kt")
            .padding(top = 18.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = CircleShape, color = tokens.card, border = BorderStroke(1.dp, tokens.separator)) {
            IconButton(
                onClick = onPrevious,
                modifier = Modifier
                    .size(58.dp)
                    .uiProbe("player.previous", "上一首按钮", "PlayerTransport.kt"),
            ) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "上一首", modifier = Modifier.size(30.dp), tint = tokens.label)
            }
        }
        Surface(shape = CircleShape, color = tokens.accent, shadowElevation = 12.dp) {
            IconButton(
                onClick = onPlay,
                modifier = Modifier
                    .size(78.dp)
                    .uiProbe("player.play-toggle", "播放/暂停按钮", "PlayerTransport.kt"),
            ) {
                Icon(
                    if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "播放",
                    tint = tokens.bg,
                    modifier = Modifier.size(42.dp),
                )
            }
        }
        Surface(shape = CircleShape, color = tokens.card, border = BorderStroke(1.dp, tokens.separator)) {
            IconButton(
                onClick = onNext,
                modifier = Modifier
                    .size(58.dp)
                    .uiProbe("player.next", "下一首按钮", "PlayerTransport.kt"),
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = "下一首", modifier = Modifier.size(30.dp), tint = tokens.label)
            }
        }
    }
}

@Composable
internal fun PlayerBottomActions(
    onSleepTimer: () -> Unit,
    onMixLayers: () -> Unit,
    onQueue: () -> Unit,
) {
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

@Composable
private fun PlayerActionButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    val actionId = when (label) {
        "睡眠" -> "sleep"
        "混音" -> "mix"
        "队列" -> "queue"
        else -> label
    }
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.uiProbe(
            id = "player.action.$actionId",
            label = "播放器动作按钮：$label",
            sourceHint = "PlayerTransport.kt",
            metadata = mapOf("enabled" to enabled.toString(), "selected" to selected.toString()),
        ),
    ) {
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
