package io.github.summerdez.asmrplayer.data

import io.github.summerdez.asmrplayer.data.download.DlsiteDownloadQueueRepository
import io.github.summerdez.asmrplayer.domain.model.DlsiteContent
import io.github.summerdez.asmrplayer.domain.model.DlsiteDownloadOption
import io.github.summerdez.asmrplayer.domain.model.DlsiteDownloadQueueTask
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface DlsiteRepository {
    val worksFlow: Flow<List<DlsiteWork>>
    val contentsFlow: Flow<List<DlsiteContent>>
    val lastSyncMsFlow: Flow<Long>
    val downloadStateFlow: StateFlow<DlsiteDownloadState>

    fun hasLoginCookie(): Boolean
    fun fetchPurchasedWorks(): List<DlsiteWork>
    fun fetchDownloadOptions(work: DlsiteWork): List<DlsiteDownloadOption>
    fun downloadCover(work: DlsiteWork, outputDir: File): File
    suspend fun getWork(workId: String): DlsiteWork?
    suspend fun getContents(workId: String): List<DlsiteContent>
    suspend fun enqueueDownload(work: DlsiteWork, optionIds: List<String>, optionTitle: String?): DlsiteDownloadQueueTask?
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
    suspend fun mergeDiscoveredWorks(discoveredWorks: List<DlsiteWork>): List<DlsiteWork>
    suspend fun saveWork(updatedWork: DlsiteWork)
    suspend fun saveDownloadOptions(work: DlsiteWork, options: List<DlsiteDownloadOption>): List<DlsiteContent>
    suspend fun markDownloading(work: DlsiteWork)
    suspend fun markDownloading(work: DlsiteWork, optionId: String, optionTitle: String?)
    suspend fun markQueued(work: DlsiteWork, optionIds: List<String>, optionTitle: String?)
    suspend fun markPaused(work: DlsiteWork)
    suspend fun markDownloaded(work: DlsiteWork, playlistId: String, localPath: String, trackCount: Int)
    suspend fun markImported(work: DlsiteWork, playlistId: String, localPath: String, trackCount: Int)
    suspend fun markFailed(work: DlsiteWork, error: String?)
    suspend fun markInterruptedDownloads(error: String?): Int
    suspend fun markCacheDeleted(work: DlsiteWork)
    suspend fun markContentQueued(workId: String, optionIds: List<String>)
    suspend fun markContentDownloading(workId: String, optionId: String)
    suspend fun markContentDownloaded(
        workId: String,
        optionId: String,
        optionTitle: String?,
        localPath: String,
        trackIds: List<String>,
        trackCount: Int,
    )
    suspend fun markContentFailed(workId: String, optionId: String, error: String?)
    suspend fun markContentPaused(workId: String, optionIds: List<String>)
    suspend fun markContentCacheDeleted(workId: String, optionId: String)
    suspend fun markAllQueuedDownloadsPaused()
    fun clearCompletedDownloadTasks()
}

class RoomDlsiteRepository(
    private val localStore: DlsiteLocalStore,
    private val remoteSource: DlsiteRemoteSource,
    private val downloadQueueRepository: DlsiteDownloadQueueRepository,
    private val downloadStateStore: DlsiteDownloadStateStore,
) : DlsiteRepository {
    override val worksFlow: Flow<List<DlsiteWork>> =
        localStore.worksFlow

    override val contentsFlow: Flow<List<DlsiteContent>> =
        localStore.contentsFlow

    override val lastSyncMsFlow: Flow<Long> =
        localStore.lastSyncMsFlow

    override val downloadStateFlow: StateFlow<DlsiteDownloadState> =
        downloadStateStore.state

    override fun clearCompletedDownloadTasks() {
        downloadStateStore.clearCompleted()
    }

    override fun hasLoginCookie(): Boolean {
        return remoteSource.hasLoginCookie()
    }

    override fun fetchPurchasedWorks(): List<DlsiteWork> {
        return remoteSource.fetchPurchasedWorks()
    }

    override fun fetchDownloadOptions(work: DlsiteWork): List<DlsiteDownloadOption> {
        return remoteSource.fetchDownloadOptions(work)
    }

    override fun downloadCover(work: DlsiteWork, outputDir: File): File {
        return remoteSource.downloadCover(work, outputDir)
    }

    override suspend fun getWork(workId: String): DlsiteWork? =
        localStore.getWork(workId)

    override suspend fun getContents(workId: String): List<DlsiteContent> =
        localStore.getContents(workId)

    override suspend fun enqueueDownload(
        work: DlsiteWork,
        optionIds: List<String>,
        optionTitle: String?,
    ): DlsiteDownloadQueueTask? =
        downloadQueueRepository.enqueueDownload(work, optionIds, optionTitle)

    override suspend fun pendingDownloadQueueTasks(limit: Int): List<DlsiteDownloadQueueTask> =
        downloadQueueRepository.pendingDownloadQueueTasks(limit)

    override suspend fun activeDownloadQueueTasks(): List<DlsiteDownloadQueueTask> =
        downloadQueueRepository.activeDownloadQueueTasks()

    override suspend fun resetRunningDownloadQueue(): Int =
        downloadQueueRepository.resetRunningDownloadQueue()

    override suspend fun markDownloadQueueTaskRunning(taskId: String): DlsiteDownloadQueueTask? =
        downloadQueueRepository.markDownloadQueueTaskRunning(taskId)

    override suspend fun markDownloadQueueTaskCompleted(taskId: String) {
        downloadQueueRepository.markDownloadQueueTaskCompleted(taskId)
    }

    override suspend fun markDownloadQueueTaskFailed(taskId: String, error: String?) {
        downloadQueueRepository.markDownloadQueueTaskFailed(taskId, error)
    }

    override suspend fun markDownloadQueueTaskPaused(taskId: String) {
        downloadQueueRepository.markDownloadQueueTaskPaused(taskId)
    }

    override suspend fun markDownloadQueueTaskCanceled(taskId: String) {
        downloadQueueRepository.markDownloadQueueTaskCanceled(taskId)
    }

    override suspend fun markDownloadQueueTaskPending(taskId: String) {
        downloadQueueRepository.markDownloadQueueTaskPending(taskId)
    }

    override suspend fun pauseQueuedDownload(workId: String): DlsiteDownloadQueueTask? =
        downloadQueueRepository.pauseQueuedDownload(workId)

    override suspend fun cancelQueuedDownload(workId: String): DlsiteDownloadQueueTask? =
        downloadQueueRepository.cancelQueuedDownload(workId)

    override suspend fun mergeDiscoveredWorks(discoveredWorks: List<DlsiteWork>): List<DlsiteWork> =
        localStore.mergeDiscoveredWorks(discoveredWorks)

    override suspend fun saveWork(updatedWork: DlsiteWork) {
        localStore.saveWork(updatedWork)
    }

    override suspend fun saveDownloadOptions(work: DlsiteWork, options: List<DlsiteDownloadOption>): List<DlsiteContent> =
        localStore.saveDownloadOptions(work, options)

    override suspend fun markDownloading(work: DlsiteWork) {
        localStore.markDownloading(work)
    }

    override suspend fun markDownloading(work: DlsiteWork, optionId: String, optionTitle: String?) {
        localStore.markDownloading(work, optionId, optionTitle)
    }

    override suspend fun markQueued(work: DlsiteWork, optionIds: List<String>, optionTitle: String?) {
        localStore.markQueued(work, optionIds, optionTitle)
    }

    override suspend fun markPaused(work: DlsiteWork) {
        localStore.markPaused(work)
    }

    override suspend fun markDownloaded(work: DlsiteWork, playlistId: String, localPath: String, trackCount: Int) {
        localStore.markDownloaded(work, playlistId, localPath, trackCount)
    }

    override suspend fun markImported(work: DlsiteWork, playlistId: String, localPath: String, trackCount: Int) {
        localStore.markImported(work, playlistId, localPath, trackCount)
    }

    override suspend fun markFailed(work: DlsiteWork, error: String?) {
        localStore.markFailed(work, error)
    }

    override suspend fun markInterruptedDownloads(error: String?): Int =
        localStore.markInterruptedDownloads(error)

    override suspend fun markCacheDeleted(work: DlsiteWork) {
        localStore.markCacheDeleted(work)
    }

    override suspend fun markContentQueued(workId: String, optionIds: List<String>) {
        localStore.markContentQueued(workId, optionIds)
    }

    override suspend fun markContentDownloading(workId: String, optionId: String) {
        localStore.markContentDownloading(workId, optionId)
    }

    override suspend fun markContentDownloaded(
        workId: String,
        optionId: String,
        optionTitle: String?,
        localPath: String,
        trackIds: List<String>,
        trackCount: Int,
    ) {
        localStore.markContentDownloaded(workId, optionId, optionTitle, localPath, trackIds, trackCount)
    }

    override suspend fun markContentFailed(workId: String, optionId: String, error: String?) {
        localStore.markContentFailed(workId, optionId, error)
    }

    override suspend fun markContentPaused(workId: String, optionIds: List<String>) {
        localStore.markContentPaused(workId, optionIds)
    }

    override suspend fun markContentCacheDeleted(workId: String, optionId: String) {
        localStore.markContentCacheDeleted(workId, optionId)
    }

    override suspend fun markAllQueuedDownloadsPaused() {
        downloadQueueRepository.markAllQueuedDownloadsPaused()
    }
}
