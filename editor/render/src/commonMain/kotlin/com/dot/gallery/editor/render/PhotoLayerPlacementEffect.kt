package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.graph.EffectDescriptor
import com.dot.gallery.editor.core.graph.EffectParameters
import com.dot.gallery.editor.core.model.PhotoLayerMapping
import kotlin.coroutines.coroutineContext
import kotlin.math.floor
import kotlinx.coroutines.ensureActive

/**
 * CPU implementation of `photo.layer.place` v1: inverse-maps each canvas
 * output pixel into the layer through [PhotoLayerMapping] and samples it
 * bilinearly on linear-light straight RGBA. Neighbours outside the layer
 * input are transparent (never edge-clamped), alpha is interpolated as a
 * straight component and then multiplied by the evaluated layer mask;
 * rejected points stay transparent.
 *
 * The input is the layer's already-upright preview frame, so the mapping
 * runs with orientation 1. Cancellation is checked per output row.
 */
class PhotoLayerPlacementEffect : FrameEffect {

    override val descriptor: EffectDescriptor = EffectDescriptor.PhotoLayerPlacement

    override suspend fun render(
        inputs: List<LinearRgbaFrame>,
        parameters: EffectParameters
    ): LinearRgbaFrame {
        if (inputs.size != 1) {
            throw UnsupportedOperationException(
                "photo.layer.place takes exactly 1 input, got ${inputs.size}"
            )
        }
        val placement = parameters as? EffectParameters.LayerPlacement
            ?: throw UnsupportedOperationException(
                "photo.layer.place requires EffectParameters.LayerPlacement, got $parameters"
            )
        val input = inputs.single()
        val outputSize = placement.outputSize
        val reference = placement.referenceOutputSize
        // The stored translation is in full base-output pixels — scale it
        // into this render's canvas before mapping.
        val transform = placement.layer.transform.copy(
            translationX = placement.layer.transform.translationX *
                (outputSize.width.toFloat() / reference.width),
            translationY = placement.layer.transform.translationY *
                (outputSize.height.toFloat() / reference.height)
        )
        val mapping = PhotoLayerMapping(
            input.size, orientation = 1, outputSize, transform
        )
        val mask = placement.layer.mask
        val inWidth = input.size.width
        val inHeight = input.size.height
        val outWidth = outputSize.width
        val outHeight = outputSize.height
        val output = FloatArray(outputSize.pixelCount.toInt() * COMPONENTS_PER_PIXEL)

        fun component(x: Int, y: Int, c: Int): Float =
            if (x in 0 until inWidth && y in 0 until inHeight) {
                input.component((y * inWidth + x) * COMPONENTS_PER_PIXEL + c)
            } else {
                0f
            }

        for (y in 0 until outHeight) {
            coroutineContext.ensureActive()
            val rowStart = y * outWidth
            for (x in 0 until outWidth) {
                val sample = mapping.sourcePoint(x.toDouble(), y.toDouble()) ?: continue
                val px = sample.raw.x
                val py = sample.raw.y
                val x0 = floor(px).toInt()
                val y0 = floor(py).toInt()
                val tx = (px - x0).toFloat()
                val ty = (py - y0).toFloat()
                val dstIndex = (rowStart + x) * COMPONENTS_PER_PIXEL
                var alpha = 0f
                for (c in 0 until COMPONENTS_PER_PIXEL) {
                    val topLeft = component(x0, y0, c)
                    val topRight = component(x0 + 1, y0, c)
                    val bottomLeft = component(x0, y0 + 1, c)
                    val bottomRight = component(x0 + 1, y0 + 1, c)
                    val top = topLeft + (topRight - topLeft) * tx
                    val bottom = bottomLeft + (bottomRight - bottomLeft) * tx
                    val value = top + (bottom - top) * ty
                    if (c == ALPHA_OFFSET) alpha = value else output[dstIndex + c] = value
                }
                val maskAlpha = photoMaskAlpha(
                    mask, sample.normalizedX, sample.normalizedY, input.size
                )
                output[dstIndex + ALPHA_OFFSET] = alpha * maskAlpha
            }
        }
        return LinearRgbaFrame.fromOwned(outputSize, output)
    }

    private companion object {
        const val COMPONENTS_PER_PIXEL = 4
        const val ALPHA_OFFSET = 3
    }
}
