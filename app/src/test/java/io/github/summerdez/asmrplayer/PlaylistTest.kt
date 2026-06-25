package io.github.summerdez.asmrplayer

import io.github.summerdez.asmrplayer.domain.model.Playlist
import io.github.summerdez.asmrplayer.domain.model.TrackItem

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistTest {
    @Test
    fun constructorUsesKotlinDefaults() {
        val playlist = Playlist("id", "name")

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
    fun threeArgumentConstructorTreatsThirdParameterAsTracks() {
        val track = TrackItem("track", "Track", "content://track")
        val playlist = Playlist("id", "name", listOf(track))

        assertEquals("", playlist.coverUri)
        assertEquals(1, playlist.tracks.size)
        assertEquals(track, playlist.tracks[0])
    }
}
