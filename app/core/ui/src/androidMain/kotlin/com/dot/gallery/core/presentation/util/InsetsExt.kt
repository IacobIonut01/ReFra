/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.util

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


val LocalFixedInsets = compositionLocalOf<FixedInsets> { error("no FixedInsets provided!") }

data class FixedInsets(
    val statusBarHeight: Dp = 0.dp,
    val navigationBarsPadding: PaddingValues = PaddingValues(),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProvideInsets(content: @Composable () -> Unit) {
    val layoutDirection = LocalLayoutDirection.current
    // Use the *IgnoringVisibility insets so the values stay correct while the
    // system bars are hidden (immersive media view) — the reason these were
    // "fixed" in the first place — while still recomputing on configuration
    // changes such as screen rotation. The previous keyless remember
    // captured the initial-orientation insets and never updated on rotation.
    val statusBarsPadding = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues()
    val navigationBarsPadding = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues()
    // IMPORTANT: key the remember on the resolved Dp primitives, NOT on the
    // PaddingValues objects. asPaddingValues() returns a stable instance whose
    // calculateXPadding() reads the live insets lazily, so as a remember key it
    // stays equal across a rotation and the block would never recompute.
    val statusTop = statusBarsPadding.calculateTopPadding()
    val navBottom = navigationBarsPadding.calculateBottomPadding()
    val navStart = navigationBarsPadding.calculateStartPadding(layoutDirection)
    val navEnd = navigationBarsPadding.calculateEndPadding(layoutDirection)
    val fixedInsets = remember(statusTop, navBottom, navStart, navEnd) {
        FixedInsets(
            statusBarHeight = statusTop,
            navigationBarsPadding = PaddingValues(
                bottom = navBottom,
                start = navStart,
                end = navEnd,
            ),
        )
    }

    CompositionLocalProvider(
        values = arrayOf(
            LocalFixedInsets provides fixedInsets,
        ),
    ) {
        content()
    }
}

@Composable
fun rememberNavigationBarHeight(): Dp {
    val insets = LocalFixedInsets.current
    val layoutDirection = LocalLayoutDirection.current

    val navigationBarPaddingBottom = remember(insets) {
        insets.navigationBarsPadding.calculateBottomPadding()
    }
    val navigationBarPaddingTop = remember(insets) {
        insets.navigationBarsPadding.calculateTopPadding()
    }
    val navigationBarPaddingStart = remember(insets, layoutDirection) {
        insets.navigationBarsPadding.calculateStartPadding(layoutDirection)
    }
    val navigationBarPaddingEnd = remember(insets, layoutDirection) {
        insets.navigationBarsPadding.calculateEndPadding(layoutDirection)
    }

    return remember(insets, layoutDirection) {
        if (navigationBarPaddingTop > 0.dp) {
            navigationBarPaddingTop
        } else if (navigationBarPaddingBottom > 0.dp) {
            navigationBarPaddingBottom
        } else if (navigationBarPaddingStart > 0.dp) {
            navigationBarPaddingStart
        } else {
            navigationBarPaddingEnd
        }
    }
}

@Composable
fun rememberGestureNavigationEnabled(): Boolean {
    val navigationBarHeight = rememberNavigationBarHeight()

    return remember(navigationBarHeight) {
        navigationBarHeight < 48.dp
    }
}

/**
 * Returns true when the system navigation bar sits on the sides instead of the
 * bottom (e.g. 3-button navigation in landscape). In that case there is no
 * bottom inset, but there is a horizontal one.
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
