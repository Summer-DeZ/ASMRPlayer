package io.github.summerdez.asmrplayer.data

import io.github.summerdez.asmrplayer.R
import io.github.summerdez.asmrplayer.data.*
import io.github.summerdez.asmrplayer.data.remote.*
import io.github.summerdez.asmrplayer.data.download.*
import io.github.summerdez.asmrplayer.data.files.*
import io.github.summerdez.asmrplayer.domain.*
import io.github.summerdez.asmrplayer.domain.model.*
import io.github.summerdez.asmrplayer.playback.*
import io.github.summerdez.asmrplayer.presentation.*
import io.github.summerdez.asmrplayer.ui.*
import io.github.summerdez.asmrplayer.ui.activity.*
import io.github.summerdez.asmrplayer.ui.components.*
import io.github.summerdez.asmrplayer.ui.screens.*
import io.github.summerdez.asmrplayer.ui.theme.*
import io.github.summerdez.asmrplayer.ui.util.*
import io.github.summerdez.asmrplayer.di.*
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
