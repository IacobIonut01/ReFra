/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr

import com.dot.gallery.core.ocr.geometry.DbPostProcessor
import com.dot.gallery.core.ocr.geometry.PixelPoint
import com.dot.gallery.core.ocr.geometry.PixelQuad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DbPostProcessorTest {
    @Test
    fun unclipGrowsEverySideByAreaTimesRatioOverPerimeter() {
        // A 40 × 6 kernel: PaddleOCR grows it by 40·6·1.5 / (2·46) ≈ 3.91 on every side.
        val map = map(100, 40) { x, y -> if (x in 10 until 50 && y in 17 until 23) 0.9f else 0f }
        val box = boxes(map, 100, 40).single()
        val grow = 40.0 * 6 * 1.5 / (2 * 46)
        assertEquals((40 + 2 * grow).toFloat(), box.cropWidth, 0.1f)
        // The old per-axis growth made this 9 tall (glyph tops cut) and 60 wide (neighbours pulled in).
        assertEquals((6 + 2 * grow).toFloat(), box.cropHeight, 0.1f)
        assertEquals((10 - grow).toFloat(), box.topLeft.x, 0.1f)
        assertEquals((17 - grow).toFloat(), box.topLeft.y, 0.1f)
    }

    @Test
    fun boxScoreAveragesTheWholeRectangleNotOnlyTheBrightPixels() {
        // A 1 px outline around a dark interior: every pixel of the region is 0.9, the box is mostly 0.1.
        val map = map(60, 30) { x, y ->
            val inside = x in 10..39 && y in 5..24
            val edge = x == 10 || x == 39 || y == 5 || y == 24
            when {
                inside && edge -> 0.9f
                inside -> 0.1f
                else -> 0f
            }
        }
        assertTrue(boxes(map, 60, 30).isEmpty())
    }

    @Test
    fun regionsThinnerThanTheMinimumSizeAreDropped() {
        val map = map(60, 20) { x, y -> if (x in 5 until 55 && y in 9 until 11) 0.9f else 0f }
        assertTrue(boxes(map, 60, 20).isEmpty())
    }

    @Test
    fun verticalTextKeepsItsImageOrientation() {
        // A tall column: the box must stay tall so the crop is turned like PaddleOCR turns it,
        // not laid on its side by the principal axis (whose sign flipped at random near 90°).
        val map = map(40, 100) { x, y -> if (x in 17 until 23 && y in 10 until 90) 0.9f else 0f }
        val box = boxes(map, 40, 100).single()
        assertTrue(box.cropHeight > box.cropWidth * 1.5f)
        assertTrue(box.topLeft.y < box.bottomLeft.y && box.topLeft.x < box.topRight.x)
    }

    @Test
    fun cornersAreOrderedLikePaddleMiniBoxes() {
        // A slightly tilted line, corners given in no particular order.
        val quad = DbPostProcessor.orderCorners(
            listOf(PixelPoint(21f, 10f), PixelPoint(1f, 12f), PixelPoint(0f, 2f), PixelPoint(20f, 0f))
        )
        assertEquals(
            PixelQuad(PixelPoint(0f, 2f), PixelPoint(20f, 0f), PixelPoint(21f, 10f), PixelPoint(1f, 12f)).points,
            listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft),
        )
    }

    private fun boxes(map: FloatArray, width: Int, height: Int) = DbPostProcessor.boxes(
        map = map,
        offset = 0,
        width = width,
        height = height,
        threshold = 0.3f,
        boxThreshold = 0.6f,
        unclipRatio = 1.5f,
        sourceWidth = width,
        sourceHeight = height,
        maximumDetections = 10,
    )

    private fun map(width: Int, height: Int, value: (Int, Int) -> Float) =
        FloatArray(width * height) { value(it % width, it / width) }
}
