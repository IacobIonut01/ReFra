package com.dot.gallery.editor.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * One decoded layer input for the composite runtime shader: the layer's
 * upright source bitmap plus its mask rasterized at the same size —
 * `layerImageN`/`layerMaskN` in the generated shader. The
 * ([layerId], [sourceId]) pair is the renderer's recorded signature: a
 * recipe whose visible stack no longer matches is declined so the owner
 * can rebuild or fall back to the CPU pipeline.
 */
@Immutable
internal data class PhotoShaderLayerSource(
    val layerId: String,
    val sourceId: String,
    val bitmap: ImageBitmap,
    val maskBitmap: ImageBitmap
)
