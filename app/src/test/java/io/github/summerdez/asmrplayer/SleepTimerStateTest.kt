package io.github.summerdez.asmrplayer

import io.github.summerdez.asmrplayer.domain.SleepTimerState

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerStateTest {
    @Test
    fun timedTimerClampsMinutesAndCalculatesRemainingTime() {
        val state = SleepTimerState()

        val durationMs = state.setMinutes(0, 1_000L)

        assertEquals(60_000L, durationMs)
        assertEquals(1, state.minutes())
        assertFalse(state.isAtEndOfTrack())
        assertEquals(60_000L, state.remainingMs(1_000L))
        assertEquals(30_000L, state.remainingMs(31_000L))
        assertEquals(0L, state.remainingMs(61_001L))
    }

    @Test
    fun endOfTrackTimerConsumesOnce() {
        val state = SleepTimerState()

        state.setAtEndOfTrack()

        assertTrue(state.isAtEndOfTrack())
        assertEquals(0, state.minutes())
        assertEquals(0L, state.remainingMs(10_000L))
        assertTrue(state.consumeAtEndOfTrack())
        assertFalse(state.consumeAtEndOfTrack())
        assertFalse(state.isAtEndOfTrack())
    }

    @Test
    fun cancelClearsTimedAndEndOfTrackTimers() {
        val state = SleepTimerState()

        state.setMinutes(45, 5_000L)
        state.cancel()

        assertEquals(0, state.minutes())
        assertEquals(0L, state.remainingMs(5_000L))
        assertFalse(state.isAtEndOfTrack())

        state.setAtEndOfTrack()
        state.cancel()

        assertFalse(state.isAtEndOfTrack())
    }
}
