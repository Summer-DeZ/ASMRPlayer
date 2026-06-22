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

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DlsiteDownloadService extends Service {
    static final String ACTION_DOWNLOAD = "io.github.summerdez.asmrplayer.action.DLSITE_DOWNLOAD";
    static final String ACTION_PAUSE = "io.github.summerdez.asmrplayer.action.DLSITE_PAUSE";
    static final String ACTION_DELETE = "io.github.summerdez.asmrplayer.action.DLSITE_DELETE";
    static final String EXTRA_WORK_ID = "extra_work_id";
    static final String EXTRA_OPTION_ID = "extra_option_id";
    static final String EXTRA_OPTION_IDS = "extra_option_ids";

    private static final String STOP_NONE = "";
    private static final String STOP_PAUSE = "pause";
    private static final String STOP_DELETE = "delete";
    private static final int MAX_CONCURRENT_DOWNLOADS = 2;

    private final Object lock = new Object();
    private final LinkedHashMap<String, DownloadRequest> queuedRequests = new LinkedHashMap<>();
    private final LinkedHashMap<String, DownloadWorker> runningWorkers = new LinkedHashMap<>();
    private DlsiteRepository dlsiteRepository;
    private DlsiteApi dlsiteApi;
    private LibraryRepository libraryRepository;
    private int latestStartId;

    public static Intent downloadIntent(Context context, String workId, String optionId) {
        List<String> optionIds = new ArrayList<>();
        if (optionId != null) {
            optionIds.add(optionId);
        }
        return downloadIntent(context, workId, optionIds);
    }

    public static Intent downloadIntent(Context context, String workId, List<String> optionIds) {
        Intent intent = new Intent(context, DlsiteDownloadService.class);
        intent.setAction(ACTION_DOWNLOAD);
        intent.putExtra(EXTRA_WORK_ID, workId == null ? "" : workId);
        ArrayList<String> ids = new ArrayList<>();
        if (optionIds != null) {
            ids.addAll(optionIds);
        }
        intent.putStringArrayListExtra(EXTRA_OPTION_IDS, ids);
        intent.putExtra(EXTRA_OPTION_ID, ids.isEmpty() ? "" : ids.get(0));
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
        latestStartId = startId;
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
        ArrayList<String> optionIds = intent.getStringArrayListExtra(EXTRA_OPTION_IDS);
        if (optionIds == null) {
            optionIds = new ArrayList<>();
            String optionId = intent.getStringExtra(EXTRA_OPTION_ID);
            if (optionId != null) {
                optionIds.add(optionId);
            }
        }
        DlsiteWork work = dlsiteRepository.getWork(workId);
        if (work == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        enqueueDownload(work, optionIds);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        synchronized (lock) {
            for (DownloadWorker worker : runningWorkers.values()) {
                worker.stopRequest = STOP_PAUSE;
                worker.interrupt();
            }
        }
        super.onDestroy();
    }

    private void enqueueDownload(DlsiteWork work, List<String> optionIds) {
        synchronized (lock) {
            if (queuedRequests.containsKey(work.workId) || runningWorkers.containsKey(work.workId)) {
                return;
            }
            DownloadRequest request = new DownloadRequest(work.workId, work.displayTitle(), new ArrayList<>(optionIds));
            queuedRequests.put(work.workId, request);
            dlsiteRepository.markQueued(work, request.optionIds, request.optionIds.size() + " 个内容");
            publishQueuePositionsLocked();
            promoteToForeground();
            scheduleLocked();
        }
    }

    private void scheduleLocked() {
        while (runningWorkers.size() < MAX_CONCURRENT_DOWNLOADS && !queuedRequests.isEmpty()) {
            Map.Entry<String, DownloadRequest> entry = queuedRequests.entrySet().iterator().next();
            queuedRequests.remove(entry.getKey());
            DownloadRequest request = entry.getValue();
            DlsiteWork work = dlsiteRepository.getWork(request.workId);
            if (work == null) {
                DlsiteDownloadStateBus.remove(request.workId);
                continue;
            }
            DownloadWorker worker = new DownloadWorker(request, work);
            runningWorkers.put(request.workId, worker);
            worker.start();
        }
        publishQueuePositionsLocked();
        updateNotification();
    }

    private void publishQueuePositionsLocked() {
        int index = 1;
        for (DownloadRequest request : queuedRequests.values()) {
            DlsiteDownloadStateBus.publishQueued(request.workId, request.title, index);
            index++;
        }
    }

    private void finishWorker(DownloadWorker worker) {
        synchronized (lock) {
            runningWorkers.remove(worker.request.workId);
            scheduleLocked();
            if (runningWorkers.isEmpty() && queuedRequests.isEmpty()) {
                stopForegroundSafely();
                stopSelf(latestStartId);
            }
        }
    }

    private void handlePause(String workId, int startId) {
        DlsiteWork work = dlsiteRepository.getWork(workId);
        if (work == null) {
            stopSelf(startId);
            return;
        }
        synchronized (lock) {
            DownloadWorker worker = runningWorkers.get(work.workId);
            if (worker != null) {
                worker.stopRequest = STOP_PAUSE;
                DlsiteDownloadStateBus.publishPaused(work.workId, work.displayTitle());
                markPausedSafely(work);
                worker.interrupt();
                return;
            }
            DownloadRequest queued = queuedRequests.remove(work.workId);
            if (queued != null) {
                markPausedSafely(work);
                dlsiteRepository.markContentPaused(work.workId, queued.optionIds);
                DlsiteDownloadStateBus.publishPaused(work.workId, work.displayTitle());
                publishQueuePositionsLocked();
                updateNotification();
                stopIfIdleLocked(startId);
                return;
            }
        }
        stopSelf(startId);
    }

    private void handleDelete(String workId, int startId) {
        DlsiteWork work = dlsiteRepository.getWork(workId);
        if (work == null) {
            stopSelf(startId);
            return;
        }
        synchronized (lock) {
            DownloadWorker worker = runningWorkers.get(work.workId);
            if (worker != null) {
                worker.stopRequest = STOP_DELETE;
                DlsiteDownloadStateBus.publishTask(
                        work.workId,
                        work.displayTitle(),
                        DlsiteDownloadTaskStatus.DOWNLOADING,
                        "删除中");
                dlsiteRepository.markCacheDeleted(work);
                worker.interrupt();
                return;
            }
            DownloadRequest queued = queuedRequests.remove(work.workId);
            if (queued != null) {
                deleteCachedWork(work);
                DlsiteDownloadStateBus.remove(work.workId);
                publishQueuePositionsLocked();
                updateNotification();
                stopIfIdleLocked(startId);
                return;
            }
        }
        deleteCachedWork(work);
        stopSelf(startId);
    }

    private void stopIfIdleLocked(int startId) {
        if (runningWorkers.isEmpty() && queuedRequests.isEmpty()) {
            stopForegroundSafely();
            stopSelf(startId);
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

    private void promoteToForeground() {
        DlsiteDownloadNotifications.ensureChannel(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    DlsiteDownloadNotifications.NOTIFICATION_ID,
                    DlsiteDownloadNotifications.buildSummary(this, DlsiteDownloadStateBus.snapshot()),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(
                    DlsiteDownloadNotifications.NOTIFICATION_ID,
                    DlsiteDownloadNotifications.buildSummary(this, DlsiteDownloadStateBus.snapshot()));
        }
    }

    private void updateNotification() {
        DlsiteDownloadNotifications.updateSummary(this, DlsiteDownloadStateBus.snapshot());
    }

    private String shortError(Exception exception) {
        String message = exception == null ? "" : exception.getMessage();
        if (TextUtils.isEmpty(message)) {
            message = "下载失败";
        }
        return message.length() > 42 ? message.substring(0, 42) + "..." : message;
    }

    private static final class DownloadRequest {
        final String workId;
        final String title;
        final List<String> optionIds;

        DownloadRequest(String workId, String title, List<String> optionIds) {
            this.workId = workId == null ? "" : workId;
            this.title = title == null ? "" : title;
            this.optionIds = optionIds == null ? new ArrayList<>() : new ArrayList<>(optionIds);
        }
    }

    private final class DownloadWorker extends Thread implements DlsiteDownloadTask.ContentListener {
        final DownloadRequest request;
        final DlsiteWork initialWork;
        volatile String stopRequest = STOP_NONE;

        DownloadWorker(DownloadRequest request, DlsiteWork work) {
            super("dlsite-download-" + request.workId);
            this.request = request;
            this.initialWork = work;
        }

        @Override
        public void run() {
            DlsiteWork work = initialWork;
            try {
                dlsiteRepository.markDownloading(work, TextUtils.join("|", request.optionIds), request.optionIds.size() + " 个内容");
                DlsiteDownloadStateBus.publishTask(
                        work.workId,
                        work.displayTitle(),
                        DlsiteDownloadTaskStatus.DOWNLOADING,
                        "下载中");
                updateNotification();
                DlsiteDownloadTask.Result result = DlsiteDownloadTask.downloadAndImport(
                        DlsiteDownloadService.this,
                        dlsiteApi,
                        libraryRepository,
                        work,
                        request.optionIds,
                        this);
                if (STOP_NONE.equals(stopRequest)) {
                    DlsiteWork downloadedWork = TextUtils.isEmpty(result.coverUri)
                            ? work
                            : work.withCoverUri(result.coverUri);
                    dlsiteRepository.markDownloaded(downloadedWork, result.playlistId, result.localPath, result.trackCount);
                    DlsiteDownloadStateBus.publishCompleted(work.workId, work.displayTitle());
                    updateNotification();
                }
            } catch (Exception exception) {
                if (STOP_PAUSE.equals(stopRequest)) {
                    markPausedSafely(work);
                    dlsiteRepository.markContentPaused(work.workId, request.optionIds);
                    DlsiteDownloadStateBus.publishPaused(work.workId, work.displayTitle());
                } else if (STOP_DELETE.equals(stopRequest)) {
                    deleteCachedWorkSafely(work);
                    DlsiteDownloadStateBus.remove(work.workId);
                } else {
                    String message = shortError(exception);
                    dlsiteRepository.markFailed(work, message);
                    for (String optionId : request.optionIds) {
                        dlsiteRepository.markContentFailed(work.workId, optionId, message);
                    }
                    DlsiteDownloadStateBus.publishFailed(work.workId, work.displayTitle(), message);
                }
                updateNotification();
            } finally {
                finishWorker(this);
            }
        }

        @Override
        public void onContentStarted(DlsiteDownloadOption option, File contentDir) {
            dlsiteRepository.markContentDownloading(request.workId, option.id);
            DlsiteDownloadStateBus.publishTask(
                    request.workId,
                    initialWork.displayTitle(),
                    DlsiteDownloadTaskStatus.DOWNLOADING,
                    "下载中",
                    0,
                    0L,
                    -1L,
                    0L,
                    option.id,
                    option.title);
            updateNotification();
        }

        @Override
        public void onContentFinished(DlsiteDownloadOption option, DlsiteDownloadTask.ContentResult result) {
            dlsiteRepository.markContentDownloaded(
                    request.workId,
                    option.id,
                    result.localPath,
                    result.trackIds,
                    result.trackCount);
        }
    }
}
