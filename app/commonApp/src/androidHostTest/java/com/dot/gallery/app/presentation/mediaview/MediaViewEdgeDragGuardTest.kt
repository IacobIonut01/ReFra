/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.mediaview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaViewEdgeDragGuardTest {

    @Test
    fun zoneMatchesInsideStartStrip() {
        assertTrue(edgeGuardZone(x = 0f, widthPx = 400f, startPx = 40f, endPx = 40f))
        assertTrue(edgeGuardZone(x = 20f, widthPx = 400f, startPx = 40f, endPx = 40f))
        assertTrue(edgeGuardZone(x = 39.9f, widthPx = 400f, startPx = 40f, endPx = 40f))
    }

    @Test
    fun zoneExcludesStartStripBoundaryAndMiddle() {
        assertFalse(edgeGuardZone(x = 40f, widthPx = 400f, startPx = 40f, endPx = 40f))
        assertFalse(edgeGuardZone(x = 200f, widthPx = 400f, startPx = 40f, endPx = 40f))
    }

    @Test
    fun zoneMatchesInsideEndStrip() {
        // End strip is (width - endPx, width]: x must exceed 360 and may equal 400.
        assertTrue(edgeGuardZone(x = 360.1f, widthPx = 400f, startPx = 40f, endPx = 40f))
        assertTrue(edgeGuardZone(x = 400f, widthPx = 400f, startPx = 40f, endPx = 40f))
        assertFalse(edgeGuardZone(x = 360f, widthPx = 400f, startPx = 40f, endPx = 40f))
    }

    @Test
    fun zoneRejectsPositionsOutsideNode() {
        assertFalse(edgeGuardZone(x = -1f, widthPx = 400f, startPx = 40f, endPx = 40f))
        assertFalse(edgeGuardZone(x = 401f, widthPx = 400f, startPx = 40f, endPx = 40f))
    }

    @Test
    fun zoneEmptyWhenStripsHaveZeroWidth() {
        assertFalse(edgeGuardZone(x = 0f, widthPx = 400f, startPx = 0f, endPx = 0f))
        assertFalse(edgeGuardZone(x = 20f, widthPx = 400f, startPx = 0f, endPx = 0f))
        assertFalse(edgeGuardZone(x = 200f, widthPx = 400f, startPx = 0f, endPx = 0f))
        assertFalse(edgeGuardZone(x = 400f, widthPx = 400f, startPx = 0f, endPx = 0f))
    }

    @Test
    fun zoneHonoursEachStripIndependently() {
        // Only a start strip: the right edge stays clear.
        assertTrue(edgeGuardZone(x = 10f, widthPx = 400f, startPx = 40f, endPx = 0f))
        assertFalse(edgeGuardZone(x = 390f, widthPx = 400f, startPx = 40f, endPx = 0f))
        // Only an end strip: the left edge stays clear.
        assertFalse(edgeGuardZone(x = 10f, widthPx = 400f, startPx = 0f, endPx = 40f))
        assertTrue(edgeGuardZone(x = 390f, widthPx = 400f, startPx = 0f, endPx = 40f))
    }

    @Test
    fun blockRequiresHorizontalPastSlop() {
        val slop = 20f
        assertFalse(shouldBlockEdgeDrag(dx = 19.9f, dy = 0f, touchSlop = slop))
        assertTrue(shouldBlockEdgeDrag(dx = 20f, dy = 0f, touchSlop = slop))
        assertTrue(shouldBlockEdgeDrag(dx = -25f, dy = 3f, touchSlop = slop))
    }

    @Test
    fun blockRejectsVerticalDominantDrag() {
        val slop = 20f
        assertFalse(shouldBlockEdgeDrag(dx = 25f, dy = 25f, touchSlop = slop))
        assertFalse(shouldBlockEdgeDrag(dx = 25f, dy = 40f, touchSlop = slop))
        assertFalse(shouldBlockEdgeDrag(dx = 10f, dy = 60f, touchSlop = slop))
    }
}
