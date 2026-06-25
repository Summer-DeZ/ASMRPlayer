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
import androidx.compose.material.icons.filled.GraphicEq
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
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.compositeOver
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
import io.github.summerdez.asmrplayer.presentation.SleepTimerUiState
import io.github.summerdez.asmrplayer.ui.components.GroupedCard
import io.github.summerdez.asmrplayer.ui.components.noRippleClickable
import io.github.summerdez.asmrplayer.ui.theme.LocalAmberTokens
import io.github.summerdez.asmrplayer.ui.uiProbe
import io.github.summerdez.asmrplayer.ui.util.sleepStatusText
import java.text.DateFormat
import java.util.Date
import kotlin.math.max

@Composable
fun SleepTab(
    state: SleepTimerUiState,
    onSetMinutes: (Int) -> Unit,
    onSetEndOfTrack: () -> Unit,
    onCustom: () -> Unit,
    onCancel: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    var selectedMinutes by remember {
        mutableIntStateOf(if (state.minutes > 0) state.minutes else 45)
    }
    var fadeEnabled by remember { mutableStateOf(true) }
    var stopAfterCurrent by remember { mutableStateOf(state.active && state.atEndOfTrack) }
    var sleepVolume by remember { mutableIntStateOf(64) }
    val context = LocalContext.current
    LaunchedEffect(state.active, state.atEndOfTrack, state.minutes) {
        if (state.active && !state.atEndOfTrack && state.minutes > 0) {
            selectedMinutes = state.minutes
        }
        if (state.active) {
            stopAfterCurrent = state.atEndOfTrack
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .uiProbe(
                id = "sleep.root",
                label = "睡眠定时页根容器",
                sourceHint = "SleepScreen.kt",
                metadata = mapOf(
                    "active" to state.active.toString(),
                    "atEndOfTrack" to state.atEndOfTrack.toString(),
                    "minutes" to state.minutes.toString(),
                    "selectedMinutes" to selectedMinutes.toString(),
                ),
            )
            .verticalScroll(rememberScrollState())
            .padding(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SleepRing(state, selectedMinutes)
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(15, 30, 45, 60, 90).forEach { minutes ->
                SleepChip(
                    minutes = minutes,
                    selected = !state.atEndOfTrack &&
                        if (state.active) state.minutes == minutes else selectedMinutes == minutes,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        selectedMinutes = minutes
                        stopAfterCurrent = false
                    },
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        GroupedCard {
            SleepSwitchSettingRow(
                icon = Icons.Default.GraphicEq,
                title = "结束前淡出",
                subtitle = "临近结束时缓缓降低音量",
                checked = fadeEnabled,
                onCheckedChange = {
                    fadeEnabled = !fadeEnabled
                    Toast.makeText(context, if (fadeEnabled) "已开启结束前淡出" else "已关闭结束前淡出", Toast.LENGTH_SHORT).show()
                },
            )
            HorizontalDivider(color = tokens.separator, modifier = Modifier.padding(start = 48.dp))
            SleepSwitchSettingRow(
                icon = Icons.Default.MusicNote,
                title = "播完当前作品后停止",
                subtitle = "忽略计时，听完整段",
                checked = stopAfterCurrent,
                onCheckedChange = {
                    val next = !stopAfterCurrent
                    stopAfterCurrent = next
                    if (next) {
                        onSetEndOfTrack()
                    } else if (state.active && state.atEndOfTrack) {
                        onCancel()
                    }
                },
            )
            HorizontalDivider(color = tokens.separator, modifier = Modifier.padding(start = 48.dp))
            SleepVolumeSettingRow(
                volume = sleepVolume,
                onVolumeChange = { sleepVolume = it },
            )
        }
        Spacer(Modifier.height(22.dp))
        SleepPrimaryButton(
            text = "开始睡眠定时",
            icon = Icons.Default.Bedtime,
            onClick = {
                if (stopAfterCurrent) {
                    onSetEndOfTrack()
                } else {
                    onSetMinutes(selectedMinutes)
                }
            },
        )
        TextButton(
            onClick = onCustom,
            modifier = Modifier
                .height(44.dp)
                .uiProbe("sleep.custom-minutes", "自定义睡眠分钟按钮", "SleepScreen.kt"),
        ) {
            Text("自定义分钟数", color = tokens.label3, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        if (state.active) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier
                    .height(54.dp)
                    .uiProbe("sleep.cancel", "取消睡眠定时按钮", "SleepScreen.kt"),
            ) {
                Text("取消睡眠定时", color = tokens.accent2, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

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
                sourceHint = "SleepScreen.kt",
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
                sourceHint = "SleepScreen.kt",
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
private fun SleepSwitchSettingRow(
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
                sourceHint = "SleepScreen.kt",
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
private fun SleepVolumeSettingRow(volume: Int, onVolumeChange: (Int) -> Unit) {
    val tokens = LocalAmberTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .uiProbe(
                id = "sleep.volume-row",
                label = "睡眠入睡音量设置",
                sourceHint = "SleepScreen.kt",
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
private fun SleepPrimaryButton(
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
                sourceHint = "SleepScreen.kt",
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
                sourceHint = "SleepScreen.kt",
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
