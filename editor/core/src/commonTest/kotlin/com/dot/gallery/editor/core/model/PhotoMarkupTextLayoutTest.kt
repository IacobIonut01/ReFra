package com.dot.gallery.editor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhotoMarkupTextLayoutTest {

    private fun label(size: Float = 0.1f, rotation: Float = 0f, x: Float = 0.5f, y: Float = 0.5f) =
        PhotoMarkupText(1L, "Label", x, y, -1, size, rotation)

    /** The label's rotated bounds, as left, top, right, bottom in pixels. */
    private fun bounds(text: PhotoMarkupText, emW: Float, emH: Float, w: Float, h: Float): List<Float> {
        val px = text.size * h
        val (ew, eh) = PhotoMarkupTextLayout.rotatedExtent(emW * px, emH * px, text.rotation)
        val cx = text.x * w
        val cy = text.y * h
        return listOf(cx - ew / 2, cy - eh / 2, cx + ew / 2, cy + eh / 2)
    }

    @Test
    fun aDraggedLabelStaysInsideTheFrame() {
        listOf(0f, 30f, 90f, -135f).forEach { rotation ->
            val out = PhotoMarkupTextLayout.clampCentre(
                label(rotation = rotation, x = 1f, y = 0f), 4f, 1.2f, 1000f, 800f
            )
            val (l, t, r, b) = bounds(out, 4f, 1.2f, 1000f, 800f)
            assertTrue(l >= -0.5f && t >= -0.5f, "rotation $rotation: $l, $t")
            assertTrue(r <= 1000.5f && b <= 800.5f, "rotation $rotation: $r, $b")
        }
    }

    @Test
    fun sizeNeverPassesTheOldEditorsRange() {
        val wide = PhotoMarkupTextLayout.maxFittingSize(label(), 0.5f, 1.2f, 4000f, 1000f)
        assertEquals(PhotoMarkupTextLayout.MAX_SIZE, wide)
        val huge = PhotoMarkupTextLayout.maxFittingSize(label(), 500f, 1.2f, 1000f, 1000f)
        assertEquals(PhotoMarkupTextLayout.MIN_SIZE, huge)
    }

    @Test
    fun theSizeCapFollowsTheRotatedBox() {
        // A long line fits wide but not once it stands upright in a landscape frame.
        val flat = PhotoMarkupTextLayout.maxFittingSize(label(), 8f, 1.2f, 2000f, 1000f)
        val upright = PhotoMarkupTextLayout.maxFittingSize(
            label(rotation = 90f), 8f, 1.2f, 2000f, 1000f
        )
        assertTrue(upright < flat, "$upright should be under $flat")
        val fitted = PhotoMarkupTextLayout.fit(
            label(size = 0.2f, rotation = 90f), 8f, 1.2f, 2000f, 1000f
        )
        val (_, top, _, bottom) = bounds(fitted, 8f, 1.2f, 2000f, 1000f)
        assertTrue(top >= -0.5f && bottom <= 1000.5f, "$top, $bottom")
    }

    @Test
    fun anOversizedBoxCoversTheFrameInsteadOfEscaping() {
        val out = PhotoMarkupTextLayout.fit(
            label(size = 0.02f, x = 0f, y = 0f), 300f, 1.2f, 1000f, 1000f
        )
        val (l, _, r, _) = bounds(out, 300f, 1.2f, 1000f, 1000f)
        assertTrue(l <= 0f && r >= 1000f, "$l, $r")
    }

    @Test
    fun rotationNormalizesIntoOneTurn() {
        assertEquals(170f, PhotoMarkupTextLayout.normalizeRotation(530f))
        assertEquals(-90f, PhotoMarkupTextLayout.normalizeRotation(270f))
        assertEquals(180f, PhotoMarkupTextLayout.normalizeRotation(-180f))
    }

    @Test
    fun blankEdgeLinesGoAndInnerOnesStay() {
        assertEquals("a\n\nb", PhotoMarkupTextLayout.normalizeText("\n  \na\n\nb\n \n"))
        assertEquals("", PhotoMarkupTextLayout.normalizeText(" \n\n "))
    }
}
