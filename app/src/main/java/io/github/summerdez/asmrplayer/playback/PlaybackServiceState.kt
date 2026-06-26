package io.github.summerdez.asmrplayer.playback

import android.os.Bundle
import java.util.ArrayList

data class PlaybackServiceSnapshot(
    val connected: Boolean = false,
    val playlistId: String = "",
    val playlistIndex: Int = -1,
    val audioUri: String = "",
    val subtitleLines: List<String> = emptyList(),
    val subtitleStartsMs: List<Int> = emptyList(),
    val subtitleIndex: Int = -1,
    val subtitleCount: Int = 0,
    val overlayRequested: Boolean = false,
    val overlayLocked: Boolean = false,
    val error: PlaybackError = PlaybackError.None,
    val sleepTimerActive: Boolean = false,
    val sleepTimerAtEndOfTrack: Boolean = false,
    val sleepTimerEndElapsedRealtimeMs: Long = 0L,
    val sleepTimerRemainingMs: Long = 0L,
    val sleepTimerMinutes: Int = 0,
)

sealed interface PlaybackError {
    val message: String

    data object None : PlaybackError {
        override val message: String = ""
    }

    data class Service(override val message: String) : PlaybackError

    companion object {
        fun fromMessage(message: String?): PlaybackError {
            return if (message.isNullOrEmpty()) None else Service(message)
        }
    }
}

internal fun PlaybackServiceSnapshot.toSessionExtras(): Bundle {
    return Bundle().apply {
        putBoolean(KEY_CONNECTED, connected)
        putString(KEY_PLAYLIST_ID, playlistId)
        putInt(KEY_PLAYLIST_INDEX, playlistIndex)
        putString(KEY_AUDIO_URI, audioUri)
        putStringArrayList(KEY_SUBTITLE_LINES, ArrayList(subtitleLines))
        putIntArray(KEY_SUBTITLE_STARTS, subtitleStartsMs.toIntArray())
        putInt(KEY_SUBTITLE_INDEX, subtitleIndex)
        putInt(KEY_SUBTITLE_COUNT, subtitleCount)
        putBoolean(KEY_OVERLAY_REQUESTED, overlayRequested)
        putBoolean(KEY_OVERLAY_LOCKED, overlayLocked)
        putString(KEY_ERROR_MESSAGE, error.message)
        putBoolean(KEY_SLEEP_TIMER_ACTIVE, sleepTimerActive)
        putBoolean(KEY_SLEEP_TIMER_AT_END_OF_TRACK, sleepTimerAtEndOfTrack)
        putLong(KEY_SLEEP_TIMER_END_ELAPSED_REALTIME_MS, sleepTimerEndElapsedRealtimeMs)
        putInt(KEY_SLEEP_TIMER_MINUTES, sleepTimerMinutes)
    }
}

internal fun playbackServiceSnapshotFromSessionExtras(extras: Bundle?): PlaybackServiceSnapshot {
    if (extras == null || !extras.getBoolean(KEY_CONNECTED, false)) {
        return PlaybackServiceSnapshot()
    }
    return PlaybackServiceSnapshot(
        connected = true,
        playlistId = extras.getString(KEY_PLAYLIST_ID).orEmpty(),
        playlistIndex = extras.getInt(KEY_PLAYLIST_INDEX, -1),
        audioUri = extras.getString(KEY_AUDIO_URI).orEmpty(),
        subtitleLines = extras.getStringArrayList(KEY_SUBTITLE_LINES).orEmpty(),
        subtitleStartsMs = extras.getIntArray(KEY_SUBTITLE_STARTS)?.toList().orEmpty(),
        subtitleIndex = extras.getInt(KEY_SUBTITLE_INDEX, -1),
        subtitleCount = extras.getInt(KEY_SUBTITLE_COUNT, 0),
        overlayRequested = extras.getBoolean(KEY_OVERLAY_REQUESTED, false),
        overlayLocked = extras.getBoolean(KEY_OVERLAY_LOCKED, false),
        error = PlaybackError.fromMessage(extras.getString(KEY_ERROR_MESSAGE)),
        sleepTimerActive = extras.getBoolean(KEY_SLEEP_TIMER_ACTIVE, false),
        sleepTimerAtEndOfTrack = extras.getBoolean(KEY_SLEEP_TIMER_AT_END_OF_TRACK, false),
        sleepTimerEndElapsedRealtimeMs = extras.getLong(KEY_SLEEP_TIMER_END_ELAPSED_REALTIME_MS, 0L),
        sleepTimerMinutes = extras.getInt(KEY_SLEEP_TIMER_MINUTES, 0),
    )
}

private const val KEY_PREFIX = "io.github.summerdez.asmrplayer.playback."
private const val KEY_CONNECTED = KEY_PREFIX + "connected"
private const val KEY_PLAYLIST_ID = KEY_PREFIX + "playlist_id"
private const val KEY_PLAYLIST_INDEX = KEY_PREFIX + "playlist_index"
private const val KEY_AUDIO_URI = KEY_PREFIX + "audio_uri"
private const val KEY_SUBTITLE_LINES = KEY_PREFIX + "subtitle_lines"
private const val KEY_SUBTITLE_STARTS = KEY_PREFIX + "subtitle_starts"
private const val KEY_SUBTITLE_INDEX = KEY_PREFIX + "subtitle_index"
private const val KEY_SUBTITLE_COUNT = KEY_PREFIX + "subtitle_count"
private const val KEY_OVERLAY_REQUESTED = KEY_PREFIX + "overlay_requested"
private const val KEY_OVERLAY_LOCKED = KEY_PREFIX + "overlay_locked"
private const val KEY_ERROR_MESSAGE = KEY_PREFIX + "error_message"
private const val KEY_SLEEP_TIMER_ACTIVE = KEY_PREFIX + "sleep_timer_active"
private const val KEY_SLEEP_TIMER_AT_END_OF_TRACK = KEY_PREFIX + "sleep_timer_at_end_of_track"
private const val KEY_SLEEP_TIMER_END_ELAPSED_REALTIME_MS = KEY_PREFIX + "sleep_timer_end_elapsed_realtime_ms"
private const val KEY_SLEEP_TIMER_MINUTES = KEY_PREFIX + "sleep_timer_minutes"
