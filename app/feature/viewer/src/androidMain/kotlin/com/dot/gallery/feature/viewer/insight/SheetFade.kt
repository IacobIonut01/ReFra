/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer.insight

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/** Alpha and vertical offset of a viewer overlay that goes with the info sheet. */
data class SheetFade(val alpha: Float, val translationY: Float)

/**
 * Maps the sheet's travel from the image-only detent ([progress] 0) to the expanded one ([progress] 1)
 * onto an overlay that fades out and moves down by [travelPx].
 */
fun sheetFadeTransform(progress: Float, travelPx: Float): SheetFade {
    val p = progress.coerceIn(0f, 1f)
    return SheetFade(alpha = 1f - p, translationY = travelPx * p)
}

/**
 * Fades and slides this layer with the info sheet. [progress] is read inside the layer block, so the
 * sheet can move without recomposing the overlay.
 */
fun Modifier.fadeOutWithSheet(progress: () -> Float, travel: Dp): Modifier = graphicsLayer {
    val fade = sheetFadeTransform(progress(), travel.toPx())
    alpha = fade.alpha
    translationY = fade.translationY
    compositingStrategy = CompositingStrategy.ModulateAlpha
}
