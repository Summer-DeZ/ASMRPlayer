package io.github.summerdez.asmrplayer.data.update

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import io.github.summerdez.asmrplayer.di.AppGraph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AppUpdateDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private var lastNotificationAt = 0L
    private var lastNotificationProgress: Int? = null

    override fun onCreate() {
        super.onCreate()
        AppUpdateNotifications.ensureChannel(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DOWNLOAD -> startDownload(intent, startId)
            ACTION_CANCEL -> cancelDownload(startId)
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startDownload(intent: Intent, startId: Int) {
        val release = intent.release() ?: run {
            stopSelf(startId)
            return
        }
        if (downloadJob?.isActive == true) {
            return
        }
        lastNotificationAt = 0L
        lastNotificationProgress = null
        AppUpdateDownloadStateBus.publishDownloading(
            release = release,
            bytesDownloaded = 0L,
            totalBytes = release.apkSizeBytes,
        )
        promoteToForeground()
        downloadJob = scope.launch {
            try {
                val repository = AppGraph.container(this@AppUpdateDownloadService).updateRepository
                val apkFile = repository.downloadReleaseApk(release) { progress ->
                    AppUpdateDownloadStateBus.publishDownloading(
                        release = release,
                        bytesDownloaded = progress.bytesDownloaded,
                        totalBytes = progress.totalBytes,
                        speedBytesPerSecond = progress.speedBytesPerSecond,
                    )
                    updateNotification()
                }
                val totalBytes = apkFile.length().takeIf { it > 0L } ?: release.apkSizeBytes
                AppUpdateDownloadStateBus.publishDownloaded(
                    release = release,
                    apkPath = apkFile.absolutePath,
                    totalBytes = totalBytes,
                )
                finishWithTerminalNotification()
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    AppUpdateDownloadStateBus.publishCanceled(release)
                    removeNotification()
                    stopSelf(startId)
                    return@launch
                }
                val current = AppUpdateDownloadStateBus.state.value
                AppUpdateDownloadStateBus.publishFailed(
                    release = release,
                    message = error.message ?: "下载更新失败",
                    bytesDownloaded = current.bytesDownloaded,
                    totalBytes = current.totalBytes.takeIf { it > 0L } ?: release.apkSizeBytes,
                    speedBytesPerSecond = current.speedBytesPerSecond,
                )
                finishWithTerminalNotification()
            } finally {
                downloadJob = null
            }
        }
    }

    private fun cancelDownload(startId: Int) {
        val activeJob = downloadJob
        if (activeJob?.isActive == true) {
            activeJob.cancel()
            return
        }
        AppUpdateDownloadStateBus.publishCanceled()
        removeNotification()
        stopSelf(startId)
    }

    private fun promoteToForeground() {
        val notification = AppUpdateNotifications.buildDownloading(this, AppUpdateDownloadStateBus.state.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                AppUpdateNotifications.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(AppUpdateNotifications.NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val state = AppUpdateDownloadStateBus.state.value
        if (shouldSkipNotification(state)) {
            return
        }
        lastNotificationAt = SystemClock.elapsedRealtime()
        lastNotificationProgress = state.progressPercent
        AppUpdateNotifications.notifyDownloading(this, state)
    }

    private fun shouldSkipNotification(state: AppUpdateDownloadState): Boolean {
        val now = SystemClock.elapsedRealtime()
        val progressChanged = lastNotificationProgress != state.progressPercent
        if (progressChanged) {
            return false
        }
        return now - lastNotificationAt < NOTIFICATION_MIN_INTERVAL_MS
    }

    private fun finishWithTerminalNotification() {
        AppUpdateNotifications.notifyTerminal(this, AppUpdateDownloadStateBus.state.value)
        stopForegroundSafely(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun removeNotification() {
        stopForegroundSafely(STOP_FOREGROUND_REMOVE)
        AppUpdateNotifications.cancel(this)
    }

    private fun stopForegroundSafely(flags: Int) {
        try {
            stopForeground(flags)
        } catch (_: RuntimeException) {
        }
    }

    private fun Intent.release(): AppUpdateRelease? {
        val versionName = getStringExtra(EXTRA_VERSION_NAME).orEmpty()
        val tagName = getStringExtra(EXTRA_TAG_NAME).orEmpty()
        val apkDownloadUrl = getStringExtra(EXTRA_APK_DOWNLOAD_URL).orEmpty()
        if (versionName.isBlank() || apkDownloadUrl.isBlank()) {
            return null
        }
        return AppUpdateRelease(
            versionName = versionName,
            tagName = tagName.ifBlank { versionName },
            releaseNotes = getStringExtra(EXTRA_RELEASE_NOTES).orEmpty(),
            apkName = getStringExtra(EXTRA_APK_NAME).orEmpty(),
            apkDownloadUrl = apkDownloadUrl,
            apkSizeBytes = getLongExtra(EXTRA_APK_SIZE_BYTES, 0L).coerceAtLeast(0L),
        )
    }

    companion object {
        private const val ACTION_DOWNLOAD = "io.github.summerdez.asmrplayer.action.APP_UPDATE_DOWNLOAD"
        private const val ACTION_CANCEL = "io.github.summerdez.asmrplayer.action.APP_UPDATE_CANCEL"
        private const val EXTRA_VERSION_NAME = "versionName"
        private const val EXTRA_TAG_NAME = "tagName"
        private const val EXTRA_RELEASE_NOTES = "releaseNotes"
        private const val EXTRA_APK_NAME = "apkName"
        private const val EXTRA_APK_DOWNLOAD_URL = "apkDownloadUrl"
        private const val EXTRA_APK_SIZE_BYTES = "apkSizeBytes"
        private const val NOTIFICATION_MIN_INTERVAL_MS = 700L

        fun downloadIntent(context: Context, release: AppUpdateRelease): Intent {
            return Intent(context, AppUpdateDownloadService::class.java)
                .setAction(ACTION_DOWNLOAD)
                .putExtra(EXTRA_VERSION_NAME, release.versionName)
                .putExtra(EXTRA_TAG_NAME, release.tagName)
                .putExtra(EXTRA_RELEASE_NOTES, release.releaseNotes)
                .putExtra(EXTRA_APK_NAME, release.apkName)
                .putExtra(EXTRA_APK_DOWNLOAD_URL, release.apkDownloadUrl)
                .putExtra(EXTRA_APK_SIZE_BYTES, release.apkSizeBytes)
        }

        fun cancelIntent(context: Context): Intent {
            return Intent(context, AppUpdateDownloadService::class.java)
                .setAction(ACTION_CANCEL)
        }
    }
}
