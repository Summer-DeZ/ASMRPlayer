package io.github.summerdez.asmrplayer.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class DlsiteDownloadTaskStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    FAILED,
    COMPLETED,
}

data class DlsiteDownloadTaskState(
    val workId: String = "",
    val title: String = "",
    val status: DlsiteDownloadTaskStatus = DlsiteDownloadTaskStatus.QUEUED,
    val statusMessage: String = "",
    val queuePosition: Int = 0,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val speedBytesPerSecond: Long = 0L,
    val contentId: String = "",
    val contentTitle: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val active: Boolean
        get() = status == DlsiteDownloadTaskStatus.QUEUED || status == DlsiteDownloadTaskStatus.DOWNLOADING

    val progressPercent: Int?
        get() = if (totalBytes > 0L) {
            ((bytesDownloaded.coerceAtLeast(0L) * 100L) / totalBytes).coerceIn(0L, 100L).toInt()
        } else {
            null
        }

    val statusText: String
        get() {
            return when (status) {
                DlsiteDownloadTaskStatus.QUEUED ->
                    if (queuePosition > 0) "排队中 · 第 $queuePosition 位" else "排队中"
                DlsiteDownloadTaskStatus.DOWNLOADING -> {
                    val percent = progressPercent
                    val base = statusMessage.ifEmpty { "下载中" }
                    if (percent == null) base else "$base $percent%"
                }
                DlsiteDownloadTaskStatus.PAUSED -> "已暂停"
                DlsiteDownloadTaskStatus.FAILED -> statusMessage.ifEmpty { "下载失败" }
                DlsiteDownloadTaskStatus.COMPLETED -> "已完成"
            }
        }
}

data class DlsiteDownloadSummary(
    val visible: Boolean = false,
    val runningCount: Int = 0,
    val totalCount: Int = 0,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSecond: Long = 0L,
) {
    val progressPercent: Int?
        get() = if (totalBytes > 0L) {
            ((bytesDownloaded.coerceAtLeast(0L) * 100L) / totalBytes).coerceIn(0L, 100L).toInt()
        } else {
            null
        }
}

data class DlsiteDownloadState(
    val active: Boolean = false,
    val workId: String = "",
    val title: String = "",
    val status: String = "",
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val tasks: Map<String, DlsiteDownloadTaskState> = emptyMap(),
    val summary: DlsiteDownloadSummary = DlsiteDownloadSummary(),
) {
    val progressPercent: Int?
        get() = if (totalBytes > 0L) {
            ((bytesDownloaded.coerceAtLeast(0L) * 100L) / totalBytes).coerceIn(0L, 100L).toInt()
        } else {
            null
        }

    val statusText: String
        get() {
            val percent = progressPercent
            return if (percent == null) status else "$status $percent%"
        }
}

object DlsiteDownloadStateBus {
    private val _state = MutableStateFlow(DlsiteDownloadState())
    val state: StateFlow<DlsiteDownloadState> = _state.asStateFlow()

    @JvmStatic
    fun publish(workId: String?, title: String?, status: String?) {
        publishProgress(workId, title, status, 0L, -1L)
    }

    @JvmStatic
    fun publishProgress(
        workId: String?,
        title: String?,
        status: String?,
        bytesDownloaded: Long,
        totalBytes: Long,
    ) {
        publishTask(
            workId = workId,
            title = title,
            taskStatus = DlsiteDownloadTaskStatus.DOWNLOADING,
            statusMessage = status,
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
        )
    }

    @JvmStatic
    fun clear() {
        _state.value = DlsiteDownloadState()
    }

    @JvmStatic
    fun clearCompleted() {
        _state.update { current ->
            rebuild(current.tasks.filterValues { it.status != DlsiteDownloadTaskStatus.COMPLETED })
        }
    }

    @JvmStatic
    fun remove(workId: String?) {
        if (workId.isNullOrEmpty()) {
            return
        }
        _state.update { current ->
            rebuild(current.tasks - workId)
        }
    }

    @JvmStatic
    fun publishQueued(workId: String?, title: String?, queuePosition: Int) {
        publishTask(
            workId = workId,
            title = title,
            taskStatus = DlsiteDownloadTaskStatus.QUEUED,
            queuePosition = queuePosition,
        )
    }

    @JvmStatic
    fun publishPaused(workId: String?, title: String?) {
        publishTask(workId, title, DlsiteDownloadTaskStatus.PAUSED, "已暂停")
    }

    @JvmStatic
    fun publishFailed(workId: String?, title: String?, message: String?) {
        publishTask(workId, title, DlsiteDownloadTaskStatus.FAILED, message)
    }

    @JvmStatic
    fun publishCompleted(workId: String?, title: String?) {
        publishTask(workId, title, DlsiteDownloadTaskStatus.COMPLETED, "已完成")
    }

    @JvmStatic
    fun snapshot(): DlsiteDownloadState = _state.value

    @JvmStatic
    fun taskFor(workId: String?): DlsiteDownloadTaskState? {
        return if (workId.isNullOrEmpty()) null else _state.value.tasks[workId]
    }

    @JvmStatic
    @JvmOverloads
    fun publishTask(
        workId: String?,
        title: String?,
        taskStatus: DlsiteDownloadTaskStatus,
        statusMessage: String? = "",
        queuePosition: Int = 0,
        bytesDownloaded: Long = 0L,
        totalBytes: Long = -1L,
        speedBytesPerSecond: Long = 0L,
        contentId: String? = "",
        contentTitle: String? = "",
    ) {
        val id = workId.orEmpty()
        if (id.isEmpty()) {
            return
        }
        _state.update { current ->
            val previous = current.tasks[id]
            val now = System.currentTimeMillis()
            val computedSpeed = if (speedBytesPerSecond > 0L) {
                speedBytesPerSecond
            } else if (
                previous != null &&
                previous.status == DlsiteDownloadTaskStatus.DOWNLOADING &&
                taskStatus == DlsiteDownloadTaskStatus.DOWNLOADING &&
                bytesDownloaded >= previous.bytesDownloaded &&
                now > previous.updatedAt
            ) {
                ((bytesDownloaded - previous.bytesDownloaded) * 1000L) / (now - previous.updatedAt)
            } else {
                0L
            }
            val task = DlsiteDownloadTaskState(
                workId = id,
                title = title.orEmpty().ifEmpty { previous?.title.orEmpty() },
                status = taskStatus,
                statusMessage = statusMessage.orEmpty(),
                queuePosition = queuePosition,
                bytesDownloaded = bytesDownloaded.coerceAtLeast(0L),
                totalBytes = totalBytes,
                speedBytesPerSecond = computedSpeed.coerceAtLeast(0L),
                contentId = contentId.orEmpty(),
                contentTitle = contentTitle.orEmpty(),
                updatedAt = now,
            )
            rebuild(current.tasks + (id to task))
        }
    }

    private fun rebuild(tasks: Map<String, DlsiteDownloadTaskState>): DlsiteDownloadState {
        val first = tasks.values.firstOrNull { it.active } ?: tasks.values.firstOrNull()
        val visibleTasks = tasks.values.filter { it.status != DlsiteDownloadTaskStatus.COMPLETED }
        val knownTasks = visibleTasks.filter { it.totalBytes > 0L }
        val summary = DlsiteDownloadSummary(
            visible = visibleTasks.isNotEmpty(),
            runningCount = visibleTasks.count { it.status == DlsiteDownloadTaskStatus.DOWNLOADING },
            totalCount = visibleTasks.size,
            bytesDownloaded = knownTasks.sumOf { it.bytesDownloaded.coerceAtMost(it.totalBytes).coerceAtLeast(0L) },
            totalBytes = knownTasks.sumOf { it.totalBytes.coerceAtLeast(0L) },
            speedBytesPerSecond = visibleTasks.sumOf { it.speedBytesPerSecond.coerceAtLeast(0L) },
        )
        return DlsiteDownloadState(
            active = visibleTasks.any { it.active },
            workId = first?.workId.orEmpty(),
            title = first?.title.orEmpty(),
            status = first?.statusMessage?.ifEmpty { first.statusText }.orEmpty(),
            bytesDownloaded = first?.bytesDownloaded ?: 0L,
            totalBytes = first?.totalBytes ?: -1L,
            tasks = tasks,
            summary = summary,
        )
    }
}
