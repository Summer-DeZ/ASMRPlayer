package io.github.summerdez.asmrplayer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.summerdez.asmrplayer.data.DlsiteDownloadTaskStatus
import io.github.summerdez.asmrplayer.domain.model.DlsiteContent
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import io.github.summerdez.asmrplayer.presentation.DlsiteUiState
import io.github.summerdez.asmrplayer.ui.uiProbe

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
    onOpenDownloadManager: () -> Unit,
) {
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
                onOpenDownloadManager = onOpenDownloadManager,
            )
        }
        item {
            DlsiteSectionCaption(
                text = "当前 DLsite 内容",
            )
        }
        if (state.works.isEmpty()) {
            item {
                DlsiteEmptyState()
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
