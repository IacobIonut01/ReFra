package com.dot.gallery.editor.render.video

import com.dot.gallery.editor.core.graph.EffectParameters
import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.PhotoBlendMode
import com.dot.gallery.editor.core.model.PhotoLayerMapping
import com.dot.gallery.editor.core.model.PhotoLayerTransform
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.model.VideoClip
import com.dot.gallery.editor.core.model.VideoContentMode
import com.dot.gallery.editor.core.model.VideoLayerTransform
import com.dot.gallery.editor.render.BasicColorEffect
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.render.compositeLayerPixel
import com.dot.gallery.editor.render.decodeSrgbPixel
import com.dot.gallery.editor.render.encodeAlpha
import com.dot.gallery.editor.render.encodeChannel
import com.dot.gallery.editor.render.toLinearFrame
import kotlin.coroutines.coroutineContext
import kotlin.math.floor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The single visual compositor for multi-track video: each decoded,
 * already-upright [SrgbPixels] layer is colour-graded, inverse-mapped onto
 * the sequence canvas through the [PhotoLayerMapping] fit/transform
 * contract and composited bottom-to-top with the shared
 * [compositeLayerPixel] source-over equation — the same linear-light
 * straight-alpha math the photo pipeline and its GPU shader already
 * implement, so a stacked video frame and a stacked photo agree.
 *
 * Output space is a pixel canvas, not a source: [reference] is the
 * document's canvas ([com.dot.gallery.editor.core.model.VideoSequence.size])
 * while [canvas] is this render's actual target — a bounded preview passes
 * a smaller canvas and translations scale proportionally, exactly the
 * `referenceOutputSize` rule the photo placement effect applies.
 *
 * The first layer is the Main clip — a neutral transform fills the canvas
 * fit-inside and leaves the mismatch surround transparent black (the
 * opaque export bakes it to black). Overlays follow in the order given,
 * each honouring its own transform, opacity and blend mode. A fully
 * transparent layer contribution leaves the backdrop bits exactly.
 */
class VideoFrameCompositor {

    /** One composited layer: decoded upright pixels plus the clip recipe. */
    data class Layer(
        val pixels: SrgbPixels,
        val transform: VideoLayerTransform = VideoLayerTransform(),
        val contentMode: VideoContentMode = VideoContentMode.Fit,
        val opacity: Float = 1f,
        val blendMode: PhotoBlendMode = PhotoBlendMode.Normal,
        val adjustments: ColorAdjustments = ColorAdjustments()
    ) {
        init {
            require(opacity.isFinite() && opacity in 0f..1f) {
                "opacity must be in [0, 1]: $opacity"
            }
        }

        companion object {

            /** The clip recipe as a compositor layer over decoded [pixels]. */
            fun of(clip: VideoClip, pixels: SrgbPixels) = Layer(
                pixels = pixels,
                transform = clip.transform,
                contentMode = clip.contentMode,
                opacity = clip.opacity,
                blendMode = clip.blendMode,
                adjustments = clip.adjustments
            )
        }
    }

    private val grader = BasicColorEffect()

    // One render at a time owns these; they are what keeps a video frame from
    // allocating two full-size float buffers.
    private val renderLock = Mutex()
    private var canvasBuffer = FloatArray(0)
    private var layerBuffer = FloatArray(0)

    /**
     * Composites [layers] onto a [canvas] of transparent black and returns
     * the packed sRGB result. [reference] is the canvas the stored
     * translations are expressed in; it defaults to [canvas] so a full-size
     * render needs no scaling.
     */
    suspend fun composite(
        canvas: PixelSize,
        layers: List<Layer>,
        reference: PixelSize = canvas
    ): SrgbPixels {
        require(canvas.width > 0 && canvas.height > 0) {
            "canvas must be non-empty: ${canvas.width}x${canvas.height}"
        }
        require(reference.width > 0 && reference.height > 0) {
            "reference canvas must be non-empty: " +
                "${reference.width}x${reference.height}"
        }
        unchangedFrame(canvas, layers)?.let { return it }
        return renderLock.withLock {
            val componentCount = canvas.pixelCount.toInt() * COMPONENTS_PER_PIXEL
            if (canvasBuffer.size != componentCount) {
                canvasBuffer = FloatArray(componentCount)
            } else {
                canvasBuffer.fill(0f)
            }
            for (layer in layers) {
                if (layer.opacity <= 0f) continue
                paintLayer(linearSource(layer), layer, canvas, reference, canvasBuffer)
            }
            encodeCanvas(canvas)
        }
    }

    /**
     * The result when the frame would come out unchanged: one fully visible
     * opaque layer, already canvas-sized and neutral. Linear light is the round
     * trip every 8-bit value survives, so skipping it changes no pixel.
     */
    private fun unchangedFrame(canvas: PixelSize, layers: List<Layer>): SrgbPixels? {
        val visible = layers.singleOrNull { it.opacity > 0f } ?: return null
        val unchanged = visible.opacity == 1f &&
            visible.pixels.size == canvas &&
            visible.transform == VideoLayerTransform() &&
            visible.adjustments == ColorAdjustments() &&
            visible.pixels.isOpaque
        return if (unchanged) visible.pixels else null
    }

    /** The layer as linear floats, graded when its recipe asks for it. */
    private suspend fun linearSource(layer: Layer): LinearSource {
        val size = layer.pixels.size
        if (layer.adjustments != ColorAdjustments()) {
            val graded = grader.render(
                listOf(layer.pixels.toLinearFrame()),
                EffectParameters.Color(layer.adjustments)
            )
            return LinearSource(size, graded.readOnlyStorage)
        }
        val needed = size.pixelCount.toInt() * COMPONENTS_PER_PIXEL
        if (layerBuffer.size != needed) layerBuffer = FloatArray(needed)
        val buffer = layerBuffer
        val width = size.width
        forEachRow(size) { y ->
            val rowStart = y * width
            for (x in 0 until width) {
                decodeSrgbPixel(
                    layer.pixels.component(rowStart + x),
                    buffer,
                    (rowStart + x) * COMPONENTS_PER_PIXEL
                )
            }
        }
        return LinearSource(size, buffer)
    }

    private suspend fun encodeCanvas(canvas: PixelSize): SrgbPixels {
        val width = canvas.width
        val source = canvasBuffer
        val output = IntArray(width * canvas.height)
        forEachRow(canvas) { y ->
            val rowStart = y * width
            for (x in 0 until width) {
                val i = (rowStart + x) * COMPONENTS_PER_PIXEL
                output[rowStart + x] = (encodeAlpha(source[i + ALPHA_OFFSET]) shl 24) or
                    (encodeChannel(source[i]) shl 16) or
                    (encodeChannel(source[i + 1]) shl 8) or
                    encodeChannel(source[i + 2])
            }
        }
        return SrgbPixels.fromOwned(canvas, output)
    }

    /**
     * Inverse-maps every canvas pixel into [source] through
     * [PhotoLayerMapping] — bilinear, out-of-bounds neighbours transparent —
     * and composites the sample over [dst] with the layer recipe. This is
     * the fused form of `PhotoLayerPlacementEffect` + `PhotoCompositeEffect`
     * for one layer: identical math, one pass, no intermediate frame. Rows
     * touch only their own part of [dst], so they can run in parallel.
     */
    private suspend fun paintLayer(
        source: LinearSource,
        layer: Layer,
        canvas: PixelSize,
        reference: PixelSize,
        dst: FloatArray
    ) {
        // PhotoLayerMapping's fit is always min-inside; the clip's content
        // mode picks the canvas-fit factor the stored scale multiplies —
        // rescale the transform so `fit * scale` equals `modeFit * scale`.
        val minFit = minOf(
            canvas.width.toDouble() / source.size.width,
            canvas.height.toDouble() / source.size.height
        )
        val modeFit = when (layer.contentMode) {
            VideoContentMode.Fit -> minFit
            VideoContentMode.Fill -> maxOf(
                canvas.width.toDouble() / source.size.width,
                canvas.height.toDouble() / source.size.height
            )
            VideoContentMode.Crop -> 1.0
        }
        val transform = PhotoLayerTransform(
            translationX = layer.transform.translationX *
                (canvas.width.toFloat() / reference.width),
            translationY = layer.transform.translationY *
                (canvas.height.toFloat() / reference.height),
            scale = layer.transform.scale *
                (modeFit / minFit).toFloat(),
            rotationDegrees = layer.transform.rotationDegrees,
            flipHorizontal = layer.transform.flipHorizontal,
            flipVertical = layer.transform.flipVertical
        )
        val inverse = PhotoLayerMapping(
            source.size, orientation = 1, canvas, transform
        ).inverse
        val outWidth = canvas.width
        val halfWidth = inverse.uprightWidth / 2
        val halfHeight = inverse.uprightHeight / 2
        val maxU = inverse.uprightWidth - .5
        val maxV = inverse.uprightHeight - .5
        val blendMode = layer.blendMode
        val opacity = layer.opacity
        forEachRow(canvas) { y ->
            val rowStart = y * outWidth
            val dy = (y + .5 - inverse.centerY) / inverse.scale
            for (x in 0 until outWidth) {
                val dx = (x + .5 - inverse.centerX) / inverse.scale
                var u = inverse.cos * dx + inverse.sin * dy + halfWidth - .5
                var v = -inverse.sin * dx + inverse.cos * dy + halfHeight - .5
                if (inverse.flipHorizontal) u = inverse.uprightWidth - 1 - u
                if (inverse.flipVertical) v = inverse.uprightHeight - 1 - v
                if (u < -.5 || u > maxU || v < -.5 || v > maxV) continue
                val x0 = floor(u).toInt()
                val y0 = floor(v).toInt()
                val tx = (u - x0).toFloat()
                val ty = (v - y0).toFloat()
                compositeLayerPixel(
                    dst, (rowStart + x) * COMPONENTS_PER_PIXEL,
                    source.bilinear(x0, y0, 0, tx, ty),
                    source.bilinear(x0, y0, 1, tx, ty),
                    source.bilinear(x0, y0, 2, tx, ty),
                    source.bilinear(x0, y0, ALPHA_OFFSET, tx, ty),
                    blendMode, opacity, clipToBelow = false
                )
            }
        }
    }

    /**
     * Runs [row] for every row of [size], across the CPU cores for a frame big
     * enough to be worth it. Small frames stay on the caller's dispatcher.
     */
    private suspend fun forEachRow(size: PixelSize, row: (Int) -> Unit) {
        val height = size.height
        if (size.pixelCount < PARALLEL_MIN_PIXELS) {
            for (y in 0 until height) {
                coroutineContext.ensureActive()
                row(y)
            }
            return
        }
        val bandHeight = maxOf(1, height / PARALLEL_BANDS)
        coroutineScope {
            for (start in 0 until height step bandHeight) {
                launch(Dispatchers.Default) {
                    for (y in start until minOf(start + bandHeight, height)) {
                        ensureActive()
                        row(y)
                    }
                }
            }
        }
    }

    private class LinearSource(val size: PixelSize, private val components: FloatArray) {
        private val width = size.width
        private val height = size.height

        /** One component; a neighbour outside the frame reads as transparent. */
        private fun at(x: Int, y: Int, c: Int): Float =
            if (x in 0 until width && y in 0 until height) {
                components[(y * width + x) * COMPONENTS_PER_PIXEL + c]
            } else {
                0f
            }

        fun bilinear(x0: Int, y0: Int, c: Int, tx: Float, ty: Float): Float {
            val topLeft = at(x0, y0, c)
            val topRight = at(x0 + 1, y0, c)
            val bottomLeft = at(x0, y0 + 1, c)
            val bottomRight = at(x0 + 1, y0 + 1, c)
            val top = topLeft + (topRight - topLeft) * tx
            val bottom = bottomLeft + (bottomRight - bottomLeft) * tx
            return top + (bottom - top) * ty
        }
    }

    private companion object {
        const val COMPONENTS_PER_PIXEL = 4
        const val ALPHA_OFFSET = 3
        const val PARALLEL_MIN_PIXELS = 1L shl 18
        const val PARALLEL_BANDS = 16
    }
}
