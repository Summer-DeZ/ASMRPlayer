package io.github.summerdez.asmrplayer.data.ai

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
import java.util.concurrent.atomic.AtomicLong
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

internal data class VadSpeechSegment(
    val startMs: Long,
    val samples: FloatArray,
)

internal fun buildSileroVad(paths: WhisperModelPaths): Vad {
    return Vad(
        config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = paths.vad.absolutePath,
                threshold = 0.5f,
                minSilenceDuration = 0.45f,
                minSpeechDuration = 0.25f,
                windowSize = 512,
                maxSpeechDuration = 30.0f,
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
            onSegment(
                VadSpeechSegment(
                    startMs = segment.start * 1000L / WHISPER_SAMPLE_RATE,
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

        val speechSegments = mutableListOf<VadSpeechSegment>()
        var lastDecodeProgress = 0f
        val vad = buildSileroVad(paths)
        try {
            AndroidAudioPcmDecoder.decodeToMono16k(
                context = context,
                audioUri = audioUri,
                onDecodeProgress = { progress ->
                    lastDecodeProgress = progress.coerceIn(0f, 1f)
                    onProgress(lastDecodeProgress * VAD_PROGRESS_WEIGHT, emptyList())
                },
                onSamples = { samples ->
                    currentCoroutineContext().ensureActive()
                    vad.acceptWaveform(samples)
                    collectVadSegments(vad, speechSegments)
                    onProgress(lastDecodeProgress * VAD_PROGRESS_WEIGHT, emptyList())
                },
            )
            vad.flush()
            collectVadSegments(vad, speechSegments)
        } finally {
            vad.release()
        }
        if (speechSegments.isEmpty()) {
            throw IOException("未识别到可用语音片段")
        }
        val lines = recognizeSegmentsInParallel(
            spec = modelState.spec,
            paths = paths,
            segments = speechSegments,
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
        numThreads: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 4),
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

    private suspend fun collectVadSegments(
        vad: Vad,
        segments: MutableList<VadSpeechSegment>,
    ) {
        drainVadSpeechSegments(vad) { segment ->
            segments += segment
        }
    }

    private suspend fun recognizeSegmentsInParallel(
        spec: WhisperModelSpec,
        paths: WhisperModelPaths,
        segments: List<VadSpeechSegment>,
        onProgress: (progress: Float, preview: List<SubtitleLine>) -> Unit,
    ): List<SubtitleLine> {
        val results = arrayOfNulls<SubtitleLine>(segments.size)
        val completedSamples = AtomicLong(0L)
        val totalSamples = segments.sumOf { it.samples.size.toLong() }.coerceAtLeast(1L)
        val resultMutex = Mutex()
        val workerCount = cpuWorkerCount(segments.size)
        val recognizerThreads = if (workerCount > 1) 1 else Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        coroutineScope {
            val taskChannel = Channel<IndexedValue<VadSpeechSegment>>(Channel.UNLIMITED)
            repeat(workerCount) {
                launch {
                    val recognizer = buildRecognizer(spec, paths, recognizerThreads)
                    try {
                        for (task in taskChannel) {
                            currentCoroutineContext().ensureActive()
                            val segment = task.value
                            val text = recognizeSegment(recognizer, segment.samples)
                            val endMs = segment.startMs + whisperSampleCountToMs(segment.samples.size)
                            val line = if (text.isBlank()) {
                                null
                            } else {
                                SubtitleLine(
                                    id = (task.index + 1).toString(),
                                    startMs = segment.startMs,
                                    endMs = endMs.coerceAtLeast(segment.startMs + 250L),
                                    sourceText = text,
                                )
                            }
                            val doneSamples = completedSamples.addAndGet(segment.samples.size.toLong())
                            resultMutex.withLock {
                                results[task.index] = line
                                val progress = VAD_PROGRESS_WEIGHT +
                                    (doneSamples.toFloat() / totalSamples.toFloat()) * RECOGNITION_PROGRESS_WEIGHT
                                onProgress(progress.coerceIn(VAD_PROGRESS_WEIGHT, 1f), previewLines(results))
                            }
                        }
                    } finally {
                        recognizer.release()
                    }
                }
            }
            segments.forEachIndexed { index, segment ->
                taskChannel.send(IndexedValue(index, segment))
            }
            taskChannel.close()
        }
        return orderedLines(results)
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

    companion object {
        private const val VAD_PROGRESS_WEIGHT = 0.2f
        private const val RECOGNITION_PROGRESS_WEIGHT = 0.8f

        fun runtimeName(spec: WhisperModelSpec): String = "Whisper ${spec.id}"

        private fun cpuWorkerCount(segmentCount: Int): Int {
            if (segmentCount <= 1) {
                return 1
            }
            return minOf(2, Runtime.getRuntime().availableProcessors().coerceAtLeast(1), segmentCount)
        }

        private fun orderedLines(results: Array<SubtitleLine?>): List<SubtitleLine> {
            return results
                .filterNotNull()
                .sortedBy { it.startMs }
                .mapIndexed { index, line -> line.copy(id = (index + 1).toString()) }
        }

        private fun previewLines(results: Array<SubtitleLine?>): List<SubtitleLine> {
            return orderedLines(results).takeLast(WHISPER_PREVIEW_LINE_COUNT)
        }
    }
}

internal object AndroidAudioPcmDecoder {
    private const val TIMEOUT_US = 10_000L

    suspend fun decodeToMono16k(
        context: Context,
        audioUri: Uri,
        onDecodeProgress: (progress: Float) -> Unit,
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
