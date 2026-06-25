package io.github.summerdez.asmrplayer.data.ai

import io.github.summerdez.asmrplayer.domain.model.SubtitleLine
import java.io.IOException
import kotlin.math.roundToLong

internal object RemoteWhisperResponseParser {
    fun parseHealthResponse(body: String): RemoteWhisperHealth {
        return RemoteWhisperHealth(
            status = jsonStringField(body, "status").orEmpty(),
            service = jsonStringField(body, "service").orEmpty(),
            version = jsonStringField(body, "version").orEmpty(),
            device = jsonStringField(body, "device").orEmpty(),
            defaultModel = jsonStringField(body, "default_model").orEmpty(),
            modelsReady = jsonBooleanField(body, "models_ready") ?: false,
        )
    }

    fun parseCreateJobResponse(body: String): RemoteTranscriptionJob {
        val jobId = jsonStringField(body, "job_id")
            ?: jsonStringField(body, "jobId")
            ?: jsonStringField(body, "task_id")
            ?: jsonStringField(body, "id")
            ?: throw IOException("远程转写服务响应缺少 job_id")
        if (jobId.isBlank()) {
            throw IOException("远程转写服务返回空 job_id")
        }
        return RemoteTranscriptionJob(jobId.trim())
    }

    fun parseTranscriptionStatusResponse(body: String): RemoteTranscriptionStatus {
        val progress = jsonFloatField(body, "progress")?.let { value ->
            if (value > 1f) value / 100f else value
        }?.coerceIn(0f, 1f)
        return RemoteTranscriptionStatus(
            status = jsonStringField(body, "status").orEmpty(),
            stage = jsonStringField(body, "stage").orEmpty(),
            processedMs = jsonLongField(body, "processed_ms"),
            durationMs = jsonLongField(body, "duration_ms"),
            progress = progress,
            message = parseErrorMessage(body)
                .ifBlank { jsonStringField(body, "message").orEmpty() },
            updatedAt = jsonStringField(body, "updated_at").orEmpty(),
            previewLines = parsePreviewSegments(body),
        )
    }

    fun parseTranscriptionResultResponse(body: String): List<SubtitleLine> {
        return parseSegmentsResponse(body, allowWrappedResult = true)
    }

    fun parseErrorMessage(body: String): String {
        val nestedError = jsonRawField(body, "error")
        val nestedCode = nestedError
            ?.takeIf { it.startsWith('{') }
            ?.let { jsonStringField(it, "code") ?: jsonStringField(it, "error_code") }
        val topLevelCode = jsonStringField(body, "code") ?: jsonStringField(body, "error_code")
        return cleanJsonMessage(jsonRawField(body, "error")?.let { raw ->
            when {
                raw.startsWith('{') ->
                    jsonStringField(raw, "message")
                        ?: jsonStringField(raw, "detail")
                        ?: jsonStringField(raw, "error")
                        ?: ""
                raw.startsWith('"') -> decodeJsonString(raw)
                else -> raw
            }
        })
            .ifBlank { cleanJsonMessage(jsonStringField(body, "error_message")) }
            .ifBlank { cleanJsonMessage(jsonStringField(body, "message")) }
            .ifBlank { cleanJsonMessage(jsonStringField(body, "detail")) }
            .ifBlank { remoteErrorCodeMessage(nestedCode ?: topLevelCode) }
    }

    private fun remoteErrorCodeMessage(code: String?): String {
        return when (code.orEmpty().trim().uppercase()) {
            "INVALID_REQUEST" -> "远程转写请求参数无效，请检查音频文件和服务端配置"
            "UNAUTHORIZED" -> "远程转写服务鉴权失败，请检查 Bearer Token"
            "QUEUE_FULL" -> "远程转写服务队列已满，请稍后重试"
            "MODEL_NOT_READY" -> "远程转写模型尚未就绪，请稍后重试"
            "ASR_FAILED" -> "远程语音识别失败，请检查音频或服务端日志"
            "ALIGNMENT_FAILED" -> "远程字幕对齐失败，请检查服务端对齐模型"
            "JOB_NOT_FOUND" -> "远程转写任务不存在或已过期，请重新生成"
            "JOB_NOT_READY" -> "远程转写结果尚未准备好，请稍后重试"
            "RESULT_EXPIRED" -> "远程转写结果已过期，请重新生成"
            else -> ""
        }
    }

    private fun parseSegmentsResponse(body: String, allowWrappedResult: Boolean): List<SubtitleLine> {
        val wrappedSegmentsContent = if (allowWrappedResult) {
            jsonObjectContent(body, "result")?.let { jsonArrayContent(it, "segments") }
        } else {
            null
        }
        val segmentsContent = jsonArrayContent(body, "segments")
            ?: wrappedSegmentsContent
            ?: throw IOException("远程转写服务响应缺少 segments")
        return parseSegmentsContent(segmentsContent)
    }

    private fun parsePreviewSegments(body: String): List<SubtitleLine> {
        val segmentsContent = jsonArrayContent(body, "preview_segments")
            ?: jsonArrayContent(body, "segments")
            ?: return emptyList()
        return runCatching { parseSegmentsContent(segmentsContent) }.getOrDefault(emptyList())
    }

    private fun parseSegmentsContent(segmentsContent: String): List<SubtitleLine> {
        val segments = splitJsonObjects(segmentsContent)
        val lines = mutableListOf<SubtitleLine>()
        var lastStart = Long.MIN_VALUE
        segments.forEachIndexed { index, segment ->
            val startMs = jsonTimeMillisField(segment, "start_ms", "start")
            val endMs = jsonTimeMillisField(segment, "end_ms", "end")
            val text = jsonStringField(segment, "text").orEmpty().trim()
            if (startMs == null || endMs == null || endMs <= startMs) {
                throw IOException("远程转写服务第 ${index + 1} 段时间轴无效")
            }
            if (startMs < lastStart) {
                throw IOException("远程转写服务 segments 未按时间升序返回")
            }
            if (text.isBlank()) {
                throw IOException("远程转写服务第 ${index + 1} 段文本为空")
            }
            lastStart = startMs
            val rawId = jsonScalarField(segment, "id")
                .orEmpty()
                .trim()
                .takeUnless { it.equals("null", ignoreCase = true) }
                .orEmpty()
            lines += SubtitleLine(
                id = rawId.ifBlank { (index + 1).toString() },
                startMs = startMs,
                endMs = endMs,
                sourceText = text,
            )
        }
        return lines
    }

    private fun jsonArrayContent(json: String, field: String): String? {
        val valueStart = jsonFieldValueRange(json, field)?.first ?: return null
        val arrayStart = skipWhitespace(json, valueStart).takeIf { it < json.length && json[it] == '[' } ?: return null
        val arrayEnd = matchingJsonBoundary(json, arrayStart, '[', ']') ?: return null
        return json.substring(arrayStart + 1, arrayEnd)
    }

    private fun jsonObjectContent(json: String, field: String): String? {
        val valueStart = jsonFieldValueRange(json, field)?.first ?: return null
        val objectStart = skipWhitespace(json, valueStart).takeIf { it < json.length && json[it] == '{' } ?: return null
        val objectEnd = matchingJsonBoundary(json, objectStart, '{', '}') ?: return null
        return json.substring(objectStart + 1, objectEnd)
    }

    private fun splitJsonObjects(arrayContent: String): List<String> {
        val objects = mutableListOf<String>()
        var index = 0
        while (index < arrayContent.length) {
            index = skipWhitespaceAndCommas(arrayContent, index)
            if (index >= arrayContent.length) {
                break
            }
            if (arrayContent[index] != '{') {
                throw IOException("远程转写服务 segments 包含非 object 项")
            }
            val end = matchingJsonBoundary(arrayContent, index, '{', '}')
                ?: throw IOException("远程转写服务 segments JSON 不完整")
            objects += arrayContent.substring(index, end + 1)
            index = end + 1
        }
        return objects
    }

    private fun jsonStringField(json: String, field: String): String? {
        val raw = jsonRawField(json, field) ?: return null
        return if (raw.startsWith('"')) decodeJsonString(raw) else raw
    }

    private fun jsonLongField(json: String, field: String): Long? {
        return jsonScalarField(json, field)?.toLongOrNull()
    }

    private fun jsonFloatField(json: String, field: String): Float? {
        return jsonDoubleField(json, field)?.toFloat()
    }

    private fun jsonDoubleField(json: String, field: String): Double? {
        return jsonScalarField(json, field)
            ?.trim()
            ?.removeSuffix("%")
            ?.toDoubleOrNull()
    }

    private fun jsonTimeMillisField(json: String, millisField: String, secondsField: String): Long? {
        return jsonLongField(json, millisField)
            ?: jsonDoubleField(json, millisField)?.roundToLong()
            ?: jsonDoubleField(json, secondsField)?.let { (it * 1_000.0).roundToLong() }
    }

    private fun cleanJsonMessage(value: String?): String {
        return value
            .orEmpty()
            .trim()
            .takeUnless { it.equals("null", ignoreCase = true) }
            .orEmpty()
    }

    private fun jsonBooleanField(json: String, field: String): Boolean? {
        return when (jsonScalarField(json, field)?.lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

    private fun jsonScalarField(json: String, field: String): String? {
        val raw = jsonRawField(json, field) ?: return null
        return if (raw.startsWith('"')) decodeJsonString(raw) else raw
    }

    private fun jsonRawField(json: String, field: String): String? {
        val range = jsonFieldValueRange(json, field) ?: return null
        return json.substring(range).trim()
    }

    private fun jsonFieldValueRange(json: String, field: String): IntRange? {
        var index = 0
        while (index < json.length) {
            index = json.indexOf('"', index)
            if (index < 0) {
                return null
            }
            val keyEnd = findStringEnd(json, index) ?: return null
            val key = decodeJsonString(json.substring(index, keyEnd + 1))
            var cursor = skipWhitespace(json, keyEnd + 1)
            if (cursor < json.length && json[cursor] == ':') {
                cursor = skipWhitespace(json, cursor + 1)
                val valueEnd = jsonValueEnd(json, cursor) ?: return null
                if (key == field) {
                    return cursor..valueEnd
                }
                index = valueEnd + 1
            } else {
                index = keyEnd + 1
            }
        }
        return null
    }

    private fun jsonValueEnd(json: String, start: Int): Int? {
        if (start >= json.length) {
            return null
        }
        return when (json[start]) {
            '"' -> findStringEnd(json, start)
            '{' -> matchingJsonBoundary(json, start, '{', '}')
            '[' -> matchingJsonBoundary(json, start, '[', ']')
            else -> {
                var index = start
                while (index < json.length && json[index] != ',' && json[index] != '}' && json[index] != ']') {
                    index++
                }
                index - 1
            }
        }
    }

    private fun matchingJsonBoundary(json: String, start: Int, open: Char, close: Char): Int? {
        var depth = 0
        var index = start
        var inString = false
        var escaping = false
        while (index < json.length) {
            val char = json[index]
            if (inString) {
                when {
                    escaping -> escaping = false
                    char == '\\' -> escaping = true
                    char == '"' -> inString = false
                }
            } else {
                when (char) {
                    '"' -> inString = true
                    open -> depth++
                    close -> {
                        depth--
                        if (depth == 0) {
                            return index
                        }
                    }
                }
            }
            index++
        }
        return null
    }

    private fun findStringEnd(json: String, startQuote: Int): Int? {
        var index = startQuote + 1
        var escaping = false
        while (index < json.length) {
            val char = json[index]
            when {
                escaping -> escaping = false
                char == '\\' -> escaping = true
                char == '"' -> return index
            }
            index++
        }
        return null
    }

    private fun decodeJsonString(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.length < 2 || trimmed.first() != '"' || trimmed.last() != '"') {
            return trimmed
        }
        val builder = StringBuilder()
        var index = 1
        while (index < trimmed.lastIndex) {
            val char = trimmed[index]
            if (char != '\\' || index + 1 >= trimmed.lastIndex) {
                builder.append(char)
                index++
                continue
            }
            when (val escaped = trimmed[index + 1]) {
                '"', '\\', '/' -> builder.append(escaped)
                'b' -> builder.append('\b')
                'f' -> builder.append('\u000C')
                'n' -> builder.append('\n')
                'r' -> builder.append('\r')
                't' -> builder.append('\t')
                'u' -> {
                    val hexStart = index + 2
                    val hexEnd = hexStart + 4
                    if (hexEnd <= trimmed.lastIndex) {
                        val code = trimmed.substring(hexStart, hexEnd).toIntOrNull(16)
                        if (code != null) {
                            builder.append(code.toChar())
                            index += 4
                        }
                    }
                }
                else -> builder.append(escaped)
            }
            index += 2
        }
        return builder.toString()
    }

    private fun skipWhitespace(value: String, start: Int): Int {
        var index = start
        while (index < value.length && value[index].isWhitespace()) {
            index++
        }
        return index
    }

    private fun skipWhitespaceAndCommas(value: String, start: Int): Int {
        var index = start
        while (index < value.length && (value[index].isWhitespace() || value[index] == ',')) {
            index++
        }
        return index
    }
}
