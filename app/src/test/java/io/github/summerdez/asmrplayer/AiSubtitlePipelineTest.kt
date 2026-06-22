package io.github.summerdez.asmrplayer

import io.github.summerdez.asmrplayer.data.ai.AiSubtitleVtt
import io.github.summerdez.asmrplayer.data.ai.AiSubtitleSegmentCache
import io.github.summerdez.asmrplayer.data.ai.AiSubtitleTaskStateBus
import io.github.summerdez.asmrplayer.data.ai.AiSubtitleTranslationCache
import io.github.summerdez.asmrplayer.data.ai.OpenAiCompatibleTranslator
import io.github.summerdez.asmrplayer.data.ai.SceneContext
import io.github.summerdez.asmrplayer.data.ai.SceneContextBuilder
import io.github.summerdez.asmrplayer.data.ai.StreamingLinearResampler
import io.github.summerdez.asmrplayer.data.ai.TranslationBatch
import io.github.summerdez.asmrplayer.data.ai.TranslationRequestExecutor
import io.github.summerdez.asmrplayer.data.ai.TranslationRequest
import io.github.summerdez.asmrplayer.data.ai.TranslationResponse
import io.github.summerdez.asmrplayer.data.ai.VadMergeConfig
import io.github.summerdez.asmrplayer.data.ai.VadSpeechSegment
import io.github.summerdez.asmrplayer.data.ai.WhisperRecognitionConcurrency
import io.github.summerdez.asmrplayer.data.ai.buildTranslationBatch
import io.github.summerdez.asmrplayer.data.ai.mergeVadSpeechSegments
import io.github.summerdez.asmrplayer.data.ai.planWhisperRecognitionConcurrency
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleSettings
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleStage
import io.github.summerdez.asmrplayer.domain.model.AiTranslationEngine
import io.github.summerdez.asmrplayer.domain.model.SubtitleGenerationTarget
import io.github.summerdez.asmrplayer.domain.model.SubtitleLine
import io.github.summerdez.asmrplayer.domain.model.WhisperModelSpec
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
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
    fun completeTranslationContentAcceptsOrderedLinesOnly() {
        val source = listOf(
            SubtitleLine("1", 0L, 1_000L, "耳元に来て"),
            SubtitleLine("2", 1_000L, 2_000L, "深呼吸して"),
        )

        val parsed = OpenAiCompatibleTranslator.parseCompleteTranslationContent(
            content = """
                ```json
                {"lines":[{"id":"1","zh":"来到耳边"},{"id":"2","zh":"深呼吸"}]}
                ```
            """.trimIndent(),
            finishReason = "stop",
            sourceLines = source,
        )

        assertEquals(listOf("来到耳边", "深呼吸"), parsed.map { it.translatedText })
    }

    @Test
    fun completeTranslationContentRejectsFlatMapProtocol() {
        val source = listOf(SubtitleLine("1", 0L, 1_000L, "耳元に来て"))

        val error = assertThrows(
            IllegalArgumentException::class.java,
        ) {
            OpenAiCompatibleTranslator.parseCompleteTranslationContent(
                content = """{"1":"来到耳边"}""",
                finishReason = "stop",
                sourceLines = source,
            )
        }

        assertTrue(error.message.orEmpty().contains("lines"))
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
                content = """{"lines":[{"id":"1","zh":"来到耳边"}]}""",
                finishReason = "stop",
                sourceLines = source,
            )
        }

        assertTrue(error.message.orEmpty().contains("行数"))
    }

    @Test
    fun orderedTranslationContentRejectsWrongOrderDuplicateAndBlankText() {
        val source = listOf(
            SubtitleLine("1", 0L, 1_000L, "耳元に来て"),
            SubtitleLine("2", 1_000L, 2_000L, "深呼吸して"),
        )

        val wrongOrder = assertThrows(IllegalArgumentException::class.java) {
            OpenAiCompatibleTranslator.parseCompleteTranslationContent(
                content = """{"lines":[{"id":"2","zh":"深呼吸"},{"id":"1","zh":"来到耳边"}]}""",
                finishReason = "stop",
                sourceLines = source,
            )
        }
        val duplicate = assertThrows(IllegalArgumentException::class.java) {
            OpenAiCompatibleTranslator.parseCompleteTranslationContent(
                content = """{"lines":[{"id":"1","zh":"来到耳边"},{"id":"1","zh":"深呼吸"}]}""",
                finishReason = "stop",
                sourceLines = source,
            )
        }
        val blank = assertThrows(IllegalArgumentException::class.java) {
            OpenAiCompatibleTranslator.parseCompleteTranslationContent(
                content = """{"lines":[{"id":"1","zh":"来到耳边"},{"id":"2","zh":""}]}""",
                finishReason = "stop",
                sourceLines = source,
            )
        }

        assertTrue(wrongOrder.message.orEmpty().contains("错位"))
        assertTrue(duplicate.message.orEmpty().contains("重复"))
        assertTrue(blank.message.orEmpty().contains("为空"))
    }

    @Test
    fun orderedTranslationContentAcceptsChineseOnomatopoeia() {
        val source = listOf(SubtitleLine("1", 0L, 1_000L, "ん…"))

        val parsed = OpenAiCompatibleTranslator.parseCompleteTranslationContent(
            content = """{"lines":[{"id":"1","zh":"嗯…"}]}""",
            finishReason = "stop",
            sourceLines = source,
        )

        assertEquals("嗯…", parsed.single().translatedText)
    }

    @Test
    fun orderedTranslationContentAcceptsChineseAsmrSoundLabel() {
        val source = listOf(SubtitleLine("1", 0L, 1_000L, "れろ…"))

        val parsed = OpenAiCompatibleTranslator.parseCompleteTranslationContent(
            content = """{"lines":[{"id":"1","zh":"（舔舐声）"}]}""",
            finishReason = "stop",
            sourceLines = source,
        )

        assertEquals("（舔舐声）", parsed.single().translatedText)
    }

    @Test
    fun orderedTranslationContentRejectsJapaneseKanaInTranslation() {
        val source = listOf(SubtitleLine("1", 0L, 1_000L, "ん…"))

        val error = assertThrows(IllegalArgumentException::class.java) {
            OpenAiCompatibleTranslator.parseCompleteTranslationContent(
                content = """{"lines":[{"id":"1","zh":"ん…"}]}""",
                finishReason = "stop",
                sourceLines = source,
            )
        }

        assertTrue(error.message.orEmpty().contains("日文假名"))
    }

    @Test
    fun orderedTranslationContentAcceptsNumericIdAndDifferentFieldOrder() {
        val source = listOf(
            SubtitleLine("1", 0L, 1_000L, "かせちゃ"),
            SubtitleLine("2", 1_000L, 2_000L, "車が見えた"),
        )

        val parsed = OpenAiCompatibleTranslator.parseCompleteTranslationContent(
            content = """{"lines":[{"zh":"借给你","id":1},{"ja":"車が見えた","zh":"看见车了","id":"2"}]}""",
            finishReason = "stop",
            sourceLines = source,
        )

        assertEquals(listOf("借给你", "看见车了"), parsed.map { it.translatedText })
    }

    @Test
    fun translatorFallsBackToSingleLineWhenBatchCannotAlign() {
        val source = listOf(
            SubtitleLine("1", 0L, 1_000L, "耳元に来て"),
            SubtitleLine("2", 1_000L, 2_000L, "深呼吸して"),
        )
        var callCount = 0
        val translator = OpenAiCompatibleTranslator(
            maxAttempts = 1,
            retryDelayMillis = 0,
            requestExecutor = object : TranslationRequestExecutor {
                override suspend fun execute(
                    settings: AiSubtitleSettings,
                    request: TranslationRequest,
                ): TranslationResponse {
                    callCount += 1
                    return when (callCount) {
                        1 -> TranslationResponse("""{"lines":[{"id":"2","zh":"错位"}]}""", "stop")
                        2 -> TranslationResponse("""{"lines":[{"id":"1","zh":"来到耳边"}]}""", "stop")
                        else -> TranslationResponse("""{"lines":[{"id":"2","zh":"深呼吸"}]}""", "stop")
                    }
                }
            },
        )

        val translated = runBlocking {
            translator.translate(aiSettings(), TranslationBatch(lines = source, contextTitle = "playlist"))
        }

        assertEquals(listOf("来到耳边", "深呼吸"), translated.map { it.translatedText })
        assertEquals(3, callCount)
    }

    @Test
    fun translationPromptRequiresConservativeLineGroundedTranslation() {
        val source = listOf(SubtitleLine("1", 0L, 1_000L, "かせちゃ"))
        var userPrompt = ""
        val translator = OpenAiCompatibleTranslator(
            requestExecutor = object : TranslationRequestExecutor {
                override suspend fun execute(
                    settings: AiSubtitleSettings,
                    request: TranslationRequest,
                ): TranslationResponse {
                    userPrompt = request.messages.joinToString("\n") { it.content }
                    return TranslationResponse("""{"lines":[{"id":"1","zh":"（听不清）"}]}""", "stop")
                }
            },
        )

        runBlocking {
            translator.translate(aiSettings(), TranslationBatch(lines = source, contextTitle = "playlist"))
        }

        assertTrue(userPrompt.contains("逐行保守翻译"))
        assertTrue(userPrompt.contains("当前日文没有对应词"))
        assertTrue(userPrompt.contains("短碎片"))
        assertTrue(userPrompt.contains("不要保留日文"))
        assertTrue(userPrompt.contains("（听不清）"))
    }

    @Test
    fun translationPromptRequiresAsmrSoundEffectLabels() {
        val source = listOf(SubtitleLine("1", 0L, 1_000L, "れろれろ"))
        var userPrompt = ""
        val translator = OpenAiCompatibleTranslator(
            requestExecutor = object : TranslationRequestExecutor {
                override suspend fun execute(
                    settings: AiSubtitleSettings,
                    request: TranslationRequest,
                ): TranslationResponse {
                    userPrompt = request.messages.joinToString("\n") { it.content }
                    return TranslationResponse("""{"lines":[{"id":"1","zh":"（舔舐声）"}]}""", "stop")
                }
            },
        )

        runBlocking {
            translator.translate(aiSettings(), TranslationBatch(lines = source, contextTitle = "playlist"))
        }

        assertTrue(userPrompt.contains("ASMR 音效翻译"))
        assertTrue(userPrompt.contains("不要硬译拟声词字面"))
        assertTrue(userPrompt.contains("（舔舐声）"))
        assertTrue(userPrompt.contains("（摩擦声）"))
    }

    @Test
    fun translationPromptCanEnableAdultContentLiteralTranslation() {
        val source = listOf(SubtitleLine("1", 0L, 1_000L, "耳元で言うね"))
        var userPrompt = ""
        val translator = OpenAiCompatibleTranslator(
            requestExecutor = object : TranslationRequestExecutor {
                override suspend fun execute(
                    settings: AiSubtitleSettings,
                    request: TranslationRequest,
                ): TranslationResponse {
                    userPrompt = request.messages.joinToString("\n") { it.content }
                    return TranslationResponse("""{"lines":[{"id":"1","zh":"在耳边说哦"}]}""", "stop")
                }
            },
        )

        runBlocking {
            translator.translate(
                aiSettings().copy(allowAdultContentTranslation = true),
                TranslationBatch(lines = source, contextTitle = "playlist"),
            )
        }

        assertTrue(userPrompt.contains("成人内容直译已开启"))
        assertTrue(userPrompt.contains("按原文直译"))
        assertTrue(userPrompt.contains("不得添加当前 ja 没有的成人内容"))
    }

    @Test
    fun translatorReportsLineIdWhenSingleLineFallbackFails() {
        val source = listOf(SubtitleLine("7", 0L, 1_000L, "耳元に来て"))
        val translator = OpenAiCompatibleTranslator(
            maxAttempts = 1,
            retryDelayMillis = 0,
            requestExecutor = object : TranslationRequestExecutor {
                override suspend fun execute(
                    settings: AiSubtitleSettings,
                    request: TranslationRequest,
                ): TranslationResponse {
                    return TranslationResponse("""{"lines":[{"id":"7","zh":""}]}""", "stop")
                }
            },
        )

        val error = assertThrows(IOException::class.java) {
            runBlocking {
                translator.translate(aiSettings(), TranslationBatch(lines = source, contextTitle = "playlist"))
            }
        }

        assertTrue(error.message.orEmpty().contains("第 7 句"))
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
        val sceneContext = SceneContext(
            scene = "床边轻声哄睡",
            speaker = "姐姐",
            listenerAddress = "你",
            tone = "轻柔",
            glossary = mapOf("耳かき" to "掏耳朵"),
            rawSummary = "睡前陪伴",
        )
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

        cache.save(target, "base", settings, source, translated, sceneContext)

        assertEquals(translated, cache.load(target, "base", settings, source, sceneContext))
        assertNull(cache.load(target.copy(audioUri = "content://audio/2"), "base", settings, source, sceneContext))
        assertNull(cache.load(target, "small", settings, source, sceneContext))
        assertNull(
            cache.load(
                target,
                "base",
                settings.copy(ollamaModel = "qwen2.5:14b"),
                source,
                sceneContext,
            ),
        )
        assertNull(cache.load(target, "base", settings, source.map { it.copy(sourceText = "${it.sourceText}!") }, sceneContext))
        assertNull(cache.load(target, "base", settings, source, sceneContext.copy(tone = "冷淡")))
        assertNull(cache.load(target, "base", settings.copy(allowAdultContentTranslation = true), source, sceneContext))
    }

    @Test
    fun sceneContextParserAndCacheUseStableMetadata() {
        val sceneContext = OpenAiCompatibleTranslator.parseSceneContextContent(
            content = """
                ```json
                {"scene":"床边哄睡","speaker":"姐姐","listenerAddress":"你","tone":"轻柔耳语","glossary":{"耳かき":"掏耳朵"},"summary":"睡前陪伴"}
                ```
            """.trimIndent(),
            finishReason = "stop",
        )
        val cacheDir = Files.createTempDirectory("ai-subtitle-scenes").toFile()
        val builder = SceneContextBuilder(cacheDir)
        val target = SubtitleGenerationTarget(
            playlistId = "playlist-1",
            trackId = "track-1",
            trackTitle = "track",
            audioUri = "content://audio/1",
            contextTitle = "playlist",
        )
        val settings = aiSettings()
        val source = listOf(SubtitleLine("1", 0L, 1_000L, "耳元に来て"))

        builder.save(target, settings, source, sceneContext)

        assertEquals("床边哄睡", sceneContext.scene)
        assertEquals("掏耳朵", sceneContext.glossary["耳かき"])
        assertEquals(sceneContext, builder.load(target, settings, source))
        assertNull(builder.load(target.copy(trackTitle = "other"), settings, source))
        assertNull(builder.load(target, settings, source.map { it.copy(sourceText = "深呼吸して") }))

        builder.clear(target.trackId)

        assertNull(builder.load(target, settings, source))
    }

    @Test
    fun sceneContextBuilderFallsBackToEmptyContextWhenRequestFails() {
        val cacheDir = Files.createTempDirectory("ai-subtitle-scenes-fail").toFile()
        val builder = SceneContextBuilder(
            cacheDir,
            OpenAiCompatibleTranslator(
                requestExecutor = object : TranslationRequestExecutor {
                    override suspend fun execute(
                        settings: AiSubtitleSettings,
                        request: TranslationRequest,
                    ): TranslationResponse {
                        throw IOException("network down")
                    }
                },
            ),
        )
        val context = runBlocking {
            builder.loadOrBuild(
                settings = aiSettings(),
                target = SubtitleGenerationTarget(
                    playlistId = "playlist-1",
                    trackId = "track-1",
                    trackTitle = "track",
                    audioUri = "content://audio/1",
                    contextTitle = "playlist",
                ),
                sourceLines = listOf(SubtitleLine("1", 0L, 1_000L, "耳元に来て")),
            )
        }

        assertEquals(SceneContext(), context)
    }

    @Test
    fun translationBatchIncludesReadOnlyPreviousAndNextContext() {
        val source = (1..8).map { index ->
            SubtitleLine(index.toString(), index * 1_000L, index * 1_000L + 500L, "原文$index")
        }
        val translatedById = mapOf(
            "2" to source[1].copy(translatedText = "译文2"),
            "3" to source[2].copy(translatedText = "译文3"),
        )

        val batch = buildTranslationBatch(
            sourceLines = source,
            batchLines = source.subList(3, 5),
            batchIndex = 1,
            totalBatches = 3,
            translatedById = translatedById,
            contextTitle = "playlist",
            sceneContext = SceneContext(scene = "床边"),
            contextWindowSize = 2,
        )

        assertEquals(listOf("4", "5"), batch.lines.map { it.id })
        assertEquals(listOf("2", "3"), batch.previousContext.map { it.id })
        assertEquals(listOf("译文2", "译文3"), batch.previousContext.map { it.zh })
        assertEquals(listOf("6", "7"), batch.nextContext.map { it.id })
        assertTrue(batch.nextContext.all { it.zh.isBlank() })
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
    fun deepSeekDefaultModelUsesFlash() {
        assertEquals("deepseek-v4-flash", AiTranslationEngine.DEEPSEEK.defaultModel)
    }

    @Test
    fun aiSubtitleTaskProgressDoesNotRegressWhenPreviewArrives() {
        val target = SubtitleGenerationTarget(
            playlistId = "playlist-progress",
            trackId = "track-progress",
            trackTitle = "track",
            audioUri = "content://audio/progress",
        )

        AiSubtitleTaskStateBus.publish(target, AiSubtitleStage.TRANSCRIBING)
        AiSubtitleTaskStateBus.publishTranscribing(
            target = target,
            progress = 0.42f,
            preview = listOf(SubtitleLine("1", 0L, 1_000L, "進んだ")),
        )
        AiSubtitleTaskStateBus.publishTranscribing(
            target = target,
            progress = 0.02f,
            preview = listOf(SubtitleLine("2", 1_000L, 2_000L, "プレビュー")),
        )

        val task = AiSubtitleTaskStateBus.taskFor(target.trackId)
        assertEquals(0.42f, task?.transcribeProgress ?: 0f, 0.0001f)
        assertEquals("プレビュー", task?.previewLines?.last()?.sourceText)
        AiSubtitleTaskStateBus.remove(target.trackId)
    }

    @Test
    fun resamplerKeepsBoundarySampleAcrossUnevenChunks() {
        val resampler = StreamingLinearResampler(inputRate = 48_000, outputRate = 16_000)

        val first = resampler.process(FloatArray(623) { it / 623f })
        val second = resampler.process(FloatArray(623) { it / 623f })

        assertTrue(first.isNotEmpty())
        assertTrue(second.isNotEmpty())
    }

    @Test
    fun vadMergeCombinesSegmentsWhenGapIsWithinThreshold() {
        val first = vadSegment(startMs = 0L, endMs = 1_000L, sampleCount = 16_000, sampleValue = 1f)
        val second = vadSegment(startMs = 1_500L, endMs = 2_500L, sampleCount = 16_000, sampleValue = 2f)

        val merged = mergeVadSpeechSegments(listOf(first, second))

        assertEquals(1, merged.size)
        assertEquals(0L, merged.single().startMs)
        assertEquals(2_500L, merged.single().endMs)
        assertEquals(32_000, merged.single().samples.size)
        assertEquals(1f, merged.single().samples.first(), 0.0001f)
        assertEquals(2f, merged.single().samples[16_000], 0.0001f)
    }

    @Test
    fun vadMergeKeepsSegmentsWhenGapExceedsThreshold() {
        val first = vadSegment(startMs = 0L, endMs = 1_000L, sampleCount = 16_000, sampleValue = 1f)
        val second = vadSegment(startMs = 1_601L, endMs = 2_601L, sampleCount = 16_000, sampleValue = 2f)

        val merged = mergeVadSpeechSegments(
            listOf(first, second),
            VadMergeConfig(maxGapMs = 600L),
        )

        assertEquals(2, merged.size)
        assertEquals(listOf(0L, 1_601L), merged.map { it.startMs })
    }

    @Test
    fun vadMergeRespectsMaxSpeechDuration() {
        val first = vadSegment(startMs = 0L, endMs = 1_000L, sampleCount = 16_000, sampleValue = 1f)
        val second = vadSegment(startMs = 1_300L, endMs = 2_300L, sampleCount = 16_000, sampleValue = 2f)

        val merged = mergeVadSpeechSegments(
            listOf(first, second),
            VadMergeConfig(maxGapMs = 600L, maxSpeechDurationMs = 1_500L),
        )

        assertEquals(2, merged.size)
        assertEquals(listOf(1_000L, 2_300L), merged.map { it.endMs })
    }

    @Test
    fun vadMergeLeavesSingleSegmentUnchanged() {
        val segment = vadSegment(startMs = 120L, endMs = 880L, sampleCount = 12_160, sampleValue = 1f)

        val merged = mergeVadSpeechSegments(listOf(segment))

        assertEquals(1, merged.size)
        assertEquals(120L, merged.single().startMs)
        assertEquals(880L, merged.single().endMs)
        assertEquals(12_160, merged.single().samples.size)
    }

    @Test
    fun vadMergeChainsNearbySegmentsAndKeepsRealTimelineEnd() {
        val first = vadSegment(startMs = 0L, endMs = 500L, sampleCount = 8_000, sampleValue = 1f)
        val second = vadSegment(startMs = 800L, endMs = 1_300L, sampleCount = 8_000, sampleValue = 2f)
        val third = vadSegment(startMs = 1_600L, endMs = 2_100L, sampleCount = 8_000, sampleValue = 3f)

        val merged = mergeVadSpeechSegments(listOf(first, second, third))

        assertEquals(1, merged.size)
        assertEquals(0L, merged.single().startMs)
        assertEquals(2_100L, merged.single().endMs)
        assertEquals(24_000, merged.single().samples.size)
        assertEquals(1f, merged.single().samples[0], 0.0001f)
        assertEquals(2f, merged.single().samples[8_000], 0.0001f)
        assertEquals(3f, merged.single().samples[16_000], 0.0001f)
    }

    @Test
    fun whisperConcurrencyLetsBaseUseMoreWorkersOnLargeDevices() {
        val plan = planWhisperRecognitionConcurrency(
            modelSpec = WhisperModelSpec.BASE,
            availMemBytes = 3L * GIB,
            cores = 8,
            segmentCount = 12,
        )

        assertEquals(WhisperRecognitionConcurrency(workerCount = 4, threadsPerWorker = 2), plan)
    }

    @Test
    fun whisperConcurrencyKeepsSmallToTwoWorkersWithEnoughMemory() {
        val plan = planWhisperRecognitionConcurrency(
            modelSpec = WhisperModelSpec.SMALL,
            availMemBytes = 3L * GIB,
            cores = 8,
            segmentCount = 12,
        )

        assertEquals(WhisperRecognitionConcurrency(workerCount = 2, threadsPerWorker = 4), plan)
    }

    @Test
    fun whisperConcurrencyFallsBackToOneWorkerOnLowMemory() {
        val basePlan = planWhisperRecognitionConcurrency(
            modelSpec = WhisperModelSpec.BASE,
            availMemBytes = 256L * MIB,
            cores = 8,
            segmentCount = 8,
        )
        val smallPlan = planWhisperRecognitionConcurrency(
            modelSpec = WhisperModelSpec.SMALL,
            availMemBytes = 256L * MIB,
            cores = 8,
            segmentCount = 8,
        )

        assertEquals(WhisperRecognitionConcurrency(workerCount = 1, threadsPerWorker = 4), basePlan)
        assertEquals(WhisperRecognitionConcurrency(workerCount = 1, threadsPerWorker = 4), smallPlan)
    }

    @Test
    fun whisperConcurrencyRespectsCoreAndSegmentLimits() {
        val noSegments = planWhisperRecognitionConcurrency(
            modelSpec = WhisperModelSpec.BASE,
            availMemBytes = 3L * GIB,
            cores = 8,
            segmentCount = 0,
        )
        val oneSegment = planWhisperRecognitionConcurrency(
            modelSpec = WhisperModelSpec.SMALL,
            availMemBytes = 3L * GIB,
            cores = 8,
            segmentCount = 1,
        )
        val sixCoreBase = planWhisperRecognitionConcurrency(
            modelSpec = WhisperModelSpec.BASE,
            availMemBytes = 3L * GIB,
            cores = 6,
            segmentCount = 12,
        )

        assertEquals(WhisperRecognitionConcurrency(workerCount = 1, threadsPerWorker = 4), noSegments)
        assertEquals(WhisperRecognitionConcurrency(workerCount = 1, threadsPerWorker = 4), oneSegment)
        assertEquals(WhisperRecognitionConcurrency(workerCount = 3, threadsPerWorker = 2), sixCoreBase)
        assertTrue(sixCoreBase.workerCount * sixCoreBase.threadsPerWorker <= 6)
    }

    @Test
    fun whisperConcurrencyUsesMeasuredMemoryBudgetForPlgClassDevice() {
        val basePlan = planWhisperRecognitionConcurrency(
            modelSpec = WhisperModelSpec.BASE,
            availMemBytes = 1_920L * MIB,
            cores = 8,
            segmentCount = 12,
        )
        val smallPlan = planWhisperRecognitionConcurrency(
            modelSpec = WhisperModelSpec.SMALL,
            availMemBytes = 1_920L * MIB,
            cores = 8,
            segmentCount = 12,
        )

        assertEquals(WhisperRecognitionConcurrency(workerCount = 3, threadsPerWorker = 2), basePlan)
        assertEquals(WhisperRecognitionConcurrency(workerCount = 2, threadsPerWorker = 4), smallPlan)
    }

    private fun aiSettings(): AiSubtitleSettings {
        return AiSubtitleSettings(
            translationEngine = AiTranslationEngine.OLLAMA,
            ollamaBaseUrl = "http://translator.local/v1",
            ollamaModel = "qwen2.5:7b",
        )
    }

    private fun vadSegment(
        startMs: Long,
        endMs: Long,
        sampleCount: Int,
        sampleValue: Float,
    ): VadSpeechSegment {
        return VadSpeechSegment(
            startMs = startMs,
            endMs = endMs,
            samples = FloatArray(sampleCount) { sampleValue },
        )
    }

    private companion object {
        const val MIB = 1024L * 1024L
        const val GIB = 1024L * MIB
    }
}
