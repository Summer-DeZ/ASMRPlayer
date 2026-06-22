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
object PlaybackNavigation {
    @JvmStatic
    fun relative(selection: PlaybackSelection, playlist: Playlist?, delta: Int): Result {
        if (playlist == null || playlist.tracks.isEmpty()) {
            return Result.empty()
        }

        val baseIndex = selection.baseIndexFor(playlist)
        var targetIndex = baseIndex + delta
        if (delta > 0 && baseIndex < 0) {
            targetIndex = 0
        }
        if (targetIndex < 0) {
            return Result.beforeStart()
        }
        if (targetIndex >= playlist.tracks.size) {
            return Result.afterEnd()
        }
        return Result.ready(targetIndex)
    }

    data class Result(
        val status: Status,
        val targetIndex: Int,
    ) {
        enum class Status {
            READY,
            EMPTY,
            BEFORE_START,
            AFTER_END,
        }

        companion object {
            fun ready(targetIndex: Int): Result = Result(Status.READY, targetIndex)

            fun empty(): Result = Result(Status.EMPTY, -1)

            fun beforeStart(): Result = Result(Status.BEFORE_START, -1)

            fun afterEnd(): Result = Result(Status.AFTER_END, -1)
        }
    }
}
