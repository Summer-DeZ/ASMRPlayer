package io.github.summerdez.asmrplayer.data.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.summerdez.asmrplayer.domain.model.SubtitleGenerationTarget
import java.io.IOException
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink

internal fun buildRemoteWhisperUploadMultipart(
    context: Context,
    uri: Uri,
    fileName: String,
    totalBytes: Long,
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
    )
}

internal fun remoteTranscriptionUploadMultipart(
    fileName: String,
    uploadBody: RequestBody,
): MultipartBody {
    return MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("file", fileName, uploadBody)
        .addFormDataPart("language", "ja")
        .addFormDataPart("task", "transcribe")
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

internal fun audioDisplayName(context: Context, uri: Uri): String {
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

internal fun audioSizeBytes(context: Context, uri: Uri): Long {
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

internal fun safeUploadFileName(target: SubtitleGenerationTarget): String {
    return target.trackTitle
        .replace(Regex("[^A-Za-z0-9._-]+"), "-")
        .trim('-')
        .ifBlank { target.trackId }
        .ifBlank { "audio" }
        .take(80)
}
