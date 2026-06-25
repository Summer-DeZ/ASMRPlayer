package io.github.summerdez.asmrplayer.data.download

import io.github.summerdez.asmrplayer.data.DlsiteRepository
import io.github.summerdez.asmrplayer.data.LibraryRepository
import io.github.summerdez.asmrplayer.domain.model.DlsiteDownloadQueueTask
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import io.github.summerdez.asmrplayer.domain.model.Playlist
import io.github.summerdez.asmrplayer.domain.model.TrackItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class DlsiteDownloadBlockingAdapter constructor(
    private val dlsiteRepository: DlsiteRepository,
    private val libraryRepository: LibraryRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    fun getPlaylist(playlistId: String?): Playlist? {
        val normalizedPlaylistId = playlistId?.takeIf { it.isNotEmpty() } ?: return null
        return blocking {
            libraryRepository.getPlaylist(normalizedPlaylistId)
        }
    }

    fun createPlaylist(name: String?): Playlist = blocking {
        libraryRepository.createPlaylist(name.orEmpty())
    }

    fun setPlaylistCover(playlistId: String?, coverUri: String?) {
        val normalizedPlaylistId = playlistId?.takeIf { it.isNotEmpty() } ?: return
        blocking {
            libraryRepository.setPlaylistCover(normalizedPlaylistId, coverUri.orEmpty())
        }
    }

    fun addTrack(playlistId: String?, track: TrackItem?) {
        val normalizedPlaylistId = playlistId?.takeIf { it.isNotEmpty() } ?: return
        val normalizedTrack = track ?: return
        blocking {
            libraryRepository.addTrack(normalizedPlaylistId, normalizedTrack)
        }
    }

    fun getWork(workId: String?): DlsiteWork? {
        val normalizedWorkId = workId?.takeIf { it.isNotEmpty() } ?: return null
        return blocking {
            dlsiteRepository.getWork(normalizedWorkId)
        }
    }

    fun enqueueDownload(
        work: DlsiteWork?,
        optionIds: List<String>,
        optionTitle: String?,
    ): DlsiteDownloadQueueTask? {
        val normalizedWork = work?.takeIf { it.workId.isNotEmpty() } ?: return null
        return blocking {
            dlsiteRepository.enqueueDownload(normalizedWork, optionIds, optionTitle)
        }
    }

    fun pendingDownloadQueueTasks(limit: Int): List<DlsiteDownloadQueueTask> = blocking {
        dlsiteRepository.pendingDownloadQueueTasks(limit)
    }

    fun resetRunningDownloadQueue(): Int = blocking {
        dlsiteRepository.resetRunningDownloadQueue()
    }

    fun markDownloadQueueTaskRunning(taskId: String?): DlsiteDownloadQueueTask? {
        val normalizedTaskId = taskId?.takeIf { it.isNotEmpty() } ?: return null
        return blocking {
            dlsiteRepository.markDownloadQueueTaskRunning(normalizedTaskId)
        }
    }

    fun markDownloadQueueTaskCompleted(taskId: String?) {
        val normalizedTaskId = taskId?.takeIf { it.isNotEmpty() } ?: return
        blocking {
            dlsiteRepository.markDownloadQueueTaskCompleted(normalizedTaskId)
        }
    }

    fun markDownloadQueueTaskFailed(taskId: String?, error: String?) {
        val normalizedTaskId = taskId?.takeIf { it.isNotEmpty() } ?: return
        blocking {
            dlsiteRepository.markDownloadQueueTaskFailed(normalizedTaskId, error)
        }
    }

    fun markDownloadQueueTaskPaused(taskId: String?) {
        val normalizedTaskId = taskId?.takeIf { it.isNotEmpty() } ?: return
        blocking {
            dlsiteRepository.markDownloadQueueTaskPaused(normalizedTaskId)
        }
    }

    fun markDownloadQueueTaskCanceled(taskId: String?) {
        val normalizedTaskId = taskId?.takeIf { it.isNotEmpty() } ?: return
        blocking {
            dlsiteRepository.markDownloadQueueTaskCanceled(normalizedTaskId)
        }
    }

    fun markDownloadQueueTaskPending(taskId: String?) {
        val normalizedTaskId = taskId?.takeIf { it.isNotEmpty() } ?: return
        blocking {
            dlsiteRepository.markDownloadQueueTaskPending(normalizedTaskId)
        }
    }

    fun pauseQueuedDownload(workId: String?): DlsiteDownloadQueueTask? {
        val normalizedWorkId = workId?.takeIf { it.isNotEmpty() } ?: return null
        return blocking {
            dlsiteRepository.pauseQueuedDownload(normalizedWorkId)
        }
    }

    fun cancelQueuedDownload(workId: String?): DlsiteDownloadQueueTask? {
        val normalizedWorkId = workId?.takeIf { it.isNotEmpty() } ?: return null
        return blocking {
            dlsiteRepository.cancelQueuedDownload(normalizedWorkId)
        }
    }

    fun markDownloading(work: DlsiteWork?, optionId: String?, optionTitle: String?) {
        val normalizedWork = work?.takeIf { it.workId.isNotEmpty() } ?: return
        blocking {
            dlsiteRepository.markDownloading(normalizedWork, optionId.orEmpty(), optionTitle)
        }
    }

    fun markQueued(work: DlsiteWork?, optionIds: List<String>, optionTitle: String?) {
        val normalizedWork = work?.takeIf { it.workId.isNotEmpty() } ?: return
        blocking {
            dlsiteRepository.markQueued(normalizedWork, optionIds, optionTitle)
        }
    }

    fun markPaused(work: DlsiteWork?) {
        val normalizedWork = work?.takeIf { it.workId.isNotEmpty() } ?: return
        blocking {
            dlsiteRepository.markPaused(normalizedWork)
        }
    }

    fun markDownloaded(work: DlsiteWork?, playlistId: String?, localPath: String?, trackCount: Int) {
        val normalizedWork = work?.takeIf { it.workId.isNotEmpty() } ?: return
        blocking {
            dlsiteRepository.markDownloaded(normalizedWork, playlistId.orEmpty(), localPath.orEmpty(), trackCount)
        }
    }

    fun markImported(work: DlsiteWork?, playlistId: String?, localPath: String?, trackCount: Int) {
        val normalizedWork = work?.takeIf { it.workId.isNotEmpty() } ?: return
        blocking {
            dlsiteRepository.markImported(normalizedWork, playlistId.orEmpty(), localPath.orEmpty(), trackCount)
        }
    }

    fun markFailed(work: DlsiteWork?, error: String?) {
        val normalizedWork = work?.takeIf { it.workId.isNotEmpty() } ?: return
        blocking {
            dlsiteRepository.markFailed(normalizedWork, error)
        }
    }

    fun markCacheDeleted(work: DlsiteWork?) {
        val normalizedWork = work?.takeIf { it.workId.isNotEmpty() } ?: return
        blocking {
            dlsiteRepository.markCacheDeleted(normalizedWork)
        }
    }

    fun markContentQueued(workId: String?, optionIds: List<String>) {
        val normalizedWorkId = workId?.takeIf { it.isNotEmpty() } ?: return
        blocking {
            dlsiteRepository.markContentQueued(normalizedWorkId, optionIds)
        }
    }

    fun markContentDownloading(workId: String?, optionId: String?) {
        val normalizedWorkId = workId?.takeIf { it.isNotEmpty() } ?: return
        blocking {
            dlsiteRepository.markContentDownloading(normalizedWorkId, optionId.orEmpty())
        }
    }

    fun markContentDownloaded(
        workId: String?,
        optionId: String?,
        optionTitle: String?,
        localPath: String?,
        trackIds: List<String>,
        trackCount: Int,
    ) {
        val normalizedWorkId = workId?.takeIf { it.isNotEmpty() } ?: return
        blocking {
            dlsiteRepository.markContentDownloaded(
                normalizedWorkId,
                optionId.orEmpty(),
                optionTitle,
                localPath.orEmpty(),
                trackIds,
                trackCount,
            )
        }
    }

    fun markContentFailed(workId: String?, optionId: String?, error: String?) {
        val normalizedWorkId = workId?.takeIf { it.isNotEmpty() } ?: return
        blocking {
            dlsiteRepository.markContentFailed(normalizedWorkId, optionId.orEmpty(), error)
        }
    }

    fun markContentPaused(workId: String?, optionIds: List<String>) {
        val normalizedWorkId = workId?.takeIf { it.isNotEmpty() } ?: return
        blocking {
            dlsiteRepository.markContentPaused(normalizedWorkId, optionIds)
        }
    }

    private fun <T> blocking(block: suspend () -> T): T {
        val restoreInterrupt = Thread.interrupted()
        try {
            return runBlocking(dispatcher) {
                block()
            }
        } finally {
            if (restoreInterrupt) {
                Thread.currentThread().interrupt()
            }
        }
    }
}
