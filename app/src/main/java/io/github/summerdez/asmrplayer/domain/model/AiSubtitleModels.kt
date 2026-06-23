package io.github.summerdez.asmrplayer.domain.model

data class SubtitleLine(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val sourceText: String,
    val translatedText: String = "",
)

data class SubtitleGenerationTarget(
    val playlistId: String,
    val trackId: String,
    val trackTitle: String,
    val audioUri: String,
    val contextTitle: String = "",
)

enum class AiSubtitleStage {
    TRANSCRIBING,
    TRANSLATING,
    BINDING,
    COMPLETED,
    PAUSED,
    FAILED,
    CANCELED,
}

data class AiSubtitleTaskState(
    val target: SubtitleGenerationTarget,
    val stage: AiSubtitleStage = AiSubtitleStage.TRANSCRIBING,
    val transcriptionTitle: String = "本机转写",
    val transcribeProgress: Float = 0f,
    val translateProgress: Float = 0f,
    val previewLines: List<SubtitleLine> = emptyList(),
    val subtitlePath: String = "",
    val error: String = "",
) {
    val overallProgress: Float
        get() = when (stage) {
            AiSubtitleStage.TRANSCRIBING -> (transcribeProgress * 0.6f).coerceIn(0f, 0.6f)
            AiSubtitleStage.TRANSLATING -> (0.6f + translateProgress * 0.35f).coerceIn(0.6f, 0.95f)
            AiSubtitleStage.BINDING -> 0.98f
            AiSubtitleStage.COMPLETED -> 1f
            AiSubtitleStage.PAUSED,
            AiSubtitleStage.FAILED,
            AiSubtitleStage.CANCELED,
            -> (transcribeProgress * 0.6f + translateProgress * 0.35f).coerceIn(0f, 0.95f)
        }
}
