package io.github.summerdez.asmrplayer.playback

import android.content.ContentResolver
import android.net.Uri
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

object SubtitleParser {
    private val LRC_TIME: Pattern =
        Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val SRT_TIME: Pattern =
        Pattern.compile("(.+?)\\s*-->\\s*(.+)")

    @JvmStatic
    @Throws(IOException::class)
    fun parse(resolver: ContentResolver?, uri: Uri?): List<SubtitleCue> {
        val lines = readLines(resolver, uri)
        var cues = parseSrt(lines)
        if (cues.isNotEmpty()) {
            return cues
        }

        cues = parseLrc(lines)
        if (cues.isNotEmpty()) {
            return cues
        }

        val plainText = StringBuilder()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) {
                if (plainText.isNotEmpty()) {
                    plainText.append('\n')
                }
                plainText.append(trimmed)
            }
        }
        if (plainText.isEmpty()) {
            return emptyList()
        }
        val fallback = ArrayList<SubtitleCue>()
        fallback.add(SubtitleCue(0L, Long.MAX_VALUE, plainText.toString()))
        return fallback
    }

    @JvmStatic
    fun textAt(cues: List<SubtitleCue>?, positionMs: Long): String? {
        val index = indexAt(cues, positionMs)
        return if (index >= 0) cues!![index].text else ""
    }

    @JvmStatic
    fun indexAt(cues: List<SubtitleCue>?, positionMs: Long): Int {
        if (cues == null || cues.isEmpty()) {
            return -1
        }

        var low = 0
        var high = cues.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val cue = cues[mid]
            if (positionMs < cue.startMs) {
                high = mid - 1
            } else if (positionMs > cue.endMs) {
                low = mid + 1
            } else {
                return mid
            }
        }
        return if (high >= 0) high else -1
    }

    @JvmStatic
    fun nextCueStartAfter(cues: List<SubtitleCue>?, positionMs: Long): Long {
        if (cues == null || cues.isEmpty()) {
            return -1L
        }

        var low = 0
        var high = cues.size - 1
        var nextStartMs = -1L
        while (low <= high) {
            val mid = (low + high) ushr 1
            val startMs = cues[mid].startMs
            if (startMs > positionMs) {
                nextStartMs = startMs
                high = mid - 1
            } else {
                low = mid + 1
            }
        }
        return nextStartMs
    }

    @Throws(IOException::class)
    private fun readLines(resolver: ContentResolver?, uri: Uri?): List<String> {
        val lines = ArrayList<String>()
        resolver!!.openInputStream(uri!!).use { stream ->
            if (stream == null) {
                return lines
            }
            BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    lines.add(line)
                    line = reader.readLine()
                }
            }
        }
        return lines
    }

    private fun parseLrc(lines: List<String>): List<SubtitleCue> {
        val cues = ArrayList<SubtitleCue>()
        for (line in lines) {
            val matcher = LRC_TIME.matcher(line)
            val times = ArrayList<Long>()
            var lastMatchEnd = -1
            while (matcher.find()) {
                times.add(parseLrcTime(matcher))
                lastMatchEnd = matcher.end()
            }
            if (times.isEmpty() || lastMatchEnd < 0) {
                continue
            }

            val text = line.substring(lastMatchEnd).trim()
            if (text.isEmpty()) {
                continue
            }
            for (time in times) {
                cues.add(SubtitleCue(time, time + 5000L, text))
            }
        }

        cues.sortWith(compareBy { it.startMs })
        val normalized = ArrayList<SubtitleCue>()
        for (i in cues.indices) {
            val cue = cues[i]
            val end = if (i + 1 < cues.size) {
                Math.max(cue.startMs, cues[i + 1].startMs - 1L)
            } else {
                cue.endMs
            }
            normalized.add(SubtitleCue(cue.startMs, end, cue.text))
        }
        return normalized
    }

    private fun parseSrt(lines: List<String>): List<SubtitleCue> {
        val cues = ArrayList<SubtitleCue>()
        var index = 0
        while (index < lines.size) {
            var line = lines[index].trim()
            if (line.isEmpty()) {
                index++
                continue
            }

            if (isInteger(line) && index + 1 < lines.size) {
                index++
                line = lines[index].trim()
            }

            val matcher = SRT_TIME.matcher(line)
            if (!matcher.matches()) {
                index++
                continue
            }

            val start = parseSrtTime(matcher.group(1)!!.trim())
            val end = parseSrtTime(matcher.group(2)!!.trim())
            index++

            val text = StringBuilder()
            while (index < lines.size) {
                val textLine = lines[index].trim()
                if (textLine.isEmpty()) {
                    break
                }
                if (text.isNotEmpty()) {
                    text.append('\n')
                }
                text.append(textLine)
                index++
            }

            if (start >= 0L && end >= start && text.isNotEmpty()) {
                cues.add(SubtitleCue(start, end, text.toString()))
            }
            index++
        }
        cues.sortWith(compareBy { it.startMs })
        return cues
    }

    private fun parseLrcTime(matcher: Matcher): Long {
        val minutes = matcher.group(1)!!.toLong()
        val seconds = matcher.group(2)!!.toLong()
        val fraction = matcher.group(3)
        var millis = 0L
        if (fraction != null && fraction.isNotEmpty()) {
            millis = if (fraction.length == 1) {
                fraction.toLong() * 100L
            } else if (fraction.length == 2) {
                fraction.toLong() * 10L
            } else {
                fraction.substring(0, 3).toLong()
            }
        }
        return minutes * 60_000L + seconds * 1000L + millis
    }

    private fun parseSrtTime(value: String): Long {
        val cleaned = value.replace(',', '.')
        val pieces = cleaned.split(":")
        if (pieces.size < 2 || pieces.size > 3) {
            return -1L
        }

        var hours = 0L
        val minutes: Long
        val secondsPart: String
        if (pieces.size == 3) {
            hours = parseLongSafe(pieces[0])
            minutes = parseLongSafe(pieces[1])
            secondsPart = pieces[2]
        } else {
            minutes = parseLongSafe(pieces[0])
            secondsPart = pieces[1]
        }

        val secondsPieces = secondsPart.split(".", limit = 2)
        val seconds = parseLongSafe(secondsPieces[0])
        var millis = 0L
        if (secondsPieces.size > 1) {
            val fraction = secondsPieces[1]
            millis = if (fraction.length == 1) {
                parseLongSafe(fraction) * 100L
            } else if (fraction.length == 2) {
                parseLongSafe(fraction) * 10L
            } else {
                parseLongSafe(fraction.substring(0, 3))
            }
        }

        if (hours < 0L || minutes < 0L || seconds < 0L || millis < 0L) {
            return -1L
        }
        return hours * 3_600_000L + minutes * 60_000L + seconds * 1000L + millis
    }

    private fun isInteger(value: String): Boolean {
        for (i in value.indices) {
            if (!Character.isDigit(value[i])) {
                return false
            }
        }
        return value.isNotEmpty()
    }

    private fun parseLongSafe(value: String): Long {
        return try {
            value.trim().lowercase(Locale.US).toLong()
        } catch (error: NumberFormatException) {
            -1L
        }
    }
}
