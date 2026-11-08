package com.dot.gallery.editor.core.model

/**
 * Continuous output-space warp of a photo: rotation in degrees, a
 * horizontal/vertical keystone in percent and a uniform zoom. The warp
 * applies on the rigid crop/turn/flip output frame, so it never changes
 * [PhotoMapping.outputSize]; it only moves where each output pixel
 * samples from.
 *
 * Validation is atomic: an out-of-range or non-finite argument fails the
 * constructor before any field is committed.
 */
data class PhotoWarp(
    val rotationDegrees: Float = 0f,
    val verticalPerspective: Float = 0f,
    val horizontalPerspective: Float = 0f,
    val scale: Float = 1f,
) {
    init {
        require(rotationDegrees.isFinite() && rotationDegrees in -45f..45f) {
            "rotationDegrees must be in -45..45: $rotationDegrees"
        }
        require(verticalPerspective.isFinite() && verticalPerspective in -100f..100f) {
            "verticalPerspective must be in -100..100: $verticalPerspective"
        }
        require(horizontalPerspective.isFinite() && horizontalPerspective in -100f..100f) {
            "horizontalPerspective must be in -100..100: $horizontalPerspective"
        }
        require(scale.isFinite() && scale in 1f..2f) {
            "scale must be in 1..2: $scale"
        }
    }

    /** A neutral warp takes the integer [PhotoMapping] path and stays bit-exact. */
    val isNeutral: Boolean
        get() = rotationDegrees == 0f && verticalPerspective == 0f &&
            horizontalPerspective == 0f && scale == 1f
}
