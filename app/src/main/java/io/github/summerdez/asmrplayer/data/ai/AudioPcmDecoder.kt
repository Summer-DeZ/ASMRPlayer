package io.github.summerdez.asmrplayer.data.ai

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.max
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

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
            val createdDecoder = MediaCodec.createDecoderByType(mime)
            try {
                createdDecoder.configure(inputFormat, null, null, 0)
            } catch (error: Throwable) {
                runCatching { createdDecoder.release() }
                throw error
            }
            decoder = createdDecoder
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
        var decoderStarted = false

        try {
            decoder.start()
            decoderStarted = true
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
                        resampler = StreamingLinearResampler(sampleRate, WHISPER_SAMPLE_RATE)
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
                                resampler = StreamingLinearResampler(sampleRate, WHISPER_SAMPLE_RATE)
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
            if (decoderStarted) {
                runCatching { decoder.stop() }
            }
            try {
                decoder.release()
            } finally {
                extractor.release()
            }
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
