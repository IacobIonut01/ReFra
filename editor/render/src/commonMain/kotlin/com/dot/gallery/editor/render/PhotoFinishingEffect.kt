package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.graph.EffectDescriptor
import com.dot.gallery.editor.core.graph.EffectParameters

/**
 * CPU implementation of `photo.finishing` v1: white balance, tone
 * curves, vignette and grain over the composited linear frame — one
 * input, the recipe from [EffectParameters.Finishing]. A full-frame
 * node carries `originY = 0` and `fullSize = frame.size`; a neutral
 * finishing returns the input frame untouched.
 */
class PhotoFinishingEffect : FrameEffect {

    override val descriptor: EffectDescriptor = EffectDescriptor.PhotoFinishing

    override suspend fun render(
        inputs: List<LinearRgbaFrame>,
        parameters: EffectParameters
    ): LinearRgbaFrame {
        if (inputs.size != 1) {
            throw UnsupportedOperationException(
                "photo.finishing takes exactly 1 input, got ${inputs.size}"
            )
        }
        val params = parameters as? EffectParameters.Finishing
            ?: throw UnsupportedOperationException(
                "photo.finishing requires EffectParameters.Finishing, " +
                    "got $parameters"
            )
        val input = inputs.single()
        return processPhotoFinishing(
            input, params.finishing, params.originY, params.fullSize
        )
    }
}
