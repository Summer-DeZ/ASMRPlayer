package io.github.summerdez.asmrplayer.data.download

import io.github.summerdez.asmrplayer.data.DlsiteDownloadStateStore
import io.github.summerdez.asmrplayer.data.DlsiteDownloadTaskStatus
import io.github.summerdez.asmrplayer.domain.model.DlsiteDownloadOption
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork

internal fun publishQueuePositions(
    repository: DlsiteDownloadBlockingAdapter,
    stateStore: DlsiteDownloadStateStore,
) {
    var index = 1
    for (task in repository.pendingDownloadQueueTasks(Int.MAX_VALUE)) {
        val work = repository.getWork(task.workId)
        val title = work?.displayTitle() ?: task.workId
        stateStore.publishQueued(task.workId, title, index)
        index++
    }
}

internal fun publishDeleteFailed(
    repository: DlsiteDownloadBlockingAdapter,
    stateStore: DlsiteDownloadStateStore,
    work: DlsiteWork,
    taskId: String? = null,
    optionIds: List<String> = emptyList(),
) {
    if (taskId != null) {
        repository.markDownloadQueueTaskFailed(taskId, DLSITE_DELETE_FAILED_MESSAGE)
    }
    if (isContentDownload(optionIds)) {
        for (optionId in optionIds) {
            repository.markContentFailed(work.workId, optionId, DLSITE_DELETE_FAILED_MESSAGE)
        }
    } else {
        repository.markFailed(work, DLSITE_DELETE_FAILED_MESSAGE)
    }
    stateStore.publishFailed(work.workId, work.displayTitle(), DLSITE_DELETE_FAILED_MESSAGE)
}

internal fun publishContentDownloadProgress(
    stateStore: DlsiteDownloadStateStore,
    workId: String,
    title: String,
    option: DlsiteDownloadOption,
    bytesDownloaded: Long,
    totalBytes: Long,
) {
    stateStore.publishTask(
        workId,
        title,
        DlsiteDownloadTaskStatus.DOWNLOADING,
        "下载中",
        0,
        bytesDownloaded,
        totalBytes,
        0L,
        option.id,
        option.title,
    )
}

internal fun isContentDownload(optionIds: List<String>): Boolean {
    return optionIds.isNotEmpty()
}

internal fun shortDownloadError(exception: Exception?): String {
    var message = exception?.message.orEmpty()
    if (message.isEmpty()) {
        message = "下载失败"
    }
    return if (message.length > 42) message.substring(0, 42) + "..." else message
}

private const val DLSITE_DELETE_FAILED_MESSAGE = "删除缓存失败，请重试"
