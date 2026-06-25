package io.github.summerdez.asmrplayer.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.summerdez.asmrplayer.presentation.SleepTimerUiState
import io.github.summerdez.asmrplayer.ui.components.noRippleClickable
import io.github.summerdez.asmrplayer.ui.theme.LocalAmberTokens
import io.github.summerdez.asmrplayer.ui.uiProbe
import io.github.summerdez.asmrplayer.ui.util.sleepStatusText

@Composable
fun SleepRing(state: SleepTimerUiState, displayMinutes: Int) {
    val tokens = LocalAmberTokens.current
    val progress = when {
        state.atEndOfTrack -> 1f
        state.active && state.minutes > 0 -> {
            (state.remainingMs.toFloat() / (state.minutes * 60_000f)).coerceIn(0f, 1f)
        }
        else -> (displayMinutes.toFloat() / 90f).coerceIn(0f, 1f)
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
                    "displayMinutes" to displayMinutes.toString(),
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
                        listOf(tokens.label.copy(alpha = 0.05f), Color.Transparent),
                    ),
                ),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 3.dp.toPx()
                val inset = 18.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)
                drawArc(
                    color = tokens.gray5,
                    startAngle = -45f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round),
                )
                if (progress > 0f) {
                    drawArc(
                        color = tokens.accent,
                        startAngle = -45f,
                        sweepAngle = 320f * progress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
            when {
                state.atEndOfTrack -> Icon(Icons.Default.MusicNote, contentDescription = null, tint = tokens.accent, modifier = Modifier.size(58.dp))
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (state.active) {
                            ((state.remainingMs + 59_999L) / 60_000L).coerceAtLeast(0L).toString()
                        } else {
                            displayMinutes.toString()
                        },
                        color = tokens.label,
                        fontSize = 58.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 60.sp,
                    )
                    Text("分钟", color = tokens.label3, fontSize = 13.sp, letterSpacing = 1.6.sp)
                }
            }
        }
        Text(
            sleepStatusText(state),
            color = if (state.active) tokens.label else tokens.label2,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
fun SleepChip(minutes: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tokens = LocalAmberTokens.current
    val shape = CircleShape
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
        color = if (selected) tokens.accent else tokens.label.copy(alpha = 0.06f),
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
                color = if (selected) tokens.bg else tokens.label2,
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
    subtitle: String,
    checked: Boolean,
    onCheckedChange: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .uiProbe(
                id = "sleep.switch-row:$title",
                label = "睡眠设置开关：$title",
                sourceHint = "SleepComponents.kt",
                metadata = mapOf("checked" to checked.toString(), "subtitle" to subtitle),
            )
            .clickable(onClick = onCheckedChange)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tokens.label2, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = tokens.label,
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
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = { onCheckedChange() },
            colors = SwitchDefaults.colors(
                checkedTrackColor = tokens.switchOn,
                uncheckedTrackColor = tokens.label.copy(alpha = 0.10f),
                checkedThumbColor = Color(0xFF0A0A0B),
                uncheckedThumbColor = tokens.label2,
                uncheckedBorderColor = Color.Transparent,
                checkedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
internal fun SleepVolumeSettingRow(volume: Int, onVolumeChange: (Int) -> Unit) {
    val tokens = LocalAmberTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .uiProbe(
                id = "sleep.volume-row",
                label = "睡眠入睡音量设置",
                sourceHint = "SleepComponents.kt",
                metadata = mapOf("volume" to volume.toString()),
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = tokens.label2, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Text(
                "入睡音量",
                color = tokens.label,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "$volume%",
                color = tokens.label2,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Slider(
            value = volume.toFloat(),
            onValueChange = { onVolumeChange(it.toInt().coerceIn(0, 100)) },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = tokens.accent2,
                activeTrackColor = tokens.accent2,
                inactiveTrackColor = tokens.label.copy(alpha = 0.10f),
            ),
            modifier = Modifier.padding(start = 34.dp, top = 8.dp),
        )
    }
}

@Composable
internal fun SleepPrimaryButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .uiProbe(
                id = "sleep.primary-button",
                label = "开始睡眠定时按钮",
                sourceHint = "SleepComponents.kt",
                metadata = mapOf("text" to text),
            )
            .noRippleClickable(onClick = onClick),
        shape = CircleShape,
        color = tokens.accent,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tokens.bg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = tokens.bg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
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
