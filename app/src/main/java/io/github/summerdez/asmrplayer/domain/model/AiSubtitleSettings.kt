package io.github.summerdez.asmrplayer.domain.model

enum class AiTranslationEngine(
    val label: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val requiresApiKey: Boolean,
) {
    OLLAMA(
        label = "本地 Ollama",
        defaultBaseUrl = "http://127.0.0.1:11434/v1",
        defaultModel = "qwen2.5:7b",
        requiresApiKey = false,
    ),
    DEEPSEEK(
        label = "云端 DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com",
        defaultModel = "deepseek-v4-flash",
        requiresApiKey = true,
    ),
}

enum class AiAsrBackendPreference(
    val label: String,
) {
    AUTO("自动"),
    CPU("CPU"),
    GPU_EXPERIMENTAL("GPU（实验）"),
}

data class AiSubtitleSettings(
    val translationEngine: AiTranslationEngine = AiTranslationEngine.OLLAMA,
    val ollamaBaseUrl: String = AiTranslationEngine.OLLAMA.defaultBaseUrl,
    val ollamaModel: String = AiTranslationEngine.OLLAMA.defaultModel,
    val deepSeekBaseUrl: String = AiTranslationEngine.DEEPSEEK.defaultBaseUrl,
    val deepSeekModel: String = AiTranslationEngine.DEEPSEEK.defaultModel,
    val deepSeekApiKey: String = "",
    val whisperModelId: String = WhisperModelSpec.DEFAULT_ID,
    val asrBackendPreference: AiAsrBackendPreference = AiAsrBackendPreference.AUTO,
    val gpuWhisperModelId: String = GpuWhisperModelSpec.DEFAULT_ID,
) {
    val activeBaseUrl: String
        get() = when (translationEngine) {
            AiTranslationEngine.OLLAMA -> ollamaBaseUrl
            AiTranslationEngine.DEEPSEEK -> deepSeekBaseUrl
        }

    val activeModel: String
        get() = when (translationEngine) {
            AiTranslationEngine.OLLAMA -> ollamaModel
            AiTranslationEngine.DEEPSEEK -> deepSeekModel
        }

    val activeApiKey: String
        get() = when (translationEngine) {
            AiTranslationEngine.OLLAMA -> ""
            AiTranslationEngine.DEEPSEEK -> deepSeekApiKey
        }
}

data class WhisperModelSpec(
    val id: String,
    val label: String,
    val directoryName: String,
    val encoderFileName: String,
    val decoderFileName: String,
    val tokensFileName: String,
    val files: List<WhisperModelFileSpec>,
) {
    val sizeBytes: Long
        get() = files.sumOf { it.sizeBytes }

    companion object {
        const val DEFAULT_ID = "base"
        private const val SHERPA_ASR_MODELS =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models"
        private const val SHERPA_WHISPER_BASE =
            "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/main"
        private const val SHERPA_WHISPER_SMALL =
            "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-small/resolve/main"

        val SILERO_VAD = WhisperModelFileSpec(
            fileName = "silero_vad.onnx",
            downloadUrl = "$SHERPA_ASR_MODELS/silero_vad.onnx",
            sizeBytes = 643_854L,
        )

        val BASE = WhisperModelSpec(
            id = DEFAULT_ID,
            label = "Whisper base（默认）",
            directoryName = "sherpa-onnx-whisper-base",
            encoderFileName = "base-encoder.int8.onnx",
            decoderFileName = "base-decoder.int8.onnx",
            tokensFileName = "base-tokens.txt",
            files = listOf(
                WhisperModelFileSpec(
                    fileName = "base-encoder.int8.onnx",
                    downloadUrl = "$SHERPA_WHISPER_BASE/base-encoder.int8.onnx",
                    sizeBytes = 29_120_534L,
                ),
                WhisperModelFileSpec(
                    fileName = "base-decoder.int8.onnx",
                    downloadUrl = "$SHERPA_WHISPER_BASE/base-decoder.int8.onnx",
                    sizeBytes = 130_672_026L,
                ),
                WhisperModelFileSpec(
                    fileName = "base-tokens.txt",
                    downloadUrl = "$SHERPA_WHISPER_BASE/base-tokens.txt",
                    sizeBytes = 816_730L,
                ),
                SILERO_VAD,
            ),
        )

        val SMALL = WhisperModelSpec(
            id = "small",
            label = "Whisper small（可选）",
            directoryName = "sherpa-onnx-whisper-small",
            encoderFileName = "small-encoder.int8.onnx",
            decoderFileName = "small-decoder.int8.onnx",
            tokensFileName = "small-tokens.txt",
            files = listOf(
                WhisperModelFileSpec(
                    fileName = "small-encoder.int8.onnx",
                    downloadUrl = "$SHERPA_WHISPER_SMALL/small-encoder.int8.onnx",
                    sizeBytes = 112_442_483L,
                ),
                WhisperModelFileSpec(
                    fileName = "small-decoder.int8.onnx",
                    downloadUrl = "$SHERPA_WHISPER_SMALL/small-decoder.int8.onnx",
                    sizeBytes = 262_226_114L,
                ),
                WhisperModelFileSpec(
                    fileName = "small-tokens.txt",
                    downloadUrl = "$SHERPA_WHISPER_SMALL/small-tokens.txt",
                    sizeBytes = 816_730L,
                ),
                SILERO_VAD,
            ),
        )

        val ALL: List<WhisperModelSpec> = listOf(BASE, SMALL)

        fun byId(id: String?): WhisperModelSpec {
            return ALL.firstOrNull { it.id == id } ?: BASE
        }
    }
}

data class WhisperModelFileSpec(
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
)

data class GpuWhisperModelSpec(
    val id: String,
    val label: String,
    val directoryName: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val experimental: Boolean = false,
) {
    companion object {
        const val DEFAULT_ID = "base-q5_1"
        private const val WHISPER_CPP_MODELS =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main"

        val BASE = GpuWhisperModelSpec(
            id = DEFAULT_ID,
            label = "Whisper base Q5_1（GPU）",
            directoryName = "whisper-cpp-base-q5_1",
            fileName = "ggml-base-q5_1.bin",
            downloadUrl = "$WHISPER_CPP_MODELS/ggml-base-q5_1.bin",
            sizeBytes = 59_700_000L,
        )

        val SMALL = GpuWhisperModelSpec(
            id = "small-q5_1",
            label = "Whisper small Q5_1（GPU 实验）",
            directoryName = "whisper-cpp-small-q5_1",
            fileName = "ggml-small-q5_1.bin",
            downloadUrl = "$WHISPER_CPP_MODELS/ggml-small-q5_1.bin",
            sizeBytes = 163_000_000L,
            experimental = true,
        )

        val ALL: List<GpuWhisperModelSpec> = listOf(BASE, SMALL)

        fun byId(id: String?): GpuWhisperModelSpec {
            return ALL.firstOrNull { it.id == id } ?: BASE
        }
    }
}
