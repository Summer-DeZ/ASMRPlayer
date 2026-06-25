package io.github.summerdez.asmrplayer.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleStage
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleTaskState
import io.github.summerdez.asmrplayer.domain.model.transcriptionDetailLabel
import io.github.summerdez.asmrplayer.presentation.PlaybackUiState
import io.github.summerdez.asmrplayer.ui.components.CoverBox
import io.github.summerdez.asmrplayer.ui.theme.LocalAmberTokens
import io.github.summerdez.asmrplayer.ui.theme.amberSwitchColors
import io.github.summerdez.asmrplayer.ui.uiProbe

@Composable
internal fun PlayerMoreMenuSheet(
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
    val aiSubtitleStage = aiSubtitleTask?.stage
    val hasAiSubtitleTask = aiSubtitleTask != null
    val aiTaskStatusText = aiSubtitleTask?.let(::playerAiSubtitleStatusText)
    val subtitlesEnabled = hasSubtitle && subtitlesVisible
    val aiIcon = when {
        hasAiSubtitleTask -> Icons.Default.Sync
        subtitlesEnabled -> Icons.Default.SubtitlesOff
        else -> Icons.Default.Subtitles
    }
    val aiLabel = when (aiSubtitleStage) {
        AiSubtitleStage.TRANSCRIBING,
        AiSubtitleStage.TRANSLATING,
        AiSubtitleStage.BINDING -> "AI 字幕生成中"
        AiSubtitleStage.PAUSED -> "AI 字幕已暂停"
        AiSubtitleStage.COMPLETED -> "AI 字幕已完成"
        AiSubtitleStage.FAILED -> "AI 字幕生成失败"
        AiSubtitleStage.CANCELED -> "AI 字幕已取消"
        null -> when {
            subtitlesEnabled -> "AI 字幕已开启"
            hasSubtitle -> "字幕已关闭"
            else -> "AI 识别生成字幕"
        }
    }
    val aiSub = when {
        aiTaskStatusText != null -> aiTaskStatusText
        subtitlesEnabled -> "点击关闭字幕"
        hasSubtitle -> "点击重新显示字幕"
        else -> "使用 Whisper 实时识别语音"
    }
    val onAiClick = {
        when {
            hasAiSubtitleTask -> {
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
            .uiProbe("player.more-sheet", "播放器更多菜单 Sheet", "PlayerMoreMenu.kt")
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
                        probeId = "player.more.action.ai-subtitle",
                        probeLabel = "更多菜单：AI 字幕",
                        icon = aiIcon,
                        label = aiLabel,
                        sub = aiSub,
                        onClick = onAiClick,
                        trailing = {
                            if (aiSubtitleTask != null) {
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
                                    colors = amberSwitchColors(),
                                )
                            }
                        },
                        consumeTrailingClicks = !hasAiSubtitleTask,
                    )
                    PlayerMenuActionRow(
                        probeId = "player.more.action.sleep-timer",
                        probeLabel = "更多菜单：睡眠定时",
                        icon = Icons.Default.Timer,
                        label = "睡眠定时",
                        sub = "设置自动停止时间",
                        onClick = onSleepTimer,
                    )
                    PlayerMenuActionRow(
                        probeId = "player.more.action.mix-layers",
                        probeLabel = "更多菜单：混音层叠",
                        icon = Icons.Default.Layers,
                        label = "混音层叠",
                        sub = "将此段叠加到环境音轨",
                        onClick = onMixLayers,
                    )
                    PlayerMenuActionRow(
                        probeId = "player.more.action.download",
                        probeLabel = "更多菜单：下载状态",
                        icon = Icons.Default.Download,
                        label = if (currentTrackIsLocal) "已下载到本地" else "下载到本地",
                        sub = if (currentTrackIsLocal) "已可离线播放" else "需要网络连接",
                        enabled = !currentTrackIsLocal,
                        onClick = if (currentTrackIsLocal) {
                            {}
                        } else {
                            onDownloadTrack
                        },
                        trailing = if (currentTrackIsLocal) {
                            { PlayerMenuStatusPill("本地") }
                        } else {
                            null
                        },
                    )
                    PlayerMenuActionRow(
                        probeId = "player.more.action.remove-from-queue",
                        probeLabel = "更多菜单：从队列中移除",
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
    probeId: String,
    probeLabel: String,
    icon: ImageVector,
    label: String,
    sub: String,
    danger: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    consumeTrailingClicks: Boolean = false,
    onClick: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    val trailingInteractionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .uiProbe(
                id = probeId,
                label = probeLabel,
                sourceHint = "PlayerMoreMenu.kt",
                metadata = mapOf(
                    "enabled" to enabled.toString(),
                    "danger" to danger.toString(),
                ),
            )
            .clickable(enabled = enabled, onClick = onClick)
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
                modifier = if (consumeTrailingClicks) {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = trailingInteractionSource,
                        onClick = {},
                    )
                } else {
                    Modifier
                },
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
