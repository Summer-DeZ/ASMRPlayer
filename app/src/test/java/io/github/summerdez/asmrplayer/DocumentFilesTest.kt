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

class DocumentFilesTest {
    @Test
    fun subtitleNamesUseFullAudioFileName() {
        assertEquals("a.mp3.vtt", DocumentFiles.subtitleNameForAudioName("a.mp3"))
        assertEquals("b.wav.vtt", DocumentFiles.subtitleNameForAudioName("b.wav"))
    }

    @Test
    fun supportedAudioNamesIncludeMainstreamFormats() {
        assertTrue(DocumentFiles.isSupportedAudioName("voice.mp3", ""))
        assertTrue(DocumentFiles.isSupportedAudioName("voice.wav", ""))
        assertTrue(DocumentFiles.isSupportedAudioName("voice.flac", ""))
        assertTrue(DocumentFiles.isSupportedAudioName("anything.bin", "audio/mpeg"))
        assertFalse(DocumentFiles.isSupportedAudioName("voice.mp3.vtt", "text/vtt"))
        assertFalse(DocumentFiles.isSupportedAudioName("voice.mp3.vtt", "audio/mpeg"))
    }
}
