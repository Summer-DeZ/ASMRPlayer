package io.github.summerdez.asmrplayer.data.download;

import io.github.summerdez.asmrplayer.R;
import io.github.summerdez.asmrplayer.data.*;
import io.github.summerdez.asmrplayer.data.remote.*;
import io.github.summerdez.asmrplayer.data.download.*;
import io.github.summerdez.asmrplayer.data.files.*;
import io.github.summerdez.asmrplayer.domain.*;
import io.github.summerdez.asmrplayer.domain.model.*;
import io.github.summerdez.asmrplayer.playback.*;
import io.github.summerdez.asmrplayer.presentation.*;
import io.github.summerdez.asmrplayer.ui.*;
import io.github.summerdez.asmrplayer.ui.activity.*;
import io.github.summerdez.asmrplayer.ui.components.*;
import io.github.summerdez.asmrplayer.ui.screens.*;
import io.github.summerdez.asmrplayer.ui.theme.*;
import io.github.summerdez.asmrplayer.ui.util.*;
import io.github.summerdez.asmrplayer.di.*;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

final class DlsiteDownloadNotifications {
    static final int NOTIFICATION_ID = 2001;

    private static final String CHANNEL_ID = "dlsite_download";

    private DlsiteDownloadNotifications() {
    }

    static void ensureChannel(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "DLsite 下载",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("DLsite 作品下载与导入");
        manager.createNotificationChannel(channel);
    }

    static Notification build(Context context, String title, String status) {
        Intent openIntent = new Intent(context, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(TextUtils.isEmpty(title) ? "DLsite 下载" : title)
                .setContentText(TextUtils.isEmpty(status) ? "下载中" : status)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    static void update(Context context, String title, String status) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        manager.notify(NOTIFICATION_ID, build(context, title, status));
    }
}
