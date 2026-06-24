package io.github.summerdez.asmrplayer

import io.github.summerdez.asmrplayer.domain.model.Playlist
import io.github.summerdez.asmrplayer.domain.model.TrackItem

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistTest {
    @Test
    fun constructorNormalizesNullCoverUri() {
        val playlist = Playlist("id", "name", null, emptyList())

        assertEquals("id", playlist.id)
        assertEquals("name", playlist.name)
        assertEquals("", playlist.coverUri)
        assertEquals(emptyList<Any>(), playlist.tracks)
    }

    @Test
    fun copyOverridesRequestedFieldsOnly() {
        val original = Playlist("id", "name", "cover", emptyList())

        val renamed = original.copy(name = "renamed")
        assertEquals("renamed", renamed.name)
        assertEquals("cover", renamed.coverUri)

        val recovered = original.copy(coverUri = "x")
        assertEquals("x", recovered.coverUri)
        assertEquals("name", recovered.name)
    }

    @Test
    fun defaultTracksRemainMutableForJavaFieldCompatibility() {
        val playlist = Playlist("id", "name")

        @Suppress("UNCHECKED_CAST")
        val mutableTracks = playlist.tracks as MutableList<TrackItem>
        mutableTracks.add(TrackItem("track", "Track", "content://track"))

        assertEquals(1, playlist.tracks.size)
    }
}
