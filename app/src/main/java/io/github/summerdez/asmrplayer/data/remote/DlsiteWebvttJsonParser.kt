package io.github.summerdez.asmrplayer.data.remote

import java.util.Locale
import java.util.regex.Pattern

object DlsiteWebvttJsonParser {
    private val WHITESPACE_PATTERN: Pattern = Pattern.compile("\\s+")
    private val COLON_PATTERN: Pattern = Pattern.compile(":")

    @Throws(DlsiteJsonParser.IOExceptionLikeJsonException::class)
    fun parseWebvttJson(json: String?): String {
        val trimmed = if (json == null) "" else javaTrim(json)
        if (trimmed.startsWith("WEBVTT")) {
            return if (trimmed.endsWith("\n")) trimmed else "$trimmed\n"
        }

        try {
            val root = DlsiteJsonSupport.parse(json)
            val cues = webvttCuesFromRoot(root)
            val numbersAreMilliseconds = numericCueTimesLookLikeMilliseconds(cues)
            val builder = StringBuilder("WEBVTT\n\n")
            var written = 0
            for (value in cues) {
                val cue = DlsiteJsonSupport.asObjectOrNull(value) ?: continue
                val start = cueTime(cue, startTimeKeys(), numbersAreMilliseconds)
                val end = cueTime(cue, endTimeKeys(), numbersAreMilliseconds)
                val text = cueText(cue)
                if (start.isEmpty() || end.isEmpty() || text.isEmpty()) {
                    continue
                }
                builder.append(start)
                    .append(" --> ")
                    .append(end)
                    .append('\n')
                    .append(text)
                    .append("\n\n")
                written++
            }
            if (written == 0) {
                throw IllegalArgumentException("No usable webvtt cues")
            }
            return builder.toString()
        } catch (exception: IllegalArgumentException) {
            throw DlsiteJsonParser.IOExceptionLikeJsonException("DLsite 字幕解析失败", exception)
        }
    }

    private fun webvttCuesFromRoot(root: Any?): List<Any?> {
        val rootList = DlsiteJsonSupport.asListOrNull(root)
        if (rootList != null) {
            return rootList
        }
        val rootObject = DlsiteJsonSupport.asObjectOrNull(root)
        if (rootObject == null) {
            return ArrayList()
        }

        val listKeys = arrayOf("webvtt", "cues", "items", "captions", "subtitles", "data")
        for (key in listKeys) {
            val list = DlsiteJsonSupport.asListOrNull(rootObject[key])
            if (list != null) {
                return list
            }
        }
        for (key in listKeys) {
            val nested = DlsiteJsonSupport.asObjectOrNull(rootObject[key])
            if (nested != null) {
                val nestedCues = webvttCuesFromRoot(nested)
                if (nestedCues.isNotEmpty()) {
                    return nestedCues
                }
            }
        }

        val singleCue = ArrayList<Any?>()
        singleCue.add(rootObject)
        return singleCue
    }

    private fun numericCueTimesLookLikeMilliseconds(cues: List<Any?>): Boolean {
        var max = 0.0
        var millisecondVotes = 0
        var secondVotes = 0
        var hasFractionalTime = false
        for (value in cues) {
            val cue = DlsiteJsonSupport.asObjectOrNull(value) ?: continue
            val start = numericCueTime(cue, startTimeKeys())
            val end = numericCueTime(cue, endTimeKeys())
            if (start != null) {
                max = Math.max(max, Math.abs(start))
                hasFractionalTime = hasFractionalTime or hasFraction(start)
            }
            if (end != null) {
                max = Math.max(max, Math.abs(end))
                hasFractionalTime = hasFractionalTime or hasFraction(end)
            }
            if (start == null || end == null || end <= start) {
                continue
            }
            val duration = end - start
            if (duration > 30.0 && duration <= 300_000.0) {
                millisecondVotes++
            } else if (duration > 0.0 && duration <= 30.0) {
                secondVotes++
            }
        }
        if (hasFractionalTime) {
            return false
        }
        if (max > 10_000.0) {
            return true
        }
        return millisecondVotes > secondVotes
    }

    private fun startTimeKeys(): Array<String> = arrayOf(
        "start",
        "startTime",
        "start_time",
        "startTimeMs",
        "start_time_ms",
        "begin",
        "beginTime",
        "begin_time",
        "beginTimeMs",
        "begin_time_ms",
        "from",
        "fromMs",
        "from_ms",
        "startMs",
        "start_ms",
    )

    private fun endTimeKeys(): Array<String> = arrayOf(
        "end",
        "endTime",
        "end_time",
        "endTimeMs",
        "end_time_ms",
        "finish",
        "finishTime",
        "finish_time",
        "finishTimeMs",
        "finish_time_ms",
        "to",
        "toMs",
        "to_ms",
        "endMs",
        "end_ms",
    )

    private fun timingObjectKeys(): Array<String> = arrayOf("time", "timing", "timestamp", "range", "span", "period")

    private fun numericCueTime(cue: Map<String, Any?>, keys: Array<String>): Double? {
        for (key in keys) {
            if (!cue.containsKey(key)) {
                continue
            }
            val value = numericCueTime(cue[key])
            if (value != null) {
                return value
            }
        }
        for (key in timingObjectKeys()) {
            val nested = DlsiteJsonSupport.asObjectOrNull(cue[key])
            if (nested == null) {
                continue
            }
            val value = numericCueTime(nested, keys)
            if (value != null) {
                return value
            }
        }
        return null
    }

    private fun numericCueTime(value: Any?): Double? {
        if (value is Number) {
            return value.toDouble()
        }
        if (value is String) {
            val text = javaTrim(value)
            if (text.isEmpty() || text.contains(":")) {
                return null
            }
            try {
                return java.lang.Double.parseDouble(text)
            } catch (ignored: NumberFormatException) {
                return null
            }
        }
        val obj = DlsiteJsonSupport.asObjectOrNull(value)
        if (obj == null) {
            return null
        }
        for (key in arrayOf("time", "seconds", "second", "s", "milliseconds", "millisecond", "ms", "value")) {
            val nested = numericCueTime(obj[key])
            if (nested != null) {
                return nested
            }
        }
        return null
    }

    private fun hasFraction(value: Double): Boolean = Math.abs(value - Math.rint(value)) > 0.000_001

    private fun cueTime(cue: Map<String, Any?>, keys: Array<String>, numbersAreMilliseconds: Boolean): String {
        for (key in keys) {
            if (!cue.containsKey(key)) {
                continue
            }
            val time = cueTime(cue[key], key, numbersAreMilliseconds)
            if (time.isNotEmpty()) {
                return time
            }
        }
        for (key in timingObjectKeys()) {
            val nested = DlsiteJsonSupport.asObjectOrNull(cue[key])
            if (nested == null) {
                continue
            }
            val time = cueTime(nested, keys, numbersAreMilliseconds)
            if (time.isNotEmpty()) {
                return time
            }
        }
        return ""
    }

    private fun cueTime(value: Any?, key: String?, numbersAreMilliseconds: Boolean): String {
        if (value is Number) {
            return formatCueNumber(value.toDouble(), key, numbersAreMilliseconds)
        }
        if (value is String) {
            val text = javaTrim(value)
            if (text.isEmpty()) {
                return ""
            }
            if (text.contains(":")) {
                return normalizeColonTime(text)
            }
            try {
                return formatCueNumber(java.lang.Double.parseDouble(text), key, numbersAreMilliseconds)
            } catch (ignored: NumberFormatException) {
                return ""
            }
        }
        val obj = DlsiteJsonSupport.asObjectOrNull(value)
        if (obj == null) {
            return ""
        }
        for (nestedKey in arrayOf("time", "seconds", "second", "s", "milliseconds", "millisecond", "ms", "value")) {
            if (!obj.containsKey(nestedKey)) {
                continue
            }
            val time = cueTime(obj[nestedKey], nestedKey, numbersAreMilliseconds)
            if (time.isNotEmpty()) {
                return time
            }
        }
        return ""
    }

    private fun formatCueNumber(value: Double, key: String?, numbersAreMilliseconds: Boolean): String {
        val lowerKey = if (key == null) "" else key.lowercase(Locale.US)
        val milliseconds = numbersAreMilliseconds || lowerKey.contains("ms") || lowerKey.contains("milli")
        val millis = if (milliseconds) Math.round(value) else Math.round(value * 1000.0)
        return formatCueMillis(millis)
    }

    private fun normalizeColonTime(value: String): String {
        val main = WHITESPACE_PATTERN.split(javaTrim(value.replace(',', '.')), 2)[0]
        val pieces = COLON_PATTERN.split(main)
        if (pieces.size < 2 || pieces.size > 3) {
            return ""
        }
        try {
            var hours = 0L
            val minutes: Long
            val secondsText: String
            if (pieces.size == 3) {
                hours = java.lang.Long.parseLong(pieces[0])
                minutes = java.lang.Long.parseLong(pieces[1])
                secondsText = pieces[2]
            } else {
                minutes = java.lang.Long.parseLong(pieces[0])
                secondsText = pieces[1]
            }
            val seconds = java.lang.Double.parseDouble(secondsText)
            val millis = hours * 3_600_000L +
                minutes * 60_000L +
                Math.round(seconds * 1000.0)
            return formatCueMillis(millis)
        } catch (exception: NumberFormatException) {
            return ""
        }
    }

    private fun formatCueMillis(millis: Long): String {
        val safeMillis = Math.max(0L, millis)
        val hours = safeMillis / 3_600_000L
        var remaining = safeMillis % 3_600_000L
        val minutes = remaining / 60_000L
        remaining %= 60_000L
        val seconds = remaining / 1000L
        val milliseconds = remaining % 1000L
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, milliseconds)
    }

    private fun cueText(cue: Map<String, Any?>): String {
        val textKeys = arrayOf(
            "text",
            "body",
            "cue",
            "content",
            "value",
            "caption",
            "subtitle",
            "subtitles",
            "line",
            "payload",
        )
        for (key in textKeys) {
            val text = cueText(cue[key])
            if (text.isNotEmpty()) {
                return text
            }
        }
        for (key in arrayOf("lines", "texts", "children", "paragraphs")) {
            val text = cueText(cue[key])
            if (text.isNotEmpty()) {
                return text
            }
        }
        for (key in arrayOf("parts", "segments", "runs")) {
            val text = cueText(cue[key])
            if (text.isNotEmpty()) {
                return text
            }
        }
        return ""
    }

    private fun cueText(value: Any?): String {
        if (value is String) {
            return normalizeCueText(value)
        }
        if (value is Number || value is Boolean) {
            return ""
        }
        val list = DlsiteJsonSupport.asListOrNull(value)
        if (list != null) {
            val builder = StringBuilder()
            for (item in list) {
                val text = cueText(item)
                if (text.isEmpty()) {
                    continue
                }
                if (builder.isNotEmpty()) {
                    builder.append('\n')
                }
                builder.append(text)
            }
            return builder.toString()
        }
        val obj = DlsiteJsonSupport.asObjectOrNull(value)
        return if (obj == null) "" else cueText(obj)
    }

    private fun normalizeCueText(text: String?): String {
        return if (text == null) {
            ""
        } else {
            javaTrim(text.replace("\r\n", "\n").replace('\r', '\n'))
        }
    }

    private fun javaTrim(value: String): String = value.trim { it <= ' ' }
}
