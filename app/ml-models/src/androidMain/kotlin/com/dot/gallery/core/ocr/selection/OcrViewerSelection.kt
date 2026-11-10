/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.selection

import com.dot.gallery.core.ocr.domain.NormalizedPoint
import com.dot.gallery.core.ocr.domain.OcrDocument
import com.dot.gallery.core.ocr.domain.OcrWord
import kotlin.math.max
import kotlin.math.min

object OcrViewerSelection {
    const val DEFAULT_HYSTERESIS = 0.018f

    fun orderedWords(document: OcrDocument): List<OcrWord> =
        document.lines.asSequence().flatMap { it.words.asSequence() }.sortedBy { it.readingIndex }.toList()

    fun rangeForWord(document: OcrDocument, wordId: String): OcrSelectionRange? {
        val index = orderedWords(document).indexOfFirst { it.id == wordId }
        return index.takeIf { it >= 0 }?.let { OcrSelectionRange(it, it) }
    }

    fun rangeBetween(document: OcrDocument, anchorWordId: String, currentWordId: String): OcrSelectionRange? {
        val words = orderedWords(document)
        val anchor = words.indexOfFirst { it.id == anchorWordId }
        val current = words.indexOfFirst { it.id == currentWordId }
        if (anchor < 0 || current < 0) return null
        return OcrSelectionRange(min(anchor, current), max(anchor, current))
    }

    fun extend(range: OcrSelectionRange, wordIndex: Int, wordCount: Int): OcrSelectionRange? {
        if (wordIndex !in 0 until wordCount) return null
        return OcrSelectionRange(min(range.startIndex, wordIndex), max(range.startIndex, wordIndex))
    }

    fun moveHandle(
        range: OcrSelectionRange,
        handle: OcrSelectionHandle,
        wordIndex: Int,
        wordCount: Int,
    ): OcrSelectionRange? {
        if (wordIndex !in 0 until wordCount) return null
        return when (handle) {
            OcrSelectionHandle.START -> OcrSelectionRange(min(wordIndex, range.endIndex), max(wordIndex, range.endIndex))
            OcrSelectionHandle.END -> OcrSelectionRange(min(range.startIndex, wordIndex), max(range.startIndex, wordIndex))
        }
    }

    fun selectedWordIds(document: OcrDocument, range: OcrSelectionRange?): Set<String> {
        val words = orderedWords(document)
        if (range == null || range.endIndex >= words.size) return emptySet()
        return words.subList(range.startIndex, range.endIndex + 1).mapTo(linkedSetOf(), OcrWord::id)
    }

    /** Uses source offsets so original Unicode separators and line breaks are copied unchanged. */
    fun selectedText(document: OcrDocument, range: OcrSelectionRange?): String {
        val words = orderedWords(document)
        if (range == null || range.endIndex >= words.size) return ""
        val first = words[range.startIndex]
        val last = words[range.endIndex]
        return document.fullText.substring(first.startOffset, last.endOffset)
    }

    fun nearestWordIndex(
        document: OcrDocument,
        point: NormalizedPoint,
        currentIndex: Int?,
        hysteresis: Float = DEFAULT_HYSTERESIS,
    ): Int? {
        val words = orderedWords(document)
        if (words.isEmpty()) return null
        val distances = words.map { word -> distanceSquaredToBounds(word, point) }
        val nearest = distances.indices.minByOrNull(distances::get) ?: return null
        val current = currentIndex?.takeIf { it in words.indices } ?: return nearest
        val margin = hysteresis.coerceAtLeast(0f)
        return if (distances[current] <= distances[nearest] + margin * margin) current else nearest
    }

    private fun distanceSquaredToBounds(word: OcrWord, point: NormalizedPoint): Float {
        val left = word.quad.points.minOf { it.x }
        val right = word.quad.points.maxOf { it.x }
        val top = word.quad.points.minOf { it.y }
        val bottom = word.quad.points.maxOf { it.y }
        val dx = when {
            point.x < left -> left - point.x
            point.x > right -> point.x - right
            else -> 0f
        }
        val dy = when {
            point.y < top -> top - point.y
            point.y > bottom -> point.y - bottom
            else -> 0f
        }
        return dx * dx + dy * dy
    }
}
