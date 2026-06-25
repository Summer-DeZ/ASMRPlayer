package io.github.summerdez.asmrplayer.data.ai

import android.content.Context
import io.github.summerdez.asmrplayer.domain.model.SubtitleGenerationTarget
import java.io.File
import java.util.Locale

internal fun Context.writeAiSubtitleFile(
    target: SubtitleGenerationTarget,
    suffix: String,
    body: String,
): File {
    val dir = File(filesDir, "ai-subtitles/generated").apply { mkdirs() }
    val file = File(dir, generatedSubtitleFileName(target, suffix))
    file.writeText(body, Charsets.UTF_8)
    return file
}

internal fun generatedSubtitleFileName(target: SubtitleGenerationTarget, suffix: String): String {
    val safeTrackId = subtitleFileSegment(target.trackId)
        .ifBlank { "track" }
        .take(64)
    val safeTitle = subtitleFileSegment(target.trackTitle)
        .ifBlank { "audio" }
        .take(48)
    val safeSuffix = subtitleFileSegment(suffix)
        .ifBlank { "subtitle" }
        .take(16)
    return "$safeTrackId-$safeTitle-$safeSuffix.vtt"
}

private fun subtitleFileSegment(value: String): String {
    return value
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9._-]+"), "-")
        .trim('-', '.', '_')
}
