package io.github.summerdez.asmrplayer.data.ai

import android.content.Context
import io.github.summerdez.asmrplayer.domain.model.GpuWhisperModelSpec
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class GpuWhisperModelState(
    val spec: GpuWhisperModelSpec,
    val downloaded: Boolean,
    val localPath: String = "",
    val modelFile: File? = null,
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

class GpuWhisperModelRepository(
    context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder().build(),
) {
    private val modelsDir = File(context.applicationContext.filesDir, "ai-subtitles/gpu-models")

    fun modelDirectory(spec: GpuWhisperModelSpec): File = File(modelsDir, spec.directoryName)

    fun modelFile(spec: GpuWhisperModelSpec): File {
        return File(modelDirectory(spec), spec.fileName)
    }

    fun state(spec: GpuWhisperModelSpec): GpuWhisperModelState {
        val target = modelFile(spec)
        val partial = File(target.parentFile, "${spec.fileName}.part")
        val downloaded = target.isFile && target.length() > 0L
        val bytesDownloaded = when {
            downloaded -> target.length()
            partial.exists() -> partial.length()
            else -> 0L
        }
        return GpuWhisperModelState(
            spec = spec,
            downloaded = downloaded,
            localPath = if (downloaded) target.absolutePath else "",
            modelFile = if (downloaded) target else null,
            bytesDownloaded = bytesDownloaded,
            totalBytes = maxOf(bytesDownloaded, spec.sizeBytes),
        )
    }

    suspend fun download(
        spec: GpuWhisperModelSpec,
        onProgress: (GpuWhisperModelState) -> Unit,
    ): GpuWhisperModelState = withContext(Dispatchers.IO) {
        val dir = modelDirectory(spec).apply { mkdirs() }
        val target = File(dir, spec.fileName)
        if (target.isFile && target.length() > 0L) {
            return@withContext state(spec)
        }
        val partial = File(dir, "${spec.fileName}.part")
        if (partial.exists()) {
            partial.delete()
        }
        val request = Request.Builder()
            .url(spec.downloadUrl)
            .header("User-Agent", "ASMRPlayer-Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("${spec.label} 下载失败：HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("${spec.label} 下载失败：响应为空")
            val contentLength = body.contentLength().takeIf { it > 0L } ?: spec.sizeBytes
            body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) {
                            break
                        }
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(
                            GpuWhisperModelState(
                                spec = spec,
                                downloaded = false,
                                bytesDownloaded = downloaded,
                                totalBytes = contentLength,
                                downloading = true,
                            ),
                        )
                    }
                }
            }
        }
        if (partial.length() <= 0L) {
            partial.delete()
            throw IOException("${spec.label} 下载失败：文件为空")
        }
        if (target.exists()) {
            target.delete()
        }
        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
        state(spec)
    }

    fun delete(spec: GpuWhisperModelSpec): Boolean {
        val dir = modelDirectory(spec)
        return dir.exists() && dir.deleteRecursively()
    }
}
