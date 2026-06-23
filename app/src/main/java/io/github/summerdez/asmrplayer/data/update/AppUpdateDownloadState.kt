package io.github.summerdez.asmrplayer.data.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppUpdateDownloadStateStatus {
    IDLE,
    DOWNLOADING,
    DOWNLOADED,
    FAILED,
    CANCELED,
}

data class AppUpdateDownloadState(
    val release: AppUpdateRelease? = null,
    val versionName: String = release?.versionName.orEmpty(),
    val status: AppUpdateDownloadStateStatus = AppUpdateDownloadStateStatus.IDLE,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSecond: Long = 0L,
    val apkPath: String = "",
    val error: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val active: Boolean
        get() = status == AppUpdateDownloadStateStatus.DOWNLOADING

    val progressPercent: Int?
        get() = if (totalBytes > 0L) {
            ((bytesDownloaded.coerceAtLeast(0L) * 100L) / totalBytes).coerceIn(0L, 100L).toInt()
        } else {
            null
        }
}

object AppUpdateDownloadStateBus {
    private val _state = MutableStateFlow(AppUpdateDownloadState())
    val state: StateFlow<AppUpdateDownloadState> = _state.asStateFlow()

    fun publishDownloading(
        release: AppUpdateRelease,
        bytesDownloaded: Long,
        totalBytes: Long,
        speedBytesPerSecond: Long = 0L,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val previous = _state.value
        val safeBytes = bytesDownloaded.coerceAtLeast(0L)
        val computedSpeed = if (speedBytesPerSecond > 0L) {
            speedBytesPerSecond
        } else if (
            previous.status == AppUpdateDownloadStateStatus.DOWNLOADING &&
            previous.versionName == release.versionName &&
            safeBytes >= previous.bytesDownloaded &&
            nowMillis > previous.updatedAt
        ) {
            ((safeBytes - previous.bytesDownloaded) * 1000L) / (nowMillis - previous.updatedAt)
        } else {
            0L
        }
        _state.value = AppUpdateDownloadState(
            release = release,
            versionName = release.versionName,
            status = AppUpdateDownloadStateStatus.DOWNLOADING,
            bytesDownloaded = safeBytes,
            totalBytes = totalBytes.coerceAtLeast(0L),
            speedBytesPerSecond = computedSpeed.coerceAtLeast(0L),
            updatedAt = nowMillis,
        )
    }

    fun publishDownloaded(
        release: AppUpdateRelease,
        apkPath: String,
        totalBytes: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        _state.value = AppUpdateDownloadState(
            release = release,
            versionName = release.versionName,
            status = AppUpdateDownloadStateStatus.DOWNLOADED,
            bytesDownloaded = totalBytes.coerceAtLeast(0L),
            totalBytes = totalBytes.coerceAtLeast(0L),
            apkPath = apkPath,
            updatedAt = nowMillis,
        )
    }

    fun publishFailed(
        release: AppUpdateRelease,
        message: String,
        bytesDownloaded: Long = _state.value.bytesDownloaded,
        totalBytes: Long = _state.value.totalBytes.takeIf { it > 0L } ?: release.apkSizeBytes,
        speedBytesPerSecond: Long = _state.value.speedBytesPerSecond,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        _state.value = AppUpdateDownloadState(
            release = release,
            versionName = release.versionName,
            status = AppUpdateDownloadStateStatus.FAILED,
            bytesDownloaded = bytesDownloaded.coerceAtLeast(0L),
            totalBytes = totalBytes.coerceAtLeast(0L),
            speedBytesPerSecond = speedBytesPerSecond.coerceAtLeast(0L),
            error = message.ifEmpty { "下载更新失败" },
            updatedAt = nowMillis,
        )
    }

    fun publishCanceled(
        release: AppUpdateRelease? = _state.value.release,
        bytesDownloaded: Long = _state.value.bytesDownloaded,
        totalBytes: Long = _state.value.totalBytes,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        _state.value = AppUpdateDownloadState(
            release = release,
            versionName = release?.versionName.orEmpty(),
            status = AppUpdateDownloadStateStatus.CANCELED,
            bytesDownloaded = bytesDownloaded.coerceAtLeast(0L),
            totalBytes = totalBytes.coerceAtLeast(0L),
            updatedAt = nowMillis,
        )
    }

    fun clear() {
        _state.value = AppUpdateDownloadState()
    }
}
