package io.github.summerdez.asmrplayer

import io.github.summerdez.asmrplayer.data.ai.AiSubtitleVtt
import io.github.summerdez.asmrplayer.data.ai.AiSubtitleSegmentCache
import io.github.summerdez.asmrplayer.data.ai.AiSubtitleTranslationCache
import io.github.summerdez.asmrplayer.data.ai.AsrBackendPlanner
import io.github.summerdez.asmrplayer.data.ai.AsrRuntimeBackend
import io.github.summerdez.asmrplayer.data.ai.OpenAiCompatibleTranslator
import io.github.summerdez.asmrplayer.data.ai.StreamingLinearResampler
import io.github.summerdez.asmrplayer.domain.model.AiAsrBackendPreference
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleSettings
import io.github.summerdez.asmrplayer.domain.model.AiTranslationEngine
import io.github.summerdez.asmrplayer.domain.model.GpuWhisperModelSpec
import io.github.summerdez.asmrplayer.domain.model.SubtitleGenerationTarget
import io.github.summerdez.asmrplayer.domain.model.SubtitleLine
import io.github.summerdez.asmrplayer.domain.model.WhisperModelSpec
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSubtitlePipelineTest {
    @Test
    fun vttWriterOutputsTranslatedOnlyCues() {
        val lines = listOf(
            SubtitleLine("1", 1_250L, 3_500L, "そっと囁くね", "轻轻地低语哦"),
            SubtitleLine("2", 61_000L, 62_250L, "大丈夫", "没关系"),
        )

        val vtt = AiSubtitleVtt.translated(lines)

        assertTrue(vtt.startsWith("WEBVTT"))
        assertTrue(vtt.contains("00:00:01.250 --> 00:00:03.500"))
        assertTrue(vtt.contains("轻轻地低语哦"))
        assertTrue(!vtt.contains("そっと囁くね"))
        assertTrue(vtt.contains("00:01:01.000 --> 00:01:02.250"))
    }

    @Test
    fun translationObjectParserKeepsTimelineIds() {
        val source = listOf(
            SubtitleLine("1", 0L, 1_000L, "耳元に来て"),
            SubtitleLine("2", 1_000L, 2_000L, "深呼吸して"),
        )

        val parsed = OpenAiCompatibleTranslator.parseTranslationObject(
            """{"1":"来到耳边","2":"深呼吸"}""",
            source,
        )

        assertEquals(source.map { it.id }, parsed.map { it.id })
        assertEquals("来到耳边", parsed[0].translatedText)
        assertEquals("深呼吸", parsed[1].translatedText)
    }

    @Test
    fun looseParserRecoversJsonWrappedInText() {
        val source = listOf(SubtitleLine("12", 0L, 1_000L, "おやすみ"))

        val parsed = OpenAiCompatibleTranslator.parseLoose(
            "结果如下：\n```json\n{\"12\":\"晚安\"}\n```",
            source,
        )

        assertEquals(1, parsed.size)
        assertEquals("晚安", parsed[0].translatedText)
    }

    @Test
    fun translationContentParserFallsBackWhenResponseIsNotPureJsonObject() {
        val source = listOf(
            SubtitleLine("1", 0L, 1_000L, "耳元に来て"),
            SubtitleLine("2", 1_000L, 2_000L, "深呼吸して"),
        )

        val parsed = OpenAiCompatibleTranslator.parseTranslationContent(
            "翻译结果如下：\n```json\n{\"1\":\"来到耳边\",\"2\":\"深呼吸\"}\n```",
            source,
        )

        assertEquals(source.map { it.id }, parsed.map { it.id })
        assertEquals("来到耳边", parsed[0].translatedText)
        assertEquals("深呼吸", parsed[1].translatedText)
    }

    @Test
    fun completeTranslationContentAcceptsLooseFullBatch() {
        val source = listOf(
            SubtitleLine("1", 0L, 1_000L, "耳元に来て"),
            SubtitleLine("2", 1_000L, 2_000L, "深呼吸して"),
        )

        val parsed = OpenAiCompatibleTranslator.parseCompleteTranslationContent(
            content = "翻译结果如下：\n```json\n{\"1\":\"来到耳边\",\"2\":\"深呼吸\"}\n```",
            finishReason = "stop",
            sourceLines = source,
        )

        assertEquals(listOf("来到耳边", "深呼吸"), parsed.map { it.translatedText })
    }

    @Test
    fun completeTranslationContentRejectsTruncatedBatch() {
        val source = listOf(SubtitleLine("1", 0L, 1_000L, "耳元に来て"))

        val error = assertThrows(
            IllegalArgumentException::class.java,
        ) {
            OpenAiCompatibleTranslator.parseCompleteTranslationContent(
                content = "{\"1\":\"来到耳边\"",
                finishReason = "length",
                sourceLines = source,
            )
        }

        assertTrue(error.message.orEmpty().contains("截断"))
    }

    @Test
    fun completeTranslationContentRequiresEverySourceLine() {
        val source = listOf(
            SubtitleLine("1", 0L, 1_000L, "耳元に来て"),
            SubtitleLine("2", 1_000L, 2_000L, "深呼吸して"),
        )

        val error = assertThrows(
            IllegalArgumentException::class.java,
        ) {
            OpenAiCompatibleTranslator.parseCompleteTranslationContent(
                content = """{"1":"来到耳边"}""",
                finishReason = "stop",
                sourceLines = source,
            )
        }

        assertTrue(error.message.orEmpty().contains("完整"))
    }

    @Test
    fun segmentCacheRestoresSourceLinesForMatchingTrackAudioAndModel() {
        val cacheDir = Files.createTempDirectory("ai-subtitle-segments").toFile()
        val cache = AiSubtitleSegmentCache(cacheDir)
        val target = SubtitleGenerationTarget(
            playlistId = "playlist-1",
            trackId = "track-1",
            trackTitle = "track",
            audioUri = "content://audio/1",
            contextTitle = "playlist",
        )
        val lines = listOf(
            SubtitleLine("1", 0L, 1_000L, "耳元に来て"),
            SubtitleLine("2", 1_000L, 2_000L, "深呼吸して"),
        )

        cache.save(target, "base", lines)

        assertEquals(lines, cache.load(target, "base"))
        assertNull(cache.load(target.copy(audioUri = "content://audio/2"), "base"))
        assertNull(cache.load(target, "small"))
    }

    @Test
    fun translationCacheRestoresOnlyMatchingContextAndSourceLines() {
        val cacheDir = Files.createTempDirectory("ai-subtitle-translations").toFile()
        val cache = AiSubtitleTranslationCache(cacheDir)
        val target = SubtitleGenerationTarget(
            playlistId = "playlist-1",
            trackId = "track-1",
            trackTitle = "track",
            audioUri = "content://audio/1",
            contextTitle = "playlist",
        )
        val settings = aiSettings()
        val source = listOf(
            SubtitleLine("1", 0L, 1_000L, "耳元に来て"),
            SubtitleLine("2", 1_000L, 2_000L, "深呼吸して"),
        )
        val translated = listOf(
            source[0].copy(translatedText = "来到耳边"),
            source[1].copy(translatedText = "深呼吸"),
        )

        cache.save(target, "base", settings, source, translated)

        assertEquals(translated, cache.load(target, "base", settings, source))
        assertNull(cache.load(target.copy(audioUri = "content://audio/2"), "base", settings, source))
        assertNull(cache.load(target, "small", settings, source))
        assertNull(
            cache.load(
                target,
                "base",
                settings.copy(ollamaModel = "qwen2.5:14b"),
                source,
            ),
        )
        assertNull(cache.load(target, "base", settings, source.map { it.copy(sourceText = "${it.sourceText}!") }))
    }

    @Test
    fun whisperModelSpecsKeepBaseDefaultAndSmallOptional() {
        assertEquals("base", WhisperModelSpec.DEFAULT_ID)
        assertEquals(WhisperModelSpec.BASE, WhisperModelSpec.byId(null))
        assertEquals(listOf("base", "small"), WhisperModelSpec.ALL.map { it.id })
        assertTrue(WhisperModelSpec.BASE.files.any { it.fileName == "base-encoder.int8.onnx" })
        assertTrue(WhisperModelSpec.SMALL.files.any { it.fileName == "small-encoder.int8.onnx" })
        assertTrue(WhisperModelSpec.SMALL.sizeBytes > WhisperModelSpec.BASE.sizeBytes)
    }

    @Test
    fun gpuWhisperModelSpecsStaySeparateFromCpuOnnxModels() {
        assertEquals("base-q5_1", GpuWhisperModelSpec.DEFAULT_ID)
        assertEquals(GpuWhisperModelSpec.BASE, GpuWhisperModelSpec.byId(null))
        assertEquals(listOf("base-q5_1", "small-q5_1"), GpuWhisperModelSpec.ALL.map { it.id })
        assertEquals("ggml-base-q5_1.bin", GpuWhisperModelSpec.BASE.fileName)
        assertEquals("ggml-small-q5_1.bin", GpuWhisperModelSpec.SMALL.fileName)
        assertTrue(GpuWhisperModelSpec.SMALL.experimental)
        assertTrue(GpuWhisperModelSpec.SMALL.sizeBytes > GpuWhisperModelSpec.BASE.sizeBytes)
        assertTrue(GpuWhisperModelSpec.BASE.directoryName.startsWith("whisper-cpp"))
    }

    @Test
    fun deepSeekDefaultModelUsesFlash() {
        assertEquals("deepseek-v4-flash", AiTranslationEngine.DEEPSEEK.defaultModel)
    }

    @Test
    fun asrBackendPlannerKeepsCpuFallbackWhenGpuBackendIsMissing() {
        val decision = AsrBackendPlanner.choose(
            preference = AiAsrBackendPreference.AUTO,
            gpuAvailable = true,
            gpuBackendRegistered = false,
        )

        assertEquals(AsrRuntimeBackend.SHERPA_ONNX_CPU, decision.backend)
        assertTrue(decision.fallbackReason.contains("CPU"))
    }

    @Test
    fun asrBackendPlannerSelectsGpuOnlyWhenAvailableAndRegistered() {
        val decision = AsrBackendPlanner.choose(
            preference = AiAsrBackendPreference.GPU_EXPERIMENTAL,
            gpuAvailable = true,
            gpuBackendRegistered = true,
        )

        assertEquals(AsrRuntimeBackend.WHISPER_CPP_VULKAN, decision.backend)
    }

    @Test
    fun resamplerKeepsBoundarySampleAcrossUnevenChunks() {
        val resampler = StreamingLinearResampler(inputRate = 48_000, outputRate = 16_000)

        val first = resampler.process(FloatArray(623) { it / 623f })
        val second = resampler.process(FloatArray(623) { it / 623f })

        assertTrue(first.isNotEmpty())
        assertTrue(second.isNotEmpty())
    }

    private fun aiSettings(): AiSubtitleSettings {
        return AiSubtitleSettings(
            translationEngine = AiTranslationEngine.OLLAMA,
            ollamaBaseUrl = "http://translator.local/v1",
            ollamaModel = "qwen2.5:7b",
        )
    }

}
