package io.github.summerdez.asmrplayer.data.ai

import io.github.summerdez.asmrplayer.domain.model.AiSubtitleSettings
import io.github.summerdez.asmrplayer.domain.model.AiTranslationEngine
import io.github.summerdez.asmrplayer.domain.model.SubtitleLine
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class TranslationBatch(
    val lines: List<SubtitleLine>,
    val contextTitle: String,
)

class OpenAiCompatibleTranslator(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build(),
    private val maxAttempts: Int = DEFAULT_TRANSLATION_ATTEMPTS,
    private val retryDelayMillis: Long = DEFAULT_RETRY_DELAY_MS,
) {
    suspend fun translate(
        settings: AiSubtitleSettings,
        batch: TranslationBatch,
    ): List<SubtitleLine> = withContext(Dispatchers.IO) {
        if (batch.lines.isEmpty()) {
            return@withContext emptyList()
        }
        val attempts = maxAttempts.coerceAtLeast(1)
        var lastError: IOException? = null
        repeat(attempts) { attempt ->
            currentCoroutineContext().ensureActive()
            try {
                val response = execute(settings, buildRequestJson(settings, batch))
                val parsed = parseCompleteTranslationContentForRetry(response, batch.lines)
                return@withContext parsed
            } catch (error: IOException) {
                lastError = error
                val retryable = error.isRetryableTranslationFailure()
                if (!retryable) {
                    throw error
                }
                if (attempt == attempts - 1) {
                    val prefix = if (attempts > 1 && error.isRetryableTranslationFailure()) {
                        "翻译批次连续 $attempts 次失败"
                    } else {
                        "翻译失败"
                    }
                    throw IOException("$prefix：${error.message ?: "未知错误"}", error)
                }
                if (retryDelayMillis > 0) {
                    delay(retryDelayMillis * (attempt + 1))
                }
            }
        }
        throw IOException("翻译失败：${lastError?.message ?: "未知错误"}", lastError)
    }

    private fun parseCompleteTranslationContentForRetry(
        response: TranslationResponse,
        sourceLines: List<SubtitleLine>,
    ): List<SubtitleLine> {
        return try {
            parseCompleteTranslationContent(
                content = response.content,
                finishReason = response.finishReason,
                sourceLines = sourceLines,
            )
        } catch (error: IllegalArgumentException) {
            throw RetryableTranslationException(error.message ?: "翻译响应不是完整的 JSON 字幕")
        }
    }

    private suspend fun execute(settings: AiSubtitleSettings, json: JSONObject): TranslationResponse {
        val baseUrl = settings.activeBaseUrl.trim().trimEnd('/')
        if (baseUrl.isEmpty()) {
            throw IOException("翻译接口地址为空")
        }
        if (settings.translationEngine.requiresApiKey && settings.activeApiKey.isBlank()) {
            throw IOException("请先填写 API Key")
        }
        val requestBuilder = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Content-Type", "application/json")
            .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
        if (settings.activeApiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${settings.activeApiKey}")
        }
        val response = try {
            client.newCall(requestBuilder.build()).execute()
        } catch (error: IOException) {
            throw IOException(networkErrorMessage(settings, error), error)
        }
        response.use { response ->
            currentCoroutineContext().ensureActive()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("翻译失败：HTTP ${response.code}${responseErrorHint(body)}")
            }
            val root = JSONObject(body)
            val content = root.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
                .trim()
            if (content.isEmpty()) {
                throw RetryableTranslationException("翻译响应为空")
            }
            val finishReason = root.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optString("finish_reason")
                .orEmpty()
            return TranslationResponse(content = content, finishReason = finishReason)
        }
    }

    private fun networkErrorMessage(settings: AiSubtitleSettings, error: IOException): String {
        return when {
            error is SocketTimeoutException || error.message.equals("timeout", ignoreCase = true) ->
                "翻译请求超时：${settings.translationEngine.label} 响应超过 ${CALL_TIMEOUT_SECONDS} 秒，请检查网络、接口地址和模型名后重试"
            error is UnknownHostException ->
                "翻译接口无法解析：请检查接口地址和网络连接"
            else ->
                "翻译请求失败：${error.message ?: "网络异常"}"
        }
    }

    private fun responseErrorHint(body: String): String {
        val message = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")
        return if (message.isBlank()) "" else "：$message"
    }

    private fun buildRequestJson(settings: AiSubtitleSettings, batch: TranslationBatch): JSONObject {
        val system = """
            你是 ASMR 日译中字幕翻译器。请保持亲密、轻柔、自然的中文口语，保留耳语、呼吸、拟声词和 ASMR 术语的氛围。
            根据作品情境翻译，不解释，不增删时间轴。必须输出 json 对象，格式示例：{"1":"译文","2":"译文"}。
        """.trimIndent()
        val user = buildString {
            appendLine("作品情境：${batch.contextTitle.ifBlank { "ASMR 音声" }}")
            appendLine("请把下面日文字幕逐句翻译为中文，并只返回 json：")
            batch.lines.forEach { line ->
                appendLine("${line.id}: ${line.sourceText}")
            }
        }
        val root = JSONObject()
            .put("model", settings.activeModel)
            .put("temperature", 0.4)
            .put("max_tokens", 4096)
            .put(
                "messages",
                org.json.JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user)),
            )
        if (settings.translationEngine == AiTranslationEngine.DEEPSEEK) {
            root
                .put("response_format", JSONObject().put("type", "json_object"))
                .put("thinking", JSONObject().put("type", "disabled"))
        }
        return root
    }

    companion object {
        private const val DEFAULT_TRANSLATION_ATTEMPTS = 3
        private const val DEFAULT_RETRY_DELAY_MS = 750L
        private const val CONNECT_TIMEOUT_SECONDS = 20L
        private const val WRITE_TIMEOUT_SECONDS = 60L
        private const val READ_TIMEOUT_SECONDS = 120L
        private const val CALL_TIMEOUT_SECONDS = 180L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun parseTranslationObject(content: String, sourceLines: List<SubtitleLine>): List<SubtitleLine> {
            val normalized = content
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val root = parseFlatStringObject(normalized)
            return sourceLines.mapNotNull { line ->
                val text = root[line.id].orEmpty().trim()
                if (text.isEmpty()) {
                    null
                } else {
                    line.copy(translatedText = text)
                }
            }
        }

        fun parseTranslationContent(content: String, sourceLines: List<SubtitleLine>): List<SubtitleLine> {
            val strict = runCatching {
                parseTranslationObject(content, sourceLines)
            }.getOrDefault(emptyList())
            if (strict.size == sourceLines.size) {
                return strict
            }
            return parseLoose(content, sourceLines)
        }

        fun parseCompleteTranslationContent(
            content: String,
            finishReason: String,
            sourceLines: List<SubtitleLine>,
        ): List<SubtitleLine> {
            if (finishReason.equals("length", ignoreCase = true)) {
                throw IllegalArgumentException("翻译响应被模型截断")
            }
            val parsed = parseTranslationContent(content, sourceLines)
            if (parsed.size != sourceLines.size) {
                throw IllegalArgumentException("翻译响应不是完整的 JSON 字幕")
            }
            return parsed
        }

        fun parseLoose(content: String, sourceLines: List<SubtitleLine>): List<SubtitleLine> {
            val objectStart = content.indexOf('{')
            val objectEnd = content.lastIndexOf('}')
            if (objectStart >= 0 && objectEnd > objectStart) {
                try {
                    return parseTranslationObject(content.substring(objectStart, objectEnd + 1), sourceLines)
                } catch (_: IllegalArgumentException) {
                    // Fall through to line-based fallback.
                }
            }
            val translatedById = mutableMapOf<String, String>()
            content.lines().forEach { rawLine ->
                val line = rawLine.trim()
                val separator = listOf(":", "：", "=>").firstOrNull { line.contains(it) } ?: return@forEach
                val id = line.substringBefore(separator).trim().trim('"')
                val value = line.substringAfter(separator).trim().trim('"', ',', ' ')
                if (id.isNotBlank() && value.isNotBlank()) {
                    translatedById[id] = value
                }
            }
            return sourceLines.mapNotNull { line ->
                translatedById[line.id]?.let { line.copy(translatedText = it) }
            }
        }

        private fun parseFlatStringObject(content: String): Map<String, String> {
            val trimmed = content.trim()
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
                throw IllegalArgumentException("Not a JSON object")
            }
            return Regex("\"((?:\\\\.|[^\"])*)\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
                .findAll(trimmed)
                .associate { match ->
                    unescapeJson(match.groupValues[1]) to unescapeJson(match.groupValues[2])
                }
        }

        private fun unescapeJson(value: String): String {
            val builder = StringBuilder()
            var index = 0
            while (index < value.length) {
                val char = value[index]
                if (char == '\\' && index + 1 < value.length) {
                    val next = value[index + 1]
                    builder.append(
                        when (next) {
                            '"' -> '"'
                            '\\' -> '\\'
                            '/' -> '/'
                            'b' -> '\b'
                            'f' -> '\u000C'
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            'u' -> {
                                if (index + 5 < value.length) {
                                    val hex = value.substring(index + 2, index + 6)
                                    index += 4
                                    hex.toIntOrNull(16)?.toChar() ?: next
                                } else {
                                    next
                                }
                            }
                            else -> next
                        },
                    )
                    index += 2
                } else {
                    builder.append(char)
                    index++
                }
            }
            return builder.toString()
        }
    }
}

private data class TranslationResponse(
    val content: String,
    val finishReason: String,
)

private class RetryableTranslationException(message: String) : IOException(message)

private fun IOException.isRetryableTranslationFailure(): Boolean {
    return this is RetryableTranslationException ||
        this is SocketTimeoutException ||
        cause is SocketTimeoutException ||
        message.equals("timeout", ignoreCase = true) ||
        cause?.message.equals("timeout", ignoreCase = true)
}
