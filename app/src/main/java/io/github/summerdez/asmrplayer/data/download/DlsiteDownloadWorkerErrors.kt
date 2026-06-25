package io.github.summerdez.asmrplayer.data.download

import android.util.Log
import io.github.summerdez.asmrplayer.data.DlsiteDownloadStateStore
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import java.io.InterruptedIOException

internal fun Throwable.isDlsiteDownloadInterruption(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is InterruptedException || current is InterruptedIOException) {
            return true
        }
        val next = current.cause
        if (next === current) {
            break
        }
        current = next
    }
    return false
}

internal fun markDlsiteDownloadFailed(
    repository: DlsiteDownloadBlockingAdapter,
    stateStore: DlsiteDownloadStateStore,
    runningWorkers: MutableMap<String, *>,
    request: DlsiteDownloadRequest,
    work: DlsiteWork,
    contentDownload: Boolean,
    exception: Exception,
) {
    val message = shortDownloadError(exception)
    Log.w(TAG, "DLsite download failed work=${work.workId} task=${request.taskId}", exception)
    repository.markDownloadQueueTaskFailed(request.taskId, message)
    if (!contentDownload) {
        repository.markFailed(work, message)
    }
    for (optionId in request.optionIds) {
        repository.markContentFailed(work.workId, optionId, message)
    }
    stateStore.publishFailed(work.workId, work.displayTitle(), message)
    runningWorkers.remove(request.workId)
}

internal inline fun finishDlsiteDownloadWorker(
    restoreInterrupt: Boolean,
    finish: () -> Unit,
) {
    try {
        finish()
    } finally {
        if (restoreInterrupt) {
            Thread.currentThread().interrupt()
        }
    }
}

private const val TAG = "DlsiteDownloadService"
