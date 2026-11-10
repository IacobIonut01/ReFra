/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr

import com.dot.gallery.core.ocr.text.CtcDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CtcDecoderTest {
    @Test
    fun collapsesBlanksRepeatsAndCombiningMarksWithOffsets() {
        val dictionary = listOf("a", "b", " ", "\u0301", "")
        val winners = intArrayOf(0, 0, 4, 0, 3, 2, 1, 1)
        val result = CtcDecoder.decode(logits(winners, dictionary.size), winners.size, dictionary.size, dictionary, 4)!!

        assertEquals("aa\u0301 b", result.text)
        assertEquals(listOf("a", "a\u0301", " ", "b"), result.tokens.map { it.text })
        assertEquals(0, result.tokens[0].timestepStart)
        assertEquals(2, result.tokens[0].timestepEnd)
        assertEquals(1, result.tokens[1].startOffset)
        assertEquals(3, result.tokens[1].endOffset)
        assertEquals(listOf("aa\u0301", "b"), result.words.map { it.text })
        assertTrue(result.tokens.all { it.confidence in 0f..1f })
    }

    @Test
    fun paddleDictionaryAddsImplicitTrailingSpaceClass() {
        val dictionary = listOf("a", "b")
        val probabilities = floatArrayOf(
            0.05f, 0.9f, 0.03f, 0.02f,
            0.05f, 0.02f, 0.03f, 0.9f,
            0.05f, 0.02f, 0.9f, 0.03f
        )
        val result = CtcDecoder.decode(
            probabilities,
            timeSteps = 3,
            classCount = 4,
            dictionary = dictionary,
            blankIndex = 0,
            probabilities = true
        )!!

        assertEquals("a b", result.text)
    }

    @Test
    fun noSpaceRecognitionGroupsEachTokenAsAWord() {
        val dictionary = listOf("你", "好", "")
        val winners = intArrayOf(0, 2, 1)
        val result = CtcDecoder.decode(logits(winners, 3), 3, 3, dictionary, 2)!!

        assertEquals("你好", result.text)
        assertEquals(listOf("你", "好"), result.words.map { it.text })
        assertEquals(listOf(0, 1), result.words.map { it.startOffset })
    }

    @Test
    fun preservesRecognizerProbabilitiesWithoutSecondSoftmax() {
        val probabilities = floatArrayOf(0.05f, 0.9f, 0.05f)
        val result = CtcDecoder.decode(
            probabilities,
            timeSteps = 1,
            classCount = 3,
            dictionary = listOf("a", "b", ""),
            blankIndex = 2,
            probabilities = true
        )!!

        assertEquals("b", result.text)
        assertEquals(0.9f, result.tokens.single().confidence, 0.0001f)
    }

    @Test
    fun rejectsMalformedDimensionsAndNonFiniteLogits() {
        assertNull(CtcDecoder.decode(FloatArray(5), 2, 3, listOf("a", "b", ""), 2))
        val malformed = logits(intArrayOf(0), 2)
        malformed[0] = Float.NaN
        assertNull(CtcDecoder.decode(malformed, 1, 2, listOf("a", ""), 1))
    }

    @Test
    fun rightToLeftOutputIsReorderedFromVisualToLogicalOrder() {
        // The Arabic recognizer reads the crop left to right: "لا بك" comes out as ك ب ␠ ا ل.
        val dictionary = listOf("ك", "ب", " ", "ا", "ل", "")
        val winners = intArrayOf(0, 0, 1, 5, 2, 3, 4)
        val result = CtcDecoder.decode(
            logits(winners, dictionary.size), winners.size, dictionary.size, dictionary, 5,
            reverseVisualOrder = true,
        )!!

        assertEquals("لا بك", result.text)
        assertEquals(listOf("لا", "بك"), result.words.map { it.text })
        // Word boxes follow the time steps, so the logically first word is drawn on the right.
        val (firstStart, firstEnd) = result.wordSpan(result.words[0], contentFraction = 1f)
        assertEquals(5f / 7f, firstStart, 0.0001f)
        assertEquals(1f, firstEnd, 0.0001f)
        val (lastStart, lastEnd) = result.wordSpan(result.words[1], contentFraction = 1f)
        assertEquals(0f, lastStart, 0.0001f)
        assertEquals(3f / 7f, lastEnd, 0.0001f)
    }

    @Test
    fun visualOrderKeepsCombiningMarksOnTheirBase() {
        // Labels are reversed for training, so a mark is emitted before its base letter.
        val dictionary = listOf("َ", "ب", "")
        val result = CtcDecoder.decode(logits(intArrayOf(0, 1), 3), 2, 3, dictionary, 2, reverseVisualOrder = true)!!
        assertEquals("بَ", result.text)
        assertEquals(1, result.tokens.size)
    }

    @Test
    fun linesWithoutRightToLeftLettersAreNotReordered() {
        val dictionary = listOf("a", "b", ",", " ", "c", "")
        val winners = intArrayOf(0, 1, 2, 3, 4)
        val result = CtcDecoder.decode(
            logits(winners, dictionary.size), winners.size, dictionary.size, dictionary, 5,
            reverseVisualOrder = true,
        )!!
        assertEquals("ab, c", result.text)
    }

    @Test
    fun wordSpanAccountsForPaddingAfterTheText() {
        val dictionary = listOf("a", "b", " ", "")
        // "a b" fills the first half of a padded input of 8 time steps.
        val winners = intArrayOf(0, 2, 1, 3, 3, 3, 3, 3)
        val result = CtcDecoder.decode(logits(winners, dictionary.size), winners.size, dictionary.size, dictionary, 3)!!
        val (start, end) = result.wordSpan(result.words[1], contentFraction = 0.5f)
        assertEquals(0.5f, start, 0.0001f)
        assertEquals(0.75f, end, 0.0001f)
    }

    private fun logits(winners: IntArray, classes: Int): FloatArray = FloatArray(winners.size * classes) { index ->
        if (index % classes == winners[index / classes]) 5f else -5f
    }
}
