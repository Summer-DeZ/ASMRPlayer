package io.github.summerdez.asmrplayer

import io.github.summerdez.asmrplayer.data.DlsiteContentEntity
import io.github.summerdez.asmrplayer.data.DlsiteDao
import io.github.summerdez.asmrplayer.data.DlsiteDownloadQueueEntity
import io.github.summerdez.asmrplayer.data.DlsiteWorkEntity
import io.github.summerdez.asmrplayer.data.download.DlsiteDownloadQueueTransactionRunner
import io.github.summerdez.asmrplayer.data.download.RoomDlsiteDownloadQueueRepository
import io.github.summerdez.asmrplayer.data.toContent
import io.github.summerdez.asmrplayer.data.toEntity
import io.github.summerdez.asmrplayer.data.toWork
import io.github.summerdez.asmrplayer.domain.model.DlsiteContent
import io.github.summerdez.asmrplayer.domain.model.DlsiteDownloadQueueTask
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DlsiteDownloadQueueRepositoryTest {
    @Test
    fun enqueueCreatesTaskAndMarksWorkAndContentQueuedAndDedupesActiveWork() = runBlocking {
        val dao = FakeDlsiteDao()
        val transactionRunner = FakeTransactionRunner()
        val repository = repository(dao, transactionRunner)
        val work = work("RJ00000001", updatedAt = 100L)
        dao.upsert(work.toEntity())
        dao.upsertContent(content(work.workId, "").toEntity())

        val first = repository.enqueueDownload(work, emptyList(), null)
        val second = repository.enqueueDownload(work, listOf("mp3"), "MP3")

        assertNotNull(first)
        assertEquals(first, second)
        assertEquals(1, dao.queue.size)
        assertEquals(2, transactionRunner.runCount)
        assertEquals(DlsiteDownloadQueueTask.STATUS_PENDING, first?.status)
        assertEquals(DlsiteWork.STATUS_QUEUED, dao.workById(work.workId)?.toWork()?.status)
        assertEquals(DlsiteContent.STATUS_QUEUED, dao.contentById(work.workId, "")?.toContent()?.status)
    }

    @Test
    fun pendingTasksUseFifoOrderAndLimit() = runBlocking {
        val dao = FakeDlsiteDao()
        val repository = repository(dao)

        val first = repository.enqueueDownload(work("RJ00000001"), emptyList(), null)
        val second = repository.enqueueDownload(work("RJ00000002"), emptyList(), null)
        repository.enqueueDownload(work("RJ00000003"), emptyList(), null)

        assertEquals(
            listOf(first?.taskId, second?.taskId),
            repository.pendingDownloadQueueTasks(limit = 2).map { it.taskId },
        )
        assertEquals(emptyList<DlsiteDownloadQueueTask>(), repository.pendingDownloadQueueTasks(limit = 0))
    }

    @Test
    fun resetRunningTasksRestoresPendingState() = runBlocking {
        val dao = FakeDlsiteDao()
        val repository = repository(dao)
        val task = repository.enqueueDownload(work("RJ00000001"), emptyList(), null)

        val running = repository.markDownloadQueueTaskRunning(task?.taskId)
        val resetCount = repository.resetRunningDownloadQueue()
        val reset = dao.downloadQueueByTaskId(task?.taskId.orEmpty())

        assertEquals(DlsiteDownloadQueueTask.STATUS_RUNNING, running?.status)
        assertEquals(1, resetCount)
        assertEquals(DlsiteDownloadQueueTask.STATUS_PENDING, reset?.status)
        assertNull(reset?.startedAt)
        assertNull(reset?.finishedAt)
        assertNull(reset?.errorMessage)
    }

    @Test
    fun pauseAndCancelQueuedDownloadsReturnTaskAndFinishQueueState() = runBlocking {
        val dao = FakeDlsiteDao()
        val repository = repository(dao)
        val pausedTask = repository.enqueueDownload(work("RJ00000001"), emptyList(), null)
        val canceledTask = repository.enqueueDownload(work("RJ00000002"), emptyList(), null)

        val paused = repository.pauseQueuedDownload("RJ00000001")
        val canceled = repository.cancelQueuedDownload("RJ00000002")

        assertEquals(pausedTask?.taskId, paused?.taskId)
        assertEquals(canceledTask?.taskId, canceled?.taskId)
        assertEquals(DlsiteDownloadQueueTask.STATUS_PAUSED, dao.downloadQueueByTaskId(pausedTask?.taskId.orEmpty())?.status)
        assertEquals(DlsiteDownloadQueueTask.STATUS_CANCELED, dao.downloadQueueByTaskId(canceledTask?.taskId.orEmpty())?.status)
        assertEquals(emptyList<DlsiteDownloadQueueTask>(), repository.activeDownloadQueueTasks())
    }

    @Test
    fun finishedTasksLeaveActiveQueueForEveryTerminalStatus() = runBlocking {
        val dao = FakeDlsiteDao()
        val repository = repository(dao)
        val completed = repository.enqueueDownload(work("RJ00000001"), emptyList(), null)
        val failed = repository.enqueueDownload(work("RJ00000002"), emptyList(), null)
        val paused = repository.enqueueDownload(work("RJ00000003"), emptyList(), null)
        val canceled = repository.enqueueDownload(work("RJ00000004"), emptyList(), null)

        repository.markDownloadQueueTaskRunning(completed?.taskId)
        repository.markDownloadQueueTaskRunning(failed?.taskId)
        repository.markDownloadQueueTaskRunning(paused?.taskId)
        repository.markDownloadQueueTaskRunning(canceled?.taskId)
        repository.markDownloadQueueTaskCompleted(completed?.taskId)
        repository.markDownloadQueueTaskFailed(failed?.taskId, "network")
        repository.markDownloadQueueTaskPaused(paused?.taskId)
        repository.markDownloadQueueTaskCanceled(canceled?.taskId)

        assertEquals(emptyList<DlsiteDownloadQueueTask>(), repository.activeDownloadQueueTasks())
        assertEquals(DlsiteDownloadQueueTask.STATUS_COMPLETED, dao.downloadQueueByTaskId(completed?.taskId.orEmpty())?.status)
        assertEquals(DlsiteDownloadQueueTask.STATUS_FAILED, dao.downloadQueueByTaskId(failed?.taskId.orEmpty())?.status)
        assertEquals("network", dao.downloadQueueByTaskId(failed?.taskId.orEmpty())?.errorMessage)
        assertEquals(DlsiteDownloadQueueTask.STATUS_PAUSED, dao.downloadQueueByTaskId(paused?.taskId.orEmpty())?.status)
        assertEquals(DlsiteDownloadQueueTask.STATUS_CANCELED, dao.downloadQueueByTaskId(canceled?.taskId.orEmpty())?.status)
    }

    private fun repository(
        dao: FakeDlsiteDao,
        transactionRunner: FakeTransactionRunner = FakeTransactionRunner(),
    ): RoomDlsiteDownloadQueueRepository {
        return RoomDlsiteDownloadQueueRepository(
            dlsiteDao = dao,
            transactionRunner = transactionRunner,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    private fun work(workId: String, updatedAt: Long = 100L): DlsiteWork {
        return DlsiteWork(
            workId = workId,
            title = workId,
            detailUrl = "",
            downloadUrl = "",
            updatedAt = updatedAt,
        )
    }

    private fun content(workId: String, optionId: String): DlsiteContent {
        return DlsiteContent(workId = workId, optionId = optionId, title = optionId.ifEmpty { "默认版本" })
    }
}

private class FakeTransactionRunner : DlsiteDownloadQueueTransactionRunner {
    var runCount: Int = 0
        private set

    override suspend fun <T> run(block: suspend () -> T): T {
        runCount++
        return block()
    }
}

private class FakeDlsiteDao : DlsiteDao {
    val works = linkedMapOf<String, DlsiteWorkEntity>()
    val contents = linkedMapOf<Pair<String, String>, DlsiteContentEntity>()
    val queue = linkedMapOf<String, DlsiteDownloadQueueEntity>()

    override suspend fun works(): List<DlsiteWorkEntity> {
        return works.values.sortedWith(compareByDescending<DlsiteWorkEntity> { it.updatedAt }.thenBy { it.workId })
    }

    override fun workFlow(): Flow<List<DlsiteWorkEntity>> = flowOf(emptyList())

    override suspend fun workById(workId: String): DlsiteWorkEntity? = works[workId]

    override suspend fun contents(): List<DlsiteContentEntity> {
        return contents.values.sortedWith(
            compareByDescending<DlsiteContentEntity> { it.updatedAt }
                .thenBy { it.workId }
                .thenBy { it.title.lowercase() }
                .thenBy { it.optionId },
        )
    }

    override fun contentFlow(): Flow<List<DlsiteContentEntity>> = flowOf(emptyList())

    override suspend fun contentsForWork(workId: String): List<DlsiteContentEntity> {
        return contents.values
            .filter { it.workId == workId }
            .sortedWith(compareBy<DlsiteContentEntity> { it.title.lowercase() }.thenBy { it.optionId })
    }

    override suspend fun contentById(workId: String, optionId: String): DlsiteContentEntity? {
        return contents[workId to optionId]
    }

    override suspend fun activeDownloadQueueByWorkId(workId: String): DlsiteDownloadQueueEntity? {
        return queue.values
            .filter { it.workId == workId && it.status in activeStatuses }
            .sortedWith(queueOrderComparator)
            .firstOrNull()
    }

    override suspend fun downloadQueueByTaskId(taskId: String): DlsiteDownloadQueueEntity? = queue[taskId]

    override suspend fun pendingDownloadQueue(limit: Int): List<DlsiteDownloadQueueEntity> {
        return queue.values
            .filter { it.status == DlsiteDownloadQueueTask.STATUS_PENDING }
            .sortedWith(queueOrderComparator)
            .take(limit)
    }

    override suspend fun activeDownloadQueue(): List<DlsiteDownloadQueueEntity> {
        return queue.values
            .filter { it.status in activeStatuses }
            .sortedWith(queueOrderComparator)
    }

    override suspend fun nextDownloadQueueOrder(): Long {
        return (queue.values.maxOfOrNull { it.queueOrder } ?: 0L) + 1L
    }

    override suspend fun upsert(work: DlsiteWorkEntity) {
        works[work.workId] = work
    }

    override suspend fun upsertAll(works: List<DlsiteWorkEntity>) {
        works.forEach { upsert(it) }
    }

    override suspend fun upsertContent(content: DlsiteContentEntity) {
        contents[content.workId to content.optionId] = content
    }

    override suspend fun upsertContents(contents: List<DlsiteContentEntity>) {
        contents.forEach { upsertContent(it) }
    }

    override suspend fun upsertDownloadQueue(task: DlsiteDownloadQueueEntity) {
        queue[task.taskId] = task
    }

    override suspend fun resetRunningDownloadQueue(now: Long): Int {
        return updateMatchingQueue(
            predicate = { it.status == DlsiteDownloadQueueTask.STATUS_RUNNING },
            update = { task ->
                task.copy(
                    status = DlsiteDownloadQueueTask.STATUS_PENDING,
                    startedAt = null,
                    updatedAt = now,
                    finishedAt = null,
                    errorMessage = null,
                )
            },
        )
    }

    override suspend fun markDownloadQueueRunning(taskId: String, now: Long): Int {
        val task = queue[taskId] ?: return 0
        if (task.status != DlsiteDownloadQueueTask.STATUS_PENDING) {
            return 0
        }
        queue[taskId] = task.copy(
            status = DlsiteDownloadQueueTask.STATUS_RUNNING,
            startedAt = now,
            updatedAt = now,
            finishedAt = null,
            errorMessage = null,
        )
        return 1
    }

    override suspend fun finishDownloadQueue(
        taskId: String,
        status: String,
        now: Long,
        errorMessage: String?,
    ): Int {
        val task = queue[taskId] ?: return 0
        queue[taskId] = task.copy(
            status = status,
            updatedAt = now,
            finishedAt = now,
            errorMessage = errorMessage,
        )
        return 1
    }

    override suspend fun markDownloadQueuePending(taskId: String, now: Long): Int {
        val task = queue[taskId] ?: return 0
        queue[taskId] = task.copy(
            status = DlsiteDownloadQueueTask.STATUS_PENDING,
            startedAt = null,
            updatedAt = now,
            finishedAt = null,
            errorMessage = null,
        )
        return 1
    }

    private fun updateMatchingQueue(
        predicate: (DlsiteDownloadQueueEntity) -> Boolean,
        update: (DlsiteDownloadQueueEntity) -> DlsiteDownloadQueueEntity,
    ): Int {
        val matches = queue.values.filter(predicate)
        matches.forEach { task -> queue[task.taskId] = update(task) }
        return matches.size
    }

    private companion object {
        val activeStatuses = setOf(
            DlsiteDownloadQueueTask.STATUS_PENDING,
            DlsiteDownloadQueueTask.STATUS_RUNNING,
        )
        val queueOrderComparator =
            compareBy<DlsiteDownloadQueueEntity> { it.queueOrder }.thenBy { it.createdAt }
    }
}
