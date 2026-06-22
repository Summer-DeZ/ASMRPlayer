package io.github.summerdez.asmrplayer.data.ai

import android.content.Context
import android.os.Build

internal object NativeWhisperRuntime {
    private const val LIBRARY_NAME = "asrm_whisper_jni"

    private val loadResult: Result<Unit> by lazy {
        runCatching { System.loadLibrary(LIBRARY_NAME) }
    }

    val loaded: Boolean
        get() = loadResult.isSuccess

    fun loadErrorMessage(): String {
        return loadResult.exceptionOrNull()?.message.orEmpty()
    }

    fun smokeTest(): Boolean {
        return loaded && runCatching { nativeSmokeTest() }.getOrDefault(false)
    }

    fun isVulkanCapable(context: Context): Boolean {
        if (Build.SUPPORTED_ABIS.none { it == "arm64-v8a" }) {
            return false
        }
        val hasVulkan = context.packageManager.hasSystemFeature("android.hardware.vulkan.version")
        return hasVulkan && smokeTest() && nativeIsVulkanCapable()
    }

    @JvmStatic
    external fun nativeSmokeTest(): Boolean

    @JvmStatic
    external fun nativeIsVulkanCapable(): Boolean

    @JvmStatic
    external fun nativeCreateSession(modelPath: String): Long

    @JvmStatic
    external fun nativeReleaseSession(sessionHandle: Long)

    @JvmStatic
    external fun nativeTranscribeSession(sessionHandle: Long, pcm16kMono: FloatArray): String

    @JvmStatic
    external fun nativeTranscribeJapanese(modelPath: String, pcm16kMono: FloatArray): String
}
