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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Surface(shape = RoundedCornerShape(16.dp), color = tokens.card) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (state.loggedIn) "DLsite 已登录" else "DLsite 未登录", fontWeight = FontWeight.SemiBold, color = tokens.label)
                    Text(
                        "上次同步：${formatSyncTime(state.lastSyncMs)}",
                        color = tokens.label2,
                        fontSize = 12.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onLogin, enabled = !state.busy) {
                            Icon(Icons.AutoMirrored.Filled.Login, null)
                            Spacer(Modifier.width(6.dp))
                            Text("登录")
                        }
                        Button(onClick = onSync, enabled = state.loggedIn && !state.busy) {
                            Icon(Icons.Default.Download, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.busy) "处理中" else "同步")
                        }
                        TextButton(onClick = onLogout, enabled = state.loggedIn && !state.busy) {
                            Icon(Icons.AutoMirrored.Filled.Logout, null)
                            Spacer(Modifier.width(6.dp))
                            Text("退出")
                        }
                    }
                }
            }
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
        if (state.works.isEmpty()) {
            item {
                Text(
                    "同步后会在这里显示已购买作品",
                    color = tokens.label2,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
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
    Surface(shape = RoundedCornerShape(16.dp), color = tokens.card) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverBox(work.coverUri, Modifier.size(54.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(work.displayTitle(), fontSize = 17.sp, fontWeight = FontWeight.Medium, color = tokens.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val statusLabel = taskState?.statusText ?: contentAwareStatusLabel(work, contents)
                Text("${work.workId} · $statusLabel", color = tokens.label2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (taskState?.progressPercent != null || work.isFailed()) {
                    val progress = (taskState?.progressPercent ?: 100).coerceIn(0, 100) / 100f
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(tokens.label3.copy(alpha = 0.28f), RoundedCornerShape(3.dp)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .background(if (work.isFailed()) tokens.accent2 else tokens.accent, RoundedCornerShape(3.dp)),
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            when {
                taskState?.status == DlsiteDownloadTaskStatus.QUEUED -> {
                    TextButton(onClick = onDelete) { Text("取消", color = tokens.accent2) }
                }
                taskState?.status == DlsiteDownloadTaskStatus.DOWNLOADING || work.isDownloading() -> {
                    TextButton(onClick = onPause) { Text("暂停", color = tokens.accent) }
                }
                work.isPaused() -> {
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = onResume, enabled = !busy) { Text("继续") }
                        TextButton(onClick = onDelete, enabled = !busy) { Text("删除", color = tokens.accent2) }
                    }
                }
                work.isFailed() -> {
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = onResume, enabled = !busy) { Text("重试") }
                        TextButton(onClick = onDelete, enabled = !busy) { Text("删除", color = tokens.accent2) }
                    }
                }
                work.isDownloaded() -> {
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = onDownload, enabled = !busy) {
                            Icon(Icons.Default.Download, null, tint = tokens.accent)
                            Spacer(Modifier.width(4.dp))
                            Text("内容", color = tokens.accent)
                        }
                        TextButton(onClick = onDelete, enabled = !busy) {
                            Icon(Icons.Default.Delete, null, tint = tokens.accent2)
                            Spacer(Modifier.width(4.dp))
                            Text("删除", color = tokens.accent2)
                        }
                    }
                }
                else -> {
                    TextButton(onClick = onDownload, enabled = !busy) {
                        Icon(Icons.Default.Download, null, tint = tokens.accent)
                        Spacer(Modifier.width(4.dp))
                        Text("下载", color = tokens.accent)
                    }
                }
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
        shape = RoundedCornerShape(16.dp),
        color = tokens.card,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "正在下载 ${summary.runningCount} / ${summary.totalCount} 项",
                    color = tokens.label,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    summary.progressPercent?.let { "$it%" } ?: "--",
                    color = tokens.accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(tokens.label3.copy(alpha = 0.28f), RoundedCornerShape(3.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((summary.progressPercent ?: 0) / 100f)
                        .background(tokens.accent, RoundedCornerShape(3.dp)),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatBytes(summary.speedBytesPerSecond)}/s · ${formatBytes(summary.bytesDownloaded)} / ${formatBytes(summary.totalBytes)}",
                    color = tokens.label2,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = if (summary.runningCount > 0) onPauseAll else onResumeAll) {
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
    Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
        Box(
            Modifier
                .width(38.dp)
                .height(5.dp)
                .background(tokens.label3, RoundedCornerShape(3.dp))
                .align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("下载管理", color = tokens.label, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = if (state.downloadState.summary.runningCount > 0) onPauseAll else onResumeAll) {
                Text(if (state.downloadState.summary.runningCount > 0) "全部暂停" else "全部继续", color = tokens.accent)
            }
        }
        DlsiteTotalDownloadCard(state.downloadState, onOpen = {}, onPauseAll = onPauseAll, onResumeAll = onResumeAll)
        Spacer(Modifier.height(10.dp))
        if (tasks.isEmpty()) {
            Text("没有下载任务", color = tokens.label2, modifier = Modifier.padding(vertical = 24.dp))
        } else {
            tasks.forEach { task ->
                val work = state.works.firstOrNull { it.workId == task.workId }
                if (work != null) {
                    DlsiteWorkRow(
                        work = work,
                        busy = state.busy,
                        taskState = task,
                        contents = state.contentsByWork[work.workId].orEmpty(),
                        onDownload = {},
                        onPause = { onPause(work) },
                        onResume = { onResume(work) },
                        onDelete = { onCancel(work) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        TextButton(onClick = onClearCompleted, modifier = Modifier.align(Alignment.End)) {
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
        mutableStateOf(
            options
                .filter { contentsById[it.id]?.isDownloaded() != true && contentsById[it.id]?.isDownloading() != true }
                .map { it.id }
                .toSet(),
        )
    }
    Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
        Box(
            Modifier
                .width(38.dp)
                .height(5.dp)
                .background(tokens.label3, RoundedCornerShape(3.dp))
                .align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("下载内容", color = tokens.label, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(work.displayTitle(), color = tokens.label2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = null, tint = tokens.label2)
            }
        }
        options.forEach { option ->
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
                Box(
                    Modifier
                        .size(22.dp)
                        .background(
                            if (selectedIds.contains(option.id) || downloaded) tokens.accent else Color.Transparent,
                            RoundedCornerShape(6.dp),
                        )
                        .padding(2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selectedIds.contains(option.id) || downloaded) {
                        Text("✓", color = tokens.bg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(option.title, color = tokens.label, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${option.audioFiles.size} 首 · ${contentStatusText(content)}", color = tokens.label2, fontSize = 12.sp)
                }
                when {
                    downloaded -> TextButton(onClick = { onDeleteContent(content) }) {
                        Text("删除", color = tokens.accent2)
                    }
                    active -> Text("下载中", color = tokens.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    content?.isFailed() == true -> Text("失败", color = tokens.accent2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            HorizontalDivider(color = tokens.separator)
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("已选择 ${selectedIds.size} 项", color = tokens.label2, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Button(
                onClick = { onStart(options.filter { selectedIds.contains(it.id) }) },
                enabled = selectedIds.isNotEmpty(),
            ) {
                Text("加入下载")
            }
        }
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
