package com.dot.gallery.editor.core.model

/**
 * Placement of a layer on the base output canvas. [translationX]/
 * [translationY] are in base-output pixels relative to a centred
 * fit-inside placement; [scale] multiplies that fit scale;
 * [rotationDegrees] rotates the layer around the canvas centre; the
 * flips mirror the layer inside its own bounds.
 */
data class PhotoLayerTransform(
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
) {
    init {
        require(translationX.isFinite() && translationY.isFinite()) {
            "translation must be finite: ($translationX, $translationY)"
        }
        require(scale.isFinite() && scale in 0.01f..10f) {
            "scale must be in [0.01, 10]: $scale"
        }
        require(rotationDegrees.isFinite() && rotationDegrees in -180f..180f) {
            "rotation must be in [-180, 180]: $rotationDegrees"
        }
    }
}
