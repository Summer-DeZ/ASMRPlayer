package io.github.summerdez.asmrplayer.data.ai

import android.content.Context
import android.content.Intent
import io.github.summerdez.asmrplayer.domain.model.SubtitleGenerationTarget

internal object AiSubtitleGenerationIntents {
    const val ACTION_START = "io.github.summerdez.asmrplayer.ai.START"
    const val ACTION_PAUSE = "io.github.summerdez.asmrplayer.ai.PAUSE"
    const val ACTION_CANCEL = "io.github.summerdez.asmrplayer.ai.CANCEL"

    private const val EXTRA_PLAYLIST_ID = "playlistId"
    private const val EXTRA_TRACK_ID = "trackId"
    private const val EXTRA_TRACK_TITLE = "trackTitle"
    private const val EXTRA_AUDIO_URI = "audioUri"
    private const val EXTRA_CONTEXT_TITLE = "contextTitle"
    private const val EXTRA_FORCE_REGENERATE = "forceRegenerate"
    private const val EXTRA_FORCE_RETRANSLATE = "forceRetranslate"

    fun targetFrom(intent: Intent): SubtitleGenerationTarget {
        return SubtitleGenerationTarget(
            playlistId = intent.getStringExtra(EXTRA_PLAYLIST_ID).orEmpty(),
            trackId = intent.getStringExtra(EXTRA_TRACK_ID).orEmpty(),
            trackTitle = intent.getStringExtra(EXTRA_TRACK_TITLE).orEmpty(),
            audioUri = intent.getStringExtra(EXTRA_AUDIO_URI).orEmpty(),
            contextTitle = intent.getStringExtra(EXTRA_CONTEXT_TITLE).orEmpty(),
        )
    }

    fun forceRegenerateFrom(intent: Intent): Boolean {
        return intent.getBooleanExtra(EXTRA_FORCE_REGENERATE, false)
    }

    fun forceRetranslateFrom(intent: Intent): Boolean {
        return intent.getBooleanExtra(EXTRA_FORCE_RETRANSLATE, false)
    }

    fun trackIdFrom(intent: Intent): String {
        return intent.getStringExtra(EXTRA_TRACK_ID).orEmpty()
    }

    fun startIntent(
        context: Context,
        target: SubtitleGenerationTarget,
        forceRegenerate: Boolean = false,
        forceRetranslate: Boolean = false,
    ): Intent {
        return Intent(context, AiSubtitleGenerationService::class.java)
            .setAction(ACTION_START)
            .putExtra(EXTRA_PLAYLIST_ID, target.playlistId)
            .putExtra(EXTRA_TRACK_ID, target.trackId)
            .putExtra(EXTRA_TRACK_TITLE, target.trackTitle)
            .putExtra(EXTRA_AUDIO_URI, target.audioUri)
            .putExtra(EXTRA_CONTEXT_TITLE, target.contextTitle)
            .putExtra(EXTRA_FORCE_REGENERATE, forceRegenerate)
            .putExtra(EXTRA_FORCE_RETRANSLATE, forceRetranslate)
    }

    fun pauseIntent(context: Context, trackId: String): Intent {
        return Intent(context, AiSubtitleGenerationService::class.java)
            .setAction(ACTION_PAUSE)
            .putExtra(EXTRA_TRACK_ID, trackId)
    }

    fun cancelIntent(context: Context, trackId: String): Intent {
        return Intent(context, AiSubtitleGenerationService::class.java)
            .setAction(ACTION_CANCEL)
            .putExtra(EXTRA_TRACK_ID, trackId)
    }
}
