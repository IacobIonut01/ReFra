package com.dot.gallery.editor.core.model

/**
 * Lens-blur recipe in model units — the UI speaks 0..100 / -180..180:
 * [mode] picks the radial or linear focus plane, [strength] scales the
 * maximum 16 px blur radius, [transition] widens the focus-to-blur
 * ramp, [highlightBokeh] re-brightens blurred highlights, [centerX]/
 * [centerY] place the focus point in normalized output space and
 * [angleDegrees] rotates the linear band (ignored by Radial).
 * Zero strength is the neutral, bit-preserving recipe.
 */
data class PhotoLensBlur(
    val mode: LensBlurMode = LensBlurMode.Radial,
    val strength: Float = 0f,
    val transition: Float = 0.6f,
    val highlightBokeh: Float = 0.12f,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val angleDegrees: Float = 0f
) {
    init {
        require(strength.isFinite() && strength in 0f..1f) {
            "lens blur strength must be finite inside 0..1: $strength"
        }
        require(transition.isFinite() && transition in 0.05f..1f) {
            "lens blur transition must be finite inside .05..1: $transition"
        }
        require(highlightBokeh.isFinite() && highlightBokeh in 0f..1f) {
            "lens blur highlightBokeh must be finite inside 0..1: " +
                "$highlightBokeh"
        }
        require(centerX.isFinite() && centerX in 0f..1f) {
            "lens blur centerX must be finite inside 0..1: $centerX"
        }
        require(centerY.isFinite() && centerY in 0f..1f) {
            "lens blur centerY must be finite inside 0..1: $centerY"
        }
        require(angleDegrees.isFinite() && angleDegrees in -180f..180f) {
            "lens blur angleDegrees must be finite inside -180..180: " +
                "$angleDegrees"
        }
    }

    val isNeutral: Boolean get() = strength == 0f
}
