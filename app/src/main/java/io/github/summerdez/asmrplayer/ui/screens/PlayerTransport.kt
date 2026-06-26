package io.github.summerdez.asmrplayer.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
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
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        seekToProgressOffset(down.position.x, size.width, duration, onSeek)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                break
                            }
                            seekToProgressOffset(change.position.x, size.width, duration, onSeek)
                            change.consume()
                        }
                    }
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
    Row(
        Modifier
            .fillMaxWidth()
            .uiProbe("player.transport-controls", "播放器主控制区", "PlayerTransport.kt")
            .padding(top = 18.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerTransportIconButton(
            icon = Icons.Default.SkipPrevious,
            contentDescription = "上一首",
            probeId = "player.previous",
            probeLabel = "上一首按钮",
            onClick = onPrevious,
        )
        PlayerTransportIconButton(
            icon = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = "播放",
            probeId = "player.play-toggle",
            probeLabel = "播放/暂停按钮",
            onClick = onPlay,
        )
        PlayerTransportIconButton(
            icon = Icons.Default.SkipNext,
            contentDescription = "下一首",
            probeId = "player.next",
            probeLabel = "下一首按钮",
            onClick = onNext,
        )
    }
}

@Composable
private fun PlayerTransportIconButton(
    icon: ImageVector,
    contentDescription: String,
    probeId: String,
    probeLabel: String,
    onClick: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(64.dp)
            .uiProbe(probeId, probeLabel, "PlayerTransport.kt"),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tokens.label,
            modifier = Modifier.size(34.dp),
        )
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
    val actionId = when (label) {
        "睡眠" -> "sleep"
        "混音" -> "mix"
        "队列" -> "queue"
        else -> label
    }
    val tokens = LocalAmberTokens.current
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(64.dp)
            .uiProbe(
                id = "player.action.$actionId",
                label = "播放器动作按钮：$label",
                sourceHint = "PlayerTransport.kt",
                metadata = mapOf("enabled" to enabled.toString(), "selected" to selected.toString()),
            ),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) tokens.label else tokens.label.copy(alpha = 0.45f),
            modifier = Modifier.size(34.dp),
        )
    }
}
