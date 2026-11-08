package com.dot.gallery.editor.core.model

/**
 * A crop rectangle in normalized `[0, 1]` coordinates of the upright source
 * frame. All components must be finite with `left < right` and
 * `top < bottom`.
 */
data class NormalizedCrop(
    val left: Double = 0.0,
    val top: Double = 0.0,
    val right: Double = 1.0,
    val bottom: Double = 1.0
) {
    init {
        require(
            left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()
        ) { "crop components must be finite" }
        require(left in 0.0..1.0 && top in 0.0..1.0 &&
            right in 0.0..1.0 && bottom in 0.0..1.0
        ) { "crop components must be within [0, 1]" }
        require(left < right) { "crop left must be < right: $left >= $right" }
        require(top < bottom) { "crop top must be < bottom: $top >= $bottom" }
    }
}
