package com.dot.gallery.editor.core.time

/**
 * A rational frame rate, `numerator` frames per `denominator` seconds
 * (e.g. 30000/1001 for NTSC 29.97).
 *
 * The rate must be exactly representable in ticks: rates whose frame period is
 * not a whole number of [EDITOR_TICKS_PER_SECOND] ticks are rejected instead of
 * silently rounded.
 *
 * Equality is field-based, so two rates that are mathematically equivalent but
 * written differently (for example 30000/1001 versus 60000/2002) compare
 * unequal. Both remain individually valid rates.
 */
data class FrameRate(val numerator: Int, val denominator: Int = 1) {

    /** Whole ticks spanned by a single frame. */
    val ticksPerFrame: Long

    init {
        require(numerator > 0) { "frame rate numerator must be positive: $numerator" }
        require(denominator > 0) { "frame rate denominator must be positive: $denominator" }
        val ticksPerDenominator =
            checkedMultiply(EDITOR_TICKS_PER_SECOND, denominator.toLong())
        require(ticksPerDenominator % numerator.toLong() == 0L) {
            "frame rate $numerator/$denominator is not exactly representable in ticks"
        }
        ticksPerFrame = ticksPerDenominator / numerator
    }

    /** Timeline position of the start of [frame]; overflow throws. */
    fun timeAtFrame(frame: Long): EditorTime {
        require(frame >= 0L) { "frame must be non-negative: $frame" }
        return EditorTime(checkedMultiply(frame, ticksPerFrame))
    }

    /** Index of the frame containing [time] (the frame at or before it). */
    fun frameAtOrBefore(time: EditorTime): Long {
        require(time.ticks >= 0L) { "time must be non-negative: ${time.ticks}" }
        return time.ticks / ticksPerFrame
    }
}
