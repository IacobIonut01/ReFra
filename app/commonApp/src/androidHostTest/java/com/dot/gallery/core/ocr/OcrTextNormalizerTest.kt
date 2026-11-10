/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr

import com.dot.gallery.core.ocr.text.OcrTextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrTextNormalizerTest {
    @Test
    fun appliesNfkcRootLowercaseAndWhitespaceSanitization() {
        assertEquals("foo bar", OcrTextNormalizer.normalizeForSearch(" ＦＯＯ\u0000\tBAR "))
    }

    @Test
    fun queryTruncationNeverSplitsSurrogatePairs() {
        val query = "a😀bc"
        assertEquals("a😀", OcrTextNormalizer.sanitizeQuery(query, maxCodePoints = 2))
        assertFalse(OcrTextNormalizer.sanitizeQuery("x\u0000y").contains('\u0000'))
    }

    @Test
    fun createsQuotedPrefixExpressionWithoutRawOperators() {
        // FTS4 syntax: the star sits inside the quotes and tokens are joined by spaces (implicit AND);
        // `"foo"* AND "bar"*` matched exact tokens only and searched for the word "and".
        assertEquals("\"foo*\" \"bar*\"", OcrTextNormalizer.quotedPrefixExpression("Foo BAR"))
    }

    @Test
    fun prefixExpressionDropsQuotesAndStarsThatWouldBreakThePhrase() {
        assertEquals("\"ab*\" \"c*\"", OcrTextNormalizer.quotedPrefixExpression("a\"b c* \"*\""))
    }

    @Test
    fun noSpaceNgramsAreCodePointSafeDeduplicatedAndBounded() {
        val grams = OcrTextNormalizer.boundedNGrams("你好世界", minGram = 2, maxGram = 3, maxTokens = 4)
        assertEquals(listOf("你好", "好世", "世界", "你好世"), grams)
        assertTrue(grams.all { it.codePointCount(0, it.length) in 2..3 })
    }

    @Test
    fun jvmLowercaseDocumentsDifferenceFromFullIcuCaseFolding() {
        // Full Unicode case folding maps both sigma forms to σ. Locale.ROOT lowercase retains the
        // context-sensitive final sigma; this is the intentional dependency-free limitation.
        assertEquals("ος", OcrTextNormalizer.caseFold("ΟΣ"))
        assertTrue(OcrTextNormalizer.caseFold("ΟΣ").endsWith("ς"))
    }
}
