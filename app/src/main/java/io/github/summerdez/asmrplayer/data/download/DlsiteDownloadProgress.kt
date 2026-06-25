package io.github.summerdez.asmrplayer.data.download

import io.github.summerdez.asmrplayer.domain.model.DlsiteContentFile
import io.github.summerdez.asmrplayer.domain.model.DlsiteDownloadOption
import java.io.File

internal class DlsiteDownloadProgressTracker(options: List<DlsiteDownloadOption>) {
    private val entries = ArrayList<DlsiteDownloadProgressEntry>()
    private val totalBytes: Long

    init {
        var total = 0L
        var hasFiles = false
        var hasUnknownLength = false
        for (option in options) {
            for (file in option.audioFiles) {
                hasFiles = true
                val lengthBytes = file.lengthBytes
                if (lengthBytes <= 0L) {
                    hasUnknownLength = true
                } else {
                    total += lengthBytes
                }
                entries.add(DlsiteDownloadProgressEntry(option.id, file, lengthBytes))
            }
        }
        totalBytes = if (hasFiles && !hasUnknownLength && total > 0L) total else -1L
    }

    fun snapshot(): DlsiteDownloadTask.TaskProgress {
        return DlsiteDownloadTask.TaskProgress(bytesDownloaded(), totalBytes)
    }

    fun onFileProgress(
        option: DlsiteDownloadOption,
        contentFile: DlsiteContentFile,
        fileBytesDownloaded: Long,
        fileTotalBytes: Long,
    ): DlsiteDownloadTask.TaskProgress {
        val index = indexOf(option, contentFile)
        if (index >= 0) {
            val entry = entries[index]
            entry.bytesDownloaded = maxOf(entry.bytesDownloaded, maxOf(0L, fileBytesDownloaded))
            entry.responseTotalBytes = maxOf(entry.responseTotalBytes, fileTotalBytes)
        }
        return snapshot()
    }

    fun markOptionComplete(option: DlsiteDownloadOption, audioFiles: List<File>) {
        for (entry in entries) {
            if (entry.optionId != option.id) {
                continue
            }
            var completedBytes = entry.lengthBytes
            if (completedBytes <= 0L) {
                completedBytes = entry.responseTotalBytes
            }
            if (completedBytes <= 0L) {
                completedBytes = matchingLocalLength(entry.contentFile, audioFiles)
            }
            if (completedBytes > 0L) {
                entry.bytesDownloaded = maxOf(entry.bytesDownloaded, completedBytes)
            }
        }
    }

    private fun bytesDownloaded(): Long {
        var downloaded = 0L
        for (entry in entries) {
            var bytes = maxOf(0L, entry.bytesDownloaded)
            if (totalBytes > 0L && entry.lengthBytes > 0L) {
                bytes = minOf(bytes, entry.lengthBytes)
            }
            downloaded += bytes
        }
        return if (totalBytes > 0L) minOf(downloaded, totalBytes) else downloaded
    }

    private fun indexOf(
        option: DlsiteDownloadOption,
        contentFile: DlsiteContentFile,
    ): Int {
        for (i in entries.indices) {
            val entry = entries[i]
            if (entry.optionId == option.id && sameContentFile(entry.contentFile, contentFile)) {
                return i
            }
        }
        return -1
    }

    private fun sameContentFile(
        left: DlsiteContentFile,
        right: DlsiteContentFile,
    ): Boolean {
        return left.contentPath == right.contentPath &&
            left.displayPath == right.displayPath
    }

    private fun matchingLocalLength(
        contentFile: DlsiteContentFile,
        audioFiles: List<File>,
    ): Long {
        for (audioFile in audioFiles) {
            if (audioFile.isFile &&
                audioFile.name == DlsiteDownloadFiles.safeFileName(contentFile.displayName)
            ) {
                return audioFile.length()
            }
        }
        return 0L
    }
}

private class DlsiteDownloadProgressEntry(
    val optionId: String,
    val contentFile: DlsiteContentFile,
    lengthBytes: Long,
) {
    val lengthBytes: Long = lengthBytes.coerceAtLeast(0L)
    var bytesDownloaded: Long = 0L
    var responseTotalBytes: Long = -1L
}
