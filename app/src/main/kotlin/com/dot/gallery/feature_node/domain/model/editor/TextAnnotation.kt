package com.dot.gallery.feature_node.domain.model.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * @param position Normalized position (0..1) relative to the canvas/image.
 */
data class TextAnnotation(
    val text: String,
    val color: Color,
    val position: Offset,
    val fontSize: Float = 0.045f,
    val rotation: Float = 0f // degrees
)
