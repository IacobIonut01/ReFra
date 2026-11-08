package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.graph.EffectDescriptor
import com.dot.gallery.editor.core.graph.EffectParameters
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoMapping
import com.dot.gallery.editor.core.model.PhotoWarpMapping
import kotlin.coroutines.coroutineContext
import kotlin.math.floor
import kotlinx.coroutines.ensureActive

/**
 * CPU implementation of `geometry.photo` v2: normalized crop, quarter-turn
 * rotation, axis flips and the continuous output-space warp over linear
 * RGBA frames.
 *
 * A neutral warp keeps the v1 path — each output pixel samples exactly one
 * input pixel via [PhotoMapping], nearest and lossless. An active warp
 * inverts through [PhotoWarpMapping] and samples bilinearly on
 * linear-light straight RGBA; neighbours outside the input are treated as
 * transparent (never edge-clamped), alpha is interpolated as a straight
 * component, and points the mapping rejects stay transparent. A full
 * pass-through geometry shares the input frame instead of copying.
 *
 * Frames are already upright, so the mapping runs with orientation 1.
 * Cancellation is checked per output row.
 */
class GeometryPhotoEffect : FrameEffect {

    override val descriptor: EffectDescriptor = EffectDescriptor.GeometryPhoto

    override suspend fun render(
        inputs: List<LinearRgbaFrame>,
        parameters: EffectParameters
    ): LinearRgbaFrame {
        if (inputs.size != 1) {
            throw UnsupportedOperationException(
                "geometry.photo takes exactly 1 input, got ${inputs.size}"
            )
        }
        val geometry = parameters as? EffectParameters.Geometry
            ?: throw UnsupportedOperationException(
                "geometry.photo requires EffectParameters.Geometry, got $parameters"
            )
        val input = inputs.single()
        return if (geometry.geometry.warp.isNeutral) {
            renderRigid(input, geometry.geometry)
        } else {
            renderWarped(input, geometry.geometry)
        }
    }

    /** The v1 path: one integer-mapped source pixel per output pixel. */
    private suspend fun renderRigid(
        input: LinearRgbaFrame,
        geometry: PhotoGeometry
    ): LinearRgbaFrame {
        val mapping = PhotoMapping(input.size, orientation = 1, geometry)
        if (mapping.isIdentity) return input

        val outWidth = mapping.outputSize.width
        val outHeight = mapping.outputSize.height
        val output = FloatArray(mapping.outputSize.pixelCount.toInt() * COMPONENTS_PER_PIXEL)
        for (y in 0 until outHeight) {
            coroutineContext.ensureActive()
            val rowStart = y * outWidth
            for (x in 0 until outWidth) {
                val packed = mapping.sourcePointPacked(x, y)
                val srcIndex =
                    (packed.toInt() * input.size.width + (packed shr 32).toInt()) *
                        COMPONENTS_PER_PIXEL
                val dstIndex = (rowStart + x) * COMPONENTS_PER_PIXEL
                output[dstIndex] = input.component(srcIndex)
                output[dstIndex + 1] = input.component(srcIndex + 1)
                output[dstIndex + 2] = input.component(srcIndex + 2)
                output[dstIndex + 3] = input.component(srcIndex + 3)
            }
        }
        return LinearRgbaFrame.fromOwned(mapping.outputSize, output)
    }

    /**
     * The warp path: bilinear sampling of straight linear RGBA through the
     * continuous inverse mapping. The output buffer starts transparent, so
     * rejected points and out-of-input neighbours already carry the right
     * zero.
     */
    private suspend fun renderWarped(
        input: LinearRgbaFrame,
        geometry: PhotoGeometry
    ): LinearRgbaFrame {
        val mapping = PhotoWarpMapping(input.size, orientation = 1, geometry)
        val outWidth = mapping.outputSize.width
        val outHeight = mapping.outputSize.height
        val inWidth = input.size.width
        val inHeight = input.size.height
        val output = FloatArray(mapping.outputSize.pixelCount.toInt() * COMPONENTS_PER_PIXEL)

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
                val source = mapping.sourcePoint(x.toDouble(), y.toDouble()) ?: continue
                val x0 = floor(source.x).toInt()
                val y0 = floor(source.y).toInt()
                val tx = (source.x - x0).toFloat()
                val ty = (source.y - y0).toFloat()
                val dstIndex = (rowStart + x) * COMPONENTS_PER_PIXEL
                for (c in 0 until COMPONENTS_PER_PIXEL) {
                    val topLeft = component(x0, y0, c)
                    val topRight = component(x0 + 1, y0, c)
                    val bottomLeft = component(x0, y0 + 1, c)
                    val bottomRight = component(x0 + 1, y0 + 1, c)
                    val top = topLeft + (topRight - topLeft) * tx
                    val bottom = bottomLeft + (bottomRight - bottomLeft) * tx
                    output[dstIndex + c] = top + (bottom - top) * ty
                }
            }
        }
        return LinearRgbaFrame.fromOwned(mapping.outputSize, output)
    }

    private companion object {
        const val COMPONENTS_PER_PIXEL = 4
    }
}
