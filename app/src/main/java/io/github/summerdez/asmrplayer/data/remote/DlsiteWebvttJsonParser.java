package io.github.summerdez.asmrplayer.data.remote;

import io.github.summerdez.asmrplayer.R;
import io.github.summerdez.asmrplayer.data.*;
import io.github.summerdez.asmrplayer.data.remote.*;
import io.github.summerdez.asmrplayer.data.download.*;
import io.github.summerdez.asmrplayer.data.files.*;
import io.github.summerdez.asmrplayer.domain.*;
import io.github.summerdez.asmrplayer.domain.model.*;
import io.github.summerdez.asmrplayer.playback.*;
import io.github.summerdez.asmrplayer.presentation.*;
import io.github.summerdez.asmrplayer.ui.*;
import io.github.summerdez.asmrplayer.ui.activity.*;
import io.github.summerdez.asmrplayer.ui.components.*;
import io.github.summerdez.asmrplayer.ui.screens.*;
import io.github.summerdez.asmrplayer.ui.theme.*;
import io.github.summerdez.asmrplayer.ui.util.*;
import io.github.summerdez.asmrplayer.di.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class DlsiteWebvttJsonParser {
    private DlsiteWebvttJsonParser() {
    }

    static String parseWebvttJson(String json) throws DlsiteJsonParser.IOExceptionLikeJsonException {
        String trimmed = json == null ? "" : json.trim();
        if (trimmed.startsWith("WEBVTT")) {
            return trimmed.endsWith("\n") ? trimmed : trimmed + "\n";
        }

        try {
            Object root = DlsiteJsonSupport.parse(json);
            List<Object> cues = webvttCuesFromRoot(root);
            boolean numbersAreMilliseconds = numericCueTimesLookLikeMilliseconds(cues);
            StringBuilder builder = new StringBuilder("WEBVTT\n\n");
            int written = 0;
            for (Object value : cues) {
                Map<String, Object> cue = DlsiteJsonSupport.asObjectOrNull(value);
                if (cue == null) {
                    continue;
                }
                String start = cueTime(cue, startTimeKeys(), numbersAreMilliseconds);
                String end = cueTime(cue, endTimeKeys(), numbersAreMilliseconds);
                String text = cueText(cue);
                if (start.isEmpty() || end.isEmpty() || text.isEmpty()) {
                    continue;
                }
                builder.append(start)
                        .append(" --> ")
                        .append(end)
                        .append('\n')
                        .append(text)
                        .append("\n\n");
                written++;
            }
            if (written == 0) {
                throw new IllegalArgumentException("No usable webvtt cues");
            }
            return builder.toString();
        } catch (IllegalArgumentException exception) {
            throw new DlsiteJsonParser.IOExceptionLikeJsonException("DLsite 字幕解析失败", exception);
        }
    }

    private static List<Object> webvttCuesFromRoot(Object root) {
        List<Object> rootList = DlsiteJsonSupport.asListOrNull(root);
        if (rootList != null) {
            return rootList;
        }
        Map<String, Object> rootObject = DlsiteJsonSupport.asObjectOrNull(root);
        if (rootObject == null) {
            return new ArrayList<>();
        }

        String[] listKeys = new String[]{"webvtt", "cues", "items", "captions", "subtitles", "data"};
        for (String key : listKeys) {
            List<Object> list = DlsiteJsonSupport.asListOrNull(rootObject.get(key));
            if (list != null) {
                return list;
            }
        }
        for (String key : listKeys) {
            Map<String, Object> nested = DlsiteJsonSupport.asObjectOrNull(rootObject.get(key));
            if (nested != null) {
                List<Object> nestedCues = webvttCuesFromRoot(nested);
                if (!nestedCues.isEmpty()) {
                    return nestedCues;
                }
            }
        }

        List<Object> singleCue = new ArrayList<>();
        singleCue.add(rootObject);
        return singleCue;
    }

    private static boolean numericCueTimesLookLikeMilliseconds(List<Object> cues) {
        double max = 0D;
        int millisecondVotes = 0;
        int secondVotes = 0;
        boolean hasFractionalTime = false;
        for (Object value : cues) {
            Map<String, Object> cue = DlsiteJsonSupport.asObjectOrNull(value);
            if (cue == null) {
                continue;
            }
            Double start = numericCueTime(cue, startTimeKeys());
            Double end = numericCueTime(cue, endTimeKeys());
            if (start != null) {
                max = Math.max(max, Math.abs(start));
                hasFractionalTime |= hasFraction(start);
            }
            if (end != null) {
                max = Math.max(max, Math.abs(end));
                hasFractionalTime |= hasFraction(end);
            }
            if (start == null || end == null || end <= start) {
                continue;
            }
            double duration = end - start;
            if (duration > 30D && duration <= 300_000D) {
                millisecondVotes++;
            } else if (duration > 0D && duration <= 30D) {
                secondVotes++;
            }
        }
        if (hasFractionalTime) {
            return false;
        }
        if (max > 10_000D) {
            return true;
        }
        return millisecondVotes > secondVotes;
    }

    private static String[] startTimeKeys() {
        return new String[]{
                "start", "startTime", "start_time", "startTimeMs", "start_time_ms",
                "begin", "beginTime", "begin_time", "beginTimeMs", "begin_time_ms",
                "from", "fromMs", "from_ms", "startMs", "start_ms"
        };
    }

    private static String[] endTimeKeys() {
        return new String[]{
                "end", "endTime", "end_time", "endTimeMs", "end_time_ms",
                "finish", "finishTime", "finish_time", "finishTimeMs", "finish_time_ms",
                "to", "toMs", "to_ms", "endMs", "end_ms"
        };
    }

    private static String[] timingObjectKeys() {
        return new String[]{"time", "timing", "timestamp", "range", "span", "period"};
    }

    private static Double numericCueTime(Map<String, Object> cue, String[] keys) {
        for (String key : keys) {
            if (!cue.containsKey(key)) {
                continue;
            }
            Double value = numericCueTime(cue.get(key));
            if (value != null) {
                return value;
            }
        }
        for (String key : timingObjectKeys()) {
            Map<String, Object> nested = DlsiteJsonSupport.asObjectOrNull(cue.get(key));
            if (nested == null) {
                continue;
            }
            Double value = numericCueTime(nested, keys);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Double numericCueTime(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty() || text.contains(":")) {
                return null;
            }
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        Map<String, Object> object = DlsiteJsonSupport.asObjectOrNull(value);
        if (object == null) {
            return null;
        }
        for (String key : new String[]{"time", "seconds", "second", "s", "milliseconds", "millisecond", "ms", "value"}) {
            Double nested = numericCueTime(object.get(key));
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static boolean hasFraction(double value) {
        return Math.abs(value - Math.rint(value)) > 0.000_001D;
    }

    private static String cueTime(Map<String, Object> cue, String[] keys, boolean numbersAreMilliseconds) {
        for (String key : keys) {
            if (!cue.containsKey(key)) {
                continue;
            }
            String time = cueTime(cue.get(key), key, numbersAreMilliseconds);
            if (!time.isEmpty()) {
                return time;
            }
        }
        for (String key : timingObjectKeys()) {
            Map<String, Object> nested = DlsiteJsonSupport.asObjectOrNull(cue.get(key));
            if (nested == null) {
                continue;
            }
            String time = cueTime(nested, keys, numbersAreMilliseconds);
            if (!time.isEmpty()) {
                return time;
            }
        }
        return "";
    }

    private static String cueTime(Object value, String key, boolean numbersAreMilliseconds) {
        if (value instanceof Number) {
            return formatCueNumber(((Number) value).doubleValue(), key, numbersAreMilliseconds);
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) {
                return "";
            }
            if (text.contains(":")) {
                return normalizeColonTime(text);
            }
            try {
                return formatCueNumber(Double.parseDouble(text), key, numbersAreMilliseconds);
            } catch (NumberFormatException ignored) {
                return "";
            }
        }
        Map<String, Object> object = DlsiteJsonSupport.asObjectOrNull(value);
        if (object == null) {
            return "";
        }
        for (String nestedKey : new String[]{
                "time", "seconds", "second", "s", "milliseconds", "millisecond", "ms", "value"
        }) {
            if (!object.containsKey(nestedKey)) {
                continue;
            }
            String time = cueTime(object.get(nestedKey), nestedKey, numbersAreMilliseconds);
            if (!time.isEmpty()) {
                return time;
            }
        }
        return "";
    }

    private static String formatCueNumber(double value, String key, boolean numbersAreMilliseconds) {
        String lowerKey = key == null ? "" : key.toLowerCase(Locale.US);
        boolean milliseconds = numbersAreMilliseconds || lowerKey.contains("ms") || lowerKey.contains("milli");
        long millis = milliseconds ? Math.round(value) : Math.round(value * 1000D);
        return formatCueMillis(millis);
    }

    private static String normalizeColonTime(String value) {
        String main = value.replace(',', '.').trim().split("\\s+", 2)[0];
        String[] pieces = main.split(":");
        if (pieces.length < 2 || pieces.length > 3) {
            return "";
        }
        try {
            long hours = 0L;
            long minutes;
            String secondsText;
            if (pieces.length == 3) {
                hours = Long.parseLong(pieces[0]);
                minutes = Long.parseLong(pieces[1]);
                secondsText = pieces[2];
            } else {
                minutes = Long.parseLong(pieces[0]);
                secondsText = pieces[1];
            }
            double seconds = Double.parseDouble(secondsText);
            long millis = hours * 3_600_000L
                    + minutes * 60_000L
                    + Math.round(seconds * 1000D);
            return formatCueMillis(millis);
        } catch (NumberFormatException exception) {
            return "";
        }
    }

    private static String formatCueMillis(long millis) {
        long safeMillis = Math.max(0L, millis);
        long hours = safeMillis / 3_600_000L;
        long remaining = safeMillis % 3_600_000L;
        long minutes = remaining / 60_000L;
        remaining %= 60_000L;
        long seconds = remaining / 1000L;
        long milliseconds = remaining % 1000L;
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, milliseconds);
    }

    private static String cueText(Map<String, Object> cue) {
        String[] textKeys = new String[]{
                "text", "body", "cue", "content", "value", "caption", "subtitle", "subtitles", "line", "payload"
        };
        for (String key : textKeys) {
            String text = cueText(cue.get(key));
            if (!text.isEmpty()) {
                return text;
            }
        }
        for (String key : new String[]{"lines", "texts", "children", "paragraphs"}) {
            String text = cueText(cue.get(key));
            if (!text.isEmpty()) {
                return text;
            }
        }
        for (String key : new String[]{"parts", "segments", "runs"}) {
            String text = cueText(cue.get(key));
            if (!text.isEmpty()) {
                return text;
            }
        }
        return "";
    }

    private static String cueText(Object value) {
        if (value instanceof String) {
            return normalizeCueText((String) value);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return "";
        }
        List<Object> list = DlsiteJsonSupport.asListOrNull(value);
        if (list != null) {
            StringBuilder builder = new StringBuilder();
            for (Object item : list) {
                String text = cueText(item);
                if (text.isEmpty()) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(text);
            }
            return builder.toString();
        }
        Map<String, Object> object = DlsiteJsonSupport.asObjectOrNull(value);
        return object == null ? "" : cueText(object);
    }

    private static String normalizeCueText(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n').trim();
    }
}
