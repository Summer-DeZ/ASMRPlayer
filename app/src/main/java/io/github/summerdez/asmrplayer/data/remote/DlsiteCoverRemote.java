package io.github.summerdez.asmrplayer.data.remote;

import android.text.TextUtils;

import io.github.summerdez.asmrplayer.domain.model.DlsiteWork;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.Response;
import okhttp3.ResponseBody;

final class DlsiteCoverRemote {
    private final DlsiteHttpClient httpClient;

    DlsiteCoverRemote(DlsiteHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    File downloadCover(DlsiteWork work, File targetDir) throws IOException {
        if (work == null) {
            throw new IOException("没有找到封面地址");
        }
        DlsiteWork coverWork = work.withEnsuredCoverUrl();
        IOException firstFailure = null;
        if (!TextUtils.isEmpty(coverWork.coverUrl)) {
            try {
                return downloadCoverUrl(coverWork, coverWork.coverUrl, targetDir);
            } catch (IOException exception) {
                firstFailure = exception;
            }
        }

        String resolvedCoverUrl = resolveCoverUrl(coverWork);
        if (!TextUtils.isEmpty(resolvedCoverUrl) && !resolvedCoverUrl.equals(coverWork.coverUrl)) {
            return downloadCoverUrl(coverWork.withCoverUrl(resolvedCoverUrl), resolvedCoverUrl, targetDir);
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
        throw new IOException("没有找到封面地址");
    }

    private File downloadCoverUrl(DlsiteWork work, String coverUrl, File targetDir) throws IOException {
        if (TextUtils.isEmpty(coverUrl)) {
            throw new IOException("没有找到封面地址");
        }
        if (!targetDir.exists() && !targetDir.mkdirs() && !targetDir.isDirectory()) {
            throw new IOException("无法创建封面目录");
        }

        File tempFile = new File(targetDir, "cover.part");
        String contentType;
        try (Response response = httpClient.execute(
                coverUrl,
                TextUtils.isEmpty(work.detailUrl) ? DlsiteRemoteConstants.PLAY_BASE_URL + "/library" : work.detailUrl,
                "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*",
                "GET",
                null,
                DlsiteRemoteConstants.COVER_CONNECT_TIMEOUT_MS,
                DlsiteRemoteConstants.COVER_READ_TIMEOUT_MS)) {
            int code = response.code();
            if (!response.isSuccessful()) {
                String body = DlsiteHttpClient.bodyString(response);
                throw new IOException(body.isEmpty() ? "封面下载失败: HTTP " + code : "封面下载失败: HTTP " + code);
            }

            contentType = response.header("Content-Type");
            if (contentType != null
                    && (contentType.contains("text/html") || contentType.contains("application/json"))) {
                String body = DlsiteHttpClient.bodyString(response);
                throw new IOException("DLsite 返回了网页或错误信息，未拿到封面: " + summarizeBody(body));
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new IOException("封面下载失败: 响应为空");
            }
            try (InputStream input = new BufferedInputStream(responseBody.byteStream());
                 BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(tempFile))) {
                byte[] buffer = new byte[32 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    throwIfInterrupted();
                    output.write(buffer, 0, count);
                }
            }
        }

        File targetFile = new File(targetDir, "cover" + coverExtension(coverUrl, contentType));
        if (looksLikeHtml(tempFile) || looksLikeJson(tempFile)) {
            deleteQuietly(tempFile);
            throw new IOException("DLsite 返回了网页或错误信息，未拿到封面");
        }
        if (targetFile.exists() && !targetFile.delete()) {
            throw new IOException("无法替换旧封面");
        }
        if (!tempFile.renameTo(targetFile)) {
            throw new IOException("无法保存封面");
        }
        return targetFile;
    }

    private String resolveCoverUrl(DlsiteWork work) {
        String workId = work == null ? "" : work.workId;
        if (TextUtils.isEmpty(workId)) {
            return "";
        }

        try {
            String detailJson = get(
                    "/api/v3/work/" + encodeQueryValue(workId),
                    DlsiteRemoteConstants.PLAY_BASE_URL + "/library",
                    "application/json, text/plain, */*");
            DlsiteWork detail = DlsiteJsonParser.parseWorkDetail(detailJson);
            if (detail != null
                    && !TextUtils.isEmpty(detail.coverUrl)
                    && !detail.coverUrl.equals(work.coverUrl)) {
                return detail.coverUrl;
            }
        } catch (IOException ignored) {
        }

        for (String publicUrl : publicWorkUrls(workId)) {
            try {
                String html = getCoverPage(publicUrl);
                String coverUrl = DlsiteHtmlParser.findCoverUrl(html, publicUrl, workId);
                if (!TextUtils.isEmpty(coverUrl) && !coverUrl.equals(work.coverUrl)) {
                    return coverUrl;
                }
            } catch (IOException ignored) {
            }
        }
        return "";
    }

    private List<String> publicWorkUrls(String workId) {
        List<String> urls = new ArrayList<>();
        if (TextUtils.isEmpty(workId)) {
            return urls;
        }
        char first = Character.toUpperCase(workId.charAt(0));
        if (first == 'B') {
            urls.add("https://www.dlsite.com/books/work/=/product_id/" + workId + ".html");
            return urls;
        }
        if (first == 'V') {
            urls.add("https://www.dlsite.com/pro/work/=/product_id/" + workId + ".html");
            return urls;
        }
        urls.add("https://www.dlsite.com/maniax/work/=/product_id/" + workId + ".html");
        urls.add("https://www.dlsite.com/home/work/=/product_id/" + workId + ".html");
        return urls;
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

    private String getCoverPage(String url) throws IOException {
        return httpClient.text(
                url,
                DlsiteRemoteConstants.DL_SITE_COOKIE_URL,
                "text/html,application/xhtml+xml,*/*",
                "GET",
                null,
                DlsiteRemoteConstants.COVER_CONNECT_TIMEOUT_MS,
                DlsiteRemoteConstants.COVER_READ_TIMEOUT_MS);
    }

    private String encodeQueryValue(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name())
                    .replace("+", "%20");
        } catch (IOException exception) {
            return "";
        }
    }

    private String coverExtension(String url, String contentType) {
        String typeExtension = coverExtensionFromContentType(contentType);
        if (!typeExtension.isEmpty()) {
            return typeExtension;
        }
        String path = url == null ? "" : url;
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        int fragment = path.indexOf('#');
        if (fragment >= 0) {
            path = path.substring(0, fragment);
        }
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        if (dot > slash && dot < path.length() - 1) {
            String extension = path.substring(dot + 1).toLowerCase(Locale.US);
            if ("jpg".equals(extension)
                    || "jpeg".equals(extension)
                    || "png".equals(extension)
                    || "webp".equals(extension)
                    || "gif".equals(extension)
                    || "avif".equals(extension)) {
                return "." + extension;
            }
        }
        return ".jpg";
    }

    private String coverExtensionFromContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        String lower = contentType.toLowerCase(Locale.US);
        if (lower.contains("image/jpeg") || lower.contains("image/jpg")) {
            return ".jpg";
        }
        if (lower.contains("image/png")) {
            return ".png";
        }
        if (lower.contains("image/webp")) {
            return ".webp";
        }
        if (lower.contains("image/gif")) {
            return ".gif";
        }
        if (lower.contains("image/avif")) {
            return ".avif";
        }
        return "";
    }

    private boolean looksLikeHtml(File file) throws IOException {
        if (file == null || !file.isFile()) {
            return false;
        }
        byte[] header = new byte[(int) Math.min(128, file.length())];
        try (FileInputStream input = new FileInputStream(file)) {
            int count = input.read(header);
            if (count <= 0) {
                return false;
            }
            String text = new String(header, 0, count, StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
            return text.startsWith("<!doctype html") || text.startsWith("<html");
        }
    }

    private boolean looksLikeJson(File file) throws IOException {
        if (file == null || !file.isFile()) {
            return false;
        }
        byte[] header = new byte[(int) Math.min(128, file.length())];
        try (FileInputStream input = new FileInputStream(file)) {
            int count = input.read(header);
            if (count <= 0) {
                return false;
            }
            String text = new String(header, 0, count, StandardCharsets.UTF_8).trim();
            return text.startsWith("{") || text.startsWith("[");
        }
    }

    private void deleteQuietly(File file) {
        if (file != null && file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }

    private String summarizeBody(String body) {
        if (body == null) {
            return "";
        }
        String text = body.replaceAll("\\s+", " ").trim();
        return text.length() <= 160 ? text : text.substring(0, 160);
    }

    private void throwIfInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("下载已取消");
        }
    }
}
