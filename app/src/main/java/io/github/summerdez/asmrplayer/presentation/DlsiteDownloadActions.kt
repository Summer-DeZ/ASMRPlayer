package io.github.summerdez.asmrplayer.presentation

import android.app.Application
import android.text.TextUtils
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.summerdez.asmrplayer.data.DlsiteDownloadState
import io.github.summerdez.asmrplayer.data.DlsiteRepository
import io.github.summerdez.asmrplayer.data.LibraryRepository
import io.github.summerdez.asmrplayer.data.download.DlsiteDownloadQueueRepository
import io.github.summerdez.asmrplayer.data.download.DlsiteDownloadService
import io.github.summerdez.asmrplayer.data.download.DlsiteDownloadTask
import io.github.summerdez.asmrplayer.domain.model.DlsiteContent
import io.github.summerdez.asmrplayer.domain.model.DlsiteDownloadOption
import io.github.summerdez.asmrplayer.domain.model.DlsiteDownloadQueueTask
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "DlsiteViewModel"

internal class DlsiteDownloadActions(
    private val application: Application,
    private val scope: CoroutineScope,
    private val dlsiteRepository: DlsiteRepository,
    private val downloadQueueRepository: DlsiteDownloadQueueRepository,
    private val libraryRepository: LibraryRepository,
    private val stateProvider: () -> DlsiteUiState,
    private val downloadStateProvider: () -> DlsiteDownloadState,
    private val isBusy: () -> Boolean,
    private val setBusy: (Boolean) -> Unit,
    private val showMessage: (String) -> Unit,
) {
    fun requestDownloadOptions(work: DlsiteWork) {
        if (isBusy()) {
            return
        }
        val cachedContents = stateProvider().contentsByWork[work.workId].orEmpty()
        if (cachedContents.isNotEmpty()) {
            showMessage("已加载 ${cachedContents.size} 个内容")
            return
        }
        if (!dlsiteRepository.hasLoginCookie()) {
            showMessage("请先登录 DLsite")
            return
        }
        setBusy(true)
        scope.launch(Dispatchers.IO) {
            try {
                val options = dlsiteRepository.fetchDownloadOptions(work)
                val contents = dlsiteRepository.saveDownloadOptions(work, options)
                withContext(Dispatchers.Main) {
                    setBusy(false)
                    if (options.isEmpty()) {
                        showMessage("没有找到可下载内容")
                    } else {
                        showMessage("已解析 ${contents.size} 个内容")
                    }
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

    fun startDownload(work: DlsiteWork, option: DlsiteDownloadOption) {
        startDownload(work, listOf(option))
    }

    fun startDownload(work: DlsiteWork, content: DlsiteContent) {
        startDownload(
            work,
            DlsiteDownloadOption(
                content.optionId,
                content.title.ifEmpty { "默认版本" },
                emptyList(),
            ),
        )
    }

    fun startDownload(work: DlsiteWork, options: List<DlsiteDownloadOption>) {
        if (isBusy() || options.isEmpty()) {
            return
        }
        setBusy(true)
        val optionIds = options.map { it.id }
        val optionTitle = options.joinToString("、") { it.title }
        scope.launch {
            var queuedTask: DlsiteDownloadQueueTask? = null
            try {
                queuedTask = withContext(Dispatchers.IO) {
                    downloadQueueRepository.enqueueDownload(work, optionIds, optionTitle)
                }
                if (queuedTask == null) {
                    setBusy(false)
                    showMessage("无法加入下载队列")
                    return@launch
                }
                ContextCompat.startForegroundService(
                    application,
                    DlsiteDownloadService.downloadIntent(application, work.workId, optionIds),
                )
                setBusy(false)
                showMessage("已加入下载队列")
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                val message = shortDlsiteError(exception)
                withContext(Dispatchers.IO) {
                    queuedTask?.let { task ->
                        downloadQueueRepository.markDownloadQueueTaskFailed(task.taskId, message)
                    }
                    dlsiteRepository.markFailed(work, message)
                }
                setBusy(false)
                showMessage(message)
            }
        }
    }

    fun pauseDownload(work: DlsiteWork) {
        val task = downloadStateProvider().tasks[work.workId]
        if (!work.isDownloading() && !work.isQueued() && task == null) {
            return
        }
        try {
            application.startService(DlsiteDownloadService.pauseIntent(application, work.workId))
            showMessage("正在暂停下载")
        } catch (exception: RuntimeException) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        dlsiteRepository.markPaused(work)
                    } catch (pauseException: CancellationException) {
                        throw pauseException
                    } catch (pauseException: Exception) {
                        Log.w(
                            TAG,
                            "Failed to mark DLsite work=${work.workId} paused after pause service request failed",
                            pauseException,
                        )
                    }
                }
                showMessage(shortDlsiteError(exception))
            }
        }
    }

    fun resumeDownload(work: DlsiteWork) {
        if (isBusy()) {
            return
        }
        if (!TextUtils.isEmpty(work.downloadOptionId)) {
            val ids = work.downloadOptionId.split("|").filter { it.isNotEmpty() }
            val contents = stateProvider().contentsByWork[work.workId].orEmpty().associateBy { it.optionId }
            val options = ids.map { id ->
                DlsiteDownloadOption(id, contents[id]?.title ?: work.downloadOptionTitle, emptyList())
            }
            startDownload(work, options)
            return
        }
        requestDownloadOptions(work)
    }

    fun deleteCache(work: DlsiteWork, onLibraryChanged: () -> Unit) {
        val task = downloadStateProvider().tasks[work.workId]
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
        scope.launch(Dispatchers.IO) {
            try {
                val playlistId = work.playlistId
                DlsiteDownloadTask.deleteCache(application, work)
                if (!TextUtils.isEmpty(playlistId)) {
                    libraryRepository.deletePlaylist(playlistId)
                }
                dlsiteRepository.markCacheDeleted(work)
                withContext(Dispatchers.Main) {
                    setBusy(false)
                    onLibraryChanged()
                    showMessage("已删除缓存")
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

    fun cancelDownload(work: DlsiteWork) {
        try {
            application.startService(DlsiteDownloadService.deleteIntent(application, work.workId))
            showMessage("正在取消下载")
        } catch (exception: RuntimeException) {
            showMessage(shortDlsiteError(exception))
        }
    }

    fun pauseAllDownloads() {
        val state = stateProvider()
        val downloadState = downloadStateProvider()
        val activeWorks = state.works.filter { work ->
            work.isDownloading() || work.isQueued() || downloadState.tasks[work.workId]?.active == true
        }
        activeWorks.forEach { work ->
            try {
                application.startService(DlsiteDownloadService.pauseIntent(application, work.workId))
            } catch (exception: RuntimeException) {
                Log.w(TAG, "Failed to request pause for DLsite work=${work.workId}; continuing pause-all", exception)
            }
        }
        scope.launch {
            withContext(Dispatchers.IO) {
                downloadQueueRepository.markAllQueuedDownloadsPaused()
            }
            showMessage("正在暂停全部下载")
        }
    }

    fun resumeAllDownloads() {
        val pausedWorks = stateProvider().works.filter { it.isPaused() }
        pausedWorks.forEach { resumeDownload(it) }
    }

    fun deleteContent(work: DlsiteWork, content: DlsiteContent, onLibraryChanged: () -> Unit) {
        if (content.isDownloading() || content.isQueued()) {
            showMessage("请先暂停该内容")
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                content.trackIdList().forEach { trackId ->
                    libraryRepository.removeTrack(work.playlistId, trackId)
                }
                DlsiteDownloadTask.deleteContentCache(application, work, content.optionId)
                dlsiteRepository.markContentCacheDeleted(work.workId, content.optionId)
                withContext(Dispatchers.Main) {
                    onLibraryChanged()
                    showMessage("已删除该内容")
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) {
                    showMessage(shortDlsiteError(exception))
                }
            }
        }
    }
}
