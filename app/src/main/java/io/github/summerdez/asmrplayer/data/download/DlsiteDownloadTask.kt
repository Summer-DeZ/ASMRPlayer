package io.github.summerdez.asmrplayer.data.download

import android.content.Context
import android.net.Uri
import io.github.summerdez.asmrplayer.data.DlsiteApi
import io.github.summerdez.asmrplayer.domain.model.DlsiteContentFile
import io.github.summerdez.asmrplayer.domain.model.DlsiteDownloadOption
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException

class DlsiteDownloadTask private constructor() {
    companion object {
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
            downloadOptionId?.takeIf { it.isNotEmpty() }?.let(optionIds::add)
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
            val workDir = DlsiteDownloadFiles.workDir(context, importWork)

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
                val contentDir = DlsiteDownloadFiles.contentDir(workDir, option)
                val marker = File(contentDir, ".downloaded")
                val audioFiles: List<File>
                if (listener != null) {
                    listener.onContentStarted(option, contentDir)
                    val progress = progressTracker.snapshot()
                    listener.onContentProgress(option, null, progress.bytesDownloaded, progress.totalBytes)
                }
                if (marker.isFile) {
                    audioFiles = DlsiteDownloadFiles.audioFilesIn(contentDir)
                    progressTracker.markOptionComplete(option, audioFiles)
                    if (listener != null) {
                        val progress = progressTracker.snapshot()
                        listener.onContentProgress(option, null, progress.bytesDownloaded, progress.totalBytes)
                    }
                } else {
                    DlsiteDownloadFiles.deleteRecursively(contentDir)
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

            var coverFile = DlsiteDownloadCoverHandler.existingCoverFile(importWork)
            if (coverFile == null) {
                coverFile = DlsiteDownloadCoverHandler.downloadCover(dlsiteApi, importWork, workDir)
            }
            var coverUri = ""
            if (coverFile != null && coverFile.isFile) {
                coverUri = Uri.fromFile(coverFile).toString()
            }
            val importResult = DlsiteDownloadImportHelper.importPlaylist(
                context,
                repository,
                importWork,
                importedAudioFiles,
                coverFile,
            )
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
            DlsiteDownloadFiles.deleteCache(context, work)
        }

        @Throws(IOException::class)
        fun deleteContentCache(context: Context, work: DlsiteWork, optionId: String?) {
            DlsiteDownloadFiles.deleteContentCache(context, work, optionId)
        }

        fun contentResultsForImport(
            downloadedContents: List<DownloadedContent>,
            importResult: ImportResult,
        ): List<ContentResult> {
            return DlsiteDownloadImportHelper.contentResultsForImport(downloadedContents, importResult)
        }

        @Throws(IOException::class)
        private fun selectedOptions(
            options: List<DlsiteDownloadOption>,
            downloadOptionIds: List<String>?,
        ): List<DlsiteDownloadOption> {
            return DlsiteDownloadImportHelper.selectedOptions(options, downloadOptionIds)
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
        fun onContentStarted(option: DlsiteDownloadOption, contentDir: File)

        fun onContentProgress(
            option: DlsiteDownloadOption,
            contentFile: DlsiteContentFile?,
            bytesDownloaded: Long,
            totalBytes: Long,
        )

        fun onContentFinished(option: DlsiteDownloadOption, result: ContentResult)
    }

    class TaskProgress(bytesDownloaded: Long, totalBytes: Long) {
        val bytesDownloaded: Long = bytesDownloaded.coerceAtLeast(0L)

        val totalBytes: Long = totalBytes
    }

    class DownloadProgressTracker(options: List<DlsiteDownloadOption>) {
        private val delegate = DlsiteDownloadProgressTracker(options)

        fun snapshot(): TaskProgress {
            return delegate.snapshot()
        }

        fun onFileProgress(
            option: DlsiteDownloadOption,
            contentFile: DlsiteContentFile,
            fileBytesDownloaded: Long,
            fileTotalBytes: Long,
        ): TaskProgress {
            return delegate.onFileProgress(option, contentFile, fileBytesDownloaded, fileTotalBytes)
        }

        fun markOptionComplete(option: DlsiteDownloadOption, audioFiles: List<File>) {
            delegate.markOptionComplete(option, audioFiles)
        }
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
