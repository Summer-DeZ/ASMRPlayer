package io.github.summerdez.asmrplayer.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

internal class PlaybackSessionCallback(
    private val handleCommand: (String, Bundle) -> Unit,
) : MediaSession.Callback {
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        val connection = super.onConnect(session, controller)
        val commands = connection.availableSessionCommands.buildUpon()
        playbackServiceCustomCommandActions().forEach { commands.add(SessionCommand(it, Bundle.EMPTY)) }
        return MediaSession.ConnectionResult.accept(commands.build(), connection.availablePlayerCommands)
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        handleCommand(customCommand.customAction, args)
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }
}

internal sealed interface PlaybackServiceCommand {
    data class PlayMedia(
        val audioUri: Uri?,
        val subtitleUri: Uri?,
        val title: String?,
        val playlistId: String?,
        val playlistIndex: Int,
    ) : PlaybackServiceCommand

    object TogglePlayback : PlaybackServiceCommand
    object StopPlayback : PlaybackServiceCommand
    data class SeekTo(val positionMs: Long) : PlaybackServiceCommand
    data class SetSubtitle(val subtitleUri: Uri?) : PlaybackServiceCommand
    object ShowOverlay : PlaybackServiceCommand
    object HideOverlay : PlaybackServiceCommand
    object UnlockOverlay : PlaybackServiceCommand
    data class SetSleepMinutes(val minutes: Int) : PlaybackServiceCommand
    object SetSleepAtEnd : PlaybackServiceCommand
    object CancelSleep : PlaybackServiceCommand
}

internal fun parsePlaybackServiceCommand(action: String, args: Bundle): PlaybackServiceCommand? {
    return when (action) {
        PlaybackService.ACTION_PLAY_MEDIA, PlaybackService.COMMAND_PLAY_MEDIA -> PlaybackServiceCommand.PlayMedia(
            audioUri = parsePlaybackUri(args.getString(PlaybackService.EXTRA_AUDIO_URI)),
            subtitleUri = parsePlaybackUri(args.getString(PlaybackService.EXTRA_SUBTITLE_URI)),
            title = args.getString(PlaybackService.EXTRA_TRACK_TITLE),
            playlistId = args.getString(PlaybackService.EXTRA_PLAYLIST_ID),
            playlistIndex = args.getInt(PlaybackService.EXTRA_PLAYLIST_INDEX, -1),
        )
        PlaybackService.ACTION_TOGGLE_PLAYBACK -> PlaybackServiceCommand.TogglePlayback
        PlaybackService.ACTION_STOP_PLAYBACK -> PlaybackServiceCommand.StopPlayback
        PlaybackService.ACTION_SEEK_TO -> PlaybackServiceCommand.SeekTo(
            args.getLong(PlaybackService.EXTRA_POSITION_MS, 0L),
        )
        PlaybackService.ACTION_SET_SUBTITLE, PlaybackService.COMMAND_SET_SUBTITLE -> PlaybackServiceCommand.SetSubtitle(
            parsePlaybackUri(args.getString(PlaybackService.EXTRA_SUBTITLE_URI)),
        )
        PlaybackService.ACTION_SHOW_OVERLAY, PlaybackService.COMMAND_SHOW_OVERLAY -> PlaybackServiceCommand.ShowOverlay
        PlaybackService.ACTION_HIDE_OVERLAY, PlaybackService.COMMAND_HIDE_OVERLAY -> PlaybackServiceCommand.HideOverlay
        PlaybackService.ACTION_UNLOCK_OVERLAY, PlaybackService.COMMAND_UNLOCK_OVERLAY -> PlaybackServiceCommand.UnlockOverlay
        PlaybackService.ACTION_SET_SLEEP_MINUTES, PlaybackService.COMMAND_SET_SLEEP_MINUTES ->
            PlaybackServiceCommand.SetSleepMinutes(args.getInt(PlaybackService.EXTRA_SLEEP_MINUTES, 0))
        PlaybackService.ACTION_SET_SLEEP_AT_END, PlaybackService.COMMAND_SET_SLEEP_AT_END ->
            PlaybackServiceCommand.SetSleepAtEnd
        PlaybackService.ACTION_CANCEL_SLEEP, PlaybackService.COMMAND_CANCEL_SLEEP -> PlaybackServiceCommand.CancelSleep
        else -> null
    }
}

internal fun parsePlaybackUri(value: String?): Uri? {
    return value?.takeIf { it.isNotEmpty() }?.let(Uri::parse)
}

private fun playbackServiceCustomCommandActions(): List<String> {
    return listOf(
        PlaybackService.COMMAND_PLAY_MEDIA,
        PlaybackService.COMMAND_SET_SUBTITLE,
        PlaybackService.COMMAND_SHOW_OVERLAY,
        PlaybackService.COMMAND_HIDE_OVERLAY,
        PlaybackService.COMMAND_UNLOCK_OVERLAY,
        PlaybackService.COMMAND_SET_SLEEP_MINUTES,
        PlaybackService.COMMAND_SET_SLEEP_AT_END,
        PlaybackService.COMMAND_CANCEL_SLEEP,
    )
}
