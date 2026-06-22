package io.github.summerdez.asmrplayer.data.remote;

import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

final class DlsiteRemoteFiles {
    private DlsiteRemoteFiles() {
    }

    static String encodePath(String path) {
        if (TextUtils.isEmpty(path)) {
            return "";
        }
        String[] segments = path.split("/");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                builder.append('/');
            }
            builder.append(encodeQueryValue(segments[i]));
        }
        return builder.toString();
    }

    static String encodeQueryValue(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name())
                    .replace("+", "%20");
        } catch (IOException exception) {
            return "";
        }
    }

    static File localFileFor(File workDir, String relativePath) {
        String[] segments = relativePath == null ? new String[0] : relativePath.split("/");
        File current = workDir;
        for (String segment : segments) {
            String safe = safeFileName(segment);
            if (!safe.isEmpty()) {
                current = new File(current, safe);
            }
        }
        return current;
    }

    static File uniqueTarget(File targetFile, Set<String> usedTargets) {
        File candidate = targetFile;
        int index = 2;
        while (!usedTargets.add(candidate.getAbsolutePath())) {
            String name = targetFile.getName();
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            String extension = dot > 0 ? name.substring(dot) : "";
            candidate = new File(targetFile.getParentFile(), base + " " + index + extension);
            index++;
        }
        return candidate;
    }

    static String safeFileName(String value) {
        String safe = TextUtils.isEmpty(value) ? "download" : value.trim();
        safe = safe.replaceAll("[\\\\/:*?\"<>|]+", "_");
        if (safe.equals(".") || safe.equals("..")) {
            return "download";
        }
        return safe;
    }

    static boolean looksLikeHtml(File file) throws IOException {
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

    static boolean looksLikeJson(File file) throws IOException {
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

    static String readTextFile(File file) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            char[] buffer = new char[16 * 1024];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, count);
            }
        }
        return builder.toString();
    }

    static void writeTextFile(File file, String text) throws IOException {
        try (OutputStream output = new FileOutputStream(file)) {
            output.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
        }
    }

    static void deleteQuietly(File file) {
        if (file != null && file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }

    static String summarizeBody(String body) {
        if (body == null) {
            return "";
        }
        String text = body.replaceAll("\\s+", " ").trim();
        return text.length() <= 160 ? text : text.substring(0, 160);
    }

    static void throwIfInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("下载已取消");
        }
    }
}
