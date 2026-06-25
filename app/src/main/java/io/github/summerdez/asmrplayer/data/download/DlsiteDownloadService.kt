package io.github.summerdez.asmrplayer.data.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.text.TextUtils
import io.github.summerdez.asmrplayer.data.DlsiteApi
import io.github.summerdez.asmrplayer.data.DlsiteDownloadStateStore
import io.github.summerdez.asmrplayer.data.DlsiteDownloadTaskStatus
import io.github.summerdez.asmrplayer.data.remote.DlsiteJsonParser
import io.github.summerdez.asmrplayer.di.AppGraph
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
    private var downloadRepository: DlsiteDownloadBlockingAdapter? = null
    private var dlsiteApi: DlsiteApi? = null
    private var dlsiteDownloadStateStore: DlsiteDownloadStateStore? = null
    private var latestStartId = 0
    private var destroying = false

    override fun onCreate() {
        super.onCreate()
        destroying = false
        val dependencies = AppGraph.container(this).dlsiteDownloadServiceDependencies
        downloadRepository = DlsiteDownloadBlockingAdapter(
            dependencies.dlsiteRepository,
            dependencies.libraryRepository,
        )
        dlsiteApi = dependencies.dlsiteApi
        dlsiteDownloadStateStore = dependencies.dlsiteDownloadStateStore
        DlsiteDownloadNotifications.ensureChannel(this)
        dispatchToScheduler({ downloadRepository!!.resetRunningDownloadQueue() }, 0)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        if (intent == null || TextUtils.isEmpty(intent.action)) {
            dispatchToScheduler({ handleEmptyStart(startId) }, startId)
            return START_NOT_STICKY
        }
        if (ACTION_PAUSE == intent.action) {
            val workId = intent.getStringExtra(EXTRA_WORK_ID)
            dispatchToScheduler({ handlePause(workId, startId) }, startId)
            return START_NOT_STICKY
        }
        if (ACTION_DELETE == intent.action) {
            val workId = intent.getStringExtra(EXTRA_WORK_ID)
            dispatchToScheduler({ handleDelete(workId, startId) }, startId)
            return START_NOT_STICKY
        }
        if (ACTION_DOWNLOAD != intent.action) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val workId = intent.getStringExtra(EXTRA_WORK_ID)
        var optionIds = intent.getStringArrayListExtra(EXTRA_OPTION_IDS)
        if (optionIds == null) {
            optionIds = ArrayList()
            val optionId = intent.getStringExtra(EXTRA_OPTION_ID)
            if (optionId != null) {
                optionIds.add(optionId)
            }
        }
        val requestedOptionIds = ArrayList(optionIds)
        dispatchToScheduler({ handleDownload(workId, requestedOptionIds, startId) }, startId)
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
                    stopSelf(startId)
                }
            }
        } catch (exception: RuntimeException) {
            stopSelf(startId)
        }
    }

    private fun handleEmptyStart(startId: Int) {
        synchronized(lock) {
            if (hasPendingDownloadsLocked()) {
                promoteToForeground()
            }
            scheduleLocked()
            stopIfIdleLocked(startId)
        }
    }

    private fun handleDownload(workId: String?, optionIds: List<String>, startId: Int) {
        val work = downloadRepository!!.getWork(workId)
        if (work == null) {
            stopSelf(startId)
            return
        }
        enqueueDownload(work, optionIds)
    }

    private fun enqueueDownload(work: DlsiteWork, optionIds: List<String>?) {
        synchronized(lock) {
            val task = downloadRepository!!.enqueueDownload(
                work,
                optionIds ?: emptyList(),
                if (optionIds == null) "" else optionIds.size.toString() + " 个内容",
            )
            if (task == null) {
                stopIfIdleLocked(latestStartId)
                return
            }
            publishQueuePositionsLocked()
            promoteToForeground()
            scheduleLocked()
        }
    }

    private fun scheduleLocked() {
        if (destroying) {
            publishQueuePositionsLocked()
            updateNotification()
            return
        }
        if (hasPendingDownloadsLocked()) {
            promoteToForeground()
        }
        while (runningWorkers.size < MAX_CONCURRENT_DOWNLOADS) {
            val pendingTasks = downloadRepository!!.pendingDownloadQueueTasks(1)
            if (pendingTasks.isEmpty()) {
                break
            }
            val pendingTask = pendingTasks[0]
            if (runningWorkers.containsKey(pendingTask.workId)) {
                downloadRepository!!.markDownloadQueueTaskCanceled(pendingTask.taskId)
                continue
            }
            val work = downloadRepository!!.getWork(pendingTask.workId)
            if (work == null) {
                downloadRepository!!.markDownloadQueueTaskFailed(pendingTask.taskId, "找不到作品记录")
                dlsiteDownloadStateStore!!.remove(pendingTask.workId)
                continue
            }
            val runningTask = downloadRepository!!.markDownloadQueueTaskRunning(pendingTask.taskId)
            if (runningTask == null) {
                continue
            }
            val request = DownloadRequest(
                runningTask.taskId,
                runningTask.workId,
                work.displayTitle(),
                runningTask.optionIdList(),
            )
            val worker = DownloadWorker(request, work)
            runningWorkers[request.workId] = worker
            worker.start()
        }
        publishQueuePositionsLocked()
        updateNotification()
    }

    private fun publishQueuePositionsLocked() {
        var index = 1
        for (task in downloadRepository!!.pendingDownloadQueueTasks(Int.MAX_VALUE)) {
            val work = downloadRepository!!.getWork(task.workId)
            val title = work?.displayTitle() ?: task.workId
            dlsiteDownloadStateStore!!.publishQueued(task.workId, title, index)
            index++
        }
    }

    private fun finishWorker(worker: DownloadWorker) {
        synchronized(lock) {
            runningWorkers.remove(worker.request.workId)
            if (!destroying) {
                scheduleLocked()
            }
            if (runningWorkers.isEmpty() && !hasPendingDownloadsLocked()) {
                stopForegroundSafely()
                stopSelf(latestStartId)
            }
        }
    }

    private fun handlePause(workId: String?, startId: Int) {
        val work = downloadRepository!!.getWork(workId)
        if (work == null) {
            stopSelf(startId)
            return
        }
        synchronized(lock) {
            val worker = runningWorkers[work.workId]
            if (worker != null) {
                worker.stopRequest = STOP_PAUSE
                dlsiteDownloadStateStore!!.publishPaused(work.workId, work.displayTitle())
                if (isContentDownload(worker.request.optionIds)) {
                    downloadRepository!!.markContentPaused(work.workId, worker.request.optionIds)
                } else {
                    markPausedSafely(work)
                }
                worker.interrupt()
                return
            }
            val queued = downloadRepository!!.pauseQueuedDownload(work.workId)
            if (queued != null) {
                if (isContentDownload(queued.optionIdList())) {
                    downloadRepository!!.markContentPaused(work.workId, queued.optionIdList())
                } else {
                    markPausedSafely(work)
                }
                dlsiteDownloadStateStore!!.publishPaused(work.workId, work.displayTitle())
                scheduleLocked()
                stopIfIdleLocked(startId)
                return
            }
        }
        stopSelf(startId)
    }

    private fun handleDelete(workId: String?, startId: Int) {
        val work = downloadRepository!!.getWork(workId)
        if (work == null) {
            stopSelf(startId)
            return
        }
        synchronized(lock) {
            val worker = runningWorkers[work.workId]
            if (worker != null) {
                worker.stopRequest = STOP_DELETE
                dlsiteDownloadStateStore!!.publishTask(
                    work.workId,
                    work.displayTitle(),
                    DlsiteDownloadTaskStatus.DOWNLOADING,
                    "删除中",
                )
                downloadRepository!!.markCacheDeleted(work)
                worker.interrupt()
                return
            }
            val queued = downloadRepository!!.cancelQueuedDownload(work.workId)
            if (queued != null) {
                deleteCachedWork(work)
                dlsiteDownloadStateStore!!.remove(work.workId)
                scheduleLocked()
                stopIfIdleLocked(startId)
                return
            }
        }
        deleteCachedWork(work)
        stopSelf(startId)
    }

    private fun stopIfIdleLocked(startId: Int) {
        if (runningWorkers.isEmpty() && !hasPendingDownloadsLocked()) {
            stopForegroundSafely()
            stopSelf(startId)
        }
    }

    private fun hasPendingDownloadsLocked(): Boolean {
        return downloadRepository!!.pendingDownloadQueueTasks(1).isNotEmpty()
    }

    private fun deleteCachedWork(work: DlsiteWork) {
        try {
            DlsiteDownloadTask.deleteCache(this, work)
        } catch (ignored: Exception) {
        }
        downloadRepository!!.markCacheDeleted(work)
    }

    private fun deleteCachedWorkSafely(work: DlsiteWork) {
        try {
            deleteCachedWork(work)
        } catch (ignored: Exception) {
        }
    }

    private fun markPausedSafely(work: DlsiteWork) {
        try {
            downloadRepository!!.markPaused(work)
        } catch (ignored: Exception) {
        }
    }

    private fun isContentDownload(optionIds: List<String>?): Boolean {
        return optionIds != null && optionIds.isNotEmpty()
    }

    private fun stopForegroundSafely() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (ignored: RuntimeException) {
        }
    }

    private fun promoteToForeground() {
        DlsiteDownloadNotifications.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                DlsiteDownloadNotifications.NOTIFICATION_ID,
                DlsiteDownloadNotifications.buildSummary(this, dlsiteDownloadStateStore!!.snapshot()),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(
                DlsiteDownloadNotifications.NOTIFICATION_ID,
                DlsiteDownloadNotifications.buildSummary(this, dlsiteDownloadStateStore!!.snapshot()),
            )
        }
    }

    private fun updateNotification() {
        DlsiteDownloadNotifications.updateSummary(this, dlsiteDownloadStateStore!!.snapshot())
    }

    private fun shortError(exception: Exception?): String {
        var message = exception?.message ?: ""
        if (TextUtils.isEmpty(message)) {
            message = "下载失败"
        }
        return if (message.length > 42) message.substring(0, 42) + "..." else message
    }

    private class DownloadRequest(
        taskId: String?,
        workId: String?,
        title: String?,
        optionIds: List<String>?,
    ) {
        val taskId: String = taskId ?: ""
        val workId: String = workId ?: ""
        val title: String = title ?: ""
        val optionIds: List<String> = if (optionIds == null) ArrayList() else ArrayList(optionIds)
    }

    private inner class DownloadWorker(
        val request: DownloadRequest,
        val initialWork: DlsiteWork,
    ) : Thread("dlsite-download-" + request.workId), DlsiteDownloadTask.ContentListener {
        @Volatile
        var stopRequest: String = STOP_NONE

        override fun run() {
            val work = initialWork
            val contentDownload = isContentDownload(request.optionIds)
            try {
                val api = dlsiteApi ?: throw IllegalStateException("DLsite API not initialized")
                val repository = downloadRepository ?: throw IllegalStateException("DLsite download repository not initialized")
                if (!contentDownload) {
                    repository.markDownloading(
                        work,
                        TextUtils.join("|", request.optionIds),
                        request.optionIds.size.toString() + " 个内容",
                    )
                    dlsiteDownloadStateStore!!.publishTask(
                        work.workId,
                        work.displayTitle(),
                        DlsiteDownloadTaskStatus.DOWNLOADING,
                        "下载中",
                    )
                    updateNotification()
                }
                val result = DlsiteDownloadTask.downloadAndImport(
                    this@DlsiteDownloadService,
                    api,
                    repository,
                    work,
                    request.optionIds,
                    this,
                )
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
                    dlsiteDownloadStateStore!!.publishCompleted(work.workId, work.displayTitle())
                    updateNotification()
                }
            } catch (exception: Exception) {
                if (STOP_PAUSE == stopRequest) {
                    downloadRepository!!.markDownloadQueueTaskPaused(request.taskId)
                    if (!contentDownload) {
                        markPausedSafely(work)
                    }
                    downloadRepository!!.markContentPaused(work.workId, request.optionIds)
                    dlsiteDownloadStateStore!!.publishPaused(work.workId, work.displayTitle())
                } else if (STOP_DELETE == stopRequest) {
                    downloadRepository!!.markDownloadQueueTaskCanceled(request.taskId)
                    deleteCachedWorkSafely(work)
                    dlsiteDownloadStateStore!!.remove(work.workId)
                } else if (STOP_RESCHEDULE == stopRequest) {
                    downloadRepository!!.markDownloadQueueTaskPending(request.taskId)
                    if (contentDownload) {
                        downloadRepository!!.markContentQueued(work.workId, request.optionIds)
                    } else {
                        downloadRepository!!.markQueued(
                            work,
                            request.optionIds,
                            request.optionIds.size.toString() + " 个内容",
                        )
                    }
                    dlsiteDownloadStateStore!!.publishQueued(work.workId, work.displayTitle(), 1)
                } else {
                    val message = shortError(exception)
                    downloadRepository!!.markDownloadQueueTaskFailed(request.taskId, message)
                    if (!contentDownload) {
                        downloadRepository!!.markFailed(work, message)
                    }
                    for (optionId in request.optionIds) {
                        downloadRepository!!.markContentFailed(work.workId, optionId, message)
                    }
                    dlsiteDownloadStateStore!!.publishFailed(work.workId, work.displayTitle(), message)
                }
                updateNotification()
            } finally {
                finishWorker(this)
            }
        }

        override fun onContentStarted(option: DlsiteDownloadOption?, contentDir: File?) {
            downloadRepository!!.markContentDownloading(request.workId, option!!.id)
        }

        override fun onContentProgress(
            option: DlsiteDownloadOption?,
            contentFile: DlsiteJsonParser.ContentFile?,
            bytesDownloaded: Long,
            totalBytes: Long,
        ) {
            dlsiteDownloadStateStore!!.publishTask(
                request.workId,
                initialWork.displayTitle(),
                DlsiteDownloadTaskStatus.DOWNLOADING,
                "下载中",
                0,
                bytesDownloaded,
                totalBytes,
                0L,
                option!!.id,
                option.title,
            )
            updateNotification()
        }

        override fun onContentFinished(option: DlsiteDownloadOption?, result: DlsiteDownloadTask.ContentResult?) {
            downloadRepository!!.markContentDownloaded(
                request.workId,
                option!!.id,
                result!!.title,
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

        fun downloadIntent(context: Context?, workId: String?, optionId: String?): Intent {
            val optionIds = ArrayList<String>()
            if (optionId != null) {
                optionIds.add(optionId)
            }
            return downloadIntent(context, workId, optionIds)
        }

        fun downloadIntent(context: Context?, workId: String?, optionIds: List<String>?): Intent {
            val intent = Intent(context, DlsiteDownloadService::class.java)
            intent.action = ACTION_DOWNLOAD
            intent.putExtra(EXTRA_WORK_ID, workId ?: "")
            val ids = ArrayList<String>()
            if (optionIds != null) {
                ids.addAll(optionIds)
            }
            intent.putStringArrayListExtra(EXTRA_OPTION_IDS, ids)
            intent.putExtra(EXTRA_OPTION_ID, if (ids.isEmpty()) "" else ids[0])
            return intent
        }

        fun pauseIntent(context: Context?, workId: String?): Intent {
            val intent = Intent(context, DlsiteDownloadService::class.java)
            intent.action = ACTION_PAUSE
            intent.putExtra(EXTRA_WORK_ID, workId ?: "")
            return intent
        }

        fun deleteIntent(context: Context?, workId: String?): Intent {
            val intent = Intent(context, DlsiteDownloadService::class.java)
            intent.action = ACTION_DELETE
            intent.putExtra(EXTRA_WORK_ID, workId ?: "")
            return intent
        }
    }
}
