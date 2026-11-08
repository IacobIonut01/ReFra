package com.dot.gallery.editor.ui

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.NormalizedCrop
import com.dot.gallery.editor.core.model.NormalizedPoint
import com.dot.gallery.editor.core.model.PhotoBlendMode
import com.dot.gallery.editor.core.model.PhotoCurves
import com.dot.gallery.editor.core.model.PhotoDetails
import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoFinishing
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoToneCurve
import com.dot.gallery.editor.core.model.PhotoVignette
import com.dot.gallery.editor.core.model.PhotoWhiteBalance
import com.dot.gallery.editor.core.model.PhotoImageLayer
import com.dot.gallery.editor.core.model.PhotoLayerMask
import com.dot.gallery.editor.core.model.PhotoLayerTransform
import com.dot.gallery.editor.core.model.PhotoLook
import com.dot.gallery.editor.core.model.PhotoLookId
import com.dot.gallery.editor.core.model.PhotoMapping
import com.dot.gallery.editor.core.model.PhotoMaskMode
import com.dot.gallery.editor.core.model.PhotoMaskStroke
import com.dot.gallery.editor.core.model.PhotoSpatialEffects
import com.dot.gallery.editor.core.model.PhotoWarp
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.CpuFrameRenderer
import com.dot.gallery.editor.render.FrameSource
import com.dot.gallery.editor.render.LinearRgbaFrame
import com.dot.gallery.editor.render.RenderRequest
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.render.rasterizePhotoMask
import com.dot.gallery.editor.render.renderGraph
import com.dot.gallery.editor.render.toLinearFrame
import com.dot.gallery.editor.render.toSrgbPixels
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AGSL runtime-shader tests on device/emulator hardware canvases.
 * `Surface.lockHardwareCanvas()` gives a RenderThread-backed canvas — the
 * same hardware path Compose renders through — without needing a window.
 * Parity is asserted against the CPU reference pipeline within 2/255 per
 * channel, the 8-bit display rounding bound.
 */
@RunWith(AndroidJUnit4::class)
class AndroidPhotoShaderTest {

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

    private fun cpuRender(pixels: SrgbPixels, edits: PhotoEdits): SrgbPixels =
        runBlocking {
            val document = PhotoDocument(
                MediaAsset.Image("source", "source", pixels.size),
                edits.color,
                edits.geometry,
                edits.layers,
                edits.spatial,
                edits.finishing
            )
            CpuFrameRenderer(SingleFrameSource(pixels.toLinearFrame()))
                .render(RenderRequest(1, document.renderGraph()))
                .toSrgbPixels()
        }

    /**
     * Draws the shader over a hardware canvas and reads the RGBA result back
     * from an [ImageReader] surface. Also asserts the factory did in fact
     * produce a renderer — a null renderer would be a silent skip.
     */
    private fun drawHardware(
        pixels: SrgbPixels,
        edits: PhotoEdits,
        overBlack: Boolean = false
    ): IntArray {
        val source = pixels.toEditorImageBitmap()
        val renderer = createPhotoShaderRenderer(source) {
            throw AssertionError("shader failed on-device: $it")
        }
        assertNotNull(renderer, "runtime-shader factory declined on API>=33")
        val mapping = PhotoMapping(pixels.size, 1, edits.geometry)
        return drawHardwareFrame(
            renderer, edits, mapping.outputSize, overBlack
        )
    }

    /** Draws [renderer]'s frame over a hardware canvas and reads it back. */
    private fun drawHardwareFrame(
        renderer: PhotoShaderRenderer,
        edits: PhotoEdits,
        outputSize: PixelSize,
        overBlack: Boolean
    ): IntArray {
        val w = outputSize.width
        val h = outputSize.height
        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        val listenerThread = HandlerThread("shader-readback").apply { start() }
        val frameReady = CountDownLatch(1)
        val frame = arrayOfNulls<Image>(1)
        try {
            reader.setOnImageAvailableListener(
                { r -> frame[0] = r.acquireLatestImage(); frameReady.countDown() },
                Handler(listenerThread.looper)
            )
            val surface = reader.surface
            val hwCanvas = surface.lockHardwareCanvas()
            assertTrue(hwCanvas.isHardwareAccelerated)
            CanvasDrawScope().draw(
                Density(1f), LayoutDirection.Ltr, Canvas(hwCanvas),
                Size(w.toFloat(), h.toFloat())
            ) {
                if (overBlack) drawRect(Color.Black)
                assertTrue(renderer.draw(this, edits))
            }
            surface.unlockCanvasAndPost(hwCanvas)
            assertTrue(
                frameReady.await(10, TimeUnit.SECONDS),
                "no rendered frame arrived on the surface"
            )
            val image = frame[0]
            assertNotNull(image, "frame callback fired without an image")
            val out = IntArray(w * h)
            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        val i = y * plane.rowStride + x * plane.pixelStride
                        val r = buffer.get(i).toInt() and 0xFF
                        val g = buffer.get(i + 1).toInt() and 0xFF
                        val b = buffer.get(i + 2).toInt() and 0xFF
                        val a = buffer.get(i + 3).toInt() and 0xFF
                        out[y * w + x] =
                            (a shl 24) or (r shl 16) or (g shl 8) or b
                    }
                }
            } finally {
                image.close()
            }
            return out
        } finally {
            renderer.close()
            reader.close()
            listenerThread.quitSafely()
        }
    }

    private fun assertParity(
        pixels: SrgbPixels,
        edits: PhotoEdits,
        overBlack: Boolean = false,
        label: String
    ) {
        val expected = cpuRender(pixels, edits)
        val actual = drawHardware(pixels, edits, overBlack)
        val exp = expected.copyPixels()
        assertEquals(exp.size, actual.size, "$label: pixel count")
        for (i in exp.indices) {
            val e = exp[i]
            val a = actual[i]
            if (overBlack) {
                val alpha = e ushr 24 and 0xFF
                assertEquals(0xFF, a ushr 24 and 0xFF,
                    "$label pixel $i must composite to opaque")
                for (shift in intArrayOf(16, 8, 0)) {
                    val composite = ((e shr shift and 0xFF) * alpha + 127) / 255
                    assertTrue(
                        abs(composite - (a shr shift and 0xFF)) <= 2,
                        "$label pixel $i ch${shift / 8}: " +
                            "cpu 0x%08X vs gpu 0x%08X".format(e, a)
                    )
                }
            } else {
                for (shift in intArrayOf(24, 16, 8, 0)) {
                    val diff =
                        abs((e shr shift and 0xFF) - (a shr shift and 0xFF))
                    assertTrue(
                        diff <= 2,
                        "$label pixel $i ch${shift / 8} off by $diff: " +
                            "cpu 0x%08X vs gpu 0x%08X".format(e, a)
                    )
                }
            }
        }
    }

    @SdkSuppress(minSdkVersion = 33)
    @Test
    fun hardwareCanvasMatchesCpuIdentity() {
        assertParity(tileSource(), PhotoEdits(), label = "identity")
    }

    @SdkSuppress(minSdkVersion = 33)
    @Test
    fun hardwareCanvasMatchesCpuColorRecipe() {
        assertParity(
            tileSource(),
            PhotoEdits(
                color = ColorAdjustments(
                    exposureStops = 0.5f, contrast = 2f, saturation = 0.5f
                )
            ),
            label = "exposure+contrast+saturation"
        )
    }

    /**
     * Each tonal parameter alone at both range ends — the tone stage of the
     * shader has to match the CPU curve field by field, not only in
     * combination.
     */
    @SdkSuppress(minSdkVersion = 33)
    @Test
    fun hardwareCanvasMatchesCpuTonalExtremes() {
        val pixels = tileSource()
        val extremes = listOf(
            "highlights+1" to ColorAdjustments(highlights = 1f),
            "highlights-1" to ColorAdjustments(highlights = -1f),
            "shadows+1" to ColorAdjustments(shadows = 1f),
            "shadows-1" to ColorAdjustments(shadows = -1f),
            "whites+1" to ColorAdjustments(whites = 1f),
            "whites-1" to ColorAdjustments(whites = -1f),
            "blacks+1" to ColorAdjustments(blacks = 1f),
            "blacks-1" to ColorAdjustments(blacks = -1f)
        )
        for ((label, adjustments) in extremes) {
            assertParity(pixels, PhotoEdits(color = adjustments), label = label)
        }
    }

    @SdkSuppress(minSdkVersion = 33)
    @Test
    fun hardwareCanvasMatchesCpuTonalCombined() {
        assertParity(
            tileSource(),
            PhotoEdits(
                color = ColorAdjustments(
                    exposureStops = .5f,
                    contrast = 1.2f,
                    saturation = .7f,
                    highlights = .6f,
                    shadows = .4f,
                    whites = -.2f,
                    blacks = .1f
                )
            ),
            label = "tonal-combined"
        )
    }

    /**
     * White balance on hardware: temperature and tint gains multiply
     * linear RGB before the curves — the warm/cool extremes plus a
     * tinted recipe must read back within the 2-LSB bound.
     */
    @SdkSuppress(minSdkVersion = 33)
    @Test
    fun hardwareCanvasMatchesCpuWhiteBalance() {
        val pixels = tileSource()
        for ((label, wb) in listOf(
            "tungsten" to PhotoWhiteBalance(3200f, 0f),
            "hot" to PhotoWhiteBalance(12000f, -60f),
            "fluorescent" to PhotoWhiteBalance(4200f, 12f)
        )) {
            assertParity(
                pixels,
                PhotoEdits(finishing = PhotoFinishing(whiteBalance = wb)),
                label = "wb-$label"
            )
        }
    }

    /**
     * The tone curves on hardware: a bent master plus per-channel curves
     * through the segment uniform arrays — interpolation,
     * not just the endpoints, must agree with the CPU evaluator.
     */
    @SdkSuppress(minSdkVersion = 33)
    @Test
    fun hardwareCanvasMatchesCpuCurves() {
        val pixels = tileSource()
        val finishing = PhotoFinishing(
            curves = PhotoCurves(
                master = PhotoToneCurve.ofQuarterOrdinates(
                    listOf(0f, .35f, .6f, .8f, 1f)
                ),
                red = PhotoToneCurve.ofQuarterOrdinates(
                    listOf(.05f, .2f, .5f, .8f, .95f)
                ),
                blue = PhotoToneCurve.ofQuarterOrdinates(
                    listOf(0f, .3f, .5f, .7f, .9f)
                )
            )
        )
        assertParity(
            pixels, PhotoEdits(finishing = finishing), label = "curves"
        )
    }

    /** A full curve, inset end points and falling outputs on the real GPU. */
    @SdkSuppress(minSdkVersion = 33)
    @Test
    fun hardwareCanvasMatchesCpuFreeCurvePoints() {
        val pixels = tileSource()
        val zigzag = PhotoToneCurve.ofSteps(
            *Array(16) { i -> i * 17 to if (i % 2 == 0) i * 12 else i * 17 }
        )
        val finishing = PhotoFinishing(
            curves = PhotoCurves(
                master = zigzag,
                red = PhotoToneCurve.ofSteps(30 to 220, 128 to 40, 230 to 200),
                green = PhotoToneCurve.ofSteps(0 to 34, 128 to 132, 255 to 236)
            )
        )
        assertParity(
            pixels, PhotoEdits(finishing = finishing), label = "free curve points"
        )
    }

    /**
     * Vignette on hardware: the mask is computed in output-pixel
     * coordinates like the CPU processor — darkening and brightening
     * amounts, a square-ish roundness and highlight protection must all
     * land inside the 2-LSB bound. Grain is deliberately absent: it has
     * no bit-exact shader form and gates to the CPU preview instead.
     */
    @SdkSuppress(minSdkVersion = 33)
    @Test
    fun hardwareCanvasMatchesCpuVignette() {
        val pixels = tileSource()
        for ((label, vignette) in listOf(
            "dark" to PhotoVignette(amount = -.8f),
            "bright" to PhotoVignette(amount = .6f),
            "square+protected" to PhotoVignette(
                amount = -.6f, midpoint = .3f, feather = .8f,
                roundness = -1f, highlights = 1f
            )
        )) {
            assertParity(
                pixels,
                PhotoEdits(finishing = PhotoFinishing(vignette = vignette)),
                label = "vignette-$label"
            )
        }
    }

    /**
     * Every look transform on hardware: each branch of the lookId
     * dispatch — saturation lifts, split-toning, the mono path — plus a
     * partial blend must agree with the CPU processor per channel.
     */
    @SdkSuppress(minSdkVersion = 33)
    @Test
    fun hardwareCanvasMatchesCpuLooks() {
        val pixels = tileSource()
        for ((label, look) in listOf(
            "clean" to PhotoLook(PhotoLookId.Clean),
            "warmfade" to PhotoLook(PhotoLookId.WarmFade, .7f),
            "cool" to PhotoLook(PhotoLookId.Cool),
            "mono" to PhotoLook(PhotoLookId.Mono),
            "cinematic" to PhotoLook(PhotoLookId.Cinematic, .6f)
        )) {
            assertParity(
                pixels,
                PhotoEdits(finishing = PhotoFinishing(look = look)),
                label = "look-$label"
            )
        }
    }

    /**
     * All four shader-side finishing stages together — the same order
     * as the CPU processor (white balance, curves, look, then vignette),
     * so a recipe exercising every stage stays inside the bound.
     */
    @SdkSuppress(minSdkVersion = 33)
    @Test
    fun hardwareCanvasMatchesCpuFinishingCombined() {
        assertParity(
            tileSource(),
            PhotoEdits(
                finishing = PhotoFinishing(
                    whiteBalance = PhotoWhiteBalance(5000f, -20f),
                    curves = PhotoCurves(
                        master = PhotoToneCurve.ofQuarterOrdinates(
                    listOf(0f, .3f, .55f, .8f, 1f)
                )
                    ),
                    look = PhotoLook(PhotoLookId.Cinematic, .7f),
                    vignette = PhotoVignette(amount = -.4f, feather = .7f)
                )
            ),
            label = "finishing-combined"
        )
    }

    /**
     * The Details stage on hardware: the edge-clamped 3x3 decomposition
     * — high-band texture/sharpening, the luma-local clarity term and
     * the noise-reduction lerp — runs in the shared shader before
     * finishing, so a recipe exercising all four fields must stay inside
     * the 2-LSB bound. Lens blur is deliberately absent: like grain it
     * has no honest shader form and gates to the CPU preview.
     */
    @SdkSuppress(minSdkVersion = 33)
    @Test
    fun hardwareCanvasMatchesCpuDetails() {
        val pixels = tileSource()
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
                pixels,
                PhotoEdits(
                    spatial = PhotoSpatialEffects(details = details)
                ),
                label = "details-$label"
            )
        }
    }

    @SdkSuppress(minSdkVersion = 33)
    @Test
    fun hardwareCanvasMatchesCpuCropRotateFlip() {
        assertParity(
            tileSource(),
            PhotoEdits(
                color = ColorAdjustments(exposureStops = 0.5f),
                geometry = PhotoGeometry(
                    crop = NormalizedCrop(0.25, 0.0, 0.75, 1.0),
                    quarterTurns = 1,
                    flipHorizontal = true
                )
            ),
            label = "crop+q1+flipH"
        )
    }

    /**
     * The v2 warp recipe on hardware: rotation + scale + both perspective
     * axes through the active shader branch, composited over black since
     * warped edges legitimately carry alpha < 1.
     */
    @SdkSuppress(minSdkVersion = 33)
    @Test
    fun hardwareCanvasMatchesCpuWarp() {
        assertParity(
            tileSource(),
            PhotoEdits(
                color = ColorAdjustments(exposureStops = 0.5f, contrast = 1.5f),
                geometry = PhotoGeometry(
                    warp = PhotoWarp(
                        rotationDegrees = 20f,
                        scale = 1.25f,
                        verticalPerspective = 30f,
                        horizontalPerspective = -20f
                    )
                )
            ),
            overBlack = true,
            label = "warp-combined"
        )
    }

    /**
     * Translucent pixels premultiply-composite like the CPU path — identity
     * and a real edit, so the unpremultiply→linear→premultiply round trip is
     * exercised, not only the passthrough.
     */
    @SdkSuppress(minSdkVersion = 33)
    @Test
    fun hardwareCanvasPremultipliesAlphaOnBlack() {
        val source = SrgbPixels(
            PixelSize(3, 1),
            intArrayOf(0x00000000, 0x80804020.toInt(), 0x80FFFFFF.toInt())
        )
        assertParity(source, PhotoEdits(), overBlack = true, label = "alpha")
        assertParity(
            source,
            PhotoEdits(
                color = ColorAdjustments(
                    exposureStops = 0.5f, contrast = 2f, saturation = 0.5f
                )
            ),
            overBlack = true,
            label = "alpha+edit"
        )
    }

    /**
     * The layered recipe on hardware: the composite program draws the
     * capture recipe — Screen at 65%, a scaled/rotated/translated layer
     * under a real paint+erase mask — and must read back within the
     * 2-LSB bound of the CPU composite. The document's source is far
     * larger than the decoded preview, so stored translations exercise
     * the outputSize/referenceOutputSize scaling too.
     */
    @SdkSuppress(minSdkVersion = 33)
    @Test
    fun hardwareCanvasMatchesCpuCompositeLayers() {
        val size = PixelSize(4, 4)
        val base = SrgbPixels(size, IntArray(16) { 0xFF804020.toInt() })
        val layerPixels = SrgbPixels(size, IntArray(16) { 0xFF10E0A0.toInt() })
        val mask = PhotoLayerMask(
            strokes = persistentListOf(
                PhotoMaskStroke(
                    points = persistentListOf(NormalizedPoint(.5f, .5f)),
                    diameter = .5f, hardness = .5f,
                    mode = PhotoMaskMode.Erase
                ),
                PhotoMaskStroke(
                    points = listOf(
                        NormalizedPoint(.05f, .05f),
                        NormalizedPoint(.6f, .05f)
                    ).toPersistentList(),
                    diameter = .4f, hardness = 1f,
                    mode = PhotoMaskMode.Paint
                )
            )
        )
        val fullSource = PixelSize(96, 96)
        val layer = PhotoImageLayer(
            id = "l1", name = "l1",
            source = MediaAsset.Image("src-a", "src-a", size),
            transform = PhotoLayerTransform(
                translationX = 48f, translationY = 64f,
                scale = .65f, rotationDegrees = -8f
            ),
            blendMode = PhotoBlendMode.Screen,
            opacity = .65f,
            mask = mask
        )
        val edits = PhotoEdits(layers = persistentListOf(layer))
        val document = PhotoDocument(
            MediaAsset.Image("base-src", "base-src", fullSource),
            edits.color, edits.geometry, edits.layers
        )
        val expected = runBlocking {
            val frames = mapOf(
                "base-src" to base.toLinearFrame(),
                "src-a" to layerPixels.toLinearFrame()
            )
            CpuFrameRenderer(object : FrameSource {
                override suspend fun read(sourceId: String): LinearRgbaFrame =
                    frames.getValue(sourceId)
            }).render(
                RenderRequest(
                    1,
                    document.renderGraph(
                        PhotoMapping(size, 1, edits.geometry).outputSize
                    )
                )
            ).toSrgbPixels().copyPixels()
        }
        val renderer = createCompositePhotoShaderRenderer(
            base.toEditorImageBitmap(),
            listOf(
                PhotoShaderLayerSource(
                    layerId = layer.id,
                    sourceId = layer.source.id,
                    bitmap = layerPixels.toEditorImageBitmap(),
                    maskBitmap = rasterizePhotoMask(mask, size)
                        .toEditorImageBitmap()
                )
            ),
            fullSource
        ) { throw AssertionError("composite shader failed on-device: $it") }
        assertNotNull(renderer)
        val actual = drawHardwareFrame(
            renderer, edits,
            PhotoMapping(size, 1, edits.geometry).outputSize,
            overBlack = true
        )
        for (i in expected.indices) {
            val e = expected[i]
            val a = actual[i]
            val alpha = e ushr 24 and 0xFF
            assertEquals(
                0xFF, a ushr 24 and 0xFF,
                "pixel $i must composite to opaque"
            )
            for (shift in intArrayOf(16, 8, 0)) {
                val composite = ((e shr shift and 0xFF) * alpha + 127) / 255
                assertTrue(
                    abs(composite - (a shr shift and 0xFF)) <= 2,
                    "pixel $i ch${shift / 8}: " +
                        "cpu 0x%08X vs gpu 0x%08X".format(e, a)
                )
            }
        }
    }

    /** A software bitmap canvas must be rejected, not silently misrendered. */
    @SdkSuppress(minSdkVersion = 33)
    @Test
    fun softwareCanvasIsDeclined() {
        val renderer = createPhotoShaderRenderer(
            tileSource().toEditorImageBitmap()
        ) {}
        assertNotNull(renderer)
        val target = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(android.graphics.Canvas(target))
        CanvasDrawScope().draw(
            Density(1f), LayoutDirection.Ltr, canvas, Size(4f, 4f)
        ) {
            assertTrue(!renderer.draw(this, PhotoEdits()),
                "software canvas must not report a drawn frame")
        }
        renderer.close()
    }

    /** API < 33 has no AGSL runtime shaders — the factory must decline. */
    @SdkSuppress(maxSdkVersion = 32)
    @Test
    fun factoryDeclinesOnApi32AndBelow() {
        var reported = false
        val renderer = createPhotoShaderRenderer(
            tileSource().toEditorImageBitmap()
        ) { reported = true }
        assertNull(renderer, "factory must decline where AGSL is absent")
        assertTrue(!reported, "declining is a capability absence, not a failure")
    }
}
