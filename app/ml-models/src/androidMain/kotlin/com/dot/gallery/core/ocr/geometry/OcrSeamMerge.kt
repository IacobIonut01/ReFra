/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.geometry

import com.dot.gallery.core.ocr.domain.OcrLine
import com.dot.gallery.core.ocr.domain.OcrWord
import com.dot.gallery.core.ocr.text.OcrTextNormalizer

object OcrSeamMerge {
    fun mergeLines(
        lines: List<OcrLine>,
        iouThreshold: Float = 0.5f,
    ): List<OcrLine> {
        require(iouThreshold.isFinite() && iouThreshold in 0f..1f)
        val merged = ArrayList<OcrLine>()
        val ordered = lines.sortedWith(compareBy(OcrLine::readingIndex, OcrLine::id))
        for (candidate in ordered) {
            val normalized = normalizedDuplicateText(candidate.text)
            val duplicateIndex = merged.indexOfFirst { existing ->
                normalized.isNotEmpty() && normalized == normalizedDuplicateText(existing.text) &&
                    overlap(existing.quad, candidate.quad) >= iouThreshold
            }
            if (duplicateIndex < 0) {
                merged += candidate
            } else {
                val existing = merged[duplicateIndex]
                if (candidate.confidence > existing.confidence ||
                    (candidate.confidence == existing.confidence && candidate.id < existing.id)
                ) {
                    merged[duplicateIndex] = candidate
                }
            }
        }
        return merged
    }

    fun mergeWords(
        words: List<OcrWord>,
        iouThreshold: Float = 0.5f,
    ): List<OcrWord> {
        require(iouThreshold.isFinite() && iouThreshold in 0f..1f)
        val merged = ArrayList<OcrWord>()
        val ordered = words.sortedWith(compareBy(OcrWord::readingIndex, OcrWord::id))
        for (candidate in ordered) {
            val normalized = normalizedDuplicateText(candidate.text)
            val duplicateIndex = merged.indexOfFirst { existing ->
                normalized.isNotEmpty() && normalized == normalizedDuplicateText(existing.text) &&
                    overlap(existing.quad, candidate.quad) >= iouThreshold
            }
            if (duplicateIndex < 0) {
                merged += candidate
            } else {
                val existing = merged[duplicateIndex]
                if (candidate.confidence > existing.confidence ||
                    (candidate.confidence == existing.confidence && candidate.id < existing.id)
                ) {
                    merged[duplicateIndex] = candidate
                }
            }
        }
        return merged
    }

    private fun overlap(
        first: com.dot.gallery.core.ocr.domain.NormalizedQuad,
        second: com.dot.gallery.core.ocr.domain.NormalizedQuad,
    ): Float {
        val firstBounds = OcrGeometry.bounds(first)
        val secondBounds = OcrGeometry.bounds(second)
        val intersection = firstBounds.intersection(secondBounds)?.area ?: 0f
        val minimumArea = minOf(firstBounds.area, secondBounds.area)
        val containment = if (minimumArea > 0f) intersection / minimumArea else 0f
        return maxOf(
            OcrGeometry.polygonIoU(first, second),
            OcrGeometry.boundsIoU(first, second),
            containment,
        )
    }

    private fun normalizedDuplicateText(text: String): String =
        OcrTextNormalizer.normalizeForSearch(text)
}
