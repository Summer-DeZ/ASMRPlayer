package io.github.summerdez.asmrplayer.data.ai

import android.content.Context
import android.net.Uri
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import io.github.summerdez.asmrplayer.domain.model.SubtitleGenerationTarget
import io.github.summerdez.asmrplayer.domain.model.SubtitleLine
import io.github.summerdez.asmrplayer.domain.model.WhisperModelSpec
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
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

private data class IndexedVadSpeechSegment(
    val index: Int,
    val segment: VadSpeechSegment,
)

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
                        val activeRecognizer = recognizer ?: buildWhisperRecognizer(
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
