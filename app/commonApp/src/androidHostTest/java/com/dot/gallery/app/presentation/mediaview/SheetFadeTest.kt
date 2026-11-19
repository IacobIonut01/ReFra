/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.mediaview

import com.dot.gallery.feature.viewer.insight.sheetFadeTransform
import org.junit.Assert.assertEquals
import org.junit.Test

class SheetFadeTest {

    @Test
    fun collapsedSheetLeavesOverlayUntouched() {
        val fade = sheetFadeTransform(progress = 0f, travelPx = 200f)

        assertEquals(1f, fade.alpha, 0f)
        assertEquals(0f, fade.translationY, 0f)
    }

    @Test
    fun expandedSheetFadesOverlayFullyAndMovesItDownByTravel() {
        val fade = sheetFadeTransform(progress = 1f, travelPx = 200f)

        assertEquals(0f, fade.alpha, 0f)
        assertEquals(200f, fade.translationY, 0f)
    }

    @Test
    fun halfwaySheetHalvesAlphaAndHalvesTravel() {
        val fade = sheetFadeTransform(progress = 0.5f, travelPx = 200f)

        assertEquals(0.5f, fade.alpha, 0f)
        assertEquals(100f, fade.translationY, 0f)
    }

    @Test
    fun outOfRangeProgressIsClamped() {
        val below = sheetFadeTransform(progress = -0.5f, travelPx = 200f)
        val above = sheetFadeTransform(progress = 1.5f, travelPx = 200f)

        assertEquals(1f, below.alpha, 0f)
        assertEquals(0f, below.translationY, 0f)
        assertEquals(0f, above.alpha, 0f)
        assertEquals(200f, above.translationY, 0f)
    }
}
