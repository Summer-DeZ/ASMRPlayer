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
}

interface DlsiteRepository {
    val worksFlow: Flow<List<DlsiteWork>>
    val lastSyncMsFlow: Flow<Long>
    val downloadStateFlow: StateFlow<DlsiteDownloadState>

    fun hasLoginCookie(): Boolean
    fun fetchPurchasedWorks(): List<DlsiteWork>
    fun fetchDownloadOptions(work: DlsiteWork): List<DlsiteDownloadOption>
    fun downloadCover(work: DlsiteWork, outputDir: File): File
    fun getWorks(): List<DlsiteWork>
    fun getWork(workId: String?): DlsiteWork?
    fun getLastSyncMs(): Long
    fun mergeDiscoveredWorks(discoveredWorks: List<DlsiteWork>): List<DlsiteWork>
    fun saveWork(updatedWork: DlsiteWork?)
    fun markDownloading(work: DlsiteWork)
    fun markDownloading(work: DlsiteWork, optionId: String?, optionTitle: String?)
    fun markPaused(work: DlsiteWork)
    fun markDownloaded(work: DlsiteWork, playlistId: String?, localPath: String?, trackCount: Int)
    fun markFailed(work: DlsiteWork, error: String?)
    fun markInterruptedDownloads(error: String?): Int
    fun markCacheDeleted(work: DlsiteWork)
}

class RoomDlsiteRepository(
    private val database: AsrmDatabase,
    private val dlsiteApi: DlsiteApi,
) : DlsiteRepository {
    private val dlsiteDao = database.dlsiteDao()
    private val settingsDao = database.appSettingsDao()

    override val worksFlow: Flow<List<DlsiteWork>> =
        dlsiteDao.workFlow().map { works -> works.map { it.toWork() } }

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

    override fun getLastSyncMs(): Long {
        return DbIo.run {
            settingsDao.value(KEY_LAST_SYNC_MS)?.toLongOrNull() ?: 0L
        }
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
            val work = updatedWork
                .withEnsuredCoverUrl()
                .copy(updatedAt = System.currentTimeMillis())
            dlsiteDao.upsert(work.toEntity())
        }
    }

    override fun markDownloading(work: DlsiteWork) {
        saveWork(work.asDownloading())
    }

    override fun markDownloading(work: DlsiteWork, optionId: String?, optionTitle: String?) {
        markDownloading(work.withDownloadOption(optionId, optionTitle))
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
            var changed = 0
            dlsiteDao.works().map { it.toWork() }.forEach { work ->
                if (work.isDownloading()) {
                    dlsiteDao.upsert(work.asFailed(message).toEntity())
                    changed++
                }
            }
            changed
        }
    }

    override fun markCacheDeleted(work: DlsiteWork) {
        saveWork(work.asCacheDeleted())
    }

    private companion object {
        const val KEY_LAST_SYNC_MS = "dlsite_last_sync_ms"
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
}
