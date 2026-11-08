/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Shared, mutable signals between the timeline grid, its scroller and the months view. Kept apart
 * from [TimelineDecorations] so they survive the decorations being rebuilt for new media.
 */
@Stable
class TimelineSignals {
    /** True while the scroller handle is held; the floating date label hides meanwhile. */
    var isScrubbing by mutableStateOf(false)

    /** A month key (see [monthKeyOf]) the grid should scroll to, consumed by the scroller. */
    var pendingMonthJump by mutableStateOf<Int?>(null)
}

/**
 * What the main timeline adds to the shared media grids: month highlight cards, places in the
 * day headers, the floating date label and the months view. Grids read it through
 * [LocalTimelineDecorations]; other pages provide nothing and keep the plain headers.
 */
@Immutable
data class TimelineDecorations(
    val index: TimelineIndex,
    val signals: TimelineSignals,
    /** Month headers become highlight cards (months under [MONTH_HIGHLIGHT_MIN_ITEMS] stay compact). */
    val monthHighlights: Boolean,
    /** Month cards carry a play button that starts a slideshow from the month. */
    val monthRecaps: Boolean,
    /** Month headers and day headers name the places. */
    val headerPlaces: Boolean,
    /** The sticky date header becomes a floating pill that opens the months view. */
    val floatingDateLabel: Boolean,
    val onOpenPlace: ((TimelinePlace) -> Unit)? = null,
    val onPlayMonth: ((TimelineMonth) -> Unit)? = null,
    val onOpenMonths: (() -> Unit)? = null,
)

val LocalTimelineDecorations = compositionLocalOf<TimelineDecorations?> { null }
