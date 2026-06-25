package io.github.summerdez.asmrplayer

import io.github.summerdez.asmrplayer.domain.model.TrackItem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackItemTest {
    @Test
    fun constructorUsesKotlinDefaultsAndNormalizesDuration() {
        val track = TrackItem(durationMs = -1L)

        assertEquals("", track.id)
        assertEquals("", track.title)
        assertEquals("", track.uri)
        assertEquals("", track.subtitleUri)
        assertEquals("", track.subtitleTitle)
        assertEquals(0L, track.durationMs)
        assertFalse(track.hasAudioUri())
    }

    @Test
    fun hasAudioUriReflectsStoredUri() {
        assertFalse(TrackItem("id", "title", "").hasAudioUri())
        assertTrue(TrackItem("id", "title", "content://audio").hasAudioUri())
    }

    @Test
    fun subtitleTitleFallsBackWhenMissing() {
        assertEquals(
            "fallback",
            TrackItem("id", "title", "content://audio").subtitleTitleOr("fallback"),
        )
        assertEquals(
            "subtitle.srt",
            TrackItem("id", "title", "content://audio", "content://subtitle", "subtitle.srt")
                .subtitleTitleOr("fallback"),
        )
    }
}
