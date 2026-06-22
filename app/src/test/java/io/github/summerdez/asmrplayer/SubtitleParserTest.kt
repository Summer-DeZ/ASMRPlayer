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
import org.junit.Test

class SubtitleParserTest {
    @Test
    fun textAtKeepsPreviousCueDuringGaps() {
        val cues = listOf(
            SubtitleCue(1_000L, 2_000L, "first"),
            SubtitleCue(5_000L, 6_000L, "second")
        )

        assertEquals("", SubtitleParser.textAt(cues, 500L))
        assertEquals("first", SubtitleParser.textAt(cues, 1_500L))
        assertEquals("first", SubtitleParser.textAt(cues, 3_000L))
        assertEquals("second", SubtitleParser.textAt(cues, 5_500L))
        assertEquals("second", SubtitleParser.textAt(cues, 7_000L))
    }
}
