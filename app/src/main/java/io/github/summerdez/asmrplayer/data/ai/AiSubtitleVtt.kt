package io.github.summerdez.asmrplayer.data.ai

import io.github.summerdez.asmrplayer.domain.model.SubtitleLine

object AiSubtitleVtt {
    fun mono(lines: List<SubtitleLine>): String {
        return buildString {
            appendLine("WEBVTT")
            appendLine()
            lines.forEachIndexed { index, line ->
                appendLine(index + 1)
                appendLine("${timestamp(line.startMs)} --> ${timestamp(line.endMs)}")
                appendLine(line.sourceText.trim())
                appendLine()
            }
        }
    }

    fun translated(lines: List<SubtitleLine>): String {
        return buildString {
            appendLine("WEBVTT")
            appendLine()
            lines.forEachIndexed { index, line ->
                appendLine(index + 1)
                appendLine("${timestamp(line.startMs)} --> ${timestamp(line.endMs)}")
                appendLine(line.translatedText.trim())
                appendLine()
            }
        }
    }

    fun timestamp(ms: Long): String {
        val safeMs = ms.coerceAtLeast(0L)
        val hours = safeMs / 3_600_000L
        val minutes = (safeMs % 3_600_000L) / 60_000L
        val seconds = (safeMs % 60_000L) / 1_000L
        val millis = safeMs % 1_000L
        return "%02d:%02d:%02d.%03d".format(hours, minutes, seconds, millis)
    }
}
