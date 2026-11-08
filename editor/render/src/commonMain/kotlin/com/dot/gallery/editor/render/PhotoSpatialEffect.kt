package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.graph.EffectDescriptor
import com.dot.gallery.editor.core.graph.EffectParameters

/**
 * CPU implementation of `photo.spatial` v1: the Details 3x3
 * local-contrast pass and the masked lens blur over the composited
 * linear frame — one input, the recipe from [EffectParameters.Spatial].
 * A full-frame node carries `originY = 0` and `fullSize = frame.size`;
 * a neutral spatial recipe returns the input frame untouched.
 */
class PhotoSpatialEffect : FrameEffect {

    override val descriptor: EffectDescriptor = EffectDescriptor.PhotoSpatial

    override suspend fun render(
        inputs: List<LinearRgbaFrame>,
        parameters: EffectParameters
    ): LinearRgbaFrame {
        if (inputs.size != 1) {
            throw UnsupportedOperationException(
                "photo.spatial takes exactly 1 input, got ${inputs.size}"
            )
        }
        val params = parameters as? EffectParameters.Spatial
            ?: throw UnsupportedOperationException(
                "photo.spatial requires EffectParameters.Spatial, " +
                    "got $parameters"
            )
        val input = inputs.single()
        return processPhotoSpatial(
            input, params.spatial, params.originY, params.fullSize
        )
    }
}
