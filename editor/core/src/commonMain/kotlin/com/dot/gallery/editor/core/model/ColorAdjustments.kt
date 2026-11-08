package com.dot.gallery.editor.core.model

import kotlin.math.sqrt

/**
 * Basic colour correction applied in order: exposure, then the tonal
 * operation (highlights/shadows/whites/blacks), then contrast around a
 * 0.18 pivot, then saturation against Rec.709 luma. All fields must be
 * finite and inside the declared ranges; the tonal parameters are the
 * normalized [-1, 1] form of the UI's [-100, +100] amounts.
 */
data class ColorAdjustments(
    val exposureStops: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f
) {
    init {
        require(exposureStops.isFinite() && exposureStops in -10f..10f) {
            "exposureStops must be finite in [-10, 10]: $exposureStops"
        }
        require(contrast.isFinite() && contrast in 0f..4f) {
            "contrast must be finite in [0, 4]: $contrast"
        }
        require(saturation.isFinite() && saturation in 0f..4f) {
            "saturation must be finite in [0, 4]: $saturation"
        }
        require(highlights.isFinite() && highlights in -1f..1f) {
            "highlights must be finite in [-1, 1]: $highlights"
        }
        require(shadows.isFinite() && shadows in -1f..1f) {
            "shadows must be finite in [-1, 1]: $shadows"
        }
        require(whites.isFinite() && whites in -1f..1f) {
            "whites must be finite in [-1, 1]: $whites"
        }
        require(blacks.isFinite() && blacks in -1f..1f) {
            "blacks must be finite in [-1, 1]: $blacks"
        }
    }

    /** True when every tonal parameter is at its neutral 0. */
    fun isToneNeutral(): Boolean =
        highlights == 0f && shadows == 0f && whites == 0f && blacks == 0f
}

/**
 * The UI's contrast control speaks amounts in [-100, +100]; the model keeps
 * the existing multiplicative factor in [0, 4]. The mapping is the square
 * curve — -100 -> 0, 0 -> 1, +100 -> 4.
 */
fun contrastFactor(amount: Float): Float {
    val x = 1f + amount / 100f
    return x * x
}

/** Inverse of [contrastFactor]: factor 1 -> 0, factor 4 -> +100. */
fun contrastAmount(factor: Float): Float =
    100f * (sqrt(factor) - 1f)
