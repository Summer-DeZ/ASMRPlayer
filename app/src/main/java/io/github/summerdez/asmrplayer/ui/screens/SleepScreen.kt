package io.github.summerdez.asmrplayer.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.summerdez.asmrplayer.presentation.SleepTimerUiState
import io.github.summerdez.asmrplayer.ui.components.GroupedCard
import io.github.summerdez.asmrplayer.ui.theme.LocalAmberTokens
import io.github.summerdez.asmrplayer.ui.uiProbe

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
