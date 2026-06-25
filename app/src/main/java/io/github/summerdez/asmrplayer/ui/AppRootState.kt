package io.github.summerdez.asmrplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.summerdez.asmrplayer.domain.model.Playlist
import io.github.summerdez.asmrplayer.domain.model.TrackItem

@Stable
internal class AppRootState {
    var showLibraryMenu by mutableStateOf(false)
    var playerOpen by mutableStateOf(false)
    var queueOpen by mutableStateOf(false)
    var createPlaylistDialog by mutableStateOf(false)
    var renamePlaylist by mutableStateOf<Playlist?>(null)
    var deletePlaylist by mutableStateOf<Playlist?>(null)
    var renameTrack by mutableStateOf<Pair<Playlist, TrackItem>?>(null)
    var deleteTrack by mutableStateOf<Triple<Playlist, TrackItem, Int>?>(null)
    var moveTrack by mutableStateOf<Pair<Playlist, TrackItem>?>(null)
    var customSleepDialog by mutableStateOf(false)
    var downloadManagerOpen by mutableStateOf(false)
    var aiSettingField by mutableStateOf<AiSettingField?>(null)
    var activeAiSubtitleTrackId by mutableStateOf<String?>(null)
    var settingsAiOpen by mutableStateOf(false)

    fun closeTopOverlay() {
        if (queueOpen) {
            queueOpen = false
        } else {
            playerOpen = false
        }
    }
}

@Composable
internal fun rememberAppRootState(): AppRootState = remember { AppRootState() }

@Composable
internal fun AppRootBackHandler(rootState: AppRootState) {
    BackHandler(enabled = rootState.playerOpen || rootState.queueOpen) {
        rootState.closeTopOverlay()
    }
}
