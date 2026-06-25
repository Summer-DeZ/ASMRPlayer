package io.github.summerdez.asmrplayer.ui.screens

import android.content.*
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import io.github.summerdez.asmrplayer.data.*
import io.github.summerdez.asmrplayer.domain.model.*
import io.github.summerdez.asmrplayer.ui.components.CoverBox
import io.github.summerdez.asmrplayer.ui.theme.LocalAmberTokens
import io.github.summerdez.asmrplayer.ui.uiProbe
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DlsiteWorkRow(
    work: DlsiteWork,
    busy: Boolean,
    taskState: DlsiteDownloadTaskState?,
    contents: List<DlsiteContent>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onDownload: () -> Unit,
    onDownloadContent: (DlsiteContent) -> Unit,
    onDeleteContent: (DlsiteContent) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    val workTaskState = taskState?.takeIf { it.contentId.isBlank() }
    val statusLabel = dlsiteWorkStatusText(work, workTaskState, contents)
    val hasContents = contents.isNotEmpty()
    val progress = if (hasContents) null else dlsiteProgressFraction(work, workTaskState)
    val failed = !hasContents && (workTaskState?.status == DlsiteDownloadTaskStatus.FAILED || work.isFailed())
    val actionState = dlsiteRowActionState(work, workTaskState, contents)
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, tween(220), label = "dlsiteWorkChevron")
    val actionClick = when (actionState) {
        DlsiteRowActionState.Download -> onDownload
        DlsiteRowActionState.Ready -> ({})
        DlsiteRowActionState.Queued -> onDelete
        DlsiteRowActionState.Downloading -> onPause
        DlsiteRowActionState.Paused -> onResume
        DlsiteRowActionState.Failed -> onResume
    }
    Surface(
        shape = RoundedCornerShape(24.dp), color = tokens.card, border = BorderStroke(0.5.dp, tokens.separator),
        modifier = Modifier.fillMaxWidth().uiProbe(
            id = "dlsite.work-row:${work.workId}", label = "DLsite 作品卡：${work.displayTitle()}", sourceHint = "DlsiteRows.kt",
            metadata = mapOf("workId" to work.workId, "status" to statusLabel, "action" to actionState.name, "expanded" to expanded.toString()),
        ),
    ) {
        Column(Modifier.background(tokens.card).padding(horizontal = 10.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().clickable(enabled = hasContents, onClick = onToggleExpanded), verticalAlignment = Alignment.CenterVertically) {
                CoverBox(work.coverUri, Modifier.size(52.dp)); Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(work.displayTitle(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = tokens.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(work.workId, color = tokens.label2, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(10.dp))
                if (hasContents) {
                    Box(Modifier.size(32.dp).graphicsLayer { rotationZ = chevronRotation }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = if (expanded) "收起内容" else "展开内容", tint = tokens.accent, modifier = Modifier.size(22.dp))
                    }
                } else {
                    DlsiteDownloadStatusControl(actionState, workTaskState?.progressPercent, !busy && actionState != DlsiteRowActionState.Ready, actionClick)
                }
            }
            if (progress != null) DlsiteProgressLine(progress, failed, Modifier.fillMaxWidth())
            DlsiteWorkContentPanel(busy, taskState, actionState, contents, expanded, onDownloadContent, onDeleteContent, onPause, onResume, onDelete)
        }
    }
}

@Composable
private fun DlsiteWorkContentPanel(
    busy: Boolean,
    taskState: DlsiteDownloadTaskState?,
    actionState: DlsiteRowActionState,
    contents: List<DlsiteContent>,
    expanded: Boolean,
    onDownloadContent: (DlsiteContent) -> Unit,
    onDeleteContent: (DlsiteContent) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    val showActions = dlsiteExpandedActionsVisible(actionState) && (expanded || contents.isEmpty())
    if ((!expanded || contents.isEmpty()) && !showActions) return
    AnimatedVisibility(expanded || showActions, enter = expandVertically(tween(260)) + fadeIn(tween(200)), exit = shrinkVertically(tween(220)) + fadeOut(tween(150))) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HorizontalDivider(color = tokens.separator)
            if (expanded && contents.isNotEmpty()) Column {
                contents.forEachIndexed { index, content ->
                    DlsiteContentRow(content, taskState?.takeIf { it.contentId == content.optionId }, busy, { onDownloadContent(content) }, { onDeleteContent(content) }, onPause, onResume)
                    if (index != contents.lastIndex) HorizontalDivider(color = tokens.separator)
                }
            }
            if (showActions) DlsiteExpandedActionRow(busy, actionState, onPause, onResume, onDelete)
        }
    }
}

@Composable
private fun DlsiteContentRow(
    content: DlsiteContent,
    taskState: DlsiteDownloadTaskState?,
    busy: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    val context = LocalContext.current
    val cached = remember(content.workId, content.optionId, content.status, content.localPath, content.updatedAt, taskState?.status) { dlsiteContentHasLocalCache(context, content) }
    val progress = dlsiteContentProgressFraction(content, taskState)
    val failed = taskState?.status == DlsiteDownloadTaskStatus.FAILED || content.isFailed()
    Column(
        Modifier.fillMaxWidth().uiProbe(
            id = "dlsite.content-row:${content.optionId}", label = "DLsite 内容行：${content.title.ifEmpty { "默认版本" }}", sourceHint = "DlsiteRows.kt",
            metadata = mapOf("optionId" to content.optionId, "status" to (taskState?.statusText ?: contentStatusText(content, cached)), "cached" to cached.toString()),
        ).padding(horizontal = 2.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DlsiteContentStatusDot(cached); Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(content.title.ifEmpty { "默认版本" }, color = tokens.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(dlsiteContentMetaText(content, taskState, cached), color = tokens.label2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Box(contentAlignment = Alignment.CenterEnd) {
                when {
                    content.isDownloading() || taskState?.status == DlsiteDownloadTaskStatus.DOWNLOADING -> DlsiteSoftActionPill(Icons.Default.Pause, "暂停", onPause, !busy)
                    content.isQueued() || taskState?.status == DlsiteDownloadTaskStatus.QUEUED -> DlsiteStatusPill("排队中", false)
                    content.isPaused() || taskState?.status == DlsiteDownloadTaskStatus.PAUSED -> DlsiteSoftActionPill(Icons.Default.PlayArrow, "继续", onResume, !busy)
                    content.isFailed() || taskState?.status == DlsiteDownloadTaskStatus.FAILED -> DlsiteSoftActionPill(Icons.Default.Download, "重试", onDownload, !busy)
                    cached -> DlsiteSoftActionPill(Icons.Default.Delete, "删除", onDelete, !busy)
                    else -> DlsiteSoftActionPill(Icons.Default.Download, "下载", onDownload, !busy)
                }
            }
        }
        if (progress != null) DlsiteProgressLine(progress, failed, Modifier.padding(start = 39.dp, top = 8.dp).fillMaxWidth())
    }
}

@Composable
private fun DlsiteContentStatusDot(downloaded: Boolean) {
    val tokens = LocalAmberTokens.current
    val color = if (downloaded) tokens.accent else tokens.label3
    Surface(shape = CircleShape, color = color.copy(alpha = 0.16f), border = BorderStroke(0.5.dp, color.copy(alpha = if (downloaded) 0.55f else 0.35f)), modifier = Modifier.size(28.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Check, contentDescription = null, tint = color, modifier = Modifier.size(15.dp)) }
    }
}

@Composable
private fun DlsiteExpandedActionRow(busy: Boolean, actionState: DlsiteRowActionState, onPause: () -> Unit, onResume: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (actionState) {
            DlsiteRowActionState.Queued -> DlsiteSoftActionPill(Icons.Default.Close, "取消", onDelete, !busy)
            DlsiteRowActionState.Downloading -> DlsiteSoftActionPill(Icons.Default.Pause, "暂停", onPause, !busy)
            DlsiteRowActionState.Paused -> { DlsiteSoftActionPill(Icons.Default.PlayArrow, "继续", onResume, !busy); DlsiteSoftActionPill(Icons.Default.Delete, "删除缓存", onDelete, !busy) }
            DlsiteRowActionState.Failed -> { DlsiteSoftActionPill(Icons.Default.Download, "重试", onResume, !busy); DlsiteSoftActionPill(Icons.Default.Delete, "删除缓存", onDelete, !busy) }
            DlsiteRowActionState.Ready, DlsiteRowActionState.Download -> Unit
        }
    }
}

private enum class DlsiteRowActionState { Download, Ready, Queued, Downloading, Paused, Failed }
private fun dlsiteRowActionState(work: DlsiteWork, taskState: DlsiteDownloadTaskState?, contents: List<DlsiteContent>) = when {
    contents.isNotEmpty() -> DlsiteRowActionState.Ready
    taskState?.status == DlsiteDownloadTaskStatus.QUEUED || work.isQueued() -> DlsiteRowActionState.Queued
    taskState?.status == DlsiteDownloadTaskStatus.DOWNLOADING || work.isDownloading() -> DlsiteRowActionState.Downloading
    taskState?.status == DlsiteDownloadTaskStatus.PAUSED || work.isPaused() -> DlsiteRowActionState.Paused
    taskState?.status == DlsiteDownloadTaskStatus.FAILED || work.isFailed() -> DlsiteRowActionState.Failed
    else -> DlsiteRowActionState.Download
}
private fun dlsiteExpandedActionsVisible(actionState: DlsiteRowActionState) = actionState in setOf(DlsiteRowActionState.Queued, DlsiteRowActionState.Downloading, DlsiteRowActionState.Paused, DlsiteRowActionState.Failed)

@Composable
private fun DlsiteDownloadStatusControl(state: DlsiteRowActionState, progressPercent: Int?, enabled: Boolean, onClick: () -> Unit) {
    val tokens = LocalAmberTokens.current
    val (icon, text, color) = when (state) {
        DlsiteRowActionState.Download -> Triple(Icons.Default.Sync, "同步", tokens.accent)
        DlsiteRowActionState.Ready -> Triple(Icons.Default.KeyboardArrowDown, "内容", tokens.accent)
        DlsiteRowActionState.Queued -> Triple(Icons.Default.Sync, "排队中", tokens.accent)
        DlsiteRowActionState.Downloading -> Triple(Icons.Default.Pause, progressPercent?.let { "$it%" } ?: "下载中", tokens.accent)
        DlsiteRowActionState.Paused -> Triple(Icons.Default.PlayArrow, "继续", tokens.accent)
        DlsiteRowActionState.Failed -> Triple(Icons.Default.Download, "重试", tokens.accent2)
    }
    if (state == DlsiteRowActionState.Download) {
        Box(Modifier.size(40.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = text, tint = if (enabled) color else tokens.label3, modifier = Modifier.size(21.dp)) }
        return
    }
    Row(Modifier.height(40.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = text, tint = if (enabled) color else tokens.label3, modifier = Modifier.size(20.dp))
        Text(text, color = if (enabled) color else tokens.label3, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun DlsiteAccountCard(
    loggedIn: Boolean,
    busy: Boolean,
    lastSyncMs: Long,
    workCount: Int,
    offlineWorkCount: Int,
    downloadSummary: DlsiteDownloadSummary,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onSync: () -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onOpenDownloadManager: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    val context = LocalContext.current
    val hasDownloadTasks = downloadSummary.totalCount > 0
    val bulkActionIcon = if (downloadSummary.runningCount > 0) Icons.Default.Pause else Icons.Default.PlayArrow
    val bulkActionText = if (downloadSummary.runningCount > 0) "全部暂停" else "全部继续"
    val bulkActionClick = if (downloadSummary.runningCount > 0) onPauseAll else onResumeAll
    if (!loggedIn) {
        Column(Modifier.uiProbe("dlsite.account-card", "DLsite 未登录账号卡", "DlsiteRows.kt"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(28.dp), color = Color.Transparent, border = BorderStroke(0.5.dp, DlsiteBlueBorder), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.background(Brush.linearGradient(listOf(DlsiteBlueSoft, tokens.card))).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { DlsiteLogoBadge(50.dp); Spacer(Modifier.width(12.dp)); Column(verticalArrangement = Arrangement.spacedBy(3.dp)) { Text("同步 DLsite 音声作品", color = tokens.label, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("已购买的作品，自动出现在资料库", color = tokens.label2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                }
            }
            DlsiteVisualInput("DLsite 账号 / 邮箱", Icons.Default.Person, "your_id@dlsite.com", onLogin)
            DlsiteVisualInput("密码", Icons.Default.Lock, "••••••••", onLogin)
            DlsiteFilledActionPill(Icons.Default.Download, if (busy) "正在登录并同步..." else "登录并同步", onLogin, !busy, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF84C2A3), modifier = Modifier.size(14.dp)); Spacer(Modifier.width(8.dp)); Text("登录信息仅用于同步，加密保存在本设备", color = tokens.label3, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Text("没有账户？前往 DLsite 注册", color = DlsiteBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable { openDlsiteRegister(context) }.padding(top = 2.dp))
        }
        return
    }
    Column(Modifier.uiProbe(id = "dlsite.account-card", label = "DLsite 已登录账号卡", sourceHint = "DlsiteRows.kt", metadata = mapOf("workCount" to workCount.toString(), "offlineWorkCount" to offlineWorkCount.toString(), "busy" to busy.toString())), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(28.dp), color = tokens.card, border = BorderStroke(0.5.dp, tokens.separator), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.background(tokens.card).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                DlsiteLogoBadge(52.dp); Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("DLsite", color = tokens.label, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("已连接 · 已同步 ${workCount} 个作品", color = tokens.label2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) { DlsiteStatusPill("已连接", false); TextButton(onClick = onLogout, enabled = !busy, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(30.dp)) { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = tokens.label2, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("退出登录", color = tokens.label2, fontSize = 13.sp) } }
            }
        }
        Surface(shape = RoundedCornerShape(28.dp), color = tokens.card, border = BorderStroke(0.5.dp, tokens.separator), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.background(tokens.card).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("同步状态", color = tokens.label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(if (busy) "正在同步..." else "上次同步 · ${formatSyncTimeCompact(lastSyncMs)}", color = tokens.label2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DlsiteSoftActionPill(Icons.Default.Sync, if (busy) "同步中" else "重新同步", onSync, !busy)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    DlsiteStat("$workCount", "已同步作品", Modifier.weight(1f))
                    DlsiteStat("$offlineWorkCount", "可离线作品", Modifier.weight(1f))
                    DlsiteStat(if (downloadSummary.totalCount > 0) "${downloadSummary.runningCount}/${downloadSummary.totalCount}" else "0", "下载任务", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DlsiteSoftActionPill(Icons.Default.Download, "下载管理", onOpenDownloadManager)
                    DlsiteSoftActionPill(bulkActionIcon, bulkActionText, bulkActionClick, !busy && hasDownloadTasks)
                }
            }
        }
    }
}

@Composable
private fun DlsiteVisualInput(label: String, icon: ImageVector, placeholder: String, onClick: () -> Unit) {
    val tokens = LocalAmberTokens.current
    Column(Modifier.uiProbe(id = "dlsite.visual-input:$label", label = "DLsite 登录输入框：$label", sourceHint = "DlsiteRows.kt", metadata = mapOf("placeholder" to placeholder)), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = tokens.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Surface(Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onClick), RoundedCornerShape(20.dp), color = tokens.label.copy(alpha = 0.05f), border = BorderStroke(1.dp, tokens.separator)) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, contentDescription = null, tint = tokens.label3, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text(placeholder, color = tokens.label3, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun DlsiteStat(value: String, label: String, modifier: Modifier = Modifier) {
    val tokens = LocalAmberTokens.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(value, color = tokens.label, fontSize = 26.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(label, color = tokens.label2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun DlsiteFilledActionPill(icon: ImageVector, text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val tokens = LocalAmberTokens.current
    Surface(shape = RoundedCornerShape(999.dp), color = if (enabled) DlsiteBlue else tokens.gray5, modifier = modifier.height(48.dp).clickable(enabled = enabled, onClick = onClick)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) { Icon(icon, contentDescription = null, tint = if (enabled) Color.White else tokens.label3, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text(text, color = if (enabled) Color.White else tokens.label3, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
    }
}

private fun openDlsiteRegister(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.dlsite.com/maniax/regist"))
    runCatching { context.startActivity(intent) }.onFailure { Toast.makeText(context, "无法打开注册链接", Toast.LENGTH_SHORT).show() }
}
private fun dlsiteContentProgressFraction(content: DlsiteContent, taskState: DlsiteDownloadTaskState?) = when {
    taskState?.progressPercent != null -> taskState.progressPercent.coerceIn(0, 100) / 100f
    taskState?.status == DlsiteDownloadTaskStatus.FAILED || content.isFailed() -> 1f
    else -> null
}
private fun dlsiteWorkStatusText(work: DlsiteWork, taskState: DlsiteDownloadTaskState?, contents: List<DlsiteContent>): String = when {
    contents.isNotEmpty() -> "${contents.size} 个内容"
    taskState?.status == DlsiteDownloadTaskStatus.QUEUED || work.isQueued() -> "排队中"
    taskState?.status == DlsiteDownloadTaskStatus.DOWNLOADING || work.isDownloading() -> taskState?.progressPercent?.let { "载入中 $it%" } ?: "载入中"
    taskState?.status == DlsiteDownloadTaskStatus.PAUSED || work.isPaused() -> "已暂停"
    taskState?.status == DlsiteDownloadTaskStatus.FAILED || work.isFailed() -> "载入失败"
    taskState?.status == DlsiteDownloadTaskStatus.COMPLETED -> "已完成"
    else -> "未载入"
}
private fun contentStatusText(content: DlsiteContent?, cached: Boolean = content?.isDownloaded() == true) = when {
    content == null -> "未载入"
    cached -> "已载入"
    content.isQueued() -> "排队中"
    content.isDownloading() -> "载入中"
    content.isPaused() -> "已暂停"
    content.isFailed() -> content.error.ifEmpty { "载入失败" }
    else -> "未载入"
}
private fun dlsiteContentMetaText(content: DlsiteContent, taskState: DlsiteDownloadTaskState?, cached: Boolean): String {
    val parts = mutableListOf(if (content.trackCount > 0) "音频 ${content.trackCount} 首" else "音频数待解析", if (cached) "本地缓存" else "未缓存")
    if (taskState != null && taskState.totalBytes > 0L) parts += "${formatBytes(taskState.bytesDownloaded)} / ${formatBytes(taskState.totalBytes)}"
    return parts.joinToString(" · ")
}
private fun dlsiteContentHasLocalCache(context: Context, content: DlsiteContent): Boolean {
    if (content.isDownloaded() || content.localPath.isNotEmpty()) return true
    val contentDir = File(File(File(context.filesDir, "dlsite/works/${content.workId}"), "contents"), safeDlsiteContentId(content.optionId))
    return File(contentDir, ".downloaded").isFile
}
private fun safeDlsiteContentId(optionId: String) = optionId.ifEmpty { "default" }.replace(Regex("[\\\\/:*?\"<>|]+"), "_")
private fun formatSyncTimeCompact(lastSyncMs: Long) = if (lastSyncMs <= 0L) "未同步" else SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(Date(lastSyncMs))
