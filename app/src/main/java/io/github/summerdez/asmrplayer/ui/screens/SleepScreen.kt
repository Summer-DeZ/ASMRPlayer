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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
    var selectedMinutes: Int? by remember {
        mutableStateOf(if (state.active && !state.atEndOfTrack && state.minutes > 0) state.minutes else null)
    }
    var fadeEnabled by remember { mutableStateOf(true) }
    var stopAfterCurrent by remember { mutableStateOf(state.active && state.atEndOfTrack) }
    val context = LocalContext.current
    LaunchedEffect(state.active, state.atEndOfTrack, state.minutes) {
        if (state.active && !state.atEndOfTrack && state.minutes > 0) {
            selectedMinutes = state.minutes
        }
        if (!state.active) {
            selectedMinutes = null
        }
        if (state.active) {
            stopAfterCurrent = state.atEndOfTrack
        } else if (state.atEndOfTrack) {
            stopAfterCurrent = false
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
                    "selectedMinutes" to (selectedMinutes?.toString() ?: ""),
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
                        onSetMinutes(minutes)
                    },
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        GroupedCard {
            SleepSwitchSettingRow(
                icon = Icons.Default.GraphicEq,
                title = "结束前淡出",
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
        }
        Spacer(Modifier.height(22.dp))
        TextButton(
            onClick = if (state.active) onCancel else onCustom,
            modifier = Modifier
                .height(54.dp)
                .uiProbe(
                    id = "sleep.timer-action",
                    label = if (state.active) "取消睡眠定时按钮" else "自定义睡眠时间按钮",
                    sourceHint = "SleepScreen.kt",
                    metadata = mapOf(
                        "active" to state.active.toString(),
                        "text" to if (state.active) "取消定时" else "自定义时间",
                    ),
                ),
        ) {
            Text(
                if (state.active) "取消定时" else "自定义时间",
                color = if (state.active) tokens.switchOn else tokens.label3,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
