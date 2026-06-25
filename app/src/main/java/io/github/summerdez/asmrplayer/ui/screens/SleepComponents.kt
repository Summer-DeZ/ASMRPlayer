package io.github.summerdez.asmrplayer.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.summerdez.asmrplayer.presentation.SleepTimerUiState
import io.github.summerdez.asmrplayer.ui.components.noRippleClickable
import io.github.summerdez.asmrplayer.ui.theme.LocalAmberTokens
import io.github.summerdez.asmrplayer.ui.theme.amberSwitchColors
import io.github.summerdez.asmrplayer.ui.uiProbe

@Composable
fun SleepRing(state: SleepTimerUiState, displayMinutes: Int?) {
    val tokens = LocalAmberTokens.current
    val lightTheme = tokens.grouped.luminance() > 0.5f
    val timerAccent = if (lightTheme) tokens.label else Color.White
    val timerTrack = if (lightTheme) tokens.label.copy(alpha = 0.16f) else tokens.gray5
    val timerGlow = timerAccent.copy(alpha = if (lightTheme) 0.05f else 0.10f)
    val countdownMs = if (state.active) state.remainingMs else displayMinutes?.let { it * 60_000L }
    val progress = when {
        state.atEndOfTrack -> 1f
        state.active && state.minutes > 0 -> {
            (state.remainingMs.toFloat() / (state.minutes * 60_000f)).coerceIn(0f, 1f)
        }
        displayMinutes != null -> 1f
        else -> 0f
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(top = 6.dp)
            .uiProbe(
                id = "sleep.ring",
                label = "睡眠定时环",
                sourceHint = "SleepComponents.kt",
                metadata = mapOf(
                    "active" to state.active.toString(),
                    "atEndOfTrack" to state.atEndOfTrack.toString(),
                    "displayMinutes" to (displayMinutes?.toString() ?: ""),
                    "progressPercent" to (progress * 100f).toInt().toString(),
                ),
            ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(210.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(timerGlow, Color.Transparent),
                    ),
                ),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 3.dp.toPx()
                val inset = 18.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)
                drawArc(
                    color = timerTrack,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round),
                )
                if (progress > 0f) {
                    drawArc(
                        color = timerAccent,
                        startAngle = -90f,
                        sweepAngle = -360f * progress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
            when {
                state.atEndOfTrack -> Icon(Icons.Default.MusicNote, contentDescription = null, tint = timerAccent, modifier = Modifier.size(58.dp))
                countdownMs != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatSleepCountdown(countdownMs),
                        color = timerAccent,
                        fontSize = 52.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 56.sp,
                    )
                }
            }
        }
    }
}

private fun formatSleepCountdown(milliseconds: Long): String {
    val totalSeconds = ((milliseconds.coerceAtLeast(0L) + 999L) / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}

@Composable
fun SleepChip(minutes: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tokens = LocalAmberTokens.current
    val shape = CircleShape
    val selectedColor = tokens.switchOn
    Surface(
        modifier = modifier
            .height(40.dp)
            .uiProbe(
                id = "sleep.chip:$minutes",
                label = "睡眠定时快捷分钟：$minutes 分",
                sourceHint = "SleepComponents.kt",
                metadata = mapOf("minutes" to minutes.toString(), "selected" to selected.toString()),
            )
            .noRippleClickable(onClick = onClick),
        shape = shape,
        color = if (selected) selectedColor else tokens.label.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, if (selected) Color.Transparent else tokens.separator),
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${minutes}分",
                color = if (selected) Color.White else tokens.label2,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun SleepSwitchSettingRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .uiProbe(
                id = "sleep.switch-row:$title",
                label = "睡眠设置开关：$title",
                sourceHint = "SleepComponents.kt",
                metadata = mapOf("checked" to checked.toString()),
            )
            .clickable(onClick = onCheckedChange)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tokens.label2, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            title,
            color = tokens.label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = { onCheckedChange() },
            colors = amberSwitchColors(),
        )
    }
}

@Composable
fun SleepOptionRow(icon: ImageVector, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val tokens = LocalAmberTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .uiProbe(
                id = "sleep.option-row:$title",
                label = "睡眠选项行：$title",
                sourceHint = "SleepComponents.kt",
                metadata = mapOf("subtitle" to subtitle, "selected" to selected.toString()),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(14.dp),
            color = if (selected) tokens.accentSoft else tokens.label3.copy(alpha = 0.12f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = if (selected) tokens.accent else tokens.label2, modifier = Modifier.size(23.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (selected) tokens.accent else tokens.label,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                color = tokens.label2,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        if (selected) {
            Text("✓", color = tokens.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        } else {
            Text("›", color = tokens.label3, fontSize = 26.sp)
        }
    }
}
