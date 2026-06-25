package io.github.summerdez.asmrplayer.domain

class PendingLibrarySelection {
    private var coverPlaylistId: String = ""
    private var subtitlePlaylistId: String = ""
    private var subtitleTrackId: String = ""

    fun startCoverPicker(playlistId: String?) {
        coverPlaylistId = playlistId.orEmpty()
    }

    fun consumeCoverPlaylistId(): String {
        val playlistId = coverPlaylistId
        clearCoverPicker()
        return playlistId
    }

    fun clearCoverPicker() {
        coverPlaylistId = ""
    }

    fun startSubtitlePicker(playlistId: String?, trackId: String?) {
        subtitlePlaylistId = playlistId.orEmpty()
        subtitleTrackId = trackId.orEmpty()
    }

    fun consumeSubtitleTarget(): TrackSubtitleTarget {
        val target = TrackSubtitleTarget(subtitlePlaylistId, subtitleTrackId)
        clearSubtitlePicker()
        return target
    }

    fun clearSubtitlePicker() {
        subtitlePlaylistId = ""
        subtitleTrackId = ""
    }

    data class TrackSubtitleTarget(
        val playlistId: String,
        val trackId: String,
    )
}
