package com.dot.gallery.editor.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The phone All tools sheet's body height. The sheet opens at [basePx];
 * once the list is scrolled away from its first row it grows to [maxPx]
 * (the window minus a top margin) so more tools fit on screen, and it
 * returns to [basePx] when the sheet closes.
 *
 * It only reports a target height and never takes scroll deltas from the
 * list, so the rows scroll exactly as before and a programmatic scroll
 * (accessibility, tests) still moves them.
 */
@Stable
internal class GrowingSheetBody(basePx: Float) {
    var basePx by mutableFloatStateOf(basePx)
        private set
    var maxPx by mutableFloatStateOf(basePx)
        private set

    /** Whether the user has scrolled the list since the sheet opened. */
    var grown by mutableStateOf(false)
        private set

    /** The height the body animates to. Never below [basePx]. */
    val targetPx: Float
        get() = if (grown) maxOf(basePx, maxPx) else basePx

    /** Applies the window's limits; a window too short to grow keeps [basePx]. */
    fun updateLimits(base: Float, max: Float) {
        basePx = base
        maxPx = max
    }

    /** The list left (or returned to) its first row. Growing sticks until [reset]. */
    fun onScrolledFromTop(scrolled: Boolean) {
        if (scrolled) grown = true
    }

    /** Back to the opening height, for the next time the sheet opens. */
    fun reset() {
        grown = false
    }
}
