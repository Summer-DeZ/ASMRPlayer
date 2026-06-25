package io.github.summerdez.asmrplayer.data.download

import android.content.Context
import android.net.Uri
import android.text.TextUtils
import io.github.summerdez.asmrplayer.data.DlsiteApi
import io.github.summerdez.asmrplayer.data.files.DocumentFiles
import io.github.summerdez.asmrplayer.data.remote.DlsiteJsonParser
import io.github.summerdez.asmrplayer.domain.model.DlsiteDownloadOption
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import io.github.summerdez.asmrplayer.domain.model.TrackItem
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream

class DlsiteDownloadTask private constructor() {
    companion object {
        private const val BUFFER_SIZE = 64 * 1024

        @Throws(IOException::class)
        fun downloadAndImport(
            context: Context,
            dlsiteApi: DlsiteApi,
            repository: DlsiteDownloadBlockingAdapter,
            work: DlsiteWork,
        ): Result {
            return downloadAndImport(context, dlsiteApi, repository, work, "")
        }

        @Throws(IOException::class)
        fun downloadAndImport(
            context: Context,
            dlsiteApi: DlsiteApi,
            repository: DlsiteDownloadBlockingAdapter,
            work: DlsiteWork,
            downloadOptionId: String?,
        ): Result {
            val optionIds = ArrayList<String>()
            if (!TextUtils.isEmpty(downloadOptionId)) {
                optionIds.add(downloadOptionId!!)
            }
            return downloadAndImport(context, dlsiteApi, repository, work, optionIds, null)
        }

        @Throws(IOException::class)
        fun downloadAndImport(
            context: Context,
            dlsiteApi: DlsiteApi,
            repository: DlsiteDownloadBlockingAdapter,
            work: DlsiteWork,
            downloadOptionIds: List<String>?,
            listener: ContentListener?,
        ): Result {
            val importWork = work.withEnsuredCoverUrl()
            val workDir = File(context.filesDir, "dlsite/works/" + importWork.workId)

            if (!workDir.mkdirs() && !workDir.isDirectory) {
                throw IOException("无法创建作品目录")
            }

            val options = dlsiteApi.fetchDownloadOptions(importWork)
            val selectedOptions = selectedOptions(options, downloadOptionIds)
            if (selectedOptions.isEmpty()) {
                throw IOException("没有找到可下载的内容")
            }
            val progressTracker = DownloadProgressTracker(selectedOptions)

            val importedAudioFiles = ArrayList<File>()
            val downloadedContents = ArrayList<DownloadedContent>()
            for (option in selectedOptions) {
                throwIfInterrupted()
                val contentDir = contentDir(workDir, option)
                val marker = File(contentDir, ".downloaded")
                val audioFiles: List<File>
                if (listener != null) {
                    listener.onContentStarted(option, contentDir)
                    val progress = progressTracker.snapshot()
                    listener.onContentProgress(option, null, progress.bytesDownloaded, progress.totalBytes)
                }
                if (marker.isFile) {
                    audioFiles = audioFilesIn(contentDir)
                    progressTracker.markOptionComplete(option, audioFiles)
                    if (listener != null) {
                        val progress = progressTracker.snapshot()
                        listener.onContentProgress(option, null, progress.bytesDownloaded, progress.totalBytes)
                    }
                } else {
                    deleteRecursively(contentDir)
                    if (!contentDir.mkdirs() && !contentDir.isDirectory) {
                        throw IOException("无法创建内容目录")
                    }
                    audioFiles = dlsiteApi.downloadWorkFiles(
                        importWork,
                        contentDir,
                        option.id,
                    ) { contentFile, fileBytesDownloaded, fileTotalBytes ->
                        val progress = progressTracker.onFileProgress(
                            option,
                            contentFile,
                            fileBytesDownloaded,
                            fileTotalBytes,
                        )
                        if (listener != null) {
                            listener.onContentProgress(
                                option,
                                contentFile,
                                progress.bytesDownloaded,
                                progress.totalBytes,
                            )
                        }
                    }
                    if (audioFiles.isEmpty()) {
                        throw IOException("没有找到可导入的音频")
                    }
                    progressTracker.markOptionComplete(option, audioFiles)
                    if (listener != null) {
                        val progress = progressTracker.snapshot()
                        listener.onContentProgress(option, null, progress.bytesDownloaded, progress.totalBytes)
                    }
                    if (!marker.createNewFile() && !marker.isFile) {
                        throw IOException("无法写入内容下载标记")
                    }
                }
                if (audioFiles.isEmpty()) {
                    throw IOException("没有找到可导入的音频")
                }
                importedAudioFiles.addAll(audioFiles)
                downloadedContents.add(DownloadedContent(option, contentDir, audioFiles))
            }

            var coverFile = existingCoverFile(importWork)
            if (coverFile == null) {
                coverFile = downloadCover(dlsiteApi, importWork, workDir)
            }
            var coverUri = ""
            if (coverFile != null && coverFile.isFile) {
                coverUri = Uri.fromFile(coverFile).toString()
            }
            val importResult = importPlaylist(context, repository, importWork, importedAudioFiles, coverFile)
            val contentResults = contentResultsForImport(downloadedContents, importResult)
            for (i in downloadedContents.indices) {
                listener?.onContentFinished(downloadedContents[i].option, contentResults[i])
            }
            return Result(
                importResult.playlistId,
                workDir.absolutePath,
                importResult.totalTrackCount,
                coverUri,
                contentResults,
            )
        }

        @Throws(IOException::class)
        fun deleteCache(context: Context, work: DlsiteWork) {
            if (TextUtils.isEmpty(work.workId)) {
                return
            }
            val workDir = File(context.filesDir, "dlsite/works/" + work.workId)
            deleteRecursively(workDir)
        }

        @Throws(IOException::class)
        fun deleteContentCache(context: Context, work: DlsiteWork, optionId: String?) {
            if (TextUtils.isEmpty(work.workId)) {
                return
            }
            val workDir = File(context.filesDir, "dlsite/works/" + work.workId)
            deleteRecursively(File(File(workDir, "contents"), safeContentId(optionId)))
        }

        private fun importPlaylist(
            context: Context,
            repository: DlsiteDownloadBlockingAdapter,
            work: DlsiteWork,
            audioFiles: List<File>,
            coverFile: File?,
        ): ImportResult {
            val playlist = repository.getPlaylist(work.playlistId) ?: repository.createPlaylist(work.displayTitle())
            if (coverFile != null && coverFile.isFile) {
                repository.setPlaylistCover(playlist.id, Uri.fromFile(coverFile).toString())
            }
            val existingUris = HashSet<String>()
            val existingTrackIdsByUri = HashMap<String, String>()
            for (track in playlist.tracks) {
                existingUris.add(track.uri)
                existingTrackIdsByUri[track.uri] = track.id
            }
            val subtitles = subtitleFilesByName(audioFiles)
            val addedTrackIds = ArrayList<String>()
            val trackIdsByPath = HashMap<String, String>()
            for (audioFile in audioFiles) {
                val audioUri = Uri.fromFile(audioFile).toString()
                if (existingUris.contains(audioUri)) {
                    val existingTrackId = existingTrackIdsByUri[audioUri]
                    if (!TextUtils.isEmpty(existingTrackId)) {
                        trackIdsByPath[audioFile.absolutePath] = existingTrackId!!
                    }
                    continue
                }
                val subtitleFile = subtitles[normalizedName(audioFile.name + ".vtt")]
                val trackId = UUID.randomUUID().toString()
                repository.addTrack(
                    playlist.id,
                    TrackItem(
                        trackId,
                        audioFile.name,
                        audioUri,
                        if (subtitleFile == null) "" else Uri.fromFile(subtitleFile).toString(),
                        subtitleFile?.name ?: "",
                        DocumentFiles.audioDurationMs(context, Uri.fromFile(audioFile)),
                    ),
                )
                addedTrackIds.add(trackId)
                existingUris.add(audioUri)
                existingTrackIdsByUri[audioUri] = trackId
                trackIdsByPath[audioFile.absolutePath] = trackId
            }
            val updated = repository.getPlaylist(playlist.id)
            return ImportResult(
                playlist.id,
                addedTrackIds,
                updated?.tracks?.size ?: (playlist.tracks.size + addedTrackIds.size),
                trackIdsByPath,
            )
        }

        fun contentResultsForImport(
            downloadedContents: List<DownloadedContent>,
            importResult: ImportResult,
        ): List<ContentResult> {
            val contentResults = ArrayList<ContentResult>()
            val trackIdsByPath = importResult.trackIdsByPath
            for (content in downloadedContents) {
                val trackIds = ArrayList<String>()
                for (audioFile in content.audioFiles) {
                    val trackId = trackIdsByPath[audioFile.absolutePath]
                    if (trackId != null && trackId.isNotEmpty()) {
                        trackIds.add(trackId)
                    }
                }
                contentResults.add(
                    ContentResult(
                        content.option.id,
                        content.option.title,
                        content.contentDir.absolutePath,
                        trackIds,
                        content.audioFiles.size,
                    ),
                )
            }
            return contentResults
        }

        @Throws(IOException::class)
        private fun selectedOptions(
            options: List<DlsiteDownloadOption>,
            downloadOptionIds: List<String>?,
        ): List<DlsiteDownloadOption> {
            if (options.isEmpty()) {
                return emptyList()
            }
            if (downloadOptionIds == null || downloadOptionIds.isEmpty()) {
                return options
            }
            val ids = HashSet(downloadOptionIds)
            val selected = ArrayList<DlsiteDownloadOption>()
            for (option in options) {
                if (ids.contains(option.id)) {
                    selected.add(option)
                }
            }
            if (selected.isEmpty()) {
                throw IOException("没有找到所选下载内容")
            }
            return selected
        }

        private fun contentDir(workDir: File, option: DlsiteDownloadOption): File {
            return File(File(workDir, "contents"), safeContentId(option.id))
        }

        private fun safeContentId(optionId: String?): String {
            val id = optionId?.takeIf { it.isNotEmpty() } ?: "default"
            return safeFileName(id)
        }

        private fun downloadCover(dlsiteApi: DlsiteApi, work: DlsiteWork, workDir: File): File? {
            if (TextUtils.isEmpty(work.coverUrl)) {
                return null
            }
            return try {
                dlsiteApi.downloadCover(work, File(workDir, "cover"))
            } catch (ignored: IOException) {
                null
            }
        }

        private fun existingCoverFile(work: DlsiteWork): File? {
            if (TextUtils.isEmpty(work.coverUri)) {
                return null
            }
            return try {
                val uri = Uri.parse(work.coverUri)
                if ("file" != uri.scheme || TextUtils.isEmpty(uri.path)) {
                    return null
                }
                val file = File(uri.path!!)
                if (file.isFile) file else null
            } catch (exception: RuntimeException) {
                null
            }
        }

        private fun subtitleFilesByName(audioFiles: List<File>): Map<String, File> {
            val subtitles = HashMap<String, File>()
            val roots = ArrayList<File>()
            for (audioFile in audioFiles) {
                val parent = audioFile.parentFile
                if (parent != null && !roots.contains(parent)) {
                    roots.add(parent)
                }
            }
            for (root in roots) {
                val children = root.listFiles() ?: continue
                for (child in children) {
                    if (child.isFile && normalizedName(child.name).endsWith(".vtt")) {
                        subtitles[normalizedName(child.name)] = child
                    }
                }
            }
            return subtitles
        }

        @Throws(IOException::class)
        private fun unzipWithFallback(zipFile: File, destinationDir: File) {
            try {
                unzip(zipFile, destinationDir, StandardCharsets.UTF_8)
            } catch (firstFailure: IllegalArgumentException) {
                deleteChildren(destinationDir)
                unzip(zipFile, destinationDir, Charset.forName("Shift_JIS"))
            }
        }

        @Throws(IOException::class)
        private fun unzip(zipFile: File, destinationDir: File, charset: Charset) {
            val destinationPath = destinationDir.canonicalPath + File.separator
            ZipInputStream(
                BufferedInputStream(FileInputStream(zipFile)),
                charset,
            ).use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
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
        private fun isZipArchive(file: File): Boolean {
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
        private fun copySingleFile(source: File, target: File) {
            val parent = target.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw IOException("无法创建目录")
            }
            FileInputStream(source).use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
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

        private fun audioFilesIn(root: File): List<File> {
            val files = ArrayList<File>()
            collectAudioFiles(root, files)
            files.sortWith { left, right ->
                String.CASE_INSENSITIVE_ORDER.compare(left.absolutePath, right.absolutePath)
            }
            return files
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

        @Throws(IOException::class)
        private fun deleteRecursively(file: File?) {
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
        private fun deleteChildren(directory: File) {
            val children = directory.listFiles() ?: return
            for (child in children) {
                deleteRecursively(child)
            }
        }

        private fun safeFileName(value: String): String {
            val safe = value.ifEmpty { "download" }
            return safe.replace("[\\\\/:*?\"<>|]+".toRegex(), "_")
        }

        private fun normalizedName(value: String): String {
            return value.trim().lowercase(Locale.ROOT)
        }

        @Throws(InterruptedIOException::class)
        private fun throwIfInterrupted() {
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedIOException("下载已中断")
            }
        }
    }

    class Result(
        val playlistId: String,
        val localPath: String,
        val trackCount: Int,
        val coverUri: String,
        contentResults: List<ContentResult> = emptyList(),
    ) {
        val contentResults: List<ContentResult> = ArrayList(contentResults)
    }

    interface ContentListener {
        fun onContentStarted(option: DlsiteDownloadOption?, contentDir: File?)

        fun onContentProgress(
            option: DlsiteDownloadOption?,
            contentFile: DlsiteJsonParser.ContentFile?,
            bytesDownloaded: Long,
            totalBytes: Long,
        )

        fun onContentFinished(option: DlsiteDownloadOption?, result: ContentResult?)
    }

    class TaskProgress(bytesDownloaded: Long, totalBytes: Long) {
        val bytesDownloaded: Long = bytesDownloaded.coerceAtLeast(0L)

        val totalBytes: Long = totalBytes
    }

    class DownloadProgressTracker(options: List<DlsiteDownloadOption>) {
        private val entries = ArrayList<ProgressEntry>()
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
                    entries.add(ProgressEntry(option.id, file, lengthBytes))
                }
            }
            totalBytes = if (hasFiles && !hasUnknownLength && total > 0L) total else -1L
        }

        fun snapshot(): TaskProgress {
            return TaskProgress(bytesDownloaded(), totalBytes)
        }

        fun onFileProgress(
            option: DlsiteDownloadOption,
            contentFile: DlsiteJsonParser.ContentFile,
            fileBytesDownloaded: Long,
            fileTotalBytes: Long,
        ): TaskProgress {
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
            contentFile: DlsiteJsonParser.ContentFile,
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
            left: DlsiteJsonParser.ContentFile,
            right: DlsiteJsonParser.ContentFile,
        ): Boolean {
            return left.contentPath == right.contentPath &&
                left.displayPath == right.displayPath
        }

        private fun matchingLocalLength(
            contentFile: DlsiteJsonParser.ContentFile,
            audioFiles: List<File>,
        ): Long {
            for (audioFile in audioFiles) {
                if (audioFile.isFile &&
                    audioFile.name == Companion.safeFileName(contentFile.displayName)
                ) {
                    return audioFile.length()
                }
            }
            return 0L
        }
    }

    private class ProgressEntry(
        val optionId: String,
        val contentFile: DlsiteJsonParser.ContentFile,
        lengthBytes: Long,
    ) {
        val lengthBytes: Long = lengthBytes.coerceAtLeast(0L)
        var bytesDownloaded: Long = 0L
        var responseTotalBytes: Long = -1L
    }

    class ContentResult(
        val optionId: String,
        val title: String,
        val localPath: String,
        trackIds: List<String>,
        val trackCount: Int,
    ) {
        val trackIds: List<String> = ArrayList(trackIds)
    }

    class DownloadedContent(
        val option: DlsiteDownloadOption,
        val contentDir: File,
        audioFiles: List<File>,
    ) {
        val audioFiles: List<File> = ArrayList(audioFiles)
    }

    class ImportResult(
        val playlistId: String,
        addedTrackIds: List<String>,
        val totalTrackCount: Int,
        trackIdsByPath: Map<String, String> = emptyMap(),
    ) {
        val addedTrackIds: List<String> = ArrayList(addedTrackIds)

        val trackIdsByPath: Map<String, String> = HashMap(trackIdsByPath)
    }
}
