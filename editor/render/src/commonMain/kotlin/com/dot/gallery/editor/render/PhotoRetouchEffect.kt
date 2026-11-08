package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.graph.EffectDescriptor
import com.dot.gallery.editor.core.graph.EffectParameters

/**
 * CPU implementation of `photo.retouch` v1 over the finished linear frame. A
 * full-frame node carries `originY = 0` and `fullSize = frame.size`; a neutral
 * recipe returns the input frame untouched.
 */
class PhotoRetouchEffect : FrameEffect {

    override val descriptor: EffectDescriptor = EffectDescriptor.PhotoRetouch

    override suspend fun render(
        inputs: List<LinearRgbaFrame>,
        parameters: EffectParameters
    ): LinearRgbaFrame {
        if (inputs.size != 1) {
            throw UnsupportedOperationException(
                "photo.retouch takes exactly 1 input, got ${inputs.size}"
            )
        }
        val params = parameters as? EffectParameters.Retouch
            ?: throw UnsupportedOperationException(
                "photo.retouch requires EffectParameters.Retouch, got $parameters"
            )
        return PhotoRetouchRenderer(params.retouch, params.fullSize)
            .process(inputs.single(), params.originY)
    }
}
