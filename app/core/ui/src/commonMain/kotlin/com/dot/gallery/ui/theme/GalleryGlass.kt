/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The `glass` colour tokens of the 6.0.0 design system, mapped to the current colour scheme.
 * Light tints are the page surface, dark tints its near-black counterpart; only the alpha
 * differs between modes, as in the FigCraft `Color` collection.
 */
object GalleryGlass {
    /** Frosted panel over the setup blobs (24 dp backdrop blur under it). */
    val panel: Color
        @Composable
        get() = surfaceTint(light = 0.35f, dark = 0.30f)

    /** Row inside a glass panel; never blurred again. */
    val row: Color
        @Composable
        get() = if (isDarkTheme()) {
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.70f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.70f)
        }

    /** Round buttons sitting on a transparent bar; no blur of their own. */
    val chrome: Color
        @Composable
        get() = surfaceTint(light = 0.60f, dark = 0.65f)

    /** Top stop of the scrolled top bar's fade tint. */
    val fadeTop: Color
        @Composable
        get() = surfaceTint(light = 0.80f, dark = 0.70f)

    /** Bottom stop of the scrolled top bar's fade tint. */
    val fadeClear: Color
        @Composable
        get() = MaterialTheme.colorScheme.surface.copy(alpha = 0f)

    /** Outer stops of the scrim laid over the setup blobs. */
    val scrimEdge: Color
        @Composable
        get() = MaterialTheme.colorScheme.surface.copy(alpha = 0.50f)

    /** Middle stop of the scrim laid over the setup blobs. */
    val scrimMid: Color
        @Composable
        get() = MaterialTheme.colorScheme.surface.copy(alpha = 0f)

    @Composable
    private fun surfaceTint(light: Float, dark: Float): Color =
        MaterialTheme.colorScheme.surface.copy(alpha = if (isDarkTheme()) dark else light)
}
