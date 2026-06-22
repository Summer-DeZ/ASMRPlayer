package io.github.summerdez.asmrplayer.data.ai

import android.content.Context
import io.github.summerdez.asmrplayer.domain.model.SubtitleGenerationTarget
import io.github.summerdez.asmrplayer.domain.model.SubtitleLine
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64

internal class AiSubtitleSegmentCache(
    private val rootDir: File,
) {
    constructor(context: Context) : this(File(context.cacheDir, "ai-subtitles/segments"))

    fun load(target: SubtitleGenerationTarget, whisperModelId: String): List<SubtitleLine>? {
        val file = cacheFile(target.trackId)
        if (!file.isFile) {
            return null
        }
        return runCatching {
            val lines = file.readLines(Charsets.UTF_8)
            if (lines.firstOrNull() != HEADER) {
                return@runCatching null
            }
            val lineStart = lines.indexOf(KEY_LINES)
            if (lineStart <= 0) {
                return@runCatching null
            }
            val metadata = lines
                .subList(1, lineStart)
                .mapNotNull { raw ->
                    val separator = raw.indexOf('=')
                    if (separator <= 0) {
                        null
                    } else {
                        raw.substring(0, separator) to decode(raw.substring(separator + 1))
                    }
                }
                .toMap()
            if (metadata[KEY_TRACK_ID] != target.trackId ||
                metadata[KEY_AUDIO_URI] != target.audioUri ||
                metadata[KEY_WHISPER_MODEL_ID] != whisperModelId
            ) {
                return@runCatching null
            }
            lines
                .drop(lineStart + 1)
                .mapNotNull { it.toSubtitleLineOrNull() }
                .takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    fun save(target: SubtitleGenerationTarget, whisperModelId: String, lines: List<SubtitleLine>) {
        if (lines.isEmpty()) {
            return
        }
        rootDir.mkdirs()
        val file = cacheFile(target.trackId)
        val body = buildString {
            appendLine(HEADER)
            appendLine("$KEY_TRACK_ID=${encode(target.trackId)}")
            appendLine("$KEY_AUDIO_URI=${encode(target.audioUri)}")
            appendLine("$KEY_WHISPER_MODEL_ID=${encode(whisperModelId)}")
            appendLine("$KEY_CREATED_AT=${System.currentTimeMillis()}")
            appendLine(KEY_LINES)
            lines.forEach { line ->
                appendLine(
                    listOf(
                        encode(line.id),
                        line.startMs.toString(),
                        line.endMs.toString(),
                        Base64.getEncoder().encodeToString(line.sourceText.toByteArray(Charsets.UTF_8)),
                    ).joinToString("\t"),
                )
            }
        }
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(body, Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.writeText(body, Charsets.UTF_8)
            tmp.delete()
        }
    }

    fun clear(trackId: String) {
        cacheFile(trackId).delete()
    }

    private fun cacheFile(trackId: String): File {
        val safeName = trackId
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .ifBlank { "track" }
        return File(rootDir, "$safeName.segments")
    }

    private fun String.toSubtitleLineOrNull(): SubtitleLine? {
        val parts = split('\t')
        if (parts.size != 4) {
            return null
        }
        val id = decode(parts[0])
        val startMs = parts[1].toLongOrNull() ?: return null
        val endMs = parts[2].toLongOrNull() ?: return null
        val sourceText = runCatching {
            Base64.getDecoder().decode(parts[3]).toString(Charsets.UTF_8)
        }.getOrDefault("")
        if (id.isBlank() || sourceText.isBlank() || endMs <= startMs) {
            return null
        }
        return SubtitleLine(id = id, startMs = startMs, endMs = endMs, sourceText = sourceText)
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name())
    }

    private fun decode(value: String): String {
        return URLDecoder.decode(value, Charsets.UTF_8.name())
    }

    private companion object {
        const val HEADER = "ASMRPLAYER_AI_SEGMENTS_V1"
        const val KEY_TRACK_ID = "trackId"
        const val KEY_AUDIO_URI = "audioUri"
        const val KEY_WHISPER_MODEL_ID = "whisperModelId"
        const val KEY_CREATED_AT = "createdAt"
        const val KEY_LINES = "lines"
    }
}
