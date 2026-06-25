package io.github.summerdez.asmrplayer.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.session.MediaSession
import io.github.summerdez.asmrplayer.ui.activity.MainActivity

internal fun playbackSessionActivity(context: Context): PendingIntent {
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    return PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), flags)
}

internal fun publishPlaybackServiceState(
    mediaSession: MediaSession?,
    lastSessionExtrasSnapshot: PlaybackServiceSnapshot,
    snapshot: PlaybackServiceSnapshot,
): PlaybackServiceSnapshot {
    val sessionExtrasSnapshot = snapshot.asPlaybackSessionExtrasSnapshot()
    if (sessionExtrasSnapshot != lastSessionExtrasSnapshot) {
        mediaSession?.setSessionExtras(sessionExtrasSnapshot.toSessionExtras())
        return sessionExtrasSnapshot
    }
    return lastSessionExtrasSnapshot
}

internal fun buildPlaybackServiceSnapshot(
    playlistId: String,
    playlistIndex: Int,
    audioUri: String,
    subtitleLines: List<String>,
    subtitleIndex: Int,
    subtitleCount: Int,
    overlayRequested: Boolean,
    overlayLocked: Boolean,
    lastError: String,
    sleepTimerActive: Boolean,
    sleepTimerAtEndOfTrack: Boolean,
    sleepTimerEndElapsedRealtimeMs: Long,
    sleepTimerMinutes: Int,
): PlaybackServiceSnapshot {
    return PlaybackServiceSnapshot(
        connected = true,
        playlistId = playlistId,
        playlistIndex = playlistIndex,
        audioUri = audioUri,
        subtitleLines = subtitleLines,
        subtitleIndex = subtitleIndex,
        subtitleCount = subtitleCount,
        overlayRequested = overlayRequested,
        overlayLocked = overlayLocked,
        error = PlaybackError.fromMessage(lastError),
        sleepTimerActive = sleepTimerActive,
        sleepTimerAtEndOfTrack = sleepTimerAtEndOfTrack,
        sleepTimerEndElapsedRealtimeMs = sleepTimerEndElapsedRealtimeMs,
        sleepTimerMinutes = sleepTimerMinutes,
    )
}

private fun PlaybackServiceSnapshot.asPlaybackSessionExtrasSnapshot(): PlaybackServiceSnapshot {
    // Session extras carry only low-frequency custom state; the client derives countdown ticks locally.
    return copy(
        sleepTimerRemainingMs = 0L,
    )
}
