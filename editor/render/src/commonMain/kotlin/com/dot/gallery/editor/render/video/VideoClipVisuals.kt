package com.dot.gallery.editor.render.video

import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.PhotoBlendMode
import com.dot.gallery.editor.core.model.VideoContentMode
import com.dot.gallery.editor.core.model.VideoLayerTransform

/**
 * A partial visual recipe for a clip — every field is nullable and a null
 * means "keep the clip's current value", so a transform control and a
 * colour control can write into the same open draft or one-shot edit
 * without clobbering each other. Whole-recipe writes pass every field.
 */
data class VideoClipVisuals(
    val transform: VideoLayerTransform? = null,
    val contentMode: VideoContentMode? = null,
    val opacity: Float? = null,
    val blendMode: PhotoBlendMode? = null,
    val adjustments: ColorAdjustments? = null
)
