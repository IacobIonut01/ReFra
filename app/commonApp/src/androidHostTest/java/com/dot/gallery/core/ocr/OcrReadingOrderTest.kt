/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr

import com.dot.gallery.core.ocr.domain.NormalizedQuad
import com.dot.gallery.core.ocr.domain.OcrLine
import com.dot.gallery.core.ocr.geometry.OcrReadingOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrReadingOrderTest {
    @Test
    fun groupsBasicColumnsDeterministically() {
        val lines = listOf(
            line("right-bottom", 0.75f, 0.6f),
            line("left-bottom", 0.1f, 0.6f),
            line("right-top", 0.75f, 0.1f),
            line("left-top", 0.1f, 0.1f),
        )

        assertEquals(
            listOf("left-top", "left-bottom", "right-top", "right-bottom"),
            OcrReadingOrder.order(lines).map { it.id },
        )
    }

    @Test
    fun rtlAndVerticalModesReverseColumnsButKeepTopToBottomOrder() {
        val lines = listOf(
            line("left-bottom", 0.1f, 0.6f),
            line("right-top", 0.75f, 0.1f),
            line("right-bottom", 0.75f, 0.6f),
            line("left-top", 0.1f, 0.1f),
        )

        val expected = listOf("right-top", "right-bottom", "left-top", "left-bottom")
        assertEquals(expected, OcrReadingOrder.order(lines, rtl = true).map { it.id })
        assertEquals(expected, OcrReadingOrder.order(lines, vertical = true).map { it.id })
        assertEquals(expected, OcrReadingOrder.order(lines, rtl = true, vertical = true).map { it.id })
    }

    @Test
    fun receiptPricesAreReadOnTheRowOfTheirItem() {
        // Items in a wide column, prices in a narrow one on the same rows, a hair higher than the item.
        val lines = listOf(
            line("item-1", 0.05f, 0.10f, width = 0.5f, height = 0.05f),
            line("item-2", 0.05f, 0.20f, width = 0.45f, height = 0.05f),
            line("item-3", 0.05f, 0.30f, width = 0.5f, height = 0.05f),
            line("price-1", 0.80f, 0.098f, width = 0.1f, height = 0.05f),
            line("price-2", 0.80f, 0.198f, width = 0.1f, height = 0.05f),
            line("price-3", 0.80f, 0.298f, width = 0.1f, height = 0.05f),
        )
        assertEquals(
            listOf("item-1", "price-1", "item-2", "price-2", "item-3", "price-3"),
            OcrReadingOrder.order(lines).map { it.id },
        )
        assertEquals(
            listOf("price-1", "item-1", "price-2", "item-2", "price-3", "item-3"),
            OcrReadingOrder.order(lines, rtl = true).map { it.id },
        )
    }

    @Test
    fun alignedColumnsOfSimilarWidthStayColumns() {
        // Two columns of running text whose lines share rows are still read column by column.
        val lines = listOf(
            line("a1", 0.05f, 0.10f, width = 0.4f),
            line("b1", 0.55f, 0.10f, width = 0.4f),
            line("a2", 0.05f, 0.20f, width = 0.4f),
            line("b2", 0.55f, 0.20f, width = 0.4f),
        )
        assertEquals(listOf("a1", "a2", "b1", "b2"), OcrReadingOrder.order(lines).map { it.id })
    }

    private fun line(id: String, left: Float, top: Float, width: Float = 0.15f, height: Float = 0.1f): OcrLine {
        val text = id
        return OcrLine(
            id = id,
            readingIndex = id.hashCode() and Int.MAX_VALUE,
            text = text,
            startOffset = 0,
            endOffset = text.length,
            quad = NormalizedQuad.axisAligned(left, top, left + width, top + height),
            confidence = 1f,
        )
    }
}
