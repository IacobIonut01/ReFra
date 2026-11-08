package com.dot.gallery.editor.core.model

/**
 * Geometric photo edits applied on the upright frame: [crop] resolved in
 * pixels first, then [quarterTurns] 90-degree rotations, then the axis flips
 * of the rotated result, and finally the continuous [warp] sampled in
 * output space.
 */
data class PhotoGeometry(
    val crop: NormalizedCrop = NormalizedCrop(),
    val quarterTurns: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val warp: PhotoWarp = PhotoWarp()
) {
    init {
        require(quarterTurns in 0..3) {
            "quarterTurns must be in 0..3: $quarterTurns"
        }
    }
}
