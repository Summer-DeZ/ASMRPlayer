package io.github.summerdez.asmrplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleStage
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleTaskState
import io.github.summerdez.asmrplayer.domain.model.transcriptionDetailLabel
import io.github.summerdez.asmrplayer.ui.components.CoverBox
import io.github.summerdez.asmrplayer.ui.theme.LocalAmberTokens
import io.github.summerdez.asmrplayer.ui.uiProbe

@Composable
fun AiSubtitleGenerationSheet(
    task: AiSubtitleTaskState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onRegenerate: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    Column(
        Modifier
            .fillMaxWidth()
            .uiProbe(
                id = "ai-subtitle.sheet:${task.target.trackId}",
                label = "AI 字幕生成弹层：${task.target.trackTitle}",
                sourceHint = "LibrarySheets.kt",
                metadata = mapOf(
                    "trackId" to task.target.trackId,
                    "playlistId" to task.target.playlistId,
                    "stage" to task.stage.name,
                    "transcribePercent" to (task.transcribeProgress * 100f).toInt().toString(),
                    "translatePercent" to (task.translateProgress * 100f).toInt().toString(),
                ),
            )
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 18.dp),
    ) {
        Box(
            Modifier
                .padding(bottom = 18.dp)
                .size(width = 44.dp, height = 5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(tokens.label3.copy(alpha = 0.55f))
                .align(Alignment.CenterHorizontally),
        )
        Row(verticalAlignment = Alignment.Top) {
            Text("生成字幕", color = tokens.label, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .uiProbe("ai-subtitle.close", "关闭 AI 字幕生成弹层", "LibrarySheets.kt")
                    .clickable(onClick = onDismiss),
                shape = CircleShape,
                color = tokens.label.copy(alpha = 0.08f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", tint = tokens.label2, modifier = Modifier.size(26.dp))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 22.dp, bottom = 20.dp)) {
            CoverBox("", Modifier.size(56.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.target.trackTitle,
                    color = tokens.label,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    task.target.contextTitle.ifBlank { "当前曲目" },
                    color = tokens.label2,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        AiSubtitleStageCard(
            index = 1,
            title = task.transcriptionTitle,
            detail = task.transcriptionDetailLabel(),
            active = task.stage == AiSubtitleStage.TRANSCRIBING,
            done = task.transcribeProgress >= 1f,
            failed = task.stage == AiSubtitleStage.FAILED && task.transcribeProgress < 1f,
            progress = task.transcribeProgress,
        )
        Spacer(Modifier.height(12.dp))
        AiSubtitleStageCard(
            index = 2,
            title = "上下文翻译",
            active = task.stage == AiSubtitleStage.TRANSLATING,
            done = task.translateProgress >= 1f,
            failed = task.stage == AiSubtitleStage.FAILED && task.transcribeProgress >= 1f,
            progress = task.translateProgress,
        )
        if (task.error.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Surface(
                color = tokens.accent2Soft,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .uiProbe(
                        id = "ai-subtitle.error",
                        label = "AI 字幕错误提示",
                        sourceHint = "LibrarySheets.kt",
                        metadata = mapOf("message" to task.error),
                    ),
            ) {
                Text(
                    task.error,
                    color = tokens.accent2,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
        if (task.warning.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Surface(
                color = tokens.accentSoft,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .uiProbe(
                        id = "ai-subtitle.warning",
                        label = "AI 字幕警告提示",
                        sourceHint = "LibrarySheets.kt",
                        metadata = mapOf("message" to task.warning),
                    ),
            ) {
                Text(
                    task.warning,
                    color = tokens.accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
        if (task.previewLines.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Surface(
                color = tokens.card,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .uiProbe(
                        id = "ai-subtitle.preview",
                        label = "AI 字幕预览卡片",
                        sourceHint = "LibrarySheets.kt",
                        metadata = mapOf("lineCount" to task.previewLines.size.toString()),
                    ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Icon(Icons.Default.Subtitles, contentDescription = null, tint = tokens.accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(10.dp))
                    task.previewLines.takeLast(2).forEach { line ->
                        Text(
                            line.translatedText.ifBlank { line.sourceText },
                            color = tokens.label,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            when (task.stage) {
                AiSubtitleStage.FAILED -> {
                    val label = if (task.transcribeProgress >= 1f) "重试翻译" else "重试生成"
                    PrimarySheetAction(label, onRetry)
                    Row(horizontalArrangement = Arrangement.spacedBy(46.dp), modifier = Modifier.padding(top = 16.dp)) {
                        TextButton(
                            onClick = onRegenerate,
                            modifier = Modifier.uiProbe("ai-subtitle.regenerate", "AI 字幕重新分片按钮", "LibrarySheets.kt"),
                        ) {
                            Text("重新分片", color = tokens.label3)
                        }
                        TextButton(
                            onClick = onCancel,
                            modifier = Modifier.uiProbe("ai-subtitle.cancel", "取消 AI 字幕生成按钮", "LibrarySheets.kt"),
                        ) {
                            Text("取消", color = tokens.accent2)
                        }
                    }
                }
                AiSubtitleStage.COMPLETED -> {
                    PrimarySheetAction("完成", onDismiss)
                    TextButton(
                        onClick = onRegenerate,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .uiProbe("ai-subtitle.regenerate", "AI 字幕重新分片按钮", "LibrarySheets.kt"),
                    ) {
                        Text("重新分片", color = tokens.label3)
                    }
                }
                AiSubtitleStage.CANCELED -> {
                    PrimarySheetAction("关闭", onDismiss)
                    TextButton(
                        onClick = onRegenerate,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .uiProbe("ai-subtitle.regenerate", "AI 字幕重新分片按钮", "LibrarySheets.kt"),
                    ) {
                        Text("重新分片", color = tokens.label3)
                    }
                }
                else -> {
                    TextButton(
                        onClick = if (task.stage == AiSubtitleStage.PAUSED) onResume else onPause,
                        modifier = Modifier.uiProbe(
                            id = "ai-subtitle.pause-resume",
                            label = if (task.stage == AiSubtitleStage.PAUSED) "继续 AI 字幕生成按钮" else "暂停 AI 字幕生成按钮",
                            sourceHint = "LibrarySheets.kt",
                            metadata = mapOf("stage" to task.stage.name),
                        ),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                if (task.stage == AiSubtitleStage.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null,
                                tint = tokens.accent2,
                                modifier = Modifier.size(34.dp),
                            )
                            Text(if (task.stage == AiSubtitleStage.PAUSED) "继续" else "暂停", color = tokens.accent2, fontSize = 16.sp)
                        }
                    }
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.uiProbe("ai-subtitle.cancel", "取消 AI 字幕生成按钮", "LibrarySheets.kt"),
                    ) {
                        Text("取消", color = tokens.accent2)
                    }
                }
            }
        }
    }
}

@Composable
private fun AiSubtitleStageCard(
    index: Int,
    title: String,
    detail: String = "",
    active: Boolean,
    done: Boolean,
    failed: Boolean,
    progress: Float,
) {
    val tokens = LocalAmberTokens.current
    Surface(
        color = tokens.card,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .uiProbe(
                id = "ai-subtitle.stage-card:$index",
                label = "AI 字幕阶段卡：$title",
                sourceHint = "LibrarySheets.kt",
                metadata = mapOf(
                    "index" to index.toString(),
                    "title" to title,
                    "detail" to detail,
                    "active" to active.toString(),
                    "done" to done.toString(),
                    "failed" to failed.toString(),
                    "progressPercent" to (progress * 100f).toInt().toString(),
                ),
            ),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            when {
                                failed -> tokens.accent2Soft
                                done -> tokens.accent
                                active -> tokens.accentSoft
                                else -> tokens.label3.copy(alpha = 0.12f)
                            },
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        when {
                            done -> "✓"
                            failed -> "!"
                            else -> index.toString()
                        },
                        color = when {
                            done -> tokens.bg
                            failed -> tokens.accent2
                            else -> tokens.accent
                        },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = tokens.label,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (detail.isNotBlank()) {
                        Text(
                            detail,
                            color = tokens.label2,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
                Text(
                    when {
                        failed -> "失败"
                        active || done -> "${(progress * 100).toInt()}%"
                        else -> "等待"
                    },
                    color = when {
                        failed -> tokens.accent2
                        active || done -> tokens.accent
                        else -> tokens.label3
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Box(
                modifier = Modifier
                    .padding(top = 18.dp)
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(tokens.label3.copy(alpha = 0.22f), RoundedCornerShape(3.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .background(if (failed) tokens.accent2 else tokens.accent, RoundedCornerShape(3.dp)),
                )
            }
        }
    }
}

@Composable
private fun PrimarySheetAction(text: String, onClick: () -> Unit) {
    val tokens = LocalAmberTokens.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .uiProbe(
                id = "sheet.primary-action:$text",
                label = "弹层主按钮：$text",
                sourceHint = "LibrarySheets.kt",
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = tokens.accent,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = tokens.bg, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}
