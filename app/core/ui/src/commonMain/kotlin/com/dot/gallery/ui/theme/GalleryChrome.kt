/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.ui.theme

import androidx.compose.ui.unit.dp

/** Geometry shared by the home toolbar, floating dock and rail, and gallery detail panes. */
object GalleryChrome {
    val ToolbarButtonSize = 48.dp
    val ToolbarVerticalPadding = 8.dp
    val ToolbarCornerRadius = 28.dp
    val DockMaxWidth = 336.dp
    val DockGap = 16.dp
    val DockIndicatorWidth = 52.dp
    val DockIndicatorHeight = 32.dp
    val IndicatorCornerRadius = 12.dp
    val PaneGap = 12.dp
    val PaneCornerRadius = 28.dp
    val DetailMinWindowWidth = 840.dp
    val DetailMinWindowHeight = 480.dp
    val MasterPaneMinWidth = 320.dp
    val DetailPaneMinWidth = 360.dp
    const val DefaultMasterPaneFraction = 0.45f
    val PaneHandleWidth = 3.dp
    val PaneHandleActiveWidth = 6.dp
    val PaneHandleHeight = 112.dp
    val PaneHandleTouchWidth = 24.dp

    // Floating rail used by wide windows when the gallery is not split.
    val RailStartPadding = 12.dp
    // 56 dp: the same footprint as the viewer action segments and standard toolbar buttons.
    val RailItemWidth = 56.dp
    val RailItemHeight = 56.dp
    val RailItemGap = 4.dp
    val RailVerticalPadding = 8.dp
    val RailCornerRadius = 28.dp
    val RailIndicatorWidth = 32.dp
    val RailIndicatorHeight = 40.dp
    val RailContentGap = 10.dp
    val RailWidth = RailStartPadding + RailItemWidth + RailContentGap

    // Connected action group used by the viewer.
    val ActionSegmentSize = 56.dp
    val ActionSegmentGap = 1.dp
    val ActionSegmentInnerRadius = 4.dp
    val ActionGroupCornerRadius = 32.dp

    // Backup header reveal.
    val HeaderPullThreshold = 72.dp
}
