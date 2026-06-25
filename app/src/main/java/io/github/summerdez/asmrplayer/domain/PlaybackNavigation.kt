package io.github.summerdez.asmrplayer.domain

import io.github.summerdez.asmrplayer.domain.model.Playlist
object PlaybackNavigation {
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
