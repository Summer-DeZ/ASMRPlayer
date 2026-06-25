package io.github.summerdez.asmrplayer.data

import androidx.room.withTransaction
import io.github.summerdez.asmrplayer.domain.model.DlsiteContent
import io.github.summerdez.asmrplayer.domain.model.DlsiteDownloadOption
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import java.util.LinkedHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface DlsiteLocalStore {
    val worksFlow: Flow<List<DlsiteWork>>
    val contentsFlow: Flow<List<DlsiteContent>>
    val lastSyncMsFlow: Flow<Long>

    suspend fun getWork(workId: String): DlsiteWork?
    suspend fun getContents(workId: String): List<DlsiteContent>
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
}

class RoomDlsiteLocalStore(
    private val database: AsrmDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DlsiteLocalStore {
    private val dlsiteDao = database.dlsiteDao()
    private val settingsDao = database.appSettingsDao()

    override val worksFlow: Flow<List<DlsiteWork>> =
        dlsiteDao.workFlow().map { works -> works.map { it.toWork() } }

    override val contentsFlow: Flow<List<DlsiteContent>> =
        dlsiteDao.contentFlow().map { contents -> contents.map { it.toContent() } }

    override val lastSyncMsFlow: Flow<Long> =
        settingsDao.valueFlow(KEY_LAST_SYNC_MS).map { it?.toLongOrNull() ?: 0L }

    override suspend fun getWork(workId: String): DlsiteWork? {
        if (workId.isEmpty()) {
            return null
        }
        return withContext(ioDispatcher) {
            dlsiteDao.workById(workId)?.toWork()
        }
    }

    override suspend fun getContents(workId: String): List<DlsiteContent> {
        if (workId.isEmpty()) {
            return emptyList()
        }
        return withContext(ioDispatcher) {
            dlsiteDao.contentsForWork(workId).map { it.toContent() }
        }
    }

    override suspend fun mergeDiscoveredWorks(discoveredWorks: List<DlsiteWork>): List<DlsiteWork> {
        return withContext(ioDispatcher) {
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

    override suspend fun saveWork(updatedWork: DlsiteWork) {
        if (updatedWork.workId.isEmpty()) {
            return
        }
        withContext(ioDispatcher) {
            saveWorkInCurrentContext(updatedWork)
        }
    }

    private suspend fun saveWorkInCurrentContext(updatedWork: DlsiteWork) {
        val existing = dlsiteDao.workById(updatedWork.workId)?.toWork()
        val work = updatedWork
            .withEnsuredCoverUrl()
            .withRepositoryUpdatedAt(existing)
        dlsiteDao.upsert(work.toEntity())
    }

    override suspend fun saveDownloadOptions(work: DlsiteWork, options: List<DlsiteDownloadOption>): List<DlsiteContent> {
        if (work.workId.isEmpty()) {
            return emptyList()
        }
        return withContext(ioDispatcher) {
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

    override suspend fun markDownloading(work: DlsiteWork) {
        saveWork(work.asDownloading())
    }

    override suspend fun markDownloading(work: DlsiteWork, optionId: String, optionTitle: String?) {
        markDownloading(work.withDownloadOption(optionId, optionTitle))
    }

    override suspend fun markQueued(work: DlsiteWork, optionIds: List<String>, optionTitle: String?) {
        withContext(ioDispatcher) {
            database.withTransaction {
                markQueuedInCurrentTransaction(work, optionIds, optionTitle)
            }
        }
    }

    override suspend fun markPaused(work: DlsiteWork) {
        saveWork(work.asPaused())
    }

    override suspend fun markDownloaded(work: DlsiteWork, playlistId: String, localPath: String, trackCount: Int) {
        saveWork(work.asDownloaded(playlistId, localPath, trackCount))
    }

    override suspend fun markImported(work: DlsiteWork, playlistId: String, localPath: String, trackCount: Int) {
        val updated = work.copy(
            status = DlsiteWork.STATUS_FOUND,
            playlistId = playlistId,
            localPath = localPath,
            trackCount = trackCount,
            error = "",
            downloadOptionId = "",
            downloadOptionTitle = "",
            updatedAt = System.currentTimeMillis(),
        )
        saveWork(updated)
    }

    override suspend fun markFailed(work: DlsiteWork, error: String?) {
        saveWork(work.asFailed(error))
    }

    override suspend fun markInterruptedDownloads(error: String?): Int {
        return withContext(ioDispatcher) {
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

    override suspend fun markCacheDeleted(work: DlsiteWork) {
        withContext(ioDispatcher) {
            saveWorkInCurrentContext(work.asCacheDeleted())
            dlsiteDao.contentsForWork(work.workId).forEach { content ->
                dlsiteDao.upsertContent(content.toContent().asCacheDeleted().toEntity())
            }
        }
    }

    override suspend fun markContentQueued(workId: String, optionIds: List<String>) {
        updateContents(workId, optionIds) { it.asQueued() }
    }

    override suspend fun markContentDownloading(workId: String, optionId: String) {
        updateContents(workId, listOf(optionId)) { it.asDownloading() }
    }

    override suspend fun markContentDownloaded(
        workId: String,
        optionId: String,
        optionTitle: String?,
        localPath: String,
        trackIds: List<String>,
        trackCount: Int,
    ) {
        updateContents(workId, listOf(optionId), defaultTitle = optionTitle) {
            it.asDownloaded(localPath, trackIds, trackCount)
        }
    }

    override suspend fun markContentFailed(workId: String, optionId: String, error: String?) {
        updateContents(workId, listOf(optionId)) { it.asFailed(error) }
    }

    override suspend fun markContentPaused(workId: String, optionIds: List<String>) {
        updateContents(workId, optionIds) { it.asPaused() }
    }

    override suspend fun markContentCacheDeleted(workId: String, optionId: String) {
        updateContents(workId, listOf(optionId)) { it.asCacheDeleted() }
    }

    private suspend fun updateContents(
        workId: String,
        optionIds: List<String>,
        defaultTitle: String? = null,
        transform: (DlsiteContent) -> DlsiteContent,
    ) {
        if (workId.isEmpty()) {
            return
        }
        withContext(ioDispatcher) {
            val ids = optionIds.ifEmpty { listOf("") }.toSet()
            val matchedIds = mutableSetOf<String>()
            dlsiteDao.contentsForWork(workId).map { it.toContent() }.forEach { content ->
                if (ids.contains(content.optionId)) {
                    matchedIds += content.optionId
                    dlsiteDao.upsertContent(transform(content).toEntity())
                }
            }
            ids.filter { it.isNotEmpty() && it !in matchedIds }.forEach { optionId ->
                val title = defaultTitle?.takeIf { ids.size == 1 && it.isNotBlank() }
                    ?: optionId.ifEmpty { "默认版本" }
                dlsiteDao.upsertContent(
                    transform(
                        DlsiteContent(
                            workId = workId,
                            optionId = optionId,
                            title = title,
                        ),
                    ).toEntity(),
                )
            }
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
                if (sanitizedOptionIds.isEmpty()) nextWork.asQueued() else nextWork.copy(status = DlsiteWork.STATUS_FOUND, error = "")
            }
            .withRepositoryUpdatedAt(existing)
            .let { nextWork ->
                if (sanitizedOptionIds.isEmpty()) nextWork else nextWork.copy(updatedAt = existing?.updatedAt ?: nextWork.updatedAt)
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
        const val KEY_LAST_SYNC_MS = "dlsite_last_sync_ms"
        const val OPTION_ID_SEPARATOR = "|"
    }
}
