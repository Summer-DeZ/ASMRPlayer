package io.github.summerdez.asmrplayer.playback;

import io.github.summerdez.asmrplayer.R;
import io.github.summerdez.asmrplayer.data.*;
import io.github.summerdez.asmrplayer.data.remote.*;
import io.github.summerdez.asmrplayer.data.download.*;
import io.github.summerdez.asmrplayer.data.files.*;
import io.github.summerdez.asmrplayer.domain.*;
import io.github.summerdez.asmrplayer.domain.model.*;
import io.github.summerdez.asmrplayer.playback.*;
import io.github.summerdez.asmrplayer.presentation.*;
import io.github.summerdez.asmrplayer.ui.*;
import io.github.summerdez.asmrplayer.ui.activity.*;
import io.github.summerdez.asmrplayer.ui.components.*;
import io.github.summerdez.asmrplayer.ui.screens.*;
import io.github.summerdez.asmrplayer.ui.theme.*;
import io.github.summerdez.asmrplayer.ui.util.*;
import io.github.summerdez.asmrplayer.di.*;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

final class PlaybackIntents {
    private PlaybackIntents() {
    }

    static Intent simpleAction(Context context, String action) {
        Intent intent = new Intent(context, PlaybackService.class);
        intent.setAction(action);
        return intent;
    }

    static Intent playMedia(
            Context context,
            Uri audioUri,
            String title,
            Uri subtitleUri,
            String playlistId,
            int playlistIndex) {
        Intent intent = simpleAction(context, PlaybackService.ACTION_PLAY_MEDIA);
        intent.putExtra(PlaybackService.EXTRA_AUDIO_URI, audioUri.toString());
        intent.putExtra(PlaybackService.EXTRA_TRACK_TITLE, title);
        intent.putExtra(PlaybackService.EXTRA_PLAYLIST_ID, playlistId);
        intent.putExtra(PlaybackService.EXTRA_PLAYLIST_INDEX, playlistIndex);
        if (subtitleUri != null) {
            intent.putExtra(PlaybackService.EXTRA_SUBTITLE_URI, subtitleUri.toString());
        }
        return intent;
    }

    static Intent seekTo(Context context, long positionMs) {
        Intent intent = simpleAction(context, PlaybackService.ACTION_SEEK_TO);
        intent.putExtra(PlaybackService.EXTRA_POSITION_MS, positionMs);
        return intent;
    }

    static Intent setSubtitle(Context context, Uri subtitleUri) {
        Intent intent = simpleAction(context, PlaybackService.ACTION_SET_SUBTITLE);
        intent.putExtra(PlaybackService.EXTRA_SUBTITLE_URI, subtitleUri.toString());
        return intent;
    }

    static Intent setSleepMinutes(Context context, int minutes) {
        Intent intent = simpleAction(context, PlaybackService.ACTION_SET_SLEEP_MINUTES);
        intent.putExtra(PlaybackService.EXTRA_SLEEP_MINUTES, minutes);
        return intent;
    }
}
