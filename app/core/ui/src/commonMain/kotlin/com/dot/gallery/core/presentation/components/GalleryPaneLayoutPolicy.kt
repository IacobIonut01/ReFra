/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dot.gallery.ui.theme.GalleryChrome

/** Leave short landscape windows in fullscreen even if their width would fit two panes. */
fun galleryDetailPaneFits(width: Dp, height: Dp): Boolean =
    width >= GalleryChrome.DetailMinWindowWidth && height >= GalleryChrome.DetailMinWindowHeight

/** Default gallery width: 45% of the window, kept between 360 and 480 dp. */
fun galleryMasterPaneWidth(width: Dp): Dp = (width * 0.45f).coerceIn(360.dp, 480.dp)

/**
 * Gallery width for a user-chosen [fraction]. The default fraction keeps the reference geometry;
 * a dragged fraction may use any width that still leaves both panes usable.
 */
fun galleryMasterPaneWidth(width: Dp, fraction: Float): Dp {
    if (fraction == GalleryChrome.DefaultMasterPaneFraction) return galleryMasterPaneWidth(width)
    return (width * fraction).coerceIn(galleryMasterPaneMinWidth(width), galleryMasterPaneMaxWidth(width))
}

fun galleryMasterPaneMinWidth(width: Dp): Dp =
    GalleryChrome.MasterPaneMinWidth.coerceAtMost(galleryMasterPaneMaxWidth(width))

fun galleryMasterPaneMaxWidth(width: Dp): Dp =
    (width - GalleryChrome.PaneGap - GalleryChrome.DetailPaneMinWidth).coerceAtLeast(0.dp)

/** Fractions saved on another window size are clamped instead of trusted. */
fun galleryPaneFractionFor(width: Dp, masterWidth: Dp): Float =
    if (width <= 0.dp) GalleryChrome.DefaultMasterPaneFraction
    else (masterWidth.coerceIn(galleryMasterPaneMinWidth(width), galleryMasterPaneMaxWidth(width)) / width)
        .coerceIn(0f, 1f)

/** Retained viewer data outlives its exit animation and must not keep an empty detail pane open. */
fun galleryDetailPaneActive(
    hasMediaOverlay: Boolean,
    overlayRequested: Boolean,
    overlayCurrentlyVisible: Boolean,
): Boolean = hasMediaOverlay && (overlayRequested || overlayCurrentlyVisible)
