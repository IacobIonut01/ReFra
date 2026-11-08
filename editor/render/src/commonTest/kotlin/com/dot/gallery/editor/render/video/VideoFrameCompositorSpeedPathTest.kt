package com.dot.gallery.editor.render.video

import com.dot.gallery.editor.core.graph.EffectParameters
import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.PhotoBlendMode
import com.dot.gallery.editor.core.model.PhotoLayerMapping
import com.dot.gallery.editor.core.model.PhotoLayerTransform
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.model.VideoContentMode
import com.dot.gallery.editor.core.model.VideoLayerTransform
import com.dot.gallery.editor.render.BasicColorEffect
import com.dot.gallery.editor.render.LinearRgbaFrame
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.render.compositeLayerPixel
import com.dot.gallery.editor.render.encodeChannel
import com.dot.gallery.editor.render.encodeChannelExact
import com.dot.gallery.editor.render.toLinearFrame
import com.dot.gallery.editor.render.toSrgbPixels
import kotlin.math.floor
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest

/**
 * Holds the fast compositor to the original per-pixel algorithm, kept here
 * verbatim as the reference: the same pixels, whatever path a frame takes.
 */
class VideoFrameCompositorSpeedPathTest {

    private fun noise(size: PixelSize, seed: Int, opaque: Boolean = true): SrgbPixels {
        val random = Random(seed)
        return SrgbPixels(
            size,
            IntArray(size.width * size.height) {
                val alpha = if (opaque) 0xFF else random.nextInt(256)
                (alpha shl 24) or (random.nextInt() and 0xFFFFFF)
            }
        )
    }

    /** The compositor as it was: one pixel, one mapping lookup, one thread. */
    private suspend fun reference(
        canvas: PixelSize,
        layers: List<VideoFrameCompositor.Layer>
    ): SrgbPixels {
        val dst = FloatArray(canvas.width * canvas.height * 4)
        for (layer in layers) {
            if (layer.opacity <= 0f) continue
            var linear: LinearRgbaFrame = layer.pixels.toLinearFrame()
            if (layer.adjustments != ColorAdjustments()) {
                linear = BasicColorEffect().render(
                    listOf(linear), EffectParameters.Color(layer.adjustments)
                )
            }
            val pixels = linear.copyPixels()
            val inWidth = linear.size.width
            val inHeight = linear.size.height
            val minFit = minOf(
                canvas.width.toDouble() / inWidth, canvas.height.toDouble() / inHeight
            )
            val modeFit = when (layer.contentMode) {
                VideoContentMode.Fit -> minFit
                VideoContentMode.Fill -> maxOf(
                    canvas.width.toDouble() / inWidth, canvas.height.toDouble() / inHeight
                )
                VideoContentMode.Crop -> 1.0
            }
            val mapping = PhotoLayerMapping(
                linear.size, orientation = 1, canvas,
                PhotoLayerTransform(
                    translationX = layer.transform.translationX,
                    translationY = layer.transform.translationY,
                    scale = layer.transform.scale * (modeFit / minFit).toFloat(),
                    rotationDegrees = layer.transform.rotationDegrees,
                    flipHorizontal = layer.transform.flipHorizontal,
                    flipVertical = layer.transform.flipVertical
                )
            )
            fun at(x: Int, y: Int, c: Int): Float =
                if (x in 0 until inWidth && y in 0 until inHeight) {
                    pixels[(y * inWidth + x) * 4 + c]
                } else {
                    0f
                }
            for (y in 0 until canvas.height) for (x in 0 until canvas.width) {
                val sample = mapping.sourcePoint(x.toDouble(), y.toDouble()) ?: continue
                val x0 = floor(sample.raw.x).toInt()
                val y0 = floor(sample.raw.y).toInt()
                val tx = (sample.raw.x - x0).toFloat()
                val ty = (sample.raw.y - y0).toFloat()
                fun value(c: Int): Float {
                    val top = at(x0, y0, c) + (at(x0 + 1, y0, c) - at(x0, y0, c)) * tx
                    val bottom = at(x0, y0 + 1, c) + (at(x0 + 1, y0 + 1, c) - at(x0, y0 + 1, c)) * tx
                    return top + (bottom - top) * ty
                }
                compositeLayerPixel(
                    dst, (y * canvas.width + x) * 4,
                    value(0), value(1), value(2), value(3),
                    layer.blendMode, layer.opacity, clipToBelow = false
                )
            }
        }
        return LinearRgbaFrame(canvas, dst).toSrgbPixels()
    }

    private suspend fun assertMatchesReference(
        canvas: PixelSize,
        layers: List<VideoFrameCompositor.Layer>
    ) {
        assertContentEquals(
            reference(canvas, layers).copyPixels(),
            VideoFrameCompositor().composite(canvas, layers).copyPixels()
        )
    }

    @Test
    fun theEncodeTablesMatchTheTransferFunctionEverywhere() {
        // Every float in [0, 1] would take minutes; this walks all 8-bit level
        // edges closely and the range densely, plus the out-of-range clamps.
        val random = Random(7)
        repeat(2_000_000) {
            val value = random.nextFloat()
            assertEquals(encodeChannelExact(value), encodeChannel(value), "at $value")
        }
        repeat(200_000) {
            val value = random.nextFloat() * 0.01f
            assertEquals(encodeChannelExact(value), encodeChannel(value), "at $value")
        }
        listOf(0f, 1f, -1f, 2f, 0.0031308f, Float.MIN_VALUE).forEach { value ->
            assertEquals(encodeChannelExact(value), encodeChannel(value), "at $value")
        }
    }

    @Test
    fun aMovedScaledRotatedLayerMatchesTheReference() = runTest {
        val canvas = PixelSize(64, 48)
        assertMatchesReference(
            canvas,
            listOf(
                VideoFrameCompositor.Layer(
                    noise(canvas, 1),
                    transform = VideoLayerTransform(
                        scale = 1.3f, rotationDegrees = 17f,
                        translationX = 0.05f, translationY = -0.1f,
                        flipHorizontal = true
                    )
                )
            )
        )
    }

    @Test
    fun everyContentModeOnAMismatchedSourceMatchesTheReference() = runTest {
        val canvas = PixelSize(64, 48)
        VideoContentMode.entries.forEach { mode ->
            assertMatchesReference(
                canvas,
                listOf(VideoFrameCompositor.Layer(noise(PixelSize(50, 90), 2), contentMode = mode))
            )
        }
    }

    @Test
    fun blendedTranslucentGradedStacksMatchTheReference() = runTest {
        val canvas = PixelSize(64, 48)
        PhotoBlendMode.entries.forEach { mode ->
            assertMatchesReference(
                canvas,
                listOf(
                    VideoFrameCompositor.Layer(
                        noise(canvas, 3),
                        adjustments = ColorAdjustments(exposureStops = 0.5f, saturation = 1.3f)
                    ),
                    VideoFrameCompositor.Layer(
                        noise(PixelSize(30, 30), 4, opaque = false),
                        opacity = 0.6f,
                        blendMode = mode,
                        transform = VideoLayerTransform(scale = 0.5f, rotationDegrees = 30f)
                    )
                )
            )
        }
    }

    @Test
    fun framesBigEnoughToRunInParallelMatchTheReference() = runTest {
        val canvas = PixelSize(640, 480)
        assertMatchesReference(
            canvas,
            listOf(
                VideoFrameCompositor.Layer(
                    noise(canvas, 5),
                    adjustments = ColorAdjustments(exposureStops = -0.25f)
                ),
                VideoFrameCompositor.Layer(
                    noise(PixelSize(200, 120), 6),
                    opacity = 0.5f,
                    transform = VideoLayerTransform(scale = 0.6f, rotationDegrees = -12f)
                )
            )
        )
    }

    @Test
    fun aReusedCompositorDoesNotCarryTheLastFrameOver() = runTest {
        val canvas = PixelSize(32, 32)
        val shifted = listOf(
            VideoFrameCompositor.Layer(
                noise(canvas, 8), transform = VideoLayerTransform(scale = 0.5f)
            )
        )
        val compositor = VideoFrameCompositor()
        compositor.composite(
            canvas,
            listOf(VideoFrameCompositor.Layer(noise(canvas, 9), transform = VideoLayerTransform(scale = 2f)))
        )
        assertContentEquals(
            reference(canvas, shifted).copyPixels(),
            compositor.composite(canvas, shifted).copyPixels()
        )
    }

    @Test
    fun anUneditedFrameSkipsLinearLightAndChangesNoPixel() = runTest {
        val canvas = PixelSize(64, 48)
        val pixels = noise(canvas, 10)
        val layers = listOf(VideoFrameCompositor.Layer(pixels))
        val result = VideoFrameCompositor().composite(canvas, layers)
        assertSame(pixels, result)
        assertContentEquals(reference(canvas, layers).copyPixels(), result.copyPixels())
    }

    @Test
    fun anythingThatChangesTheFrameStillComposites() = runTest {
        val canvas = PixelSize(16, 16)
        val pixels = noise(canvas, 11)
        val compositor = VideoFrameCompositor()
        val cases = listOf(
            VideoFrameCompositor.Layer(pixels, opacity = 0.5f),
            VideoFrameCompositor.Layer(pixels, transform = VideoLayerTransform(scale = 0.5f)),
            VideoFrameCompositor.Layer(pixels, adjustments = ColorAdjustments(exposureStops = 1f)),
            VideoFrameCompositor.Layer(noise(PixelSize(8, 8), 12)),
            VideoFrameCompositor.Layer(noise(canvas, 13, opaque = false))
        )
        cases.forEach { layer ->
            val result = compositor.composite(canvas, listOf(layer))
            assertContentEquals(
                reference(canvas, listOf(layer)).copyPixels(),
                result.copyPixels()
            )
        }
    }
}
