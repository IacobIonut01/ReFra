package com.dot.gallery.editor.core.model

/**
 * Vignette recipe in model units — the UI speaks -100..100 / 0..100:
 * [amount] darkens (negative) or lightens (positive) the edges,
 * [midpoint] moves the falloff start, [feather] widens the transition,
 * [roundness] morphs the iso-line between the frame's square and a
 * circle, [highlights] protects bright tones from a darkening vignette.
 */
data class PhotoVignette(
    val amount: Float = 0f,
    val midpoint: Float = 0.5f,
    val feather: Float = 0.5f,
    val roundness: Float = 0f,
    val highlights: Float = 0f
) {
    init {
        require(amount.isFinite() && amount in -1f..1f) {
            "vignette amount must be finite inside -1..1: $amount"
        }
        require(midpoint.isFinite() && midpoint in 0f..1f) {
            "vignette midpoint must be finite inside 0..1: $midpoint"
        }
        require(feather.isFinite() && feather in 0.01f..1f) {
            "vignette feather must be finite inside .01..1: $feather"
        }
        require(roundness.isFinite() && roundness in -1f..1f) {
            "vignette roundness must be finite inside -1..1: $roundness"
        }
        require(highlights.isFinite() && highlights in 0f..1f) {
            "vignette highlights must be finite inside 0..1: $highlights"
        }
    }

    val isNeutral: Boolean get() = amount == 0f
}
