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
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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

data class RemoteTranscriptionJob(
    val jobId: String,
)

data class RemoteTranscriptionStatus(
    val status: String,
    val stage: String,
    val processedMs: Long?,
    val durationMs: Long?,
    val progress: Float?,
    val message: String,
    val updatedAt: String,
    val previewLines: List<SubtitleLine> = emptyList(),
) {
    val normalizedStatus: String
        get() = status.trim().lowercase()

    val normalizedStage: String
        get() = stage.trim().lowercase()

    val isCompleted: Boolean
        get() = normalizedStatus in COMPLETED_REMOTE_STATUSES ||
            normalizedStage in COMPLETED_REMOTE_STATUSES

    val isFailed: Boolean
        get() = normalizedStatus in FAILED_REMOTE_STATUSES ||
            normalizedStage in FAILED_REMOTE_STATUSES
}

data class RemoteTranscriptionProgress(
    val processedMs: Long? = null,
    val durationMs: Long? = null,
    val detailText: String = "",
)

private val COMPLETED_REMOTE_STATUSES = setOf("succeeded", "done")
private val FAILED_REMOTE_STATUSES = setOf("failed", "error", "canceled", "cancelled")

class RemoteWhisperTranscriber(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(REMOTE_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(REMOTE_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(REMOTE_SHORT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build(),
) {
    private val pollingClient = client.newBuilder()
        .readTimeout(REMOTE_SHORT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(REMOTE_SHORT_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

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
        onProgress: (
            progress: Float,
            preview: List<SubtitleLine>,
            remoteProgress: RemoteTranscriptionProgress?,
        ) -> Unit,
    ): List<SubtitleLine> = withContext(Dispatchers.IO) {
        val baseUrl = remoteBaseUrl(settings)
        val uri = Uri.parse(target.audioUri)
        val fileName = audioDisplayName(context, uri).ifBlank { safeUploadFileName(target) }
        val totalBytes = audioSizeBytes(context, uri)
        var jobId: String? = null
        try {
            onProgress(0.02f, emptyList(), RemoteTranscriptionProgress(detailText = "准备上传"))
            val job = createAsyncJob(
                context = context,
                uri = uri,
                fileName = fileName,
                totalBytes = totalBytes,
                baseUrl = baseUrl,
                settings = settings,
                onProgress = onProgress,
            )
            jobId = job.jobId
            onProgress(0.35f, emptyList(), RemoteTranscriptionProgress(detailText = "等待远程转写"))
            val lines = pollAsyncResult(
                baseUrl = baseUrl,
                jobId = job.jobId,
                settings = settings,
                onProgress = onProgress,
            )
            onProgress(1f, lines.takeLast(WHISPER_PREVIEW_LINE_COUNT), RemoteTranscriptionProgress(detailText = "转写完成"))
            lines
        } catch (error: CancellationException) {
            jobId?.let { activeJobId ->
                withContext(NonCancellable) {
                    cancelRemoteJob(baseUrl, activeJobId, settings)
                }
            }
            throw error
        }
    }

    private suspend fun createAsyncJob(
        context: Context,
        uri: Uri,
        fileName: String,
        totalBytes: Long,
        baseUrl: String,
        settings: AiSubtitleSettings,
        onProgress: (
            progress: Float,
            preview: List<SubtitleLine>,
            remoteProgress: RemoteTranscriptionProgress?,
        ) -> Unit,
    ): RemoteTranscriptionJob {
        val multipart = buildUploadMultipart(
            context = context,
            uri = uri,
            fileName = fileName,
            totalBytes = totalBytes,
            settings = settings,
        ) { uploaded, total ->
            if (total > 0L) {
                val uploadProgress = (uploaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                onProgress(
                    (0.03f + uploadProgress * 0.32f).coerceIn(0.03f, 0.35f),
                    emptyList(),
                    RemoteTranscriptionProgress(detailText = "上传音频"),
                )
            }
        }
        val request = Request.Builder()
            .url("$baseUrl/transcriptions")
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .applyAuthorization(settings)
            .post(multipart)
            .build()
        val response = try {
            executeCancellable(client.newCall(request))
        } catch (error: IOException) {
            throw IOException(remoteNetworkError(error), error)
        }
        response.use { activeResponse ->
            val body = activeResponse.body?.string().orEmpty()
            if (!activeResponse.isSuccessful) {
                throw IOException("远程转写服务创建任务失败：HTTP ${activeResponse.code}${remoteErrorHint(body)}")
            }
            if (body.isBlank()) {
                throw IOException("远程转写服务创建任务响应为空")
            }
            return parseCreateJobResponse(body)
        }
    }

    private suspend fun pollAsyncResult(
        baseUrl: String,
        jobId: String,
        settings: AiSubtitleSettings,
        onProgress: (
            progress: Float,
            preview: List<SubtitleLine>,
            remoteProgress: RemoteTranscriptionProgress?,
        ) -> Unit,
    ): List<SubtitleLine> {
        var lastSnapshot: RemoteProgressSnapshot? = null
        var lastMovementAt = System.nanoTime()
        while (true) {
            currentCoroutineContext().ensureActive()
            val status = try {
                fetchStatus(baseUrl, jobId, settings)
            } catch (error: IOException) {
                if (elapsedMillisSince(lastMovementAt) >= REMOTE_STALL_TIMEOUT_MS) {
                    throw IOException(
                        "远程转写进度连续 10 分钟没有更新，请检查服务器任务状态",
                        error,
                    )
                }
                delay(REMOTE_POLL_INTERVAL_MS)
                continue
            }
            val snapshot = RemoteProgressSnapshot.from(status)
            if (lastSnapshot == null || snapshot != lastSnapshot) {
                lastSnapshot = snapshot
                lastMovementAt = System.nanoTime()
            } else if (elapsedMillisSince(lastMovementAt) >= REMOTE_STALL_TIMEOUT_MS) {
                throw IOException("远程转写进度连续 10 分钟没有更新，请检查服务器任务状态")
            }
            val progress = remoteStatusProgress(status)
            onProgress(
                progress,
                status.previewLines.takeLast(WHISPER_PREVIEW_LINE_COUNT),
                RemoteTranscriptionProgress(
                    processedMs = status.processedMs,
                    durationMs = status.durationMs,
                    detailText = remoteStatusDetail(status),
                ),
            )
            if (status.isFailed) {
                throw IOException(status.message.ifBlank { "远程转写任务失败：${status.status}" })
            }
            if (status.isCompleted) {
                val lines = fetchResult(baseUrl, jobId, settings)
                if (lines.isEmpty()) {
                    throw IOException("远程转写服务未识别到可用语音片段")
                }
                return lines
            }
            delay(REMOTE_POLL_INTERVAL_MS)
        }
    }

    private suspend fun fetchStatus(
        baseUrl: String,
        jobId: String,
        settings: AiSubtitleSettings,
    ): RemoteTranscriptionStatus {
        val request = Request.Builder()
            .url("$baseUrl/transcriptions/${Uri.encode(jobId)}")
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .applyAuthorization(settings)
            .build()
        return executeJsonRequest(request, pollingClient) { body ->
            parseTranscriptionStatusResponse(body)
        }
    }

    private suspend fun fetchResult(
        baseUrl: String,
        jobId: String,
        settings: AiSubtitleSettings,
    ): List<SubtitleLine> {
        val request = Request.Builder()
            .url("$baseUrl/transcriptions/${Uri.encode(jobId)}/result")
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .applyAuthorization(settings)
            .build()
        return executeJsonRequest(request, pollingClient) { body ->
            parseTranscriptionResultResponse(body)
        }
    }

    private fun buildUploadMultipart(
        context: Context,
        uri: Uri,
        fileName: String,
        totalBytes: Long,
        settings: AiSubtitleSettings,
        onUploadProgress: (uploaded: Long, total: Long) -> Unit,
    ): MultipartBody {
        val uploadBody = ProgressRequestBody(
            context = context.applicationContext,
            uri = uri,
            mediaType = "application/octet-stream".toMediaTypeOrNull(),
            contentLength = totalBytes,
            onProgress = onUploadProgress,
        )
        return remoteTranscriptionUploadMultipart(
            fileName = fileName,
            uploadBody = uploadBody,
            model = settings.activeRemoteWhisperModel,
        )
    }

    private suspend fun cancelRemoteJob(baseUrl: String, jobId: String, settings: AiSubtitleSettings) {
        val request = Request.Builder()
            .url("$baseUrl/transcriptions/${Uri.encode(jobId)}")
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .applyAuthorization(settings)
            .delete()
            .build()
        runCatching {
            executeCancellable(pollingClient.newCall(request)).close()
        }
    }

    private suspend fun <T> executeJsonRequest(
        request: Request,
        requestClient: OkHttpClient = client,
        parser: (String) -> T,
    ): T {
        val call = requestClient.newCall(request)
        val response = try {
            executeCancellable(call)
        } catch (error: IOException) {
            throw IOException(remoteNetworkError(error), error)
        }
        response.use { activeResponse ->
            val body = activeResponse.body?.string().orEmpty()
            if (!activeResponse.isSuccessful) {
                throw IOException("远程转写服务失败：HTTP ${activeResponse.code}${remoteErrorHint(body)}")
            }
            if (body.isBlank()) {
                throw IOException("远程转写服务响应为空")
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
            throw IOException("请先填写远程转写服务地址")
        }
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            throw IOException("远程转写服务地址需要以 http:// 或 https:// 开头")
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
        private const val REMOTE_SHORT_READ_TIMEOUT_SECONDS = 45L
        private const val REMOTE_SHORT_CALL_TIMEOUT_SECONDS = 60L
        private const val REMOTE_POLL_INTERVAL_MS = 2_000L
        private const val REMOTE_STALL_TIMEOUT_MS = 10 * 60 * 1_000L
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

        private fun parseSegmentsResponse(body: String, allowWrappedResult: Boolean): List<SubtitleLine> {
            val segmentsContent = jsonArrayContent(body, "segments")
                ?: if (allowWrappedResult) {
                    jsonObjectContent(body, "result")?.let { jsonArrayContent(it, "segments") }
                } else {
                    null
                }
                ?: throw IOException("远程转写服务响应缺少 segments")
            return parseSegmentsContent(segmentsContent)
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
}

private data class RemoteProgressSnapshot(
    val status: String,
    val stage: String,
    val progress: Float?,
    val processedMs: Long?,
    val updatedAt: String,
) {
    companion object {
        fun from(status: RemoteTranscriptionStatus): RemoteProgressSnapshot {
            return RemoteProgressSnapshot(
                status = status.normalizedStatus,
                stage = status.normalizedStage,
                progress = status.progress,
                processedMs = status.processedMs,
                updatedAt = status.updatedAt,
            )
        }
    }
}

private fun remoteStatusProgress(status: RemoteTranscriptionStatus): Float {
    val processed = status.processedMs
    val duration = status.durationMs
    val remoteProgress = if (processed != null && duration != null && duration > 0L) {
        (processed.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        status.progress
    }
    return remoteProgress
        ?.let { (0.35f + it * 0.6f).coerceIn(0.35f, 0.95f) }
        ?: 0.35f
}

private fun remoteStatusDetail(status: RemoteTranscriptionStatus): String {
    if (status.message.isNotBlank()) {
        return status.message
    }
    if (status.isCompleted) {
        return "转写完成"
    }
    if (status.isFailed) {
        return "转写失败"
    }
    return when (status.normalizedStage) {
        "queued" -> "等待远程转写"
        "asr" -> "正在语音识别"
        "aligning" -> "正在对齐字幕"
        "finalizing" -> "正在写出结果"
        else -> when (status.normalizedStatus) {
            "queued" -> "等待远程转写"
            else -> "正在语音识别"
        }
    }
}

private fun elapsedMillisSince(startNanoTime: Long): Long {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanoTime)
}

internal fun remoteTranscriptionUploadMultipart(
    fileName: String,
    uploadBody: RequestBody,
    model: String,
): MultipartBody {
    return MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("file", fileName, uploadBody)
        .addFormDataPart("language", "ja")
        .addFormDataPart("task", "transcribe")
        .apply {
            val normalizedModel = model.trim()
            if (normalizedModel.isNotBlank()) {
                addFormDataPart("model", normalizedModel)
            }
        }
        .build()
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
            "远程转写服务请求超时，请检查服务器是否仍在转写、网络是否稳定"
        is UnknownHostException ->
            "远程转写服务地址无法解析，请检查服务器地址和网络"
        else ->
            "远程转写服务请求失败：${error.message ?: "网络异常"}"
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
