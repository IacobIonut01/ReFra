package com.dot.gallery.editor.core.project

/**
 * The hand-rolled JSON machinery shared by the project codecs and the
 * recents index — the document model carries no serialization dependency,
 * so this stays a small writer/parser pair: keys land in declaration
 * order, numbers round-trip through their shortest representation, and
 * the same snapshot always encodes to the same bytes.
 */
object ProjectJson {

    // ----- encoding -----------------------------------------------------

    /**
     * Compact JSON writer: commas come from a per-container "first
     * element" stack, so nesting never needs the caller to track
     * separators. Keys are emitted by the call sites in declaration
     * order — that is what makes the output deterministic.
     */
    class Writer {
        private val out = StringBuilder()
        private val firstElement = ArrayDeque<Boolean>()

        /** Set by [key] — the value that follows is not a new element. */
        private var afterKey = false

        private fun beforeElement() {
            if (afterKey) {
                afterKey = false
                return
            }
            if (firstElement.isNotEmpty()) {
                if (firstElement.last()) {
                    firstElement.removeLast()
                    firstElement.addLast(false)
                } else {
                    out.append(',')
                }
            }
        }

        fun beginObject(): Writer {
            beforeElement()
            out.append('{')
            firstElement.addLast(true)
            return this
        }

        fun endObject(): Writer {
            firstElement.removeLast()
            out.append('}')
            return this
        }

        fun beginArray(): Writer {
            beforeElement()
            out.append('[')
            firstElement.addLast(true)
            return this
        }

        fun endArray(): Writer {
            firstElement.removeLast()
            out.append(']')
            return this
        }

        fun key(name: String): Writer {
            beforeElement()
            string(name)
            out.append(':')
            afterKey = true
            return this
        }

        fun value(value: String): Writer {
            beforeElement()
            string(value)
            return this
        }

        fun stringOrNull(value: String?): Writer {
            beforeElement()
            if (value == null) out.append("null") else string(value)
            return this
        }

        fun nullValue(): Writer {
            beforeElement()
            out.append("null")
            return this
        }

        fun value(value: Long): Writer {
            beforeElement()
            out.append(value)
            return this
        }

        fun value(value: Int): Writer {
            beforeElement()
            out.append(value)
            return this
        }

        fun value(value: Double): Writer {
            beforeElement()
            out.append(value)
            return this
        }

        fun value(value: Float): Writer {
            beforeElement()
            out.append(value)
            return this
        }

        fun value(value: Boolean): Writer {
            beforeElement()
            out.append(value)
            return this
        }

        private fun string(value: String) {
            out.append('"')
            for (char in value) {
                when (char) {
                    '"' -> out.append("\\\"")
                    '\\' -> out.append("\\\\")
                    '\n' -> out.append("\\n")
                    '\r' -> out.append("\\r")
                    '\t' -> out.append("\\t")
                    else -> {
                        if (char < ' ') {
                            out.append("\\u")
                            out.append(char.code.toString(16).padStart(4, '0'))
                        } else {
                            out.append(char)
                        }
                    }
                }
            }
            out.append('"')
        }

        override fun toString(): String = out.toString()
    }

    // ----- decoding -----------------------------------------------------

    sealed interface Value

    object Null : Value

    class Object(private val fields: Map<String, Value>) : Value {

        /**
         * [name]'s value, or null when absent or an explicit JSON null —
         * a stored null and a missing key both mean "use the default".
         */
        private fun member(name: String, required: Boolean): Value? {
            val value = fields[name]
            if ((value == null || value is Null) && required) {
                throw IllegalArgumentException("missing required key: $name")
            }
            return if (value is Null) null else value
        }

        fun optObj(name: String): Object? =
            when (val v = member(name, required = false)) {
                null -> null
                is Object -> v
                else -> typeError(name, "object")
            }

        fun reqObj(name: String): Object =
            optObj(name) ?: missing(name)

        fun reqArray(name: String): Array =
            when (val v = member(name, required = true)) {
                is Array -> v
                else -> typeError(name, "array")
            }

        /** [name]'s array, or null when absent/null — decode-time default. */
        fun optArray(name: String): Array? =
            when (val v = member(name, required = false)) {
                null -> null
                is Array -> v
                else -> typeError(name, "array")
            }

        fun optString(name: String): String? =
            when (val v = member(name, required = false)) {
                null -> null
                is Str -> v.value
                else -> typeError(name, "string")
            }

        fun reqString(name: String): String =
            optString(name) ?: missing(name)

        fun optBoolean(name: String): Boolean? =
            when (val v = member(name, required = false)) {
                null -> null
                is Bool -> v.value
                else -> typeError(name, "boolean")
            }

        fun reqBoolean(name: String): Boolean =
            optBoolean(name) ?: missing(name)

        fun optLong(name: String): Long? =
            when (val v = member(name, required = false)) {
                null -> null
                is Num -> v.long(name)
                else -> typeError(name, "number")
            }

        fun reqLong(name: String): Long =
            optLong(name) ?: missing(name)

        fun optInt(name: String): Int? =
            optLong(name)?.let {
                require(it in Int.MIN_VALUE..Int.MAX_VALUE) {
                    "key $name overflows Int: $it"
                }
                it.toInt()
            }

        fun reqInt(name: String): Int =
            optInt(name) ?: missing(name)

        fun optFloat(name: String): Float? =
            when (val v = member(name, required = false)) {
                null -> null
                is Num -> v.float(name)
                else -> typeError(name, "number")
            }

        fun optDouble(name: String): Double? =
            when (val v = member(name, required = false)) {
                null -> null
                is Num -> v.double(name)
                else -> typeError(name, "number")
            }

        private fun missing(name: String): Nothing =
            throw IllegalArgumentException("missing required key: $name")

        private fun typeError(name: String, expected: String): Nothing =
            throw IllegalArgumentException("key $name is not a $expected")
    }

    class Array(val values: List<Value>) : Value
    class Str(val value: String) : Value
    class Bool(val value: Boolean) : Value

    class Num(val text: String) : Value {
        fun long(name: String): Long = text.toLongOrNull()
            ?: throw IllegalArgumentException("key $name is not a long: $text")

        fun float(name: String): Float = text.toFloatOrNull()
            ?: throw IllegalArgumentException("key $name is not a float: $text")

        fun double(name: String): Double = text.toDoubleOrNull()
            ?: throw IllegalArgumentException("key $name is not a double: $text")
    }

    /** Parses [text] — strict RFC 8259; trailing content fails. */
    fun parse(text: String): Value = Parser(text).parseRoot()

    /** Recursive-descent JSON parser — strict RFC 8259. */
    private class Parser(private val text: String) {
        private var index = 0

        fun parseRoot(): Value {
            skipWhitespace()
            val value = parseValue()
            skipWhitespace()
            if (index != text.length) {
                fail("trailing content at $index")
            }
            return value
        }

        private fun parseValue(): Value {
            if (index >= text.length) fail("unexpected end of input")
            return when (text[index]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> Str(parseString())
                't' -> { expect("true"); Bool(true) }
                'f' -> { expect("false"); Bool(false) }
                'n' -> { expect("null"); Null }
                else -> parseNumber()
            }
        }

        private fun parseObject(): Object {
            expectChar('{')
            val fields = LinkedHashMap<String, Value>()
            skipWhitespace()
            if (peek('}')) {
                index++
                return Object(fields)
            }
            while (true) {
                skipWhitespace()
                val name = parseString()
                skipWhitespace()
                expectChar(':')
                skipWhitespace()
                fields[name] = parseValue()
                skipWhitespace()
                when {
                    peek(',') -> index++
                    peek('}') -> {
                        index++
                        return Object(fields)
                    }
                    else -> fail("expected ',' or '}' at $index")
                }
            }
        }

        private fun parseArray(): Array {
            expectChar('[')
            val values = ArrayList<Value>()
            skipWhitespace()
            if (peek(']')) {
                index++
                return Array(values)
            }
            while (true) {
                skipWhitespace()
                values += parseValue()
                skipWhitespace()
                when {
                    peek(',') -> index++
                    peek(']') -> {
                        index++
                        return Array(values)
                    }
                    else -> fail("expected ',' or ']' at $index")
                }
            }
        }

        private fun parseString(): String {
            expectChar('"')
            val out = StringBuilder()
            while (index < text.length) {
                when (val char = text[index++]) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (index >= text.length) fail("unterminated escape")
                        when (val esc = text[index++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (index + 4 > text.length) {
                                    fail("truncated \\u escape")
                                }
                                val code = text.substring(index, index + 4)
                                    .toIntOrNull(16)
                                    ?: fail("bad \\u escape")
                                out.append(code.toChar())
                                index += 4
                            }
                            else -> fail("bad escape: \\$esc")
                        }
                    }
                    else -> out.append(char)
                }
            }
            fail("unterminated string")
        }

        private fun parseNumber(): Num {
            val start = index
            if (peek('-')) index++
            var digits = 0
            while (index < text.length && text[index].isDigit()) {
                index++
                digits++
            }
            if (digits == 0) fail("expected a value at $start")
            if (peek('.')) {
                index++
                if (index >= text.length || !text[index].isDigit()) {
                    fail("bad fraction at $index")
                }
                while (index < text.length && text[index].isDigit()) index++
            }
            if (peek('e') || peek('E')) {
                index++
                if (peek('+') || peek('-')) index++
                if (index >= text.length || !text[index].isDigit()) {
                    fail("bad exponent at $index")
                }
                while (index < text.length && text[index].isDigit()) index++
            }
            return Num(text.substring(start, index))
        }

        private fun expect(literal: String) {
            if (!text.startsWith(literal, index)) {
                fail("expected '$literal' at $index")
            }
            index += literal.length
        }

        private fun expectChar(char: Char) {
            if (index >= text.length || text[index] != char) {
                fail("expected '$char' at $index")
            }
            index++
        }

        private fun peek(char: Char): Boolean =
            index < text.length && text[index] == char

        private fun skipWhitespace() {
            while (index < text.length &&
                (text[index] == ' ' || text[index] == '\t' ||
                    text[index] == '\n' || text[index] == '\r')
            ) {
                index++
            }
        }

        private fun fail(message: String): Nothing =
            throw IllegalArgumentException("malformed project JSON: $message")
    }
}
