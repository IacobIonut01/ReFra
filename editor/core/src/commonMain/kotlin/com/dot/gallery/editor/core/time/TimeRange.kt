package com.dot.gallery.editor.core.time

/**
 * A half-open timeline range `[start, start + duration)`. Both media source
 * ranges and clip placements use this convention.
 */
data class TimeRange(val start: EditorTime, val duration: EditorTime) {

    /** First instant after the range. Overflow on [start] + [duration] throws. */
    val endExclusive: EditorTime

    init {
        require(start.ticks >= 0L) { "range start must be non-negative: ${start.ticks}" }
        require(duration.ticks > 0L) { "range duration must be positive: ${duration.ticks}" }
        endExclusive = start + duration
    }

    operator fun contains(time: EditorTime): Boolean = time >= start && time < endExclusive
}
