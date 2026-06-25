package io.github.summerdez.asmrplayer.data.ai

import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlin.math.max

internal const val VAD_MODEL_THRESHOLD = 0.5f
internal const val VAD_MIN_SILENCE_DURATION_SECONDS = 0.45f
internal const val VAD_MIN_SPEECH_DURATION_SECONDS = 0.25f
internal const val VAD_MAX_SPEECH_DURATION_SECONDS = 30.0f
internal const val VAD_WINDOW_SIZE = 512
internal const val VAD_MERGE_MAX_GAP_MS = 600L
internal val VAD_MERGE_MAX_SPEECH_DURATION_MS: Long = (VAD_MAX_SPEECH_DURATION_SECONDS * 1000f).toLong()

internal data class VadSpeechSegment(
    val startMs: Long,
    val endMs: Long,
    val samples: FloatArray,
)

internal data class VadMergeConfig(
    val maxGapMs: Long = VAD_MERGE_MAX_GAP_MS,
    val maxSpeechDurationMs: Long = VAD_MERGE_MAX_SPEECH_DURATION_MS,
)

internal fun buildSileroVad(paths: WhisperModelPaths): Vad {
    return Vad(
        config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = paths.vad.absolutePath,
                threshold = VAD_MODEL_THRESHOLD,
                minSilenceDuration = VAD_MIN_SILENCE_DURATION_SECONDS,
                minSpeechDuration = VAD_MIN_SPEECH_DURATION_SECONDS,
                windowSize = VAD_WINDOW_SIZE,
                maxSpeechDuration = VAD_MAX_SPEECH_DURATION_SECONDS,
            ),
            sampleRate = WHISPER_SAMPLE_RATE,
            numThreads = 1,
            provider = "cpu",
        ),
    )
}

internal suspend fun drainVadSpeechSegments(
    vad: Vad,
    onSegment: suspend (VadSpeechSegment) -> Unit,
) {
    while (!vad.empty()) {
        val segment = vad.front()
        if (segment.samples.isNotEmpty()) {
            val startMs = segment.start * 1000L / WHISPER_SAMPLE_RATE
            onSegment(
                VadSpeechSegment(
                    startMs = startMs,
                    endMs = startMs + whisperSampleCountToMs(segment.samples.size),
                    samples = segment.samples,
                ),
            )
        }
        vad.pop()
    }
}

internal fun whisperSampleCountToMs(sampleCount: Int): Long {
    return sampleCount * 1000L / WHISPER_SAMPLE_RATE
}

internal fun mergeVadSpeechSegments(
    segments: List<VadSpeechSegment>,
    config: VadMergeConfig = VadMergeConfig(),
): List<VadSpeechSegment> {
    if (segments.isEmpty()) {
        return emptyList()
    }
    val mergedSegments = mutableListOf<VadSpeechSegment>()
    var pending = segments.first()
    segments.drop(1).forEach { segment ->
        if (shouldMergeVadSpeechSegments(pending, segment, config)) {
            pending = mergeVadSpeechSegmentPair(pending, segment)
        } else {
            mergedSegments += pending
            pending = segment
        }
    }
    mergedSegments += pending
    return mergedSegments
}

internal fun shouldMergeVadSpeechSegments(
    previous: VadSpeechSegment,
    next: VadSpeechSegment,
    config: VadMergeConfig = VadMergeConfig(),
): Boolean {
    val gapMs = next.startMs - previous.endMs
    val mergedSpeechDurationMs = whisperSampleCountToMs(previous.samples.size + next.samples.size)
    return gapMs in 0..config.maxGapMs &&
        mergedSpeechDurationMs <= config.maxSpeechDurationMs
}

private fun mergeVadSpeechSegmentPair(
    previous: VadSpeechSegment,
    next: VadSpeechSegment,
): VadSpeechSegment {
    return VadSpeechSegment(
        startMs = previous.startMs,
        endMs = max(previous.endMs, next.endMs),
        samples = previous.samples + next.samples,
    )
}

internal class StreamingVadSegmentMerger(
    private val config: VadMergeConfig = VadMergeConfig(),
    private val onSegmentReady: suspend (VadSpeechSegment) -> Unit,
) {
    private var pending: VadSpeechSegment? = null

    suspend fun accept(segment: VadSpeechSegment) {
        val current = pending
        if (current == null) {
            pending = segment
            return
        }
        if (shouldMergeVadSpeechSegments(current, segment, config)) {
            pending = mergeVadSpeechSegmentPair(current, segment)
        } else {
            onSegmentReady(current)
            pending = segment
        }
    }

    suspend fun flush() {
        pending?.let { onSegmentReady(it) }
        pending = null
    }
}
