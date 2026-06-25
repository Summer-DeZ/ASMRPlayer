package io.github.summerdez.asmrplayer.data.download

import android.content.Context
import android.text.TextUtils
import io.github.summerdez.asmrplayer.data.files.DocumentFiles
import io.github.summerdez.asmrplayer.domain.model.DlsiteDownloadOption
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipInputStream

private const val DLSITE_DOWNLOAD_BUFFER_SIZE = 64 * 1024

internal object DlsiteDownloadFiles {
    @Throws(IOException::class)
    fun deleteCache(context: Context, work: DlsiteWork) {
        if (TextUtils.isEmpty(work.workId)) {
            return
        }
        deleteRecursively(workDir(context, work))
    }

    @Throws(IOException::class)
    fun deleteContentCache(context: Context, work: DlsiteWork, optionId: String?) {
        if (TextUtils.isEmpty(work.workId)) {
            return
        }
        val workDir = workDir(context, work)
        deleteRecursively(File(File(workDir, "contents"), safeContentId(optionId)))
    }

    fun workDir(context: Context, work: DlsiteWork): File {
        return File(context.filesDir, "dlsite/works/" + work.workId)
    }

    fun contentDir(workDir: File, option: DlsiteDownloadOption): File {
        return File(File(workDir, "contents"), safeContentId(option.id))
    }

    fun audioFilesIn(root: File): List<File> {
        val files = ArrayList<File>()
        collectAudioFiles(root, files)
        files.sortWith { left, right ->
            String.CASE_INSENSITIVE_ORDER.compare(left.absolutePath, right.absolutePath)
        }
        return files
    }

    @Throws(IOException::class)
    fun deleteRecursively(file: File?) {
        if (file == null || !file.exists()) {
            return
        }
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    deleteRecursively(child)
                }
            }
        }
        if (!file.delete()) {
            throw IOException("无法清理旧文件")
        }
    }

    @Throws(IOException::class)
    fun deleteChildren(directory: File) {
        val children = directory.listFiles() ?: return
        for (child in children) {
            deleteRecursively(child)
        }
    }

    @Throws(IOException::class)
    fun unzipWithFallback(zipFile: File, destinationDir: File) {
        try {
            unzip(zipFile, destinationDir, StandardCharsets.UTF_8)
        } catch (firstFailure: IllegalArgumentException) {
            deleteChildren(destinationDir)
            unzip(zipFile, destinationDir, Charset.forName("Shift_JIS"))
        }
    }

    @Throws(IOException::class)
    fun unzip(zipFile: File, destinationDir: File, charset: Charset) {
        val destinationPath = destinationDir.canonicalPath + File.separator
        ZipInputStream(
            BufferedInputStream(FileInputStream(zipFile)),
            charset,
        ).use { input ->
            val buffer = ByteArray(DLSITE_DOWNLOAD_BUFFER_SIZE)
            while (true) {
                val entry = input.nextEntry ?: break
                val outputFile = File(destinationDir, entry.name)
                val outputPath = outputFile.canonicalPath
                if (!outputPath.startsWith(destinationPath)) {
                    throw IOException("压缩包路径不安全")
                }
                if (entry.isDirectory) {
                    if (!outputFile.mkdirs() && !outputFile.isDirectory) {
                        throw IOException("无法创建目录")
                    }
                    continue
                }
                val parent = outputFile.parentFile
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw IOException("无法创建目录")
                }
                BufferedOutputStream(FileOutputStream(outputFile)).use { output ->
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) {
                            break
                        }
                        output.write(buffer, 0, count)
                    }
                }
                input.closeEntry()
            }
        }
    }

    @Throws(IOException::class)
    fun isZipArchive(file: File): Boolean {
        val header = ByteArray(4)
        FileInputStream(file).use { input ->
            val read = input.read(header)
            return read == 4 &&
                header[0] == 'P'.code.toByte() &&
                header[1] == 'K'.code.toByte() &&
                (header[2] == 3.toByte() || header[2] == 5.toByte() || header[2] == 7.toByte()) &&
                (header[3] == 4.toByte() || header[3] == 6.toByte() || header[3] == 8.toByte())
        }
    }

    @Throws(IOException::class)
    fun copySingleFile(source: File, target: File) {
        val parent = target.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw IOException("无法创建目录")
        }
        FileInputStream(source).use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(DLSITE_DOWNLOAD_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) {
                        break
                    }
                    output.write(buffer, 0, count)
                }
            }
        }
    }

    fun safeFileName(value: String): String {
        val safe = value.ifEmpty { "download" }
        return safe.replace("[\\\\/:*?\"<>|]+".toRegex(), "_")
    }

    fun normalizedName(value: String): String {
        return value.trim().lowercase(Locale.ROOT)
    }

    private fun safeContentId(optionId: String?): String {
        val id = optionId?.takeIf { it.isNotEmpty() } ?: "default"
        return safeFileName(id)
    }

    private fun collectAudioFiles(file: File?, output: MutableList<File>) {
        if (file == null || !file.exists()) {
            return
        }
        if (file.isFile) {
            if (DocumentFiles.isSupportedAudioName(file.name, "")) {
                output.add(file)
            }
            return
        }
        val children = file.listFiles() ?: return
        for (child in children) {
            collectAudioFiles(child, output)
        }
    }
}
