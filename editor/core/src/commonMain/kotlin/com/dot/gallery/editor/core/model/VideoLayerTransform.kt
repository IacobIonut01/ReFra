package com.dot.gallery.editor.core.model

/**
 * Placement of a visual clip on the sequence canvas — the same placement
 * contract and bounds as [PhotoLayerTransform]: [translationX]/
 * [translationY] are in sequence-canvas pixels relative to a centred
 * fit-inside placement, [scale] multiplies that fit scale,
 * [rotationDegrees] rotates the clip around the canvas centre and the
 * flips mirror it inside its own bounds. Sharing the photo bounds keeps a
 * persisted transform legal on every renderer; the default is the
 * identity — a legacy clip fills the canvas exactly as before.
 */
data class VideoLayerTransform(
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
