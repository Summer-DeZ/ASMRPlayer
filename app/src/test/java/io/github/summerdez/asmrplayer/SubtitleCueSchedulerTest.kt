package io.github.summerdez.asmrplayer

import io.github.summerdez.asmrplayer.playback.SubtitleCue
import io.github.summerdez.asmrplayer.playback.SubtitleCueScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleCueSchedulerTest {
    @Test
    fun scheduleBeforeFirstCueReturnsEmptyFrameAndFirstStartDelay() {
        val schedule = SubtitleCueScheduler.scheduleAt(cues(), 500L)

        assertEquals("", schedule.frame.text)
        assertEquals(-1, schedule.frame.index)
        assertEquals(525L, schedule.nextWakeDelayMs)
    }

    @Test
    fun scheduleInsideCueReturnsCurrentFrameAndNextStartDelay() {
        val schedule = SubtitleCueScheduler.scheduleAt(cues(), 1_500L)

        assertEquals("first", schedule.frame.text)
        assertEquals(0, schedule.frame.index)
        assertEquals(3_525L, schedule.nextWakeDelayMs)
    }

    @Test
    fun scheduleDuringCueGapKeepsPreviousCueFrame() {
        val schedule = SubtitleCueScheduler.scheduleAt(cues(), 3_000L)

        assertEquals("first", schedule.frame.text)
        assertEquals(0, schedule.frame.index)
        assertEquals(2_025L, schedule.nextWakeDelayMs)
    }

    @Test
    fun scheduleAfterLastCueHasNoNextWakeDelay() {
        val schedule = SubtitleCueScheduler.scheduleAt(cues(), 7_000L)

        assertEquals("second", schedule.frame.text)
        assertEquals(1, schedule.frame.index)
        assertNull(schedule.nextWakeDelayMs)
    }

    @Test
    fun scheduleWithEmptyCuesReturnsEmptyFrameAndNoNextWakeDelay() {
        val schedule = SubtitleCueScheduler.scheduleAt(emptyList(), 1_000L)

        assertEquals("", schedule.frame.text)
        assertEquals(-1, schedule.frame.index)
        assertNull(schedule.nextWakeDelayMs)
    }

    private fun cues(): List<SubtitleCue> {
        return listOf(
            SubtitleCue(1_000L, 2_000L, "first"),
            SubtitleCue(5_000L, 6_000L, "second"),
        )
    }
}
