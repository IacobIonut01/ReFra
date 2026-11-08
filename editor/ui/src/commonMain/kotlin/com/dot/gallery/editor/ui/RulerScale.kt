package com.dot.gallery.editor.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A value-anchored ladder for [EditorPrecisionRuler]: a tick every
 * [minor] units, taller ticks at multiples of [mid] and the tallest at
 * multiples of [major], labelled at multiples of [labelEvery]. The
 * straighten ruler uses 1° minor / 5° major ticks across ±45°.
 */
internal data class RulerScale(
    val minor: Float,
    val mid: Float,
    val major: Float,
    val labelEvery: Float,
    val labelSuffix: String = ""
) {
    init {
        require(minor > 0f && mid >= minor && major >= mid && labelEvery >= minor) {
            "ruler scale steps must grow: $this"
        }
    }

    fun isMultiple(value: Float, of: Float): Boolean {
        val q = value / of
        return abs(q - q.roundToInt()) < 1e-3f
    }

    /** 0 and the majors — where the crop rulers tick haptically. */
    fun isDetent(value: Float): Boolean = abs(value) < 1e-4f || isMultiple(value, major)

    /**
     * The accepted value the drag reached or crossed from [from] to [to],
     * if any — so a fast drag over a major still ticks once.
     */
    fun crossedHapticValue(from: Float, to: Float, accept: (Float) -> Boolean): Float? {
        if (from.isNaN()) return to.takeIf(accept)
        val lo = minOf(from, to)
        val hi = maxOf(from, to)
        val first = ceil(lo / major) * major
        var v = first
        var hit: Float? = null
        while (v <= hi + 1e-4f) {
            if (v != from && accept(v)) hit = v
            v += major
        }
        if (lo < 0f && hi > 0f && accept(0f)) hit = 0f
        return hit ?: to.takeIf { it != from && accept(it) }
    }
}

/**
 * Paints a [RulerScale] ladder on the ruler's 12 dp-inset track.
 *
 * Three weights: a subtle minor tick, a taller mid tick, and the tallest
 * ladder line — shorter than the ruler's needle — at every
 * [RulerScale.labelEvery] value, drawn in [accentColor] so the solid
 * numbers read through the ladder. The ladder
 * thins itself to the space it has — minors are only painted while they
 * stay at least 6 dp apart — so it looks the same on a 320 dp phone table
 * row and a wide inspector, and at any display density.
 */
internal fun DrawScope.drawRulerScale(
    scale: RulerScale,
    range: ClosedFloatingPointRange<Float>,
    tickTop: Float,
    labelTop: Float,
    trackStart: Float,
    trackEnd: Float,
    majorColor: Color,
    tickColor: Color,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    drawLabels: Boolean = true,
    accentColor: Color = majorColor
) {
    val span = range.endInclusive - range.start
    if (span <= 0f) return
    fun xFor(v: Float) = trackStart + (trackEnd - trackStart) * ((v - range.start) / span)
    val first = ceil(range.start / scale.minor).toInt()
    val last = floor(range.endInclusive / scale.minor).toInt()
    // Thin the minors to the available width; checkpoints and mid ticks
    // always paint.
    val trackPx = (trackEnd - trackStart).coerceAtLeast(1f)
    val minorCount = (span / scale.minor).coerceAtLeast(1f)
    val minorSpacing = trackPx / minorCount
    val minSpacing = 6.dp.toPx()
    val stride = ceil(minSpacing / minorSpacing.coerceAtLeast(0.01f))
        .toInt().coerceAtLeast(1)
    for (i in first..last) {
        val v = i * scale.minor
        val checkpoint = scale.isMultiple(v, scale.labelEvery)
        val major = scale.isMultiple(v, scale.major)
        val mid = scale.isMultiple(v, scale.mid)
        if (!checkpoint && !major && !mid && i % stride != 0) continue
        // The ladder stays short of the needle: the live value's 22 dp bar
        // must read as the tallest line on the track, checkpoints second.
        val heightDp = when {
            checkpoint -> 12.dp
            major || mid -> 8.dp
            else -> 4.dp
        }
        val widthPx = when {
            checkpoint -> 2.dp.toPx()
            major || mid -> 1.5.dp.toPx()
            else -> 1.dp.toPx()
        }
        val color = when {
            checkpoint -> accentColor.copy(alpha = 0.9f)
            major || mid -> majorColor.copy(alpha = 0.4f)
            else -> tickColor.copy(alpha = 0.25f)
        }
        drawRoundRect(
            color = color,
            topLeft = Offset(xFor(v) - widthPx / 2f, tickTop),
            size = Size(widthPx, heightDp.toPx()),
            cornerRadius = CornerRadius(widthPx / 2f)
        )
    }
    if (!drawLabels) return
    val firstLabel = ceil(range.start / scale.labelEvery).toInt()
    val lastLabel = floor(range.endInclusive / scale.labelEvery).toInt()
    // Labels are dropped rather than crowded: a label whose box would
    // touch the previous one is skipped, so large text keeps whole
    // numbers on the scale instead of an unreadable run.
    val minGap = 6.dp.toPx()
    var previousRight = Float.NEGATIVE_INFINITY
    for (i in firstLabel..lastLabel) {
        val v = i * scale.labelEvery
        val layout = textMeasurer.measure(formatSignedCompact(v) + scale.labelSuffix, style = labelStyle)
        val labelX = (xFor(v) - layout.size.width / 2f)
            .coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
        if (labelX < previousRight + minGap) continue
        drawText(layout, topLeft = Offset(labelX, labelTop))
        previousRight = labelX + layout.size.width
    }
}
