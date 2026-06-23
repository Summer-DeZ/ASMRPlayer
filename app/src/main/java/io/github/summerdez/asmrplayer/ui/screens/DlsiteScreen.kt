package io.github.summerdez.asmrplayer.ui.screens

import io.github.summerdez.asmrplayer.R
import io.github.summerdez.asmrplayer.data.*
import io.github.summerdez.asmrplayer.data.remote.*
import io.github.summerdez.asmrplayer.data.download.*
import io.github.summerdez.asmrplayer.data.files.*
import io.github.summerdez.asmrplayer.domain.*
import io.github.summerdez.asmrplayer.domain.model.*
import io.github.summerdez.asmrplayer.playback.*
import io.github.summerdez.asmrplayer.presentation.*
import io.github.summerdez.asmrplayer.ui.*
import io.github.summerdez.asmrplayer.ui.activity.*
import io.github.summerdez.asmrplayer.ui.components.*
import io.github.summerdez.asmrplayer.ui.screens.*
import io.github.summerdez.asmrplayer.ui.theme.*
import io.github.summerdez.asmrplayer.ui.util.*
import io.github.summerdez.asmrplayer.di.*
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
import java.text.DateFormat
import java.util.Date
import kotlin.math.max

@Composable
fun DlsiteTab(
    state: DlsiteUiState,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onSync: () -> Unit,
    onDownload: (DlsiteWork) -> Unit,
    onPause: (DlsiteWork) -> Unit,
    onResume: (DlsiteWork) -> Unit,
    onDelete: (DlsiteWork) -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onOpenDownloadManager: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            DlsiteAccountCard(
                loggedIn = state.loggedIn,
                busy = state.busy,
                lastSyncMs = state.lastSyncMs,
                onLogin = onLogin,
                onLogout = onLogout,
                onSync = onSync,
            )
        }
        if (state.downloadState.summary.visible) {
            item {
                DlsiteTotalDownloadCard(
                    state = state.downloadState,
                    onOpen = onOpenDownloadManager,
                    onPauseAll = onPauseAll,
                    onResumeAll = onResumeAll,
                )
            }
        }
        item {
            DlsiteSectionCaption("作品")
        }
        if (state.works.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = tokens.card,
                    border = BorderStroke(0.5.dp, tokens.separator),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "同步后会在这里显示已购买作品",
                        color = tokens.label2,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            items(state.works, key = { it.workId }) { work ->
                DlsiteWorkRow(
                    work = work,
                    busy = state.busy,
                    taskState = state.downloadState.tasks[work.workId],
                    contents = state.contentsByWork[work.workId].orEmpty(),
                    onDownload = { onDownload(work) },
                    onPause = { onPause(work) },
                    onResume = { onResume(work) },
                    onDelete = { onDelete(work) },
                )
            }
        }
    }
}

@Composable
fun DlsiteWorkRow(
    work: DlsiteWork,
    busy: Boolean,
    taskState: DlsiteDownloadTaskState?,
    contents: List<DlsiteContent>,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    val statusLabel = taskState?.statusText ?: contentAwareStatusLabel(work, contents)
    val progress = dlsiteProgressFraction(work, taskState)
    val failed = taskState?.status == DlsiteDownloadTaskStatus.FAILED || work.isFailed()
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        border = BorderStroke(0.5.dp, tokens.separator),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .background(Brush.verticalGradient(listOf(tokens.cardTop, tokens.cardBottom)))
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverBox(work.coverUri, Modifier.size(58.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        work.displayTitle(),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    DlsiteStatusLine(
                        workId = work.workId,
                        status = statusLabel,
                        failed = failed,
                        badged = dlsiteShouldBadgeStatus(work, taskState, statusLabel),
                    )
                }
                Spacer(Modifier.width(10.dp))
                DlsiteWorkActions(
                    work = work,
                    busy = busy,
                    taskState = taskState,
                    onDownload = onDownload,
                    onPause = onPause,
                    onResume = onResume,
                    onDelete = onDelete,
                )
            }
            if (progress != null) {
                DlsiteProgressLine(
                    progress = progress,
                    failed = failed,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DlsiteTotalDownloadCard(
    state: DlsiteDownloadState,
    onOpen: () -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    val summary = state.summary
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        border = BorderStroke(0.5.dp, tokens.separator),
        shadowElevation = 5.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(
            modifier = Modifier
                .background(Brush.verticalGradient(listOf(tokens.cardTop, tokens.cardBottom)))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DlsiteIconBadge(Icons.AutoMirrored.Filled.QueueMusic)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "正在下载 ${summary.runningCount} / ${summary.totalCount} 项",
                        color = tokens.label,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${formatBytes(summary.speedBytesPerSecond)}/s · ${formatBytes(summary.bytesDownloaded)} / ${formatBytes(summary.totalBytes)}",
                        color = tokens.label2,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    summary.progressPercent?.let { "$it%" } ?: "--",
                    color = tokens.accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            DlsiteProgressLine(
                progress = ((summary.progressPercent ?: 0) / 100f).coerceIn(0f, 1f),
                failed = false,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("点按查看全部任务", color = tokens.label2, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = if (summary.runningCount > 0) onPauseAll else onResumeAll) {
                    Icon(
                        if (summary.runningCount > 0) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = tokens.accent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(if (summary.runningCount > 0) "全部暂停" else "全部继续", color = tokens.accent)
                }
            }
        }
    }
}

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
    val tasks = state.downloadState.tasks.values.sortedWith(
        compareBy<DlsiteDownloadTaskState> { it.status.ordinal }.thenBy { it.updatedAt },
    )
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        DlsiteSheetGrabber()
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("下载管理", color = tokens.label, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text(downloadManagerCaption(tasks, state.downloadState.summary), color = tokens.label2, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = if (state.downloadState.summary.runningCount > 0) onPauseAll else onResumeAll) {
                Icon(
                    if (state.downloadState.summary.runningCount > 0) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = tokens.accent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(if (state.downloadState.summary.runningCount > 0) "全部暂停" else "全部继续", color = tokens.accent, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(18.dp))
        if (tasks.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = tokens.card,
                border = BorderStroke(0.5.dp, tokens.separator),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("没有下载任务", color = tokens.label2, modifier = Modifier.padding(18.dp))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                tasks.forEach { task ->
                    val work = state.works.firstOrNull { it.workId == task.workId }
                    if (work != null) {
                        DlsiteDownloadTaskRow(
                            work = work,
                            busy = state.busy,
                            taskState = task,
                            contents = state.contentsByWork[work.workId].orEmpty(),
                            onPause = { onPause(work) },
                            onResume = { onResume(work) },
                            onCancel = { onCancel(work) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onClearCompleted, modifier = Modifier.align(Alignment.End)) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = tokens.label2, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(5.dp))
            Text("清除已完成", color = tokens.label2)
        }
    }
}

@Composable
fun DownloadContentsSheet(
    work: DlsiteWork,
    options: List<DlsiteDownloadOption>,
    contents: List<DlsiteContent>,
    onDismiss: () -> Unit,
    onStart: (List<DlsiteDownloadOption>) -> Unit,
    onDeleteContent: (DlsiteContent) -> Unit,
) {
    val tokens = LocalAmberTokens.current
    val contentsById = contents.associateBy { it.optionId }
    var selectedIds by remember(options) {
        mutableStateOf(emptySet<String>())
    }
    val selectableOptions = options.filter {
        contentsById[it.id]?.isDownloaded() != true &&
            contentsById[it.id]?.isDownloading() != true &&
            contentsById[it.id]?.isQueued() != true
    }
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        DlsiteSheetGrabber()
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("选择下载内容", color = tokens.label, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text(work.displayTitle(), color = tokens.label2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("已解析文件目录树，共 ${options.size} 项", color = tokens.label2, fontSize = 12.sp)
            }
            DlsiteIconActionButton(
                icon = Icons.Default.Close,
                contentDescription = "关闭",
                onClick = onDismiss,
                destructive = false,
                filled = false,
                size = 44.dp,
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("勾选文件夹或根目录音频", color = tokens.label2, fontSize = 13.sp, modifier = Modifier.weight(1f))
            TextButton(
                onClick = { selectedIds = selectableOptions.map { it.id }.toSet() },
                enabled = selectableOptions.isNotEmpty(),
            ) {
                Text("全选", color = tokens.accent)
            }
            TextButton(
                onClick = { selectedIds = emptySet() },
                enabled = selectedIds.isNotEmpty(),
            ) {
                Text("清空", color = tokens.accent2)
            }
        }
        Spacer(Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = tokens.card,
            border = BorderStroke(0.5.dp, tokens.separator),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                options.forEachIndexed { index, option ->
                    val content = contentsById[option.id]
                    val downloaded = content?.isDownloaded() == true
                    val active = content?.isDownloading() == true || content?.isQueued() == true
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !downloaded && !active) {
                                selectedIds = if (selectedIds.contains(option.id)) {
                                    selectedIds - option.id
                                } else {
                                    selectedIds + option.id
                                }
                            }
                            .padding(vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DlsiteContentCheckBox(
                            checked = selectedIds.contains(option.id) || downloaded,
                            disabled = active,
                        )
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(option.title, color = tokens.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${option.audioFiles.size} 首 · ${contentStatusText(content)}", color = tokens.label2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        when {
                            downloaded -> TextButton(onClick = { onDeleteContent(content) }) {
                                Text("删除", color = tokens.accent2)
                            }
                            active -> DlsiteStatusPill("下载中", failed = false)
                            content?.isFailed() == true -> DlsiteStatusPill("失败", failed = true)
                        }
                    }
                    if (index != options.lastIndex) {
                        HorizontalDivider(color = tokens.separator)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("已选择 ${selectedIds.size} 项", color = tokens.label2, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Button(
                onClick = { onStart(options.filter { selectedIds.contains(it.id) }) },
                enabled = selectedIds.isNotEmpty(),
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("加入下载")
            }
        }
    }
}

@Composable
private fun DlsiteAccountCard(
    loggedIn: Boolean,
    busy: Boolean,
    lastSyncMs: Long,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onSync: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        border = BorderStroke(0.5.dp, tokens.separator),
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .background(Brush.verticalGradient(listOf(tokens.cardTop, tokens.cardBottom)))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DlsiteIconBadge(if (loggedIn) Icons.Default.LockOpen else Icons.AutoMirrored.Filled.Login, size = 64.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (loggedIn) "已登录" else "未登录", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = tokens.label)
                Text(
                    if (busy) "DLsite 正在处理请求" else "上次同步 ${formatSyncTime(lastSyncMs)}",
                    color = tokens.label2,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (loggedIn) {
                    TextButton(onClick = onLogout, enabled = !busy, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = tokens.label2, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("退出登录", color = tokens.label2, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            DlsiteIconActionButton(
                icon = if (loggedIn) Icons.Default.Download else Icons.AutoMirrored.Filled.Login,
                contentDescription = if (loggedIn) "同步" else "登录",
                onClick = if (loggedIn) onSync else onLogin,
                enabled = if (loggedIn) !busy else !busy,
                filled = true,
                size = 56.dp,
            )
        }
    }
}

@Composable
private fun DlsiteWorkActions(
    work: DlsiteWork,
    busy: Boolean,
    taskState: DlsiteDownloadTaskState?,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    val status = taskState?.status
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            status == DlsiteDownloadTaskStatus.QUEUED || work.isQueued() -> {
                DlsiteIconActionButton(Icons.Default.Close, "取消", onDelete, enabled = !busy, destructive = true)
            }
            status == DlsiteDownloadTaskStatus.DOWNLOADING || work.isDownloading() -> {
                DlsiteIconActionButton(Icons.Default.Pause, "暂停", onPause, enabled = !busy)
            }
            status == DlsiteDownloadTaskStatus.PAUSED || work.isPaused() -> {
                DlsiteIconActionButton(Icons.Default.PlayArrow, "继续", onResume, enabled = !busy)
                DlsiteIconActionButton(Icons.Default.Delete, "删除", onDelete, enabled = !busy, destructive = true, size = 44.dp)
            }
            status == DlsiteDownloadTaskStatus.FAILED || work.isFailed() -> {
                DlsiteIconActionButton(Icons.Default.Download, "重试", onResume, enabled = !busy)
                DlsiteIconActionButton(Icons.Default.Delete, "删除", onDelete, enabled = !busy, destructive = true, size = 44.dp)
            }
            work.isDownloaded() -> {
                DlsiteIconActionButton(Icons.Default.FolderOpen, "选择内容", onDownload, enabled = !busy, size = 44.dp)
                DlsiteIconActionButton(Icons.Default.Delete, "删除缓存", onDelete, enabled = !busy, destructive = true, size = 44.dp)
            }
            else -> {
                DlsiteIconActionButton(Icons.Default.Download, "下载", onDownload, enabled = !busy)
            }
        }
    }
}

@Composable
private fun DlsiteDownloadTaskRow(
    work: DlsiteWork,
    busy: Boolean,
    taskState: DlsiteDownloadTaskState,
    contents: List<DlsiteContent>,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    val statusLabel = taskState.statusText.ifEmpty { contentAwareStatusLabel(work, contents) }
    val progress = dlsiteProgressFraction(work, taskState)
    val failed = taskState.status == DlsiteDownloadTaskStatus.FAILED || work.isFailed()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverBox(work.coverUri, Modifier.size(56.dp))
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(work.displayTitle(), color = tokens.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                DlsiteStatusLine(
                    workId = work.workId,
                    status = statusLabel,
                    failed = failed,
                    badged = dlsiteShouldBadgeStatus(work, taskState, statusLabel),
                )
            }
            Spacer(Modifier.width(10.dp))
            when (taskState.status) {
                DlsiteDownloadTaskStatus.QUEUED -> DlsiteIconActionButton(Icons.Default.Close, "取消", onCancel, enabled = !busy, destructive = true)
                DlsiteDownloadTaskStatus.DOWNLOADING -> DlsiteIconActionButton(Icons.Default.Pause, "暂停", onPause, enabled = !busy)
                DlsiteDownloadTaskStatus.PAUSED -> DlsiteIconActionButton(Icons.Default.PlayArrow, "继续", onResume, enabled = !busy)
                DlsiteDownloadTaskStatus.FAILED -> DlsiteIconActionButton(Icons.Default.Download, "重试", onResume, enabled = !busy)
                DlsiteDownloadTaskStatus.COMPLETED -> DlsiteIconActionButton(Icons.Default.Delete, "清除", onCancel, enabled = !busy, destructive = true)
            }
        }
        if (progress != null) {
            DlsiteProgressLine(progress, failed, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DlsiteStatusLine(
    workId: String,
    status: String,
    failed: Boolean,
    badged: Boolean,
) {
    val tokens = LocalAmberTokens.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(workId, color = tokens.label2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(" · ", color = tokens.label2, fontSize = 13.sp)
        if (badged) {
            DlsiteStatusPill(status, failed)
        } else {
            Text(
                status,
                color = if (failed) tokens.accent2 else tokens.label2,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DlsiteStatusPill(text: String, failed: Boolean) {
    val tokens = LocalAmberTokens.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (failed) tokens.accent2Soft else tokens.accentSoft,
    ) {
        Text(
            text,
            color = if (failed) tokens.accent2 else tokens.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun DlsiteSectionCaption(text: String) {
    val tokens = LocalAmberTokens.current
    Text(
        text,
        color = tokens.label2,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 2.dp, top = 4.dp),
    )
}

@Composable
private fun DlsiteIconBadge(icon: ImageVector, size: androidx.compose.ui.unit.Dp = 48.dp) {
    val tokens = LocalAmberTokens.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = tokens.accentSoft,
        border = BorderStroke(0.5.dp, tokens.separator),
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tokens.accent, modifier = Modifier.size(size * 0.42f))
        }
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
    size: androidx.compose.ui.unit.Dp = 56.dp,
) {
    val tokens = LocalAmberTokens.current
    val background = when {
        !enabled -> tokens.gray5
        filled -> tokens.accent
        destructive -> tokens.accent2Soft
        else -> tokens.accentSoft
    }
    val tint = when {
        !enabled -> tokens.label3
        filled -> tokens.bg
        destructive -> tokens.accent2
        else -> tokens.accent
    }
    Surface(
        shape = RoundedCornerShape(if (size >= 52.dp) 18.dp else 14.dp),
        color = background,
        border = if (filled) null else BorderStroke(0.5.dp, tokens.separator),
        modifier = Modifier
            .size(size)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(if (size >= 52.dp) 25.dp else 21.dp))
        }
    }
}

@Composable
private fun DlsiteProgressLine(
    progress: Float,
    failed: Boolean,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    Box(
        modifier
            .height(5.dp)
            .background(tokens.label3.copy(alpha = 0.28f), RoundedCornerShape(3.dp)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(if (failed) tokens.accent2 else tokens.accent, RoundedCornerShape(3.dp)),
        )
    }
}

@Composable
private fun DlsiteSheetGrabber() {
    val tokens = LocalAmberTokens.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Box(
            Modifier
                .width(42.dp)
                .height(5.dp)
                .background(tokens.label3, RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center,
        ) {}
    }
}

@Composable
private fun DlsiteContentCheckBox(checked: Boolean, disabled: Boolean) {
    val tokens = LocalAmberTokens.current
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = when {
            checked -> tokens.accent
            disabled -> tokens.gray5
            else -> Color.Transparent
        },
        border = BorderStroke(1.dp, if (checked) tokens.accent else tokens.separator),
        modifier = Modifier.size(24.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (checked) {
                Text("✓", color = tokens.bg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun dlsiteProgressFraction(work: DlsiteWork, taskState: DlsiteDownloadTaskState?): Float? {
    val progress = taskState?.progressPercent
    return when {
        progress != null -> (progress.coerceIn(0, 100) / 100f)
        taskState?.status == DlsiteDownloadTaskStatus.FAILED || work.isFailed() -> 1f
        else -> null
    }
}

private fun dlsiteShouldBadgeStatus(
    work: DlsiteWork,
    taskState: DlsiteDownloadTaskState?,
    statusLabel: String,
): Boolean {
    return when {
        taskState?.status == DlsiteDownloadTaskStatus.QUEUED -> true
        taskState?.status == DlsiteDownloadTaskStatus.FAILED -> true
        taskState?.status == DlsiteDownloadTaskStatus.COMPLETED -> true
        work.isDownloaded() || work.isQueued() || work.isFailed() -> true
        statusLabel.startsWith("已下载") -> true
        else -> false
    }
}

private fun downloadManagerCaption(
    tasks: List<DlsiteDownloadTaskState>,
    summary: DlsiteDownloadSummary,
): String {
    return when {
        summary.totalCount > 0 -> "${summary.totalCount} 个任务进行中"
        tasks.isNotEmpty() -> "${tasks.size} 个任务"
        else -> "没有下载任务"
    }
}

private fun contentAwareStatusLabel(work: DlsiteWork, contents: List<DlsiteContent>): String {
    val downloadedCount = contents.count { it.isDownloaded() }
    return when {
        downloadedCount > 0 && downloadedCount < contents.size -> "已下载 $downloadedCount / ${contents.size} 项"
        downloadedCount > 0 -> work.statusLabel()
        else -> work.statusLabel()
    }
}

private fun contentStatusText(content: DlsiteContent?): String {
    return when {
        content == null -> "未下载"
        content.isDownloaded() -> "已下载"
        content.isQueued() -> "排队中"
        content.isDownloading() -> "下载中"
        content.isPaused() -> "已暂停"
        content.isFailed() -> content.error.ifEmpty { "失败" }
        else -> "未下载"
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) {
        return "--"
    }
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return if (unit == 0) {
        "${value.toLong()} ${units[unit]}"
    } else {
        "%.1f %s".format(value, units[unit])
    }
}
