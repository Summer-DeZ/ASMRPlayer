package io.github.summerdez.asmrplayer.data

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
import java.io.File
import java.io.IOException
import java.util.LinkedHashMap
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

interface DlsiteApi {
    fun hasLoginCookie(): Boolean
    @Throws(IOException::class)
    fun fetchPurchasedWorks(): List<DlsiteWork>
    @Throws(IOException::class)
    fun fetchDownloadOptions(work: DlsiteWork): List<DlsiteDownloadOption>
    @Throws(IOException::class)
    fun downloadCover(work: DlsiteWork, outputDir: File): File
    @Throws(IOException::class)
    fun downloadWorkFiles(work: DlsiteWork, workDir: File, downloadOptionId: String): List<File>
    @Throws(IOException::class)
    fun downloadWorkFiles(
        work: DlsiteWork,
        workDir: File,
        downloadOptionId: String,
        progressListener: DlsiteContentProgressListener?,
    ): List<File> {
        return downloadWorkFiles(work, workDir, downloadOptionId)
    }
}

interface DlsiteRepository {
    val worksFlow: Flow<List<DlsiteWork>>
    val contentsFlow: Flow<List<DlsiteContent>>
    val lastSyncMsFlow: Flow<Long>
    val downloadStateFlow: StateFlow<DlsiteDownloadState>

    fun hasLoginCookie(): Boolean
    fun fetchPurchasedWorks(): List<DlsiteWork>
    fun fetchDownloadOptions(work: DlsiteWork): List<DlsiteDownloadOption>
    fun downloadCover(work: DlsiteWork, outputDir: File): File
    fun getWorks(): List<DlsiteWork>
    fun getWork(workId: String?): DlsiteWork?
    fun getContents(workId: String?): List<DlsiteContent>
    fun getLastSyncMs(): Long
    fun enqueueDownload(work: DlsiteWork?, optionIds: List<String>, optionTitle: String?): DlsiteDownloadQueueTask?
    fun pendingDownloadQueueTasks(limit: Int): List<DlsiteDownloadQueueTask>
    fun activeDownloadQueueTasks(): List<DlsiteDownloadQueueTask>
    fun resetRunningDownloadQueue(): Int
    fun markDownloadQueueTaskRunning(taskId: String?): DlsiteDownloadQueueTask?
    fun markDownloadQueueTaskCompleted(taskId: String?)
    fun markDownloadQueueTaskFailed(taskId: String?, error: String?)
    fun markDownloadQueueTaskPaused(taskId: String?)
    fun markDownloadQueueTaskCanceled(taskId: String?)
    fun markDownloadQueueTaskPending(taskId: String?)
    fun pauseQueuedDownload(workId: String?): DlsiteDownloadQueueTask?
    fun cancelQueuedDownload(workId: String?): DlsiteDownloadQueueTask?
    fun mergeDiscoveredWorks(discoveredWorks: List<DlsiteWork>): List<DlsiteWork>
    fun saveWork(updatedWork: DlsiteWork?)
    fun saveDownloadOptions(work: DlsiteWork, options: List<DlsiteDownloadOption>): List<DlsiteContent>
    fun markDownloading(work: DlsiteWork)
    fun markDownloading(work: DlsiteWork, optionId: String?, optionTitle: String?)
    fun markQueued(work: DlsiteWork, optionIds: List<String>, optionTitle: String?)
    fun markPaused(work: DlsiteWork)
    fun markDownloaded(work: DlsiteWork, playlistId: String?, localPath: String?, trackCount: Int)
    fun markFailed(work: DlsiteWork, error: String?)
    fun markInterruptedDownloads(error: String?): Int
    fun markCacheDeleted(work: DlsiteWork)
    fun markContentQueued(workId: String?, optionIds: List<String>)
    fun markContentDownloading(workId: String?, optionId: String?)
    fun markContentDownloaded(workId: String?, optionId: String?, localPath: String?, trackIds: List<String>, trackCount: Int)
    fun markContentFailed(workId: String?, optionId: String?, error: String?)
    fun markContentPaused(workId: String?, optionIds: List<String>)
    fun markContentCacheDeleted(workId: String?, optionId: String?)
    fun markAllQueuedDownloadsPaused()
}

class RoomDlsiteRepository(
    private val database: AsrmDatabase,
    private val dlsiteApi: DlsiteApi,
) : DlsiteRepository {
    private val dlsiteDao = database.dlsiteDao()
    private val settingsDao = database.appSettingsDao()

    override val worksFlow: Flow<List<DlsiteWork>> =
        dlsiteDao.workFlow().map { works -> works.map { it.toWork() } }

    override val contentsFlow: Flow<List<DlsiteContent>> =
        dlsiteDao.contentFlow().map { contents -> contents.map { it.toContent() } }

    override val lastSyncMsFlow: Flow<Long> =
        settingsDao.valueFlow(KEY_LAST_SYNC_MS).map { it?.toLongOrNull() ?: 0L }

    override val downloadStateFlow: StateFlow<DlsiteDownloadState> =
        DlsiteDownloadStateBus.state

    override fun hasLoginCookie(): Boolean {
        return dlsiteApi.hasLoginCookie()
    }

    override fun fetchPurchasedWorks(): List<DlsiteWork> {
        return dlsiteApi.fetchPurchasedWorks()
    }

    override fun fetchDownloadOptions(work: DlsiteWork): List<DlsiteDownloadOption> {
        return dlsiteApi.fetchDownloadOptions(work)
    }

    override fun downloadCover(work: DlsiteWork, outputDir: File): File {
        return dlsiteApi.downloadCover(work, outputDir)
    }

    override fun getWorks(): List<DlsiteWork> {
        return DbIo.run {
            dlsiteDao.works().map { it.toWork() }
        }
    }

    override fun getWork(workId: String?): DlsiteWork? {
        if (workId.isNullOrEmpty()) {
            return null
        }
        return DbIo.run {
            dlsiteDao.workById(workId)?.toWork()
        }
    }

    override fun getContents(workId: String?): List<DlsiteContent> {
        if (workId.isNullOrEmpty()) {
            return emptyList()
        }
        return DbIo.run {
            dlsiteDao.contentsForWork(workId).map { it.toContent() }
        }
    }

    override fun getLastSyncMs(): Long {
        return DbIo.run {
            settingsDao.value(KEY_LAST_SYNC_MS)?.toLongOrNull() ?: 0L
        }
    }

    override fun enqueueDownload(
        work: DlsiteWork?,
        optionIds: List<String>,
        optionTitle: String?,
    ): DlsiteDownloadQueueTask? {
        if (work == null || work.workId.isEmpty()) {
            return null
        }
        val sanitizedOptionIds = optionIds.filter { it.isNotBlank() }
        return DbIo.run {
            database.withTransaction {
                dlsiteDao.activeDownloadQueueByWorkId(work.workId)?.toQueueTask()?.let { existing ->
                    return@withTransaction existing
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

    override fun pendingDownloadQueueTasks(limit: Int): List<DlsiteDownloadQueueTask> {
        if (limit <= 0) {
            return emptyList()
        }
        return DbIo.run {
            dlsiteDao.pendingDownloadQueue(limit).map { it.toQueueTask() }
        }
    }

    override fun activeDownloadQueueTasks(): List<DlsiteDownloadQueueTask> {
        return DbIo.run {
            dlsiteDao.activeDownloadQueue().map { it.toQueueTask() }
        }
    }

    override fun resetRunningDownloadQueue(): Int {
        return DbIo.run {
            dlsiteDao.resetRunningDownloadQueue(System.currentTimeMillis())
        }
    }

    override fun markDownloadQueueTaskRunning(taskId: String?): DlsiteDownloadQueueTask? {
        if (taskId.isNullOrEmpty()) {
            return null
        }
        return DbIo.run {
            val now = System.currentTimeMillis()
            val changed = dlsiteDao.markDownloadQueueRunning(taskId, now)
            if (changed == 0) {
                null
            } else {
                dlsiteDao.downloadQueueByTaskId(taskId)?.toQueueTask()
            }
        }
    }

    override fun markDownloadQueueTaskCompleted(taskId: String?) {
        finishDownloadQueueTask(taskId, DlsiteDownloadQueueTask.STATUS_COMPLETED, null)
    }

    override fun markDownloadQueueTaskFailed(taskId: String?, error: String?) {
        finishDownloadQueueTask(taskId, DlsiteDownloadQueueTask.STATUS_FAILED, error)
    }

    override fun markDownloadQueueTaskPaused(taskId: String?) {
        finishDownloadQueueTask(taskId, DlsiteDownloadQueueTask.STATUS_PAUSED, null)
    }

    override fun markDownloadQueueTaskCanceled(taskId: String?) {
        finishDownloadQueueTask(taskId, DlsiteDownloadQueueTask.STATUS_CANCELED, null)
    }

    override fun markDownloadQueueTaskPending(taskId: String?) {
        if (taskId.isNullOrEmpty()) {
            return
        }
        DbIo.run {
            dlsiteDao.markDownloadQueuePending(taskId, System.currentTimeMillis())
        }
    }

    override fun pauseQueuedDownload(workId: String?): DlsiteDownloadQueueTask? {
        return finishActiveQueueTaskForWork(workId, DlsiteDownloadQueueTask.STATUS_PAUSED, null)
    }

    override fun cancelQueuedDownload(workId: String?): DlsiteDownloadQueueTask? {
        return finishActiveQueueTaskForWork(workId, DlsiteDownloadQueueTask.STATUS_CANCELED, null)
    }

    override fun mergeDiscoveredWorks(discoveredWorks: List<DlsiteWork>): List<DlsiteWork> {
        return DbIo.run {
            val byId = LinkedHashMap<String, DlsiteWork>()
            dlsiteDao.works().map { it.toWork() }.forEach { byId[it.workId] = it }
            discoveredWorks.forEach { discovered ->
                val existing = byId[discovered.workId]
                if (existing == null) {
                    byId[discovered.workId] = discovered
                } else {
                    byId[discovered.workId] = existing.mergedWithDiscovery(discovered)
                }
            }
            val merged = byId.values.map { it.withEnsuredCoverUrl() }
            dlsiteDao.upsertAll(merged.map { it.toEntity() })
            settingsDao.put(AppSettingEntity(KEY_LAST_SYNC_MS, System.currentTimeMillis().toString()))
            merged
        }
    }

    override fun saveWork(updatedWork: DlsiteWork?) {
        if (updatedWork == null || updatedWork.workId.isEmpty()) {
            return
        }
        DbIo.run {
            val existing = dlsiteDao.workById(updatedWork.workId)?.toWork()
            val work = updatedWork
                .withEnsuredCoverUrl()
                .withRepositoryUpdatedAt(existing)
            dlsiteDao.upsert(work.toEntity())
        }
    }

    override fun saveDownloadOptions(work: DlsiteWork, options: List<DlsiteDownloadOption>): List<DlsiteContent> {
        if (work.workId.isEmpty()) {
            return emptyList()
        }
        return DbIo.run {
            val existing = dlsiteDao.contentsForWork(work.workId)
                .map { it.toContent() }
                .associateBy { it.optionId }
            val contents = options.map { option ->
                val existingContent = existing[option.id]
                DlsiteContent(
                    workId = work.workId,
                    optionId = option.id,
                    title = option.title.ifEmpty { "默认版本" },
                    status = existingContent?.status ?: DlsiteContent.STATUS_FOUND,
                    localPath = existingContent?.localPath.orEmpty(),
                    trackIds = existingContent?.trackIds.orEmpty(),
                    trackCount = existingContent?.trackCount ?: option.audioFiles.size,
                    error = existingContent?.error.orEmpty(),
                    updatedAt = existingContent?.updatedAt ?: System.currentTimeMillis(),
                )
            }
            dlsiteDao.upsertContents(contents.map { it.toEntity() })
            contents
        }
    }

    override fun markDownloading(work: DlsiteWork) {
        saveWork(work.asDownloading())
    }

    override fun markDownloading(work: DlsiteWork, optionId: String?, optionTitle: String?) {
        markDownloading(work.withDownloadOption(optionId, optionTitle))
    }

    override fun markQueued(work: DlsiteWork, optionIds: List<String>, optionTitle: String?) {
        DbIo.run {
            database.withTransaction {
                markQueuedInCurrentTransaction(work, optionIds, optionTitle)
            }
        }
    }

    override fun markPaused(work: DlsiteWork) {
        saveWork(work.asPaused())
    }

    override fun markDownloaded(work: DlsiteWork, playlistId: String?, localPath: String?, trackCount: Int) {
        saveWork(work.asDownloaded(playlistId, localPath, trackCount))
    }

    override fun markFailed(work: DlsiteWork, error: String?) {
        saveWork(work.asFailed(error))
    }

    override fun markInterruptedDownloads(error: String?): Int {
        return DbIo.run {
            val message = if (error.isNullOrEmpty()) "下载已中断，请重试" else error
            val activeWorkIds = dlsiteDao.activeDownloadQueue().map { it.workId }.toSet()
            var changed = 0
            dlsiteDao.works().map { it.toWork() }.forEach { work ->
                if (work.isDownloading() && !activeWorkIds.contains(work.workId)) {
                    dlsiteDao.upsert(work.asFailed(message).toEntity())
                    changed++
                }
            }
            changed
        }
    }

    override fun markCacheDeleted(work: DlsiteWork) {
        saveWork(work.asCacheDeleted())
        DbIo.run {
            dlsiteDao.contentsForWork(work.workId).forEach { content ->
                dlsiteDao.upsertContent(content.toContent().asCacheDeleted().toEntity())
            }
        }
    }

    override fun markContentQueued(workId: String?, optionIds: List<String>) {
        updateContents(workId, optionIds) { it.asQueued() }
    }

    override fun markContentDownloading(workId: String?, optionId: String?) {
        updateContents(workId, listOf(optionId.orEmpty())) { it.asDownloading() }
        updateWorkFromContents(workId)
    }

    override fun markContentDownloaded(
        workId: String?,
        optionId: String?,
        localPath: String?,
        trackIds: List<String>,
        trackCount: Int,
    ) {
        updateContents(workId, listOf(optionId.orEmpty())) {
            it.asDownloaded(localPath, trackIds, trackCount)
        }
        updateWorkFromContents(workId)
    }

    override fun markContentFailed(workId: String?, optionId: String?, error: String?) {
        updateContents(workId, listOf(optionId.orEmpty())) { it.asFailed(error) }
        updateWorkFromContents(workId)
    }

    override fun markContentPaused(workId: String?, optionIds: List<String>) {
        updateContents(workId, optionIds) { it.asPaused() }
        updateWorkFromContents(workId)
    }

    override fun markContentCacheDeleted(workId: String?, optionId: String?) {
        updateContents(workId, listOf(optionId.orEmpty())) { it.asCacheDeleted() }
        updateWorkFromContents(workId)
    }

    override fun markAllQueuedDownloadsPaused() {
        DbIo.run {
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

    private fun updateContents(
        workId: String?,
        optionIds: List<String>,
        transform: (DlsiteContent) -> DlsiteContent,
    ) {
        if (workId.isNullOrEmpty()) {
            return
        }
        DbIo.run {
            val ids = optionIds.ifEmpty { listOf("") }.toSet()
            dlsiteDao.contentsForWork(workId).map { it.toContent() }.forEach { content ->
                if (ids.contains(content.optionId)) {
                    dlsiteDao.upsertContent(transform(content).toEntity())
                }
            }
        }
    }

    private fun updateWorkFromContents(workId: String?) {
        if (workId.isNullOrEmpty()) {
            return
        }
        DbIo.run {
            val work = dlsiteDao.workById(workId)?.toWork() ?: return@run
            val contents = dlsiteDao.contentsForWork(workId).map { it.toContent() }
            if (contents.isEmpty()) {
                return@run
            }
            val downloaded = contents.filter { it.isDownloaded() }
            val updated = when {
                contents.any { it.isDownloading() } -> work.asDownloading()
                contents.any { it.isQueued() } -> work.asQueued()
                contents.any { it.isFailed() } -> work.asFailed(contents.first { it.isFailed() }.error)
                contents.any { it.isPaused() } -> work.asPaused()
                downloaded.isNotEmpty() -> work.asDownloaded(
                    playlistId = work.playlistId,
                    localPath = work.localPath,
                    trackCount = downloaded.sumOf { it.trackCount },
                )
                else -> work.asCacheDeleted()
            }
            dlsiteDao.upsert(updated.toEntity())
        }
    }

    private fun finishDownloadQueueTask(taskId: String?, status: String, error: String?) {
        if (taskId.isNullOrEmpty()) {
            return
        }
        DbIo.run {
            dlsiteDao.finishDownloadQueue(taskId, status, System.currentTimeMillis(), error)
        }
    }

    private fun finishActiveQueueTaskForWork(
        workId: String?,
        status: String,
        error: String?,
    ): DlsiteDownloadQueueTask? {
        if (workId.isNullOrEmpty()) {
            return null
        }
        return DbIo.run {
            val task = dlsiteDao.activeDownloadQueueByWorkId(workId)?.toQueueTask() ?: return@run null
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
        val queued = work
            .withDownloadOption(optionIds.filter { it.isNotBlank() }.joinToString(OPTION_ID_SEPARATOR), optionTitle)
            .asQueued()
            .withRepositoryUpdatedAt(existing)
        dlsiteDao.upsert(queued.toEntity())
        updateContentsInCurrentTransaction(work.workId, optionIds) { it.asQueued() }
    }

    private suspend fun updateContentsInCurrentTransaction(
        workId: String?,
        optionIds: List<String>,
        transform: (DlsiteContent) -> DlsiteContent,
    ) {
        if (workId.isNullOrEmpty()) {
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
        const val KEY_LAST_SYNC_MS = "dlsite_last_sync_ms"
        const val OPTION_ID_SEPARATOR = "|"
    }
}

internal fun createDlsiteApi(): DlsiteApi {
    return DlsiteClientApi(DlsiteClient())
}

private class DlsiteClientApi(private val client: DlsiteClient) : DlsiteApi {
    override fun hasLoginCookie(): Boolean {
        return client.hasLoginCookie()
    }

    override fun fetchPurchasedWorks(): List<DlsiteWork> {
        return client.fetchPurchasedWorks()
    }

    override fun fetchDownloadOptions(work: DlsiteWork): List<DlsiteDownloadOption> {
        return client.fetchDownloadOptions(work)
    }

    override fun downloadCover(work: DlsiteWork, outputDir: File): File {
        return client.downloadCover(work, outputDir)
    }

    override fun downloadWorkFiles(work: DlsiteWork, workDir: File, downloadOptionId: String): List<File> {
        return client.downloadWorkFiles(work, workDir, downloadOptionId)
    }

    override fun downloadWorkFiles(
        work: DlsiteWork,
        workDir: File,
        downloadOptionId: String,
        progressListener: DlsiteContentProgressListener?,
    ): List<File> {
        return client.downloadWorkFiles(work, workDir, downloadOptionId, progressListener)
    }
}
