/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.text

import com.dot.gallery.core.ocr.domain.OcrLimits
import java.text.Normalizer
import java.util.Locale

/** JVM/Android-compatible normalization without requiring ICU4J. */
object OcrTextNormalizer {
    /**
     * NFKC followed by locale-independent lowercase. This deliberately approximates Unicode case
     * folding: unlike full ICU folding, context-sensitive lowercase forms such as final sigma are
     * retained and multi-character CaseFolding.txt mappings are not guaranteed.
     */
    fun caseFold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT)

    fun normalizeForSearch(text: String): String = collapseWhitespace(
        buildString(text.length) {
            caseFold(text).codePoints().forEach { codePoint ->
                when {
                    Character.isISOControl(codePoint) -> append(' ')
                    isUnsafeFormat(codePoint) -> Unit
                    else -> appendCodePoint(codePoint)
                }
            }
        },
    )

    fun sanitizeQuery(
        query: String,
        maxCodePoints: Int = OcrLimits.MAX_QUERY_CODE_POINTS,
    ): String {
        require(maxCodePoints >= 0)
        return truncateCodePoints(normalizeForSearch(query), maxCodePoints).trim()
    }

    fun truncateCodePoints(text: String, maxCodePoints: Int): String {
        require(maxCodePoints >= 0)
        if (text.codePointCount(0, text.length) <= maxCodePoints) return text
        val end = text.offsetByCodePoints(0, maxCodePoints)
        return text.substring(0, end)
    }

    /**
     * Produces an FTS4 `MATCH` expression in which every user token is quoted and matched as a prefix.
     * FTS4 reads the prefix star only inside the quotes (`"tok*"`; a star after the closing quote is
     * ignored), and its standard syntax has no `AND` operator (the word would be searched for), so the
     * tokens are joined by spaces, which FTS4 treats as AND in both its standard and enhanced syntax.
     * Quotes inside a token are dropped: FTS4 phrases have no escape for them.
     */
    fun quotedPrefixExpression(query: String): String {
        val sanitized = sanitizeQuery(query)
        if (sanitized.isEmpty()) return ""
        return sanitized.split(' ')
            .asSequence()
            .map { it.replace("\"", "").replace("*", "") }
            .filter(String::isNotEmpty)
            .joinToString(" ") { token -> "\"$token*\"" }
    }

    /**
     * Generates code-point-safe n-grams for scripts whose OCR output normally has no spaces.
     * Results are de-duplicated in encounter order and hard bounded to protect the indexer.
     */
    fun boundedNGrams(
        text: String,
        minGram: Int = 2,
        maxGram: Int = 3,
        maxTokens: Int = OcrLimits.MAX_TOKENS,
    ): List<String> {
        require(minGram > 0 && maxGram >= minGram)
        require(maxTokens in 0..OcrLimits.MAX_TOKENS)
        if (maxTokens == 0) return emptyList()

        val normalized = normalizeForSearch(text)
        val codePoints = normalized.codePoints().toArray()
            .filterNot { Character.isWhitespace(it) }
            .toIntArray()
        if (codePoints.size < minGram) return emptyList()

        val result = LinkedHashSet<String>(minOf(maxTokens, codePoints.size * 2))
        val effectiveMin = minGram
        val effectiveMax = minOf(maxGram, codePoints.size)
        for (size in effectiveMin..effectiveMax) {
            for (start in 0..codePoints.size - size) {
                result += String(codePoints, start, size)
                if (result.size == maxTokens) return result.toList()
            }
        }
        return result.toList()
    }

    fun indexTokens(
        text: String,
        maxTokens: Int = OcrLimits.MAX_TOKENS,
    ): List<String> {
        require(maxTokens in 0..OcrLimits.MAX_TOKENS)
        val normalized = normalizeForSearch(text)
        if (normalized.isEmpty() || maxTokens == 0) return emptyList()
        return if (normalized.any(Character::isWhitespace)) {
            normalized.split(' ').filter(String::isNotEmpty).distinct().take(maxTokens)
        } else {
            boundedNGrams(normalized, maxTokens = maxTokens)
        }
    }

    private fun collapseWhitespace(text: String): String = buildString(text.length) {
        var pendingSpace = false
        text.codePoints().forEach { codePoint ->
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                pendingSpace = isNotEmpty()
            } else {
                if (pendingSpace) append(' ')
                appendCodePoint(codePoint)
                pendingSpace = false
            }
        }
    }

    private fun isUnsafeFormat(codePoint: Int): Boolean = when (codePoint) {
        0x200C, 0x200D -> false // Preserve ZWNJ/ZWJ used by natural-language graphemes.
        else -> Character.getType(codePoint) == Character.FORMAT.toInt()
    }
}

fun String.toOcrSearchText(): String = OcrTextNormalizer.normalizeForSearch(this)
