package io.github.summerdez.asmrplayer.data.download

import androidx.room.withTransaction
import io.github.summerdez.asmrplayer.data.AsrmDatabase
import io.github.summerdez.asmrplayer.data.DlsiteDao
import io.github.summerdez.asmrplayer.data.toContent
import io.github.summerdez.asmrplayer.data.toEntity
import io.github.summerdez.asmrplayer.data.toQueueTask
import io.github.summerdez.asmrplayer.data.toWork
import io.github.summerdez.asmrplayer.domain.model.DlsiteContent
import io.github.summerdez.asmrplayer.domain.model.DlsiteDownloadQueueTask
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface DlsiteDownloadQueueRepository {
    suspend fun enqueueDownload(
        work: DlsiteWork,
        optionIds: List<String>,
        optionTitle: String?,
    ): DlsiteDownloadQueueTask?

    suspend fun pendingDownloadQueueTasks(limit: Int): List<DlsiteDownloadQueueTask>
    suspend fun activeDownloadQueueTasks(): List<DlsiteDownloadQueueTask>
    suspend fun resetRunningDownloadQueue(): Int
    suspend fun markDownloadQueueTaskRunning(taskId: String): DlsiteDownloadQueueTask?
    suspend fun markDownloadQueueTaskCompleted(taskId: String)
    suspend fun markDownloadQueueTaskFailed(taskId: String, error: String?)
    suspend fun markDownloadQueueTaskPaused(taskId: String)
    suspend fun markDownloadQueueTaskCanceled(taskId: String)
    suspend fun markDownloadQueueTaskPending(taskId: String)
    suspend fun pauseQueuedDownload(workId: String): DlsiteDownloadQueueTask?
    suspend fun cancelQueuedDownload(workId: String): DlsiteDownloadQueueTask?
    suspend fun markAllQueuedDownloadsPaused()
}

class RoomDlsiteDownloadQueueRepository private constructor(
    private val delegate: RoomDlsiteDownloadQueueRepositoryDelegate,
) : DlsiteDownloadQueueRepository {
    constructor(
        database: AsrmDatabase,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(
        delegate = RoomDlsiteDownloadQueueRepositoryDelegate(
            dlsiteDao = database.dlsiteDao(),
            transactionRunner = object : DlsiteDownloadQueueTransactionRunner {
                override suspend fun <T> run(block: suspend () -> T): T {
                    return database.withTransaction { block() }
                }
            },
            ioDispatcher = ioDispatcher,
        ),
    )

    internal constructor(
        dlsiteDao: DlsiteDao,
        transactionRunner: DlsiteDownloadQueueTransactionRunner,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(
        delegate = RoomDlsiteDownloadQueueRepositoryDelegate(
            dlsiteDao = dlsiteDao,
            transactionRunner = transactionRunner,
            ioDispatcher = ioDispatcher,
        ),
    )

    override suspend fun enqueueDownload(
        work: DlsiteWork,
        optionIds: List<String>,
        optionTitle: String?,
    ): DlsiteDownloadQueueTask? = delegate.enqueueDownload(work, optionIds, optionTitle)

    override suspend fun pendingDownloadQueueTasks(limit: Int): List<DlsiteDownloadQueueTask> =
        delegate.pendingDownloadQueueTasks(limit)

    override suspend fun activeDownloadQueueTasks(): List<DlsiteDownloadQueueTask> =
        delegate.activeDownloadQueueTasks()

    override suspend fun resetRunningDownloadQueue(): Int =
        delegate.resetRunningDownloadQueue()

    override suspend fun markDownloadQueueTaskRunning(taskId: String): DlsiteDownloadQueueTask? =
        delegate.markDownloadQueueTaskRunning(taskId)

    override suspend fun markDownloadQueueTaskCompleted(taskId: String) {
        delegate.markDownloadQueueTaskCompleted(taskId)
    }

    override suspend fun markDownloadQueueTaskFailed(taskId: String, error: String?) {
        delegate.markDownloadQueueTaskFailed(taskId, error)
    }

    override suspend fun markDownloadQueueTaskPaused(taskId: String) {
        delegate.markDownloadQueueTaskPaused(taskId)
    }

    override suspend fun markDownloadQueueTaskCanceled(taskId: String) {
        delegate.markDownloadQueueTaskCanceled(taskId)
    }

    override suspend fun markDownloadQueueTaskPending(taskId: String) {
        delegate.markDownloadQueueTaskPending(taskId)
    }

    override suspend fun pauseQueuedDownload(workId: String): DlsiteDownloadQueueTask? =
        delegate.pauseQueuedDownload(workId)

    override suspend fun cancelQueuedDownload(workId: String): DlsiteDownloadQueueTask? =
        delegate.cancelQueuedDownload(workId)

    override suspend fun markAllQueuedDownloadsPaused() {
        delegate.markAllQueuedDownloadsPaused()
    }
}

internal interface DlsiteDownloadQueueTransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

private class RoomDlsiteDownloadQueueRepositoryDelegate(
    private val dlsiteDao: DlsiteDao,
    private val transactionRunner: DlsiteDownloadQueueTransactionRunner,
    private val ioDispatcher: CoroutineDispatcher,
) : DlsiteDownloadQueueRepository {
    override suspend fun enqueueDownload(
        work: DlsiteWork,
        optionIds: List<String>,
        optionTitle: String?,
    ): DlsiteDownloadQueueTask? {
        if (work.workId.isEmpty()) {
            return null
        }
        val sanitizedOptionIds = optionIds.filter { it.isNotBlank() }
        return withContext(ioDispatcher) {
            transactionRunner.run {
                dlsiteDao.activeDownloadQueueByWorkId(work.workId)?.toQueueTask()?.let { existing ->
                    return@run existing
                }
                val now = System.currentTimeMillis()
                val task = DlsiteDownloadQueueTask.create(
                    workId = work.workId,
                    optionIds = sanitizedOptionIds,
                    queueOrder = dlsiteDao.nextDownloadQueueOrder(),
                    now = now,
                )
                dlsiteDao.upsertDownloadQueue(task.toEntity())
                markQueuedInCurrentTransaction(work, sanitizedOptionIds, optionTitle)
                task
            }
        }
    }

    override suspend fun pendingDownloadQueueTasks(limit: Int): List<DlsiteDownloadQueueTask> {
        if (limit <= 0) {
            return emptyList()
        }
        return withContext(ioDispatcher) {
            dlsiteDao.pendingDownloadQueue(limit).map { it.toQueueTask() }
        }
    }

    override suspend fun activeDownloadQueueTasks(): List<DlsiteDownloadQueueTask> {
        return withContext(ioDispatcher) {
            dlsiteDao.activeDownloadQueue().map { it.toQueueTask() }
        }
    }

    override suspend fun resetRunningDownloadQueue(): Int {
        return withContext(ioDispatcher) {
            dlsiteDao.resetRunningDownloadQueue(System.currentTimeMillis())
        }
    }

    override suspend fun markDownloadQueueTaskRunning(taskId: String): DlsiteDownloadQueueTask? {
        if (taskId.isEmpty()) {
            return null
        }
        return withContext(ioDispatcher) {
            val now = System.currentTimeMillis()
            val changed = dlsiteDao.markDownloadQueueRunning(taskId, now)
            if (changed == 0) {
                null
            } else {
                dlsiteDao.downloadQueueByTaskId(taskId)?.toQueueTask()
            }
        }
    }

    override suspend fun markDownloadQueueTaskCompleted(taskId: String) {
        finishDownloadQueueTask(taskId, DlsiteDownloadQueueTask.STATUS_COMPLETED, null)
    }

    override suspend fun markDownloadQueueTaskFailed(taskId: String, error: String?) {
        finishDownloadQueueTask(taskId, DlsiteDownloadQueueTask.STATUS_FAILED, error)
    }

    override suspend fun markDownloadQueueTaskPaused(taskId: String) {
        finishDownloadQueueTask(taskId, DlsiteDownloadQueueTask.STATUS_PAUSED, null)
    }

    override suspend fun markDownloadQueueTaskCanceled(taskId: String) {
        finishDownloadQueueTask(taskId, DlsiteDownloadQueueTask.STATUS_CANCELED, null)
    }

    override suspend fun markDownloadQueueTaskPending(taskId: String) {
        if (taskId.isEmpty()) {
            return
        }
        withContext(ioDispatcher) {
            dlsiteDao.markDownloadQueuePending(taskId, System.currentTimeMillis())
        }
    }

    override suspend fun pauseQueuedDownload(workId: String): DlsiteDownloadQueueTask? {
        return finishActiveQueueTaskForWork(workId, DlsiteDownloadQueueTask.STATUS_PAUSED, null)
    }

    override suspend fun cancelQueuedDownload(workId: String): DlsiteDownloadQueueTask? {
        return finishActiveQueueTaskForWork(workId, DlsiteDownloadQueueTask.STATUS_CANCELED, null)
    }

    override suspend fun markAllQueuedDownloadsPaused() {
        withContext(ioDispatcher) {
            val now = System.currentTimeMillis()
            dlsiteDao.activeDownloadQueue().forEach { task ->
                dlsiteDao.finishDownloadQueue(task.taskId, DlsiteDownloadQueueTask.STATUS_PAUSED, now, null)
            }
            dlsiteDao.works().map { it.toWork() }.forEach { work ->
                if (work.isQueued() || work.isDownloading()) {
                    dlsiteDao.upsert(work.asPaused().toEntity())
                }
            }
            dlsiteDao.contents().map { it.toContent() }.forEach { content ->
                if (content.isQueued() || content.isDownloading()) {
                    dlsiteDao.upsertContent(content.asPaused().toEntity())
                }
            }
        }
    }

    private suspend fun finishDownloadQueueTask(taskId: String, status: String, error: String?) {
        if (taskId.isEmpty()) {
            return
        }
        withContext(ioDispatcher) {
            dlsiteDao.finishDownloadQueue(taskId, status, System.currentTimeMillis(), error)
        }
    }

    private suspend fun finishActiveQueueTaskForWork(
        workId: String,
        status: String,
        error: String?,
    ): DlsiteDownloadQueueTask? {
        if (workId.isEmpty()) {
            return null
        }
        return withContext(ioDispatcher) {
            val task = dlsiteDao.activeDownloadQueueByWorkId(workId)?.toQueueTask() ?: return@withContext null
            dlsiteDao.finishDownloadQueue(task.taskId, status, System.currentTimeMillis(), error)
            task
        }
    }

    private suspend fun markQueuedInCurrentTransaction(
        work: DlsiteWork,
        optionIds: List<String>,
        optionTitle: String?,
    ) {
        val existing = dlsiteDao.workById(work.workId)?.toWork()
        val sanitizedOptionIds = optionIds.filter { it.isNotBlank() }
        val queued = work
            .withDownloadOption(sanitizedOptionIds.joinToString(OPTION_ID_SEPARATOR), optionTitle)
            .let { nextWork ->
                if (sanitizedOptionIds.isEmpty()) {
                    nextWork.asQueued()
                } else {
                    nextWork.copy(status = DlsiteWork.STATUS_FOUND, error = "")
                }
            }
            .withRepositoryUpdatedAt(existing)
            .let { nextWork ->
                if (sanitizedOptionIds.isEmpty()) {
                    nextWork
                } else {
                    nextWork.copy(updatedAt = existing?.updatedAt ?: nextWork.updatedAt)
                }
            }
        dlsiteDao.upsert(queued.toEntity())
        updateContentsInCurrentTransaction(work.workId, optionIds) { it.asQueued() }
    }

    private suspend fun updateContentsInCurrentTransaction(
        workId: String,
        optionIds: List<String>,
        transform: (DlsiteContent) -> DlsiteContent,
    ) {
        if (workId.isEmpty()) {
            return
        }
        val ids = optionIds.ifEmpty { listOf("") }.toSet()
        dlsiteDao.contentsForWork(workId).map { it.toContent() }.forEach { content ->
            if (ids.contains(content.optionId)) {
                dlsiteDao.upsertContent(transform(content).toEntity())
            }
        }
    }

    private fun DlsiteWork.withRepositoryUpdatedAt(existing: DlsiteWork?): DlsiteWork {
        return copy(
            updatedAt = when {
                isQueued() || isDownloading() -> existing?.updatedAt ?: updatedAt
                else -> System.currentTimeMillis()
            },
        )
    }

    private companion object {
        const val OPTION_ID_SEPARATOR = "|"
    }
}
