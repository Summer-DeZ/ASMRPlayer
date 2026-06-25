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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
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
import io.github.summerdez.asmrplayer.data.DlsiteDownloadSummary
import io.github.summerdez.asmrplayer.data.DlsiteDownloadTaskState
import io.github.summerdez.asmrplayer.data.DlsiteDownloadTaskStatus
import io.github.summerdez.asmrplayer.domain.model.DlsiteContent
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import io.github.summerdez.asmrplayer.presentation.DlsiteUiState
import io.github.summerdez.asmrplayer.ui.components.CoverBox
import io.github.summerdez.asmrplayer.ui.theme.LocalAmberTokens
import io.github.summerdez.asmrplayer.ui.uiProbe
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import kotlin.math.max

private val DlsiteBlue = Color(0xFF4F9BE0)
private val DlsiteBlueSoft = Color(0x294F9BE0)
private val DlsiteBlueBorder = Color(0x3D4F9BE0)

@Composable
fun DlsiteTab(
    state: DlsiteUiState,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onSync: () -> Unit,
    onDownload: (DlsiteWork) -> Unit,
    onDownloadContent: (DlsiteWork, DlsiteContent) -> Unit,
    onDeleteContent: (DlsiteWork, DlsiteContent) -> Unit,
    onPause: (DlsiteWork) -> Unit,
    onResume: (DlsiteWork) -> Unit,
    onDelete: (DlsiteWork) -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onOpenDownloadManager: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    val offlineWorkCount = state.works.count { work ->
        state.contentsByWork[work.workId].orEmpty().any { it.isDownloaded() }
    }
    var expandedWorkId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.works.map { it.workId }) {
        if (expandedWorkId != null && state.works.none { it.workId == expandedWorkId }) {
            expandedWorkId = null
        }
    }
    val activeContentTaskWorkId = state.downloadState.tasks.values.firstOrNull { task ->
        task.contentId.isNotBlank() && task.status != DlsiteDownloadTaskStatus.COMPLETED
    }?.workId
    LaunchedEffect(activeContentTaskWorkId) {
        if (!activeContentTaskWorkId.isNullOrEmpty()) {
            expandedWorkId = activeContentTaskWorkId
        }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .uiProbe("dlsite.root", "DLsite 页面列表", "DlsiteScreen.kt"),
        contentPadding = PaddingValues(start = 22.dp, top = 4.dp, end = 22.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            DlsiteAccountCard(
                loggedIn = state.loggedIn,
                busy = state.busy,
                lastSyncMs = state.lastSyncMs,
                workCount = state.works.size,
                offlineWorkCount = offlineWorkCount,
                downloadSummary = state.downloadState.summary,
                onLogin = onLogin,
                onLogout = onLogout,
                onSync = onSync,
            )
        }
        item {
            DlsiteSectionCaption(
                text = "当前 DLsite 内容",
            )
        }
        if (state.works.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(28.dp),
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
                val contents = state.contentsByWork[work.workId].orEmpty()
                val expanded = contents.isNotEmpty() && expandedWorkId == work.workId
                DlsiteWorkRow(
                    work = work,
                    busy = state.busy,
                    taskState = state.downloadState.tasks[work.workId],
                    contents = contents,
                    expanded = expanded,
                    onToggleExpanded = {
                        expandedWorkId = if (expanded) null else work.workId
                    },
                    onDownload = {
                        expandedWorkId = work.workId
                        onDownload(work)
                    },
                    onDownloadContent = { content ->
                        expandedWorkId = work.workId
                        onDownloadContent(work, content)
                    },
                    onDeleteContent = { content -> onDeleteContent(work, content) },
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
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220),
        label = "dlsiteWorkChevron",
    )
    val noAction: () -> Unit = {}
    val actionClick = when (actionState) {
        DlsiteRowActionState.Download -> onDownload
        DlsiteRowActionState.Ready -> noAction
        DlsiteRowActionState.Queued -> onDelete
        DlsiteRowActionState.Downloading -> onPause
        DlsiteRowActionState.Paused -> onResume
        DlsiteRowActionState.Failed -> onResume
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = tokens.card,
        border = BorderStroke(0.5.dp, tokens.separator),
        modifier = Modifier
            .fillMaxWidth()
            .uiProbe(
                id = "dlsite.work-row:${work.workId}",
                label = "DLsite 作品卡：${work.displayTitle()}",
                sourceHint = "DlsiteScreen.kt",
                metadata = mapOf(
                    "workId" to work.workId,
                    "status" to statusLabel,
                    "action" to actionState.name,
                    "expanded" to expanded.toString(),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .background(tokens.card)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = hasContents, onClick = onToggleExpanded),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverBox(work.coverUri, Modifier.size(52.dp))
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        work.displayTitle(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        work.workId,
                        color = tokens.label2,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                if (hasContents) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .graphicsLayer { rotationZ = chevronRotation },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expanded) "收起内容" else "展开内容",
                            tint = tokens.accent,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                } else {
                    DlsiteDownloadStatusControl(
                        state = actionState,
                        progressPercent = workTaskState?.progressPercent,
                        enabled = !busy && actionState != DlsiteRowActionState.Ready,
                        onClick = actionClick,
                    )
                }
            }
            if (progress != null) {
                DlsiteProgressLine(
                    progress = progress,
                    failed = failed,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            DlsiteWorkContentPanel(
                busy = busy,
                taskState = taskState,
                actionState = actionState,
                contents = contents,
                expanded = expanded,
                onDownloadContent = onDownloadContent,
                onDeleteContent = onDeleteContent,
                onPause = onPause,
                onResume = onResume,
                onDelete = onDelete,
            )
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
    if ((!expanded || contents.isEmpty()) && !showActions) {
        return
    }
    AnimatedVisibility(
        visible = expanded || showActions,
        enter = expandVertically(animationSpec = tween(260)) + fadeIn(animationSpec = tween(200)),
        exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(animationSpec = tween(150)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HorizontalDivider(color = tokens.separator)
            if (expanded && contents.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    contents.forEachIndexed { index, content ->
                        DlsiteContentRow(
                            content = content,
                            taskState = taskState?.takeIf { it.contentId == content.optionId },
                            busy = busy,
                            onDownload = { onDownloadContent(content) },
                            onDelete = { onDeleteContent(content) },
                            onPause = onPause,
                            onResume = onResume,
                        )
                        if (index != contents.lastIndex) {
                            HorizontalDivider(color = tokens.separator)
                        }
                    }
                }
            }
            if (showActions) {
                DlsiteExpandedActionRow(
                    busy = busy,
                    actionState = actionState,
                    onPause = onPause,
                    onResume = onResume,
                    onDelete = onDelete,
                )
            }
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
    val cached = remember(
        content.workId,
        content.optionId,
        content.status,
        content.localPath,
        content.updatedAt,
        taskState?.status,
    ) {
        dlsiteContentHasLocalCache(context, content)
    }
    val progress = dlsiteContentProgressFraction(content, taskState)
    val failed = taskState?.status == DlsiteDownloadTaskStatus.FAILED || content.isFailed()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .uiProbe(
                id = "dlsite.content-row:${content.optionId}",
                label = "DLsite 内容行：${content.title.ifEmpty { "默认版本" }}",
                sourceHint = "DlsiteScreen.kt",
                metadata = mapOf(
                    "optionId" to content.optionId,
                    "status" to (taskState?.statusText ?: contentStatusText(content, cached)),
                    "cached" to cached.toString(),
                ),
            )
            .padding(horizontal = 2.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DlsiteContentStatusDot(downloaded = cached)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    content.title.ifEmpty { "默认版本" },
                    color = tokens.label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    dlsiteContentMetaText(content, taskState, cached),
                    color = tokens.label2,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(contentAlignment = Alignment.CenterEnd) {
                when {
                    content.isDownloading() || taskState?.status == DlsiteDownloadTaskStatus.DOWNLOADING -> {
                        DlsiteSoftActionPill(
                            icon = Icons.Default.Pause,
                            text = "暂停",
                            onClick = onPause,
                            enabled = !busy,
                        )
                    }
                    content.isQueued() || taskState?.status == DlsiteDownloadTaskStatus.QUEUED -> {
                        DlsiteStatusPill("排队中", failed = false)
                    }
                    content.isPaused() || taskState?.status == DlsiteDownloadTaskStatus.PAUSED -> {
                        DlsiteSoftActionPill(
                            icon = Icons.Default.PlayArrow,
                            text = "继续",
                            onClick = onResume,
                            enabled = !busy,
                        )
                    }
                    content.isFailed() || taskState?.status == DlsiteDownloadTaskStatus.FAILED -> {
                        DlsiteSoftActionPill(
                            icon = Icons.Default.Download,
                            text = "重试",
                            onClick = onDownload,
                            enabled = !busy,
                        )
                    }
                    cached -> {
                        DlsiteSoftActionPill(
                            icon = Icons.Default.Delete,
                            text = "删除",
                            onClick = onDelete,
                            enabled = !busy,
                        )
                    }
                    else -> {
                        DlsiteSoftActionPill(
                            icon = Icons.Default.Download,
                            text = "下载",
                            onClick = onDownload,
                            enabled = !busy,
                        )
                    }
                }
            }
        }
        if (progress != null) {
            DlsiteProgressLine(
                progress = progress,
                failed = failed,
                modifier = Modifier
                    .padding(start = 39.dp, top = 8.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DlsiteContentStatusDot(
    downloaded: Boolean,
) {
    val tokens = LocalAmberTokens.current
    val color = if (downloaded) tokens.accent else tokens.label3
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(0.5.dp, color.copy(alpha = if (downloaded) 0.55f else 0.35f)),
        modifier = Modifier.size(28.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun DlsiteExpandedActionRow(
    busy: Boolean,
    actionState: DlsiteRowActionState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (actionState) {
            DlsiteRowActionState.Queued -> {
                DlsiteSoftActionPill(
                    icon = Icons.Default.Close,
                    text = "取消",
                    onClick = onDelete,
                    enabled = !busy,
                )
            }
            DlsiteRowActionState.Downloading -> {
                DlsiteSoftActionPill(
                    icon = Icons.Default.Pause,
                    text = "暂停",
                    onClick = onPause,
                    enabled = !busy,
                )
            }
            DlsiteRowActionState.Paused -> {
                DlsiteSoftActionPill(
                    icon = Icons.Default.PlayArrow,
                    text = "继续",
                    onClick = onResume,
                    enabled = !busy,
                )
                DlsiteSoftActionPill(
                    icon = Icons.Default.Delete,
                    text = "删除缓存",
                    onClick = onDelete,
                    enabled = !busy,
                )
            }
            DlsiteRowActionState.Failed -> {
                DlsiteSoftActionPill(
                    icon = Icons.Default.Download,
                    text = "重试",
                    onClick = onResume,
                    enabled = !busy,
                )
                DlsiteSoftActionPill(
                    icon = Icons.Default.Delete,
                    text = "删除缓存",
                    onClick = onDelete,
                    enabled = !busy,
                )
            }
            DlsiteRowActionState.Ready -> Unit
            DlsiteRowActionState.Download -> Unit
        }
    }
}

private enum class DlsiteRowActionState {
    Download,
    Ready,
    Queued,
    Downloading,
    Paused,
    Failed,
}

private fun dlsiteRowActionState(
    work: DlsiteWork,
    taskState: DlsiteDownloadTaskState?,
    contents: List<DlsiteContent>,
): DlsiteRowActionState {
    return when {
        contents.isNotEmpty() -> DlsiteRowActionState.Ready
        taskState?.status == DlsiteDownloadTaskStatus.QUEUED || work.isQueued() -> DlsiteRowActionState.Queued
        taskState?.status == DlsiteDownloadTaskStatus.DOWNLOADING || work.isDownloading() -> DlsiteRowActionState.Downloading
        taskState?.status == DlsiteDownloadTaskStatus.PAUSED || work.isPaused() -> DlsiteRowActionState.Paused
        taskState?.status == DlsiteDownloadTaskStatus.FAILED || work.isFailed() -> DlsiteRowActionState.Failed
        else -> DlsiteRowActionState.Download
    }
}

private fun dlsiteExpandedActionsVisible(actionState: DlsiteRowActionState): Boolean {
    return when (actionState) {
        DlsiteRowActionState.Queued,
        DlsiteRowActionState.Downloading,
        DlsiteRowActionState.Paused,
        DlsiteRowActionState.Failed -> true
        DlsiteRowActionState.Download,
        DlsiteRowActionState.Ready -> false
    }
}

@Composable
private fun DlsiteDownloadStatusControl(
    state: DlsiteRowActionState,
    progressPercent: Int?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
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
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = text,
                tint = if (enabled) color else tokens.label3,
                modifier = Modifier.size(21.dp),
            )
        }
        return
    }
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = text, tint = if (enabled) color else tokens.label3, modifier = Modifier.size(20.dp))
        Text(
            text,
            color = if (enabled) color else tokens.label3,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
            .uiProbe("dlsite.download-manager-sheet", "DLsite 下载管理 Sheet", "DlsiteScreen.kt")
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
private fun DlsiteAccountCard(
    loggedIn: Boolean,
    busy: Boolean,
    lastSyncMs: Long,
    workCount: Int,
    offlineWorkCount: Int,
    downloadSummary: DlsiteDownloadSummary,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onSync: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    val context = LocalContext.current
    if (!loggedIn) {
        Column(
            modifier = Modifier.uiProbe("dlsite.account-card", "DLsite 未登录账号卡", "DlsiteScreen.kt"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.Transparent,
                border = BorderStroke(0.5.dp, DlsiteBlueBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .background(Brush.linearGradient(listOf(DlsiteBlueSoft, tokens.card)))
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DlsiteLogoBadge(size = 50.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                "同步 DLsite 音声作品",
                                color = tokens.label,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "已购买的作品，自动出现在资料库",
                                color = tokens.label2,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            DlsiteVisualInput(
                label = "DLsite 账号 / 邮箱",
                icon = Icons.Default.Person,
                placeholder = "your_id@dlsite.com",
                onClick = onLogin,
            )
            DlsiteVisualInput(
                label = "密码",
                icon = Icons.Default.Lock,
                placeholder = "••••••••",
                onClick = onLogin,
            )
            DlsiteFilledActionPill(
                icon = Icons.Default.Download,
                text = if (busy) "正在登录并同步..." else "登录并同步",
                onClick = onLogin,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF84C2A3), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "登录信息仅用于同步，加密保存在本设备",
                    color = tokens.label3,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "没有账户？前往 DLsite 注册",
                color = DlsiteBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.dlsite.com/maniax/regist"))
                        runCatching { context.startActivity(intent) }
                            .onFailure {
                                Toast.makeText(context, "无法打开注册链接", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .padding(top = 2.dp),
            )
        }
        return
    }

    Column(
        modifier = Modifier.uiProbe(
            id = "dlsite.account-card",
            label = "DLsite 已登录账号卡",
            sourceHint = "DlsiteScreen.kt",
            metadata = mapOf(
                "workCount" to workCount.toString(),
                "offlineWorkCount" to offlineWorkCount.toString(),
                "busy" to busy.toString(),
            ),
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = tokens.card,
            border = BorderStroke(0.5.dp, tokens.separator),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .background(tokens.card)
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DlsiteLogoBadge(size = 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "DLsite",
                        color = tokens.label,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "已连接 · 已同步 ${workCount} 个作品",
                        color = tokens.label2,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    DlsiteStatusPill("已连接", failed = false)
                    TextButton(
                        onClick = onLogout,
                        enabled = !busy,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = tokens.label2, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("退出登录", color = tokens.label2, fontSize = 13.sp)
                    }
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = tokens.card,
            border = BorderStroke(0.5.dp, tokens.separator),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .background(tokens.card)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            "同步状态",
                            color = tokens.label,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (busy) "正在同步..." else "上次同步 · ${formatSyncTimeCompact(lastSyncMs)}",
                            color = tokens.label2,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    DlsiteSoftActionPill(
                        icon = Icons.Default.Sync,
                        text = if (busy) "同步中" else "重新同步",
                        onClick = onSync,
                        enabled = !busy,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    DlsiteStat("${workCount}", "已同步作品", Modifier.weight(1f))
                    DlsiteStat("${offlineWorkCount}", "可离线作品", Modifier.weight(1f))
                    DlsiteStat(
                        if (downloadSummary.totalCount > 0) "${downloadSummary.runningCount}/${downloadSummary.totalCount}" else "0",
                        "下载任务",
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DlsiteVisualInput(
    label: String,
    icon: ImageVector,
    placeholder: String,
    onClick: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    Column(
        modifier = Modifier.uiProbe(
            id = "dlsite.visual-input:$label",
            label = "DLsite 登录输入框：$label",
            sourceHint = "DlsiteScreen.kt",
            metadata = mapOf(
                "placeholder" to placeholder,
            ),
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            color = tokens.label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(20.dp),
            color = tokens.label.copy(alpha = 0.05f),
            border = BorderStroke(1.dp, tokens.separator),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = tokens.label3, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    placeholder,
                    color = tokens.label3,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DlsiteStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            value,
            color = tokens.label,
            fontSize = 26.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            label,
            color = tokens.label2,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DlsiteFilledActionPill(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (enabled) DlsiteBlue else tokens.gray5,
        modifier = modifier
            .height(48.dp)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) Color.White else tokens.label3,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text,
                color = if (enabled) Color.White else tokens.label3,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DlsiteSoftActionPill(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val tokens = LocalAmberTokens.current
    val accentSoft = tokens.accent.copy(alpha = 0.14f)
    val accentBorder = tokens.accent.copy(alpha = 0.34f)
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (enabled) accentSoft else tokens.gray5,
        border = BorderStroke(0.5.dp, if (enabled) accentBorder else tokens.separator),
        modifier = Modifier
            .height(38.dp)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) tokens.accent else tokens.label3,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text,
                color = if (enabled) tokens.accent else tokens.label3,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DlsiteDownloadTaskRow(
    work: DlsiteWork,
    busy: Boolean,
    taskState: DlsiteDownloadTaskState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    val statusLabel = dlsiteTaskStatusText(taskState)
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
) {
    val tokens = LocalAmberTokens.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            workId,
            color = tokens.label2,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(6.dp))
        DlsiteStatusPill(status, failed)
    }
}

@Composable
private fun DlsiteStatusPill(text: String, failed: Boolean) {
    val tokens = LocalAmberTokens.current
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (failed) tokens.accent2Soft else tokens.accent.copy(alpha = 0.14f),
        border = BorderStroke(0.5.dp, if (failed) tokens.accent2.copy(alpha = 0.45f) else tokens.accent.copy(alpha = 0.34f)),
    ) {
        Text(
            text,
            color = if (failed) tokens.accent2 else tokens.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun DlsiteSectionCaption(
    text: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val tokens = LocalAmberTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            color = tokens.label,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Text(
                action,
                color = tokens.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(enabled = onAction != null) { onAction?.invoke() },
            )
        }
    }
}

@Composable
private fun DlsiteLogoBadge(size: androidx.compose.ui.unit.Dp) {
    Surface(
        shape = RoundedCornerShape(if (size >= 60.dp) 18.dp else 14.dp),
        color = DlsiteBlue,
        border = BorderStroke(0.5.dp, DlsiteBlueBorder),
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "DL",
                color = Color.White,
                fontSize = (size.value * 0.34f).sp,
                lineHeight = (size.value * 0.34f).sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun DlsiteIconBadge(icon: ImageVector, size: androidx.compose.ui.unit.Dp = 48.dp) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DlsiteBlueSoft,
        border = BorderStroke(0.5.dp, DlsiteBlueBorder),
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = DlsiteBlue, modifier = Modifier.size(size * 0.42f))
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
        filled -> DlsiteBlue
        destructive -> tokens.accent2Soft
        else -> DlsiteBlueSoft
    }
    val tint = when {
        !enabled -> tokens.label3
        filled -> Color.White
        destructive -> tokens.accent2
        else -> DlsiteBlue
    }
    Surface(
        shape = RoundedCornerShape(if (size >= 52.dp) 18.dp else 14.dp),
        color = background,
        border = if (filled) null else BorderStroke(0.5.dp, if (destructive) tokens.separator else DlsiteBlueBorder),
        modifier = Modifier
            .size(size)
            .uiProbe(
                id = "dlsite.action.${contentDescription.ifBlank { "button" }}",
                label = "DLsite 操作按钮：$contentDescription",
                sourceHint = "DlsiteScreen.kt",
                metadata = mapOf("enabled" to enabled.toString()),
            )
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
            .height(3.dp)
            .background(tokens.label3.copy(alpha = 0.18f), RoundedCornerShape(3.dp)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(if (failed) tokens.accent2 else DlsiteBlue, RoundedCornerShape(3.dp)),
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

private fun dlsiteProgressFraction(work: DlsiteWork, taskState: DlsiteDownloadTaskState?): Float? {
    val progress = taskState?.progressPercent
    return when {
        progress != null -> (progress.coerceIn(0, 100) / 100f)
        taskState?.status == DlsiteDownloadTaskStatus.FAILED || work.isFailed() -> 1f
        else -> null
    }
}

private fun dlsiteContentProgressFraction(content: DlsiteContent, taskState: DlsiteDownloadTaskState?): Float? {
    val progress = taskState?.progressPercent
    return when {
        progress != null -> (progress.coerceIn(0, 100) / 100f)
        taskState?.status == DlsiteDownloadTaskStatus.FAILED || content.isFailed() -> 1f
        else -> null
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

private fun dlsiteWorkStatusText(
    work: DlsiteWork,
    taskState: DlsiteDownloadTaskState?,
    contents: List<DlsiteContent>,
): String {
    if (contents.isNotEmpty()) {
        return "${contents.size} 个内容"
    }
    return when {
        taskState?.status == DlsiteDownloadTaskStatus.QUEUED || work.isQueued() -> "排队中"
        taskState?.status == DlsiteDownloadTaskStatus.DOWNLOADING || work.isDownloading() -> {
            taskState?.progressPercent?.let { "载入中 $it%" } ?: "载入中"
        }
        taskState?.status == DlsiteDownloadTaskStatus.PAUSED || work.isPaused() -> "已暂停"
        taskState?.status == DlsiteDownloadTaskStatus.FAILED || work.isFailed() -> "载入失败"
        taskState?.status == DlsiteDownloadTaskStatus.COMPLETED -> "已完成"
        else -> "未载入"
    }
}

private fun dlsiteTaskStatusText(
    taskState: DlsiteDownloadTaskState,
): String {
    return when (taskState.status) {
        DlsiteDownloadTaskStatus.QUEUED -> "排队中"
        DlsiteDownloadTaskStatus.DOWNLOADING ->
            taskState.progressPercent?.let { "载入中 $it%" } ?: "载入中"
        DlsiteDownloadTaskStatus.PAUSED -> "已暂停"
        DlsiteDownloadTaskStatus.FAILED -> "载入失败"
        DlsiteDownloadTaskStatus.COMPLETED -> "已完成"
    }
}

private fun contentStatusText(content: DlsiteContent?, cached: Boolean = content?.isDownloaded() == true): String {
    return when {
        content == null -> "未载入"
        cached -> "已载入"
        content.isQueued() -> "排队中"
        content.isDownloading() -> "载入中"
        content.isPaused() -> "已暂停"
        content.isFailed() -> content.error.ifEmpty { "载入失败" }
        else -> "未载入"
    }
}

private fun dlsiteContentMetaText(
    content: DlsiteContent,
    taskState: DlsiteDownloadTaskState?,
    cached: Boolean,
): String {
    val parts = mutableListOf<String>()
    parts += if (content.trackCount > 0) {
        "音频 ${content.trackCount} 首"
    } else {
        "音频数待解析"
    }
    parts += if (cached) {
        "本地缓存"
    } else {
        "未缓存"
    }
    if (taskState != null && taskState.totalBytes > 0L) {
        parts += "${formatBytes(taskState.bytesDownloaded)} / ${formatBytes(taskState.totalBytes)}"
    }
    return parts.joinToString(" · ")
}

private fun dlsiteContentHasLocalCache(context: android.content.Context, content: DlsiteContent): Boolean {
    if (content.isDownloaded() || content.localPath.isNotEmpty()) {
        return true
    }
    val contentDir = File(
        File(File(context.filesDir, "dlsite/works/${content.workId}"), "contents"),
        safeDlsiteContentId(content.optionId),
    )
    return File(contentDir, ".downloaded").isFile
}

private fun safeDlsiteContentId(optionId: String): String {
    val id = optionId.ifEmpty { "default" }
    return id.replace(Regex("[\\\\/:*?\"<>|]+"), "_")
}

private fun formatSyncTimeCompact(lastSyncMs: Long): String {
    if (lastSyncMs <= 0L) {
        return "未同步"
    }
    return SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(Date(lastSyncMs))
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
