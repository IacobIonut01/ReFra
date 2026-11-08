package com.dot.gallery.editor.ui.video

import com.dot.gallery.editor.core.time.EDITOR_TICKS_PER_SECOND
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.FrameRate
import com.dot.gallery.editor.core.time.TimeRange
import kotlin.math.ceil
import kotlin.math.floor

/**
 * `mm:ss.mmm` display form below the hour, `h:mm:ss.mmm` at or past it —
 * manual formatting (no `String.format`, a JVM API this commonMain file
 * cannot import). Millisecond precision keeps a 30 fps frame step visible:
 * one frame reads `.033`, two `.066`. Negative input clamps to zero for
 * display.
 */
internal fun formatVideoTimecode(time: EditorTime): String {
    val millis = EditorTime(time.ticks.coerceAtLeast(0L))
        .toMicrosecondsFloor() / 1_000L
    val hours = millis / 3_600_000L
    val minutes = millis / 60_000L % 60L
    val seconds = millis / 1_000L % 60L
    val millisPart = millis % 1_000L
    val mm = minutes.toString().padStart(2, '0')
    val ss = seconds.toString().padStart(2, '0')
    val mmm = millisPart.toString().padStart(3, '0')
    return if (hours > 0L) "$hours:$mm:$ss.$mmm" else "$mm:$ss.$mmm"
}

/**
 * The transport readout — `mm:ss.cc` (hundredths, truncated) below the
 * hour and `h:mm:ss.cc` past it. The big transport numerals read at a
 * glance; the In/Out steppers keep the frame-exact [formatVideoTimecode].
 */
internal fun formatTransportTimecode(time: EditorTime): String {
    val centis = EditorTime(time.ticks.coerceAtLeast(0L))
        .toMicrosecondsFloor() / 10_000L
    val hours = centis / 360_000L
    val minutes = centis / 6_000L % 60L
    val seconds = centis / 100L % 60L
    val cc = (centis % 100L).toString().padStart(2, '0')
    val mm = minutes.toString().padStart(2, '0')
    val ss = seconds.toString().padStart(2, '0')
    return if (hours > 0L) "$hours:$mm:$ss.$cc" else "$mm:$ss.$cc"
}

/**
 * The timeline card's ruler label — `m:ss` (`h:mm:ss` past the hour) at
 * whole-second majors, and `m:ss.d` when majors are sub-second.
 */
internal fun formatStudioRulerTime(time: EditorTime, intervalTicks: Long): String {
    val micros = EditorTime(time.ticks.coerceAtLeast(0L)).toMicrosecondsFloor()
    val totalSeconds = micros / 1_000_000L
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds / 60L % 60L
    val ss = (totalSeconds % 60L).toString().padStart(2, '0')
    val base = if (hours > 0L) {
        "$hours:${minutes.toString().padStart(2, '0')}:$ss"
    } else {
        "$minutes:$ss"
    }
    return if (intervalTicks < EDITOR_TICKS_PER_SECOND) {
        "$base.${micros / 100_000L % 10L}"
    } else {
        base
    }
}

/**
 * The compact ruler label for a tick at [time] when majors sit
 * [intervalTicks] apart — honest units, never a clipped `mm:ss.mmm`:
 * sub-second intervals read `0.1s`, whole-second ones under a minute read
 * `4s`, past the minute `m:ss` (or `h:mm:ss` past the hour). The transport
 * and In/Out readouts keep the full `mm:ss.mmm`.
 */
internal fun formatRulerTime(time: EditorTime, intervalTicks: Long): String {
    val micros = EditorTime(time.ticks.coerceAtLeast(0L)).toMicrosecondsFloor()
    return when {
        intervalTicks < EDITOR_TICKS_PER_SECOND -> {
            val tenths = micros / 100_000L
            "${tenths / 10}.${tenths % 10}s"
        }
        micros < 60_000_000L -> "${micros / 1_000_000L}s"
        else -> {
            val totalSeconds = micros / 1_000_000L
            val hours = totalSeconds / 3_600L
            val minutes = totalSeconds / 60L % 60L
            val seconds = (totalSeconds % 60L).toString().padStart(2, '0')
            if (hours > 0L) {
                "$hours:${minutes.toString().padStart(2, '0')}:$seconds"
            } else {
                "$minutes:$seconds"
            }
        }
    }
}

/**
 * Snaps a dragged trim range to source-frame boundaries: the in-point
 * rounds DOWN to the frame at or before the drag, the out-point rounds UP
 * — the committed range always covers the whole span the user dragged
 * across, never a sliver of it. `outFraction == 1.0` lands exactly on
 * [sourceDuration] even when the duration is not frame-aligned (the last
 * frame's tail still belongs to the clip).
 *
 * [inFraction]/[outFraction] must be finite and non-reversed after
 * clamping to `[0, 1]` — a RangeSlider emits equal endpoints when its
 * handles meet, so equal input is legal and widens to one frame rather
 * than crashing. The result is at least one frame long — or the whole
 * duration when the source is shorter than a frame at [rate].
 *
 * [fixedStart]/[fixedEnd] carry the committed endpoint when that handle
 * never moved: the slider's Float round-trip of an exact frame boundary
 * can ceil one frame too far, so an unmoved endpoint is reused verbatim
 * rather than re-snapped. The one-frame minimum then extends only the
 * endpoint that actually changed.
 */
internal fun snapTrimRange(
    sourceDuration: EditorTime,
    rate: FrameRate,
    inFraction: Double,
    outFraction: Double,
    fixedStart: EditorTime? = null,
    fixedEnd: EditorTime? = null
): TimeRange {
    require(inFraction.isFinite() && outFraction.isFinite()) {
        "trim fractions must be finite: $inFraction..$outFraction"
    }
    val inF = inFraction.coerceIn(0.0, 1.0)
    val outF = outFraction.coerceIn(0.0, 1.0)
    require(inF <= outF) { "trim range must not be reversed: $inF..$outF" }
    val ticksPerFrame = rate.ticksPerFrame
    val durationTicks = sourceDuration.ticks
    // Fraction -> frame index in Double is exact enough here: the timeline
    // is capped at 24 h, well inside Double's integer precision, and the
    // rounding to whole frames happens in integer space afterwards.
    var inTicks = fixedStart?.ticks ?: (
        floor(inF * durationTicks / ticksPerFrame).toLong() * ticksPerFrame
        )
    var outTicks = fixedEnd?.ticks ?: if (outF >= 1.0) {
        durationTicks
    } else {
        minOf(
            ceil(outF * durationTicks / ticksPerFrame).toLong() * ticksPerFrame,
            durationTicks
        )
    }
    val minSegment = minOf(ticksPerFrame, durationTicks)
    if (outTicks - inTicks < minSegment) {
        // Only the endpoint the user actually moved absorbs the minimum.
        when {
            fixedStart != null && fixedEnd == null ->
                outTicks = minOf(inTicks + minSegment, durationTicks)
            fixedEnd != null && fixedStart == null ->
                inTicks = maxOf(0L, outTicks - minSegment)
            fixedStart == null && fixedEnd == null -> {
                outTicks = minOf(inTicks + minSegment, durationTicks)
                inTicks = maxOf(0L, outTicks - minSegment)
            }
            // Both endpoints committed already form a valid range.
        }
    }
    return TimeRange(EditorTime(inTicks), EditorTime(outTicks - inTicks))
}
