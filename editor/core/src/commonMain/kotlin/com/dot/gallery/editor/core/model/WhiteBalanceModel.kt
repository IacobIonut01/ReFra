package com.dot.gallery.editor.core.model

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The white-balance gain model shared by the renderer and the
 * neutral-point picker. Temperature maps piecewise-linearly onto
 * t in -1..1 (2000 K → -1, 6500 K → 0, 12000 K → 1) and tint onto
 * u in -1..1; each linear-light channel is scaled by an exponential stop
 * of `R: +.35 t + .10 u`, `G: -.20 u`, `B: -.35 t + .10 u` — red and blue
 * oppose on temperature, green answers tint alone.
 *
 * [neutralFor] is the exact inverse for one colour: it returns the recipe
 * whose gains make a straight-linear patch (r, g, b) equal in all three
 * channels, clamped to the recipe's authored ranges.
 */
object WhiteBalanceModel {

    private const val TEMPERATURE_STOPS = 0.35
    private const val TINT_RB_STOPS = 0.10
    private const val TINT_G_STOPS = -0.20
    private const val NEUTRAL_KELVIN = 6500f
    private const val WARM_SPAN = 5500f
    private const val COOL_SPAN = 4500f

    /**
     * Below this linear level a channel carries too little signal to
     * solve against — a near-black patch returns null instead of an
     * extreme recipe.
     */
    const val MIN_SAMPLE_LEVEL = 1e-4f

    /** The per-channel linear gains (r, g, b) of [wb]. */
    fun gains(wb: PhotoWhiteBalance): FloatArray {
        val t = if (wb.temperatureKelvin >= NEUTRAL_KELVIN) {
            (wb.temperatureKelvin - NEUTRAL_KELVIN) / WARM_SPAN
        } else {
            (wb.temperatureKelvin - NEUTRAL_KELVIN) / COOL_SPAN
        }
        val u = wb.tint / 100f
        return floatArrayOf(
            2.0.pow(TEMPERATURE_STOPS * t + TINT_RB_STOPS * u).toFloat(),
            2.0.pow(TINT_G_STOPS * u).toFloat(),
            2.0.pow(-TEMPERATURE_STOPS * t + TINT_RB_STOPS * u).toFloat()
        )
    }

    /**
     * The recipe that neutralises the straight-linear colour (r, g, b):
     * equalising red/blue fixes t, then the green/magenta balance of the
     * red-blue mean fixes u. Kelvin rounds to whole degrees and tint to
     * tenths; both clamp to 2000..12000 K and -100..100, so an extreme
     * cast is reduced as far as the recipe allows rather than rejected.
     * Returns null when any channel is non-finite or at or below
     * [MIN_SAMPLE_LEVEL].
     */
    fun neutralFor(r: Float, g: Float, b: Float): PhotoWhiteBalance? {
        if (!r.isFinite() || !g.isFinite() || !b.isFinite()) return null
        if (r <= MIN_SAMPLE_LEVEL || g <= MIN_SAMPLE_LEVEL ||
            b <= MIN_SAMPLE_LEVEL
        ) {
            return null
        }
        val lr = log2(r.toDouble())
        val lg = log2(g.toDouble())
        val lb = log2(b.toDouble())
        // R' - B' = lr - lb + 2·.35 t = 0
        val t = ((lb - lr) / (2.0 * TEMPERATURE_STOPS)).coerceIn(-1.0, 1.0)
        // (R' + B')/2 - G' = (lr + lb)/2 - lg + (.10 + .20) u = 0, with the
        // temperature terms cancelling in the red/blue mean.
        val u = ((lg - (lr + lb) / 2.0) / (TINT_RB_STOPS - TINT_G_STOPS))
            .coerceIn(-1.0, 1.0)
        val kelvin = if (t >= 0.0) {
            NEUTRAL_KELVIN + WARM_SPAN * t
        } else {
            NEUTRAL_KELVIN + COOL_SPAN * t
        }
        return PhotoWhiteBalance(
            temperatureKelvin = kelvin.roundToInt().toFloat()
                .coerceIn(2000f, 12000f),
            tint = ((u * 100.0 * 10.0).roundToInt() / 10f)
                .coerceIn(-100f, 100f)
        )
    }

    private fun log2(x: Double): Double = ln(x) / ln(2.0)
}
