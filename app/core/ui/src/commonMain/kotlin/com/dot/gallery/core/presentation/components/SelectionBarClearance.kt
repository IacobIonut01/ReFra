/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max

/**
 * The height, from the bottom of the screen, of the bottom selection bar while it is shown.
 * The bar reports it and the grids under it reserve it, so the last row can scroll clear of
 * the bar. Zero when no bar is shown.
 */
@Stable
class SelectionBarClearance {
    // Keyed by bar, so a bar leaving during a screen transition never clears another's height.
    private val heights = mutableStateMapOf<Any, Dp>()

    val height: Dp get() = heights.values.maxOrNull() ?: 0.dp

    fun report(owner: Any, height: Dp) {
        heights[owner] = height
    }

    fun clear(owner: Any) {
        heights.remove(owner)
    }
}

/** One per app, like the media selector the bars follow. */
val LocalSelectionBarClearance = staticCompositionLocalOf { SelectionBarClearance() }

/** Gap kept between the last row of a grid and the top of the selection bar. */
val SelectionBarGridGap = 16.dp

/**
 * Reports this layout's height as the selection bar's. Apply it to the bar's outermost layout,
 * whose bottom edge sits on the bottom of the screen; the height is cleared when it leaves.
 */
@Composable
fun Modifier.reportSelectionBarHeight(): Modifier {
    val clearance = LocalSelectionBarClearance.current
    val density = LocalDensity.current
    val owner = remember { Any() }
    DisposableEffect(clearance) {
        onDispose { clearance.clear(owner) }
    }
    return onSizeChanged { clearance.report(owner, with(density) { it.height.toDp() }) }
}

/**
 * The bottom content padding of a grid that a selection bar can cover: [base] normally, or
 * enough to keep the last row above the bar while one is shown.
 */
@Composable
fun selectionAwareBottomPadding(base: Dp): Dp {
    val bar = LocalSelectionBarClearance.current.height
    return if (bar > 0.dp) max(base, bar + SelectionBarGridGap) else base
}
