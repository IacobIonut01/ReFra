/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.search.tokenizer

import java.io.Closeable
import java.io.Reader

/**
 * Minimal pull-style JSON reader covering the subset `tokenizer.json` needs:
 * objects, arrays, strings (with \uXXXX escapes), numbers, booleans, null.
 * Mirrors the `android.util.JsonReader` surface used by [GemmaTokenizer] so the
 * parser runs identically on Android and in JVM unit tests — and, unlike a
 * DOM parse of the ~32 MB file, streams straight into primitive tables.
 *
 * Note: `hasNext()` is stateful — on a non-empty container it consumes the
 * element separator, so callers must read the element before calling it again.
 */
internal class SimpleJsonReader(input: Reader) : Closeable {

    private val chars = CharArray(8192)
    private var bufferStart = 0
    private var bufferEnd = 0
    private var eof = false
    private var pending = -2 // -2: none; -1: EOF; >=0: pushed-back char
    private val input = input.buffered()

    // Per-container state: EMPTY means no element has been consumed yet.
    private val stack = ArrayDeque<Int>(32)
    private fun pushContainer(emptyState: Int) = stack.addLast(emptyState)
    private fun top() = stack.last()
    private fun replaceTop(state: Int) {
        stack.removeLast()
        stack.addLast(state)
    }

    private fun nextChar(): Int {
        if (pending != -2) {
            val c = pending
            pending = -2
            return c
        }
        if (bufferStart >= bufferEnd) {
            if (eof) return -1
            bufferEnd = input.read(chars)
            bufferStart = 0
            if (bufferEnd == -1) {
                eof = true
                return -1
            }
        }
        return chars[bufferStart++].code
    }

    private fun pushBack(c: Int) {
        pending = c
    }

    private fun skipWhitespace(): Int {
        while (true) {
            val c = nextChar()
            if (c == -1) return -1
            if (!Character.isWhitespace(c)) return c
        }
    }

    private fun expect(expected: Char) {
        val c = skipWhitespace()
        check(c == expected.code) { "Expected '$expected' but found ${if (c == -1) "EOF" else c.toChar()}" }
    }

    private fun takeLiteral(literal: String) {
        for (expected in literal) {
            check(nextChar() == expected.code) { "Invalid literal, expected $literal" }
        }
    }

    private fun takeStringValue(): String {
        // Assumes the opening quote was already consumed.
        val sb = StringBuilder(64)
        while (true) {
            when (val c = nextChar()) {
                -1 -> throw IllegalStateException("Unterminated string")
                '"'.code -> return sb.toString()
                '\\'.code -> when (val esc = nextChar().toChar()) {
                    'u' -> {
                        var code = 0
                        repeat(4) {
                            code = code * 16 + Character.digit(nextChar(), 16)
                        }
                        sb.append(code.toChar())
                    }
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    '/', '\\', '"' -> sb.append(esc)
                    else -> throw IllegalStateException("Invalid escape \\$esc")
                }
                else -> sb.append(c.toChar())
            }
        }
    }

    private fun takeBareToken(): String {
        val sb = StringBuilder()
        var c = skipWhitespace()
        while (c != -1 && !Character.isWhitespace(c) && c != ','.code && c != ']'.code && c != '}'.code) {
            sb.append(c.toChar())
            c = nextChar()
        }
        if (c != -1) pushBack(c)
        return sb.toString()
    }

    // -- Pull API mirroring android.util.JsonReader -----------------------------

    fun beginObject() {
        expect('{')
        pushContainer(EMPTY_OBJECT)
    }

    fun endObject() {
        expect('}')
        check(stack.removeLast().let { it == EMPTY_OBJECT || it == NONEMPTY_OBJECT }) { "Nesting problem" }
    }

    fun beginArray() {
        expect('[')
        pushContainer(EMPTY_ARRAY)
    }

    fun endArray() {
        expect(']')
        check(stack.removeLast().let { it == EMPTY_ARRAY || it == NONEMPTY_ARRAY }) { "Nesting problem" }
    }

    /**
     * True when another element follows. On non-empty containers this consumes the
     * separating comma — do not call twice for one element.
     */
    fun hasNext(): Boolean {
        val c = skipWhitespace()
        when (top()) {
            EMPTY_OBJECT, EMPTY_ARRAY -> {
                val closer = if (top() == EMPTY_OBJECT) '}' else ']'
                if (c == closer.code) {
                    pushBack(c)
                    return false
                }
                pushBack(c)
                replaceTop(if (top() == EMPTY_OBJECT) NONEMPTY_OBJECT else NONEMPTY_ARRAY)
                return true
            }
            NONEMPTY_OBJECT, NONEMPTY_ARRAY -> {
                if (c == ','.code) return true
                // Anything else means malformed JSON or the caller peeked the closer —
                // report no element for a closer, error otherwise.
                val closer = if (top() == NONEMPTY_OBJECT) '}' else ']'
                if (c == closer.code) {
                    pushBack(c)
                    return false
                }
                throw IllegalStateException("Expected ',' but found ${c.toChar()}")
            }
            else -> throw IllegalStateException("Not inside a container")
        }
    }

    fun nextName(): String {
        val c = skipWhitespace()
        check(c == '"'.code) { "Expected object name string" }
        val name = takeStringValue()
        expect(':')
        return name
    }

    fun nextString(): String {
        val c = skipWhitespace()
        check(c == '"'.code) { "Expected string" }
        return takeStringValue()
    }

    fun nextInt(): Int = takeBareToken().toInt()

    fun nextLong(): Long = takeBareToken().toLong()

    /** Skips the next value, whatever its shape. */
    fun skipValue() {
        when (val c = skipWhitespace()) {
            '{'.code -> {
                pushContainer(EMPTY_OBJECT)
                while (hasNext()) {
                    nextName()
                    skipValue()
                }
                endObject()
            }
            '['.code -> {
                pushContainer(EMPTY_ARRAY)
                while (hasNext()) skipValue()
                endArray()
            }
            '"'.code -> takeStringValue()
            't'.code -> takeLiteral("rue")
            'f'.code -> takeLiteral("alse")
            'n'.code -> takeLiteral("ull")
            -1 -> throw IllegalStateException("Unexpected EOF")
            else -> { pushBack(c); takeBareToken() }
        }
    }

    override fun close() = input.close()

    private companion object {
        const val EMPTY_OBJECT = 1
        const val NONEMPTY_OBJECT = 2
        const val EMPTY_ARRAY = 3
        const val NONEMPTY_ARRAY = 4
    }
}
