/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.text

import com.dot.gallery.core.ocr.domain.OcrLimits
import kotlin.math.exp

/** Pure greedy CTC decoder for time-major flattened logits. */
class CtcDecoder(
    private val dictionary: List<String>,
    private val blankIndex: Int,
    private val probabilities: Boolean = false,
) {
    fun decode(logits: FloatArray, timeSteps: Int, classCount: Int): CtcDecodeResult? =
        decode(logits, timeSteps, classCount, dictionary, blankIndex, probabilities)

    companion object {
        fun decode(
            logits: FloatArray,
            timeSteps: Int,
            classCount: Int,
            dictionary: List<String>,
            blankIndex: Int = classCount - 1,
            probabilities: Boolean = false,
            reverseVisualOrder: Boolean = false,
        ): CtcDecodeResult? {
            if (timeSteps <= 0 || classCount <= 1 || blankIndex !in 0 until classCount) return null
            val expected = timeSteps.toLong() * classCount.toLong()
            if (expected > Int.MAX_VALUE || logits.size.toLong() != expected) return null
            if (dictionary.size !in setOf(classCount, classCount - 1, classCount - 2)) return null
            if (logits.any { !it.isFinite() }) return null

            val raw = ArrayList<Run>()
            var active: Run? = null
            for (time in 0 until timeSteps) {
                val row = time * classCount
                var winner = 0
                var winnerLogit = logits[row]
                for (classIndex in 1 until classCount) {
                    val value = logits[row + classIndex]
                    if (value > winnerLogit) {
                        winner = classIndex
                        winnerLogit = value
                    }
                }
                val confidence = if (probabilities) {
                    winnerLogit.takeIf { it in 0f..1f }
                } else {
                    softmaxConfidence(logits, row, classCount, winner, winnerLogit)
                } ?: return null

                if (winner == blankIndex) {
                    active?.let(raw::add)
                    active = null
                    continue
                }
                val tokenText = dictionaryToken(dictionary, classCount, blankIndex, winner)
                    ?: return null
                if (tokenText.isEmpty()) return null
                val current = active
                if (current != null && current.classIndex == winner) {
                    current.timestepEnd = time + 1
                    current.confidenceSum += confidence
                    current.confidenceCount++
                } else {
                    current?.let(raw::add)
                    if (raw.size >= OcrLimits.MAX_TOKENS) return null
                    active = Run(winner, tokenText, time, time + 1, confidence, 1)
                }
            }
            active?.let(raw::add)
            if (raw.size > OcrLimits.MAX_TOKENS) return null

            // Reordered before combining marks are attached: in visual order a mark precedes its base.
            val logical = if (reverseVisualOrder && raw.any { hasRightToLeftText(it.text) }) {
                visualToLogical(raw)
            } else {
                raw
            }
            val grouped = groupCombiningMarks(logical)
            val textCodePoints = grouped.sumOf { it.text.codePointCount(0, it.text.length) }
            if (textCodePoints > OcrLimits.MAX_TEXT_CODE_POINTS) return null

            var offset = 0
            val tokens = grouped.map { run ->
                val start = offset
                offset += run.text.length
                CtcToken(
                    classIndex = run.classIndex,
                    text = run.text,
                    timestepStart = run.timestepStart,
                    timestepEnd = run.timestepEnd,
                    confidence = (run.confidenceSum / run.confidenceCount).coerceIn(0f, 1f),
                    startOffset = start,
                    endOffset = offset,
                )
            }
            val text = tokens.joinToString(separator = "", transform = CtcToken::text)
            return CtcDecodeResult(text, tokens, groupWords(text, tokens), timeSteps)
        }

        private fun dictionaryToken(
            dictionary: List<String>,
            classCount: Int,
            blankIndex: Int,
            classIndex: Int,
        ): String? {
            if (dictionary.size == classCount) return dictionary.getOrNull(classIndex)
            val dictionaryIndex = if (classIndex < blankIndex) classIndex else classIndex - 1
            if (dictionary.size == classCount - 2 && dictionaryIndex == dictionary.size) return " "
            return dictionary.getOrNull(dictionaryIndex)
        }

        private fun softmaxConfidence(
            logits: FloatArray,
            row: Int,
            classCount: Int,
            winner: Int,
            maxLogit: Float,
        ): Float? {
            var denominator = 0.0
            for (classIndex in 0 until classCount) {
                denominator += exp((logits[row + classIndex] - maxLogit).toDouble())
            }
            if (!denominator.isFinite() || denominator <= 0.0) return null
            val confidence = exp((logits[row + winner] - maxLogit).toDouble()) / denominator
            return confidence.toFloat().takeIf(Float::isFinite)
        }

        /**
         * CTC reads a crop left to right, so right-to-left recognizers (PaddleOCR's Arabic models are
         * trained on reversed labels) emit visual order. This is PaddleOCR's `pred_reverse`: runs of
         * characters in `[a-zA-Z0-9 :*./%+-]` stay in order as one chunk, every other character is a
         * chunk of its own, and the chunk order is reversed.
         */
        private fun visualToLogical(runs: List<Run>): List<Run> {
            val chunks = ArrayList<List<Run>>()
            var current = ArrayList<Run>()
            for (run in runs) {
                if (run.text.all(::keepsLeftToRight)) {
                    current += run
                } else {
                    if (current.isNotEmpty()) {
                        chunks += current
                        current = ArrayList()
                    }
                    chunks += listOf(run)
                }
            }
            if (current.isNotEmpty()) chunks += current
            return chunks.asReversed().flatten()
        }

        private fun keepsLeftToRight(char: Char): Boolean =
            char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char in " :*./%+-"

        /** Whether [text] has right-to-left letters (Arabic, Hebrew and the like). */
        fun hasRightToLeftText(text: String): Boolean = text.codePoints().anyMatch { codePoint ->
            when (Character.getDirectionality(codePoint)) {
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> true
                else -> false
            }
        }

        private fun groupCombiningMarks(runs: List<Run>): List<Run> {
            val grouped = ArrayList<Run>(runs.size)
            for (run in runs) {
                val first = run.text.codePointAt(0)
                if (isCombiningMark(first) && grouped.isNotEmpty()) {
                    val previous = grouped.last()
                    previous.text += run.text
                    previous.timestepEnd = maxOf(previous.timestepEnd, run.timestepEnd)
                    previous.confidenceSum += run.confidenceSum
                    previous.confidenceCount += run.confidenceCount
                } else {
                    grouped += run.copy()
                }
            }
            return grouped
        }

        private fun groupWords(text: String, tokens: List<CtcToken>): List<CtcWord> {
            val hasDelimiter = tokens.any { token ->
                token.text.codePoints().anyMatch(Character::isWhitespace)
            }
            if (!hasDelimiter) {
                return tokens.filterNot { it.text.isBlank() }.mapIndexed { index, token ->
                    CtcWord(
                        text = token.text,
                        startOffset = token.startOffset,
                        endOffset = token.endOffset,
                        tokenStart = index,
                        tokenEnd = index + 1,
                        confidence = token.confidence,
                    )
                }
            }

            return wordRanges(text).mapNotNull { range ->
                val startOffset = range.first
                val endOffset = range.last + 1
                val overlapping = tokens.withIndex().filter { (_, token) ->
                    token.endOffset > startOffset && token.startOffset < endOffset
                }
                val first = overlapping.firstOrNull() ?: return@mapNotNull null
                CtcWord(
                    text = text.substring(startOffset, endOffset),
                    startOffset = startOffset,
                    endOffset = endOffset,
                    tokenStart = first.index,
                    tokenEnd = overlapping.last().index + 1,
                    confidence = overlapping.map { it.value.confidence }.average().toFloat().coerceIn(0f, 1f),
                )
            }
        }

        private fun wordRanges(text: String): List<IntRange> {
            val ranges = ArrayList<IntRange>()
            var wordStart = -1
            var offset = 0
            while (offset < text.length) {
                val codePoint = text.codePointAt(offset)
                val whitespace = Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)
                if (whitespace && wordStart >= 0) {
                    ranges += wordStart until offset
                    wordStart = -1
                } else if (!whitespace && wordStart < 0) {
                    wordStart = offset
                }
                offset += Character.charCount(codePoint)
            }
            if (wordStart >= 0) ranges += wordStart until text.length
            return ranges
        }

        private fun isCombiningMark(codePoint: Int): Boolean = when (Character.getType(codePoint)) {
            Character.NON_SPACING_MARK.toInt(),
            Character.COMBINING_SPACING_MARK.toInt(),
            Character.ENCLOSING_MARK.toInt(), -> true
            else -> false
        }

        private data class Run(
            val classIndex: Int,
            var text: String,
            val timestepStart: Int,
            var timestepEnd: Int,
            var confidenceSum: Float,
            var confidenceCount: Int,
        )
    }
}
