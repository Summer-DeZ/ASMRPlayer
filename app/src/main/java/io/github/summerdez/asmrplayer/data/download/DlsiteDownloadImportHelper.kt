package io.github.summerdez.asmrplayer.data.download

import android.content.Context
import android.net.Uri
import io.github.summerdez.asmrplayer.data.files.DocumentFiles
import io.github.summerdez.asmrplayer.domain.model.DlsiteDownloadOption
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import io.github.summerdez.asmrplayer.domain.model.TrackItem
import java.io.File
import java.io.IOException
import java.util.UUID

internal object DlsiteDownloadImportHelper {
    fun importPlaylist(
        context: Context,
        repository: DlsiteDownloadBlockingAdapter,
        work: DlsiteWork,
        audioFiles: List<File>,
        coverFile: File?,
    ): DlsiteDownloadTask.ImportResult {
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
                if (!existingTrackId.isNullOrEmpty()) {
                    trackIdsByPath[audioFile.absolutePath] = existingTrackId
                }
                continue
            }
            val subtitleFile = subtitles[DlsiteDownloadFiles.normalizedName(audioFile.name + ".vtt")]
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
        return DlsiteDownloadTask.ImportResult(
            playlist.id,
            addedTrackIds,
            updated?.tracks?.size ?: (playlist.tracks.size + addedTrackIds.size),
            trackIdsByPath,
        )
    }

    fun contentResultsForImport(
        downloadedContents: List<DlsiteDownloadTask.DownloadedContent>,
        importResult: DlsiteDownloadTask.ImportResult,
    ): List<DlsiteDownloadTask.ContentResult> {
        val contentResults = ArrayList<DlsiteDownloadTask.ContentResult>()
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
                DlsiteDownloadTask.ContentResult(
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
    fun selectedOptions(
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
                if (child.isFile && DlsiteDownloadFiles.normalizedName(child.name).endsWith(".vtt")) {
                    subtitles[DlsiteDownloadFiles.normalizedName(child.name)] = child
                }
            }
        }
        return subtitles
    }
}
