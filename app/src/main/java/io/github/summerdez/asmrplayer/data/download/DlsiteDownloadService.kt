package io.github.summerdez.asmrplayer.data.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.text.TextUtils
import android.util.Log
import io.github.summerdez.asmrplayer.data.DlsiteApi
import io.github.summerdez.asmrplayer.data.DlsiteDownloadStateStore
import io.github.summerdez.asmrplayer.data.DlsiteDownloadTaskStatus
import io.github.summerdez.asmrplayer.data.SettingsRepository
import io.github.summerdez.asmrplayer.data.remote.DlsiteJsonParser
import io.github.summerdez.asmrplayer.di.AppGraph
import io.github.summerdez.asmrplayer.domain.model.DlsiteContentFile
import io.github.summerdez.asmrplayer.domain.model.DlsiteDownloadOption
import io.github.summerdez.asmrplayer.domain.model.DlsiteDownloadQueueTask
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import java.io.File
import java.util.LinkedHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DlsiteDownloadService : Service() {
    private val lock = Any()
    private val runningWorkers = LinkedHashMap<String, DownloadWorker>()
    private val scheduler: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "dlsite-download-scheduler")
    }
    private lateinit var downloadRepository: DlsiteDownloadBlockingAdapter
    private lateinit var dlsiteApi: DlsiteApi
    private lateinit var dlsiteDownloadStateStore: DlsiteDownloadStateStore
    private lateinit var settingsRepository: SettingsRepository
    private var latestStartId = 0
    private var destroying = false

    override fun onCreate() {
        super.onCreate()
        destroying = false
        val dependencies = AppGraph.container(this).dlsiteDownloadServiceDependencies
        downloadRepository = DlsiteDownloadBlockingAdapter(
            dependencies.dlsiteRepository,
            dependencies.dlsiteDownloadQueueRepository,
            dependencies.libraryRepository,
        )
        dlsiteApi = dependencies.dlsiteApi
        dlsiteDownloadStateStore = dependencies.dlsiteDownloadStateStore
        settingsRepository = dependencies.settingsRepository
        DlsiteDownloadNotifications.ensureChannel(this)
        dispatchToScheduler({ downloadRepository.resetRunningDownloadQueue() }, 0)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        when (val command = DlsiteDownloadServiceIntents.parseStartCommand(intent)) {
            DlsiteDownloadStartCommand.Empty -> dispatchToScheduler({ handleEmptyStart(startId) }, startId)
            is DlsiteDownloadStartCommand.Pause -> dispatchToScheduler({ handlePause(command.workId, startId) }, startId)
            is DlsiteDownloadStartCommand.Delete -> dispatchToScheduler({ handleDelete(command.workId, startId) }, startId)
            is DlsiteDownloadStartCommand.Download -> {
                dispatchToScheduler({ handleDownload(command.workId, command.optionIds, startId) }, startId)
            }
            DlsiteDownloadStartCommand.Unsupported -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        synchronized(lock) {
            destroying = true
            for (worker in runningWorkers.values) {
                if (STOP_NONE == worker.stopRequest) {
                    worker.stopRequest = STOP_RESCHEDULE
                }
                worker.interrupt()
            }
        }
        scheduler.shutdownNow()
        super.onDestroy()
    }

    private fun dispatchToScheduler(command: () -> Unit, startId: Int) {
        try {
            scheduler.execute {
                try {
                    command()
                } catch (exception: RuntimeException) {
                    Log.e(TAG, "Download scheduler command failed; stopping service startId=$startId", exception)
                    stopSelf(startId)
                }
            }
        } catch (exception: RuntimeException) {
            Log.e(TAG, "Failed to dispatch download scheduler command; stopping service startId=$startId", exception)
            stopSelf(startId)
        }
    }

    private fun handleEmptyStart(startId: Int) {
        synchronized(lock) {
            if (hasPendingDownloadsLocked()) {
                promoteDlsiteDownloadToForeground(dlsiteDownloadStateStore)
            }
            scheduleLocked()
            stopIfIdleLocked(startId)
        }
    }

    private fun handleDownload(workId: String?, optionIds: List<String>, startId: Int) {
        val normalizedWorkId = workId?.takeIf { it.isNotEmpty() }
        if (normalizedWorkId == null) {
            stopSelf(startId)
            return
        }
        val work = downloadRepository.getWork(normalizedWorkId)
        if (work == null) {
            stopSelf(startId)
            return
        }
        if (!settingsRepository.canStartDlsiteDownloadBlocking(this)) {
            stopSelf(startId)
            return
        }
        enqueueDownload(work, optionIds)
    }

    private fun enqueueDownload(work: DlsiteWork, optionIds: List<String>) {
        synchronized(lock) {
            val task = downloadRepository.enqueueDownload(
                work,
                optionIds,
                optionIds.size.toString() + " 个内容",
            )
            if (task == null) {
                stopIfIdleLocked(latestStartId)
                return
            }
            publishQueuePositions(downloadRepository, dlsiteDownloadStateStore)
            promoteDlsiteDownloadToForeground(dlsiteDownloadStateStore)
            scheduleLocked()
        }
    }

    private fun scheduleLocked() {
        if (destroying) {
            publishQueuePositions(downloadRepository, dlsiteDownloadStateStore)
            updateDlsiteDownloadNotification(dlsiteDownloadStateStore)
            return
        }
        if (hasPendingDownloadsLocked()) {
            if (!settingsRepository.canStartDlsiteDownloadBlocking(this)) {
                publishQueuePositions(downloadRepository, dlsiteDownloadStateStore)
                updateDlsiteDownloadNotification(dlsiteDownloadStateStore)
                if (runningWorkers.isEmpty()) {
                    stopDlsiteDownloadForegroundSafely()
                    stopSelf(latestStartId)
                }
                return
            }
            promoteDlsiteDownloadToForeground(dlsiteDownloadStateStore)
        }
        while (runningWorkers.size < MAX_CONCURRENT_DOWNLOADS) {
            val pendingTasks = downloadRepository.pendingDownloadQueueTasks(1)
            if (pendingTasks.isEmpty()) {
                break
            }
            val pendingTask = pendingTasks[0]
            if (runningWorkers.containsKey(pendingTask.workId)) {
                downloadRepository.markDownloadQueueTaskCanceled(pendingTask.taskId)
                continue
            }
            val work = downloadRepository.getWork(pendingTask.workId)
            if (work == null) {
                downloadRepository.markDownloadQueueTaskFailed(pendingTask.taskId, "找不到作品记录")
                dlsiteDownloadStateStore.remove(pendingTask.workId)
                continue
            }
            val runningTask = downloadRepository.markDownloadQueueTaskRunning(pendingTask.taskId)
            if (runningTask == null) {
                continue
            }
            val request = DlsiteDownloadRequest(
                runningTask.taskId,
                runningTask.workId,
                work.displayTitle(),
                runningTask.optionIdList(),
            )
            val worker = DownloadWorker(request, work)
            runningWorkers[request.workId] = worker
            worker.start()
        }
        publishQueuePositions(downloadRepository, dlsiteDownloadStateStore)
        updateDlsiteDownloadNotification(dlsiteDownloadStateStore)
    }

    private fun finishWorker(worker: DownloadWorker) {
        synchronized(lock) {
            runningWorkers.remove(worker.request.workId)
            if (!destroying) {
                scheduleLocked()
            }
            if (runningWorkers.isEmpty() && !hasPendingDownloadsLocked()) {
                stopDlsiteDownloadForegroundSafely()
                stopSelf(latestStartId)
            }
        }
    }

    private fun handlePause(workId: String?, startId: Int) {
        val normalizedWorkId = workId?.takeIf { it.isNotEmpty() }
        if (normalizedWorkId == null) {
            stopSelf(startId)
            return
        }
        val work = downloadRepository.getWork(normalizedWorkId)
        if (work == null) {
            stopSelf(startId)
            return
        }
        synchronized(lock) {
            val worker = runningWorkers[work.workId]
            if (worker != null && worker.stopRequest == STOP_NONE) {
                worker.stopRequest = STOP_PAUSE
                dlsiteDownloadStateStore.publishPaused(work.workId, work.displayTitle())
                if (isContentDownload(worker.request.optionIds)) {
                    downloadRepository.markContentPaused(work.workId, worker.request.optionIds)
                } else {
                    markPausedSafely(work)
                }
                worker.interrupt()
                return
            }
            val queued = downloadRepository.pauseQueuedDownload(work.workId)
            if (queued != null) {
                if (isContentDownload(queued.optionIdList())) {
                    downloadRepository.markContentPaused(work.workId, queued.optionIdList())
                } else {
                    markPausedSafely(work)
                }
                dlsiteDownloadStateStore.publishPaused(work.workId, work.displayTitle())
                scheduleLocked()
                stopIfIdleLocked(startId)
                return
            }
        }
        stopSelf(startId)
    }

    private fun handleDelete(workId: String?, startId: Int) {
        val normalizedWorkId = workId?.takeIf { it.isNotEmpty() }
        if (normalizedWorkId == null) {
            stopSelf(startId)
            return
        }
        val work = downloadRepository.getWork(normalizedWorkId)
        if (work == null) {
            stopSelf(startId)
            return
        }
        synchronized(lock) {
            val worker = runningWorkers[work.workId]
            if (worker != null && worker.stopRequest == STOP_NONE) {
                worker.stopRequest = STOP_DELETE
                dlsiteDownloadStateStore.publishTask(
                    work.workId,
                    work.displayTitle(),
                    DlsiteDownloadTaskStatus.DOWNLOADING,
                    "删除中",
                )
                worker.interrupt()
                return
            }
            val queued = downloadRepository.cancelQueuedDownload(work.workId)
            if (queued != null) {
                if (deleteCachedWork(work)) {
                    dlsiteDownloadStateStore.remove(work.workId)
                } else {
                    publishDeleteFailed(
                        downloadRepository,
                        dlsiteDownloadStateStore,
                        work,
                        queued.taskId,
                        queued.optionIdList(),
                    )
                }
                scheduleLocked()
                stopIfIdleLocked(startId)
                return
            }
        }
        if (deleteCachedWork(work)) {
            dlsiteDownloadStateStore.remove(work.workId)
            stopSelf(startId)
        } else {
            publishDeleteFailed(downloadRepository, dlsiteDownloadStateStore, work)
            stopSelf(startId)
        }
    }

    private fun stopIfIdleLocked(startId: Int) {
        if (runningWorkers.isEmpty() && !hasPendingDownloadsLocked()) {
            stopDlsiteDownloadForegroundSafely()
            stopSelf(startId)
        }
    }

    private fun hasPendingDownloadsLocked(): Boolean {
        return downloadRepository.pendingDownloadQueueTasks(1).isNotEmpty()
    }

    private fun deleteCachedWork(work: DlsiteWork): Boolean {
        try {
            DlsiteDownloadTask.deleteCache(this, work)
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to delete cached DLsite work=${work.workId}; leaving cache state unchanged", exception)
            return false
        }
        downloadRepository.markCacheDeleted(work)
        return true
    }

    private fun deleteCachedWorkSafely(work: DlsiteWork): Boolean {
        return try {
            deleteCachedWork(work)
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to delete cached DLsite work=${work.workId} during best-effort cleanup", exception)
            false
        }
    }

    private fun markPausedSafely(work: DlsiteWork) {
        try {
            downloadRepository.markPaused(work)
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to mark DLsite work=${work.workId} paused; keeping pause flow best-effort", exception)
        }
    }

    private inner class DownloadWorker(
        val request: DlsiteDownloadRequest,
        val initialWork: DlsiteWork,
    ) : Thread("dlsite-download-" + request.workId), DlsiteDownloadTask.ContentListener {
        var stopRequest: String = STOP_NONE

        override fun run() {
            val work = initialWork
            val contentDownload = isContentDownload(request.optionIds)
            var restoreInterrupt = false
            try {
                val api = dlsiteApi
                val repository = downloadRepository
                if (!contentDownload) {
                    runIfActive {
                        repository.markDownloading(
                            work,
                            TextUtils.join("|", request.optionIds),
                            request.optionIds.size.toString() + " 个内容",
                        )
                        dlsiteDownloadStateStore.publishTask(
                            work.workId,
                            work.displayTitle(),
                            DlsiteDownloadTaskStatus.DOWNLOADING,
                            "下载中",
                        )
                        updateDlsiteDownloadNotification(dlsiteDownloadStateStore)
                    }
                }
                val result = DlsiteDownloadTask.downloadAndImport(
                    this@DlsiteDownloadService,
                    api,
                    repository,
                    work,
                    request.optionIds,
                    this,
                )
                synchronized(lock) {
                    if (STOP_NONE == stopRequest) {
                        val downloadedWork = if (TextUtils.isEmpty(result.coverUri)) {
                            work
                        } else {
                            work.withCoverUri(result.coverUri)
                        }
                        if (contentDownload) {
                            repository.markImported(
                                downloadedWork,
                                result.playlistId,
                                result.localPath,
                                result.trackCount,
                            )
                        } else {
                            repository.markDownloaded(
                                downloadedWork,
                                result.playlistId,
                                result.localPath,
                                result.trackCount,
                            )
                        }
                        repository.markDownloadQueueTaskCompleted(request.taskId)
                        dlsiteDownloadStateStore.publishCompleted(work.workId, work.displayTitle())
                        runningWorkers.remove(request.workId)
                    } else {
                        settleStopRequest(work, contentDownload)
                    }
                }
                updateDlsiteDownloadNotification(dlsiteDownloadStateStore)
            } catch (exception: Exception) {
                restoreInterrupt = exception.isDlsiteDownloadInterruption()
                synchronized(lock) {
                    if (STOP_NONE == stopRequest) {
                        markDlsiteDownloadFailed(
                            downloadRepository, dlsiteDownloadStateStore, runningWorkers, request, work, contentDownload, exception,
                        )
                    } else {
                        settleStopRequest(work, contentDownload)
                    }
                }
                updateDlsiteDownloadNotification(dlsiteDownloadStateStore)
            } finally {
                finishDlsiteDownloadWorker(restoreInterrupt) { finishWorker(this) }
            }
        }

        private inline fun runIfActive(block: () -> Unit) = synchronized(lock) { if (STOP_NONE == stopRequest) block() }

        private fun settleStopRequest(work: DlsiteWork, contentDownload: Boolean) {
            when (stopRequest) {
                STOP_PAUSE -> {
                    downloadRepository.markDownloadQueueTaskPaused(request.taskId)
                    if (!contentDownload) markPausedSafely(work)
                    downloadRepository.markContentPaused(work.workId, request.optionIds)
                    dlsiteDownloadStateStore.publishPaused(work.workId, work.displayTitle())
                }
                STOP_DELETE -> if (deleteCachedWorkSafely(work)) {
                    downloadRepository.markDownloadQueueTaskCanceled(request.taskId)
                    dlsiteDownloadStateStore.remove(work.workId)
                } else {
                    publishDeleteFailed(downloadRepository, dlsiteDownloadStateStore, work, request.taskId, request.optionIds)
                }
                STOP_RESCHEDULE -> {
                    downloadRepository.markDownloadQueueTaskPending(request.taskId)
                    if (contentDownload) {
                        downloadRepository.markContentQueued(work.workId, request.optionIds)
                    } else {
                        downloadRepository.markQueued(work, request.optionIds, request.optionIds.size.toString() + " 个内容")
                    }
                    dlsiteDownloadStateStore.publishQueued(work.workId, work.displayTitle(), 1)
                }
            }
        }

        override fun onContentStarted(option: DlsiteDownloadOption, contentDir: File) = runIfActive {
            downloadRepository.markContentDownloading(request.workId, option.id)
        }

        override fun onContentProgress(
            option: DlsiteDownloadOption,
            contentFile: DlsiteContentFile?,
            bytesDownloaded: Long,
            totalBytes: Long,
        ) = runIfActive {
            publishContentDownloadProgress(
                dlsiteDownloadStateStore,
                request.workId,
                initialWork.displayTitle(),
                option,
                bytesDownloaded,
                totalBytes,
            )
            updateDlsiteDownloadNotification(dlsiteDownloadStateStore)
        }

        override fun onContentFinished(option: DlsiteDownloadOption, result: DlsiteDownloadTask.ContentResult) = runIfActive {
            downloadRepository.markContentDownloaded(
                request.workId,
                option.id,
                result.title,
                result.localPath,
                result.trackIds,
                result.trackCount,
            )
        }
    }

    companion object {
        const val ACTION_DOWNLOAD = "io.github.summerdez.asmrplayer.action.DLSITE_DOWNLOAD"
        const val ACTION_PAUSE = "io.github.summerdez.asmrplayer.action.DLSITE_PAUSE"
        const val ACTION_DELETE = "io.github.summerdez.asmrplayer.action.DLSITE_DELETE"
        const val EXTRA_WORK_ID = "extra_work_id"
        const val EXTRA_OPTION_ID = "extra_option_id"
        const val EXTRA_OPTION_IDS = "extra_option_ids"

        private const val STOP_NONE = ""
        private const val STOP_PAUSE = "pause"
        private const val STOP_DELETE = "delete"
        private const val STOP_RESCHEDULE = "reschedule"
        private const val MAX_CONCURRENT_DOWNLOADS = 2
        private const val TAG = "DlsiteDownloadService"
        fun downloadIntent(context: Context?, workId: String?, optionId: String?): Intent =
            DlsiteDownloadServiceIntents.downloadIntent(context, workId, optionId)
        fun downloadIntent(context: Context?, workId: String?, optionIds: List<String>?): Intent =
            DlsiteDownloadServiceIntents.downloadIntent(context, workId, optionIds)
        fun pauseIntent(context: Context?, workId: String?): Intent =
            DlsiteDownloadServiceIntents.pauseIntent(context, workId)
        fun deleteIntent(context: Context?, workId: String?): Intent =
            DlsiteDownloadServiceIntents.deleteIntent(context, workId)
    }
}
