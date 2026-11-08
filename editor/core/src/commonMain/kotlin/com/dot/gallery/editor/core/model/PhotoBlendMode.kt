package com.dot.gallery.editor.core.model

/**
 * How a layer's linear RGB channels combine with the backdrop below it.
 * Blend functions never see alpha — the compositor applies them per
 * channel inside the standard source-over equation.
 */
enum class PhotoBlendMode {
    Normal,
    Multiply,
    Screen,
    Overlay,
    SoftLight
}
