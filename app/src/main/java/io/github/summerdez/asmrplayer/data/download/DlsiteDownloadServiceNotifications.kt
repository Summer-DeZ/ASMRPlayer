package io.github.summerdez.asmrplayer.data.download

import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import io.github.summerdez.asmrplayer.data.DlsiteDownloadStateStore

internal fun Service.stopDlsiteDownloadForegroundSafely() {
    try {
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
    } catch (exception: RuntimeException) {
        Log.d(DLSITE_DOWNLOAD_NOTIFICATION_TAG, "Failed to stop DLsite download foreground notification", exception)
    }
}

internal fun Service.promoteDlsiteDownloadToForeground(stateStore: DlsiteDownloadStateStore) {
    DlsiteDownloadNotifications.ensureChannel(this)
    val notification = DlsiteDownloadNotifications.buildSummary(this, stateStore.snapshot())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(
            DlsiteDownloadNotifications.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    } else {
        startForeground(DlsiteDownloadNotifications.NOTIFICATION_ID, notification)
    }
}

internal fun Service.updateDlsiteDownloadNotification(stateStore: DlsiteDownloadStateStore) {
    DlsiteDownloadNotifications.updateSummary(this, stateStore.snapshot())
}

private const val DLSITE_DOWNLOAD_NOTIFICATION_TAG = "DlsiteDownloadService"
