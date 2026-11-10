/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer.insight

import com.dot.gallery.feature.viewer.components.media.ViewerZoomScales
import com.dot.gallery.feature.viewer.components.media.ViewerZoomScales.MIN_SELECTION_SHRINK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ViewerZoomScalesTest {
    @Test
    fun pinchingInAtFitShrinksThePageDownToHalf() {
        // zoomimage stops at "fit"; past it the page itself shrinks so the top and bottom lines
        // of a screenshot come out from under the selection bars.
        assertEquals(0.8f, ViewerZoomScales.shrinkAfterPinch(1f, zoom = 0.8f, zoomableAtMinimum = true)!!, 0.0001f)
        assertEquals(MIN_SELECTION_SHRINK, ViewerZoomScales.shrinkAfterPinch(0.6f, zoom = 0.5f, zoomableAtMinimum = true)!!, 0f)
        // Still taken at the floor, so zoomimage does not rubber-band underneath.
        assertEquals(MIN_SELECTION_SHRINK, ViewerZoomScales.shrinkAfterPinch(MIN_SELECTION_SHRINK, 0.9f, true)!!, 0f)
    }

    @Test
    fun pinchingInWhileZoomedInIsLeftToZoomimage() {
        assertNull(ViewerZoomScales.shrinkAfterPinch(1f, zoom = 0.8f, zoomableAtMinimum = false))
    }

    @Test
    fun pinchingOutGrowsThePageBackBeforeZoomimageZoomsIn() {
        assertEquals(0.75f, ViewerZoomScales.shrinkAfterPinch(0.5f, zoom = 1.5f, zoomableAtMinimum = true)!!, 0.0001f)
        assertEquals(1f, ViewerZoomScales.shrinkAfterPinch(0.9f, zoom = 1.5f, zoomableAtMinimum = true)!!, 0f)
        // At full size the zoom-in is zoomimage's.
        assertNull(ViewerZoomScales.shrinkAfterPinch(1f, zoom = 1.5f, zoomableAtMinimum = true))
    }

    @Test
    fun stepsWithoutAZoomChangeAreIgnored() {
        assertNull(ViewerZoomScales.shrinkAfterPinch(0.7f, zoom = 1f, zoomableAtMinimum = true))
        assertNull(ViewerZoomScales.shrinkAfterPinch(0.7f, zoom = Float.NaN, zoomableAtMinimum = true))
    }
}
