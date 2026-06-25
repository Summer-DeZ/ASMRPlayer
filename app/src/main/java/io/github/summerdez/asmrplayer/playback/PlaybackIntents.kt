package io.github.summerdez.asmrplayer.playback

import android.content.Context
import android.content.Intent
import android.net.Uri

object PlaybackIntents {
    fun simpleAction(context: Context?, action: String?): Intent {
        val intent = Intent(context, PlaybackService::class.java)
        intent.action = action
        return intent
    }

    fun playMedia(
        context: Context?,
        audioUri: Uri,
        title: String?,
        subtitleUri: Uri?,
        playlistId: String?,
        playlistIndex: Int,
    ): Intent {
        val intent = simpleAction(context, PlaybackService.ACTION_PLAY_MEDIA)
        intent.putExtra(PlaybackService.EXTRA_AUDIO_URI, audioUri.toString())
        intent.putExtra(PlaybackService.EXTRA_TRACK_TITLE, title)
        intent.putExtra(PlaybackService.EXTRA_PLAYLIST_ID, playlistId)
        intent.putExtra(PlaybackService.EXTRA_PLAYLIST_INDEX, playlistIndex)
        if (subtitleUri != null) {
            intent.putExtra(PlaybackService.EXTRA_SUBTITLE_URI, subtitleUri.toString())
        }
        return intent
    }

    fun seekTo(context: Context?, positionMs: Long): Intent {
        val intent = simpleAction(context, PlaybackService.ACTION_SEEK_TO)
        intent.putExtra(PlaybackService.EXTRA_POSITION_MS, positionMs)
        return intent
    }

    fun setSubtitle(context: Context?, subtitleUri: Uri): Intent {
        val intent = simpleAction(context, PlaybackService.ACTION_SET_SUBTITLE)
        intent.putExtra(PlaybackService.EXTRA_SUBTITLE_URI, subtitleUri.toString())
        return intent
    }

    fun setSleepMinutes(context: Context?, minutes: Int): Intent {
        val intent = simpleAction(context, PlaybackService.ACTION_SET_SLEEP_MINUTES)
        intent.putExtra(PlaybackService.EXTRA_SLEEP_MINUTES, minutes)
        return intent
    }
}
