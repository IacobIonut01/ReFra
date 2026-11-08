package com.dot.gallery.editor.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.dot.gallery.editor.core.model.MAX_PHOTO_IMAGE_LAYERS
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.NormalizedPoint
import com.dot.gallery.editor.core.model.PhotoBlendMode
import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoImageLayer
import com.dot.gallery.editor.core.model.PhotoLayerMask
import com.dot.gallery.editor.core.model.PhotoLayerTransform
import com.dot.gallery.editor.core.model.PhotoMapping
import com.dot.gallery.editor.core.model.PhotoMaskMode
import com.dot.gallery.editor.core.model.PhotoMaskStroke
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
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.test.runTest

/**
 * Composite-shader conformance: the generated multi-layer program is
 * evaluated by Skia on an offscreen raster surface and compared
 * channel-by-channel against the reference [CpuFrameRenderer] running the
 * real document graph — same 2-LSB display bound as the base shader
 * conformance. Sources and masks are analytic 4x4 fixtures, so placement
 * and blend math decide every pixel.
 */
class PhotoCompositeShaderConformanceTest {

    private val baseSize = PixelSize(4, 4)

    private fun solid(size: PixelSize, argb: Int): SrgbPixels =
        SrgbPixels(size, IntArray(size.width * size.height) { argb })

    private fun layer(
        id: String,
        sourceId: String,
        sourceSize: PixelSize = PixelSize(8, 8),
        transform: PhotoLayerTransform = PhotoLayerTransform(),
        blendMode: PhotoBlendMode = PhotoBlendMode.Normal,
        opacity: Float = 1f,
        visible: Boolean = true,
        clipToBelow: Boolean = false,
        mask: PhotoLayerMask = PhotoLayerMask()
    ) = PhotoImageLayer(
        id = id,
        name = id,
        source = MediaAsset.Image(sourceId, sourceId, sourceSize),
        transform = transform,
        blendMode = blendMode,
        opacity = opacity,
        visible = visible,
        clipToBelow = clipToBelow,
        mask = mask
    )

    /** The document a recipe describes: source size is the full
     * resolution the layer translations are expressed in. */
    private fun document(edits: PhotoEdits): PhotoDocument =
        PhotoDocument(
            MediaAsset.Image("base-src", "base-src", baseSize),
            edits.color,
            edits.geometry,
            edits.layers
        )

    private fun sourceOf(pixels: SrgbPixels): ImageBitmap =
        pixels.toEditorImageBitmap()

    private fun layerSources(
        layers: List<PhotoImageLayer>,
        pixelsBySource: Map<String, SrgbPixels>
    ): List<PhotoShaderLayerSource> =
        layers.filter { it.visible }.map { layer ->
            val pixels = pixelsBySource.getValue(layer.source.id)
            PhotoShaderLayerSource(
                layerId = layer.id,
                sourceId = layer.source.id,
                bitmap = sourceOf(pixels),
                maskBitmap = rasterizePhotoMask(layer.mask, pixels.size)
                    .toEditorImageBitmap()
            )
        }

    /** The reference pipeline over the same decoded preview frames the
     * shader binds — base 4x4, layers 4x4. */
    private suspend fun cpuRender(
        base: SrgbPixels,
        edits: PhotoEdits,
        frames: Map<String, SrgbPixels>
    ): SrgbPixels {
        val linear = (frames + ("base-src" to base))
            .mapValues { it.value.toLinearFrame() }
        val source = object : FrameSource {
            override suspend fun read(sourceId: String): LinearRgbaFrame =
                linear.getValue(sourceId)
        }
        val document = document(edits)
        // The preview canvas is the geometry output at the decoded
        // resolution — what the session computes before requesting a
        // layered render.
        val canvas = PhotoMapping(
            baseSize, orientation = 1, edits.geometry
        ).outputSize
        return CpuFrameRenderer(source)
            .render(RenderRequest(1, document.renderGraph(canvas)))
            .toSrgbPixels()
    }

    private fun shaderRender(
        base: SrgbPixels,
        edits: PhotoEdits,
        pixelsBySource: Map<String, SrgbPixels>,
        referenceSourceSize: PixelSize = baseSize,
        overBlack: Boolean = true
    ): IntArray {
        val failures = mutableListOf<Throwable>()
        val renderer = createCompositePhotoShaderRenderer(
            sourceOf(base),
            layerSources(edits.layers, pixelsBySource),
            referenceSourceSize
        ) { failures += it }
        assertNotNull(renderer, "composite renderer unavailable")
        assertTrue(failures.isEmpty(), "shader construction failed: $failures")
        val mapping = PhotoMapping(baseSize, 1, edits.geometry)
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
                assertTrue(
                    renderer.draw(this, edits),
                    "composite shader rejected a matching recipe"
                )
            }
        } finally {
            renderer.close()
        }
        val out = IntArray(w * h)
        target.readPixels(out, 0, 0, w, h, 0, w)
        return out
    }

    /**
     * The shader output composited over black equals the CPU straight sRGB
     * output multiplied by its own alpha — the same convention the warp
     * fixtures use for sub-unity coverage.
     */
    private suspend fun assertParity(
        base: SrgbPixels,
        edits: PhotoEdits,
        pixelsBySource: Map<String, SrgbPixels>,
        label: String,
        referenceSourceSize: PixelSize = baseSize
    ) {
        val expected = cpuRender(base, edits, pixelsBySource).copyPixels()
        val actual = shaderRender(
            base, edits, pixelsBySource, referenceSourceSize
        )
        assertEquals(expected.size, actual.size, "$label: pixel count")
        for (i in expected.indices) {
            val e = expected[i]
            val a = actual[i]
            assertEquals(
                0xFF, a ushr 24 and 0xFF,
                "$label pixel $i: composited output must be opaque"
            )
            val alpha = e ushr 24 and 0xFF
            for (shift in intArrayOf(16, 8, 0)) {
                val composite = ((e shr shift and 0xFF) * alpha + 127) / 255
                val diff = abs(composite - (a shr shift and 0xFF))
                assertTrue(
                    diff <= 2,
                    "$label pixel $i ch${shift / 8} off by $diff " +
                        "(cpu 0x%08X a=$alpha, gpu 0x%08X)".format(e, a)
                )
            }
        }
    }

    private val orange = 0xFF804020.toInt()
    private val teal = 0xFF10E0A0.toInt()
    private val violet = 0xFF4020E0.toInt()

    @Test
    fun everyBlendModeMatchesCpu() = runTest {
        val base = solid(baseSize, orange)
        val layerPixels = solid(baseSize, teal)
        // Offset by one full-output pixel — integer-aligned sampling.
        for (mode in PhotoBlendMode.entries) {
            val edits = PhotoEdits(
                layers = persistentListOf(
                    layer(
                        "l1", "src-a",
                        transform = PhotoLayerTransform(
                            translationX = 1f, translationY = 1f
                        ),
                        blendMode = mode
                    )
                )
            )
            assertParity(
                base, edits, mapOf("src-a" to layerPixels), "mode=$mode"
            )
        }
    }

    @Test
    fun opacityAndClipMatchCpu() = runTest {
        val base = solid(baseSize, orange)
        // A half-alpha layer under a clipped top layer — both the clip
        // alpha product and the opacity scalar are exercised.
        val bottom = layer(
            "b", "src-b",
            blendMode = PhotoBlendMode.Screen, opacity = .6f
        )
        val top = layer(
            "t", "src-t",
            transform = PhotoLayerTransform(translationX = 1f),
            blendMode = PhotoBlendMode.Overlay,
            opacity = .4f, clipToBelow = true
        )
        val edits = PhotoEdits(layers = persistentListOf(bottom, top))
        assertParity(
            base, edits,
            mapOf(
                "src-b" to solid(baseSize, 0x80FFFFFF.toInt()),
                "src-t" to solid(baseSize, violet)
            ),
            "opacity+clip"
        )
    }

    @Test
    fun transformScaleRotationAndFlipsMatchCpu() = runTest {
        val base = solid(baseSize, orange)
        val layerPixels = SrgbPixels(
            baseSize,
            IntArray(16) { i ->
                if (i % 3 == 0) violet else if (i % 2 == 0) teal else orange
            }
        )
        val edits = PhotoEdits(
            layers = persistentListOf(
                layer(
                    "l1", "src-a",
                    transform = PhotoLayerTransform(
                        translationX = .5f, translationY = -.5f,
                        scale = .65f, rotationDegrees = -8f,
                        flipHorizontal = true
                    ),
                    blendMode = PhotoBlendMode.SoftLight, opacity = .8f
                )
            )
        )
        assertParity(
            base, edits, mapOf("src-a" to layerPixels), "transform"
        )
    }

    @Test
    fun duplicateLayersShareOneSourceMatchCpu() = runTest {
        val base = solid(baseSize, orange)
        val layerPixels = solid(baseSize, teal)
        val edits = PhotoEdits(
            layers = persistentListOf(
                layer(
                    "l1", "shared",
                    transform = PhotoLayerTransform(translationX = 1f),
                    blendMode = PhotoBlendMode.Multiply
                ),
                layer(
                    "l2", "shared",
                    transform = PhotoLayerTransform(translationY = 1f),
                    blendMode = PhotoBlendMode.Screen, opacity = .5f
                )
            )
        )
        assertParity(
            base, edits, mapOf("shared" to layerPixels), "duplicate-source"
        )
    }

    /**
     * Translations are stored in full-resolution output pixels: with a
     * document source twice the decoded preview, (2,2) must land at (1,1)
     * on the 4x4 canvas — exactly what the placement effect computes.
     */
    @Test
    fun translationScalesByReferenceOutputSize() = runTest {
        val base = solid(baseSize, orange)
        val layerPixels = solid(baseSize, teal)
        val edits = PhotoEdits(
            layers = persistentListOf(
                layer(
                    "l1", "src-a",
                    transform = PhotoLayerTransform(
                        translationX = 2f, translationY = 2f
                    )
                )
            )
        )
        val document = document(edits)
        val fullSource = PixelSize(8, 8)
        val linear = mapOf(
            "base-src" to base.toLinearFrame(),
            "src-a" to layerPixels.toLinearFrame()
        )
        val source = object : FrameSource {
            override suspend fun read(sourceId: String): LinearRgbaFrame =
                linear.getValue(sourceId)
        }
        val expected = CpuFrameRenderer(source)
            .render(
                RenderRequest(
                    1,
                    document.copy(
                        source = document.source.copy(size = fullSource)
                    ).renderGraph(
                        PhotoMapping(baseSize, 1, edits.geometry).outputSize
                    )
                )
            )
            .toSrgbPixels()
            .copyPixels()
        val actual = shaderRender(
            base, edits, mapOf("src-a" to layerPixels),
            referenceSourceSize = fullSource
        )
        for (i in expected.indices) {
            val e = expected[i]
            val a = actual[i]
            val alpha = e ushr 24 and 0xFF
            for (shift in intArrayOf(16, 8, 0)) {
                val composite = ((e shr shift and 0xFF) * alpha + 127) / 255
                assertTrue(
                    abs(composite - (a shr shift and 0xFF)) <= 2,
                    "pixel $i ch${shift / 8}: cpu 0x%08X vs gpu 0x%08X"
                        .format(e, a)
                )
            }
        }
    }

    /** An identity-mapped layer samples mask texels exactly — paint and
     * erase strokes must modulate the same pixels the CPU evaluator
     * cuts. */
    @Test
    fun maskTexturePaintEraseMatchesCpu() = runTest {
        val base = solid(baseSize, orange)
        val layerPixels = solid(baseSize, teal)
        val mask = PhotoLayerMask(
            strokes = persistentListOf(
                PhotoMaskStroke(
                    points = persistentListOf(NormalizedPoint(.5f, .5f)),
                    diameter = .5f, hardness = 1f,
                    mode = PhotoMaskMode.Erase
                ),
                PhotoMaskStroke(
                    points = listOf(
                        NormalizedPoint(.05f, .05f),
                        NormalizedPoint(.2f, .05f)
                    ).toPersistentList(),
                    diameter = .4f, hardness = 1f,
                    mode = PhotoMaskMode.Paint
                )
            )
        )
        val edits = PhotoEdits(
            layers = persistentListOf(layer("l1", "src-a", mask = mask))
        )
        assertParity(
            base, edits, mapOf("src-a" to layerPixels), "mask"
        )
    }

    /**
     * Capability gate: the recipe caps at [MAX_PHOTO_IMAGE_LAYERS] — the
     * generated program must compile and bind every child shader and
     * uniform at the ceiling, not just below it. Sixteen visible layers
     * share one 1x1 source under distinct ids; blend modes cycle and
     * opacity varies so each unrolled composite reads its own uniforms.
     * Source bytes and generate/compile/draw times are logged
     * informationally — only conformance is asserted.
     */
    @Test
    fun sixteenLayersCompileBindAndMatchCpu() = runTest {
        var source = ""
        val genNanos = measureNanoTime {
            source = photoShaderSource(MAX_PHOTO_IMAGE_LAYERS)
        }
        val sourceBytes = source.encodeToByteArray().size

        val base = solid(baseSize, orange)
        val layerPixels = solid(PixelSize(1, 1), teal)
        val modes = PhotoBlendMode.entries
        val edits = PhotoEdits(
            layers = List(MAX_PHOTO_IMAGE_LAYERS) { i ->
                layer(
                    "cap-$i", "shared-src",
                    sourceSize = PixelSize(1, 1),
                    blendMode = modes[i % modes.size],
                    opacity = .5f + i * .03f
                )
            }.toPersistentList()
        )
        val failures = mutableListOf<Throwable>()
        var renderer: PhotoShaderRenderer? = null
        val compileNanos = measureNanoTime {
            renderer = createCompositePhotoShaderRenderer(
                sourceOf(base),
                layerSources(
                    edits.layers,
                    mapOf("shared-src" to layerPixels)
                ),
                baseSize
            ) { failures += it }
        }
        println(
            "composite-cap: source=$sourceBytes B, " +
                "generate=${genNanos / 1_000_000.0} ms, " +
                "compile=${compileNanos / 1_000_000.0} ms"
        )
        assertNotNull(renderer, "16-layer composite renderer unavailable")
        assertTrue(
            failures.isEmpty(),
            "shader construction failed: $failures"
        )

        val mapping = PhotoMapping(baseSize, 1, edits.geometry)
        val w = mapping.outputSize.width
        val h = mapping.outputSize.height
        val target = ImageBitmap(w, h)
        val drawNanos = measureNanoTime {
            try {
                CanvasDrawScope().draw(
                    Density(1f), LayoutDirection.Ltr, Canvas(target),
                    Size(w.toFloat(), h.toFloat())
                ) {
                    drawRect(Color.Black)
                    assertTrue(
                        renderer.draw(this, edits),
                        "16-layer shader rejected a matching recipe"
                    )
                }
            } finally {
                renderer.close()
            }
        }
        println("composite-cap: draw=${drawNanos / 1_000_000.0} ms")
        val actual = IntArray(w * h)
        target.readPixels(actual, 0, 0, w, h, 0, w)

        val expected = cpuRender(
            base, edits, mapOf("shared-src" to layerPixels)
        ).copyPixels()
        assertEquals(expected.size, actual.size, "16-layer: pixel count")
        for (i in expected.indices) {
            val e = expected[i]
            val a = actual[i]
            assertEquals(
                0xFF, a ushr 24 and 0xFF,
                "16-layer pixel $i: composited output must be opaque"
            )
            val alpha = e ushr 24 and 0xFF
            for (shift in intArrayOf(16, 8, 0)) {
                val composite = ((e shr shift and 0xFF) * alpha + 127) / 255
                val diff = abs(composite - (a shr shift and 0xFF))
                assertTrue(
                    diff <= 2,
                    "16-layer pixel $i ch${shift / 8} off by $diff " +
                        "(cpu 0x%08X a=$alpha, gpu 0x%08X)".format(e, a)
                )
            }
        }
    }

    /** The generator refuses stacks the recipe model itself rejects. */
    @Test
    fun photoShaderSourceRejectsCountsOutsideTheLayerCap() {
        assertFailsWith<IllegalArgumentException> {
            photoShaderSource(-1)
        }
        assertFailsWith<IllegalArgumentException> {
            photoShaderSource(MAX_PHOTO_IMAGE_LAYERS + 1)
        }
        // The cap itself still generates.
        photoShaderSource(MAX_PHOTO_IMAGE_LAYERS)
    }

    /**
     * A renderer compiled for one visible stack must decline — without a
     * failure report — any frame whose visible signature moved on:
     * visibility toggles, reordering and re-sourced layers all mismatch.
     */
    @Test
    fun drawDeclinesStaleVisibleSignatures() = runTest {
        val base = solid(baseSize, orange)
        val pixels = solid(baseSize, teal)
        val l1 = layer("l1", "src-a")
        val l2 = layer("l2", "src-b")
        val failures = mutableListOf<Throwable>()
        val renderer = createCompositePhotoShaderRenderer(
            sourceOf(base),
            layerSources(listOf(l1, l2), mapOf(
                "src-a" to pixels, "src-b" to pixels
            )),
            baseSize
        ) { failures += it }
        assertNotNull(renderer)
        val target = ImageBitmap(4, 4)
        val staleRecipes = listOf(
            "l2-hidden" to PhotoEdits(
                layers = persistentListOf(l1, l2.copy(visible = false))
            ),
            "reordered" to PhotoEdits(
                layers = persistentListOf(l2, l1)
            ),
            "dropped" to PhotoEdits(layers = persistentListOf(l1)),
            "re-sourced" to PhotoEdits(
                layers = persistentListOf(
                    l1, l2.copy(
                        source = MediaAsset.Image("src-c", "src-c", baseSize)
                    )
                )
            )
        )
        try {
            for ((label, edits) in staleRecipes) {
                CanvasDrawScope().draw(
                    Density(1f), LayoutDirection.Ltr, Canvas(target),
                    Size(4f, 4f)
                ) {
                    assertFalse(
                        renderer.draw(this, edits),
                        "$label: stale signature must be declined"
                    )
                }
            }
            // And the matching stack still draws afterwards — declining is
            // a routing answer, not renderer damage.
            CanvasDrawScope().draw(
                Density(1f), LayoutDirection.Ltr, Canvas(target),
                Size(4f, 4f)
            ) {
                assertTrue(
                    renderer.draw(
                        this,
                        PhotoEdits(layers = persistentListOf(l1, l2))
                    ),
                    "matching signature must still draw"
                )
            }
        } finally {
            renderer.close()
        }
        assertTrue(
            failures.isEmpty(),
            "signature declines must not report failures: $failures"
        )
    }
}
