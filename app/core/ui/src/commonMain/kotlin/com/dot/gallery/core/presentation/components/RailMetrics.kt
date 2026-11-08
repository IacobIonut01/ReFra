/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp

/** Vertical geometry of the floating rail, chosen per window height by [railMetrics]. */
@Immutable
data class RailMetrics(
    val itemHeight: Dp,
    val itemGap: Dp,
    val pillPadding: Dp,
    val circleSize: Dp,
    val sectionGap: Dp,
) {
    /** Height of back (optional) + destinations + search, the rail's tallest resting state. */
    fun requiredHeight(itemCount: Int, hasBack: Boolean): Dp {
        val pill = pillPadding * 2 + itemHeight * itemCount + itemGap * (itemCount - 1).coerceAtLeast(0)
        val back = if (hasBack) circleSize + sectionGap else Dp(0f)
        // Secondary screens hide the destinations, so only the primary layout needs the pill.
        val destinations = if (hasBack) Dp(0f) else pill
        return back + destinations + sectionGap + circleSize
    }
}
