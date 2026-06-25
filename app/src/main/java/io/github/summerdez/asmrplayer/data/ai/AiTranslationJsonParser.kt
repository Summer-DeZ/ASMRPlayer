package io.github.summerdez.asmrplayer.data.ai

import io.github.summerdez.asmrplayer.domain.model.SubtitleLine

internal object AiTranslationJsonParser {
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
            if (containsJapaneseHiragana(translatedText)) {
                throw IllegalArgumentException("翻译响应第 ${source.id} 句仍含平假名")
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

    fun translationQualityWarning(lines: List<SubtitleLine>): String {
        return if (lines.any { containsJapaneseKatakana(it.translatedText) }) {
            "AI 字幕已生成，但译文中仍有片假名，建议人工检查。"
        } else {
            ""
        }
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

    private fun containsJapaneseHiragana(value: String): Boolean {
        return value.any { char ->
            char in '\u3040'..'\u309f'
        }
    }

    private fun containsJapaneseKatakana(value: String): Boolean {
        return value.any { char ->
            char in '\u30a0'..'\u30ff' ||
                char in '\u31f0'..'\u31ff' ||
                char in '\uff66'..'\uff9d'
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
