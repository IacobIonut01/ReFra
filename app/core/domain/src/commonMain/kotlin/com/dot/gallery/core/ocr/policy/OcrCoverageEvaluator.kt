/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.policy

import com.dot.gallery.domain.uri.normalizeNfc

/**
 * Decides whether a recognizer can write a language: every letter of the language's CLDR main
 * exemplar set must be in the recognizer's character dictionary.
 *
 * Letters are compared after NFC. A multi-character exemplar (a cluster such as Dutch `íj́`) passes
 * when each of its NFC characters is in the dictionary. The modifier and right single quotation marks
 * count as the ASCII apostrophe, which is what the recognizers emit for Ukrainian `ʼ`.
 */
object OcrCoverageEvaluator {
    private val apostrophes = mapOf("\u02BC" to "'", "\u2019" to "'")

    /** Characters of a PaddleOCR `inference.yml` dictionary (`PostProcess.character_dict`). */
    fun parseDictionary(yml: String): Set<String> {
        val out = LinkedHashSet<String>()
        var inside = false
        for (raw in yml.lineSequence()) {
            val line = raw.trimEnd('\r')
            if (!inside) {
                if (line.trim() == "character_dict:") inside = true
                continue
            }
            val match = ENTRY.matchEntire(line) ?: break
            out += unquote(match.groupValues[1])
        }
        return out
    }

    /** Elements of a CLDR exemplar set such as `[a á b {ch} c-e]`. */
    fun parseExemplars(set: String): Set<String> {
        val body = set.trim().removePrefix("[").removeSuffix("]")
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < body.length) {
            val c = body[i]
            when {
                c.isWhitespace() -> i++
                c == '{' -> {
                    val end = body.indexOf('}', i)
                    tokens += "{" + body.substring(i + 1, end)
                    i = end + 1
                }
                c == '\\' && i + 1 < body.length && body[i + 1] == 'u' -> {
                    tokens += body.substring(i + 2, i + 6).toInt(16).toChar().toString()
                    i += 6
                }
                c == '\\' -> {
                    tokens += body[i + 1].toString()
                    i += 2
                }
                c.isHighSurrogate() && i + 1 < body.length -> {
                    tokens += body.substring(i, i + 2)
                    i += 2
                }
                else -> {
                    tokens += c.toString()
                    i++
                }
            }
        }
        val out = LinkedHashSet<String>()
        var k = 0
        while (k < tokens.size) {
            val t = tokens[k]
            if (t.startsWith("{")) {
                out += t.substring(1)
                k++
            } else if (k + 2 < tokens.size && tokens[k + 1] == "-" && !tokens[k + 2].startsWith("{")) {
                for (code in t[0].code..tokens[k + 2][0].code) out += code.toChar().toString()
                k += 3
            } else {
                out += t
                k++
            }
        }
        return out
    }

    /** Exemplar letters missing from [dictionary], after NFC, apostrophe folding and the allowed [exceptions]. */
    fun missing(exemplars: Set<String>, dictionary: Set<String>, exceptions: String = ""): List<String> {
        val folded = dictionary.mapTo(HashSet()) { apostrophes[it] ?: it }
        val allowed = exceptions.map { it.toString() }.toSet()
        fun has(text: String): Boolean {
            val f = apostrophes[text] ?: text
            return f in folded || f.normalizeNfc() in folded
        }
        return exemplars.filter { letter ->
            when {
                letter in allowed || has(letter) -> false
                letter.length > 1 -> letter.normalizeNfc().map { it.toString() }.any { it !in allowed && !has(it) }
                else -> true
            }
        }.sorted()
    }

    private val ENTRY = Regex("^ {2}- (.*)$")

    private fun unquote(value: String): String =
        if (value.length >= 2 && value.first() == '\'' && value.last() == '\'') {
            value.substring(1, value.length - 1).replace("''", "'")
        } else {
            value
        }
}
