package com.dot.gallery.editor.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.dot.gallery.editor.core.model.NormalizedCrop
import com.dot.gallery.editor.core.model.PixelSize
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * A centred aspect crop in upright-source space. The displayed aspect after
 * the quarter-turn rotation is what the user picked — for odd turn counts
 * the axes swap, so the crop is computed against the *inverse* of the
 * requested ratio. The crop only ever shrinks one axis.
 */
internal fun centeredCrop(
    size: PixelSize,
    targetAspect: Double,
    quarterTurns: Int
): NormalizedCrop {
    require(targetAspect.isFinite() && targetAspect > 0.0) {
        "target aspect must be positive: $targetAspect"
    }
    val turns = ((quarterTurns % 4) + 4) % 4
    val preRotationAspect = if (turns % 2 == 1) 1.0 / targetAspect else targetAspect
    val current = size.width.toDouble() / size.height.toDouble()
    return if (current > preRotationAspect) {
        // Too wide — shrink the width, centred.
        val fraction = preRotationAspect / current
        val margin = snappedMargin((1.0 - fraction) / 2.0)
        NormalizedCrop(left = margin, top = 0.0, right = 1.0 - margin, bottom = 1.0)
    } else {
        // Too tall — shrink the height, centred.
        val fraction = current / preRotationAspect
        val margin = snappedMargin((1.0 - fraction) / 2.0)
        NormalizedCrop(left = 0.0, top = margin, right = 1.0, bottom = 1.0 - margin)
    }
}

// (1 - 0.8) / 2 evaluates to 0.09999999999999998 — sub-ULP arithmetic
// noise that floor-maps to one source pixel too many at rasterisation
// time. Quantising to 1e-12 removes the noise while staying far below
// any meaningful crop granularity (a 100 MP source needs ~1e-5).
private fun snappedMargin(margin: Double): Double =
    (margin * 1e12).roundToLong() / 1e12

/**
 * The image fraction currently visible in the viewport, in normalized
 * image coordinates (`0..1` on each axis). The canvas keeps the image
 * fit-centred at `zoom` with `pan` in canvas pixels; the returned rect is
 * what the navigator draws as its visible-region outline.
 */
internal fun visibleImageRect(
    canvasWidth: Float,
    canvasHeight: Float,
    imageWidth: Float,
    imageHeight: Float,
    zoom: Float,
    panX: Float,
    panY: Float
): Rect {
    if (canvasWidth <= 0f || canvasHeight <= 0f ||
        imageWidth <= 0f || imageHeight <= 0f || zoom <= 0f
    ) {
        return Rect(0f, 0f, 1f, 1f)
    }
    val fit = min(canvasWidth / imageWidth, canvasHeight / imageHeight)
    val displayWidth = imageWidth * fit * zoom
    val displayHeight = imageHeight * fit * zoom
    if (displayWidth <= 0f || displayHeight <= 0f) {
        return Rect(0f, 0f, 1f, 1f)
    }
    val imageLeft = (canvasWidth - displayWidth) / 2f + panX
    val imageTop = (canvasHeight - displayHeight) / 2f + panY
    val left = ((0f - imageLeft) / displayWidth).coerceIn(0f, 1f)
    val top = ((0f - imageTop) / displayHeight).coerceIn(0f, 1f)
    val right = ((canvasWidth - imageLeft) / displayWidth).coerceIn(0f, 1f)
    val bottom = ((canvasHeight - imageTop) / displayHeight).coerceIn(0f, 1f)
    return Rect(left, top, right, bottom)
}

/**
 * The clamped canvas pan (px) that places the normalized image point
 * `([fx], [fy])` at the canvas centre — the navigator's tap/drag-to-
 * recentre mapping. Edge points clamp to the legal pan range, so the
 * image can never be pushed fully off screen.
 */
internal fun navigatorPanForCenter(
    canvasWidth: Float,
    canvasHeight: Float,
    imageWidth: Float,
    imageHeight: Float,
    zoom: Float,
    fx: Float,
    fy: Float
): Offset {
    if (imageWidth <= 0f || imageHeight <= 0f || zoom <= 0f) {
        return Offset.Zero
    }
    val fit = min(canvasWidth / imageWidth, canvasHeight / imageHeight)
    val displayWidth = imageWidth * fit * zoom
    val displayHeight = imageHeight * fit * zoom
    val rawX = displayWidth * (0.5f - fx.coerceIn(0f, 1f))
    val rawY = displayHeight * (0.5f - fy.coerceIn(0f, 1f))
    val maxX = maxOf(0f, (displayWidth - canvasWidth) / 2f)
    val maxY = maxOf(0f, (displayHeight - canvasHeight) / 2f)
    return Offset(rawX.coerceIn(-maxX, maxX), rawY.coerceIn(-maxY, maxY))
}

/** Signed two-decimal formatting, e.g. exposure "-0.75" / "+1.25". KMP-safe. */
internal fun formatSignedDecimal(value: Float): String {
    val scaled = (value * 100f).roundToInt()
    val abs = abs(scaled)
    val sign = if (scaled < 0) "-" else "+"
    return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
}

/**
 * Signed compact formatting, e.g. "+0.7" / "-20" / "0" — rounds to two
 * decimals then strips a trailing ".0", so the parameter chips and ruler
 * labels stay short. KMP-safe.
 */
internal fun formatSignedCompact(value: Float): String {
    val scaled = (value * 100f).roundToInt()
    if (scaled == 0) return "0"
    val abs = abs(scaled)
    val whole = abs / 100
    val frac = abs % 100
    val digits = when {
        frac == 0 -> whole.toString()
        frac % 10 == 0 -> "$whole.${frac / 10}"
        else -> "$whole.${frac.toString().padStart(2, '0')}"
    }
    return (if (scaled < 0) "-" else "+") + digits
}

/** Unsigned two-decimal formatting, e.g. a "1.00" ratio. */
internal fun formatDecimal(value: Float): String {
    val scaled = (value * 100f).roundToInt()
    val abs = abs(scaled)
    return "${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
}

/** One-decimal zoom label, e.g. "2.0×". */
internal fun formatZoom(zoom: Float): String {
    val scaled = (zoom * 10f).roundToInt()
    return "${scaled / 10}.${scaled % 10}×"
}

/** Whole-percent zoom label, e.g. "100%", "200%" — the Figma V2 chip. */
internal fun formatZoomPercent(zoom: Float): String =
    "${(zoom * 100f).roundToInt()}%"

/** Signed one-decimal degree label, e.g. "-1.2°", "+45.0°", "0.0°". */
internal fun formatDegrees(value: Float): String {
    val scaled = (value * 10f).roundToInt()
    val abs = abs(scaled)
    if (scaled == 0) return "0.0°"
    return (if (scaled < 0) "-" else "+") + "${abs / 10}.${abs % 10}°"
}

/**
 * The crop rectangle in *displayed* normalized coordinates: the
 * [NormalizedCrop] carried through the quarter-turn rotation and the
 * axis flips, so its axes are the ones the user sees and drags. Each
 * coordinate is a fraction of the upright axis it maps to — for odd
 * turn counts the displayed x axis runs along the upright y axis.
 * Returns `[dx0, dy0, dx1, dy1]`.
 */
internal fun displayedCropRect(
    crop: NormalizedCrop,
    quarterTurns: Int,
    flipHorizontal: Boolean,
    flipVertical: Boolean
): DoubleArray {
    var dx0: Double
    var dy0: Double
    var dx1: Double
    var dy1: Double
    when (((quarterTurns % 4) + 4) % 4) {
        1 -> { // 90° CW: displayed x = inverted upright y, y = upright x
            dx0 = 1.0 - crop.bottom; dx1 = 1.0 - crop.top
            dy0 = crop.left;         dy1 = crop.right
        }
        2 -> {
            dx0 = 1.0 - crop.right;  dx1 = 1.0 - crop.left
            dy0 = 1.0 - crop.bottom; dy1 = 1.0 - crop.top
        }
        3 -> { // 90° CCW: displayed x = upright y, y = inverted upright x
            dx0 = crop.top;          dx1 = crop.bottom
            dy0 = 1.0 - crop.right;  dy1 = 1.0 - crop.left
        }
        else -> {
            dx0 = crop.left;         dx1 = crop.right
            dy0 = crop.top;          dy1 = crop.bottom
        }
    }
    // Flips apply on the rotated output — mirror the displayed axes last.
    if (flipHorizontal) { val t = dx0; dx0 = 1.0 - dx1; dx1 = 1.0 - t }
    if (flipVertical)   { val t = dy0; dy0 = 1.0 - dy1; dy1 = 1.0 - t }
    return doubleArrayOf(dx0, dy0, dx1, dy1)
}

/** Inverse of [displayedCropRect]. */
internal fun cropFromDisplayed(
    d: DoubleArray,
    quarterTurns: Int,
    flipHorizontal: Boolean,
    flipVertical: Boolean
): NormalizedCrop {
    var dx0 = d[0]; var dy0 = d[1]; var dx1 = d[2]; var dy1 = d[3]
    if (flipHorizontal) { val t = dx0; dx0 = 1.0 - dx1; dx1 = 1.0 - t }
    if (flipVertical)   { val t = dy0; dy0 = 1.0 - dy1; dy1 = 1.0 - t }
    return when (((quarterTurns % 4) + 4) % 4) {
        1 -> NormalizedCrop(
            left = dy0, right = dy1, top = 1.0 - dx1, bottom = 1.0 - dx0
        )
        2 -> NormalizedCrop(
            left = 1.0 - dx1, right = 1.0 - dx0,
            top = 1.0 - dy1, bottom = 1.0 - dy0
        )
        3 -> NormalizedCrop(
            left = 1.0 - dy1, right = 1.0 - dy0, top = dx0, bottom = dx1
        )
        else -> NormalizedCrop(left = dx0, top = dy0, right = dx1, bottom = dy1)
    }
}
