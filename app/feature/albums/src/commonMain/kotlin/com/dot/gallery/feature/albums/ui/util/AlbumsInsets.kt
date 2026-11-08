/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.albums.ui.util

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Horizontal window insets extracted from [paddingValues] so the Albums screen
 * can apply them once at the Scaffold level and consume them for the content.
 */
fun albumScreenHorizontalInsets(
    paddingValues: PaddingValues,
    layoutDirection: LayoutDirection,
): PaddingValues = PaddingValues(
    start = paddingValues.calculateStartPadding(layoutDirection),
    end = paddingValues.calculateEndPadding(layoutDirection),
)

/**
 * True when the system navigation bar is rendered on the side of the screen
 * instead of the bottom (e.g. 3-button navigation in landscape). In that case
 * there is no bottom inset, but there is a horizontal one.
 */
@Composable
fun rememberNavigationBarOnSides(): Boolean {
    val navBarsPadding = WindowInsets.navigationBars.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    // Key on the resolved Dp primitives (see ProvideInsets) so this recomputes on
    // rotation instead of caching the initial-orientation result.
    val bottom = navBarsPadding.calculateBottomPadding()
    val start = navBarsPadding.calculateStartPadding(layoutDirection)
    val end = navBarsPadding.calculateEndPadding(layoutDirection)
    return remember(bottom, start, end) {
        bottom == 0.dp && (start > 0.dp || end > 0.dp)
    }
}

/**
 * Bottom inset to reserve for the floating bottom navigation pill (and the
 * content behind it). When the navigation bar is on the sides there is no
 * bottom system inset, so a fixed 16.dp gap is used instead of [PaddingValues]
 * bottom padding.
 */
@Composable
fun rememberBottomBarInset(paddingValues: PaddingValues): Dp {
    val onSides = rememberNavigationBarOnSides()
    val bottomInset = paddingValues.calculateBottomPadding()
    return remember(onSides, bottomInset) {
        if (onSides) 16.dp else bottomInset
    }
}
