package io.github.summerdez.asmrplayer.domain

import io.github.summerdez.asmrplayer.R
import io.github.summerdez.asmrplayer.data.*
import io.github.summerdez.asmrplayer.data.remote.*
import io.github.summerdez.asmrplayer.data.download.*
import io.github.summerdez.asmrplayer.data.files.*
import io.github.summerdez.asmrplayer.domain.*
import io.github.summerdez.asmrplayer.domain.model.*
import io.github.summerdez.asmrplayer.playback.*
import io.github.summerdez.asmrplayer.presentation.*
import io.github.summerdez.asmrplayer.ui.*
import io.github.summerdez.asmrplayer.ui.activity.*
import io.github.summerdez.asmrplayer.ui.components.*
import io.github.summerdez.asmrplayer.ui.screens.*
import io.github.summerdez.asmrplayer.ui.theme.*
import io.github.summerdez.asmrplayer.ui.util.*
import io.github.summerdez.asmrplayer.di.*
object LibraryActivityResults {
    const val REQUEST_PLAYLIST_AUDIO = 104
    const val REQUEST_TRACK_SUBTITLE = 105
    const val REQUEST_PLAYLIST_COVER = 106
    const val REQUEST_PLAYLIST_FOLDER = 107

    @JvmStatic
    fun resolve(requestCode: Int, success: Boolean, hasData: Boolean, hasPrimaryUri: Boolean): Action {
        if (!success || !hasData) {
            return clearActionFor(requestCode)
        }
        if (requestCode == REQUEST_PLAYLIST_AUDIO) {
            return Action.IMPORT_PLAYLIST_AUDIO
        }
        if (requestCode == REQUEST_PLAYLIST_FOLDER) {
            return if (hasPrimaryUri) Action.IMPORT_PLAYLIST_FOLDER else Action.IGNORE
        }
        if (!hasPrimaryUri) {
            return clearActionFor(requestCode)
        }
        if (requestCode == REQUEST_TRACK_SUBTITLE) {
            return Action.IMPORT_TRACK_SUBTITLE
        }
        if (requestCode == REQUEST_PLAYLIST_COVER) {
            return Action.IMPORT_PLAYLIST_COVER
        }
        return Action.IGNORE
    }

    private fun clearActionFor(requestCode: Int): Action {
        if (requestCode == REQUEST_TRACK_SUBTITLE) {
            return Action.CLEAR_TRACK_SUBTITLE_PICKER
        }
        if (requestCode == REQUEST_PLAYLIST_COVER) {
            return Action.CLEAR_PLAYLIST_COVER_PICKER
        }
        return Action.IGNORE
    }

    enum class Action {
        IMPORT_PLAYLIST_AUDIO,
        IMPORT_PLAYLIST_FOLDER,
        IMPORT_TRACK_SUBTITLE,
        IMPORT_PLAYLIST_COVER,
        CLEAR_TRACK_SUBTITLE_PICKER,
        CLEAR_PLAYLIST_COVER_PICKER,
        IGNORE,
    }
}
