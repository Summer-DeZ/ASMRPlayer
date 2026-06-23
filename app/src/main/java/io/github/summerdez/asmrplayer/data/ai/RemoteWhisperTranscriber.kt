package io.github.summerdez.asmrplayer.data.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleSettings
import io.github.summerdez.asmrplayer.domain.model.SubtitleGenerationTarget
import io.github.summerdez.asmrplayer.domain.model.SubtitleLine
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink

data class RemoteWhisperHealth(
    val status: String,
    val service: String,
    val version: String,
    val device: String,
    val defaultModel: String,
    val modelsReady: Boolean,
) {
    val displaySummary: String
        get() = listOf(
            status.ifBlank { "unknown" },
            device.ifBlank { "auto" },
            defaultModel.ifBlank { "default" },
        ).joinToString(" · ")
}

class RemoteWhisperTranscriber(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(REMOTE_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(REMOTE_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(REMOTE_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(REMOTE_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun checkHealth(settings: AiSubtitleSettings): RemoteWhisperHealth = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${remoteBaseUrl(settings)}/health")
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .applyAuthorization(settings)
            .build()
        executeJsonRequest(request) { body ->
            parseHealthResponse(body)
        }
    }

    suspend fun transcribeJapanese(
        context: Context,
        target: SubtitleGenerationTarget,
        settings: AiSubtitleSettings,
        onProgress: (progress: Float, preview: List<SubtitleLine>) -> Unit,
    ): List<SubtitleLine> = withContext(Dispatchers.IO) {
        val uri = Uri.parse(target.audioUri)
        val fileName = audioDisplayName(context, uri).ifBlank { safeUploadFileName(target) }
        val totalBytes = audioSizeBytes(context, uri)
        onProgress(0.02f, emptyList())
        val uploadBody = ProgressRequestBody(
            context = context.applicationContext,
            uri = uri,
            mediaType = "application/octet-stream".toMediaTypeOrNull(),
            contentLength = totalBytes,
        ) { uploaded, total ->
            if (total > 0L) {
                val uploadProgress = (uploaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                onProgress((0.05f + uploadProgress * 0.3f).coerceIn(0.05f, 0.35f), emptyList())
            }
        }
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, uploadBody)
            .addFormDataPart("language", "ja")
            .addFormDataPart("task", "transcribe")
            .addFormDataPart("vad", "auto")
            .apply {
                val model = settings.activeRemoteWhisperModel
                if (model.isNotBlank()) {
                    addFormDataPart("model", model)
                }
            }
            .build()
        val request = Request.Builder()
            .url("${remoteBaseUrl(settings)}/transcribe")
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .applyAuthorization(settings)
            .post(multipart)
            .build()
        executeJsonRequest(request) { body ->
            parseTranscribeResponse(body).also { lines ->
                if (lines.isEmpty()) {
                    throw IOException("远程 Whisper 未识别到可用语音片段")
                }
                onProgress(1f, lines.takeLast(WHISPER_PREVIEW_LINE_COUNT))
            }
        }
    }

    private suspend fun <T> executeJsonRequest(request: Request, parser: (String) -> T): T {
        val call = client.newCall(request)
        val response = try {
            executeCancellable(call)
        } catch (error: IOException) {
            throw IOException(remoteNetworkError(error), error)
        }
        response.use { activeResponse ->
            val body = activeResponse.body?.string().orEmpty()
            if (!activeResponse.isSuccessful) {
                throw IOException("远程 Whisper 失败：HTTP ${activeResponse.code}${remoteErrorHint(body)}")
            }
            if (body.isBlank()) {
                throw IOException("远程 Whisper 响应为空")
            }
            return parser(body)
        }
    }

    private fun Request.Builder.applyAuthorization(settings: AiSubtitleSettings): Request.Builder {
        val token = settings.remoteWhisperToken.trim()
        if (token.isNotBlank()) {
            header("Authorization", "Bearer $token")
        }
        return this
    }

    private fun remoteBaseUrl(settings: AiSubtitleSettings): String {
        val baseUrl = settings.normalizedRemoteWhisperBaseUrl
        if (baseUrl.isBlank()) {
            throw IOException("请先填写远程 Whisper 服务地址")
        }
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            throw IOException("远程 Whisper 服务地址需要以 http:// 或 https:// 开头")
        }
        return baseUrl
    }

    private suspend fun executeCancellable(call: Call): Response {
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (!continuation.isCancelled) {
                            continuation.resumeWithException(e)
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (!continuation.isCancelled) {
                            continuation.resume(response)
                        } else {
                            response.close()
                        }
                    }
                },
            )
        }
    }

    companion object {
        private const val REMOTE_CONNECT_TIMEOUT_SECONDS = 30L
        private const val REMOTE_WRITE_TIMEOUT_SECONDS = 600L
        private const val REMOTE_READ_TIMEOUT_SECONDS = 3600L
        private const val REMOTE_CALL_TIMEOUT_SECONDS = 5400L
        private const val USER_AGENT = "ASMRPlayer-Android"

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

        fun parseTranscribeResponse(body: String): List<SubtitleLine> {
            val segmentsContent = jsonArrayContent(body, "segments")
                ?: throw IOException("远程 Whisper 响应缺少 segments")
            val segments = splitJsonObjects(segmentsContent)
            val lines = mutableListOf<SubtitleLine>()
            var lastStart = Long.MIN_VALUE
            segments.forEachIndexed { index, segment ->
                val startMs = jsonLongField(segment, "start_ms")
                val endMs = jsonLongField(segment, "end_ms")
                val text = jsonStringField(segment, "text").orEmpty().trim()
                if (startMs == null || endMs == null || endMs <= startMs) {
                    throw IOException("远程 Whisper 第 ${index + 1} 段时间轴无效")
                }
                if (startMs < lastStart) {
                    throw IOException("远程 Whisper segments 未按时间升序返回")
                }
                if (text.isBlank()) {
                    throw IOException("远程 Whisper 第 ${index + 1} 段文本为空")
                }
                lastStart = startMs
                val rawId = jsonScalarField(segment, "id").orEmpty().trim()
                lines += SubtitleLine(
                    id = rawId.ifBlank { (index + 1).toString() },
                    startMs = startMs,
                    endMs = endMs,
                    sourceText = text,
                )
            }
            return lines
        }

        fun parseErrorMessage(body: String): String {
            return jsonStringField(body, "error").orEmpty()
        }

        private fun jsonArrayContent(json: String, field: String): String? {
            val valueStart = jsonFieldValueRange(json, field)?.first ?: return null
            val arrayStart = skipWhitespace(json, valueStart).takeIf { it < json.length && json[it] == '[' } ?: return null
            val arrayEnd = matchingJsonBoundary(json, arrayStart, '[', ']') ?: return null
            return json.substring(arrayStart + 1, arrayEnd)
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
                    throw IOException("远程 Whisper segments 包含非 object 项")
                }
                val end = matchingJsonBoundary(arrayContent, index, '{', '}')
                    ?: throw IOException("远程 Whisper segments JSON 不完整")
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
}

private class ProgressRequestBody(
    private val context: Context,
    private val uri: Uri,
    private val mediaType: MediaType?,
    private val contentLength: Long,
    private val onProgress: (uploaded: Long, total: Long) -> Unit,
) : RequestBody() {
    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long = contentLength

    override fun writeTo(sink: BufferedSink) {
        val resolver = context.contentResolver
        val input = resolver.openInputStream(uri) ?: throw IOException("无法读取音频文件")
        input.use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var uploaded = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) {
                    break
                }
                sink.write(buffer, 0, read)
                uploaded += read.toLong()
                onProgress(uploaded, contentLength)
            }
        }
    }
}

private fun remoteNetworkError(error: IOException): String {
    return when (error) {
        is SocketTimeoutException ->
            "远程 Whisper 请求超时，请检查服务器是否仍在转写、网络是否稳定"
        is UnknownHostException ->
            "远程 Whisper 地址无法解析，请检查服务器地址和网络"
        else ->
            "远程 Whisper 请求失败：${error.message ?: "网络异常"}"
    }
}

private fun remoteErrorHint(body: String): String {
    val message = runCatching { RemoteWhisperTranscriber.parseErrorMessage(body) }.getOrDefault("")
    return if (message.isBlank()) "" else "：$message"
}

private fun audioDisplayName(context: Context, uri: Uri): String {
    if (uri.scheme == "file") {
        return uri.lastPathSegment.orEmpty().substringAfterLast('/')
    }
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0).orEmpty()
                } else {
                    ""
                }
            }
    }.getOrNull().orEmpty()
}

private fun audioSizeBytes(context: Context, uri: Uri): Long {
    if (uri.scheme == "file") {
        return runCatching { java.io.File(uri.path.orEmpty()).length() }.getOrDefault(-1L)
    }
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getLong(0)
                } else {
                    -1L
                }
            } ?: -1L
    }.getOrDefault(-1L)
}

private fun safeUploadFileName(target: SubtitleGenerationTarget): String {
    return target.trackTitle
        .replace(Regex("[^A-Za-z0-9._-]+"), "-")
        .trim('-')
        .ifBlank { target.trackId }
        .ifBlank { "audio" }
        .take(80)
}
