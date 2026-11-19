/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.mediaview

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** An empty outline: a clipped layer with it takes no touches, but stays composed and placed. */
internal object NoHitShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rectangle(Rect.Zero)
}

/**
 * Lets touches fall through a layer that is kept composed but hidden, so it does not swallow taps
 * meant for the content beneath it.
 */
internal fun GraphicsLayerScope.passTouchesThrough(enabled: Boolean) {
    if (enabled) {
        clip = true
        shape = NoHitShape
    }
}
