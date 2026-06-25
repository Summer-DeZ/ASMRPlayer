package io.github.summerdez.asmrplayer.presentation

import android.app.Application
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.summerdez.asmrplayer.data.DlsiteDownloadState
import io.github.summerdez.asmrplayer.data.DlsiteRepository
import io.github.summerdez.asmrplayer.data.LibraryRepository
import io.github.summerdez.asmrplayer.data.download.DlsiteDownloadQueueRepository
import io.github.summerdez.asmrplayer.domain.model.DlsiteContent
import io.github.summerdez.asmrplayer.domain.model.DlsiteDownloadOption
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "DlsiteViewModel"

data class DlsiteUiState(
    val loggedIn: Boolean = false,
    val busy: Boolean = false,
    val works: List<DlsiteWork> = emptyList(),
    val contentsByWork: Map<String, List<DlsiteContent>> = emptyMap(),
    val lastSyncMs: Long = 0L,
    val downloadState: DlsiteDownloadState = DlsiteDownloadState(),
)

sealed interface DlsiteEvent {
    data class Message(val text: String) : DlsiteEvent
}

class DlsiteViewModel(
    application: Application,
    private val dlsiteRepository: DlsiteRepository,
    private val downloadQueueRepository: DlsiteDownloadQueueRepository,
    private val libraryRepository: LibraryRepository,
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(DlsiteUiState())
    private val messages = DlsiteEventMessageHelper()
    private val downloadActions = DlsiteDownloadActions(
        application = application,
        scope = viewModelScope,
        dlsiteRepository = dlsiteRepository,
        downloadQueueRepository = downloadQueueRepository,
        libraryRepository = libraryRepository,
        stateProvider = { _state.value },
        downloadStateProvider = { downloadState },
        isBusy = ::isBusy,
        setBusy = ::setBusy,
        showMessage = ::showMessage,
    )
    private var downloadState = DlsiteDownloadState()
    private var operationBusy = false
    val state: StateFlow<DlsiteUiState> = _state.asStateFlow()
    val events: SharedFlow<DlsiteEvent> = messages.events

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
        refreshLoginState()
        recoverInterruptedDownloads()
    }

    private fun refreshLoginState() {
        _state.value = _state.value.copy(
            loggedIn = dlsiteRepository.hasLoginCookie(),
            busy = isBusy(),
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
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) {
                    setBusy(false)
                    showMessage(shortDlsiteError(exception))
                }
            }
        }
    }

    private suspend fun syncWorkCovers(works: List<DlsiteWork>): Int {
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
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                rethrowIfCancellationOrInterrupted(exception)
                Log.w(TAG, "Failed to sync DLsite cover for work=${coverWork.workId}", exception)
            }
        }
        return synced
    }

    private suspend fun repairDownloadedCovers(works: List<DlsiteWork>): Int {
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
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                rethrowIfCancellationOrInterrupted(exception)
                Log.w(TAG, "Failed to repair DLsite downloaded cover for work=${work.workId}", exception)
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
        downloadActions.requestDownloadOptions(work)
    }

    fun startDownload(work: DlsiteWork, option: DlsiteDownloadOption) {
        downloadActions.startDownload(work, option)
    }

    fun startDownload(work: DlsiteWork, content: DlsiteContent) {
        downloadActions.startDownload(work, content)
    }

    fun startDownload(work: DlsiteWork, options: List<DlsiteDownloadOption>) {
        downloadActions.startDownload(work, options)
    }

    fun pauseDownload(work: DlsiteWork) {
        downloadActions.pauseDownload(work)
    }

    fun resumeDownload(work: DlsiteWork) {
        downloadActions.resumeDownload(work)
    }

    fun deleteCache(work: DlsiteWork, onLibraryChanged: () -> Unit) {
        downloadActions.deleteCache(work, onLibraryChanged)
    }

    fun cancelDownload(work: DlsiteWork) {
        downloadActions.cancelDownload(work)
    }

    fun pauseAllDownloads() {
        downloadActions.pauseAllDownloads()
    }

    fun resumeAllDownloads() {
        downloadActions.resumeAllDownloads()
    }

    fun clearCompletedDownloadTasks() {
        dlsiteRepository.clearCompletedDownloadTasks()
    }

    fun deleteContent(work: DlsiteWork, content: DlsiteContent, onLibraryChanged: () -> Unit) {
        downloadActions.deleteContent(work, content, onLibraryChanged)
    }

    private fun setBusy(busy: Boolean) {
        operationBusy = busy
        _state.value = _state.value.copy(busy = isBusy())
    }

    private fun recoverInterruptedDownloads() {
        if (isBusy()) {
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dlsiteRepository.markInterruptedDownloads("下载已中断，请重试")
            }
        }
    }

    private fun isBusy(): Boolean {
        return operationBusy
    }

    private fun showMessage(message: String) {
        messages.showMessage(message)
    }
}
