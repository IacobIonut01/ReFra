/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.runtime.Stable
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
