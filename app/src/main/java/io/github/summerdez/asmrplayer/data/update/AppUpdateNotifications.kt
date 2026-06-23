package io.github.summerdez.asmrplayer.data.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import io.github.summerdez.asmrplayer.R
import io.github.summerdez.asmrplayer.ui.activity.MainActivity
import java.util.Locale

object AppUpdateNotifications {
    const val NOTIFICATION_ID = 3001

    private const val CHANNEL_ID = "app_update"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "应用更新",
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.description = "应用更新包下载进度"
        manager.createNotificationChannel(channel)
    }

    fun buildDownloading(context: Context, state: AppUpdateDownloadState): Notification {
        val title = "正在下载更新 ${state.versionName.ifBlank { "--" }}"
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(progressText(state))
            .setContentIntent(openAppIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_notification),
                    "取消",
                    cancelIntent(context),
                ).build(),
            )
        if (state.totalBytes > 0L) {
            builder.setProgress(100, state.progressPercent ?: 0, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    fun buildTerminal(context: Context, state: AppUpdateDownloadState): Notification {
        val title = when (state.status) {
            AppUpdateDownloadStateStatus.DOWNLOADED -> "更新下载完成"
            AppUpdateDownloadStateStatus.FAILED -> "更新下载失败"
            AppUpdateDownloadStateStatus.CANCELED -> "更新下载已取消"
            else -> "应用更新"
        }
        val text = when (state.status) {
            AppUpdateDownloadStateStatus.DOWNLOADED ->
                "更新 ${state.versionName.ifBlank { "--" }} 已下载，点击返回应用安装"
            AppUpdateDownloadStateStatus.FAILED -> state.error.ifBlank { "下载更新失败" }
            AppUpdateDownloadStateStatus.CANCELED -> "已取消更新下载"
            else -> progressText(state)
        }
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent(context))
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
    }

    fun notifyDownloading(context: Context, state: AppUpdateDownloadState) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildDownloading(context, state))
    }

    fun notifyTerminal(context: Context, state: AppUpdateDownloadState) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildTerminal(context, state))
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.cancel(NOTIFICATION_ID)
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelIntent(context: Context): PendingIntent {
        val intent = AppUpdateDownloadService.cancelIntent(context)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getService(context, 1, intent, flags)
    }

    private fun progressText(state: AppUpdateDownloadState): String {
        val prefix = state.progressPercent?.let { "$it% · " }.orEmpty()
        return "$prefix${formatBytes(state.speedBytesPerSecond)}/s · " +
            "${formatBytes(state.bytesDownloaded)} / ${formatBytes(state.totalBytes)}"
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) {
            return "--"
        }
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024.0 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex += 1
        }
        return if (unitIndex == 0) {
            "${value.toLong()} ${units[unitIndex]}"
        } else {
            String.format(Locale.US, "%.1f %s", value, units[unitIndex])
        }
    }
}
