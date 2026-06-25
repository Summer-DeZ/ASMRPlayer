package io.github.summerdez.asmrplayer.playback

import kotlin.math.max

data class SubtitleCueFrame(
    val text: String,
    val index: Int,
)

data class SubtitleCueSchedule(
    val frame: SubtitleCueFrame,
    val nextWakeDelayMs: Long?,
)

object SubtitleCueScheduler {
    fun scheduleAt(cues: List<SubtitleCue>, positionMs: Long): SubtitleCueSchedule {
        return SubtitleCueSchedule(
            frame = frameAt(cues, positionMs),
            nextWakeDelayMs = nextWakeDelayMs(cues, positionMs),
        )
    }

    fun frameAt(cues: List<SubtitleCue>, positionMs: Long): SubtitleCueFrame {
        val index = SubtitleParser.indexAt(cues, positionMs)
        return SubtitleCueFrame(
            text = SubtitleParser.textAt(cues, positionMs).orEmpty(),
            index = index,
        )
    }

    fun nextWakeDelayMs(cues: List<SubtitleCue>, positionMs: Long): Long? {
        val nextStartMs = SubtitleParser.nextCueStartAfter(cues, positionMs)
        if (nextStartMs < 0L) {
            return null
        }
        return max(0L, nextStartMs - positionMs + SUBTITLE_BOUNDARY_GUARD_MS)
    }

    const val SUBTITLE_BOUNDARY_GUARD_MS = 25L
}
