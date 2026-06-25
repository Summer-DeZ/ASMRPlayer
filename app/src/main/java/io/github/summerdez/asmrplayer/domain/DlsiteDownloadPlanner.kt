package io.github.summerdez.asmrplayer.domain

import io.github.summerdez.asmrplayer.data.remote.DlsiteJsonParser
import io.github.summerdez.asmrplayer.domain.model.DlsiteDownloadOption

object DlsiteDownloadPlanner {
    fun optionsFor(ziptree: DlsiteJsonParser.DlsiteZiptree?): List<DlsiteDownloadOption> {
        val files = ziptree?.audioFiles.orEmpty()
        if (files.isEmpty()) {
            return emptyList()
        }

        val parentPaths = files.map { parentPath(it.displayPath) }
        val commonPrefix = commonDirectoryPrefix(parentPaths)
        val byDirectory = linkedMapOf<List<String>, MutableList<DlsiteJsonParser.ContentFile>>()
        files.forEach { file ->
            val groupPath = selectableDirectoryPath(parentPath(file.displayPath), commonPrefix)
            byDirectory.getOrPut(groupPath) { mutableListOf() }.add(file)
        }

        val usedIds = mutableSetOf<String>()
        return byDirectory.map { (directoryPath, directoryFiles) ->
            DlsiteDownloadOption(
                uniqueOptionId(directoryPath, usedIds),
                directoryTitle(directoryPath),
                directoryFiles,
            )
        }
    }

    private fun parentPath(displayPath: String?): List<String> {
        val segments = displayPath.orEmpty()
            .split("/")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (segments.size <= 1) {
            return emptyList()
        }
        return segments.dropLast(1)
    }

    private fun commonDirectoryPrefix(paths: List<List<String>>): List<String> {
        if (paths.isEmpty()) {
            return emptyList()
        }
        var length = 0
        while (true) {
            val candidate = paths.first().getOrNull(length) ?: break
            if (paths.any { it.getOrNull(length) != candidate }) {
                break
            }
            length++
        }
        return paths.first().take(length)
    }

    private fun selectableDirectoryPath(parentPath: List<String>, commonPrefix: List<String>): List<String> {
        return parentPath.drop(commonPrefix.size)
    }

    private fun directoryTitle(path: List<String>): String {
        return path
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" / ")
            .ifBlank { ROOT_AUDIO_TITLE }
    }

    private fun uniqueOptionId(path: List<String>, usedIds: MutableSet<String>): String {
        val base = path
            .map { escapeOptionIdSegment(it.trim()) }
            .filter { it.isNotEmpty() }
            .joinToString("/")
            .ifBlank { ROOT_AUDIO_ID }
        var candidate = base
        var index = 2
        while (!usedIds.add(candidate)) {
            candidate = "$base#$index"
            index++
        }
        return candidate
    }

    private fun escapeOptionIdSegment(segment: String): String {
        return segment
            .replace("%", "%25")
            .replace("|", "%7C")
    }

    private const val ROOT_AUDIO_ID = "__root_audio__"
    private const val ROOT_AUDIO_TITLE = "根目录音频"
}
