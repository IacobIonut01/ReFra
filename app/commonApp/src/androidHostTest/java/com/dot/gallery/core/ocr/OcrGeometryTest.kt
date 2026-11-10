/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr

import com.dot.gallery.core.ocr.domain.NormalizedPoint
import com.dot.gallery.core.ocr.domain.NormalizedQuad
import com.dot.gallery.core.ocr.domain.OcrLine
import com.dot.gallery.core.ocr.geometry.ExifOrientation
import com.dot.gallery.core.ocr.geometry.OcrGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrGeometryTest {
    @Test
    fun computesPolygonAndBoundsIoU() {
        val first = quad(0f, 0f, 0.5f, 0.5f)
        val second = quad(0.25f, 0f, 0.75f, 0.5f)

        assertEquals(1f / 3f, OcrGeometry.boundsIoU(first, second), 0.0001f)
        assertEquals(1f / 3f, OcrGeometry.polygonIoU(first, second), 0.0001f)
    }

    @Test
    fun hitTestingSupportsSmallInflation() {
        val quad = quad(0.2f, 0.2f, 0.4f, 0.4f)
        assertTrue(OcrGeometry.contains(quad, NormalizedPoint(0.3f, 0.3f)))
        assertFalse(OcrGeometry.contains(quad, NormalizedPoint(0.41f, 0.3f)))
        assertTrue(OcrGeometry.contains(quad, NormalizedPoint(0.41f, 0.3f), inflation = 0.011f))
    }

    @Test
    fun perspectiveMapsCornersAndExifHandlesMirroring() {
        val source = quad(0f, 0f, 1f, 1f)
        val target = quad(0.1f, 0.2f, 0.8f, 0.9f)
        val transform = OcrGeometry.perspective(source, target)!!
        val mapped = transform.map(NormalizedPoint(0f, 0f))!!
        assertEquals(0.1f, mapped.x, 0.0001f)
        assertEquals(0.2f, mapped.y, 0.0001f)

        assertEquals(
            NormalizedPoint(0.75f, 0.25f),
            OcrGeometry.mapExif(NormalizedPoint(0.25f, 0.25f), ExifOrientation.FLIP_HORIZONTAL),
        )
        assertEquals(20 to 10, OcrGeometry.orientedDimensions(10, 20, ExifOrientation.ROTATE_90))
    }

    @Test
    fun contiguousSelectionUsesStableReadingIndices() {
        val lines = listOf(line("c", 2), line("a", 0), line("b", 1))
        assertEquals(listOf("a", "b", "c"), OcrGeometry.contiguousSelection(lines, "c", "a").map { it.id })
    }

    private fun line(id: String, index: Int) = OcrLine(id, index, id, 0, id.length, quad(0f, 0f, 1f, 1f), 1f)

    private fun quad(left: Float, top: Float, right: Float, bottom: Float) =
        NormalizedQuad.axisAligned(left, top, right, bottom)
}
