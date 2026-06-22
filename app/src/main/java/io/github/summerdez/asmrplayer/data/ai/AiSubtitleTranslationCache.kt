package io.github.summerdez.asmrplayer.data.ai

import android.content.Context
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleSettings
import io.github.summerdez.asmrplayer.domain.model.SubtitleGenerationTarget
import io.github.summerdez.asmrplayer.domain.model.SubtitleLine
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64

internal class AiSubtitleTranslationCache(
    private val rootDir: File,
) {
    constructor(context: Context) : this(File(context.cacheDir, "ai-subtitles/translations"))

    fun load(
        target: SubtitleGenerationTarget,
        whisperModelId: String,
        settings: AiSubtitleSettings,
        sourceLines: List<SubtitleLine>,
        sceneContext: SceneContext,
    ): List<SubtitleLine>? {
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
                metadata[KEY_WHISPER_MODEL_ID] != whisperModelId ||
                metadata[KEY_TRANSLATION_ENGINE] != settings.translationEngine.name ||
                metadata[KEY_BASE_URL] != settings.activeBaseUrl.trim().trimEnd('/') ||
                metadata[KEY_MODEL] != settings.activeModel ||
                metadata[KEY_ADULT_CONTENT_TRANSLATION] != settings.allowAdultContentTranslation.toString() ||
                metadata[KEY_CONTEXT_TITLE] != target.contextTitle ||
                metadata[KEY_PROMPT_VERSION] != PROMPT_VERSION ||
                metadata[KEY_TRANSLATION_PROTOCOL_VERSION] != TRANSLATION_PROTOCOL_VERSION ||
                metadata[KEY_SCENE_CONTEXT_VERSION] != SCENE_CONTEXT_VERSION ||
                metadata[KEY_BATCH_STRATEGY_VERSION] != BATCH_STRATEGY_VERSION ||
                metadata[KEY_SCENE_CONTEXT_SIGNATURE] != sceneContextSignature(sceneContext) ||
                metadata[KEY_SOURCE_SIGNATURE] != aiSubtitleSourceSignature(sourceLines)
            ) {
                return@runCatching null
            }
            lines
                .drop(lineStart + 1)
                .mapNotNull { it.toSubtitleLineOrNull() }
                .takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    fun save(
        target: SubtitleGenerationTarget,
        whisperModelId: String,
        settings: AiSubtitleSettings,
        sourceLines: List<SubtitleLine>,
        translatedLines: List<SubtitleLine>,
        sceneContext: SceneContext,
    ) {
        val translatedById = translatedLines
            .filter { it.translatedText.isNotBlank() }
            .associateBy { it.id }
        val completed = sourceLines.mapNotNull { source -> translatedById[source.id] }
        if (sourceLines.isEmpty() || completed.isEmpty()) {
            return
        }
        rootDir.mkdirs()
        val file = cacheFile(target.trackId)
        val body = buildString {
            appendLine(HEADER)
            appendLine("$KEY_TRACK_ID=${encode(target.trackId)}")
            appendLine("$KEY_AUDIO_URI=${encode(target.audioUri)}")
            appendLine("$KEY_WHISPER_MODEL_ID=${encode(whisperModelId)}")
            appendLine("$KEY_TRANSLATION_ENGINE=${encode(settings.translationEngine.name)}")
            appendLine("$KEY_BASE_URL=${encode(settings.activeBaseUrl.trim().trimEnd('/'))}")
            appendLine("$KEY_MODEL=${encode(settings.activeModel)}")
            appendLine("$KEY_ADULT_CONTENT_TRANSLATION=${encode(settings.allowAdultContentTranslation.toString())}")
            appendLine("$KEY_CONTEXT_TITLE=${encode(target.contextTitle)}")
            appendLine("$KEY_PROMPT_VERSION=${encode(PROMPT_VERSION)}")
            appendLine("$KEY_TRANSLATION_PROTOCOL_VERSION=${encode(TRANSLATION_PROTOCOL_VERSION)}")
            appendLine("$KEY_SCENE_CONTEXT_VERSION=${encode(SCENE_CONTEXT_VERSION)}")
            appendLine("$KEY_BATCH_STRATEGY_VERSION=${encode(BATCH_STRATEGY_VERSION)}")
            appendLine("$KEY_SCENE_CONTEXT_SIGNATURE=${encode(sceneContextSignature(sceneContext))}")
            appendLine("$KEY_SOURCE_SIGNATURE=${encode(aiSubtitleSourceSignature(sourceLines))}")
            appendLine("$KEY_CREATED_AT=${System.currentTimeMillis()}")
            appendLine(KEY_LINES)
            completed.forEach { line ->
                appendLine(
                    listOf(
                        encode(line.id),
                        line.startMs.toString(),
                        line.endMs.toString(),
                        Base64.getEncoder().encodeToString(line.sourceText.toByteArray(Charsets.UTF_8)),
                        Base64.getEncoder().encodeToString(line.translatedText.toByteArray(Charsets.UTF_8)),
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
        return File(rootDir, "$safeName.translations")
    }

    private fun String.toSubtitleLineOrNull(): SubtitleLine? {
        val parts = split('\t')
        if (parts.size != 5) {
            return null
        }
        val id = decode(parts[0])
        val startMs = parts[1].toLongOrNull() ?: return null
        val endMs = parts[2].toLongOrNull() ?: return null
        val sourceText = runCatching {
            Base64.getDecoder().decode(parts[3]).toString(Charsets.UTF_8)
        }.getOrDefault("")
        val translatedText = runCatching {
            Base64.getDecoder().decode(parts[4]).toString(Charsets.UTF_8)
        }.getOrDefault("")
        if (id.isBlank() || sourceText.isBlank() || translatedText.isBlank() || endMs <= startMs) {
            return null
        }
        return SubtitleLine(
            id = id,
            startMs = startMs,
            endMs = endMs,
            sourceText = sourceText,
            translatedText = translatedText,
        )
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name())
    }

    private fun decode(value: String): String {
        return URLDecoder.decode(value, Charsets.UTF_8.name())
    }

    private companion object {
        const val HEADER = "ASMRPLAYER_AI_TRANSLATIONS_V1"
        const val PROMPT_VERSION = TRANSLATION_PROMPT_VERSION
        const val KEY_TRACK_ID = "trackId"
        const val KEY_AUDIO_URI = "audioUri"
        const val KEY_WHISPER_MODEL_ID = "whisperModelId"
        const val KEY_TRANSLATION_ENGINE = "translationEngine"
        const val KEY_BASE_URL = "baseUrl"
        const val KEY_MODEL = "model"
        const val KEY_ADULT_CONTENT_TRANSLATION = "adultContentTranslation"
        const val KEY_CONTEXT_TITLE = "contextTitle"
        const val KEY_PROMPT_VERSION = "promptVersion"
        const val KEY_TRANSLATION_PROTOCOL_VERSION = "translationProtocolVersion"
        const val KEY_SCENE_CONTEXT_VERSION = "sceneContextVersion"
        const val KEY_BATCH_STRATEGY_VERSION = "batchStrategyVersion"
        const val KEY_SCENE_CONTEXT_SIGNATURE = "sceneContextSignature"
        const val KEY_SOURCE_SIGNATURE = "sourceSignature"
        const val KEY_CREATED_AT = "createdAt"
        const val KEY_LINES = "lines"
    }
}
