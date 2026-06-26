package io.github.summerdez.asmrplayer.data.ai

import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleSettings
import io.github.summerdez.asmrplayer.domain.model.SubtitleGenerationTarget
import io.github.summerdez.asmrplayer.domain.model.SubtitleLine
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "RemoteWhisperTranscriber"

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
            .header("User-Agent", USER_AGENT)            .build()
        executeRemoteJsonRequest(request, client) { body ->
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
        val multipart = buildRemoteWhisperUploadMultipart(
            context = context,
            uri = uri,
            fileName = fileName,
            totalBytes = totalBytes,
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
            .header("User-Agent", USER_AGENT)            .post(multipart)
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
        var lastProgress = 0.35f
        var lastPreview = emptyList<SubtitleLine>()
        var lastRemoteProgress: RemoteTranscriptionProgress? = null
        var lastMovementAt = System.nanoTime()
        while (true) {
            currentCoroutineContext().ensureActive()
            val status = try {
                fetchStatus(baseUrl, jobId, settings)
            } catch (error: IOException) {
                Log.w(TAG, "Remote transcription status poll failed for job=$jobId; retrying until stall timeout", error)
                if (elapsedMillisSince(lastMovementAt) >= REMOTE_STALL_TIMEOUT_MS) {
                    throw IOException(
                        "远程转写进度连续 10 分钟没有更新，请检查服务器任务状态",
                        error,
                    )
                }
                onProgress(
                    lastProgress,
                    lastPreview,
                    lastRemoteProgress?.copy(detailText = "远程状态请求失败，稍后重试")
                        ?: RemoteTranscriptionProgress(detailText = "远程状态请求失败，稍后重试"),
                )
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
            val preview = status.previewLines.takeLast(WHISPER_PREVIEW_LINE_COUNT)
            val remoteProgress = RemoteTranscriptionProgress(
                processedMs = status.processedMs,
                durationMs = status.durationMs,
                detailText = remoteStatusDetail(status),
            )
            onProgress(
                progress,
                preview,
                remoteProgress,
            )
            lastProgress = progress
            lastPreview = preview
            lastRemoteProgress = remoteProgress
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
            .header("User-Agent", USER_AGENT)            .build()
        return executeRemoteJsonRequest(request, pollingClient) { body ->
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
            .header("User-Agent", USER_AGENT)            .build()
        return executeRemoteJsonRequest(request, pollingClient) { body ->
            parseTranscriptionResultResponse(body)
        }
    }

    private suspend fun cancelRemoteJob(baseUrl: String, jobId: String, settings: AiSubtitleSettings) {
        val request = Request.Builder()
            .url("$baseUrl/transcriptions/${Uri.encode(jobId)}")
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)            .delete()
            .build()
        runCatching {
            executeCancellable(pollingClient.newCall(request)).use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Remote transcription cancel requested for job=$jobId")
                } else {
                    Log.w(TAG, "Remote transcription cancel returned HTTP ${response.code} for job=$jobId")
                }
            }
        }.onFailure { error ->
            Log.w(TAG, "Remote transcription cancel failed for job=$jobId; preserving local cancellation", error)
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
            return RemoteWhisperResponseParser.parseHealthResponse(body)
        }

        fun parseCreateJobResponse(body: String): RemoteTranscriptionJob {
            return RemoteWhisperResponseParser.parseCreateJobResponse(body)
        }

        fun parseTranscriptionStatusResponse(body: String): RemoteTranscriptionStatus {
            return RemoteWhisperResponseParser.parseTranscriptionStatusResponse(body)
        }

        fun parseTranscriptionResultResponse(body: String): List<SubtitleLine> {
            return RemoteWhisperResponseParser.parseTranscriptionResultResponse(body)
        }

        fun parseErrorMessage(body: String): String {
            return RemoteWhisperResponseParser.parseErrorMessage(body)
        }
    }
}
