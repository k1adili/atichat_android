package com.k1adili.atichat.core

/**
 * Tiny dependency-free JSON codec (objects, arrays, strings, numbers, booleans, null).
 * Kept separate from org.json so the protocol core runs identically on Android and on a plain JVM.
 */
object Json {
    fun encode(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    /** Parses a JSON object; throws IllegalArgumentException for anything malformed. */
    fun parseObject(text: String): Map<String, Any?> {
        val parser = Parser(text)
        val value = parser.parseValue(0)
        parser.skipWhitespace()
        require(parser.atEnd()) { "trailing data" }
        @Suppress("UNCHECKED_CAST")
        return (value as? Map<String, Any?>) ?: throw IllegalArgumentException("not an object")
    }

    private fun write(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is String -> writeString(sb, value)
            is Boolean -> sb.append(if (value) "true" else "false")
            is Number -> sb.append(value.toString())
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) sb.append(',')
                    first = false
                    writeString(sb, k.toString())
                    sb.append(':')
                    write(sb, v)
                }
                sb.append('}')
            }
            is List<*> -> {
                sb.append('[')
                value.forEachIndexed { index, item ->
                    if (index > 0) sb.append(',')
                    write(sb, item)
                }
                sb.append(']')
            }
            else -> throw IllegalArgumentException("unsupported type ${value::class}")
        }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c == '\b' -> sb.append("\\b")
                c == '\u000c' -> sb.append("\\f")
                c < ' ' -> sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun atEnd() = i >= s.length

        fun skipWhitespace() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\r' || s[i] == '\n')) i++
        }

        fun parseValue(depth: Int): Any? {
            require(depth <= 32) { "too deeply nested" }
            skipWhitespace()
            require(!atEnd()) { "unexpected end" }
            return when (s[i]) {
                '{' -> parseObject(depth)
                '[' -> parseArray(depth)
                '"' -> parseString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> parseNumber()
            }
        }

        private fun literal(word: String, result: Any?): Any? {
            require(s.startsWith(word, i)) { "bad literal" }
            i += word.length
            return result
        }

        private fun parseObject(depth: Int): Map<String, Any?> {
            val result = LinkedHashMap<String, Any?>()
            i++ // {
            skipWhitespace()
            if (peek() == '}') {
                i++
                return result
            }
            while (true) {
                skipWhitespace()
                require(peek() == '"') { "object key expected" }
                val key = parseString()
                skipWhitespace()
                require(peek() == ':') { "colon expected" }
                i++
                result[key] = parseValue(depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> i++
                    '}' -> {
                        i++
                        return result
                    }
                    else -> throw IllegalArgumentException("bad object")
                }
            }
        }

        private fun parseArray(depth: Int): List<Any?> {
            val result = ArrayList<Any?>()
            i++ // [
            skipWhitespace()
            if (peek() == ']') {
                i++
                return result
            }
            while (true) {
                result.add(parseValue(depth + 1))
                skipWhitespace()
                when (peek()) {
                    ',' -> i++
                    ']' -> {
                        i++
                        return result
                    }
                    else -> throw IllegalArgumentException("bad array")
                }
            }
        }

        private fun peek(): Char {
            require(!atEnd()) { "unexpected end" }
            return s[i]
        }

        private fun parseString(): String {
            i++ // opening quote
            val sb = StringBuilder()
            while (true) {
                require(!atEnd()) { "unterminated string" }
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        require(!atEnd()) { "bad escape" }
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000c')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                require(i + 4 <= s.length) { "bad unicode escape" }
                                sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> throw IllegalArgumentException("bad escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun parseNumber(): Number {
            val start = i
            while (i < s.length && s[i] in "+-0123456789.eE") i++
            require(i > start) { "unexpected character" }
            val text = s.substring(start, i)
            return if (text.any { it == '.' || it == 'e' || it == 'E' }) text.toDouble() else text.toLong()
        }
    }
}
