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
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;

public class DlsiteDownloadService extends Service {
    static final String ACTION_DOWNLOAD = "io.github.summerdez.asmrplayer.action.DLSITE_DOWNLOAD";
    static final String ACTION_PAUSE = "io.github.summerdez.asmrplayer.action.DLSITE_PAUSE";
    static final String ACTION_DELETE = "io.github.summerdez.asmrplayer.action.DLSITE_DELETE";
    static final String EXTRA_WORK_ID = "extra_work_id";
    static final String EXTRA_OPTION_ID = "extra_option_id";

    private static final String STOP_NONE = "";
    private static final String STOP_PAUSE = "pause";
    private static final String STOP_DELETE = "delete";

    private static volatile boolean activeDownload;
    private static volatile String activeWorkId = "";

    private DlsiteRepository dlsiteRepository;
    private DlsiteApi dlsiteApi;
    private LibraryRepository libraryRepository;
    private Thread downloadThread;
    private volatile String stopRequest = STOP_NONE;

    public static Intent downloadIntent(Context context, String workId, String optionId) {
        Intent intent = new Intent(context, DlsiteDownloadService.class);
        intent.setAction(ACTION_DOWNLOAD);
        intent.putExtra(EXTRA_WORK_ID, workId == null ? "" : workId);
        intent.putExtra(EXTRA_OPTION_ID, optionId == null ? "" : optionId);
        return intent;
    }

    public static Intent pauseIntent(Context context, String workId) {
        Intent intent = new Intent(context, DlsiteDownloadService.class);
        intent.setAction(ACTION_PAUSE);
        intent.putExtra(EXTRA_WORK_ID, workId == null ? "" : workId);
        return intent;
    }

    public static Intent deleteIntent(Context context, String workId) {
        Intent intent = new Intent(context, DlsiteDownloadService.class);
        intent.setAction(ACTION_DELETE);
        intent.putExtra(EXTRA_WORK_ID, workId == null ? "" : workId);
        return intent;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AppContainer container = AppGraph.container(this);
        dlsiteRepository = container.getDlsiteRepository();
        dlsiteApi = container.getDlsiteApi();
        libraryRepository = container.getLibraryRepository();
        DlsiteDownloadNotifications.ensureChannel(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || TextUtils.isEmpty(intent.getAction())) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (ACTION_PAUSE.equals(intent.getAction())) {
            handlePause(intent.getStringExtra(EXTRA_WORK_ID), startId);
            return START_NOT_STICKY;
        }
        if (ACTION_DELETE.equals(intent.getAction())) {
            handleDelete(intent.getStringExtra(EXTRA_WORK_ID), startId);
            return START_NOT_STICKY;
        }
        if (!ACTION_DOWNLOAD.equals(intent.getAction())) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        String workId = intent.getStringExtra(EXTRA_WORK_ID);
        String optionId = intent.getStringExtra(EXTRA_OPTION_ID);
        DlsiteWork work = dlsiteRepository.getWork(workId);
        if (work == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        if (activeDownload) {
            return START_NOT_STICKY;
        }
        activeDownload = true;
        activeWorkId = work.workId;
        stopRequest = STOP_NONE;
        DlsiteDownloadStateBus.publish(work.workId, work.displayTitle(), "下载中");
        promoteToForeground(work.displayTitle(), "下载中");
        dlsiteRepository.markDownloading(work, optionId, work.downloadOptionTitle);
        downloadThread = new Thread(() -> runDownload(startId, work, optionId), "dlsite-download-service");
        downloadThread.start();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (downloadThread != null && downloadThread.isAlive()) {
            downloadThread.interrupt();
        }
        if (!activeDownload) {
            DlsiteDownloadStateBus.clear();
        }
        super.onDestroy();
    }

    private void runDownload(int startId, DlsiteWork work, String optionId) {
        try {
            DlsiteDownloadStateBus.publish(work.workId, work.displayTitle(), "下载并导入中");
            DlsiteDownloadNotifications.update(this, work.displayTitle(), "下载并导入中");
            DlsiteDownloadTask.Result result = DlsiteDownloadTask.downloadAndImport(
                    this,
                    dlsiteApi,
                    libraryRepository,
                    work,
                    TextUtils.isEmpty(optionId) ? "" : optionId);
            if (STOP_NONE.equals(stopRequest)) {
                DlsiteWork downloadedWork = TextUtils.isEmpty(result.coverUri)
                        ? work
                        : work.withCoverUri(result.coverUri);
                dlsiteRepository.markDownloaded(downloadedWork, result.playlistId, result.localPath, result.trackCount);
            }
        } catch (Exception exception) {
            if (STOP_NONE.equals(stopRequest)) {
                dlsiteRepository.markFailed(work, shortError(exception));
            }
        } finally {
            String finalStopRequest = stopRequest;
            if (STOP_PAUSE.equals(finalStopRequest)) {
                markPausedSafely(work);
            } else if (STOP_DELETE.equals(finalStopRequest)) {
                deleteCachedWorkSafely(work);
            }
            activeDownload = false;
            activeWorkId = "";
            stopRequest = STOP_NONE;
            DlsiteDownloadStateBus.clear();
            stopForegroundSafely();
            stopSelf(startId);
        }
    }

    private void handlePause(String workId, int startId) {
        DlsiteWork work = dlsiteRepository.getWork(workId);
        if (work == null) {
            stopSelf(startId);
            return;
        }
        if (activeDownload && work.workId.equals(activeWorkId)) {
            stopRequest = STOP_PAUSE;
            DlsiteDownloadStateBus.publish(work.workId, work.displayTitle(), "暂停中");
            markPausedSafely(work);
            interruptDownload();
            return;
        }
        if (work.isDownloading()) {
            markPausedSafely(work);
        }
        stopSelf(startId);
    }

    private void handleDelete(String workId, int startId) {
        DlsiteWork work = dlsiteRepository.getWork(workId);
        if (work == null) {
            stopSelf(startId);
            return;
        }
        if (activeDownload && work.workId.equals(activeWorkId)) {
            stopRequest = STOP_DELETE;
            DlsiteDownloadStateBus.publish(work.workId, work.displayTitle(), "删除中");
            dlsiteRepository.markCacheDeleted(work);
            interruptDownload();
            return;
        }
        deleteCachedWork(work);
        stopSelf(startId);
    }

    private void interruptDownload() {
        if (downloadThread != null && downloadThread.isAlive()) {
            downloadThread.interrupt();
        }
    }

    private void deleteCachedWork(DlsiteWork work) {
        try {
            DlsiteDownloadTask.deleteCache(this, work);
        } catch (Exception ignored) {
        }
        dlsiteRepository.markCacheDeleted(work);
    }

    private void deleteCachedWorkSafely(DlsiteWork work) {
        try {
            deleteCachedWork(work);
        } catch (Exception ignored) {
        }
    }

    private void markPausedSafely(DlsiteWork work) {
        try {
            dlsiteRepository.markPaused(work);
        } catch (Exception ignored) {
        }
    }

    private void stopForegroundSafely() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } catch (RuntimeException ignored) {
        }
    }

    private void promoteToForeground(String title, String status) {
        DlsiteDownloadNotifications.ensureChannel(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    DlsiteDownloadNotifications.NOTIFICATION_ID,
                    DlsiteDownloadNotifications.build(this, title, status),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(
                    DlsiteDownloadNotifications.NOTIFICATION_ID,
                    DlsiteDownloadNotifications.build(this, title, status));
        }
    }

    private String shortError(Exception exception) {
        String message = exception == null ? "" : exception.getMessage();
        if (TextUtils.isEmpty(message)) {
            message = "下载失败";
        }
        return message.length() > 42 ? message.substring(0, 42) + "..." : message;
    }
}
