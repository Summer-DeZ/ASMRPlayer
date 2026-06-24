package io.github.summerdez.asmrplayer

import io.github.summerdez.asmrplayer.data.update.AppUpdateDownloadState
import io.github.summerdez.asmrplayer.data.update.AppUpdateDownloadStateStore
import io.github.summerdez.asmrplayer.data.update.AppUpdateDownloadStateStatus
import io.github.summerdez.asmrplayer.data.update.AppUpdateRelease
import io.github.summerdez.asmrplayer.presentation.AppUpdateDownloadStatus
import io.github.summerdez.asmrplayer.presentation.appUpdateDownloadUiStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateDownloadStateTest {
    @Test
    fun stateStoreComputesSpeedFromProgressDelta() {
        val release = testRelease()
        val store = AppUpdateDownloadStateStore()

        store.publishDownloading(
            release = release,
            bytesDownloaded = 1_000L,
            totalBytes = 4_000L,
            nowMillis = 1_000L,
        )
        store.publishDownloading(
            release = release,
            bytesDownloaded = 3_000L,
            totalBytes = 4_000L,
            nowMillis = 2_000L,
        )

        val state = store.state.value
        assertTrue(state.active)
        assertEquals(AppUpdateDownloadStateStatus.DOWNLOADING, state.status)
        assertEquals(75, state.progressPercent)
        assertEquals(2_000L, state.speedBytesPerSecond)
    }

    @Test
    fun canceledDownloadKeepsTerminalCanceledState() {
        val release = testRelease()
        val store = AppUpdateDownloadStateStore()

        store.publishDownloading(
            release = release,
            bytesDownloaded = 512L,
            totalBytes = 4_096L,
            nowMillis = 1_000L,
        )
        store.publishCanceled(
            release = release,
            bytesDownloaded = 512L,
            totalBytes = 4_096L,
            nowMillis = 1_100L,
        )

        val state = store.state.value
        assertFalse(state.active)
        assertEquals(AppUpdateDownloadStateStatus.CANCELED, state.status)
        assertEquals(512L, state.bytesDownloaded)
        assertEquals(AppUpdateDownloadStatus.Idle, appUpdateDownloadUiStatus(state))
    }

    @Test
    fun uiStatusMapsStateStoreTerminalAndFailedStates() {
        val release = testRelease()

        val downloaded = appUpdateDownloadUiStatus(
            AppUpdateDownloadState(
                release = release,
                status = AppUpdateDownloadStateStatus.DOWNLOADED,
                bytesDownloaded = 4_096L,
                totalBytes = 4_096L,
                apkPath = "/tmp/ASMRPlayer-1.3.2.apk",
            ),
        )
        assertTrue(downloaded is AppUpdateDownloadStatus.Downloaded)
        assertEquals("/tmp/ASMRPlayer-1.3.2.apk", (downloaded as AppUpdateDownloadStatus.Downloaded).apkPath)

        val failed = appUpdateDownloadUiStatus(
            AppUpdateDownloadState(
                release = release,
                status = AppUpdateDownloadStateStatus.FAILED,
                bytesDownloaded = 1_024L,
                totalBytes = 4_096L,
                speedBytesPerSecond = 2_048L,
                error = "网络错误",
            ),
        )
        assertTrue(failed is AppUpdateDownloadStatus.Failed)
        failed as AppUpdateDownloadStatus.Failed
        assertEquals("网络错误", failed.message)
        assertEquals(1_024L, failed.bytesDownloaded)
        assertEquals(2_048L, failed.speedBytesPerSecond)
    }

    private fun testRelease(): AppUpdateRelease {
        return AppUpdateRelease(
            versionName = "1.3.2",
            tagName = "v1.3.2",
            releaseNotes = "更新下载通知",
            apkName = "ASMRPlayer.apk",
            apkDownloadUrl = "https://example.com/ASMRPlayer.apk",
            apkSizeBytes = 4_096L,
        )
    }
}
