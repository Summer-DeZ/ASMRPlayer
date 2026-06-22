package io.github.summerdez.asmrplayer.data.ai

import android.content.Context
import android.net.Uri
import io.github.summerdez.asmrplayer.domain.model.SubtitleGenerationTarget
import io.github.summerdez.asmrplayer.domain.model.SubtitleLine
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class WhisperCppVulkanTranscriber(
    private val gpuModelState: GpuWhisperModelState,
) : OnDeviceTranscriber {
    override suspend fun transcribeJapanese(
        context: Context,
        target: SubtitleGenerationTarget,
        modelState: WhisperModelState,
        onProgress: (progress: Float, preview: List<SubtitleLine>) -> Unit,
    ): List<SubtitleLine> = withContext(Dispatchers.IO) {
        val modelFile = gpuModelState.modelFile
            ?: throw IOException("请先在设置页下载 ${gpuModelState.spec.label}")
        if (!NativeWhisperRuntime.loaded) {
            throw IOException("GPU 转写后端未加载：${NativeWhisperRuntime.loadErrorMessage().ifBlank { "native 库不可用" }}")
        }
        if (!NativeWhisperRuntime.smokeTest()) {
            throw IOException("GPU 转写后端 smoke test 未通过")
        }
        val audioUri = Uri.parse(target.audioUri)
        val chunks = mutableListOf<FloatArray>()
        var sampleCount = 0
        AndroidAudioPcmDecoder.decodeToMono16k(
            context = context,
            audioUri = audioUri,
            onDecodeProgress = { progress ->
                onProgress((progress * 0.2f).coerceIn(0f, 0.2f), emptyList())
            },
            onSamples = { samples ->
                currentCoroutineContext().ensureActive()
                chunks += samples
                sampleCount += samples.size
            },
        )
        if (sampleCount <= 0) {
            throw IOException("音频解码后没有可转写样本")
        }
        val pcm = FloatArray(sampleCount)
        var offset = 0
        chunks.forEach { chunk ->
            chunk.copyInto(pcm, offset)
            offset += chunk.size
        }
        onProgress(0.25f, emptyList())
        val nativeOutput = WhisperCppVulkanWorker.transcribe(modelFile, pcm)
        val lines = nativeOutput
            .lineSequence()
            .mapIndexedNotNull { index, raw -> raw.toSubtitleLineOrNull(index + 1) }
            .toList()
        if (lines.isEmpty()) {
            throw IOException("GPU 后端未识别到可用语音片段")
        }
        onProgress(1f, lines.takeLast(PREVIEW_LINE_COUNT))
        lines
    }

    private fun String.toSubtitleLineOrNull(index: Int): SubtitleLine? {
        val parts = split('\t', limit = 3)
        if (parts.size != 3) {
            return null
        }
        val startMs = parts[0].toLongOrNull() ?: return null
        val endMs = parts[1].toLongOrNull() ?: return null
        val text = parts[2].trim()
        if (text.isBlank() || endMs <= startMs) {
            return null
        }
        return SubtitleLine(
            id = index.toString(),
            startMs = startMs,
            endMs = endMs,
            sourceText = text,
        )
    }

    private companion object {
        const val PREVIEW_LINE_COUNT = 8
    }
}
