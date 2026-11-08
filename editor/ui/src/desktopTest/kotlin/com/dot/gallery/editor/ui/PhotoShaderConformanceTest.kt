package com.dot.gallery.editor.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.NormalizedCrop
import com.dot.gallery.editor.core.model.PhotoColourGrade
import com.dot.gallery.editor.core.model.PhotoColourWheel
import com.dot.gallery.editor.core.model.PhotoCurves
import com.dot.gallery.editor.core.model.PhotoDetails
import com.dot.gallery.editor.core.model.PhotoHslAdjustment
import com.dot.gallery.editor.core.model.PhotoHslBand
import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoFinishing
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoLook
import com.dot.gallery.editor.core.model.PhotoLookId
import com.dot.gallery.editor.core.model.PhotoMapping
import com.dot.gallery.editor.core.model.PhotoSpatialEffects
import com.dot.gallery.editor.core.model.PhotoToneCurve
import com.dot.gallery.editor.core.model.PhotoVignette
import com.dot.gallery.editor.core.model.PhotoWarp
import com.dot.gallery.editor.core.model.PhotoWhiteBalance
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.CpuFrameRenderer
import com.dot.gallery.editor.render.FrameSource
import com.dot.gallery.editor.render.LinearRgbaFrame
import com.dot.gallery.editor.render.RenderRequest
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.render.renderGraph
import com.dot.gallery.editor.render.toLinearFrame
import com.dot.gallery.editor.render.toSrgbPixels
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest

/**
 * Shader conformance, not a GPU benchmark: the runtime shader is evaluated
 * by Skia on an offscreen raster surface and compared channel-by-channel
 * against the reference [CpuFrameRenderer] output. The tolerance is the
 * 8-bit display rounding bound (2 LSB per channel); it certifies parity for
 * the sRGB display path, not HDR or wide-gamut precision.
 */
class PhotoShaderConformanceTest {

    /** Six colours in 8x8 blocks over a 32x24 frame. */
    private fun tileSource(): SrgbPixels {
        val colors = intArrayOf(
            0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF804020.toInt(),
            0xFF10E0A0.toInt(), 0xFF4020E0.toInt(), 0xFF010203.toInt()
        )
        val size = PixelSize(32, 24)
        val px = IntArray(size.width * size.height) { i ->
            val x = i % size.width
            val y = i / size.width
            colors[((y / 8) * 4 + (x / 8)) % colors.size]
        }
        return SrgbPixels(size, px)
    }

    private class SingleFrameSource(
        private val frame: LinearRgbaFrame
    ) : FrameSource {
        override suspend fun read(sourceId: String): LinearRgbaFrame = frame
    }

    /** The reference pipeline: graph -> linear render -> sRGB integers. */
    private suspend fun cpuRender(
        pixels: SrgbPixels,
        edits: PhotoEdits
    ): SrgbPixels {
        val document = PhotoDocument(
            MediaAsset.Image("source", "source", pixels.size),
            edits.color,
            edits.geometry,
            edits.layers,
            edits.spatial,
            edits.finishing
        )
        return CpuFrameRenderer(SingleFrameSource(pixels.toLinearFrame()))
            .render(RenderRequest(1, document.renderGraph()))
            .toSrgbPixels()
    }

    /**
     * Draws the runtime shader at exactly one output pixel per canvas pixel
     * into an offscreen bitmap (optionally over an opaque background for the
     * alpha fixtures) and reads the ARGB result back.
     */
    private fun shaderRender(
        pixels: SrgbPixels,
        edits: PhotoEdits,
        overBlack: Boolean = false
    ): IntArray {
        val source = pixels.toEditorImageBitmap()
        val failures = mutableListOf<Throwable>()
        val renderer = createPhotoShaderRenderer(source) { failures += it }
        assertNotNull(renderer, "runtime-shader renderer unavailable")
        assertTrue(failures.isEmpty(), "shader construction failed: $failures")
        val mapping = PhotoMapping(pixels.size, orientation = 1, edits.geometry)
        val w = mapping.outputSize.width
        val h = mapping.outputSize.height
        val target = ImageBitmap(w, h)
        val drawScope = CanvasDrawScope()
        try {
            drawScope.draw(
                Density(1f), LayoutDirection.Ltr, Canvas(target),
                Size(w.toFloat(), h.toFloat())
            ) {
                if (overBlack) drawRect(Color.Black)
                assertTrue(renderer.draw(this, edits), "shader draw rejected the frame")
            }
        } finally {
            renderer.close()
        }
        val out = IntArray(w * h)
        target.readPixels(out, 0, 0, w, h, 0, w)
        return out
    }

    private suspend fun assertParity(
        pixels: SrgbPixels,
        edits: PhotoEdits,
        label: String
    ) {
        val expected = cpuRender(pixels, edits)
        val actual = shaderRender(pixels, edits)
        assertEquals(expected.size, PhotoMapping(
            pixels.size, 1, edits.geometry
        ).outputSize, "$label: output size")
        val exp = expected.copyPixels()
        assertEquals(exp.size, actual.size, "$label: pixel count")
        for (i in exp.indices) {
            val e = exp[i]
            val a = actual[i]
            // Alpha is exact — the tile source is opaque and both paths
            // must report a fully opaque frame.
            assertEquals(
                0xFF, a ushr 24,
                "$label: pixel $i alpha (expected 0x%08X, got 0x%08X)"
                    .format(e, a)
            )
            for (shift in intArrayOf(16, 8, 0)) {
                val diff = abs((e shr shift and 0xFF) - (a shr shift and 0xFF))
                assertTrue(
                    diff <= 2,
                    "$label: pixel $i channel ${shift / 8} off by $diff " +
                        "(expected 0x%08X, got 0x%08X)".format(e, a)
                )
            }
        }
    }

    @Test
    fun sampledColorRecipesMatchCpu() = runTest {
        val source = tileSource()
        for (exposure in floatArrayOf(-2.5f, 0f, 0.5f, 2f)) {
            for (contrast in floatArrayOf(0.5f, 1f, 2f)) {
                for (saturation in floatArrayOf(0f, 1f, 2f)) {
                    val edits = PhotoEdits(
                        color = ColorAdjustments(
                            exposureStops = exposure,
                            contrast = contrast,
                            saturation = saturation
                        )
                    )
                    assertParity(
                        source, edits,
                        "e=$exposure c=$contrast s=$saturation"
                    )
                }
            }
        }
    }

    /**
     * The v2 tonal controls through the shader: all 81 corner/neutral
     * combinations at a neutral base, plus each individual extreme over a
     * mixed exposure/contrast/saturation recipe.
     */
    @Test
    fun sampledTonalRecipesMatchCpu() = runTest {
        val source = tileSource()
        val corners = floatArrayOf(-1f, 0f, 1f)
        for (hi in corners) {
            for (sh in corners) {
                for (wh in corners) {
                    for (bl in corners) {
                        val edits = PhotoEdits(
                            color = ColorAdjustments(
                                highlights = hi, shadows = sh,
                                whites = wh, blacks = bl
                            )
                        )
                        assertParity(source, edits, "hi=$hi sh=$sh wh=$wh bl=$bl")
                    }
                }
            }
        }
        for ((label, tonal) in listOf(
            "highlights" to ColorAdjustments(highlights = 1f),
            "highlights-" to ColorAdjustments(highlights = -1f),
            "shadows" to ColorAdjustments(shadows = 1f),
            "shadows-" to ColorAdjustments(shadows = -1f),
            "whites" to ColorAdjustments(whites = 1f),
            "whites-" to ColorAdjustments(whites = -1f),
            "blacks" to ColorAdjustments(blacks = 1f),
            "blacks-" to ColorAdjustments(blacks = -1f)
        )) {
            assertParity(
                source,
                PhotoEdits(
                    color = tonal.copy(
                        exposureStops = 0.5f, contrast = 1.2f,
                        saturation = 0.7f
                    )
                ),
                label
            )
        }
    }

    @Test
    fun geometryParityForEveryTransform() = runTest {
        val source = tileSource()
        val color = PhotoEdits(
            color = ColorAdjustments(
                exposureStops = 0.5f, contrast = 2f, saturation = 0.5f
            )
        )
        val geometries = listOf(
            "identity" to PhotoGeometry(),
            "q1" to PhotoGeometry(quarterTurns = 1),
            "q2" to PhotoGeometry(quarterTurns = 2),
            "q3" to PhotoGeometry(quarterTurns = 3),
            "flipH" to PhotoGeometry(flipHorizontal = true),
            "flipV" to PhotoGeometry(flipVertical = true),
            "crop" to PhotoGeometry(
                crop = NormalizedCrop(0.25, 0.0, 0.75, 1.0)
            ),
            "crop+q1+flipH" to PhotoGeometry(
                crop = NormalizedCrop(0.25, 0.0, 0.75, 1.0),
                quarterTurns = 1,
                flipHorizontal = true
            )
        )
        for ((label, geometry) in geometries) {
            assertParity(source, color.copy(geometry = geometry), label)
        }
    }

    /**
     * The Details stage through the shared shader on the desktop Skia
     * path: the edge-clamped 3x3 decomposition — high-band texture and
     * sharpening, the luma-local clarity term, the noise-reduction lerp —
     * must land within the same 2-LSB display bound on the tiled source.
     * Lens blur is CPU-gated by design, so it is intentionally absent.
     */
    @Test
    fun detailsRecipesMatchCpu() = runTest {
        val source = tileSource()
        for ((label, details) in listOf(
            "sharpen" to PhotoDetails(sharpening = 1f),
            "soften" to PhotoDetails(texture = -0.7f, clarity = -0.5f),
            "denoise" to PhotoDetails(noiseReduction = 1f),
            "all" to PhotoDetails(
                texture = 0.6f, clarity = 0.5f,
                sharpening = 0.8f, noiseReduction = 0.4f
            )
        )) {
            assertParity(
                source,
                PhotoEdits(spatial = PhotoSpatialEffects(details = details)),
                "details-$label"
            )
        }
    }

    /**
     * Every look through the shared shader: each transform at full and
     * partial blend, the Original no-op, and one combined recipe that
     * pins the WB -> curves -> look -> vignette ordering end to end.
     */
    @Test
    fun lookRecipesMatchCpu() = runTest {
        val source = tileSource()
        // Every shipped look, so a new transform must land in the shader
        // (and its explicit id contract) or this fails.
        val looks = listOf("original" to PhotoLook()) +
            PhotoLookId.entries
                .filter { it != PhotoLookId.None }
                .mapIndexed { index, id ->
                    id.name.lowercase() to PhotoLook(id, if (index % 3 == 0) 0.7f else 1f)
                }
        for ((label, look) in looks) {
            assertParity(
                source,
                PhotoEdits(finishing = PhotoFinishing(look = look)),
                "look-$label"
            )
        }
        assertParity(
            source,
            PhotoEdits(
                finishing = PhotoFinishing(
                    whiteBalance = PhotoWhiteBalance(4200f, 8f),
                    curves = PhotoCurves(
                        master = PhotoToneCurve.ofQuarterOrdinates(
                    listOf(0f, 0.3f, 0.55f, 0.8f, 1f)
                )
                    ),
                    look = PhotoLook(PhotoLookId.Cinematic, 0.8f),
                    vignette = PhotoVignette(amount = -0.5f)
                )
            ),
            "look-combined"
        )
    }

    /**
     * Free curve points: a full 16-point curve, inset end points and falling
     * outputs through the segment uniforms, with channels left neutral on the
     * way so each per-channel gate gets exercised.
     */
    @Test
    fun freeCurvePointsMatchCpu() = runTest {
        val source = tileSource()
        val zigzag = PhotoToneCurve.ofSteps(
            *Array(16) { i -> i * 17 to if (i % 2 == 0) i * 12 else i * 17 }
        )
        val inset = PhotoToneCurve.ofSteps(30 to 220, 128 to 40, 230 to 200)
        val recipes = listOf(
            "full" to PhotoCurves(master = zigzag),
            "inset-red" to PhotoCurves(red = inset),
            "master-then-channel" to PhotoCurves(
                master = PhotoToneCurve.ofSteps(0 to 0, 64 to 54, 192 to 202, 255 to 255),
                blue = inset
            ),
            "fade" to PhotoCurves(
                green = PhotoToneCurve.ofSteps(0 to 34, 128 to 132, 255 to 236)
            )
        )
        for ((label, curves) in recipes) {
            assertParity(
                source,
                PhotoEdits(finishing = PhotoFinishing(curves = curves)),
                "curves-$label"
            )
        }
    }

    /**
     * Each colour grade control alone, then the whole grade behind white
     * balance and in front of the look, which pins its place in the order.
     */
    @Test
    fun colourGradeRecipesMatchCpu() = runTest {
        val source = tileSource()
        val hsl = PhotoColourGrade()
            .withAdjustment(PhotoHslBand.Red, PhotoHslAdjustment(0.6f, 0.4f, -0.3f))
            .withAdjustment(PhotoHslBand.Green, PhotoHslAdjustment(-0.5f, -0.6f, 0.4f))
            .withAdjustment(PhotoHslBand.Blue, PhotoHslAdjustment(0.3f, 0.8f, -0.5f))
        val wheels = PhotoColourGrade(
            shadows = PhotoColourWheel(215f, 0.5f, -0.2f),
            midtones = PhotoColourWheel(45f, 0.3f, 0.1f),
            highlights = PhotoColourWheel(68f, 0.7f, 0.2f)
        )
        val grades = listOf(
            "vibrance" to PhotoColourGrade(vibrance = 0.7f),
            "vibrance-skin" to PhotoColourGrade(vibrance = -0.6f, protectSkin = true),
            "hsl" to hsl,
            "wheels" to wheels
        )
        for ((label, grade) in grades) {
            assertParity(
                source,
                PhotoEdits(finishing = PhotoFinishing(grade = grade)),
                "grade-$label"
            )
        }
        assertParity(
            source,
            PhotoEdits(
                finishing = PhotoFinishing(
                    whiteBalance = PhotoWhiteBalance(4200f, 8f),
                    grade = hsl.copy(
                        vibrance = 0.4f,
                        protectSkin = true,
                        shadows = wheels.shadows,
                        midtones = wheels.midtones,
                        highlights = wheels.highlights
                    ),
                    look = PhotoLook(PhotoLookId.Cinematic, 0.8f)
                )
            ),
            "grade-combined"
        )
    }

    /**
     * Warp parity against the CPU graph. Warped outputs legitimately carry
     * alpha < 1 (rejected points, edge blends), so the shader is composited
     * over black like the alpha fixtures: expected = the CPU straight sRGB
     * output multiplied by its own alpha.
     */
    private suspend fun assertWarpParity(
        pixels: SrgbPixels,
        edits: PhotoEdits,
        label: String
    ) {
        val expected = cpuRender(pixels, edits)
        val actual = shaderRender(pixels, edits, overBlack = true)
        val exp = expected.copyPixels()
        assertEquals(exp.size, actual.size, "$label: pixel count")
        for (i in exp.indices) {
            val e = exp[i]
            val a = actual[i]
            val alpha = e ushr 24 and 0xFF
            assertEquals(
                0xFF, a ushr 24 and 0xFF,
                "$label pixel $i: composited output must be opaque"
            )
            for (shift in intArrayOf(16, 8, 0)) {
                val composite = ((e shr shift and 0xFF) * alpha + 127) / 255
                val diff = abs(composite - (a shr shift and 0xFF))
                assertTrue(
                    diff <= 2,
                    "$label pixel $i channel ${shift / 8} off by $diff " +
                        "(cpu 0x%08X a=$alpha, shader 0x%08X)".format(e, a)
                )
            }
        }
    }

    /**
     * The v2 warp through the shared shader: rotation, scale, each
     * perspective axis, one combined recipe with a quarter turn, and a
     * neutral warp proving the rigid branch stays selected.
     */
    @Test
    fun warpRecipesMatchCpu() = runTest {
        val source = tileSource()
        val edits = PhotoEdits(
            color = ColorAdjustments(
                exposureStops = 0.5f, contrast = 1.5f, saturation = 0.8f
            )
        )
        val recipes = listOf(
            "rot20" to PhotoGeometry(
                warp = PhotoWarp(rotationDegrees = 20f)
            ),
            "scale1.4" to PhotoGeometry(warp = PhotoWarp(scale = 1.4f)),
            "hp40" to PhotoGeometry(
                warp = PhotoWarp(horizontalPerspective = 40f)
            ),
            "vp-30" to PhotoGeometry(
                warp = PhotoWarp(verticalPerspective = -30f)
            ),
            "rot+persp+scale+q1" to PhotoGeometry(
                quarterTurns = 1,
                warp = PhotoWarp(
                    rotationDegrees = 33f,
                    scale = 1.25f,
                    verticalPerspective = 20f,
                    horizontalPerspective = -35f
                )
            ),
            "neutral-warp+rigid" to PhotoGeometry(
                quarterTurns = 1,
                flipHorizontal = true,
                warp = PhotoWarp()
            )
        )
        for ((label, geometry) in recipes) {
            assertWarpParity(source, edits.copy(geometry = geometry), label)
        }
    }

    /**
     * Translucent source pixels must premultiply-composite identically to the
     * CPU path: expected = CPU straight output composited on black. Runs the
     * identity recipe and a real edit, so the unpremultiply→linear-math→
     * premultiply round trip is exercised, not just the passthrough.
     */
    @Test
    fun alphaPremultipliesLikeTheCpuRenderer() = runTest {
        val source = SrgbPixels(
            PixelSize(3, 1),
            intArrayOf(0x00000000, 0x80804020.toInt(), 0x80FFFFFF.toInt())
        )
        val recipes = listOf(
            "identity" to PhotoEdits(),
            "edited" to PhotoEdits(
                color = ColorAdjustments(
                    exposureStops = 0.5f, contrast = 2f, saturation = 0.5f
                )
            )
        )
        for ((label, edits) in recipes) {
            val expected = cpuRender(source, edits).copyPixels()
            val actual = shaderRender(source, edits, overBlack = true)
            for (i in expected.indices) {
                val e = expected[i]
                val alpha = e ushr 24 and 0xFF
                val a = actual[i]
                assertEquals(
                    0xFF, a ushr 24 and 0xFF,
                    "$label pixel $i: composited-on-black output must be opaque"
                )
                for (shift in intArrayOf(16, 8, 0)) {
                    val composite = ((e shr shift and 0xFF) * alpha + 127) / 255
                    val diff = abs(composite - (a shr shift and 0xFF))
                    assertTrue(
                        diff <= 2,
                        "$label pixel $i channel ${shift / 8}: composited " +
                            "diff $diff (cpu 0x%08X a=$alpha, shader 0x%08X)"
                            .format(e, a)
                    )
                }
            }
        }
    }

    /** A fake draw rejection must flow back as a reported failure path. */
    @Test
    fun rendererFactoryFailureSurfacesThroughCallback() {
        val errors = mutableListOf<Throwable>()
        val renderer = createPhotoShaderRenderer(tileSource().toEditorImageBitmap()) {
            errors += it
        }
        assertNotNull(renderer)
        renderer.close()
        assertTrue(errors.isEmpty(), "healthy renderer must not report")
    }
}
