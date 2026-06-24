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

class DlsiteDownloadBlockingAdapter @JvmOverloads constructor(
    private val dlsiteRepository: DlsiteRepository,
    private val libraryRepository: LibraryRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    fun getPlaylist(playlistId: String?): Playlist? = blocking {
        libraryRepository.getPlaylist(playlistId)
    }

    fun createPlaylist(name: String?): Playlist = blocking {
        libraryRepository.createPlaylist(name)
    }

    fun setPlaylistCover(playlistId: String?, coverUri: String?) {
        blocking {
            libraryRepository.setPlaylistCover(playlistId, coverUri)
        }
    }

    fun addTrack(playlistId: String?, track: TrackItem?) {
        blocking {
            libraryRepository.addTrack(playlistId, track)
        }
    }

    fun getWork(workId: String?): DlsiteWork? = blocking {
        dlsiteRepository.getWork(workId)
    }

    fun enqueueDownload(
        work: DlsiteWork?,
        optionIds: List<String>,
        optionTitle: String?,
    ): DlsiteDownloadQueueTask? = blocking {
        dlsiteRepository.enqueueDownload(work, optionIds, optionTitle)
    }

    fun pendingDownloadQueueTasks(limit: Int): List<DlsiteDownloadQueueTask> = blocking {
        dlsiteRepository.pendingDownloadQueueTasks(limit)
    }

    fun resetRunningDownloadQueue(): Int = blocking {
        dlsiteRepository.resetRunningDownloadQueue()
    }

    fun markDownloadQueueTaskRunning(taskId: String?): DlsiteDownloadQueueTask? = blocking {
        dlsiteRepository.markDownloadQueueTaskRunning(taskId)
    }

    fun markDownloadQueueTaskCompleted(taskId: String?) {
        blocking {
            dlsiteRepository.markDownloadQueueTaskCompleted(taskId)
        }
    }

    fun markDownloadQueueTaskFailed(taskId: String?, error: String?) {
        blocking {
            dlsiteRepository.markDownloadQueueTaskFailed(taskId, error)
        }
    }

    fun markDownloadQueueTaskPaused(taskId: String?) {
        blocking {
            dlsiteRepository.markDownloadQueueTaskPaused(taskId)
        }
    }

    fun markDownloadQueueTaskCanceled(taskId: String?) {
        blocking {
            dlsiteRepository.markDownloadQueueTaskCanceled(taskId)
        }
    }

    fun markDownloadQueueTaskPending(taskId: String?) {
        blocking {
            dlsiteRepository.markDownloadQueueTaskPending(taskId)
        }
    }

    fun pauseQueuedDownload(workId: String?): DlsiteDownloadQueueTask? = blocking {
        dlsiteRepository.pauseQueuedDownload(workId)
    }

    fun cancelQueuedDownload(workId: String?): DlsiteDownloadQueueTask? = blocking {
        dlsiteRepository.cancelQueuedDownload(workId)
    }

    fun markDownloading(work: DlsiteWork, optionId: String?, optionTitle: String?) {
        blocking {
            dlsiteRepository.markDownloading(work, optionId, optionTitle)
        }
    }

    fun markQueued(work: DlsiteWork, optionIds: List<String>, optionTitle: String?) {
        blocking {
            dlsiteRepository.markQueued(work, optionIds, optionTitle)
        }
    }

    fun markPaused(work: DlsiteWork) {
        blocking {
            dlsiteRepository.markPaused(work)
        }
    }

    fun markDownloaded(work: DlsiteWork, playlistId: String?, localPath: String?, trackCount: Int) {
        blocking {
            dlsiteRepository.markDownloaded(work, playlistId, localPath, trackCount)
        }
    }

    fun markImported(work: DlsiteWork, playlistId: String?, localPath: String?, trackCount: Int) {
        blocking {
            dlsiteRepository.markImported(work, playlistId, localPath, trackCount)
        }
    }

    fun markFailed(work: DlsiteWork, error: String?) {
        blocking {
            dlsiteRepository.markFailed(work, error)
        }
    }

    fun markCacheDeleted(work: DlsiteWork) {
        blocking {
            dlsiteRepository.markCacheDeleted(work)
        }
    }

    fun markContentQueued(workId: String?, optionIds: List<String>) {
        blocking {
            dlsiteRepository.markContentQueued(workId, optionIds)
        }
    }

    fun markContentDownloading(workId: String?, optionId: String?) {
        blocking {
            dlsiteRepository.markContentDownloading(workId, optionId)
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
        blocking {
            dlsiteRepository.markContentDownloaded(workId, optionId, optionTitle, localPath, trackIds, trackCount)
        }
    }

    fun markContentFailed(workId: String?, optionId: String?, error: String?) {
        blocking {
            dlsiteRepository.markContentFailed(workId, optionId, error)
        }
    }

    fun markContentPaused(workId: String?, optionIds: List<String>) {
        blocking {
            dlsiteRepository.markContentPaused(workId, optionIds)
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
