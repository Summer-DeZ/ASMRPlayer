package io.github.summerdez.asmrplayer.domain.model

data class DlsiteContent(
    val workId: String,
    val optionId: String,
    val title: String,
    val status: String = STATUS_FOUND,
    val localPath: String = "",
    val trackIds: String = "",
    val trackCount: Int = 0,
    val error: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun isDownloaded(): Boolean = status == STATUS_DOWNLOADED

    fun isDownloading(): Boolean = status == STATUS_DOWNLOADING

    fun isQueued(): Boolean = status == STATUS_QUEUED

    fun isPaused(): Boolean = status == STATUS_PAUSED

    fun isFailed(): Boolean = status == STATUS_FAILED

    fun trackIdList(): List<String> = trackIds.split('|').filter { it.isNotBlank() }

    fun asQueued(): DlsiteContent = copy(status = STATUS_QUEUED, error = "", updatedAt = System.currentTimeMillis())

    fun asDownloading(): DlsiteContent = copy(status = STATUS_DOWNLOADING, error = "", updatedAt = System.currentTimeMillis())

    fun asPaused(): DlsiteContent = copy(status = STATUS_PAUSED, error = "", updatedAt = System.currentTimeMillis())

    fun asDownloaded(localPath: String?, trackIds: List<String>, trackCount: Int): DlsiteContent {
        return copy(
            status = STATUS_DOWNLOADED,
            localPath = localPath.orEmpty(),
            trackIds = trackIds.joinToString("|"),
            trackCount = trackCount,
            error = "",
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun asFailed(message: String?): DlsiteContent {
        return copy(
            status = STATUS_FAILED,
            error = message.orEmpty().ifEmpty { "下载失败" },
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun asCacheDeleted(): DlsiteContent {
        return copy(
            status = STATUS_FOUND,
            localPath = "",
            trackIds = "",
            trackCount = 0,
            error = "",
            updatedAt = System.currentTimeMillis(),
        )
    }

    companion object {
        const val STATUS_FOUND = "found"
        const val STATUS_QUEUED = "queued"
        const val STATUS_DOWNLOADING = "downloading"
        const val STATUS_PAUSED = "paused"
        const val STATUS_DOWNLOADED = "downloaded"
        const val STATUS_FAILED = "failed"
    }
}
