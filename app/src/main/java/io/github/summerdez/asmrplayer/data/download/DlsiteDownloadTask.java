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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        DlsiteWork importWork = work.withEnsuredCoverUrl();
        File workDir = new File(context.getFilesDir(), "dlsite/works/" + importWork.workId);

        if (importWork.isDownloaded() && workDir.isDirectory()) {
            return new Result(
                    importWork.playlistId,
                    workDir.getAbsolutePath(),
                    importWork.trackCount,
                    importWork.coverUri);
        }

        deleteRecursively(workDir);
        if (!workDir.mkdirs() && !workDir.isDirectory()) {
            throw new IOException("无法创建作品目录");
        }

        List<File> audioFiles = dlsiteApi.downloadWorkFiles(importWork, workDir, downloadOptionId);
        if (audioFiles.isEmpty()) {
            throw new IOException("没有找到可导入的音频");
        }

        File coverFile = existingCoverFile(importWork);
        if (coverFile == null) {
            coverFile = downloadCover(dlsiteApi, importWork, workDir);
        }
        String coverUri = "";
        if (coverFile != null && coverFile.isFile()) {
            coverUri = Uri.fromFile(coverFile).toString();
        }
        String playlistId = importPlaylist(context, libraryRepository, importWork, audioFiles, coverFile);
        return new Result(playlistId, workDir.getAbsolutePath(), audioFiles.size(), coverUri);
    }

    public static void deleteCache(Context context, DlsiteWork work) throws IOException {
        if (work == null || TextUtils.isEmpty(work.workId)) {
            return;
        }
        File workDir = new File(context.getFilesDir(), "dlsite/works/" + work.workId);
        deleteRecursively(workDir);
    }

    private static String importPlaylist(
            Context context,
            LibraryRepository libraryRepository,
            DlsiteWork work,
            List<File> audioFiles,
            File coverFile) {
        Playlist playlist = libraryRepository.createPlaylist(work.displayTitle());
        if (coverFile != null && coverFile.isFile()) {
            libraryRepository.setPlaylistCover(playlist.id, Uri.fromFile(coverFile).toString());
        }
        Map<String, File> subtitles = subtitleFilesByName(audioFiles);
        for (File audioFile : audioFiles) {
            File subtitleFile = subtitles.get(normalizedName(audioFile.getName() + ".vtt"));
            libraryRepository.addTrack(
                    playlist.id,
                    new TrackItem(
                            UUID.randomUUID().toString(),
                            audioFile.getName(),
                            Uri.fromFile(audioFile).toString(),
                            subtitleFile == null ? "" : Uri.fromFile(subtitleFile).toString(),
                            subtitleFile == null ? "" : subtitleFile.getName(),
                            DocumentFiles.audioDurationMs(context, Uri.fromFile(audioFile))));
        }
        return playlist.id;
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

    static final class Result {
        final String playlistId;
        final String localPath;
        final int trackCount;
        final String coverUri;

        Result(String playlistId, String localPath, int trackCount, String coverUri) {
            this.playlistId = playlistId == null ? "" : playlistId;
            this.localPath = localPath == null ? "" : localPath;
            this.trackCount = trackCount;
            this.coverUri = coverUri == null ? "" : coverUri;
        }
    }
}
