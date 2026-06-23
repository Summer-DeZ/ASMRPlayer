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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DlsiteJsonSupport {
    private DlsiteJsonSupport() {
    }

    static Object parse(String json) {
        return new JsonReader(json).parse();
    }

    static List<Object> arrayFromRoot(Object root, String key) {
        if (root instanceof List) {
            return asList(root);
        }
        Map<String, Object> object = asObject(root);
        List<Object> direct = asListOrNull(object.get(key));
        if (direct != null) {
            return direct;
        }
        List<Object> data = asListOrNull(object.get("data"));
        return data == null ? new ArrayList<>() : data;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asObject(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        throw new IllegalArgumentException("Expected JSON object");
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asObjectOrNull(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    static List<Object> asList(Object value) {
        if (value instanceof List) {
            return (List<Object>) value;
        }
        throw new IllegalArgumentException("Expected JSON array");
    }

    @SuppressWarnings("unchecked")
    static List<Object> asListOrNull(Object value) {
        return value instanceof List ? (List<Object>) value : null;
    }

    static List<Object> asListOrEmpty(Object value) {
        List<Object> list = asListOrNull(value);
        return list == null ? new ArrayList<>() : list;
    }

    static String asString(Object value) {
        return value instanceof String ? (String) value : "";
    }

    static String asQueryValue(Object value) {
        if (value instanceof String) {
            return ((String) value).trim();
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return "";
    }

    static int asInt(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    static long asLong(Object value, long fallback) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    static Boolean asBooleanOrNull(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if ("true".equalsIgnoreCase(text)) {
                return true;
            }
            if ("false".equalsIgnoreCase(text)) {
                return false;
            }
        }
        return null;
    }

    static String escapeJson(String value) {
        String safeValue = value == null ? "" : value;
        StringBuilder builder = new StringBuilder(safeValue.length());
        for (int i = 0; i < safeValue.length(); i++) {
            char ch = safeValue.charAt(i);
            switch (ch) {
                case '"':
                    builder.append("\\\"");
                    break;
                case '\\':
                    builder.append("\\\\");
                    break;
                case '\b':
                    builder.append("\\b");
                    break;
                case '\f':
                    builder.append("\\f");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                    break;
            }
        }
        return builder.toString();
    }

    private static final class JsonReader {
        private final String input;
        private int index;

        JsonReader(String input) {
            this.input = input == null ? "" : input;
        }

        Object parse() {
            skipWhitespace();
            Object value = parseValue();
            skipWhitespace();
            if (index != input.length()) {
                throw error("Unexpected trailing content");
            }
            return value;
        }

        private Object parseValue() {
            skipWhitespace();
            if (index >= input.length()) {
                throw error("Unexpected end of JSON");
            }
            char ch = input.charAt(index);
            if (ch == '{') {
                return parseObject();
            }
            if (ch == '[') {
                return parseArray();
            }
            if (ch == '"') {
                return parseString();
            }
            if (ch == 't') {
                expectLiteral("true");
                return true;
            }
            if (ch == 'f') {
                expectLiteral("false");
                return false;
            }
            if (ch == 'n') {
                expectLiteral("null");
                return null;
            }
            if (ch == '-' || Character.isDigit(ch)) {
                return parseNumber();
            }
            throw error("Unexpected JSON value");
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> object = new LinkedHashMap<>();
            skipWhitespace();
            if (consume('}')) {
                return object;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                object.put(key, parseValue());
                skipWhitespace();
                if (consume('}')) {
                    return object;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> array = new ArrayList<>();
            skipWhitespace();
            if (consume(']')) {
                return array;
            }
            while (true) {
                array.add(parseValue());
                skipWhitespace();
                if (consume(']')) {
                    return array;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (index < input.length()) {
                char ch = input.charAt(index++);
                if (ch == '"') {
                    return builder.toString();
                }
                if (ch == '\\') {
                    builder.append(parseEscapedCharacter());
                } else {
                    if (ch < 0x20) {
                        throw error("Unescaped control character");
                    }
                    builder.append(ch);
                }
            }
            throw error("Unterminated JSON string");
        }

        private char parseEscapedCharacter() {
            if (index >= input.length()) {
                throw error("Unterminated escape sequence");
            }
            char ch = input.charAt(index++);
            switch (ch) {
                case '"':
                case '\\':
                case '/':
                    return ch;
                case 'b':
                    return '\b';
                case 'f':
                    return '\f';
                case 'n':
                    return '\n';
                case 'r':
                    return '\r';
                case 't':
                    return '\t';
                case 'u':
                    return parseUnicodeEscape();
                default:
                    throw error("Invalid escape sequence");
            }
        }

        private char parseUnicodeEscape() {
            if (index + 4 > input.length()) {
                throw error("Invalid unicode escape");
            }
            int value = 0;
            for (int i = 0; i < 4; i++) {
                int digit = Character.digit(input.charAt(index++), 16);
                if (digit < 0) {
                    throw error("Invalid unicode escape");
                }
                value = value * 16 + digit;
            }
            return (char) value;
        }

        private Number parseNumber() {
            int start = index;
            if (consume('-') && index >= input.length()) {
                throw error("Invalid number");
            }
            if (consume('0')) {
                if (index < input.length() && Character.isDigit(input.charAt(index))) {
                    throw error("Invalid number");
                }
            } else {
                consumeDigits();
            }
            boolean floating = false;
            if (consume('.')) {
                floating = true;
                consumeDigits();
            }
            if (index < input.length()) {
                char ch = input.charAt(index);
                if (ch == 'e' || ch == 'E') {
                    floating = true;
                    index++;
                    if (index < input.length()) {
                        char sign = input.charAt(index);
                        if (sign == '+' || sign == '-') {
                            index++;
                        }
                    }
                    consumeDigits();
                }
            }
            String text = input.substring(start, index);
            try {
                return floating ? Double.parseDouble(text) : Long.parseLong(text);
            } catch (NumberFormatException exception) {
                throw error("Invalid number");
            }
        }

        private void consumeDigits() {
            int start = index;
            while (index < input.length() && Character.isDigit(input.charAt(index))) {
                index++;
            }
            if (start == index) {
                throw error("Expected digit");
            }
        }

        private void expectLiteral(String literal) {
            if (!input.startsWith(literal, index)) {
                throw error("Invalid literal");
            }
            index += literal.length();
        }

        private boolean consume(char expected) {
            if (index < input.length() && input.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!consume(expected)) {
                throw error("Expected " + expected);
            }
        }

        private void skipWhitespace() {
            while (index < input.length()) {
                char ch = input.charAt(index);
                if (ch != ' ' && ch != '\n' && ch != '\r' && ch != '\t') {
                    return;
                }
                index++;
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at " + index);
        }
    }
}
