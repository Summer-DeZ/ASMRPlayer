package io.github.summerdez.asmrplayer

import io.github.summerdez.asmrplayer.playback.SubtitleCue
import io.github.summerdez.asmrplayer.playback.SubtitleParser
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

    @Test
    fun nextCueStartAfterFindsStrictlyLaterCueStart() {
        val cues = listOf(
            SubtitleCue(1_000L, 2_000L, "first"),
            SubtitleCue(5_000L, 6_000L, "second"),
            SubtitleCue(9_000L, 10_000L, "third")
        )

        assertEquals(1_000L, SubtitleParser.nextCueStartAfter(cues, 500L))
        assertEquals(5_000L, SubtitleParser.nextCueStartAfter(cues, 1_000L))
        assertEquals(5_000L, SubtitleParser.nextCueStartAfter(cues, 3_000L))
        assertEquals(9_000L, SubtitleParser.nextCueStartAfter(cues, 5_000L))
        assertEquals(-1L, SubtitleParser.nextCueStartAfter(cues, 9_000L))
        assertEquals(-1L, SubtitleParser.nextCueStartAfter(emptyList(), 0L))
    }
}
