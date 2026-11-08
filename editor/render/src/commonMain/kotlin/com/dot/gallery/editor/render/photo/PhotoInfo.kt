package com.dot.gallery.editor.render.photo

import com.dot.gallery.editor.core.model.PixelSize

/**
 * Identity and intrinsic shape of a readable photo. [rawSize] is the stored
 * raster size; [uprightSize] applies the EXIF [orientation] (1..8), which
 * swaps the axes for orientations 5–8.
 */
data class PhotoInfo(
    val reference: String,
    val displayName: String,
    val rawSize: PixelSize,
    val orientation: Int = 1,
    /** A camera RAW, which the recipe's develop stage turns into pixels. */
    val isRaw: Boolean = false
) {
    init {
        require(reference.isNotBlank()) { "reference must be non-blank" }
        require(orientation in 1..8) { "orientation must be in 1..8: $orientation" }
    }

    val uprightSize: PixelSize =
        if (orientation in 5..8) PixelSize(rawSize.height, rawSize.width) else rawSize
}
