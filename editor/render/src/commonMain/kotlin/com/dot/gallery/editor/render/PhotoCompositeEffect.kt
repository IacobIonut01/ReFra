package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.graph.EffectDescriptor
import com.dot.gallery.editor.core.graph.EffectParameters
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * CPU implementation of `photo.composite` v1: composites a placed layer
 * over the backdrop with the source-over equation and the layer's blend
 * mode on linear RGB — the shared [compositeLayerPixel] math, identical
 * to what the streaming exporter applies per band.
 *
 * Backdrop and layer must already share the output canvas size (the
 * placement effect produces exactly that). A fully transparent layer
 * preserves the backdrop bits exactly. Cancellation is per row.
 */
class PhotoCompositeEffect : FrameEffect {

    override val descriptor: EffectDescriptor = EffectDescriptor.PhotoComposite

    override suspend fun render(
        inputs: List<LinearRgbaFrame>,
        parameters: EffectParameters
    ): LinearRgbaFrame {
        if (inputs.size != 2) {
            throw UnsupportedOperationException(
                "photo.composite takes exactly 2 inputs, got ${inputs.size}"
            )
        }
        val composite = parameters as? EffectParameters.Composite
            ?: throw UnsupportedOperationException(
                "photo.composite requires EffectParameters.Composite, got $parameters"
            )
        val base = inputs[0]
        val layer = inputs[1]
        require(base.size == layer.size) {
            "photo.composite inputs must share the canvas size: " +
                "${base.size.width}x${base.size.height} vs " +
                "${layer.size.width}x${layer.size.height}"
        }
        val width = base.size.width
        val height = base.size.height
        val output = base.copyPixels()
        for (y in 0 until height) {
            coroutineContext.ensureActive()
            val rowStart = y * width
            for (x in 0 until width) {
                val index = (rowStart + x) * COMPONENTS_PER_PIXEL
                compositeLayerPixel(
                    output, index,
                    layer.component(index),
                    layer.component(index + 1),
                    layer.component(index + 2),
                    layer.component(index + ALPHA_OFFSET),
                    composite.blendMode,
                    composite.opacity,
                    composite.clipToBelow
                )
            }
        }
        return LinearRgbaFrame.fromOwned(base.size, output)
    }

    private companion object {
        const val COMPONENTS_PER_PIXEL = 4
        const val ALPHA_OFFSET = 3
    }
}
