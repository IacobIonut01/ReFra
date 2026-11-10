/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.text

data class CtcDecodeResult(
    val text: String,
    val tokens: List<CtcToken>,
    val words: List<CtcWord>,
    /** Time steps of the decoded output; they span the whole (padded) recognizer input width. */
    val timeSteps: Int = 0,
) {
    /**
     * Where [word] sits along the line, as start and end fractions of the text's width, from the time
     * steps of its tokens. [contentFraction] is the share of the input width the text filled before
     * padding. Token order does not matter, so right-to-left words (tokens in logical order, time
     * steps running backwards) land where they are drawn.
     */
    fun wordSpan(word: CtcWord, contentFraction: Float): Pair<Float, Float> {
        val wordTokens = tokens.subList(word.tokenStart.coerceIn(0, tokens.size), word.tokenEnd.coerceIn(0, tokens.size))
        if (wordTokens.isEmpty() || timeSteps <= 0 || !(contentFraction > 0f)) return 0f to 1f
        val start = wordTokens.minOf(CtcToken::timestepStart).toFloat() / timeSteps / contentFraction
        val end = wordTokens.maxOf(CtcToken::timestepEnd).toFloat() / timeSteps / contentFraction
        return start.coerceIn(0f, 1f) to end.coerceIn(0f, 1f)
    }
}
