package io.github.summerdez.asmrplayer.data

import io.github.summerdez.asmrplayer.domain.model.DlsiteContent
import io.github.summerdez.asmrplayer.domain.model.DlsiteDownloadQueueTask
import io.github.summerdez.asmrplayer.domain.model.DlsiteWork
import io.github.summerdez.asmrplayer.domain.model.Playlist
import io.github.summerdez.asmrplayer.domain.model.TrackItem
internal fun PlaylistEntity.toPlaylist(tracks: List<TrackEntity>): Playlist {
    return Playlist(id, name, coverUri, tracks.map { it.toTrackItem() })
}

internal fun TrackEntity.toTrackItem(): TrackItem {
    return TrackItem(id, title, audioUri, subtitleUri, subtitleTitle, durationMs)
}

internal fun TrackItem.toEntity(playlistId: String, sortOrder: Int): TrackEntity {
    return TrackEntity(
        id = id,
        playlistId = playlistId,
        title = title,
        audioUri = uri,
        subtitleUri = subtitleUri,
        subtitleTitle = subtitleTitle,
        durationMs = durationMs,
        sortOrder = sortOrder,
    )
}

internal fun DlsiteWorkEntity.toWork(): DlsiteWork {
    return DlsiteWork(
        workId = workId,
        title = title,
        detailUrl = detailUrl,
        downloadUrl = downloadUrl,
        coverUrl = coverUrl,
        coverUri = coverUri,
        status = status,
        playlistId = playlistId,
        localPath = localPath,
        error = error,
        downloadOptionId = downloadOptionId,
        downloadOptionTitle = downloadOptionTitle,
        updatedAt = updatedAt,
        trackCount = trackCount,
    )
}

internal fun DlsiteWork.toEntity(): DlsiteWorkEntity {
    return DlsiteWorkEntity(
        workId = workId,
        title = title,
        detailUrl = detailUrl,
        downloadUrl = downloadUrl,
        coverUrl = coverUrl,
        coverUri = coverUri,
        status = status,
        playlistId = playlistId,
        localPath = localPath,
        error = error,
        downloadOptionId = downloadOptionId,
        downloadOptionTitle = downloadOptionTitle,
        updatedAt = updatedAt,
        trackCount = trackCount,
    )
}

internal fun DlsiteContentEntity.toContent(): DlsiteContent {
    return DlsiteContent(
        workId = workId,
        optionId = optionId,
        title = title,
        status = status,
        localPath = localPath,
        trackIds = trackIds,
        trackCount = trackCount,
        error = error,
        updatedAt = updatedAt,
    )
}

internal fun DlsiteContent.toEntity(): DlsiteContentEntity {
    return DlsiteContentEntity(
        workId = workId,
        optionId = optionId,
        title = title,
        status = status,
        localPath = localPath,
        trackIds = trackIds,
        trackCount = trackCount,
        error = error,
        updatedAt = updatedAt,
    )
}

internal fun DlsiteDownloadQueueEntity.toQueueTask(): DlsiteDownloadQueueTask {
    return DlsiteDownloadQueueTask(
        taskId = taskId,
        workId = workId,
        optionIds = optionIds,
        status = status,
        queueOrder = queueOrder,
        createdAt = createdAt,
        startedAt = startedAt,
        updatedAt = updatedAt,
        finishedAt = finishedAt,
        errorMessage = errorMessage,
    )
}

internal fun DlsiteDownloadQueueTask.toEntity(): DlsiteDownloadQueueEntity {
    return DlsiteDownloadQueueEntity(
        taskId = taskId,
        workId = workId,
        optionIds = optionIds,
        status = status,
        queueOrder = queueOrder,
        createdAt = createdAt,
        startedAt = startedAt,
        updatedAt = updatedAt,
        finishedAt = finishedAt,
        errorMessage = errorMessage,
    )
}
