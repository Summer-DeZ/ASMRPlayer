package io.github.summerdez.asmrplayer.data.ai

import android.app.ActivityManager
import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import io.github.summerdez.asmrplayer.domain.model.WhisperModelSpec
import kotlin.math.max

internal const val WHISPER_SAMPLE_RATE = 16_000
internal const val WHISPER_PREVIEW_LINE_COUNT = 8
internal const val STREAMING_SEGMENT_COUNT_HINT = Int.MAX_VALUE

private const val COMPACT_MODEL_FILE_THRESHOLD_BYTES = 220L * 1024L * 1024L
private const val COMPACT_MODEL_MEMORY_MULTIPLIER = 3L
private const val LARGE_MODEL_MEMORY_MULTIPLIER = 2L
private const val COMPACT_MODEL_COPY_MEMORY_OVERHEAD_BYTES = 96L * 1024L * 1024L
private const val LARGE_MODEL_COPY_MEMORY_OVERHEAD_BYTES = 192L * 1024L * 1024L
private const val MAX_RECOGNIZER_THREADS_PER_WORKER = 4
private const val VAD_PROGRESS_WEIGHT = 0.2f
private const val RECOGNITION_PROGRESS_WEIGHT = 0.8f

internal data class WhisperRecognitionConcurrency(
    val workerCount: Int,
    val threadsPerWorker: Int,
)

internal fun planWhisperRecognitionConcurrency(
    modelSpec: WhisperModelSpec,
    availMemBytes: Long,
    cores: Int,
    segmentCount: Int,
): WhisperRecognitionConcurrency {
    val safeCores = cores.coerceAtLeast(1)
    val safeSegmentCount = segmentCount.coerceAtLeast(0)
    val modelMaxWorkers = when (modelSpec.id) {
        WhisperModelSpec.SMALL.id -> 2
        else -> 4
    }
    val coreMaxWorkers = when {
        safeCores >= 8 -> 4
        safeCores >= 6 -> 3
        safeCores >= 2 -> 2
        else -> 1
    }
    val segmentMaxWorkers = safeSegmentCount.takeIf { it > 0 } ?: 1
    val memoryMaxWorkers = if (availMemBytes <= 0L) {
        1
    } else {
        (availMemBytes / modelCopyBudgetBytes(modelSpec))
            .toInt()
            .coerceAtLeast(1)
    }
    val workerCount = minOf(modelMaxWorkers, coreMaxWorkers, memoryMaxWorkers, segmentMaxWorkers)
        .coerceAtLeast(1)
    return WhisperRecognitionConcurrency(
        workerCount = workerCount,
        threadsPerWorker = (safeCores / workerCount)
            .coerceAtLeast(1)
            .coerceAtMost(MAX_RECOGNIZER_THREADS_PER_WORKER),
    )
}

private fun modelCopyBudgetBytes(modelSpec: WhisperModelSpec): Long {
    val modelBytes = modelSpec.files
        .filter { it.fileName == modelSpec.encoderFileName || it.fileName == modelSpec.decoderFileName }
        .sumOf { it.sizeBytes }
        .takeIf { it > 0L }
        ?: modelSpec.sizeBytes
    return if (modelBytes <= COMPACT_MODEL_FILE_THRESHOLD_BYTES) {
        modelBytes * COMPACT_MODEL_MEMORY_MULTIPLIER + COMPACT_MODEL_COPY_MEMORY_OVERHEAD_BYTES
    } else {
        modelBytes * LARGE_MODEL_MEMORY_MULTIPLIER + LARGE_MODEL_COPY_MEMORY_OVERHEAD_BYTES
    }
}

internal fun buildWhisperRecognizer(
    spec: WhisperModelSpec,
    paths: WhisperModelPaths,
    numThreads: Int,
): OfflineRecognizer {
    return OfflineRecognizer(
        config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = WHISPER_SAMPLE_RATE, featureDim = 80, dither = 0.0f),
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = paths.encoder.absolutePath,
                    decoder = paths.decoder.absolutePath,
                    language = "ja",
                    task = "transcribe",
                    enableSegmentTimestamps = true,
                ),
                tokens = paths.tokens.absolutePath,
                modelType = "whisper",
                numThreads = numThreads,
                provider = "cpu",
            ),
        ),
    )
}

internal fun recognitionMemoryBudgetBytes(context: Context): Long {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        ?: return 0L
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)
    if (memoryInfo.lowMemory) {
        return 0L
    }
    val systemReserve = if (memoryInfo.totalMem > 0L) {
        memoryInfo.totalMem / 8L
    } else {
        0L
    }
    return (memoryInfo.availMem - systemReserve).coerceAtLeast(0L)
}

internal class StreamingTranscriptionProgress {
    private var decodeProgress: Float = 0f
    private var producedSamples: Long = 0L
    private var recognizedSamples: Long = 0L
    private var lastProgress: Float = 0f

    fun recordDecodeProgress(progress: Float) {
        decodeProgress = max(decodeProgress, progress.coerceIn(0f, 1f))
    }

    fun recordSegmentProduced(sampleCount: Int) {
        producedSamples += sampleCount.coerceAtLeast(0).toLong()
    }

    fun recordSegmentRecognized(sampleCount: Int) {
        recognizedSamples += sampleCount.coerceAtLeast(0).toLong()
    }

    fun progress(): Float {
        val vadProgress = decodeProgress * VAD_PROGRESS_WEIGHT
        val recognitionRatio = if (producedSamples > 0L) {
            recognizedSamples.toFloat() / producedSamples.toFloat()
        } else {
            0f
        }
        val recognitionProgress = minOf(recognitionRatio, decodeProgress) * RECOGNITION_PROGRESS_WEIGHT
        lastProgress = max(lastProgress, (vadProgress + recognitionProgress).coerceIn(0f, 1f))
        return lastProgress
    }
}
