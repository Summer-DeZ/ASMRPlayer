package io.github.summerdez.asmrplayer.data.ai

import android.app.ActivityManager
import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import io.github.summerdez.asmrplayer.domain.model.SubtitleGenerationTarget
import io.github.summerdez.asmrplayer.domain.model.SubtitleLine
import io.github.summerdez.asmrplayer.domain.model.WhisperModelSpec
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.max
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface OnDeviceTranscriber {
    suspend fun transcribeJapanese(
        context: Context,
        target: SubtitleGenerationTarget,
        modelState: WhisperModelState,
        onProgress: (progress: Float, preview: List<SubtitleLine>) -> Unit,
    ): List<SubtitleLine>
}

internal const val WHISPER_SAMPLE_RATE = 16_000
internal const val WHISPER_PREVIEW_LINE_COUNT = 8
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

internal data class WhisperRecognitionConcurrency(
    val workerCount: Int,
    val threadsPerWorker: Int,
)

private data class IndexedVadSpeechSegment(
    val index: Int,
    val segment: VadSpeechSegment,
)

private const val COMPACT_MODEL_FILE_THRESHOLD_BYTES = 220L * 1024L * 1024L
private const val COMPACT_MODEL_MEMORY_MULTIPLIER = 3L
private const val LARGE_MODEL_MEMORY_MULTIPLIER = 2L
private const val COMPACT_MODEL_COPY_MEMORY_OVERHEAD_BYTES = 96L * 1024L * 1024L
private const val LARGE_MODEL_COPY_MEMORY_OVERHEAD_BYTES = 192L * 1024L * 1024L
private const val STREAMING_SEGMENT_COUNT_HINT = Int.MAX_VALUE
private const val MAX_RECOGNIZER_THREADS_PER_WORKER = 4
private const val VAD_PROGRESS_WEIGHT = 0.2f
private const val RECOGNITION_PROGRESS_WEIGHT = 0.8f

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

private class StreamingVadSegmentMerger(
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

class WhisperRuntimeTranscriber : OnDeviceTranscriber {
    override suspend fun transcribeJapanese(
        context: Context,
        target: SubtitleGenerationTarget,
        modelState: WhisperModelState,
        onProgress: (progress: Float, preview: List<SubtitleLine>) -> Unit,
    ): List<SubtitleLine> = withContext(Dispatchers.IO) {
        val paths = modelState.paths ?: throw IOException("请先在设置页下载 ${modelState.spec.label}")
        if (!modelState.downloaded) {
            throw IOException("请先在设置页下载完整的 ${modelState.spec.label}")
        }
        val audioUri = Uri.parse(target.audioUri)
        context.contentResolver.openInputStream(audioUri)?.use {
            // Report URI permission or file errors before runtime/model initialization.
        } ?: throw IOException("无法读取音频文件")

        val concurrency = planWhisperRecognitionConcurrency(
            modelSpec = modelState.spec,
            availMemBytes = recognitionMemoryBudgetBytes(context),
            cores = Runtime.getRuntime().availableProcessors(),
            segmentCount = STREAMING_SEGMENT_COUNT_HINT,
        )
        val lines = transcribeSegmentsWithPipeline(
            context = context,
            audioUri = audioUri,
            spec = modelState.spec,
            paths = paths,
            concurrency = concurrency,
            onProgress = onProgress,
        )
        if (lines.isEmpty()) {
            throw IOException("未识别到可用语音片段")
        }
        onProgress(1f, lines.takeLast(WHISPER_PREVIEW_LINE_COUNT))
        lines
    }

    private fun buildRecognizer(
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

    private suspend fun transcribeSegmentsWithPipeline(
        context: Context,
        audioUri: Uri,
        spec: WhisperModelSpec,
        paths: WhisperModelPaths,
        concurrency: WhisperRecognitionConcurrency,
        onProgress: (progress: Float, preview: List<SubtitleLine>) -> Unit,
    ): List<SubtitleLine> = coroutineScope {
        val taskChannel = Channel<IndexedVadSpeechSegment>((concurrency.workerCount * 2).coerceAtLeast(1))
        val results = mutableMapOf<Int, SubtitleLine>()
        val progressTracker = StreamingTranscriptionProgress()
        val resultMutex = Mutex()

        val producer = launch {
            val vad = buildSileroVad(paths)
            var nextSegmentIndex = 0
            var closeCause: Throwable? = null
            val segmentMerger = StreamingVadSegmentMerger { segment ->
                updateTranscriptionProgress(resultMutex, progressTracker, results, onProgress) {
                    recordSegmentProduced(segment.samples.size)
                }
                taskChannel.send(
                    IndexedVadSpeechSegment(
                        index = nextSegmentIndex,
                        segment = segment,
                    ),
                )
                nextSegmentIndex += 1
            }
            try {
                AndroidAudioPcmDecoder.decodeToMono16k(
                    context = context,
                    audioUri = audioUri,
                    onDecodeProgress = { progress ->
                        updateTranscriptionProgress(resultMutex, progressTracker, results, onProgress) {
                            recordDecodeProgress(progress)
                        }
                    },
                    onSamples = { samples ->
                        currentCoroutineContext().ensureActive()
                        vad.acceptWaveform(samples)
                        drainVadSpeechSegments(vad) { segment ->
                            segmentMerger.accept(segment)
                        }
                    },
                )
                vad.flush()
                drainVadSpeechSegments(vad) { segment ->
                    segmentMerger.accept(segment)
                }
                segmentMerger.flush()
            } catch (error: Throwable) {
                closeCause = error
                throw error
            } finally {
                vad.release()
                taskChannel.close(closeCause)
            }
        }

        val workers = List(concurrency.workerCount) {
            launch {
                var recognizer: OfflineRecognizer? = null
                try {
                    for (task in taskChannel) {
                        currentCoroutineContext().ensureActive()
                        val activeRecognizer = recognizer ?: buildRecognizer(
                            spec = spec,
                            paths = paths,
                            numThreads = concurrency.threadsPerWorker,
                        ).also { recognizer = it }
                        val segment = task.segment
                        val text = recognizeSegment(activeRecognizer, segment.samples)
                        val line = if (text.isBlank()) {
                            null
                        } else {
                            SubtitleLine(
                                id = (task.index + 1).toString(),
                                startMs = segment.startMs,
                                endMs = segment.endMs.coerceAtLeast(segment.startMs + 250L),
                                sourceText = text,
                            )
                        }
                        updateTranscriptionProgress(resultMutex, progressTracker, results, onProgress) {
                            if (line != null) {
                                results[task.index] = line
                            }
                            recordSegmentRecognized(segment.samples.size)
                        }
                    }
                } finally {
                    recognizer?.release()
                }
            }
        }

        producer.join()
        workers.forEach { it.join() }
        orderedLines(results.values)
    }

    private suspend fun updateTranscriptionProgress(
        resultMutex: Mutex,
        progressTracker: StreamingTranscriptionProgress,
        results: Map<Int, SubtitleLine>,
        onProgress: (progress: Float, preview: List<SubtitleLine>) -> Unit,
        update: StreamingTranscriptionProgress.() -> Unit,
    ) {
        resultMutex.withLock {
            progressTracker.update()
            onProgress(progressTracker.progress(), previewLines(results.values))
        }
    }

    private fun recognizeSegment(recognizer: OfflineRecognizer, samples: FloatArray): String {
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, WHISPER_SAMPLE_RATE)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    private fun recognitionMemoryBudgetBytes(context: Context): Long {
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

    companion object {
        fun runtimeName(spec: WhisperModelSpec): String = "Whisper ${spec.id}"

        private fun orderedLines(results: Iterable<SubtitleLine>): List<SubtitleLine> {
            return results
                .sortedBy { it.startMs }
                .mapIndexed { index, line -> line.copy(id = (index + 1).toString()) }
        }

        private fun previewLines(results: Iterable<SubtitleLine>): List<SubtitleLine> {
            return orderedLines(results).takeLast(WHISPER_PREVIEW_LINE_COUNT)
        }
    }
}

private class StreamingTranscriptionProgress {
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

internal object AndroidAudioPcmDecoder {
    private const val TIMEOUT_US = 10_000L

    suspend fun decodeToMono16k(
        context: Context,
        audioUri: Uri,
        onDecodeProgress: suspend (progress: Float) -> Unit,
        onSamples: suspend (samples: FloatArray) -> Unit,
    ) {
        val extractor = MediaExtractor()
        val decoder: MediaCodec
        val durationUs: Long
        try {
            context.contentResolver.openAssetFileDescriptor(audioUri, "r")?.use { afd ->
                if (afd.declaredLength >= 0L) {
                    extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.declaredLength)
                } else {
                    extractor.setDataSource(afd.fileDescriptor)
                }
            } ?: throw IOException("无法打开音频文件")
            val trackIndex = selectAudioTrack(extractor)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                inputFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                -1L
            }
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IOException("音频格式缺少 MIME 信息")
            extractor.selectTrack(trackIndex)
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
        } catch (error: Throwable) {
            extractor.release()
            throw error
        }

        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEnd = false
        var sawOutputEnd = false
        var channelCount = 1
        var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
        var resampler: StreamingLinearResampler? = null

        try {
            decoder.start()
            while (!sawOutputEnd) {
                currentCoroutineContext().ensureActive()
                if (!sawInputEnd) {
                    val inputIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex)
                            ?: throw IOException("音频解码输入缓冲区不可用")
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            sawInputEnd = true
                        } else {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime,
                                extractor.sampleFlags,
                            )
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = decoder.outputFormat
                        val sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        pcmEncoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else {
                            AudioFormat.ENCODING_PCM_16BIT
                        }
                        resampler = StreamingLinearResampler(sampleRate, 16_000)
                    }
                    else -> if (outputIndex >= 0) {
                        val outputBuffer = decoder.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            if (resampler == null) {
                                val outputFormat = decoder.outputFormat
                                val sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                pcmEncoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                    outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                                } else {
                                    AudioFormat.ENCODING_PCM_16BIT
                                }
                                resampler = StreamingLinearResampler(sampleRate, 16_000)
                            }
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val mono = outputBuffer.toMonoFloatArray(channelCount, pcmEncoding)
                            resampler.process(mono).takeIf { it.isNotEmpty() }?.let { onSamples(it) }
                            if (durationUs > 0L && bufferInfo.presentationTimeUs >= 0L) {
                                onDecodeProgress(
                                    (bufferInfo.presentationTimeUs.toFloat() / durationUs.toFloat())
                                        .coerceIn(0f, 0.98f),
                                )
                            }
                        }
                        sawOutputEnd = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        decoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
            resampler?.flush()?.takeIf { it.isNotEmpty() }?.let { onSamples(it) }
            onDecodeProgress(1f)
        } finally {
            decoder.stop()
            decoder.release()
            extractor.release()
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) {
                return index
            }
        }
        throw IOException("文件中没有可解码的音频轨道")
    }

    private fun ByteBuffer.toMonoFloatArray(channelCount: Int, pcmEncoding: Int): FloatArray {
        val duplicate = slice().order(ByteOrder.LITTLE_ENDIAN)
        return when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> duplicate.floatPcmToMono(channelCount)
            AudioFormat.ENCODING_PCM_8BIT -> duplicate.u8PcmToMono(channelCount)
            else -> duplicate.s16PcmToMono(channelCount)
        }
    }

    private fun ByteBuffer.s16PcmToMono(channelCount: Int): FloatArray {
        val shorts = asShortBuffer()
        val frameCount = shorts.remaining() / channelCount
        return FloatArray(frameCount) { frame ->
            var sum = 0f
            repeat(channelCount) { channel ->
                sum += shorts.get(frame * channelCount + channel) / 32768f
            }
            sum / channelCount
        }
    }

    private fun ByteBuffer.floatPcmToMono(channelCount: Int): FloatArray {
        val floats = asFloatBuffer()
        val frameCount = floats.remaining() / channelCount
        return FloatArray(frameCount) { frame ->
            var sum = 0f
            repeat(channelCount) { channel ->
                sum += floats.get(frame * channelCount + channel)
            }
            (sum / channelCount).coerceIn(-1f, 1f)
        }
    }

    private fun ByteBuffer.u8PcmToMono(channelCount: Int): FloatArray {
        val frameCount = remaining() / channelCount
        return FloatArray(frameCount) { frame ->
            var sum = 0f
            repeat(channelCount) { channel ->
                sum += ((get(frame * channelCount + channel).toInt() and 0xff) - 128) / 128f
            }
            sum / channelCount
        }
    }
}

internal class StreamingLinearResampler(
    private val inputRate: Int,
    private val outputRate: Int,
) {
    private var pending = FloatArray(0)
    private var nextSourceIndex = 0.0
    private val step = inputRate.toDouble() / outputRate.toDouble()

    fun process(input: FloatArray): FloatArray {
        if (input.isEmpty()) {
            return FloatArray(0)
        }
        if (inputRate == outputRate && pending.isEmpty()) {
            return input
        }
        pending = pending + input
        val output = ArrayList<Float>(max(16, (pending.size * outputRate) / inputRate))
        while (nextSourceIndex + 1.0 < pending.size) {
            val lower = floor(nextSourceIndex).toInt()
            val fraction = (nextSourceIndex - lower).toFloat()
            val sample = pending[lower] + (pending[lower + 1] - pending[lower]) * fraction
            output += sample.coerceIn(-1f, 1f)
            nextSourceIndex += step
        }
        discardConsumedSamples()
        return output.toFloatArray()
    }

    fun flush(): FloatArray {
        if (pending.isEmpty()) {
            return FloatArray(0)
        }
        val output = ArrayList<Float>()
        while (nextSourceIndex < pending.size) {
            val index = floor(nextSourceIndex).toInt().coerceIn(0, pending.lastIndex)
            output += pending[index].coerceIn(-1f, 1f)
            nextSourceIndex += step
        }
        pending = FloatArray(0)
        nextSourceIndex = 0.0
        return output.toFloatArray()
    }

    private fun discardConsumedSamples() {
        val consumed = floor(nextSourceIndex)
            .toInt()
            .coerceIn(0, pending.lastIndex.coerceAtLeast(0))
        if (consumed <= 0) {
            return
        }
        pending = pending.copyOfRange(consumed, pending.size)
        nextSourceIndex -= consumed
    }
}
