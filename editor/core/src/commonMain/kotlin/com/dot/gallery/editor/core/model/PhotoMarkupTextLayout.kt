package com.dot.gallery.editor.core.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The bounds rules of a markup label, shared by the canvas handles and tests.
 * A label's box is [emWidth] by [emHeight] font sizes, centred on its position
 * and rotated about that centre. Every edit keeps the rotated box inside the
 * frame: the size is capped to what fits, then the centre is clamped. A box that
 * cannot fit even at the smallest size covers the frame instead of escaping it,
 * so it stays grabbable.
 */
object PhotoMarkupTextLayout {
    const val MIN_SIZE = 0.02f
    const val MAX_SIZE = 0.2f
    const val LINE_HEIGHT = 1.2f

    /** Width and height of the rotated box's axis-aligned bounds, in pixels. */
    fun rotatedExtent(width: Float, height: Float, degrees: Float): Pair<Float, Float> {
        val radians = degrees * PI.toFloat() / 180f
        val c = abs(cos(radians))
        val s = abs(sin(radians))
        return (width * c + height * s) to (width * s + height * c)
    }

    /** The largest size at which [text]'s rotated box still fits the frame. */
    fun maxFittingSize(
        text: PhotoMarkupText,
        emWidth: Float,
        emHeight: Float,
        frameWidth: Float,
        frameHeight: Float
    ): Float {
        if (frameWidth <= 0f || frameHeight <= 0f) return text.size
        val fontPixels = text.size * frameHeight
        val (extentW, extentH) = rotatedExtent(emWidth * fontPixels, emHeight * fontPixels, text.rotation)
        val scaleW = if (extentW > 0f) frameWidth / extentW else Float.MAX_VALUE
        val scaleH = if (extentH > 0f) frameHeight / extentH else Float.MAX_VALUE
        return (text.size * min(scaleW, scaleH)).coerceIn(MIN_SIZE, MAX_SIZE)
    }

    /** [text] with its centre clamped so the rotated box stays in the frame. */
    fun clampCentre(
        text: PhotoMarkupText,
        emWidth: Float,
        emHeight: Float,
        frameWidth: Float,
        frameHeight: Float
    ): PhotoMarkupText {
        if (frameWidth <= 0f || frameHeight <= 0f) return text
        val fontPixels = text.size * frameHeight
        val (extentW, extentH) = rotatedExtent(emWidth * fontPixels, emHeight * fontPixels, text.rotation)
        return text.copy(
            x = clampAxis(text.x * frameWidth, extentW, frameWidth) / frameWidth,
            y = clampAxis(text.y * frameHeight, extentH, frameHeight) / frameHeight
        )
    }

    /** [text] with both checks applied: the size fits, then the centre stays in. */
    fun fit(
        text: PhotoMarkupText,
        emWidth: Float,
        emHeight: Float,
        frameWidth: Float,
        frameHeight: Float
    ): PhotoMarkupText {
        val cap = maxFittingSize(text, emWidth, emHeight, frameWidth, frameHeight)
        return clampCentre(
            text.copy(size = text.size.coerceIn(MIN_SIZE, cap)),
            emWidth, emHeight, frameWidth, frameHeight
        )
    }

    /** Degrees normalized into (-180, 180]. */
    fun normalizeRotation(degrees: Float): Float {
        var value = degrees % 360f
        if (value > 180f) value -= 360f
        if (value <= -180f) value += 360f
        return value
    }

    /** Strips blank lines at both ends; blank lines inside are kept. */
    fun normalizeText(text: String): String =
        text.lines()
            .dropWhile { it.isBlank() }
            .dropLastWhile { it.isBlank() }
            .joinToString("\n")

    private fun clampAxis(centre: Float, extent: Float, frame: Float): Float {
        val half = extent / 2f
        val low = min(half, frame - half)
        val high = maxOf(half, frame - half)
        // Centres stay on the frame so the recipe's own bounds always hold.
        return centre.coerceIn(low, high).coerceIn(0f, frame)
    }
}
