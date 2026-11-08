/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.mediaview.components

import com.dot.gallery.core.Position
import com.dot.gallery.feature.viewer.ViewerSheetCorners
import com.dot.gallery.feature.viewer.ViewerSheetInnerCorner
import com.dot.gallery.feature.viewer.ViewerSheetOuterCorner
import com.dot.gallery.feature.viewer.actionGridCellCorners
import com.dot.gallery.feature.viewer.formatCoordinates
import com.dot.gallery.feature.viewer.viewerSheetCorners
import com.dot.gallery.feature.viewer.viewerSheetPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class ViewerSheetKitTest {

    private val o = ViewerSheetOuterCorner
    private val i = ViewerSheetInnerCorner

    @Test
    fun positionsFollowTheRowIndex() {
        assertEquals(Position.Alone, viewerSheetPosition(0, 1))
        assertEquals(Position.Top, viewerSheetPosition(0, 3))
        assertEquals(Position.Middle, viewerSheetPosition(1, 3))
        assertEquals(Position.Bottom, viewerSheetPosition(2, 3))
    }

    @Test
    fun groupedRowsRoundOnlyTheGroupsOuterEdge() {
        assertEquals(ViewerSheetCorners(o, o, i, i), viewerSheetCorners(Position.Top))
        assertEquals(ViewerSheetCorners(i, i, i, i), viewerSheetCorners(Position.Middle))
        assertEquals(ViewerSheetCorners(i, i, o, o), viewerSheetCorners(Position.Bottom))
        assertEquals(ViewerSheetCorners(o, o, o, o), viewerSheetCorners(Position.Alone))
    }

    @Test
    fun actionGridRoundsItsFourOuterCorners() {
        // 2 rows x 2 columns.
        assertEquals(ViewerSheetCorners(o, i, i, i), actionGridCellCorners(0, 2, 0, spansRow = false))
        assertEquals(ViewerSheetCorners(i, o, i, i), actionGridCellCorners(0, 2, 1, spansRow = false))
        assertEquals(ViewerSheetCorners(i, i, i, o), actionGridCellCorners(1, 2, 0, spansRow = false))
        assertEquals(ViewerSheetCorners(i, i, o, i), actionGridCellCorners(1, 2, 1, spansRow = false))
    }

    @Test
    fun aLoneLastActionSpansItsRowAndTakesBothBottomCorners() {
        assertEquals(ViewerSheetCorners(i, i, o, o), actionGridCellCorners(2, 3, 0, spansRow = true))
        // A single action is the whole grid.
        assertEquals(ViewerSheetCorners(o, o, o, o), actionGridCellCorners(0, 1, 0, spansRow = true))
    }

    @Test
    fun coordinatesUseHemisphereLettersAndFourDecimals() {
        assertEquals("51.3217° N, 116.1860° W", formatCoordinates(51.32171, -116.18601))
        assertEquals("33.8688° S, 151.2093° E", formatCoordinates(-33.86882, 151.20929))
        assertEquals("0.0000° N, 0.0005° E", formatCoordinates(0.0, 0.00049))
    }
}
