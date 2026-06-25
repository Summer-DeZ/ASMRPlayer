package io.github.summerdez.asmrplayer.data.ai

import android.content.Context
import android.util.Log
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleSettings
import io.github.summerdez.asmrplayer.domain.model.SubtitleGenerationTarget
import io.github.summerdez.asmrplayer.domain.model.SubtitleLine
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.CancellationException

internal class SceneContextBuilder(
    private val rootDir: File,
    private val translator: OpenAiCompatibleTranslator = OpenAiCompatibleTranslator(),
) {
    constructor(
        context: Context,
        translator: OpenAiCompatibleTranslator = OpenAiCompatibleTranslator(),
    ) : this(File(context.cacheDir, "ai-subtitles/scene-contexts"), translator)

    suspend fun loadOrBuild(
        settings: AiSubtitleSettings,
        target: SubtitleGenerationTarget,
        sourceLines: List<SubtitleLine>,
    ): SceneContext {
        load(target, settings, sourceLines)?.let { return it }
        return try {
            translator.summarizeSceneContext(settings, target, sourceLines).also { sceneContext ->
                save(target, settings, sourceLines, sceneContext)
            }
        } catch (error: Throwable) {
            if (error is CancellationException) {
                throw error
            }
            Log.w(TAG, "情景卡生成失败，退化为空上下文 track=${target.trackId}", error)
            SceneContext()
        }
    }

    fun load(
        target: SubtitleGenerationTarget,
        settings: AiSubtitleSettings,
        sourceLines: List<SubtitleLine>,
    ): SceneContext? {
        val file = cacheFile(target.trackId)
        if (!file.isFile) {
            return null
        }
        return runCatching {
            val lines = file.readLines(Charsets.UTF_8)
            if (lines.firstOrNull() != HEADER) {
                return@runCatching null
            }
            val contextStart = lines.indexOf(KEY_CONTEXT_JSON)
            if (contextStart <= 0 || contextStart + 1 >= lines.size) {
                return@runCatching null
            }
            val metadata = lines
                .subList(1, contextStart)
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
                metadata[KEY_TRANSLATION_ENGINE] != settings.translationEngine.name ||
                metadata[KEY_BASE_URL] != settings.activeBaseUrl.trim().trimEnd('/') ||
                metadata[KEY_MODEL] != settings.activeModel ||
                metadata[KEY_CONTEXT_TITLE] != target.contextTitle ||
                metadata[KEY_TRACK_TITLE] != target.trackTitle ||
                metadata[KEY_SCENE_CONTEXT_VERSION] != SCENE_CONTEXT_VERSION ||
                metadata[KEY_SOURCE_SIGNATURE] != aiSubtitleSourceSignature(sourceLines)
            ) {
                return@runCatching null
            }
            val json = Base64.getDecoder().decode(lines[contextStart + 1]).toString(Charsets.UTF_8)
            OpenAiCompatibleTranslator.parseSceneContextContent(json, finishReason = "stop")
        }.getOrNull()
    }

    fun save(
        target: SubtitleGenerationTarget,
        settings: AiSubtitleSettings,
        sourceLines: List<SubtitleLine>,
        sceneContext: SceneContext,
    ) {
        if (sourceLines.isEmpty()) {
            return
        }
        rootDir.mkdirs()
        val file = cacheFile(target.trackId)
        val body = buildString {
            appendLine(HEADER)
            appendLine("$KEY_TRACK_ID=${encode(target.trackId)}")
            appendLine("$KEY_AUDIO_URI=${encode(target.audioUri)}")
            appendLine("$KEY_TRANSLATION_ENGINE=${encode(settings.translationEngine.name)}")
            appendLine("$KEY_BASE_URL=${encode(settings.activeBaseUrl.trim().trimEnd('/'))}")
            appendLine("$KEY_MODEL=${encode(settings.activeModel)}")
            appendLine("$KEY_CONTEXT_TITLE=${encode(target.contextTitle)}")
            appendLine("$KEY_TRACK_TITLE=${encode(target.trackTitle)}")
            appendLine("$KEY_SCENE_CONTEXT_VERSION=${encode(SCENE_CONTEXT_VERSION)}")
            appendLine("$KEY_SOURCE_SIGNATURE=${encode(aiSubtitleSourceSignature(sourceLines))}")
            appendLine("$KEY_CREATED_AT=${System.currentTimeMillis()}")
            appendLine(KEY_CONTEXT_JSON)
            appendLine(
                Base64.getEncoder().encodeToString(
                    sceneContextToJsonString(sceneContext).toByteArray(Charsets.UTF_8),
                ),
            )
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
        return File(rootDir, "$safeName.scene")
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name())
    }

    private fun decode(value: String): String {
        return URLDecoder.decode(value, Charsets.UTF_8.name())
    }

    private companion object {
        const val TAG = "SceneContextBuilder"
        const val HEADER = "ASMRPLAYER_AI_SCENE_CONTEXT_V1"
        const val KEY_TRACK_ID = "trackId"
        const val KEY_AUDIO_URI = "audioUri"
        const val KEY_TRANSLATION_ENGINE = "translationEngine"
        const val KEY_BASE_URL = "baseUrl"
        const val KEY_MODEL = "model"
        const val KEY_CONTEXT_TITLE = "contextTitle"
        const val KEY_TRACK_TITLE = "trackTitle"
        const val KEY_SCENE_CONTEXT_VERSION = "sceneContextVersion"
        const val KEY_SOURCE_SIGNATURE = "sourceSignature"
        const val KEY_CREATED_AT = "createdAt"
        const val KEY_CONTEXT_JSON = "contextJson"
    }
}

internal fun sceneContextSignature(sceneContext: SceneContext): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(sceneContext.signatureMaterial.toByteArray(Charsets.UTF_8))
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

internal fun sceneContextToJsonString(sceneContext: SceneContext): String {
    val glossary = sceneContext.glossary
        .toSortedMap()
        .entries
        .joinToString(",") { (key, value) -> "${jsonStringLiteral(key)}:${jsonStringLiteral(value)}" }
    return buildString {
        append("{")
        append("\"scene\":${jsonStringLiteral(sceneContext.scene)},")
        append("\"speaker\":${jsonStringLiteral(sceneContext.speaker)},")
        append("\"listenerAddress\":${jsonStringLiteral(sceneContext.listenerAddress)},")
        append("\"tone\":${jsonStringLiteral(sceneContext.tone)},")
        append("\"glossary\":{$glossary},")
        append("\"summary\":${jsonStringLiteral(sceneContext.rawSummary)}")
        append("}")
    }
}

internal fun jsonStringLiteral(value: String): String {
    val escaped = buildString {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
    return "\"$escaped\""
}
