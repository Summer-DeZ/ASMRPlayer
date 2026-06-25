package io.github.summerdez.asmrplayer.data.remote

import java.util.ArrayList
import java.util.LinkedHashMap

object DlsiteJsonSupport {
    @JvmStatic
    fun parse(json: String?): Any? {
        return JsonReader(json).parse()
    }

    @JvmStatic
    fun arrayFromRoot(root: Any?, key: String): List<Any?> {
        if (root is List<*>) {
            return asList(root)
        }
        val obj = asObject(root)
        val direct = asListOrNull(obj[key])
        if (direct != null) {
            return direct
        }
        val data = asListOrNull(obj["data"])
        return data ?: ArrayList()
    }

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun asObject(value: Any?): Map<String, Any?> {
        if (value is Map<*, *>) {
            return value as Map<String, Any?>
        }
        throw IllegalArgumentException("Expected JSON object")
    }

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun asObjectOrNull(value: Any?): Map<String, Any?>? {
        return if (value is Map<*, *>) value as Map<String, Any?> else null
    }

    @JvmStatic
    fun asList(value: Any?): List<Any?> {
        if (value is List<*>) {
            return value
        }
        throw IllegalArgumentException("Expected JSON array")
    }

    @JvmStatic
    fun asListOrNull(value: Any?): List<Any?>? {
        return if (value is List<*>) value else null
    }

    @JvmStatic
    fun asListOrEmpty(value: Any?): List<Any?> {
        val list = asListOrNull(value)
        return list ?: ArrayList()
    }

    @JvmStatic
    fun asString(value: Any?): String {
        return if (value is String) value else ""
    }

    @JvmStatic
    fun asQueryValue(value: Any?): String {
        if (value is String) {
            return value.trim { it <= ' ' }
        }
        if (value is Number || value is Boolean) {
            return value.toString()
        }
        return ""
    }

    @JvmStatic
    fun asInt(value: Any?, fallback: Int): Int {
        if (value is Number) {
            return value.toInt()
        }
        if (value is String) {
            try {
                return value.trim { it <= ' ' }.toInt()
            } catch (ignored: NumberFormatException) {
                return fallback
            }
        }
        return fallback
    }

    @JvmStatic
    fun asLong(value: Any?, fallback: Long): Long {
        if (value is Number) {
            return value.toLong()
        }
        if (value is String) {
            try {
                return value.trim { it <= ' ' }.toLong()
            } catch (ignored: NumberFormatException) {
                return fallback
            }
        }
        return fallback
    }

    @JvmStatic
    fun asBooleanOrNull(value: Any?): Boolean? {
        if (value is Boolean) {
            return value
        }
        if (value is String) {
            val text = value.trim { it <= ' ' }
            if ("true".equals(text, ignoreCase = true)) {
                return true
            }
            if ("false".equals(text, ignoreCase = true)) {
                return false
            }
        }
        return null
    }

    @JvmStatic
    fun escapeJson(value: String?): String {
        val safeValue = value ?: ""
        val builder = StringBuilder(safeValue.length)
        for (ch in safeValue) {
            when (ch) {
                '"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\b' -> builder.append("\\b")
                '\u000C' -> builder.append("\\f")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> {
                    if (ch < ' ') {
                        builder.append("\\u")
                            .append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        builder.append(ch)
                    }
                }
            }
        }
        return builder.toString()
    }

    private class JsonReader(input: String?) {
        private val input: String = input ?: ""
        private var index = 0

        fun parse(): Any? {
            skipWhitespace()
            val value = parseValue()
            skipWhitespace()
            if (index != input.length) {
                throw error("Unexpected trailing content")
            }
            return value
        }

        private fun parseValue(): Any? {
            skipWhitespace()
            if (index >= input.length) {
                throw error("Unexpected end of JSON")
            }
            val ch = input[index]
            if (ch == '{') {
                return parseObject()
            }
            if (ch == '[') {
                return parseArray()
            }
            if (ch == '"') {
                return parseString()
            }
            if (ch == 't') {
                expectLiteral("true")
                return true
            }
            if (ch == 'f') {
                expectLiteral("false")
                return false
            }
            if (ch == 'n') {
                expectLiteral("null")
                return null
            }
            if (ch == '-' || Character.isDigit(ch)) {
                return parseNumber()
            }
            throw error("Unexpected JSON value")
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            val obj = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (consume('}')) {
                return obj
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                obj[key] = parseValue()
                skipWhitespace()
                if (consume('}')) {
                    return obj
                }
                expect(',')
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val array = ArrayList<Any?>()
            skipWhitespace()
            if (consume(']')) {
                return array
            }
            while (true) {
                array.add(parseValue())
                skipWhitespace()
                if (consume(']')) {
                    return array
                }
                expect(',')
            }
        }

        private fun parseString(): String {
            expect('"')
            val builder = StringBuilder()
            while (index < input.length) {
                val ch = input[index++]
                if (ch == '"') {
                    return builder.toString()
                }
                if (ch == '\\') {
                    builder.append(parseEscapedCharacter())
                } else {
                    if (ch < ' ') {
                        throw error("Unescaped control character")
                    }
                    builder.append(ch)
                }
            }
            throw error("Unterminated JSON string")
        }

        private fun parseEscapedCharacter(): Char {
            if (index >= input.length) {
                throw error("Unterminated escape sequence")
            }
            val ch = input[index++]
            return when (ch) {
                '"', '\\', '/' -> ch
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> parseUnicodeEscape()
                else -> throw error("Invalid escape sequence")
            }
        }

        private fun parseUnicodeEscape(): Char {
            if (index + 4 > input.length) {
                throw error("Invalid unicode escape")
            }
            var value = 0
            for (i in 0 until 4) {
                val digit = Character.digit(input[index++], 16)
                if (digit < 0) {
                    throw error("Invalid unicode escape")
                }
                value = value * 16 + digit
            }
            return value.toChar()
        }

        private fun parseNumber(): Number {
            val start = index
            if (consume('-') && index >= input.length) {
                throw error("Invalid number")
            }
            if (consume('0')) {
                if (index < input.length && Character.isDigit(input[index])) {
                    throw error("Invalid number")
                }
            } else {
                consumeDigits()
            }
            var floating = false
            if (consume('.')) {
                floating = true
                consumeDigits()
            }
            if (index < input.length) {
                val ch = input[index]
                if (ch == 'e' || ch == 'E') {
                    floating = true
                    index++
                    if (index < input.length) {
                        val sign = input[index]
                        if (sign == '+' || sign == '-') {
                            index++
                        }
                    }
                    consumeDigits()
                }
            }
            val text = input.substring(start, index)
            try {
                return if (floating) text.toDouble() else text.toLong()
            } catch (exception: NumberFormatException) {
                throw error("Invalid number")
            }
        }

        private fun consumeDigits() {
            val start = index
            while (index < input.length && Character.isDigit(input[index])) {
                index++
            }
            if (start == index) {
                throw error("Expected digit")
            }
        }

        private fun expectLiteral(literal: String) {
            if (!input.startsWith(literal, index)) {
                throw error("Invalid literal")
            }
            index += literal.length
        }

        private fun consume(expected: Char): Boolean {
            if (index < input.length && input[index] == expected) {
                index++
                return true
            }
            return false
        }

        private fun expect(expected: Char) {
            if (!consume(expected)) {
                throw error("Expected $expected")
            }
        }

        private fun skipWhitespace() {
            while (index < input.length) {
                val ch = input[index]
                if (ch != ' ' && ch != '\n' && ch != '\r' && ch != '\t') {
                    return
                }
                index++
            }
        }

        private fun error(message: String): IllegalArgumentException {
            return IllegalArgumentException("$message at $index")
        }
    }
}
