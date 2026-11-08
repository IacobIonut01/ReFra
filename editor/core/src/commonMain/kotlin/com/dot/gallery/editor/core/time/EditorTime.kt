package com.dot.gallery.editor.core.time

/**
 * Ticks per second for all editor timeline math. The base is chosen to be
 * exactly divisible by common integer and NTSC frame rates and by common audio
 * sample periods, so frame indices and sample counts round-trip without drift.
 */
const val EDITOR_TICKS_PER_SECOND: Long = 705_600_000L

/**
 * A position or duration on the editing timeline, counted in ticks at
 * [EDITOR_TICKS_PER_SECOND]. Arithmetic is checked: overflow throws
 * [ArithmeticException] rather than silently wrapping.
 */
@JvmInline
value class EditorTime(val ticks: Long) : Comparable<EditorTime> {

    override fun compareTo(other: EditorTime): Int = ticks.compareTo(other.ticks)

    operator fun plus(other: EditorTime): EditorTime =
        EditorTime(checkedAdd(ticks, other.ticks))

    operator fun minus(other: EditorTime): EditorTime =
        EditorTime(checkedSubtract(ticks, other.ticks))

    companion object {
        val Zero: EditorTime = EditorTime(0L)

        /** Converts whole milliseconds to ticks; overflow throws. */
        fun fromMilliseconds(value: Long): EditorTime =
            EditorTime(checkedMultiply(value, EDITOR_TICKS_PER_SECOND / 1_000L))

        /**
         * Converts whole microseconds to ticks, rounding the sub-tick
         * remainder down. Negative values (e.g. a raw AAC priming PTS) are
         * rejected — clamp at the source before converting.
         */
        fun fromMicroseconds(value: Long): EditorTime {
            require(value >= 0L) { "microseconds must be non-negative: $value" }
            val seconds = value / 1_000_000L
            val remainderUs = value % 1_000_000L
            return EditorTime(
                checkedAdd(
                    checkedMultiply(seconds, EDITOR_TICKS_PER_SECOND),
                    checkedMultiply(remainderUs, EDITOR_TICKS_PER_SECOND) / 1_000_000L
                )
            )
        }
    }

    /**
     * Whole microseconds covered by this time, rounded down (sub-microsecond
     * remainder is dropped). Non-negative times only; overflow throws.
     */
    fun toMicrosecondsFloor(): Long {
        require(ticks >= 0L) { "time must be non-negative: $ticks" }
        val seconds = ticks / EDITOR_TICKS_PER_SECOND
        val remainderTicks = ticks % EDITOR_TICKS_PER_SECOND
        return checkedAdd(
            checkedMultiply(seconds, 1_000_000L),
            checkedMultiply(remainderTicks, 1_000_000L) / EDITOR_TICKS_PER_SECOND
        )
    }

    /**
     * First sample index at or after [time] for a [rate]-per-second clock —
     * `ceil(time * rate)` computed exactly on the tick quotient/remainder, no
     * floating point. A frame that starts inside the sample period counts.
     */
    fun samplesAtOrAfter(rate: Int): Long {
        require(rate > 0) { "sample rate must be positive: $rate" }
        require(ticks >= 0L) { "time must be non-negative: $ticks" }
        val seconds = ticks / EDITOR_TICKS_PER_SECOND
        val remainderTicks = ticks % EDITOR_TICKS_PER_SECOND
        val base = checkedMultiply(seconds, rate.toLong())
        val rem = checkedMultiply(remainderTicks, rate.toLong())
        return checkedAdd(
            base,
            (rem + EDITOR_TICKS_PER_SECOND - 1L) / EDITOR_TICKS_PER_SECOND
        )
    }
}
