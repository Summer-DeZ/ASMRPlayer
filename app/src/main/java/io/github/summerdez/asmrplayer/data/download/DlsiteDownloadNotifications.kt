package io.github.summerdez.asmrplayer.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import io.github.summerdez.asmrplayer.R
import io.github.summerdez.asmrplayer.data.DlsiteDownloadState
import io.github.summerdez.asmrplayer.data.DlsiteDownloadSummary
import io.github.summerdez.asmrplayer.ui.activity.MainActivity
import java.util.Locale

object DlsiteDownloadNotifications {
    const val NOTIFICATION_ID: Int = 2001

    private const val CHANNEL_ID = "dlsite_download"

    @JvmStatic
    fun ensureChannel(context: Context?) {
        val manager = context?.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "DLsite 下载",
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.description = "DLsite 作品下载与导入"
        manager.createNotificationChannel(channel)
    }

    @JvmStatic
    fun build(context: Context?, title: String?, status: String?): Notification {
        return build(context, title, status, 0, 0L)
    }

    @JvmStatic
    fun buildSummary(context: Context?, state: DlsiteDownloadState?): Notification {
        val summary = state?.summary ?: DlsiteDownloadSummary()
        val title = if (summary.visible) {
            "DLsite 下载 · 正在下载 " + summary.runningCount + " / " + summary.totalCount + " 项"
        } else {
            "DLsite 下载"
        }
        val progressPercent = summary.progressPercent
        val text = if (progressPercent == null) {
            formatBytes(summary.speedBytesPerSecond) + "/s"
        } else {
            progressPercent.toString() + "% · " +
                formatBytes(summary.speedBytesPerSecond) + "/s · " +
                formatBytes(summary.bytesDownloaded) + " / " +
                formatBytes(summary.totalBytes)
        }
        return build(context, title, text, progressPercent ?: 0, summary.totalBytes)
    }

    @JvmStatic
    fun build(
        context: Context?,
        title: String?,
        status: String?,
        progressPercent: Int,
        totalBytes: Long,
    ): Notification {
        val openIntent = Intent(context, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (TextUtils.isEmpty(title)) "DLsite 下载" else title)
            .setContentText(if (TextUtils.isEmpty(status)) "下载中" else status)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (totalBytes > 0L) {
            builder.setProgress(100, progressPercent, false)
        }
        return builder.build()
    }

    @JvmStatic
    fun update(context: Context?, title: String?, status: String?) {
        val manager = context?.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, build(context, title, status))
    }

    @JvmStatic
    fun updateSummary(context: Context?, state: DlsiteDownloadState?) {
        val manager = context?.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildSummary(context, state))
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) {
            return "--"
        }
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024.0 && unit < units.size - 1) {
            value /= 1024.0
            unit++
        }
        return if (unit == 0) {
            value.toLong().toString() + " " + units[unit]
        } else {
            String.format(Locale.US, "%.1f %s", value, units[unit])
        }
    }
}
