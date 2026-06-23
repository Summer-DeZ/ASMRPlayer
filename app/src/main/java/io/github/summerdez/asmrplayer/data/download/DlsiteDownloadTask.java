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
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class DlsiteDownloadTask {
    private static final int BUFFER_SIZE = 64 * 1024;

    private DlsiteDownloadTask() {
    }

    static Result downloadAndImport(
            Context context,
            DlsiteApi dlsiteApi,
            LibraryRepository libraryRepository,
            DlsiteWork work) throws IOException {
        return downloadAndImport(context, dlsiteApi, libraryRepository, work, "");
    }

    static Result downloadAndImport(
            Context context,
            DlsiteApi dlsiteApi,
            LibraryRepository libraryRepository,
            DlsiteWork work,
            String downloadOptionId) throws IOException {
        List<String> optionIds = new ArrayList<>();
        if (!TextUtils.isEmpty(downloadOptionId)) {
            optionIds.add(downloadOptionId);
        }
        return downloadAndImport(context, dlsiteApi, libraryRepository, work, optionIds, null);
    }

    static Result downloadAndImport(
            Context context,
            DlsiteApi dlsiteApi,
            LibraryRepository libraryRepository,
            DlsiteWork work,
            List<String> downloadOptionIds,
            ContentListener listener) throws IOException {
        DlsiteWork importWork = work.withEnsuredCoverUrl();
        File workDir = new File(context.getFilesDir(), "dlsite/works/" + importWork.workId);

        if (!workDir.mkdirs() && !workDir.isDirectory()) {
            throw new IOException("无法创建作品目录");
        }

        List<DlsiteDownloadOption> options = dlsiteApi.fetchDownloadOptions(importWork);
        List<DlsiteDownloadOption> selectedOptions = selectedOptions(options, downloadOptionIds);
        if (selectedOptions.isEmpty()) {
            throw new IOException("没有找到可下载的内容");
        }

        List<File> importedAudioFiles = new ArrayList<>();
        List<DownloadedContent> downloadedContents = new ArrayList<>();
        for (DlsiteDownloadOption option : selectedOptions) {
            throwIfInterrupted();
            File contentDir = contentDir(workDir, option);
            File marker = new File(contentDir, ".downloaded");
            List<File> audioFiles;
            if (listener != null) {
                listener.onContentStarted(option, contentDir);
            }
            if (marker.isFile()) {
                audioFiles = audioFilesIn(contentDir);
            } else {
                deleteRecursively(contentDir);
                if (!contentDir.mkdirs() && !contentDir.isDirectory()) {
                    throw new IOException("无法创建内容目录");
                }
                audioFiles = dlsiteApi.downloadWorkFiles(importWork, contentDir, option.id);
                if (audioFiles.isEmpty()) {
                    throw new IOException("没有找到可导入的音频");
                }
                if (!marker.createNewFile() && !marker.isFile()) {
                    throw new IOException("无法写入内容下载标记");
                }
            }
            if (audioFiles.isEmpty()) {
                throw new IOException("没有找到可导入的音频");
            }
            importedAudioFiles.addAll(audioFiles);
            downloadedContents.add(new DownloadedContent(option, contentDir, audioFiles));
        }

        File coverFile = existingCoverFile(importWork);
        if (coverFile == null) {
            coverFile = downloadCover(dlsiteApi, importWork, workDir);
        }
        String coverUri = "";
        if (coverFile != null && coverFile.isFile()) {
            coverUri = Uri.fromFile(coverFile).toString();
        }
        ImportResult importResult = importPlaylist(context, libraryRepository, importWork, importedAudioFiles, coverFile);
        List<ContentResult> contentResults = contentResultsForImport(downloadedContents, importResult);
        for (int i = 0; i < downloadedContents.size(); i++) {
            if (listener != null) {
                listener.onContentFinished(downloadedContents.get(i).option, contentResults.get(i));
            }
        }
        return new Result(
                importResult.playlistId,
                workDir.getAbsolutePath(),
                importResult.totalTrackCount,
                coverUri,
                contentResults);
    }

    public static void deleteCache(Context context, DlsiteWork work) throws IOException {
        if (work == null || TextUtils.isEmpty(work.workId)) {
            return;
        }
        File workDir = new File(context.getFilesDir(), "dlsite/works/" + work.workId);
        deleteRecursively(workDir);
    }

    public static void deleteContentCache(Context context, DlsiteWork work, String optionId) throws IOException {
        if (work == null || TextUtils.isEmpty(work.workId)) {
            return;
        }
        File workDir = new File(context.getFilesDir(), "dlsite/works/" + work.workId);
        deleteRecursively(new File(new File(workDir, "contents"), safeContentId(optionId)));
    }

    private static ImportResult importPlaylist(
            Context context,
            LibraryRepository libraryRepository,
            DlsiteWork work,
            List<File> audioFiles,
            File coverFile) {
        Playlist playlist = libraryRepository.getPlaylist(work.playlistId);
        if (playlist == null) {
            playlist = libraryRepository.createPlaylist(work.displayTitle());
        }
        if (coverFile != null && coverFile.isFile()) {
            libraryRepository.setPlaylistCover(playlist.id, Uri.fromFile(coverFile).toString());
        }
        Set<String> existingUris = new HashSet<>();
        Map<String, String> existingTrackIdsByUri = new HashMap<>();
        for (TrackItem track : playlist.tracks) {
            existingUris.add(track.uri);
            existingTrackIdsByUri.put(track.uri, track.id);
        }
        Map<String, File> subtitles = subtitleFilesByName(audioFiles);
        List<String> addedTrackIds = new ArrayList<>();
        Map<String, String> trackIdsByPath = new HashMap<>();
        for (File audioFile : audioFiles) {
            String audioUri = Uri.fromFile(audioFile).toString();
            if (existingUris.contains(audioUri)) {
                String existingTrackId = existingTrackIdsByUri.get(audioUri);
                if (!TextUtils.isEmpty(existingTrackId)) {
                    trackIdsByPath.put(audioFile.getAbsolutePath(), existingTrackId);
                }
                continue;
            }
            File subtitleFile = subtitles.get(normalizedName(audioFile.getName() + ".vtt"));
            String trackId = UUID.randomUUID().toString();
            libraryRepository.addTrack(
                    playlist.id,
                    new TrackItem(
                            trackId,
                            audioFile.getName(),
                            audioUri,
                            subtitleFile == null ? "" : Uri.fromFile(subtitleFile).toString(),
                            subtitleFile == null ? "" : subtitleFile.getName(),
                            DocumentFiles.audioDurationMs(context, Uri.fromFile(audioFile))));
            addedTrackIds.add(trackId);
            existingUris.add(audioUri);
            existingTrackIdsByUri.put(audioUri, trackId);
            trackIdsByPath.put(audioFile.getAbsolutePath(), trackId);
        }
        Playlist updated = libraryRepository.getPlaylist(playlist.id);
        return new ImportResult(
                playlist.id,
                addedTrackIds,
                updated == null ? playlist.tracks.size() + addedTrackIds.size() : updated.tracks.size(),
                trackIdsByPath);
    }

    static List<ContentResult> contentResultsForImport(
            List<DownloadedContent> downloadedContents,
            ImportResult importResult) {
        List<ContentResult> contentResults = new ArrayList<>();
        if (downloadedContents == null) {
            return contentResults;
        }
        Map<String, String> trackIdsByPath = importResult == null
                ? Collections.emptyMap()
                : importResult.trackIdsByPath;
        for (DownloadedContent content : downloadedContents) {
            List<String> trackIds = new ArrayList<>();
            for (File audioFile : content.audioFiles) {
                String trackId = trackIdsByPath.get(audioFile.getAbsolutePath());
                if (trackId != null && !trackId.isEmpty()) {
                    trackIds.add(trackId);
                }
            }
            contentResults.add(new ContentResult(
                    content.option.id,
                    content.option.title,
                    content.contentDir.getAbsolutePath(),
                    trackIds,
                    content.audioFiles.size()));
        }
        return contentResults;
    }

    private static List<DlsiteDownloadOption> selectedOptions(
            List<DlsiteDownloadOption> options,
            List<String> downloadOptionIds) throws IOException {
        if (options == null || options.isEmpty()) {
            return Collections.emptyList();
        }
        if (downloadOptionIds == null || downloadOptionIds.isEmpty()) {
            return options;
        }
        Set<String> ids = new HashSet<>(downloadOptionIds);
        List<DlsiteDownloadOption> selected = new ArrayList<>();
        for (DlsiteDownloadOption option : options) {
            if (ids.contains(option.id)) {
                selected.add(option);
            }
        }
        if (selected.isEmpty()) {
            throw new IOException("没有找到所选下载内容");
        }
        return selected;
    }

    private static File contentDir(File workDir, DlsiteDownloadOption option) {
        return new File(new File(workDir, "contents"), safeContentId(option.id));
    }

    private static String safeContentId(String optionId) {
        String id = TextUtils.isEmpty(optionId) ? "default" : optionId;
        return safeFileName(id);
    }

    private static File downloadCover(DlsiteApi dlsiteApi, DlsiteWork work, File workDir) {
        if (TextUtils.isEmpty(work.coverUrl)) {
            return null;
        }
        try {
            return dlsiteApi.downloadCover(work, new File(workDir, "cover"));
        } catch (IOException ignored) {
            return null;
        }
    }

    private static File existingCoverFile(DlsiteWork work) {
        if (work == null || TextUtils.isEmpty(work.coverUri)) {
            return null;
        }
        try {
            Uri uri = Uri.parse(work.coverUri);
            if (!"file".equals(uri.getScheme()) || TextUtils.isEmpty(uri.getPath())) {
                return null;
            }
            File file = new File(uri.getPath());
            return file.isFile() ? file : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static Map<String, File> subtitleFilesByName(List<File> audioFiles) {
        Map<String, File> subtitles = new HashMap<>();
        List<File> roots = new ArrayList<>();
        for (File audioFile : audioFiles) {
            File parent = audioFile.getParentFile();
            if (parent != null && !roots.contains(parent)) {
                roots.add(parent);
            }
        }
        for (File root : roots) {
            File[] children = root.listFiles();
            if (children == null) {
                continue;
            }
            for (File child : children) {
                if (child.isFile() && normalizedName(child.getName()).endsWith(".vtt")) {
                    subtitles.put(normalizedName(child.getName()), child);
                }
            }
        }
        return subtitles;
    }

    private static void unzipWithFallback(File zipFile, File destinationDir) throws IOException {
        try {
            unzip(zipFile, destinationDir, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException firstFailure) {
            deleteChildren(destinationDir);
            unzip(zipFile, destinationDir, Charset.forName("Shift_JIS"));
        }
    }

    private static void unzip(File zipFile, File destinationDir, Charset charset) throws IOException {
        String destinationPath = destinationDir.getCanonicalPath() + File.separator;
        try (ZipInputStream input = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zipFile)), charset)) {
            ZipEntry entry;
            byte[] buffer = new byte[BUFFER_SIZE];
            while ((entry = input.getNextEntry()) != null) {
                File outputFile = new File(destinationDir, entry.getName());
                String outputPath = outputFile.getCanonicalPath();
                if (!outputPath.startsWith(destinationPath)) {
                    throw new IOException("压缩包路径不安全");
                }
                if (entry.isDirectory()) {
                    if (!outputFile.mkdirs() && !outputFile.isDirectory()) {
                        throw new IOException("无法创建目录");
                    }
                    continue;
                }
                File parent = outputFile.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("无法创建目录");
                }
                try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(outputFile))) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                    }
                }
                input.closeEntry();
            }
        }
    }

    private static boolean isZipArchive(File file) throws IOException {
        byte[] header = new byte[4];
        try (FileInputStream input = new FileInputStream(file)) {
            int read = input.read(header);
            return read == 4
                    && header[0] == 'P'
                    && header[1] == 'K'
                    && (header[2] == 3 || header[2] == 5 || header[2] == 7)
                    && (header[3] == 4 || header[3] == 6 || header[3] == 8);
        }
    }

    private static void copySingleFile(File source, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建目录");
        }
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
        }
    }

    private static List<File> audioFilesIn(File root) {
        List<File> files = new ArrayList<>();
        collectAudioFiles(root, files);
        Collections.sort(files, Comparator.comparing(File::getAbsolutePath, String.CASE_INSENSITIVE_ORDER));
        return files;
    }

    private static void collectAudioFiles(File file, List<File> output) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isFile()) {
            if (DocumentFiles.isSupportedAudioName(file.getName(), "")) {
                output.add(file);
            }
            return;
        }
        File[] children = file.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            collectAudioFiles(child, output);
        }
    }

    private static void deleteRecursively(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!file.delete()) {
            throw new IOException("无法清理旧文件");
        }
    }

    private static void deleteChildren(File directory) throws IOException {
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            deleteRecursively(child);
        }
    }

    private static String safeFileName(String value) {
        String safe = TextUtils.isEmpty(value) ? "download" : value;
        return safe.replaceAll("[\\\\/:*?\"<>|]+", "_");
    }

    private static String normalizedName(String value) {
        return (value == null ? "" : value.trim()).toLowerCase(Locale.ROOT);
    }

    private static void throwIfInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("下载已中断");
        }
    }

    static final class Result {
        final String playlistId;
        final String localPath;
        final int trackCount;
        final String coverUri;
        final List<ContentResult> contentResults;

        Result(String playlistId, String localPath, int trackCount, String coverUri) {
            this(playlistId, localPath, trackCount, coverUri, Collections.emptyList());
        }

        Result(String playlistId, String localPath, int trackCount, String coverUri, List<ContentResult> contentResults) {
            this.playlistId = playlistId == null ? "" : playlistId;
            this.localPath = localPath == null ? "" : localPath;
            this.trackCount = trackCount;
            this.coverUri = coverUri == null ? "" : coverUri;
            this.contentResults = contentResults == null ? Collections.emptyList() : new ArrayList<>(contentResults);
        }
    }

    interface ContentListener {
        void onContentStarted(DlsiteDownloadOption option, File contentDir);

        void onContentFinished(DlsiteDownloadOption option, ContentResult result);
    }

    static final class ContentResult {
        final String optionId;
        final String title;
        final String localPath;
        final List<String> trackIds;
        final int trackCount;

        ContentResult(String optionId, String title, String localPath, List<String> trackIds, int trackCount) {
            this.optionId = optionId == null ? "" : optionId;
            this.title = title == null ? "" : title;
            this.localPath = localPath == null ? "" : localPath;
            this.trackIds = trackIds == null ? Collections.emptyList() : new ArrayList<>(trackIds);
            this.trackCount = trackCount;
        }
    }

    static final class DownloadedContent {
        final DlsiteDownloadOption option;
        final File contentDir;
        final List<File> audioFiles;

        DownloadedContent(DlsiteDownloadOption option, File contentDir, List<File> audioFiles) {
            this.option = option;
            this.contentDir = contentDir;
            this.audioFiles = audioFiles == null ? Collections.emptyList() : new ArrayList<>(audioFiles);
        }
    }

    static final class ImportResult {
        final String playlistId;
        final List<String> addedTrackIds;
        final int totalTrackCount;
        final Map<String, String> trackIdsByPath;

        ImportResult(String playlistId, List<String> addedTrackIds, int totalTrackCount) {
            this(playlistId, addedTrackIds, totalTrackCount, Collections.emptyMap());
        }

        ImportResult(
                String playlistId,
                List<String> addedTrackIds,
                int totalTrackCount,
                Map<String, String> trackIdsByPath) {
            this.playlistId = playlistId == null ? "" : playlistId;
            this.addedTrackIds = addedTrackIds == null ? Collections.emptyList() : new ArrayList<>(addedTrackIds);
            this.totalTrackCount = totalTrackCount;
            this.trackIdsByPath = trackIdsByPath == null ? Collections.emptyMap() : new HashMap<>(trackIdsByPath);
        }
    }
}
