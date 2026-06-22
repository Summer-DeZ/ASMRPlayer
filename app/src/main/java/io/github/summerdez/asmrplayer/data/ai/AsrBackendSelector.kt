package io.github.summerdez.asmrplayer.data.ai

import android.content.Context
import io.github.summerdez.asmrplayer.domain.model.AiAsrBackendPreference
import io.github.summerdez.asmrplayer.domain.model.SubtitleGenerationTarget
import io.github.summerdez.asmrplayer.domain.model.SubtitleLine
import java.io.IOException

internal enum class AsrRuntimeBackend {
    SHERPA_ONNX_CPU,
    WHISPER_CPP_VULKAN,
}

internal data class AsrBackendDecision(
    val backend: AsrRuntimeBackend,
    val fallbackReason: String = "",
)

internal object AsrBackendPlanner {
    fun choose(
        preference: AiAsrBackendPreference,
        gpuAvailable: Boolean,
        gpuBackendRegistered: Boolean,
    ): AsrBackendDecision {
        return when (preference) {
            AiAsrBackendPreference.CPU -> AsrBackendDecision(AsrRuntimeBackend.SHERPA_ONNX_CPU)
            AiAsrBackendPreference.AUTO -> {
                if (gpuAvailable && gpuBackendRegistered) {
                    AsrBackendDecision(AsrRuntimeBackend.WHISPER_CPP_VULKAN)
                } else {
                    AsrBackendDecision(
                        backend = AsrRuntimeBackend.SHERPA_ONNX_CPU,
                        fallbackReason = "GPU 转写后端暂不可用，已使用 CPU 后端",
                    )
                }
            }
            AiAsrBackendPreference.GPU_EXPERIMENTAL -> {
                if (gpuAvailable && gpuBackendRegistered) {
                    AsrBackendDecision(AsrRuntimeBackend.WHISPER_CPP_VULKAN)
                } else {
                    AsrBackendDecision(
                        backend = AsrRuntimeBackend.SHERPA_ONNX_CPU,
                        fallbackReason = "GPU 转写后端初始化条件不足，已回退 CPU 后端",
                    )
                }
            }
        }
    }
}

internal class BackendSelectingTranscriber(
    private val preferenceProvider: () -> AiAsrBackendPreference = { AiAsrBackendPreference.AUTO },
    private val gpuCapabilityDetector: (Context) -> Boolean = NativeWhisperRuntime::isVulkanCapable,
    private val cpuTranscriber: OnDeviceTranscriber = WhisperRuntimeTranscriber(),
    private val gpuTranscriber: OnDeviceTranscriber? = null,
    private val onBackendNotice: (String) -> Unit = {},
) : OnDeviceTranscriber {
    override suspend fun transcribeJapanese(
        context: Context,
        target: SubtitleGenerationTarget,
        modelState: WhisperModelState,
        onProgress: (progress: Float, preview: List<SubtitleLine>) -> Unit,
    ): List<SubtitleLine> {
        val decision = AsrBackendPlanner.choose(
            preference = preferenceProvider(),
            gpuAvailable = gpuCapabilityDetector(context),
            gpuBackendRegistered = gpuTranscriber != null,
        )
        if (decision.fallbackReason.isNotBlank()) {
            onBackendNotice(decision.fallbackReason)
        }
        return when (decision.backend) {
            AsrRuntimeBackend.SHERPA_ONNX_CPU -> cpuTranscriber.transcribeJapanese(
                context = context,
                target = target,
                modelState = modelState,
                onProgress = onProgress,
            )
            AsrRuntimeBackend.WHISPER_CPP_VULKAN -> runCatching {
                requireNotNull(gpuTranscriber).transcribeJapanese(
                    context = context,
                    target = target,
                    modelState = modelState,
                    onProgress = onProgress,
                )
            }.getOrElse { error ->
                if (error is kotlinx.coroutines.CancellationException) {
                    throw error
                }
                onBackendNotice("GPU 转写后端运行失败：${error.message ?: "未知错误"}，已回退 CPU 后端")
                cpuTranscriber.transcribeJapanese(
                    context = context,
                    target = target,
                    modelState = modelState,
                    onProgress = onProgress,
                )
            }
        }
    }
}

internal class WhisperCppVulkanTranscriberUnavailable : OnDeviceTranscriber {
    override suspend fun transcribeJapanese(
        context: Context,
        target: SubtitleGenerationTarget,
        modelState: WhisperModelState,
        onProgress: (progress: Float, preview: List<SubtitleLine>) -> Unit,
    ): List<SubtitleLine> {
        throw IOException("GPU 转写后端尚未集成")
    }
}
