package com.dot.gallery.editor.core.model

/** A point in normalized layer coordinates: `(0,0)` top-left, `(1,1)` bottom-right. */
data class NormalizedPoint(
    val x: Float,
    val y: Float
) {
    init {
        require(x.isFinite() && x in 0f..1f) { "x must be in [0, 1]: $x" }
        require(y.isFinite() && y in 0f..1f) { "y must be in [0, 1]: $y" }
    }
}
