package com.boratreino.core.model

/** Thrown by [Json.parse] on malformed text. */
internal class JsonException(message: String) : Exception(message)

/**
 * A small JSON reader and writer. Parsed values are Map<String, Any?>, List<Any?>, String, Boolean,
 * null, or [JsonNumber] (the number's text, so integers and doubles stay exact).
 */
internal object Json {
    private const val MAX_DEPTH = 64

    class JsonNumber(val text: String)

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWs()
        val value = p.readValue(0)
        p.skipWs()
        if (!p.atEnd()) throw JsonException("Unexpected text after the JSON value")
        return value
    }

    /** Writes Map, List, String, Boolean, Int, Long, Double (finite) and null. */
    fun write(value: Any?): String = StringBuilder().also { append(it, value) }.toString()

    private fun append(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is String -> appendString(sb, value)
            is Boolean -> sb.append(if (value) "true" else "false")
            is Double -> {
                require(value.isFinite()) { "Number must be finite" }
                sb.append(value.toString())
            }
            is Number -> sb.append(value.toString())
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) sb.append(',')
                    first = false
                    appendString(sb, k as String)
                    sb.append(':')
                    append(sb, v)
                }
                sb.append('}')
            }
            is List<*> -> {
                sb.append('[')
                value.forEachIndexed { i, v ->
                    if (i > 0) sb.append(',')
                    append(sb, v)
                }
                sb.append(']')
            }
            else -> throw IllegalArgumentException("Cannot write ${value::class.java.simpleName}")
        }
    }

    private fun appendString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\t' -> sb.append("\\t")
                c == '\r' -> sb.append("\\r")
                c < ' ' -> {
                    sb.append("\\u")
                    val hex = c.code.toString(16)
                    repeat(4 - hex.length) { sb.append('0') }
                    sb.append(hex)
                }
                else -> sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun atEnd() = i >= s.length

        fun skipWs() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\t' || s[i] == '\r')) i++
        }

        fun readValue(depth: Int): Any? {
            if (depth > MAX_DEPTH) throw JsonException("Nesting too deep")
            if (atEnd()) throw JsonException("Unexpected end")
            return when (val c = s[i]) {
                '{' -> readObject(depth)
                '[' -> readArray(depth)
                '"' -> readString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c in '0'..'9') readNumber() else throw JsonException("Unexpected character")
            }
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!s.startsWith(word, i)) throw JsonException("Bad literal")
            i += word.length
            return value
        }

        private fun readNumber(): JsonNumber {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i] in '0'..'9' || s[i] == '.' || s[i] == 'e' || s[i] == 'E' ||
                    s[i] == '+' || s[i] == '-')
            ) i++
            val text = s.substring(start, i)
            if (text.toDoubleOrNull() == null) throw JsonException("Bad number")
            return JsonNumber(text)
        }

        private fun readString(): String {
            i++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw JsonException("Unterminated string")
                val c = s[i++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (atEnd()) throw JsonException("Unterminated escape")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            'r' -> sb.append('\r')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                if (i + 4 > s.length) throw JsonException("Bad unicode escape")
                                val code = s.substring(i, i + 4).toIntOrNull(16)
                                    ?: throw JsonException("Bad unicode escape")
                                sb.append(code.toChar())
                                i += 4
                            }
                            else -> throw JsonException("Bad escape \\$e")
                        }
                    }
                    c < ' ' -> throw JsonException("Control character in string")
                    else -> sb.append(c)
                }
            }
        }

        private fun readArray(depth: Int): List<Any?> {
            i++ // [
            val list = ArrayList<Any?>()
            skipWs()
            if (!atEnd() && s[i] == ']') {
                i++
                return list
            }
            while (true) {
                skipWs()
                list.add(readValue(depth + 1))
                skipWs()
                if (atEnd()) throw JsonException("Unterminated array")
                when (s[i++]) {
                    ',' -> Unit
                    ']' -> return list
                    else -> throw JsonException("Bad array")
                }
            }
        }

        private fun readObject(depth: Int): Map<String, Any?> {
            i++ // {
            val map = LinkedHashMap<String, Any?>()
            skipWs()
            if (!atEnd() && s[i] == '}') {
                i++
                return map
            }
            while (true) {
                skipWs()
                if (atEnd() || s[i] != '"') throw JsonException("Expected a key")
                val key = readString()
                skipWs()
                if (atEnd() || s[i++] != ':') throw JsonException("Expected a colon")
                skipWs()
                map[key] = readValue(depth + 1)
                skipWs()
                if (atEnd()) throw JsonException("Unterminated object")
                when (s[i++]) {
                    ',' -> Unit
                    '}' -> return map
                    else -> throw JsonException("Bad object")
                }
            }
        }
    }
}
