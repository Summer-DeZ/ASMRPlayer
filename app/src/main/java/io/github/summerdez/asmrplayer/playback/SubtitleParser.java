package io.github.summerdez.asmrplayer.playback;

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
import android.content.ContentResolver;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SubtitleParser {
    private static final Pattern LRC_TIME =
            Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern SRT_TIME =
            Pattern.compile("(.+?)\\s*-->\\s*(.+)");

    private SubtitleParser() {
    }

    public static List<SubtitleCue> parse(ContentResolver resolver, Uri uri) throws IOException {
        List<String> lines = readLines(resolver, uri);
        List<SubtitleCue> cues = parseSrt(lines);
        if (!cues.isEmpty()) {
            return cues;
        }

        cues = parseLrc(lines);
        if (!cues.isEmpty()) {
            return cues;
        }

        StringBuilder plainText = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                if (plainText.length() > 0) {
                    plainText.append('\n');
                }
                plainText.append(trimmed);
            }
        }
        if (plainText.length() == 0) {
            return Collections.emptyList();
        }
        List<SubtitleCue> fallback = new ArrayList<>();
        fallback.add(new SubtitleCue(0L, Long.MAX_VALUE, plainText.toString()));
        return fallback;
    }

    public static String textAt(List<SubtitleCue> cues, long positionMs) {
        int index = indexAt(cues, positionMs);
        return index >= 0 ? cues.get(index).text : "";
    }

    public static int indexAt(List<SubtitleCue> cues, long positionMs) {
        if (cues == null || cues.isEmpty()) {
            return -1;
        }

        int low = 0;
        int high = cues.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            SubtitleCue cue = cues.get(mid);
            if (positionMs < cue.startMs) {
                high = mid - 1;
            } else if (positionMs > cue.endMs) {
                low = mid + 1;
            } else {
                return mid;
            }
        }
        return high >= 0 ? high : -1;
    }

    private static List<String> readLines(ContentResolver resolver, Uri uri) throws IOException {
        List<String> lines = new ArrayList<>();
        try (InputStream stream = resolver.openInputStream(uri)) {
            if (stream == null) {
                return lines;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    private static List<SubtitleCue> parseLrc(List<String> lines) {
        List<SubtitleCue> cues = new ArrayList<>();
        for (String line : lines) {
            Matcher matcher = LRC_TIME.matcher(line);
            List<Long> times = new ArrayList<>();
            int lastMatchEnd = -1;
            while (matcher.find()) {
                times.add(parseLrcTime(matcher));
                lastMatchEnd = matcher.end();
            }
            if (times.isEmpty() || lastMatchEnd < 0) {
                continue;
            }

            String text = line.substring(lastMatchEnd).trim();
            if (text.isEmpty()) {
                continue;
            }
            for (Long time : times) {
                cues.add(new SubtitleCue(time, time + 5000L, text));
            }
        }

        cues.sort(Comparator.comparingLong(cue -> cue.startMs));
        List<SubtitleCue> normalized = new ArrayList<>();
        for (int i = 0; i < cues.size(); i++) {
            SubtitleCue cue = cues.get(i);
            long end = i + 1 < cues.size()
                    ? Math.max(cue.startMs, cues.get(i + 1).startMs - 1L)
                    : cue.endMs;
            normalized.add(new SubtitleCue(cue.startMs, end, cue.text));
        }
        return normalized;
    }

    private static List<SubtitleCue> parseSrt(List<String> lines) {
        List<SubtitleCue> cues = new ArrayList<>();
        int index = 0;
        while (index < lines.size()) {
            String line = lines.get(index).trim();
            if (line.isEmpty()) {
                index++;
                continue;
            }

            if (isInteger(line) && index + 1 < lines.size()) {
                index++;
                line = lines.get(index).trim();
            }

            Matcher matcher = SRT_TIME.matcher(line);
            if (!matcher.matches()) {
                index++;
                continue;
            }

            long start = parseSrtTime(matcher.group(1).trim());
            long end = parseSrtTime(matcher.group(2).trim());
            index++;

            StringBuilder text = new StringBuilder();
            while (index < lines.size()) {
                String textLine = lines.get(index).trim();
                if (textLine.isEmpty()) {
                    break;
                }
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append(textLine);
                index++;
            }

            if (start >= 0L && end >= start && text.length() > 0) {
                cues.add(new SubtitleCue(start, end, text.toString()));
            }
            index++;
        }
        cues.sort(Comparator.comparingLong(cue -> cue.startMs));
        return cues;
    }

    private static long parseLrcTime(Matcher matcher) {
        long minutes = Long.parseLong(matcher.group(1));
        long seconds = Long.parseLong(matcher.group(2));
        String fraction = matcher.group(3);
        long millis = 0L;
        if (fraction != null && !fraction.isEmpty()) {
            if (fraction.length() == 1) {
                millis = Long.parseLong(fraction) * 100L;
            } else if (fraction.length() == 2) {
                millis = Long.parseLong(fraction) * 10L;
            } else {
                millis = Long.parseLong(fraction.substring(0, 3));
            }
        }
        return minutes * 60_000L + seconds * 1000L + millis;
    }

    private static long parseSrtTime(String value) {
        String cleaned = value.replace(',', '.');
        String[] pieces = cleaned.split(":");
        if (pieces.length < 2 || pieces.length > 3) {
            return -1L;
        }

        long hours = 0L;
        long minutes;
        String secondsPart;
        if (pieces.length == 3) {
            hours = parseLongSafe(pieces[0]);
            minutes = parseLongSafe(pieces[1]);
            secondsPart = pieces[2];
        } else {
            minutes = parseLongSafe(pieces[0]);
            secondsPart = pieces[1];
        }

        String[] secondsPieces = secondsPart.split("\\.", 2);
        long seconds = parseLongSafe(secondsPieces[0]);
        long millis = 0L;
        if (secondsPieces.length > 1) {
            String fraction = secondsPieces[1];
            if (fraction.length() == 1) {
                millis = parseLongSafe(fraction) * 100L;
            } else if (fraction.length() == 2) {
                millis = parseLongSafe(fraction) * 10L;
            } else {
                millis = parseLongSafe(fraction.substring(0, 3));
            }
        }

        if (hours < 0L || minutes < 0L || seconds < 0L || millis < 0L) {
            return -1L;
        }
        return hours * 3_600_000L + minutes * 60_000L + seconds * 1000L + millis;
    }

    private static boolean isInteger(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return !value.isEmpty();
    }

    private static long parseLongSafe(String value) {
        try {
            return Long.parseLong(value.trim().toLowerCase(Locale.US));
        } catch (NumberFormatException error) {
            return -1L;
        }
    }
}
