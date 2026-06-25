package io.github.summerdez.asmrplayer.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player

internal class PlaybackPlayerListener(
    private val playWhenReady: () -> Boolean,
    private val overlayRequested: () -> Boolean,
    private val startSubtitleTicker: () -> Unit,
    private val stopSubtitleTicker: () -> Unit,
    private val ensureOverlay: () -> Boolean,
    private val updateOverlayPlaybackState: () -> Unit,
    private val consumeSleepAtEndOfTrack: () -> Boolean,
    private val syncCurrentTrack: (MediaItem?) -> Unit,
    private val updateSubtitleForCurrentPositionAndSchedule: () -> Unit,
    private val markPlayerError: () -> Unit,
    private val publishState: () -> Unit,
) : Player.Listener {
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            startSubtitleTicker()
            if (overlayRequested()) {
                ensureOverlay()
            }
        } else {
            stopSubtitleTicker()
        }
        updateOverlayPlaybackState()
        publishState()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY && playWhenReady()) {
            startSubtitleTicker()
        }
        if (playbackState == Player.STATE_ENDED) {
            stopSubtitleTicker()
            consumeSleepAtEndOfTrack()
        }
        publishState()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        syncCurrentTrack(mediaItem)
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && consumeSleepAtEndOfTrack()) {
            return
        }
        updateSubtitleForCurrentPositionAndSchedule()
        publishState()
    }

    override fun onPlayerError(error: PlaybackException) {
        markPlayerError()
        stopSubtitleTicker()
        publishState()
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
                Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_MEDIA_METADATA_CHANGED,
            )
        ) {
            updateSubtitleForCurrentPositionAndSchedule()
            publishState()
        }
    }
}
