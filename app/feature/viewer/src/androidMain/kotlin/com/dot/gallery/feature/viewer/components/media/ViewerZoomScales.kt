/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer.components.media

/**
 * The extra zoom-out the photo viewer allows while text is selected.
 *
 * The selection header and action bar float over the top and bottom of the photo, so on a
 * full-screen screenshot the lines under them could not be reached. zoomimage never zooms below
 * "fit" (it clamps its minimum to the fit scale), so the viewer shrinks the whole page, photo and
 * text overlay together, by a separate factor between [MIN_SELECTION_SHRINK] and 1.
 */
object ViewerZoomScales {
    /** Smallest page size while selecting text, as a share of "fit": room above and below for both bars. */
    const val MIN_SELECTION_SHRINK = 0.5f

    /**
     * The page shrink after one pinch step of [zoom] (below 1 when the fingers close), or null
     * when the step belongs to zoomimage instead. Closing the fingers shrinks the page only once
     * zoomimage is already at its minimum ([zoomableAtMinimum]); opening them first grows the page
     * back to full size, and only then does zoomimage zoom in. A step that would go past either
     * end is still taken (and clamped), so zoomimage never rubber-bands under the shrink.
     */
    fun shrinkAfterPinch(current: Float, zoom: Float, zoomableAtMinimum: Boolean): Float? {
        if (!zoom.isFinite() || zoom <= 0f || zoom == 1f) return null
        return when {
            zoom < 1f && zoomableAtMinimum -> (current * zoom).coerceIn(MIN_SELECTION_SHRINK, 1f)
            zoom > 1f && current < 1f -> (current * zoom).coerceIn(MIN_SELECTION_SHRINK, 1f)
            else -> null
        }
    }
}
