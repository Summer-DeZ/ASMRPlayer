package io.github.summerdez.asmrplayer.data.remote

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.InterruptedIOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.regex.Pattern

object DlsiteRemoteFiles {
    private val pathSeparator: Pattern = Pattern.compile("/")

    fun encodePath(path: String?): String {
        if (path.isNullOrEmpty()) {
            return ""
        }
        val segments = pathSeparator.split(path)
        val builder = StringBuilder()
        for (i in segments.indices) {
            if (i > 0) {
                builder.append('/')
            }
            builder.append(encodeQueryValue(segments[i]))
        }
        return builder.toString()
    }

    fun encodeQueryValue(value: String?): String {
        return try {
            URLEncoder.encode(value ?: "", StandardCharsets.UTF_8.name())
                .replace("+", "%20")
        } catch (exception: IOException) {
            ""
        }
    }

    fun localFileFor(workDir: File?, relativePath: String?): File? {
        val segments = if (relativePath == null) emptyArray() else pathSeparator.split(relativePath)
        var current = workDir
        for (segment in segments) {
            val safe = safeFileName(segment)
            if (safe.isNotEmpty()) {
                current = File(current, safe)
            }
        }
        return current
    }

    fun uniqueTarget(targetFile: File?, usedTargets: MutableSet<String>?): File {
        val source = targetFile ?: throw NullPointerException("targetFile")
        val targets = usedTargets ?: throw NullPointerException("usedTargets")
        var candidate = source
        var index = 2
        while (!targets.add(candidate.absolutePath)) {
            val name = source.name
            val dot = name.lastIndexOf('.')
            val base = if (dot > 0) name.substring(0, dot) else name
            val extension = if (dot > 0) name.substring(dot) else ""
            candidate = File(source.parentFile, "$base $index$extension")
            index++
        }
        return candidate
    }

    fun safeFileName(value: String?): String {
        var safe = if (value.isNullOrEmpty()) "download" else value.trim { it <= ' ' }
        safe = safe.replace(Regex("[\\\\/:*?\"<>|]+"), "_")
        if (safe == "." || safe == "..") {
            return "download"
        }
        return safe
    }

    @Throws(IOException::class)
    fun looksLikeHtml(file: File?): Boolean {
        if (file == null || !file.isFile) {
            return false
        }
        val header = ByteArray(minOf(128L, file.length()).toInt())
        FileInputStream(file).use { input ->
            val count = input.read(header)
            if (count <= 0) {
                return false
            }
            val text = String(header, 0, count, StandardCharsets.UTF_8)
                .trim { it <= ' ' }
                .lowercase(Locale.ROOT)
            return text.startsWith("<!doctype html") || text.startsWith("<html")
        }
    }

    @Throws(IOException::class)
    fun looksLikeJson(file: File?): Boolean {
        if (file == null || !file.isFile) {
            return false
        }
        val header = ByteArray(minOf(128L, file.length()).toInt())
        FileInputStream(file).use { input ->
            val count = input.read(header)
            if (count <= 0) {
                return false
            }
            val text = String(header, 0, count, StandardCharsets.UTF_8).trim { it <= ' ' }
            return text.startsWith("{") || text.startsWith("[")
        }
    }

    @Throws(IOException::class)
    fun readTextFile(file: File?): String {
        val builder = StringBuilder()
        BufferedReader(InputStreamReader(FileInputStream(file), StandardCharsets.UTF_8)).use { reader ->
            val buffer = CharArray(16 * 1024)
            while (true) {
                val count = reader.read(buffer)
                if (count == -1) {
                    break
                }
                builder.append(buffer, 0, count)
            }
        }
        return builder.toString()
    }

    @Throws(IOException::class)
    fun writeTextFile(file: File?, text: String?) {
        FileOutputStream(file).use { output ->
            output.write((text ?: "").toByteArray(StandardCharsets.UTF_8))
        }
    }

    fun deleteQuietly(file: File?) {
        if (file != null && file.exists() && !file.delete()) {
            file.deleteOnExit()
        }
    }

    fun summarizeBody(body: String?): String {
        if (body == null) {
            return ""
        }
        val text = body.replace(Regex("\\s+"), " ").trim { it <= ' ' }
        return if (text.length <= 160) text else text.substring(0, 160)
    }

    @Throws(InterruptedIOException::class)
    fun throwIfInterrupted() {
        if (Thread.currentThread().isInterrupted) {
            throw InterruptedIOException("下载已取消")
        }
    }
}
