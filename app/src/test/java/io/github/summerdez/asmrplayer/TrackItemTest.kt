package io.github.summerdez.asmrplayer

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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackItemTest {
    @Test
    fun constructorNormalizesNullableValues() {
        val track = TrackItem("id", null, null, null, null)

        assertEquals("", track.title)
        assertEquals("", track.uri)
        assertEquals("", track.subtitleUri)
        assertEquals("", track.subtitleTitle)
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
                TrackItem("id", "title", "content://audio").subtitleTitleOr("fallback"))
        assertEquals(
                "subtitle.srt",
                TrackItem("id", "title", "content://audio", "content://subtitle", "subtitle.srt")
                        .subtitleTitleOr("fallback"))
    }
}
