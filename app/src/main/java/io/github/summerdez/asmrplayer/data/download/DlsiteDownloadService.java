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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private static final String STOP_RESCHEDULE = "reschedule";
    private static final int MAX_CONCURRENT_DOWNLOADS = 2;

    private final Object lock = new Object();
    private final LinkedHashMap<String, DownloadWorker> runningWorkers = new LinkedHashMap<>();
    private final ExecutorService scheduler = Executors.newSingleThreadExecutor(runnable ->
            new Thread(runnable, "dlsite-download-scheduler"));
    private DlsiteDownloadBlockingAdapter downloadRepository;
    private DlsiteApi dlsiteApi;
    private DlsiteDownloadStateStore dlsiteDownloadStateStore;
    private int latestStartId;
    private boolean destroying;

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
        destroying = false;
        DlsiteDownloadServiceDependencies dependencies = AppGraph.container(this).getDlsiteDownloadServiceDependencies();
        downloadRepository = new DlsiteDownloadBlockingAdapter(
                dependencies.getDlsiteRepository(),
                dependencies.getLibraryRepository());
        dlsiteApi = dependencies.getDlsiteApi();
        dlsiteDownloadStateStore = dependencies.getDlsiteDownloadStateStore();
        DlsiteDownloadNotifications.ensureChannel(this);
        dispatchToScheduler(() -> downloadRepository.resetRunningDownloadQueue(), 0);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        latestStartId = startId;
        if (intent == null || TextUtils.isEmpty(intent.getAction())) {
            dispatchToScheduler(() -> handleEmptyStart(startId), startId);
            return START_NOT_STICKY;
        }
        if (ACTION_PAUSE.equals(intent.getAction())) {
            String workId = intent.getStringExtra(EXTRA_WORK_ID);
            dispatchToScheduler(() -> handlePause(workId, startId), startId);
            return START_NOT_STICKY;
        }
        if (ACTION_DELETE.equals(intent.getAction())) {
            String workId = intent.getStringExtra(EXTRA_WORK_ID);
            dispatchToScheduler(() -> handleDelete(workId, startId), startId);
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
        ArrayList<String> requestedOptionIds = new ArrayList<>(optionIds);
        dispatchToScheduler(() -> handleDownload(workId, requestedOptionIds, startId), startId);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        synchronized (lock) {
            destroying = true;
            for (DownloadWorker worker : runningWorkers.values()) {
                if (STOP_NONE.equals(worker.stopRequest)) {
                    worker.stopRequest = STOP_RESCHEDULE;
                }
                worker.interrupt();
            }
        }
        scheduler.shutdownNow();
        super.onDestroy();
    }

    private void dispatchToScheduler(Runnable command, int startId) {
        try {
            scheduler.execute(() -> {
                try {
                    command.run();
                } catch (RuntimeException exception) {
                    stopSelf(startId);
                }
            });
        } catch (RuntimeException exception) {
            stopSelf(startId);
        }
    }

    private void handleEmptyStart(int startId) {
        synchronized (lock) {
            if (hasPendingDownloadsLocked()) {
                promoteToForeground();
            }
            scheduleLocked();
            stopIfIdleLocked(startId);
        }
    }

    private void handleDownload(String workId, List<String> optionIds, int startId) {
        DlsiteWork work = downloadRepository.getWork(workId);
        if (work == null) {
            stopSelf(startId);
            return;
        }
        enqueueDownload(work, optionIds);
    }

    private void enqueueDownload(DlsiteWork work, List<String> optionIds) {
        synchronized (lock) {
            DlsiteDownloadQueueTask task = downloadRepository.enqueueDownload(
                    work,
                    optionIds,
                    optionIds == null ? "" : optionIds.size() + " 个内容");
            if (task == null) {
                stopIfIdleLocked(latestStartId);
                return;
            }
            publishQueuePositionsLocked();
            promoteToForeground();
            scheduleLocked();
        }
    }

    private void scheduleLocked() {
        if (destroying) {
            publishQueuePositionsLocked();
            updateNotification();
            return;
        }
        if (hasPendingDownloadsLocked()) {
            promoteToForeground();
        }
        while (runningWorkers.size() < MAX_CONCURRENT_DOWNLOADS) {
            List<DlsiteDownloadQueueTask> pendingTasks = downloadRepository.pendingDownloadQueueTasks(1);
            if (pendingTasks.isEmpty()) {
                break;
            }
            DlsiteDownloadQueueTask pendingTask = pendingTasks.get(0);
            if (runningWorkers.containsKey(pendingTask.workId)) {
                downloadRepository.markDownloadQueueTaskCanceled(pendingTask.taskId);
                continue;
            }
            DlsiteWork work = downloadRepository.getWork(pendingTask.workId);
            if (work == null) {
                downloadRepository.markDownloadQueueTaskFailed(pendingTask.taskId, "找不到作品记录");
                dlsiteDownloadStateStore.remove(pendingTask.workId);
                continue;
            }
            DlsiteDownloadQueueTask runningTask = downloadRepository.markDownloadQueueTaskRunning(pendingTask.taskId);
            if (runningTask == null) {
                continue;
            }
            DownloadRequest request = new DownloadRequest(
                    runningTask.taskId,
                    runningTask.workId,
                    work.displayTitle(),
                    runningTask.optionIdList());
            DownloadWorker worker = new DownloadWorker(request, work);
            runningWorkers.put(request.workId, worker);
            worker.start();
        }
        publishQueuePositionsLocked();
        updateNotification();
    }

    private void publishQueuePositionsLocked() {
        int index = 1;
        for (DlsiteDownloadQueueTask task : downloadRepository.pendingDownloadQueueTasks(Integer.MAX_VALUE)) {
            DlsiteWork work = downloadRepository.getWork(task.workId);
            String title = work == null ? task.workId : work.displayTitle();
            dlsiteDownloadStateStore.publishQueued(task.workId, title, index);
            index++;
        }
    }

    private void finishWorker(DownloadWorker worker) {
        synchronized (lock) {
            runningWorkers.remove(worker.request.workId);
            if (!destroying) {
                scheduleLocked();
            }
            if (runningWorkers.isEmpty() && !hasPendingDownloadsLocked()) {
                stopForegroundSafely();
                stopSelf(latestStartId);
            }
        }
    }

    private void handlePause(String workId, int startId) {
        DlsiteWork work = downloadRepository.getWork(workId);
        if (work == null) {
            stopSelf(startId);
            return;
        }
        synchronized (lock) {
            DownloadWorker worker = runningWorkers.get(work.workId);
            if (worker != null) {
                worker.stopRequest = STOP_PAUSE;
                dlsiteDownloadStateStore.publishPaused(work.workId, work.displayTitle());
                if (isContentDownload(worker.request.optionIds)) {
                    downloadRepository.markContentPaused(work.workId, worker.request.optionIds);
                } else {
                    markPausedSafely(work);
                }
                worker.interrupt();
                return;
            }
            DlsiteDownloadQueueTask queued = downloadRepository.pauseQueuedDownload(work.workId);
            if (queued != null) {
                if (isContentDownload(queued.optionIdList())) {
                    downloadRepository.markContentPaused(work.workId, queued.optionIdList());
                } else {
                    markPausedSafely(work);
                }
                dlsiteDownloadStateStore.publishPaused(work.workId, work.displayTitle());
                scheduleLocked();
                stopIfIdleLocked(startId);
                return;
            }
        }
        stopSelf(startId);
    }

    private void handleDelete(String workId, int startId) {
        DlsiteWork work = downloadRepository.getWork(workId);
        if (work == null) {
            stopSelf(startId);
            return;
        }
        synchronized (lock) {
            DownloadWorker worker = runningWorkers.get(work.workId);
            if (worker != null) {
                worker.stopRequest = STOP_DELETE;
                dlsiteDownloadStateStore.publishTask(
                        work.workId,
                        work.displayTitle(),
                        DlsiteDownloadTaskStatus.DOWNLOADING,
                        "删除中");
                downloadRepository.markCacheDeleted(work);
                worker.interrupt();
                return;
            }
            DlsiteDownloadQueueTask queued = downloadRepository.cancelQueuedDownload(work.workId);
            if (queued != null) {
                deleteCachedWork(work);
                dlsiteDownloadStateStore.remove(work.workId);
                scheduleLocked();
                stopIfIdleLocked(startId);
                return;
            }
        }
        deleteCachedWork(work);
        stopSelf(startId);
    }

    private void stopIfIdleLocked(int startId) {
        if (runningWorkers.isEmpty() && !hasPendingDownloadsLocked()) {
            stopForegroundSafely();
            stopSelf(startId);
        }
    }

    private boolean hasPendingDownloadsLocked() {
        return !downloadRepository.pendingDownloadQueueTasks(1).isEmpty();
    }

    private void deleteCachedWork(DlsiteWork work) {
        try {
            DlsiteDownloadTask.deleteCache(this, work);
        } catch (Exception ignored) {
        }
        downloadRepository.markCacheDeleted(work);
    }

    private void deleteCachedWorkSafely(DlsiteWork work) {
        try {
            deleteCachedWork(work);
        } catch (Exception ignored) {
        }
    }

    private void markPausedSafely(DlsiteWork work) {
        try {
            downloadRepository.markPaused(work);
        } catch (Exception ignored) {
        }
    }

    private boolean isContentDownload(List<String> optionIds) {
        return optionIds != null && !optionIds.isEmpty();
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
                    DlsiteDownloadNotifications.buildSummary(this, dlsiteDownloadStateStore.snapshot()),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(
                    DlsiteDownloadNotifications.NOTIFICATION_ID,
                    DlsiteDownloadNotifications.buildSummary(this, dlsiteDownloadStateStore.snapshot()));
        }
    }

    private void updateNotification() {
        DlsiteDownloadNotifications.updateSummary(this, dlsiteDownloadStateStore.snapshot());
    }

    private String shortError(Exception exception) {
        String message = exception == null ? "" : exception.getMessage();
        if (TextUtils.isEmpty(message)) {
            message = "下载失败";
        }
        return message.length() > 42 ? message.substring(0, 42) + "..." : message;
    }

    private static final class DownloadRequest {
        final String taskId;
        final String workId;
        final String title;
        final List<String> optionIds;

        DownloadRequest(String taskId, String workId, String title, List<String> optionIds) {
            this.taskId = taskId == null ? "" : taskId;
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
            boolean contentDownload = isContentDownload(request.optionIds);
            try {
                if (!contentDownload) {
                    downloadRepository.markDownloading(work, TextUtils.join("|", request.optionIds), request.optionIds.size() + " 个内容");
                    dlsiteDownloadStateStore.publishTask(
                            work.workId,
                            work.displayTitle(),
                            DlsiteDownloadTaskStatus.DOWNLOADING,
                            "下载中");
                    updateNotification();
                }
                DlsiteDownloadTask.Result result = DlsiteDownloadTask.downloadAndImport(
                        DlsiteDownloadService.this,
                        dlsiteApi,
                        downloadRepository,
                        work,
                        request.optionIds,
                        this);
                if (STOP_NONE.equals(stopRequest)) {
                    DlsiteWork downloadedWork = TextUtils.isEmpty(result.coverUri)
                            ? work
                            : work.withCoverUri(result.coverUri);
                    if (contentDownload) {
                        downloadRepository.markImported(downloadedWork, result.playlistId, result.localPath, result.trackCount);
                    } else {
                        downloadRepository.markDownloaded(downloadedWork, result.playlistId, result.localPath, result.trackCount);
                    }
                    downloadRepository.markDownloadQueueTaskCompleted(request.taskId);
                    dlsiteDownloadStateStore.publishCompleted(work.workId, work.displayTitle());
                    updateNotification();
                }
            } catch (Exception exception) {
                if (STOP_PAUSE.equals(stopRequest)) {
                    downloadRepository.markDownloadQueueTaskPaused(request.taskId);
                    if (!contentDownload) {
                        markPausedSafely(work);
                    }
                    downloadRepository.markContentPaused(work.workId, request.optionIds);
                    dlsiteDownloadStateStore.publishPaused(work.workId, work.displayTitle());
                } else if (STOP_DELETE.equals(stopRequest)) {
                    downloadRepository.markDownloadQueueTaskCanceled(request.taskId);
                    deleteCachedWorkSafely(work);
                    dlsiteDownloadStateStore.remove(work.workId);
                } else if (STOP_RESCHEDULE.equals(stopRequest)) {
                    downloadRepository.markDownloadQueueTaskPending(request.taskId);
                    if (contentDownload) {
                        downloadRepository.markContentQueued(work.workId, request.optionIds);
                    } else {
                        downloadRepository.markQueued(work, request.optionIds, request.optionIds.size() + " 个内容");
                    }
                    dlsiteDownloadStateStore.publishQueued(work.workId, work.displayTitle(), 1);
                } else {
                    String message = shortError(exception);
                    downloadRepository.markDownloadQueueTaskFailed(request.taskId, message);
                    if (!contentDownload) {
                        downloadRepository.markFailed(work, message);
                    }
                    for (String optionId : request.optionIds) {
                        downloadRepository.markContentFailed(work.workId, optionId, message);
                    }
                    dlsiteDownloadStateStore.publishFailed(work.workId, work.displayTitle(), message);
                }
                updateNotification();
            } finally {
                finishWorker(this);
            }
        }

        @Override
        public void onContentStarted(DlsiteDownloadOption option, File contentDir) {
            downloadRepository.markContentDownloading(request.workId, option.id);
        }

        @Override
        public void onContentProgress(
                DlsiteDownloadOption option,
                DlsiteJsonParser.ContentFile contentFile,
                long bytesDownloaded,
                long totalBytes) {
            dlsiteDownloadStateStore.publishTask(
                    request.workId,
                    initialWork.displayTitle(),
                    DlsiteDownloadTaskStatus.DOWNLOADING,
                    "下载中",
                    0,
                    bytesDownloaded,
                    totalBytes,
                    0L,
                    option.id,
                    option.title);
            updateNotification();
        }

        @Override
        public void onContentFinished(DlsiteDownloadOption option, DlsiteDownloadTask.ContentResult result) {
            downloadRepository.markContentDownloaded(
                    request.workId,
                    option.id,
                    result.title,
                    result.localPath,
                    result.trackIds,
                    result.trackCount);
        }
    }
}
