package io.github.summerdez.asmrplayer.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleStage
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleTaskState
import io.github.summerdez.asmrplayer.domain.model.transcriptionDetailLabel
import io.github.summerdez.asmrplayer.ui.theme.LocalAmberTokens
import io.github.summerdez.asmrplayer.ui.uiProbe

// 失败 / 错误用的状态色（设计 token rose-500）；其余一律黑灰，进度条跟随全局强调灰。
private val SheetDanger = Color(0xFFE29393)

@Composable
fun AiSubtitleGenerationSheet(
    task: AiSubtitleTaskState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onRegenerate: () -> Unit,
    onRetranslate: () -> Unit,
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
                .size(width = 38.dp, height = 4.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(tokens.label3.copy(alpha = 0.55f))
                .align(Alignment.CenterHorizontally),
        )
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    "AI 字幕",
                    color = tokens.label3,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "生成字幕",
                    color = tokens.label,
                    fontFamily = FontFamily.Serif,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .uiProbe("ai-subtitle.close", "关闭 AI 字幕生成弹层", "LibrarySheets.kt")
                    .clickable(onClick = onDismiss),
                shape = CircleShape,
                color = tokens.label.copy(alpha = 0.06f),
                border = BorderStroke(1.dp, tokens.separator),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", tint = tokens.label2, modifier = Modifier.size(20.dp))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 20.dp, bottom = 20.dp)) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Brush.linearGradient(listOf(tokens.label3, tokens.labelFaint)))
                    .border(BorderStroke(0.5.dp, tokens.separator), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.88f),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.target.trackTitle,
                    color = tokens.label,
                    fontFamily = FontFamily.Serif,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    task.target.contextTitle.ifBlank { "当前曲目" },
                    color = tokens.label2,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        AiSubtitleStep(
            index = 1,
            title = task.transcriptionTitle,
            detail = task.transcriptionDetailLabel(),
            isFirst = true,
            isLast = false,
            active = task.stage == AiSubtitleStage.TRANSCRIBING,
            done = task.transcribeProgress >= 1f,
            failed = task.stage == AiSubtitleStage.FAILED && task.transcribeProgress < 1f,
            progress = task.transcribeProgress,
        )
        AiSubtitleStep(
            index = 2,
            title = "上下文翻译",
            detail = "",
            isFirst = false,
            isLast = true,
            active = task.stage == AiSubtitleStage.TRANSLATING,
            done = task.translateProgress >= 1f,
            failed = task.stage == AiSubtitleStage.FAILED && task.transcribeProgress >= 1f,
            progress = task.translateProgress,
        )
        if (task.error.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Surface(
                color = SheetDanger.copy(alpha = 0.14f),
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
                    color = SheetDanger,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
        if (task.warning.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Surface(
                color = tokens.label.copy(alpha = 0.05f),
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
                    color = tokens.label2,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
        if (task.previewLines.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Surface(
                color = tokens.label.copy(alpha = 0.04f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, tokens.separator),
                modifier = Modifier
                    .fillMaxWidth()
                    .uiProbe(
                        id = "ai-subtitle.preview",
                        label = "AI 字幕预览卡片",
                        sourceHint = "LibrarySheets.kt",
                        metadata = mapOf("lineCount" to task.previewLines.size.toString()),
                    ),
            ) {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        Modifier
                            .padding(vertical = 15.dp)
                            .width(3.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(tokens.label3.copy(alpha = 0.45f)),
                    )
                    Column(Modifier.padding(start = 15.dp, end = 16.dp, top = 15.dp, bottom = 15.dp)) {
                        Text(
                            "译文预览",
                            color = tokens.label3,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.5.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        task.previewLines.takeLast(2).forEach { line ->
                            Text(
                                line.translatedText.ifBlank { line.sourceText },
                                color = tokens.label,
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                fontSize = 17.sp,
                                lineHeight = 24.sp,
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        when (task.stage) {
            AiSubtitleStage.FAILED -> {
                val retryLabel = if (task.transcribeProgress >= 1f) "重试翻译" else "重试生成"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SheetActionButton(
                        text = "重新分片",
                        icon = Icons.Default.ContentCut,
                        primary = false,
                        probeId = "ai-subtitle.regenerate",
                        probeLabel = "AI 字幕重新分片按钮",
                        onClick = onRegenerate,
                        modifier = Modifier.weight(1f),
                    )
                    SheetActionButton(
                        text = retryLabel,
                        icon = Icons.Default.Refresh,
                        primary = true,
                        probeId = "ai-subtitle.retry",
                        probeLabel = "AI 字幕重试按钮",
                        onClick = onRetry,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            AiSubtitleStage.COMPLETED, AiSubtitleStage.CANCELED -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SheetActionButton(
                        text = "重新分片",
                        icon = Icons.Default.ContentCut,
                        primary = false,
                        probeId = "ai-subtitle.regenerate",
                        probeLabel = "AI 字幕重新分片按钮",
                        onClick = onRegenerate,
                        modifier = Modifier.weight(1f),
                    )
                    SheetActionButton(
                        text = "重新翻译",
                        icon = Icons.Default.Translate,
                        primary = false,
                        probeId = "ai-subtitle.retranslate",
                        probeLabel = "AI 字幕重新翻译按钮",
                        onClick = onRetranslate,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            else -> {
                val paused = task.stage == AiSubtitleStage.PAUSED
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SheetActionButton(
                        text = if (paused) "继续" else "暂停",
                        icon = if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        primary = true,
                        probeId = "ai-subtitle.pause-resume",
                        probeLabel = if (paused) "继续 AI 字幕生成按钮" else "暂停 AI 字幕生成按钮",
                        onClick = if (paused) onResume else onPause,
                        modifier = Modifier.weight(1f),
                    )
                    SheetActionButton(
                        text = "取消",
                        icon = Icons.Default.Close,
                        primary = false,
                        probeId = "ai-subtitle.cancel",
                        probeLabel = "取消 AI 字幕生成按钮",
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AiSubtitleStep(
    index: Int,
    title: String,
    detail: String,
    isFirst: Boolean,
    isLast: Boolean,
    active: Boolean,
    done: Boolean,
    failed: Boolean,
    progress: Float,
) {
    val tokens = LocalAmberTokens.current
    // 节点用 label/bg 反色：亮色近黑圆+白勾，暗色近白圆+深勾（跟随主题、保持黑灰）。
    val nodeColor = when {
        failed -> SheetDanger
        done || active -> tokens.label
        else -> tokens.label3.copy(alpha = 0.16f)
    }
    val nodeContent = when {
        failed -> tokens.bg
        done || active -> tokens.bg
        else -> tokens.label3
    }
    val railColor = tokens.label3.copy(alpha = 0.35f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .uiProbe(
                id = "ai-subtitle.stage-card:$index",
                label = "AI 字幕阶段：$title",
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
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(if (isFirst) Color.Transparent else railColor),
            )
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(nodeColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    done -> Icon(Icons.Default.Check, contentDescription = null, tint = nodeContent, modifier = Modifier.size(17.dp))
                    failed -> Text("!", color = nodeContent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    else -> Text(index.toString(), color = nodeContent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(
                Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(if (isLast) Color.Transparent else railColor),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = tokens.label,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (detail.isNotBlank()) {
                        Text(
                            detail,
                            color = tokens.label2,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    when {
                        failed -> "失败"
                        active || done -> "${(progress * 100).toInt()}%"
                        else -> "等待"
                    },
                    color = when {
                        failed -> SheetDanger
                        active || done -> tokens.label2
                        else -> tokens.label3
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(tokens.label3.copy(alpha = 0.15f)),
            ) {
                // 进度条填充：跟随全局强调灰。
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (failed) SheetDanger else tokens.accent),
                )
            }
        }
    }
}

@Composable
private fun SheetActionButton(
    text: String,
    icon: ImageVector,
    primary: Boolean,
    probeId: String,
    probeLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    // primary：label/bg 反色强按钮（亮色黑底白字 / 暗色白底黑字）。
    // 次级：gray5 中性按钮，跟随主题（亮浅暗深）。
    val bgColor = if (primary) tokens.label else tokens.gray5
    val fgColor = if (primary) tokens.bg else tokens.label
    Surface(
        modifier = modifier
            .height(54.dp)
            .uiProbe(probeId, probeLabel, "LibrarySheets.kt")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = if (primary) null else BorderStroke(1.dp, tokens.separator),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = fgColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = fgColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
