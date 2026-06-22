package io.github.summerdez.asmrplayer.data.ai

import android.content.Context
import io.github.summerdez.asmrplayer.domain.model.WhisperModelFileSpec
import io.github.summerdez.asmrplayer.domain.model.WhisperModelSpec
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class WhisperModelState(
    val spec: WhisperModelSpec,
    val downloaded: Boolean,
    val localPath: String = "",
    val paths: WhisperModelPaths? = null,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = spec.sizeBytes,
    val downloading: Boolean = false,
    val error: String = "",
) {
    val progress: Float
        get() = if (totalBytes > 0L) {
            (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
    }
}

data class WhisperModelPaths(
    val directory: File,
    val encoder: File,
    val decoder: File,
    val tokens: File,
    val vad: File,
)

class WhisperModelRepository(
    context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder().build(),
) {
    private val modelsDir = File(context.applicationContext.filesDir, "ai-subtitles/models")

    fun modelDirectory(spec: WhisperModelSpec): File = File(modelsDir, spec.directoryName)

    fun modelFile(spec: WhisperModelSpec, file: WhisperModelFileSpec): File {
        return File(modelDirectory(spec), file.fileName)
    }

    fun paths(spec: WhisperModelSpec): WhisperModelPaths {
        val dir = modelDirectory(spec)
        return WhisperModelPaths(
            directory = dir,
            encoder = File(dir, spec.encoderFileName),
            decoder = File(dir, spec.decoderFileName),
            tokens = File(dir, spec.tokensFileName),
            vad = File(dir, WhisperModelSpec.SILERO_VAD.fileName),
        )
    }

    fun state(spec: WhisperModelSpec): WhisperModelState {
        val dir = modelDirectory(spec)
        val downloaded = spec.files.all { fileSpec ->
            val file = modelFile(spec, fileSpec)
            file.exists() && file.length() == fileSpec.sizeBytes
        }
        val bytesDownloaded = spec.files.sumOf { fileSpec ->
            val file = modelFile(spec, fileSpec)
            val partial = File(dir, "${fileSpec.fileName}.part")
            when {
                file.exists() -> file.length().coerceAtMost(fileSpec.sizeBytes)
                partial.exists() -> partial.length().coerceAtMost(fileSpec.sizeBytes)
                else -> 0L
            }
        }
        return WhisperModelState(
            spec = spec,
            downloaded = downloaded,
            localPath = if (downloaded) dir.absolutePath else "",
            paths = if (downloaded) paths(spec) else null,
            bytesDownloaded = bytesDownloaded,
            totalBytes = spec.sizeBytes,
        )
    }

    suspend fun download(
        spec: WhisperModelSpec,
        onProgress: (WhisperModelState) -> Unit,
    ): WhisperModelState = withContext(Dispatchers.IO) {
        val dir = modelDirectory(spec).apply { mkdirs() }
        for (fileSpec in spec.files) {
            val target = File(dir, fileSpec.fileName)
            if (target.exists() && target.length() == fileSpec.sizeBytes) {
                onProgress(state(spec).copy(downloading = true))
                continue
            }
            downloadFile(spec, fileSpec, target) {
                onProgress(state(spec).copy(downloading = true))
            }
        }
        state(spec)
    }

    fun delete(spec: WhisperModelSpec): Boolean {
        val dir = modelDirectory(spec)
        return dir.exists() && dir.deleteRecursively()
    }

    private suspend fun downloadFile(
        spec: WhisperModelSpec,
        fileSpec: WhisperModelFileSpec,
        target: File,
        onProgress: () -> Unit,
    ) {
        val partial = File(target.parentFile, "${fileSpec.fileName}.part")
        if (partial.exists()) {
            partial.delete()
        }
        if (target.exists()) {
            target.delete()
        }
        val request = Request.Builder()
            .url(fileSpec.downloadUrl)
            .header("User-Agent", "ASMRPlayer-Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("${spec.label} 下载失败：${fileSpec.fileName} HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("${spec.label} 下载失败：响应为空")
            body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) {
                            break
                        }
                        output.write(buffer, 0, read)
                        onProgress()
                    }
                }
            }
        }
        if (partial.length() <= 0L) {
            partial.delete()
            throw IOException("${spec.label} 下载失败：${fileSpec.fileName} 文件为空")
        }
        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
    }
}
