package io.github.summerdez.asmrplayer.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import io.github.summerdez.asmrplayer.data.*
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import io.github.summerdez.asmrplayer.presentation.DlsiteUiState
import io.github.summerdez.asmrplayer.ui.components.CoverBox
import io.github.summerdez.asmrplayer.ui.theme.LocalAmberTokens
import io.github.summerdez.asmrplayer.ui.uiProbe

val DlsiteBlue = Color(0xFF4F9BE0)
val DlsiteBlueSoft = Color(0x294F9BE0)
val DlsiteBlueBorder = Color(0x3D4F9BE0)

@Composable
fun DownloadManagerSheet(
    state: DlsiteUiState,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onClearCompleted: () -> Unit,
    onPause: (DlsiteWork) -> Unit,
    onResume: (DlsiteWork) -> Unit,
    onCancel: (DlsiteWork) -> Unit,
) {
    val tokens = LocalAmberTokens.current
    val tasks = state.downloadState.tasks.values.sortedWith(compareBy<DlsiteDownloadTaskState> { it.status.ordinal }.thenBy { it.updatedAt })
    Column(Modifier.fillMaxWidth().uiProbe("dlsite.download-manager-sheet", "DLsite 下载管理 Sheet", "DlsiteDialogs.kt").navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 10.dp)) {
        DlsiteSheetGrabber(); Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("下载管理", color = tokens.label, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text(downloadManagerCaption(tasks, state.downloadState.summary), color = tokens.label2, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = if (state.downloadState.summary.runningCount > 0) onPauseAll else onResumeAll) {
                Icon(if (state.downloadState.summary.runningCount > 0) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = tokens.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp)); Text(if (state.downloadState.summary.runningCount > 0) "全部暂停" else "全部继续", color = tokens.accent, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(18.dp))
        if (tasks.isEmpty()) {
            Surface(shape = RoundedCornerShape(18.dp), color = tokens.card, border = BorderStroke(0.5.dp, tokens.separator), modifier = Modifier.fillMaxWidth()) { Text("没有下载任务", color = tokens.label2, modifier = Modifier.padding(18.dp)) }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                tasks.forEach { task ->
                    state.works.firstOrNull { it.workId == task.workId }?.let { work ->
                        DlsiteDownloadTaskRow(
                            work = work,
                            busy = state.busy,
                            taskState = task,
                            onPause = { onPause(work) },
                            onResume = { onResume(work) },
                            onCancel = { onCancel(work) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onClearCompleted, modifier = Modifier.align(Alignment.End)) { Icon(Icons.Default.Delete, contentDescription = null, tint = tokens.label2, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("清除已完成", color = tokens.label2) }
    }
}

@Composable
private fun DlsiteDownloadTaskRow(work: DlsiteWork, busy: Boolean, taskState: DlsiteDownloadTaskState, onPause: () -> Unit, onResume: () -> Unit, onCancel: () -> Unit) {
    val tokens = LocalAmberTokens.current
    val statusLabel = dlsiteTaskStatusText(taskState)
    val progress = dlsiteProgressFraction(work, taskState)
    val failed = taskState.status == DlsiteDownloadTaskStatus.FAILED || work.isFailed()
    val taskId = taskState.contentId.ifBlank { taskState.workId }
    fun taskActionId(action: String) = "dlsite.download-task-action:${taskState.workId}:$taskId:$action"
    fun taskActionMetadata(action: String) = mapOf(
        "workId" to taskState.workId,
        "taskId" to taskId,
        "contentId" to taskState.contentId,
        "status" to taskState.status.name,
        "action" to action,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverBox(work.coverUri, Modifier.size(56.dp)); Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(work.displayTitle(), color = tokens.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); DlsiteStatusLine(work.workId, statusLabel, failed) }
            Spacer(Modifier.width(10.dp))
            when (taskState.status) {
                DlsiteDownloadTaskStatus.QUEUED -> DlsiteIconActionButton(Icons.Default.Close, "取消", onCancel, enabled = !busy, destructive = true, probeId = taskActionId("cancel"), probeMetadata = taskActionMetadata("cancel"))
                DlsiteDownloadTaskStatus.DOWNLOADING -> DlsiteIconActionButton(Icons.Default.Pause, "暂停", onPause, enabled = !busy, probeId = taskActionId("pause"), probeMetadata = taskActionMetadata("pause"))
                DlsiteDownloadTaskStatus.PAUSED -> DlsiteIconActionButton(Icons.Default.PlayArrow, "继续", onResume, enabled = !busy, probeId = taskActionId("resume"), probeMetadata = taskActionMetadata("resume"))
                DlsiteDownloadTaskStatus.FAILED -> DlsiteIconActionButton(Icons.Default.Download, "重试", onResume, enabled = !busy, probeId = taskActionId("retry"), probeMetadata = taskActionMetadata("retry"))
                DlsiteDownloadTaskStatus.COMPLETED -> DlsiteIconActionButton(Icons.Default.Delete, "清除", onCancel, enabled = !busy, destructive = true, probeId = taskActionId("clear"), probeMetadata = taskActionMetadata("clear"))
            }
        }
        if (progress != null) DlsiteProgressLine(progress, failed, Modifier.fillMaxWidth())
    }
}

@Composable
fun DlsiteSectionCaption(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    val tokens = LocalAmberTokens.current
    Row(Modifier.fillMaxWidth().padding(start = 2.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = tokens.label, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null) Text(action, color = tokens.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(enabled = onAction != null) { onAction?.invoke() })
    }
}

@Composable
fun DlsiteEmptyState() {
    val tokens = LocalAmberTokens.current
    Surface(shape = RoundedCornerShape(28.dp), color = tokens.card, border = BorderStroke(0.5.dp, tokens.separator), modifier = Modifier.fillMaxWidth()) { Text("同步后会在这里显示已购买作品", color = tokens.label2, fontSize = 15.sp, modifier = Modifier.padding(16.dp)) }
}

@Composable
fun DlsiteSoftActionPill(icon: ImageVector, text: String, onClick: () -> Unit, enabled: Boolean = true) {
    val tokens = LocalAmberTokens.current
    val accentSoft = tokens.accent.copy(alpha = 0.14f)
    val accentBorder = tokens.accent.copy(alpha = 0.34f)
    Surface(shape = RoundedCornerShape(999.dp), color = if (enabled) accentSoft else tokens.gray5, border = BorderStroke(0.5.dp, if (enabled) accentBorder else tokens.separator), modifier = Modifier.height(38.dp).clickable(enabled = enabled, onClick = onClick)) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) { Icon(icon, contentDescription = null, tint = if (enabled) tokens.accent else tokens.label3, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(7.dp)); Text(text, color = if (enabled) tokens.accent else tokens.label3, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
fun DlsiteStatusPill(text: String, failed: Boolean) {
    val tokens = LocalAmberTokens.current
    Surface(shape = RoundedCornerShape(999.dp), color = if (failed) tokens.accent2Soft else tokens.accent.copy(alpha = 0.14f), border = BorderStroke(0.5.dp, if (failed) tokens.accent2.copy(alpha = 0.45f) else tokens.accent.copy(alpha = 0.34f))) {
        Text(text, color = if (failed) tokens.accent2 else tokens.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
fun DlsiteLogoBadge(size: Dp) {
    Surface(shape = RoundedCornerShape(if (size >= 60.dp) 18.dp else 14.dp), color = DlsiteBlue, border = BorderStroke(0.5.dp, DlsiteBlueBorder), modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) { Text("DL", color = Color.White, fontSize = (size.value * 0.34f).sp, lineHeight = (size.value * 0.34f).sp, fontWeight = FontWeight.ExtraBold) }
    }
}

@Composable
private fun DlsiteIconActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
    filled: Boolean = false,
    size: Dp = 56.dp,
    probeId: String = "dlsite.action.${contentDescription.ifBlank { "button" }}",
    probeMetadata: Map<String, String> = emptyMap(),
) {
    val tokens = LocalAmberTokens.current
    val background = when { !enabled -> tokens.gray5; filled -> DlsiteBlue; destructive -> tokens.accent2Soft; else -> DlsiteBlueSoft }
    val tint = when { !enabled -> tokens.label3; filled -> Color.White; destructive -> tokens.accent2; else -> DlsiteBlue }
    Surface(shape = RoundedCornerShape(if (size >= 52.dp) 18.dp else 14.dp), color = background, border = if (filled) null else BorderStroke(0.5.dp, if (destructive) tokens.separator else DlsiteBlueBorder), modifier = Modifier.size(size).uiProbe(id = probeId, label = "DLsite 操作按钮：$contentDescription", sourceHint = "DlsiteDialogs.kt", metadata = mapOf("enabled" to enabled.toString()) + probeMetadata).clickable(enabled = enabled, onClick = onClick)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(if (size >= 52.dp) 25.dp else 21.dp)) }
    }
}

@Composable
fun DlsiteProgressLine(progress: Float, failed: Boolean, modifier: Modifier = Modifier) {
    val tokens = LocalAmberTokens.current
    Box(modifier.height(3.dp).background(tokens.label3.copy(alpha = 0.18f), RoundedCornerShape(3.dp))) { Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(if (failed) tokens.accent2 else DlsiteBlue, RoundedCornerShape(3.dp))) }
}

@Composable
private fun DlsiteSheetGrabber() {
    val tokens = LocalAmberTokens.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { Box(Modifier.width(42.dp).height(5.dp).background(tokens.label3, RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {} }
}

@Composable
private fun DlsiteStatusLine(workId: String, status: String, failed: Boolean) {
    val tokens = LocalAmberTokens.current
    Row(verticalAlignment = Alignment.CenterVertically) { Text(workId, color = tokens.label2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)); Spacer(Modifier.width(6.dp)); DlsiteStatusPill(status, failed) }
}

fun dlsiteProgressFraction(work: DlsiteWork, taskState: DlsiteDownloadTaskState?) = when {
    taskState?.progressPercent != null -> taskState.progressPercent.coerceIn(0, 100) / 100f
    taskState?.status == DlsiteDownloadTaskStatus.FAILED || work.isFailed() -> 1f
    else -> null
}
fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "--"
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble(); var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) { value /= 1024.0; unit++ }
    return if (unit == 0) "${value.toLong()} ${units[unit]}" else "%.1f %s".format(value, units[unit])
}
private fun dlsiteTaskStatusText(taskState: DlsiteDownloadTaskState) = when (taskState.status) {
    DlsiteDownloadTaskStatus.QUEUED -> "排队中"
    DlsiteDownloadTaskStatus.DOWNLOADING -> taskState.progressPercent?.let { "载入中 $it%" } ?: "载入中"
    DlsiteDownloadTaskStatus.PAUSED -> "已暂停"
    DlsiteDownloadTaskStatus.FAILED -> "载入失败"
    DlsiteDownloadTaskStatus.COMPLETED -> "已完成"
}
private fun downloadManagerCaption(tasks: List<DlsiteDownloadTaskState>, summary: DlsiteDownloadSummary) = when {
    summary.totalCount > 0 -> "${summary.totalCount} 个任务进行中"
    tasks.isNotEmpty() -> "${tasks.size} 个任务"
    else -> "没有下载任务"
}
