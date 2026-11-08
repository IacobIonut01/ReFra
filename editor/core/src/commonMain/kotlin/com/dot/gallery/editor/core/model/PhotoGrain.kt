package com.dot.gallery.editor.core.model

/**
 * Film-grain recipe in model units — the UI speaks 0..100: [amount] is
 * the strength, [size] the grain cell scale, [roughness] the cell-edge
 * hardness (1 stays blocky, 0 smoothest), [seed] pins the deterministic
 * noise field so previews and exports always agree.
 */
data class PhotoGrain(
    val amount: Float = 0f,
    val size: Float = 0.24f,
    val roughness: Float = 0.35f,
    val seed: Int = 1337
) {
    init {
        require(amount.isFinite() && amount in 0f..1f) {
            "grain amount must be finite inside 0..1: $amount"
        }
        require(size.isFinite() && size in 0.01f..1f) {
            "grain size must be finite inside .01..1: $size"
        }
        require(roughness.isFinite() && roughness in 0f..1f) {
            "grain roughness must be finite inside 0..1: $roughness"
        }
    }

    val isNeutral: Boolean get() = amount == 0f
}
