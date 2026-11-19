package com.dot.gallery.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoWhiteBalance
import com.dot.gallery.editor.core.model.WhiteBalanceModel
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.render.photo.PhotoEditorState
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The one-tap Auto enhance: a deterministic tone and white-balance guess
 * read off the unedited preview. Only the fields the analysis owns are
 * replaced — exposure, contrast, highlights and shadows in the colour
 * stage, white balance and vibrance in finishing; every other recipe
 * field (saturation, whites/blacks, geometry, layers, retouch…) carries
 * over untouched.
 */
internal fun autoEnhance(pixels: SrgbPixels, base: PhotoEdits): PhotoEdits {
    val width = pixels.size.width
    val height = pixels.size.height
    val count = width.toLong() * height.toLong()
    if (count <= 0L || count > Int.MAX_VALUE.toLong()) return base
    val argb = pixels.copyPixels()
    val n = argb.size

    val lumas = FloatArray(n)
    var clippedCount = 0
    var saturationTotal = 0f
    var neutralCount = 0
    var neutralR = 0f
    var neutralG = 0f
    var neutralB = 0f
    for (i in argb.indices) {
        val pixel = argb[i]
        val r = ((pixel shr 16) and 0xFF) / 255f
        val g = ((pixel shr 8) and 0xFF) / 255f
        val b = (pixel and 0xFF) / 255f
        val luma = SRGB_LUMA_R * r + SRGB_LUMA_G * g + SRGB_LUMA_B * b
        lumas[i] = luma
        if (luma > CLIPPED_LUMA) clippedCount++
        val hi = max(r, max(g, b))
        val lo = min(r, min(g, b))
        val saturation = if (hi > 0f) (hi - lo) / hi else 0f
        saturationTotal += saturation
        if (luma in NEUTRAL_LUMA_RANGE && saturation < NEUTRAL_MAX_SATURATION) {
            neutralCount++
            neutralR += srgbToLinear(r)
            neutralG += srgbToLinear(g)
            neutralB += srgbToLinear(b)
        }
    }
    lumas.sort()

    fun percentile(fraction: Float): Float {
        val index = (fraction * (n - 1)).roundToInt().coerceIn(0, n - 1)
        return lumas[index]
    }

    val p5 = percentile(0.05f)
    val p25 = percentile(0.25f)
    val median = percentile(0.50f)
    val p75 = percentile(0.75f)
    val clippedFraction = clippedCount.toFloat() / n

    val linearMedian = srgbToLinear(median)
    val exposureStops = (EXPOSURE_GAIN *
        log2(MIDDLE_GRAY / max(linearMedian, MIN_LINEAR_LEVEL))
        ).coerceIn(-MAX_EXPOSURE_STOPS, MAX_EXPOSURE_STOPS)
        .toFloat()
    val highlights = if (clippedFraction > CLIPPED_FRACTION_THRESHOLD) {
        -min(MAX_HIGHLIGHT_RECOVERY, clippedFraction * HIGHLIGHT_RECOVERY_SCALE)
    } else {
        0f
    }
    val shadows = if (p5 < SHADOW_LUMA_THRESHOLD) {
        min(MAX_SHADOW_LIFT, (SHADOW_LUMA_THRESHOLD - p5) * SHADOW_LIFT_SCALE)
    } else {
        0f
    }
    val contrast = if (p75 - p25 < MIN_TONAL_SPAN) {
        1f + min(
            MAX_CONTRAST_BOOST,
            MIN_TONAL_SPAN - (p75 - p25)
        )
    } else {
        1f
    }

    val color = base.color.copy(
        exposureStops = exposureStops,
        contrast = contrast,
        highlights = highlights,
        shadows = shadows
    )

    val whiteBalance = estimateWhiteBalance(
        neutralCount = neutralCount,
        total = n,
        meanR = if (neutralCount > 0) neutralR / neutralCount else 0f,
        meanG = if (neutralCount > 0) neutralG / neutralCount else 0f,
        meanB = if (neutralCount > 0) neutralB / neutralCount else 0f,
        fallback = base.finishing.whiteBalance
    )
    val meanSaturation = saturationTotal / n
    val vibrance = if (meanSaturation < VIBRANCE_LOW_SATURATION) {
        VIBRANCE_BOOST
    } else {
        VIBRANCE_NUDGE
    }
    val finishing = base.finishing.copy(
        whiteBalance = whiteBalance,
        grade = base.finishing.grade.copy(vibrance = vibrance)
    )
    return base.copy(color = color, finishing = finishing)
}

/**
 * Grey-world white balance over the neutral candidates: the (kelvin, tint)
 * whose [WhiteBalanceModel] gains make the mean colour most neutral wins
 * a coarse grid search, then the recipe moves 60 % of the way from neutral
 * toward it. The base recipe survives when too few pixels qualify or the
 * estimate already sits near neutral.
 */
private fun estimateWhiteBalance(
    neutralCount: Int,
    total: Int,
    meanR: Float,
    meanG: Float,
    meanB: Float,
    fallback: PhotoWhiteBalance
): PhotoWhiteBalance {
    if (neutralCount <= 0 ||
        neutralCount.toDouble() < total * NEUTRAL_MIN_FRACTION
    ) {
        return fallback
    }
    var bestKelvin = NEUTRAL_KELVIN
    var bestTint = 0f
    var bestScore = Float.MAX_VALUE
    var kelvin = WB_KELVIN_MIN
    while (kelvin <= WB_KELVIN_MAX) {
        var tint = WB_TINT_MIN
        while (tint <= WB_TINT_MAX) {
            val gains = WhiteBalanceModel.gains(
                PhotoWhiteBalance(kelvin.toFloat(), tint.toFloat())
            )
            val r = meanR * gains[0]
            val g = meanG * gains[1]
            val b = meanB * gains[2]
            val score = max(abs(r - g), max(abs(g - b), abs(r - b)))
            if (score < bestScore) {
                bestScore = score
                bestKelvin = kelvin
                bestTint = tint.toFloat()
            }
            tint += WB_TINT_STEP
        }
        kelvin += WB_KELVIN_STEP
    }
    if (abs(bestKelvin - NEUTRAL_KELVIN) <= NEUTRAL_KELVIN_TOLERANCE &&
        abs(bestTint) <= NEUTRAL_TINT_TOLERANCE
    ) {
        return fallback
    }
    return PhotoWhiteBalance(
        temperatureKelvin = NEUTRAL_KELVIN +
            WB_BLEND * (bestKelvin - NEUTRAL_KELVIN),
        tint = WB_BLEND * bestTint
    )
}

/**
 * The dock's Auto destination state: [selected] while the live document
 * still equals the last Auto result, [enabled] only when a preview and
 * an idle document exist, and [onSelect] applying Auto as one history
 * step — or undoing it when it is already the document's state.
 */
internal class PhotoAutoDock(
    val selected: Boolean,
    val enabled: Boolean,
    val onSelect: () -> Unit
)

/**
 * Tracks the last Auto result per source so the dock item can show its
 * selected state and offer the one-tap undo. The comparison is the whole
 * recipe: any later edit — or an undo that leaves it — drops the
 * selection, and a matching document again (redo) re-selects it.
 */
@Composable
internal fun rememberPhotoAutoDock(
    state: PhotoEditorState,
    actions: PhotoEditorActions
): PhotoAutoDock {
    var lastEdits by remember(state.info?.reference) {
        mutableStateOf<PhotoEdits?>(null)
    }
    val documentEdits = state.document?.toEdits()
    val selected = lastEdits != null && documentEdits == lastEdits
    val enabled = state.originalPreview != null &&
        state.document != null &&
        !state.interactionActive &&
        !state.toolEditing &&
        !state.isDrafting &&
        !state.opening &&
        !state.exporting
    return PhotoAutoDock(
        selected = selected,
        enabled = enabled,
        onSelect = {
            if (selected) {
                actions.undo()
                lastEdits = null
            } else {
                val pixels = state.originalPreview
                val current = state.document?.toEdits()
                if (pixels != null && current != null) {
                    val next = autoEnhance(pixels, current)
                    actions.applyEdits(AUTO_HISTORY_LABEL, next)
                    lastEdits = next
                }
            }
        }
    )
}

internal const val AUTO_HISTORY_LABEL = "Auto"

/** sRGB transfer function to linear light, per channel. */
private fun srgbToLinear(channel: Float): Float =
    if (channel <= SRGB_LINEAR_THRESHOLD) {
        channel / SRGB_LINEAR_DIVISOR
    } else {
        ((channel + SRGB_LINEAR_OFFSET) / SRGB_LINEAR_SCALE)
            .pow(SRGB_LINEAR_EXPONENT)
    }

private const val SRGB_LUMA_R = 0.2126f
private const val SRGB_LUMA_G = 0.7152f
private const val SRGB_LUMA_B = 0.0722f
private const val CLIPPED_LUMA = 0.98f
private const val CLIPPED_FRACTION_THRESHOLD = 0.005f
private const val HIGHLIGHT_RECOVERY_SCALE = 20f
private const val MAX_HIGHLIGHT_RECOVERY = 0.6f
private const val SHADOW_LUMA_THRESHOLD = 0.08f
private const val SHADOW_LIFT_SCALE = 5f
private const val MAX_SHADOW_LIFT = 0.5f
private const val MIN_TONAL_SPAN = 0.35f
private const val MAX_CONTRAST_BOOST = 0.25f
private const val MIDDLE_GRAY = 0.18
private const val MIN_LINEAR_LEVEL = 1e-4f
private const val EXPOSURE_GAIN = 0.7
private const val MAX_EXPOSURE_STOPS = 1.5
private const val NEUTRAL_LUMA_RANGE_MIN = 0.15f
private const val NEUTRAL_LUMA_RANGE_MAX = 0.85f
private const val NEUTRAL_MAX_SATURATION = 0.6f
private const val NEUTRAL_MIN_FRACTION = 0.02
private const val VIBRANCE_LOW_SATURATION = 0.35f
private const val VIBRANCE_BOOST = 0.15f
private const val VIBRANCE_NUDGE = 0.05f
private const val NEUTRAL_KELVIN = 6500
private const val WB_KELVIN_MIN = 2000
private const val WB_KELVIN_MAX = 12000
private const val WB_KELVIN_STEP = 100
private const val WB_TINT_MIN = -100
private const val WB_TINT_MAX = 100
private const val WB_TINT_STEP = 2
private const val NEUTRAL_KELVIN_TOLERANCE = 300
private const val NEUTRAL_TINT_TOLERANCE = 5f
private const val WB_BLEND = 0.6f
private const val SRGB_LINEAR_THRESHOLD = 0.04045f
private const val SRGB_LINEAR_DIVISOR = 12.92f
private const val SRGB_LINEAR_OFFSET = 0.055f
private const val SRGB_LINEAR_SCALE = 1.055f
private const val SRGB_LINEAR_EXPONENT = 2.4f
private val NEUTRAL_LUMA_RANGE = NEUTRAL_LUMA_RANGE_MIN..NEUTRAL_LUMA_RANGE_MAX
