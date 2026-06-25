package io.github.summerdez.asmrplayer.data.ai

import io.github.summerdez.asmrplayer.domain.model.AiSubtitleSettings
import io.github.summerdez.asmrplayer.domain.model.AiTranscriptionBackend
import io.github.summerdez.asmrplayer.domain.model.SubtitleLine
import kotlin.math.max
import kotlin.math.min

internal fun aiSubtitleTranscriptionTitle(settings: AiSubtitleSettings): String {
    return when (settings.transcriptionBackend) {
        AiTranscriptionBackend.LOCAL -> "本机转写"
        AiTranscriptionBackend.REMOTE -> "远程转写"
    }
}

internal fun completedBatchProgress(
    sourceLines: List<SubtitleLine>,
    translatedById: Map<String, SubtitleLine>,
    batches: List<List<SubtitleLine>>,
): Float {
    if (sourceLines.isEmpty() || batches.isEmpty()) {
        return 0f
    }
    val completedBatches = batches.count { batch ->
        batch.all { translatedById[it.id]?.translatedText?.isNotBlank() == true }
    }
    return (completedBatches.toFloat() / batches.size).coerceIn(0f, 1f)
}

internal fun aiSubtitleQualityWarning(translatedLines: List<SubtitleLine>) =
    OpenAiCompatibleTranslator.translationQualityWarning(translatedLines)

internal fun buildTranslationBatch(
    sourceLines: List<SubtitleLine>,
    batchLines: List<SubtitleLine>,
    batchIndex: Int,
    totalBatches: Int,
    translatedById: Map<String, SubtitleLine>,
    contextTitle: String,
    sceneContext: SceneContext,
    contextWindowSize: Int,
): TranslationBatch {
    val batchStart = sourceLines.indexOfFirst { it.id == batchLines.firstOrNull()?.id }
        .takeIf { it >= 0 }
        ?: 0
    val batchEndExclusive = (batchStart + batchLines.size).coerceAtMost(sourceLines.size)
    val previousStart = max(0, batchStart - contextWindowSize)
    val nextEnd = min(sourceLines.size, batchEndExclusive + contextWindowSize)
    val previousContext = sourceLines
        .subList(previousStart, batchStart)
        .map { line ->
            TranslationContextLine(
                id = line.id,
                ja = line.sourceText,
                zh = translatedById[line.id]?.translatedText.orEmpty(),
            )
        }
    val nextContext = sourceLines
        .subList(batchEndExclusive, nextEnd)
        .map { line -> TranslationContextLine(id = line.id, ja = line.sourceText) }
    val globalSourceContext = sourceLines
        .map { line -> TranslationContextLine(id = line.id, ja = line.sourceText) }
    return TranslationBatch(
        lines = batchLines,
        contextTitle = contextTitle,
        sceneContext = sceneContext,
        globalSourceContext = globalSourceContext,
        previousContext = previousContext,
        nextContext = nextContext,
        batchIndex = batchIndex,
        totalBatches = totalBatches,
    )
}
