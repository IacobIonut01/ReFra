package com.dot.gallery.editor.ui

/**
 * How a tool's compact surface presents its controls. [AdjustmentSheet]
 * photo tools ride a swipeable bottom sheet over the canvas. Collapsed
 * controls have no enclosing background; expanded sheets have blur.
 * [Inline] remains available to other editor registries.
 * The wide/Desktop inspector ignores the taxonomy — it always owns a
 * fixed side pane.
 */
internal enum class EditorToolPresentation {
    AdjustmentSheet,
    Inline
}
