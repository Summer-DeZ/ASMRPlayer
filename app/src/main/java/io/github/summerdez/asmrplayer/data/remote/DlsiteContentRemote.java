package io.github.summerdez.asmrplayer.data.remote;

import android.text.TextUtils;

import io.github.summerdez.asmrplayer.domain.DlsiteDownloadPlanner;
import io.github.summerdez.asmrplayer.domain.model.DlsiteDownloadOption;
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import okhttp3.Response;
import okhttp3.ResponseBody;

final class DlsiteContentRemote {
    private final DlsiteHttpClient httpClient;

    DlsiteContentRemote(DlsiteHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    void downloadTo(DlsiteWork work, File targetFile) throws IOException {
        String downloadUrl = resolveDownloadUrl(work);
        if (TextUtils.isEmpty(downloadUrl)) {
            throw new IOException("没有找到下载入口");
        }

        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建下载目录");
        }

        try (Response response = httpClient.execute(
                downloadUrl,
                work.detailUrl,
                "application/zip,application/octet-stream,*/*",
                "GET",
                null,
                DlsiteRemoteConstants.CONNECT_TIMEOUT_MS,
                DlsiteRemoteConstants.READ_TIMEOUT_MS)) {
            if (!response.isSuccessful()) {
                throw new IOException("下载失败: HTTP " + response.code());
            }
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new IOException("下载失败: 响应为空");
            }
            try (InputStream input = new BufferedInputStream(responseBody.byteStream());
                 FileOutputStream output = new FileOutputStream(targetFile)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    DlsiteRemoteFiles.throwIfInterrupted();
                    output.write(buffer, 0, count);
                }
            }
        }
    }

    List<DlsiteDownloadOption> fetchDownloadOptions(DlsiteWork work) throws IOException {
        String workId = work == null ? "" : work.workId;
        if (TextUtils.isEmpty(workId)) {
            throw new IOException("作品编号为空");
        }
        signDownloadCookies(workId);
        return DlsiteDownloadPlanner.optionsFor(fetchZiptree(workId));
    }

    List<File> downloadWorkFiles(DlsiteWork work, File workDir, String downloadOptionId) throws IOException {
        return downloadWorkFiles(work, workDir, downloadOptionId, null);
    }

    List<File> downloadWorkFiles(
            DlsiteWork work,
            File workDir,
            String downloadOptionId,
            DlsiteContentProgressListener progressListener) throws IOException {
        String workId = work == null ? "" : work.workId;
        if (TextUtils.isEmpty(workId)) {
            throw new IOException("作品编号为空");
        }
        if (!workDir.exists() && !workDir.mkdirs() && !workDir.isDirectory()) {
            throw new IOException("无法创建作品目录");
        }

        signDownloadCookies(workId);
        DlsiteJsonParser.DlsiteZiptree ziptree = fetchZiptree(workId);
        if (ziptree.audioFiles.isEmpty()) {
            throw new IOException("DLsite Play 没有返回可下载的音频文件");
        }
        List<DlsiteJsonParser.ContentFile> selectedContentFiles = selectedContentFiles(ziptree, downloadOptionId);
        if (selectedContentFiles.isEmpty()) {
            throw new IOException("所选版本没有可下载的音频文件");
        }
        Map<String, String> signParams = fetchDownloadSignParams(workId);
        String revision = ziptree.revision.isEmpty() ? DlsiteRemoteConstants.DEFAULT_REVISION : ziptree.revision;

        List<File> audioFiles = new ArrayList<>();
        Set<String> usedTargets = new HashSet<>();
        for (DlsiteJsonParser.ContentFile contentFile : selectedContentFiles) {
            DlsiteRemoteFiles.throwIfInterrupted();
            File audioFile = DlsiteRemoteFiles.uniqueTarget(DlsiteRemoteFiles.localFileFor(workDir, contentFile.displayPath), usedTargets);
            downloadSignedContentFile(
                    signedContentUrl(workId, contentFile.contentPath, revision, signParams),
                    audioFile,
                    "application/octet-stream,audio/*,*/*",
                    contentFile,
                    progressListener);
            audioFiles.add(audioFile);

            if (!TextUtils.isEmpty(contentFile.subtitleContentPath)) {
                File subtitleFile = DlsiteRemoteFiles.uniqueTarget(
                        new File(audioFile.getParentFile(), DlsiteRemoteFiles.safeFileName(contentFile.subtitleName)),
                        usedTargets);
                try {
                    downloadSignedSubtitleFile(
                            signedContentUrl(workId, contentFile.subtitleContentPath, revision, signParams),
                            subtitleFile);
                } catch (IOException ignored) {
                    DlsiteRemoteFiles.deleteQuietly(subtitleFile);
                    DlsiteRemoteFiles.deleteQuietly(new File(subtitleFile.getParentFile(), subtitleFile.getName() + ".part"));
                }
            }
        }
        return audioFiles;
    }

    private List<DlsiteJsonParser.ContentFile> selectedContentFiles(
            DlsiteJsonParser.DlsiteZiptree ziptree,
            String downloadOptionId) throws IOException {
        if (TextUtils.isEmpty(downloadOptionId)) {
            return ziptree.audioFiles;
        }
        for (DlsiteDownloadOption option : DlsiteDownloadPlanner.optionsFor(ziptree)) {
            if (downloadOptionId.equals(option.id)) {
                return option.audioFiles;
            }
        }
        throw new IOException("没有找到所选下载版本");
    }

    private String resolveDownloadUrl(DlsiteWork work) {
        if (!TextUtils.isEmpty(work.workId)) {
            return DlsiteRemoteConstants.PLAY_BASE_URL + "/api/v3/download?workno=" + work.workId;
        }
        return work.downloadUrl;
    }

    private void signDownloadCookies(String workId) throws IOException {
        get(
                DlsiteRemoteConstants.PLAY_DOWNLOAD_BASE_URL + "/api/v3/download/sign/cookie?workno=" + DlsiteRemoteFiles.encodeQueryValue(workId),
                DlsiteRemoteConstants.PLAY_BASE_URL + "/work/" + workId + "/tree",
                "application/json, text/plain, */*");
    }

    private DlsiteJsonParser.DlsiteZiptree fetchZiptree(String workId) throws IOException {
        long seconds = System.currentTimeMillis() / 1000L;
        long minuteBucket = seconds - seconds % 60L;
        String json = get(
                contentUrl(workId, "ziptree.json") + "?v=" + minuteBucket,
                DlsiteRemoteConstants.PLAY_BASE_URL + "/work/" + workId + "/tree",
                "application/json, text/plain, */*");
        return DlsiteJsonParser.parseZiptree(json);
    }

    private Map<String, String> fetchDownloadSignParams(String workId) throws IOException {
        String json = get(
                "/api/v3/download/sign/url?workno=" + DlsiteRemoteFiles.encodeQueryValue(workId),
                DlsiteRemoteConstants.PLAY_BASE_URL + "/work/" + workId + "/tree",
                "application/json, text/plain, */*");
        return DlsiteJsonParser.parseSignUrlParams(json);
    }

    private String signedContentUrl(String workId, String contentPath, String revision, Map<String, String> signParams) {
        StringBuilder builder = new StringBuilder(contentUrl(workId, contentPath));
        builder.append("?v=").append(DlsiteRemoteFiles.encodeQueryValue(revision));
        for (Map.Entry<String, String> entry : signParams.entrySet()) {
            builder.append('&')
                    .append(DlsiteRemoteFiles.encodeQueryValue(entry.getKey()))
                    .append('=')
                    .append(DlsiteRemoteFiles.encodeQueryValue(entry.getValue()));
        }
        return builder.toString();
    }

    private String contentUrl(String workId, String relativePath) {
        return DlsiteRemoteConstants.PLAY_DOWNLOAD_BASE_URL + contentBasePath(workId) + "/" + DlsiteRemoteFiles.encodePath(relativePath);
    }

    private String contentBasePath(String workId) {
        String site = sitePathForWorkId(workId);
        String prefix = workId.length() >= 2 ? workId.substring(0, 2) : workId;
        String digits = workId.length() > 2 ? workId.substring(2) : "0";
        int numericId;
        try {
            numericId = Integer.parseInt(digits);
        } catch (NumberFormatException exception) {
            numericId = 0;
        }
        int bucket = ((numericId + 999) / 1000) * 1000;
        String bucketName = prefix + String.format(Locale.US, "%0" + digits.length() + "d", bucket);
        return "/content/work/" + site + "/" + bucketName + "/" + workId;
    }

    private String sitePathForWorkId(String workId) {
        if (TextUtils.isEmpty(workId)) {
            return "doujin";
        }
        char first = workId.charAt(0);
        if (first == 'B') {
            return "books";
        }
        if (first == 'V') {
            return "professional";
        }
        return "doujin";
    }

    private void downloadSignedContentFile(
            String url,
            File targetFile,
            String accept,
            DlsiteJsonParser.ContentFile contentFile,
            DlsiteContentProgressListener progressListener) throws IOException {
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建下载目录");
        }
        File tempFile = new File(parent == null ? targetFile.getParentFile() : parent, targetFile.getName() + ".part");
        try (Response response = httpClient.execute(
                url,
                DlsiteRemoteConstants.PLAY_BASE_URL + "/library",
                accept,
                "GET",
                null,
                DlsiteRemoteConstants.CONNECT_TIMEOUT_MS,
                DlsiteRemoteConstants.READ_TIMEOUT_MS)) {
            int code = response.code();
            if (!response.isSuccessful()) {
                String body = DlsiteHttpClient.bodyString(response);
                throw new IOException(body.isEmpty() ? "下载失败: HTTP " + code : "下载失败: HTTP " + code + " " + body);
            }
            String contentType = response.header("Content-Type");
            if (contentType != null
                    && (contentType.contains("text/html") || contentType.contains("application/json"))) {
                String body = DlsiteHttpClient.bodyString(response);
                throw new IOException("DLsite 返回了网页或错误信息，未拿到媒体文件: " + DlsiteRemoteFiles.summarizeBody(body));
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new IOException("下载失败: 响应为空");
            }
            long totalBytes = responseBody.contentLength();
            long bytesDownloaded = 0L;
            try (InputStream input = new BufferedInputStream(responseBody.byteStream());
                 BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(tempFile))) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    DlsiteRemoteFiles.throwIfInterrupted();
                    output.write(buffer, 0, count);
                    bytesDownloaded += count;
                    if (progressListener != null) {
                        progressListener.onProgress(contentFile, bytesDownloaded, totalBytes);
                    }
                }
            }
        }

        if (DlsiteRemoteFiles.looksLikeHtml(tempFile)) {
            if (!tempFile.delete()) {
                tempFile.deleteOnExit();
            }
            throw new IOException("DLsite 返回了网页，未拿到媒体文件");
        }
        if (targetFile.exists() && !targetFile.delete()) {
            throw new IOException("无法替换旧文件");
        }
        if (!tempFile.renameTo(targetFile)) {
            throw new IOException("无法保存下载文件");
        }
    }

    private void downloadSignedSubtitleFile(String url, File targetFile) throws IOException {
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建下载目录");
        }
        File tempFile = new File(parent == null ? targetFile.getParentFile() : parent, targetFile.getName() + ".part");
        try (Response response = httpClient.execute(
                url,
                DlsiteRemoteConstants.PLAY_BASE_URL + "/library",
                "text/vtt,text/plain,application/json,*/*",
                "GET",
                null,
                DlsiteRemoteConstants.CONNECT_TIMEOUT_MS,
                DlsiteRemoteConstants.READ_TIMEOUT_MS)) {
            int code = response.code();
            if (!response.isSuccessful()) {
                String body = DlsiteHttpClient.bodyString(response);
                throw new IOException(body.isEmpty() ? "字幕下载失败: HTTP " + code : "字幕下载失败: HTTP " + code + " " + body);
            }

            String contentType = response.header("Content-Type");
            if (contentType != null && contentType.contains("text/html")) {
                String body = DlsiteHttpClient.bodyString(response);
                throw new IOException("DLsite 返回了网页，未拿到字幕文件: " + DlsiteRemoteFiles.summarizeBody(body));
            }

            boolean jsonSubtitle = contentType != null && contentType.contains("application/json");
            if (jsonSubtitle) {
                String body = DlsiteHttpClient.bodyString(response);
                DlsiteRemoteFiles.writeTextFile(tempFile, DlsiteJsonParser.parseWebvttJson(body));
            } else {
                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    throw new IOException("字幕下载失败: 响应为空");
                }
                try (InputStream input = new BufferedInputStream(responseBody.byteStream());
                     BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(tempFile))) {
                    byte[] buffer = new byte[16 * 1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        DlsiteRemoteFiles.throwIfInterrupted();
                        output.write(buffer, 0, count);
                    }
                }
                if (DlsiteRemoteFiles.looksLikeHtml(tempFile)) {
                    if (!tempFile.delete()) {
                        tempFile.deleteOnExit();
                    }
                    throw new IOException("DLsite 返回了网页，未拿到字幕文件");
                }
                if (DlsiteRemoteFiles.looksLikeJson(tempFile)) {
                    String body = DlsiteRemoteFiles.readTextFile(tempFile);
                    DlsiteRemoteFiles.writeTextFile(tempFile, DlsiteJsonParser.parseWebvttJson(body));
                }
            }
        }

        if (targetFile.exists() && !targetFile.delete()) {
            throw new IOException("无法替换旧字幕文件");
        }
        if (!tempFile.renameTo(targetFile)) {
            throw new IOException("无法保存字幕文件");
        }
    }

    private String get(String pathOrUrl, String referer, String accept) throws IOException {
        return httpClient.text(
                pathOrUrl,
                referer,
                accept,
                "GET",
                null,
                DlsiteRemoteConstants.CONNECT_TIMEOUT_MS,
                DlsiteRemoteConstants.READ_TIMEOUT_MS);
    }

}
