package com.dot.gallery.editor.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The crop tool's camera over the canvas media: `screen = scale * fit +
 * translation`, in canvas-region pixels, where `fit` is where a point
 * draws at the canvas' resting fit. The crop window writes it so its
 * fixed, centred frame always shows the crop; [EditorCanvas] applies it to
 * the media layer in the draw phase. Inactive outside the tool.
 */
@Stable
internal class CropViewTransform {
    var active by mutableStateOf(false)
    var scale by mutableFloatStateOf(1f)
    var translationX by mutableFloatStateOf(0f)
    var translationY by mutableFloatStateOf(0f)

    fun set(scale: Float, translationX: Float, translationY: Float) {
        this.scale = scale
        this.translationX = translationX
        this.translationY = translationY
    }

    fun reset() {
        active = false
        set(1f, 0f, 0f)
    }
}
