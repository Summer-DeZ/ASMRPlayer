package io.github.summerdez.asmrplayer.data.ai

import io.github.summerdez.asmrplayer.domain.model.AiSubtitleSettings
import io.github.summerdez.asmrplayer.domain.model.AiTranslationEngine
import io.github.summerdez.asmrplayer.domain.model.SubtitleGenerationTarget
import io.github.summerdez.asmrplayer.domain.model.SubtitleLine
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

internal const val TRANSLATION_PROTOCOL_VERSION = "ordered-lines-v1"
internal const val SCENE_CONTEXT_VERSION = "scene-context-v2"
internal const val BATCH_STRATEGY_VERSION = "batch-80-window-4-deepseek-full-context-v1"
internal const val TRANSLATION_PROMPT_VERSION = "asr-ja-zh-ordered-v6"

data class TranslationContextLine(
    val id: String,
    val ja: String,
    val zh: String = "",
)

data class SceneContext(
    val scene: String = "",
    val speaker: String = "",
    val listenerAddress: String = "",
    val tone: String = "",
    val glossary: Map<String, String> = emptyMap(),
    val rawSummary: String = "",
) {
    val signatureMaterial: String
        get() = listOf(
            scene,
            speaker,
            listenerAddress,
            tone,
            glossary.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" },
            rawSummary,
        ).joinToString("\n")
}

data class TranslationBatch(
    val lines: List<SubtitleLine>,
    val contextTitle: String,
    val sceneContext: SceneContext = SceneContext(),
    val globalSourceContext: List<TranslationContextLine> = emptyList(),
    val previousContext: List<TranslationContextLine> = emptyList(),
    val nextContext: List<TranslationContextLine> = emptyList(),
    val batchIndex: Int = 0,
    val totalBatches: Int = 1,
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
    private val requestExecutor: TranslationRequestExecutor? = null,
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
                val response = execute(settings, buildTranslationRequest(settings, batch, lastError?.message))
                return@withContext parseCompleteTranslationContentForRetry(response, batch.lines)
            } catch (error: IOException) {
                if (!error.isRetryableTranslationFailure()) {
                    throw error
                }
                lastError = error
                if (attempt < attempts - 1 && retryDelayMillis > 0) {
                    delay(retryDelayMillis * (attempt + 1))
                }
            }
        }
        return@withContext translateLineByLine(settings, batch, lastError)
    }

    suspend fun summarizeSceneContext(
        settings: AiSubtitleSettings,
        target: SubtitleGenerationTarget,
        sourceLines: List<SubtitleLine>,
    ): SceneContext = withContext(Dispatchers.IO) {
        if (sourceLines.isEmpty()) {
            return@withContext SceneContext()
        }
        val response = execute(settings, buildSceneContextRequest(settings, target, sourceLines))
        parseSceneContextContent(response.content, response.finishReason)
    }

    private suspend fun translateLineByLine(
        settings: AiSubtitleSettings,
        batch: TranslationBatch,
        batchError: IOException?,
    ): List<SubtitleLine> {
        val translated = mutableListOf<SubtitleLine>()
        val attempts = maxAttempts.coerceAtLeast(1)
        batch.lines.forEachIndexed { index, line ->
            var lineResult: SubtitleLine? = null
            var lastLineError: IOException? = null
            repeat(attempts) { attempt ->
                if (lineResult != null) {
                    return@repeat
                }
                currentCoroutineContext().ensureActive()
                try {
                    val singleBatch = batch.copy(
                        lines = listOf(line),
                        previousContext = singleLinePreviousContext(batch, translated),
                        nextContext = singleLineNextContext(batch, index),
                    )
                    val response = execute(
                        settings,
                        buildTranslationRequest(settings, singleBatch, lastLineError?.message ?: batchError?.message),
                    )
                    lineResult = parseCompleteTranslationContentForRetry(response, listOf(line)).single()
                } catch (error: IOException) {
                    if (!error.isRetryableTranslationFailure()) {
                        throw error
                    }
                    lastLineError = error
                    if (attempt < attempts - 1 && retryDelayMillis > 0) {
                        delay(retryDelayMillis * (attempt + 1))
                    }
                }
            }
            translated += lineResult ?: throw IOException(
                "第 ${line.id} 句无法稳定翻译：${lastLineError?.message ?: batchError?.message ?: "未知错误"}",
                lastLineError ?: batchError,
            )
        }
        return translated
    }

    private fun singleLinePreviousContext(
        batch: TranslationBatch,
        translatedInFallback: List<SubtitleLine>,
    ): List<TranslationContextLine> {
        val previousFromFallback = translatedInFallback.takeLast(SINGLE_LINE_CONTEXT_SIZE).map { line ->
            TranslationContextLine(id = line.id, ja = line.sourceText, zh = line.translatedText)
        }
        return (batch.previousContext + previousFromFallback).takeLast(SINGLE_LINE_CONTEXT_SIZE)
    }

    private fun singleLineNextContext(
        batch: TranslationBatch,
        lineIndex: Int,
    ): List<TranslationContextLine> {
        val nextFromBatch = batch.lines
            .drop(lineIndex + 1)
            .take(SINGLE_LINE_CONTEXT_SIZE)
            .map { line -> TranslationContextLine(id = line.id, ja = line.sourceText) }
        return (nextFromBatch + batch.nextContext).take(SINGLE_LINE_CONTEXT_SIZE)
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
            throw RetryableTranslationException(error.message ?: "翻译响应不是完整的有序 JSON 字幕")
        }
    }

    private suspend fun execute(settings: AiSubtitleSettings, request: TranslationRequest): TranslationResponse {
        requestExecutor?.let { executor ->
            return executor.execute(settings, request)
        }
        return executeHttp(settings, request.toJsonObject())
    }

    private suspend fun executeHttp(settings: AiSubtitleSettings, json: JSONObject): TranslationResponse {
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

    private fun buildTranslationRequest(
        settings: AiSubtitleSettings,
        batch: TranslationBatch,
        lastFailure: String?,
    ): TranslationRequest {
        val system = """
            你是 ASMR 日译中字幕翻译器。你只能翻译文本，绝不能输出、推测或改写任何时间轴。
            必须输出合法 json object，顶层只有 lines 数组。第 i 个返回项的 id 必须等于第 i 个输入项的 id。
            每个 id 必须恰好一条 zh；叹息、喘息、拟声、符号、无实义短句也必须保留或给出自然中文拟声，不得省略、合并、拆分、解释或输出 Markdown。
            zh 必须输出简体中文、中文拟声或中文保守占位；不要把日文假名、片假名或整段日文原样放进 zh。
            ASMR 音效翻译：舔舐、吸吮、摩擦、衣料摩擦、耳边摩擦、呼吸、喘息、湿润口腔音等非台词声音，不要硬译拟声词字面；优先输出「（舔舐声）」「（摩擦声）」「（耳边摩擦声）」「（呼吸声）」这类简短中文声音描述。
            如果用户消息包含完整日文字幕全文，只能把它当作全局只读背景；输出范围仍然只由「待翻译 lines json」决定。
            前文窗口和后文窗口只供理解语气和称呼，不要输出窗口里的 id。
            逐行保守翻译：不得把情景卡、标题、术语表或后文里出现但当前日文没有对应词的内容塞进当前 zh。
            如果一行像 ASR 误识别、短碎片、半个词或无法确定含义，输出「（听不清）」或「（含混）」这类中文保守占位，不要保留日文，也不要硬猜成完整中文句子。
            例如当前行不含「女の子/女子/少女/女」等对应词时，不得译出「女孩子」；当前行不含「寒い/冷たい/冷える」等对应词时，不得译出「很冷」。
            ${adultContentRule(settings)}
        """.trimIndent()
        val user = buildString {
            appendLine("作品标题：${batch.contextTitle.ifBlank { "ASMR 音声" }}")
            appendLine("全局情景卡 json：")
            appendLine(sceneContextToJsonString(batch.sceneContext))
            if (shouldIncludeGlobalSourceContext(settings, batch)) {
                appendLine("完整日文字幕全文 json（长上下文模型只读背景；真正需要输出的 id 只看后面的待翻译 lines）：")
                appendLine(contextLinesJsonString(batch.globalSourceContext, includeZh = false))
                appendLine("全文背景规则：完整字幕只用于理解作品整体、称呼、语气、术语和音效；不得翻译全文，不得输出待翻译 lines 之外的 id，不得把远处剧情塞进当前句。")
            }
            appendLine("批次：${batch.batchIndex + 1}/${batch.totalBatches}")
            appendLine("只读前文窗口 json（不要输出这些 id）：")
            appendLine(contextLinesJsonString(batch.previousContext, includeZh = true))
            appendLine("待翻译 lines json（只翻译这些 id，保留同顺序）：")
            appendLine(sourceLinesJsonString(batch.lines))
            appendLine("只读后文窗口 json（不要输出这些 id）：")
            appendLine(contextLinesJsonString(batch.nextContext, includeZh = false))
            appendLine("质量规则：每个 zh 必须主要来自同一 id 的 ja；上下文只能消歧，不能替代当前行。短碎片或疑似识别错误时，输出中文保守占位，不要保留日文，不要凭空补人物、感受或动作。舔舐、摩擦、呼吸等 ASMR 非台词声音优先输出「（舔舐声）」「（摩擦声）」等中文声音描述。")
            if (settings.allowAdultContentTranslation) {
                appendLine("成人内容直译已开启：原文如果包含成人向或敏感词，请按原意翻成直接、自然的中文，不要用省略号、含糊词或同义规避；但仍不得添加当前 ja 没有的成人内容。")
            } else {
                appendLine("成人内容直译未开启：保持普通字幕翻译风格，不额外强化成人向表达。")
            }
            appendLine("返回格式必须是：{\"lines\":[{\"id\":\"输入 id\",\"zh\":\"中文译文\"}]}")
            if (!lastFailure.isNullOrBlank()) {
                appendLine("上次失败原因：$lastFailure。请严格修正 JSON 结构、id 顺序和每行 zh。")
            }
        }
        return TranslationRequest(
            model = settings.activeModel,
            temperature = 0.2,
            maxTokens = estimatedMaxTokens(batch),
            messages = listOf(
                TranslationMessage(role = "system", content = system),
                TranslationMessage(role = "user", content = user),
            ),
            responseFormatJsonObject = settings.translationEngine == AiTranslationEngine.DEEPSEEK,
            disableThinking = shouldDisableThinking(settings),
        )
    }

    private fun buildSceneContextRequest(
        settings: AiSubtitleSettings,
        target: SubtitleGenerationTarget,
        sourceLines: List<SubtitleLine>,
    ): TranslationRequest {
        val system = """
            你是 ASMR 日语字幕情景分析器。请只输出合法 json object，用中文概括场景、说话者、听者称呼、语气和术语表。
            不要输出 Markdown，不要解释，不要包含任何时间轴。顶层字段为 scene、speaker、listenerAddress、tone、glossary、summary。
            只能根据标题和字幕中明确出现的信息总结；不要从封面、题材常识或想象中臆造人物、关系或术语。
            glossary 的 key 必须是字幕里实际出现过的日文原词，value 才是对应中文译法；不确定的词不要加入 glossary。
        """.trimIndent()
        val user = buildString {
            appendLine("作品标题：${target.contextTitle.ifBlank { "ASMR 音声" }}")
            appendLine("单曲名：${target.trackTitle.ifBlank { "未命名音轨" }}")
            appendLine("请从以下日文字幕提取稳定情景卡，glossary 只放固定称呼、角色名、ASMR 术语或容易误译的词：")
            appendLine(sourceLinesJsonString(sourceLines))
            appendLine("注意：字幕可能来自 ASR，短碎片和疑似误识别不要被当成确定术语；不要把未在字幕出现的角色或称呼写入 glossary。")
            appendLine("返回示例：{\"scene\":\"姐姐在床边轻声哄睡\",\"speaker\":\"成年女性，姐姐角色\",\"listenerAddress\":\"对听者称你\",\"tone\":\"轻柔、耳语、睡前安抚\",\"glossary\":{\"耳かき\":\"掏耳朵\"},\"summary\":\"睡前陪伴和耳边低语为主\"}")
        }
        return TranslationRequest(
            model = settings.activeModel,
            temperature = 0.2,
            maxTokens = 2_048,
            messages = listOf(
                TranslationMessage(role = "system", content = system),
                TranslationMessage(role = "user", content = user),
            ),
            responseFormatJsonObject = settings.translationEngine == AiTranslationEngine.DEEPSEEK,
            disableThinking = shouldDisableThinking(settings),
        )
    }

    private fun estimatedMaxTokens(batch: TranslationBatch): Int {
        val sourceChars = batch.lines.sumOf { it.id.length + it.sourceText.length }
        val contextChars = batch.previousContext.sumOf { it.id.length + it.ja.length + it.zh.length } +
            batch.nextContext.sumOf { it.id.length + it.ja.length } +
            batch.sceneContext.signatureMaterial.length
        return (sourceChars * 4 + contextChars * 2 + 2_048).coerceIn(4_096, 12_000)
    }

    private fun shouldIncludeGlobalSourceContext(
        settings: AiSubtitleSettings,
        batch: TranslationBatch,
    ): Boolean {
        return settings.translationEngine == AiTranslationEngine.DEEPSEEK &&
            batch.globalSourceContext.isNotEmpty()
    }

    private fun shouldDisableThinking(settings: AiSubtitleSettings): Boolean {
        if (settings.translationEngine != AiTranslationEngine.DEEPSEEK) {
            return false
        }
        val baseUrl = settings.activeBaseUrl.lowercase(Locale.US)
        val model = settings.activeModel.lowercase(Locale.US)
        return baseUrl.contains("deepseek.com") || model.startsWith("deepseek-")
    }

    private fun adultContentRule(settings: AiSubtitleSettings): String {
        return if (settings.allowAdultContentTranslation) {
            "成人内容直译：当前 ja 若明确包含成人向或敏感表达，应按原文直译成中文敏感词，不要因为敏感而弱化、消音、跳过或改写；但不能新增原文没有的成人内容。"
        } else {
            "成人内容：按普通字幕标准翻译，不要因情景卡自行添加成人向内容。"
        }
    }

    private fun sourceLinesJsonString(lines: List<SubtitleLine>): String {
        return lines.joinToString(prefix = "[", postfix = "]") { line ->
            """{"id":${jsonStringLiteral(line.id)},"ja":${jsonStringLiteral(line.sourceText)}}"""
        }
    }

    private fun contextLinesJsonString(lines: List<TranslationContextLine>, includeZh: Boolean): String {
        return lines.joinToString(prefix = "[", postfix = "]") { line ->
            buildString {
                append("""{"id":${jsonStringLiteral(line.id)},"ja":${jsonStringLiteral(line.ja)}""")
                if (includeZh) {
                    append(""","zh":${jsonStringLiteral(line.zh)}""")
                }
                append("}")
            }
        }
    }

    companion object {
        private const val DEFAULT_TRANSLATION_ATTEMPTS = 3
        private const val DEFAULT_RETRY_DELAY_MS = 750L
        private const val CONNECT_TIMEOUT_SECONDS = 20L
        private const val WRITE_TIMEOUT_SECONDS = 60L
        private const val READ_TIMEOUT_SECONDS = 120L
        private const val CALL_TIMEOUT_SECONDS = 180L
        private const val SINGLE_LINE_CONTEXT_SIZE = 4
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun parseOrderedTranslationContent(content: String, sourceLines: List<SubtitleLine>): List<SubtitleLine> {
            val normalized = stripMarkdownFence(content)
            val arrayContent = Regex(
                pattern = """^\s*\{\s*"lines"\s*:\s*\[(.*)]\s*\}\s*$""",
                option = RegexOption.DOT_MATCHES_ALL,
            ).matchEntire(normalized)?.groupValues?.get(1)
                ?: throw IllegalArgumentException("翻译响应缺少 lines 数组")
            val lines = parseOrderedLineItems(arrayContent)
            if (lines.size != sourceLines.size) {
                throw IllegalArgumentException("翻译响应行数不匹配：期望 ${sourceLines.size}，实际 ${lines.size}")
            }
            val seenIds = mutableSetOf<String>()
            return sourceLines.mapIndexed { index, source ->
                val item = lines[index]
                val returnedId = item.id.trim()
                if (!seenIds.add(returnedId)) {
                    throw IllegalArgumentException("翻译响应重复 id：$returnedId")
                }
                if (returnedId != source.id) {
                    throw IllegalArgumentException("翻译响应 id 顺序错位：第 ${index + 1} 项期望 ${source.id}，实际 $returnedId")
                }
                val translatedText = item.zh.trim()
                if (translatedText.isBlank()) {
                    throw IllegalArgumentException("翻译响应第 ${source.id} 句为空")
                }
                if (containsJapaneseKana(translatedText)) {
                    throw IllegalArgumentException("翻译响应第 ${source.id} 句仍含日文假名")
                }
                source.copy(translatedText = translatedText)
            }
        }

        fun parseSceneContextContent(content: String, finishReason: String): SceneContext {
            if (finishReason.equals("length", ignoreCase = true)) {
                throw IllegalArgumentException("情景卡响应被模型截断")
            }
            val normalized = stripMarkdownFence(content)
            if (!normalized.trim().startsWith("{") || !normalized.trim().endsWith("}")) {
                throw IllegalArgumentException("情景卡响应不是合法 JSON object")
            }
            val glossary = Regex(
                pattern = """"glossary"\s*:\s*\{(.*?)\}""",
                option = RegexOption.DOT_MATCHES_ALL,
            ).find(normalized)
                ?.groupValues
                ?.get(1)
                ?.let { body -> runCatching { parseFlatStringObject("{$body}") }.getOrDefault(emptyMap()) }
                .orEmpty()
            val summary = jsonStringField(normalized, "summary")
                .ifBlank { jsonStringField(normalized, "rawSummary") }
            return SceneContext(
                scene = jsonStringField(normalized, "scene"),
                speaker = jsonStringField(normalized, "speaker"),
                listenerAddress = jsonStringField(normalized, "listenerAddress"),
                tone = jsonStringField(normalized, "tone"),
                glossary = glossary,
                rawSummary = summary,
            )
        }

        private fun parseOrderedLineItems(arrayContent: String): List<OrderedLineItem> {
            val itemRegex = Regex(
                pattern = """\{([^{}]*)\}""",
                option = RegexOption.DOT_MATCHES_ALL,
            )
            val items = mutableListOf<OrderedLineItem>()
            var cursor = 0
            while (cursor < arrayContent.length) {
                cursor = skipWhitespace(arrayContent, cursor)
                if (cursor >= arrayContent.length) {
                    break
                }
                val match = itemRegex.find(arrayContent, cursor)
                if (match == null || match.range.first != cursor) {
                    throw IllegalArgumentException("翻译响应 lines 项结构错误")
                }
                val fields = parseSimpleJsonObjectFields(match.groupValues[1])
                val id = fields["id"]?.trim().orEmpty()
                val zh = fields["zh"]?.trim().orEmpty()
                if (id.isBlank() || !fields.containsKey("zh")) {
                    throw IllegalArgumentException("翻译响应 lines 项缺少 id 或 zh")
                }
                items += OrderedLineItem(
                    id = id,
                    zh = zh,
                )
                cursor = skipWhitespace(arrayContent, match.range.last + 1)
                if (cursor >= arrayContent.length) {
                    break
                }
                if (arrayContent[cursor] != ',') {
                    throw IllegalArgumentException("翻译响应 lines 数组分隔符错误")
                }
                cursor += 1
            }
            return items
        }

        private fun parseSimpleJsonObjectFields(objectBody: String): Map<String, String> {
            val fieldRegex = Regex(
                pattern = """"((?:\\.|[^"])*)"\s*:\s*("((?:\\.|[^"])*)"|-?\d+)""",
                option = RegexOption.DOT_MATCHES_ALL,
            )
            return fieldRegex.findAll(objectBody).associate { match ->
                val key = unescapeJson(match.groupValues[1])
                val token = match.groupValues[2]
                val rawValue = if (token.startsWith("\"") && token.endsWith("\"")) {
                    unescapeJson(token.substring(1, token.length - 1))
                } else {
                    token
                }
                key to rawValue
            }
        }

        private fun skipWhitespace(value: String, startIndex: Int): Int {
            var index = startIndex
            while (index < value.length && value[index].isWhitespace()) {
                index += 1
            }
            return index
        }

        private fun jsonStringField(content: String, key: String): String {
            return Regex(
                pattern = """"${Regex.escape(key)}"\s*:\s*"((?:\\.|[^"])*)"""",
                option = RegexOption.DOT_MATCHES_ALL,
            ).find(content)
                ?.groupValues
                ?.get(1)
                ?.let(::unescapeJson)
                ?.trim()
                .orEmpty()
        }

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
            return parseOrderedTranslationContent(content, sourceLines)
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

        private fun stripMarkdownFence(content: String): String {
            val trimmed = content.trim()
            if (!trimmed.startsWith("```")) {
                return trimmed
            }
            val withoutOpening = trimmed.lines().drop(1).joinToString("\n").trim()
            return if (withoutOpening.endsWith("```")) {
                withoutOpening.removeSuffix("```").trim()
            } else {
                withoutOpening
            }
        }

        private fun containsJapaneseKana(value: String): Boolean {
            return value.any { char ->
                char in '\u3040'..'\u309f' || char in '\u30a0'..'\u30ff'
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

        private data class OrderedLineItem(
            val id: String,
            val zh: String,
        )
    }
}

interface TranslationRequestExecutor {
    suspend fun execute(settings: AiSubtitleSettings, request: TranslationRequest): TranslationResponse
}

data class TranslationMessage(
    val role: String,
    val content: String,
)

data class TranslationRequest(
    val model: String,
    val temperature: Double,
    val maxTokens: Int,
    val messages: List<TranslationMessage>,
    val responseFormatJsonObject: Boolean,
    val disableThinking: Boolean,
) {
    fun toJsonObject(): JSONObject {
        val root = JSONObject()
            .put("model", model)
            .put("temperature", temperature)
            .put("max_tokens", maxTokens)
            .put(
                "messages",
                JSONArray().also { array ->
                    messages.forEach { message ->
                        array.put(JSONObject().put("role", message.role).put("content", message.content))
                    }
                },
            )
        if (responseFormatJsonObject) {
            root.put("response_format", JSONObject().put("type", "json_object"))
        }
        if (disableThinking) {
            root.put("thinking", JSONObject().put("type", "disabled"))
        }
        return root
    }
}

data class TranslationResponse(
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
