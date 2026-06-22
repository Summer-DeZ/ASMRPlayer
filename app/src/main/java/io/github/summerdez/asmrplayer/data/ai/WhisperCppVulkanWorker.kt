package io.github.summerdez.asmrplayer.data.ai

import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal object WhisperCppVulkanWorker {
    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ASRM-WhisperCpp-Vulkan").apply {
            isDaemon = true
        }
    }.asCoroutineDispatcher()
    private var session: NativeSession? = null

    suspend fun transcribe(modelFile: File, pcm16kMono: FloatArray): String = withContext(dispatcher) {
        currentCoroutineContext().ensureActive()
        val activeSession = sessionFor(modelFile)
        val output = runCatching {
            NativeWhisperRuntime.nativeTranscribeSession(activeSession.handle, pcm16kMono)
        }.getOrElse { error ->
            releaseLocked(activeSession)
            throw error
        }
        currentCoroutineContext().ensureActive()
        output
    }

    suspend fun releaseModel(modelPath: String) = withContext(dispatcher) {
        val activeSession = session ?: return@withContext
        if (activeSession.modelPath == modelPath) {
            releaseLocked(activeSession)
        }
    }

    private fun sessionFor(modelFile: File): NativeSession {
        val modelPath = modelFile.absolutePath
        session?.takeIf { it.modelPath == modelPath }?.let { return it }
        session?.let(::releaseLocked)
        val handle = NativeWhisperRuntime.nativeCreateSession(modelPath)
        if (handle == 0L) {
            throw IOException("GPU 模型会话初始化失败")
        }
        return NativeSession(modelPath = modelPath, handle = handle).also {
            session = it
        }
    }

    private fun releaseLocked(activeSession: NativeSession) {
        if (session === activeSession) {
            session = null
        }
        if (activeSession.handle != 0L) {
            NativeWhisperRuntime.nativeReleaseSession(activeSession.handle)
        }
    }

    private data class NativeSession(
        val modelPath: String,
        val handle: Long,
    )
}
