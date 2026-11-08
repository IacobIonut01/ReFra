package com.dot.gallery.editor.core.model

/** Positive pixel dimensions; [pixelCount] is a Long so large frames can't overflow. */
data class PixelSize(val width: Int, val height: Int) {

    val pixelCount: Long = width.toLong() * height.toLong()

    init {
        require(width > 0) { "width must be positive: $width" }
        require(height > 0) { "height must be positive: $height" }
    }
}
