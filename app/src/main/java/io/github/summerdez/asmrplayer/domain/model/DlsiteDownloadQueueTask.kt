package io.github.summerdez.asmrplayer.domain.model

import java.util.UUID

data class DlsiteDownloadQueueTask(
    @JvmField val taskId: String,
    @JvmField val workId: String,
    @JvmField val optionIds: String,
    @JvmField val status: String,
    @JvmField val queueOrder: Long,
    @JvmField val createdAt: Long,
    @JvmField val startedAt: Long? = null,
    @JvmField val updatedAt: Long,
    @JvmField val finishedAt: Long? = null,
    @JvmField val errorMessage: String? = null,
) {
    fun optionIdList(): List<String> = optionIds.split(OPTION_ID_SEPARATOR).filter { it.isNotBlank() }

    fun isPending(): Boolean = status == STATUS_PENDING

    fun isRunning(): Boolean = status == STATUS_RUNNING

    fun isActive(): Boolean = isPending() || isRunning()

    fun asRunning(now: Long): DlsiteDownloadQueueTask {
        return copy(
            status = STATUS_RUNNING,
            startedAt = now,
            updatedAt = now,
            finishedAt = null,
            errorMessage = null,
        )
    }

    fun asPending(now: Long): DlsiteDownloadQueueTask {
        return copy(
            status = STATUS_PENDING,
            startedAt = null,
            updatedAt = now,
            finishedAt = null,
            errorMessage = null,
        )
    }

    fun asFinished(status: String, now: Long, errorMessage: String? = null): DlsiteDownloadQueueTask {
        return copy(
            status = status,
            updatedAt = now,
            finishedAt = now,
            errorMessage = errorMessage,
        )
    }

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_RUNNING = "running"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
        const val STATUS_PAUSED = "paused"
        const val STATUS_CANCELED = "canceled"
        const val OPTION_ID_SEPARATOR = "|"

        fun create(workId: String, optionIds: List<String>, queueOrder: Long, now: Long): DlsiteDownloadQueueTask {
            return DlsiteDownloadQueueTask(
                taskId = UUID.randomUUID().toString(),
                workId = workId,
                optionIds = optionIds.filter { it.isNotBlank() }.joinToString(OPTION_ID_SEPARATOR),
                status = STATUS_PENDING,
                queueOrder = queueOrder,
                createdAt = now,
                updatedAt = now,
            )
        }
    }
}
