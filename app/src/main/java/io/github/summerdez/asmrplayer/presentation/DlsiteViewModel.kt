package io.github.summerdez.asmrplayer.presentation

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
import android.app.Application
import android.net.Uri
import android.text.TextUtils
import android.webkit.CookieManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class DlsiteUiState(
    val loggedIn: Boolean = false,
    val busy: Boolean = false,
    val works: List<DlsiteWork> = emptyList(),
    val contentsByWork: Map<String, List<DlsiteContent>> = emptyMap(),
    val lastSyncMs: Long = 0L,
    val downloadState: DlsiteDownloadState = DlsiteDownloadState(),
    val optionWork: DlsiteWork? = null,
    val downloadOptions: List<DlsiteDownloadOption> = emptyList(),
)

sealed interface DlsiteEvent {
    data class Message(val text: String) : DlsiteEvent
}

class DlsiteViewModel(
    application: Application,
    private val dlsiteRepository: DlsiteRepository,
    private val libraryRepository: LibraryRepository,
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(DlsiteUiState())
    private val _events = MutableSharedFlow<DlsiteEvent>(extraBufferCapacity = 8)
    private var downloadState = DlsiteDownloadState()
    private var operationBusy = false
    val state: StateFlow<DlsiteUiState> = _state.asStateFlow()
    val events: SharedFlow<DlsiteEvent> = _events.asSharedFlow()

    init {
        observeRepository()
        refresh()
    }

    private fun observeRepository() {
        viewModelScope.launch {
            combine(
                dlsiteRepository.worksFlow,
                dlsiteRepository.contentsFlow,
                dlsiteRepository.lastSyncMsFlow,
                dlsiteRepository.downloadStateFlow,
            ) { works, contents, lastSyncMs, nextDownloadState ->
                DownloadSnapshot(works, contents, lastSyncMs, nextDownloadState)
            }.collect { snapshot ->
                val nextDownloadState = snapshot.downloadState
                downloadState = nextDownloadState
                _state.value = _state.value.copy(
                    loggedIn = dlsiteRepository.hasLoginCookie(),
                    busy = isBusy(),
                    works = snapshot.works,
                    contentsByWork = snapshot.contents.groupBy { it.workId },
                    lastSyncMs = snapshot.lastSyncMs,
                    downloadState = nextDownloadState,
                )
            }
        }
    }

    fun refresh() {
        recoverInterruptedDownloads()
        _state.value = _state.value.copy(
            loggedIn = dlsiteRepository.hasLoginCookie(),
            busy = isBusy(),
            works = dlsiteRepository.getWorks(),
            contentsByWork = dlsiteRepository.getWorks()
                .associate { work -> work.workId to dlsiteRepository.getContents(work.workId) },
            lastSyncMs = dlsiteRepository.getLastSyncMs(),
            downloadState = downloadState,
        )
    }

    fun logout() {
        if (isBusy()) {
            return
        }
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            refresh()
            showMessage("已退出 DLsite")
        }
    }

    fun syncWorks() {
        if (isBusy()) {
            return
        }
        if (!dlsiteRepository.hasLoginCookie()) {
            showMessage("请先登录 DLsite")
            return
        }
        setBusy(true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val works = dlsiteRepository.fetchPurchasedWorks()
                val merged = dlsiteRepository.mergeDiscoveredWorks(works)
                val syncedCovers = syncWorkCovers(merged)
                val repairedCovers = repairDownloadedCovers(merged)
                withContext(Dispatchers.Main) {
                    setBusy(false)
                    refresh()
                    showMessage(
                        when {
                            merged.isEmpty() -> "未找到购买作品"
                            syncedCovers > 0 && repairedCovers > 0 ->
                                "已同步 ${merged.size} 个作品，保存 $syncedCovers 个封面，补齐 $repairedCovers 个播放列表封面"
                            syncedCovers > 0 -> "已同步 ${merged.size} 个作品，保存 $syncedCovers 个封面"
                            repairedCovers > 0 -> "已同步 ${merged.size} 个作品，补齐 $repairedCovers 个播放列表封面"
                            else -> "已同步 ${merged.size} 个作品"
                        },
                    )
                }
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) {
                    setBusy(false)
                    refresh()
                    showMessage(shortDlsiteError(exception))
                }
            }
        }
    }

    private fun syncWorkCovers(works: List<DlsiteWork>): Int {
        var synced = 0
        works.forEach { work ->
            val coverWork = work.withEnsuredCoverUrl()
            if (TextUtils.isEmpty(coverWork.coverUrl)) {
                return@forEach
            }
            localCoverFile(coverWork)?.let { existingCover ->
                val existingUri = Uri.fromFile(existingCover).toString()
                if (coverWork.coverUri != existingUri) {
                    dlsiteRepository.saveWork(coverWork.withCoverUri(existingUri))
                }
                return@forEach
            }
            try {
                val coverFile = dlsiteRepository.downloadCover(coverWork, File(dlsiteCoverRoot(), coverWork.workId))
                dlsiteRepository.saveWork(coverWork.withCoverUri(Uri.fromFile(coverFile).toString()))
                synced++
            } catch (ignored: Exception) {
            }
        }
        return synced
    }

    private fun repairDownloadedCovers(works: List<DlsiteWork>): Int {
        var repaired = 0
        works.forEach { work ->
            if (!work.isDownloaded()
                || TextUtils.isEmpty(work.coverUrl)
                || TextUtils.isEmpty(work.playlistId)
                || TextUtils.isEmpty(work.localPath)
            ) {
                return@forEach
            }
            val playlist = libraryRepository.getPlaylist(work.playlistId) ?: return@forEach
            if (!TextUtils.isEmpty(playlist.coverUri)) {
                return@forEach
            }
            val workDir = File(work.localPath)
            if (!workDir.isDirectory) {
                return@forEach
            }
            try {
                val coverFile = localCoverFile(work) ?: dlsiteRepository.downloadCover(work, File(workDir, "cover"))
                val coverUri = Uri.fromFile(coverFile).toString()
                dlsiteRepository.saveWork(work.withCoverUri(coverUri))
                libraryRepository.setPlaylistCover(work.playlistId, coverUri)
                repaired++
            } catch (ignored: Exception) {
            }
        }
        return repaired
    }

    private fun localCoverFile(work: DlsiteWork): File? {
        if (TextUtils.isEmpty(work.coverUri)) {
            return null
        }
        return try {
            val uri = Uri.parse(work.coverUri)
            if (uri.scheme != "file") {
                null
            } else {
                File(uri.path.orEmpty()).takeIf { it.isFile }
            }
        } catch (ignored: Exception) {
            null
        }
    }

    private fun dlsiteCoverRoot(): File {
        return File(getApplication<Application>().filesDir, "dlsite/covers")
    }

    fun requestDownloadOptions(work: DlsiteWork) {
        if (isBusy()) {
            return
        }
        if (!dlsiteRepository.hasLoginCookie()) {
            showMessage("请先登录 DLsite")
            return
        }
        setBusy(true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val options = dlsiteRepository.fetchDownloadOptions(work)
                val contents = dlsiteRepository.saveDownloadOptions(work, options)
                withContext(Dispatchers.Main) {
                    setBusy(false)
                    refresh()
                    if (options.isEmpty()) {
                        showMessage("没有找到可下载内容")
                    } else {
                        _state.value = _state.value.copy(
                            optionWork = work,
                            downloadOptions = options,
                            contentsByWork = _state.value.contentsByWork + (work.workId to contents),
                        )
                    }
                }
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) {
                    setBusy(false)
                    refresh()
                    showMessage(shortDlsiteError(exception))
                }
            }
        }
    }

    fun dismissDownloadOptions() {
        _state.value = _state.value.copy(optionWork = null, downloadOptions = emptyList())
    }

    fun startDownload(work: DlsiteWork, option: DlsiteDownloadOption) {
        startDownload(work, listOf(option))
    }

    fun startDownload(work: DlsiteWork, options: List<DlsiteDownloadOption>) {
        dismissDownloadOptions()
        if (isBusy() || options.isEmpty()) {
            return
        }
        setBusy(true)
        val optionIds = options.map { it.id }
        val queuedTask = dlsiteRepository.enqueueDownload(work, optionIds, options.joinToString("、") { it.title })
        refresh()
        try {
            ContextCompat.startForegroundService(
                getApplication(),
                DlsiteDownloadService.downloadIntent(getApplication(), work.workId, optionIds),
            )
            setBusy(false)
            refresh()
            showMessage("已加入下载队列")
        } catch (exception: RuntimeException) {
            setBusy(false)
            dlsiteRepository.markDownloadQueueTaskFailed(queuedTask?.taskId, shortDlsiteError(exception))
            dlsiteRepository.markFailed(work, shortDlsiteError(exception))
            showMessage(shortDlsiteError(exception))
        }
    }

    fun pauseDownload(work: DlsiteWork) {
        val task = downloadState.tasks[work.workId]
        if (!work.isDownloading() && !work.isQueued() && task == null) {
            return
        }
        try {
            getApplication<Application>().startService(DlsiteDownloadService.pauseIntent(getApplication(), work.workId))
            refresh()
            showMessage("正在暂停下载")
        } catch (exception: RuntimeException) {
            try {
                dlsiteRepository.markPaused(work)
            } catch (_: RuntimeException) {
            }
            refresh()
            showMessage(shortDlsiteError(exception))
        }
    }

    fun resumeDownload(work: DlsiteWork) {
        if (isBusy()) {
            return
        }
        if (!TextUtils.isEmpty(work.downloadOptionId)) {
            val ids = work.downloadOptionId.split("|").filter { it.isNotEmpty() }
            val contents = dlsiteRepository.getContents(work.workId).associateBy { it.optionId }
            val options = ids.map { id ->
                DlsiteDownloadOption(id, contents[id]?.title ?: work.downloadOptionTitle, emptyList())
            }
            startDownload(work, options)
            return
        }
        requestDownloadOptions(work)
    }

    fun deleteCache(work: DlsiteWork, onLibraryChanged: () -> Unit) {
        val task = downloadState.tasks[work.workId]
        if (work.isDownloading() || work.isQueued() || task?.active == true) {
            cancelDownload(work)
            return
        }
        if (isBusy()) {
            return
        }
        if (!work.isPaused() && !work.isFailed() && !work.isDownloaded()) {
            return
        }
        setBusy(true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val playlistId = work.playlistId
                DlsiteDownloadTask.deleteCache(getApplication<Application>(), work)
                if (!TextUtils.isEmpty(playlistId)) {
                    libraryRepository.deletePlaylist(playlistId)
                }
                dlsiteRepository.markCacheDeleted(work)
                withContext(Dispatchers.Main) {
                    setBusy(false)
                    refresh()
                    onLibraryChanged()
                    showMessage("已删除缓存")
                }
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) {
                    setBusy(false)
                    refresh()
                    showMessage(shortDlsiteError(exception))
                }
            }
        }
    }

    fun cancelDownload(work: DlsiteWork) {
        try {
            getApplication<Application>().startService(DlsiteDownloadService.deleteIntent(getApplication(), work.workId))
            refresh()
            showMessage("正在取消下载")
        } catch (exception: RuntimeException) {
            showMessage(shortDlsiteError(exception))
        }
    }

    fun pauseAllDownloads() {
        val activeWorks = _state.value.works.filter { work ->
            work.isDownloading() || work.isQueued() || downloadState.tasks[work.workId]?.active == true
        }
        activeWorks.forEach { work ->
            getApplication<Application>().startService(DlsiteDownloadService.pauseIntent(getApplication(), work.workId))
        }
        dlsiteRepository.markAllQueuedDownloadsPaused()
        refresh()
        showMessage("正在暂停全部下载")
    }

    fun resumeAllDownloads() {
        val pausedWorks = _state.value.works.filter { it.isPaused() }
        pausedWorks.forEach { resumeDownload(it) }
    }

    fun clearCompletedDownloadTasks() {
        DlsiteDownloadStateBus.clearCompleted()
        refresh()
    }

    fun deleteContent(work: DlsiteWork, content: DlsiteContent, onLibraryChanged: () -> Unit) {
        if (content.isDownloading() || content.isQueued()) {
            showMessage("请先暂停该内容")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                content.trackIdList().forEach { trackId ->
                    libraryRepository.removeTrack(work.playlistId, trackId)
                }
                DlsiteDownloadTask.deleteContentCache(getApplication<Application>(), work, content.optionId)
                dlsiteRepository.markContentCacheDeleted(work.workId, content.optionId)
                withContext(Dispatchers.Main) {
                    refresh()
                    onLibraryChanged()
                    showMessage("已删除该内容")
                }
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) {
                    showMessage(shortDlsiteError(exception))
                }
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        operationBusy = busy
        _state.value = _state.value.copy(busy = isBusy())
    }

    private fun recoverInterruptedDownloads() {
        if (isBusy()) {
            return
        }
        dlsiteRepository.markInterruptedDownloads("下载已中断，请重试")
    }

    private fun isBusy(): Boolean {
        return operationBusy
    }

    private fun showMessage(message: String) {
        if (message.isNotEmpty()) {
            _events.tryEmit(DlsiteEvent.Message(message))
        }
    }

    private fun shortDlsiteError(exception: Exception?): String {
        var message = exception?.message.orEmpty()
        if (TextUtils.isEmpty(message)) {
            message = "DLsite 操作失败"
        }
        return if (message.length > 42) message.substring(0, 42) + "..." else message
    }

    private data class DownloadSnapshot(
        val works: List<DlsiteWork>,
        val contents: List<DlsiteContent>,
        val lastSyncMs: Long,
        val downloadState: DlsiteDownloadState,
    )
}
