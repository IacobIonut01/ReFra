package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.graph.EffectDescriptor
import com.dot.gallery.editor.core.graph.EffectNode
import com.dot.gallery.editor.core.graph.EffectParameters
import com.dot.gallery.editor.core.graph.RenderGraph
import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.PixelSize
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

class CpuFrameRendererTest {

    private fun frame(width: Int, height: Int, vararg components: Float): LinearRgbaFrame =
        LinearRgbaFrame(PixelSize(width, height), floatArrayOf(*components))

    // Node ids and source ids are deliberately different strings: identity is
    // carried by EffectParameters.Source, not the node id.
    private fun source(nodeId: String, sourceId: String) = EffectNode(
        id = nodeId,
        effect = EffectDescriptor.SourceImage,
        inputs = persistentListOf(),
        parameters = EffectParameters.Source(sourceId)
    )

    private fun color(
        id: String,
        input: String,
        adjustments: ColorAdjustments = ColorAdjustments()
    ) = EffectNode(
        id = id,
        effect = EffectDescriptor.ColorBasic,
        inputs = persistentListOf(input),
        parameters = EffectParameters.Color(adjustments)
    )

    private fun request(output: String, vararg nodes: EffectNode) =
        RenderRequest(0, RenderGraph(persistentListOf(*nodes), output))

    private class MapSource(private val frames: Map<String, LinearRgbaFrame>) : FrameSource {
        val reads = mutableListOf<String>()
        override suspend fun read(sourceId: String): LinearRgbaFrame {
            reads += sourceId
            return frames.getValue(sourceId)
        }
    }

    @Test
    fun `source parameter id is passed to the source port`() = runTest {
        val frame = frame(1, 1, 0.5f, 0.5f, 0.5f, 1.0f)
        val sources = MapSource(mapOf("asset-a" to frame))
        val renderer = CpuFrameRenderer(sources)
        val result = renderer.render(request("node-a", source("node-a", "asset-a")))
        assertEquals(listOf("asset-a"), sources.reads)
        assertSame(frame, result)
    }

    @Test
    fun `two nodes may reference the same source`() = runTest {
        val frame = frame(1, 1, 0.5f, 0.5f, 0.5f, 1.0f)
        val sources = MapSource(mapOf("asset-a" to frame))
        val mix = EffectNode(
            id = "mix",
            effect = EffectDescriptor("ext.mix", 1, 2),
            inputs = persistentListOf("n1", "n2"),
            parameters = EffectParameters.Extension(persistentMapOf())
        )
        val mixEffect = object : FrameEffect {
            override val descriptor = EffectDescriptor("ext.mix", 1, 2)
            override suspend fun render(
                inputs: List<LinearRgbaFrame>,
                parameters: EffectParameters
            ): LinearRgbaFrame = inputs[0]
        }
        val renderer = CpuFrameRenderer(sources, listOf(BasicColorEffect(), mixEffect))
        val result = renderer.render(
            request("mix", source("n1", "asset-a"), source("n2", "asset-a"), mix)
        )
        assertEquals(listOf("asset-a", "asset-a"), sources.reads)
        assertSame(frame, result)
    }

    @Test
    fun `registered two-input extension effect renders`() = runTest {
        val a = frame(1, 1, 0.2f, 0.2f, 0.2f, 1.0f)
        val b = frame(1, 1, 0.6f, 0.6f, 0.6f, 1.0f)
        val sources = MapSource(mapOf("asset-a" to a, "asset-b" to b))
        // Deterministic mix fixture: per-channel average.
        val mixEffect = object : FrameEffect {
            override val descriptor = EffectDescriptor("ext.mix", 1, 2)
            override suspend fun render(
                inputs: List<LinearRgbaFrame>,
                parameters: EffectParameters
            ): LinearRgbaFrame {
                if (parameters !is EffectParameters.Extension) {
                    throw UnsupportedOperationException("ext.mix needs Extension")
                }
                val (x, y) = inputs
                val out = FloatArray(x.componentCount) { i ->
                    (x.component(i) + y.component(i)) / 2f
                }
                return LinearRgbaFrame.fromOwned(x.size, out)
            }
        }
        val mix = EffectNode(
            id = "mix",
            effect = mixEffect.descriptor,
            inputs = persistentListOf("n1", "n2"),
            parameters = EffectParameters.Extension(persistentMapOf("mode" to "avg"))
        )
        val renderer = CpuFrameRenderer(sources, listOf(BasicColorEffect(), mixEffect))
        val result = renderer.render(
            request("mix", source("n1", "asset-a"), source("n2", "asset-b"), mix)
        )
        assertContentEquals(
            floatArrayOf(0.4f, 0.4f, 0.4f, 1.0f),
            result.copyPixels()
        )
    }

    @Test
    fun `duplicate effect registrations are rejected`() {
        val fake = object : FrameEffect {
            override val descriptor = EffectDescriptor.ColorBasic
            override suspend fun render(
                inputs: List<LinearRgbaFrame>,
                parameters: EffectParameters
            ): LinearRgbaFrame = inputs.single()
        }
        assertFailsWith<IllegalArgumentException> {
            CpuFrameRenderer(MapSource(emptyMap()), listOf(BasicColorEffect(), fake))
        }
    }

    @Test
    fun `registering the reserved source id is rejected`() {
        val fake = object : FrameEffect {
            override val descriptor = EffectDescriptor.SourceImage
            override suspend fun render(
                inputs: List<LinearRgbaFrame>,
                parameters: EffectParameters
            ): LinearRgbaFrame = error("unreachable")
        }
        assertFailsWith<IllegalArgumentException> {
            CpuFrameRenderer(MapSource(emptyMap()), listOf(fake))
        }
        // Any version of the reserved id is equally rejected.
        val otherVersion = object : FrameEffect {
            override val descriptor = EffectDescriptor("source.image", 9, 0)
            override suspend fun render(
                inputs: List<LinearRgbaFrame>,
                parameters: EffectParameters
            ): LinearRgbaFrame = error("unreachable")
        }
        assertFailsWith<IllegalArgumentException> {
            CpuFrameRenderer(MapSource(emptyMap()), listOf(otherVersion))
        }
    }

    @Test
    fun `forged descriptors matching only id and version are rejected`() = runTest {
        val frame = frame(1, 1, 0.5f, 0.5f, 0.5f, 1.0f)
        val sources = MapSource(mapOf("asset-a" to frame))
        val renderer = CpuFrameRenderer(sources)

        // Same id+version but a non-zero halo: not the real descriptor.
        val forgedColor = color("c", "n1")
            .copy(effect = EffectDescriptor.ColorBasic.copy(haloPixels = 2))
        assertFailsWith<UnsupportedOperationException> {
            renderer.render(request("c", source("n1", "asset-a"), forgedColor))
        }

        // A forged source descriptor is likewise not the real thing.
        val forgedSource = source("n1", "asset-a")
            .copy(effect = EffectDescriptor.SourceImage.copy(haloPixels = 1))
        assertFailsWith<UnsupportedOperationException> {
            renderer.render(request("n1", forgedSource))
        }

        // Neither attempt read a source.
        assertEquals(emptyList(), sources.reads)
    }

    @Test
    fun `unreachable unknown effect is never evaluated`() = runTest {
        val frame = frame(1, 1, 0.5f, 0.5f, 0.5f, 1.0f)
        val sources = MapSource(mapOf("asset-a" to frame))
        val unknown = EffectNode(
            "orphan",
            EffectDescriptor("ext.blur", 1, 1),
            persistentListOf("n1"),
            EffectParameters.Extension(persistentMapOf())
        )
        val renderer = CpuFrameRenderer(sources)
        // 'orphan' is valid but disconnected from the output: no error, no read
        // of its input beyond what the output path needs.
        val result = renderer.render(
            request("n1", source("n1", "asset-a"), unknown)
        )
        assertSame(frame, result)
        assertEquals(listOf("asset-a"), sources.reads)
    }

    @Test
    fun `unsupported downstream stage fails before any source read`() = runTest {
        val frame = frame(1, 1, 0.5f, 0.5f, 0.5f, 1.0f)
        val sources = MapSource(mapOf("asset-a" to frame))
        val unknown = EffectNode(
            "x",
            EffectDescriptor("ext.blur", 1, 1),
            persistentListOf("n1"),
            EffectParameters.Extension(persistentMapOf("radius" to "4"))
        )
        val renderer = CpuFrameRenderer(sources)
        val error = assertFailsWith<UnsupportedOperationException> {
            renderer.render(request("x", source("n1", "asset-a"), unknown))
        }
        assertTrue(error.message.orEmpty().contains("ext.blur"))
        // The unsupported stage was detected before the decode started.
        assertEquals(emptyList(), sources.reads)
    }

    @Test
    fun `identity adjustment shares the immutable input frame`() = runTest {
        val frame = frame(1, 1, 0.5f, 0.5f, 0.5f, 1.0f)
        val renderer = CpuFrameRenderer(MapSource(mapOf("asset-a" to frame)))
        val result = renderer.render(
            request("c", source("n1", "asset-a"), color("c", "n1"))
        )
        assertSame(frame, result)
    }

    @Test
    fun `exposure of one stop doubles colour and keeps alpha`() = runTest {
        val frame = frame(1, 1, 0.25f, 0.5f, 0.125f, 0.5f)
        val renderer = CpuFrameRenderer(MapSource(mapOf("asset-a" to frame)))
        val result = renderer.render(
            request(
                "c", source("n1", "asset-a"),
                color("c", "n1", ColorAdjustments(exposureStops = 1f))
            )
        )
        assertContentEquals(floatArrayOf(0.5f, 1.0f, 0.25f, 0.5f), result.copyPixels())
    }

    @Test
    fun `contrast pivots around 0 point 18`() = runTest {
        val frame = frame(2, 1, 0.18f, 0.18f, 0.18f, 1.0f, 0.4f, 0.4f, 0.4f, 1.0f)
        val renderer = CpuFrameRenderer(MapSource(mapOf("asset-a" to frame)))
        val result = renderer.render(
            request(
                "c", source("n1", "asset-a"),
                color("c", "n1", ColorAdjustments(contrast = 2f))
            )
        )
        val c2 = (0.4f - 0.18f) * 2f + 0.18f
        val expected = floatArrayOf(
            0.18f, 0.18f, 0.18f, 1.0f,
            c2, c2, c2, 1.0f
        )
        assertContentEquals(expected, result.copyPixels())
    }

    @Test
    fun `zero saturation produces Rec709 luma`() = runTest {
        val frame = frame(1, 1, 0.8f, 0.4f, 0.2f, 1.0f)
        val renderer = CpuFrameRenderer(MapSource(mapOf("asset-a" to frame)))
        val result = renderer.render(
            request(
                "c", source("n1", "asset-a"),
                color("c", "n1", ColorAdjustments(saturation = 0f))
            )
        )
        val luma = 0.2126f * 0.8f + 0.7152f * 0.4f + 0.0722f * 0.2f
        assertContentEquals(floatArrayOf(luma, luma, luma, 1.0f), result.copyPixels())
    }

    @Test
    fun `tonal operation matches the defined curve`() = runTest {
        // The contract's eight lead-checked expectations: a neutral-grey
        // input (luminance == channel value) shifts by the scalar curve's
        // offset. Tolerance covers the float luminance sum's last-bit
        // rounding, not the curve itself.
        val cases = listOf(
            // (input luminance, adjustments, expected new luminance)
            Triple(0.25f, ColorAdjustments(shadows = 1f), 0.390625f),
            Triple(0.25f, ColorAdjustments(shadows = -1f), 0.109375f),
            Triple(0.75f, ColorAdjustments(highlights = 1f), 0.890625f),
            Triple(0.75f, ColorAdjustments(highlights = -1f), 0.609375f),
            Triple(0f, ColorAdjustments(blacks = 1f), 0.125f),
            Triple(0.25f, ColorAdjustments(blacks = -1f), 0.14453125f),
            Triple(1f, ColorAdjustments(whites = 1f), 1.125f),
            Triple(1f, ColorAdjustments(whites = -1f), 0.875f)
        )
        for ((input, adjustments, expected) in cases) {
            val frame = frame(1, 1, input, input, input, 1f)
            val renderer = CpuFrameRenderer(MapSource(mapOf("asset-a" to frame)))
            val result = renderer.render(
                request("c", source("n1", "asset-a"), color("c", "n1", adjustments))
            )
            val out = result.copyPixels()
            for (channel in 0..2) {
                assertEquals(
                    expected, out[channel], 1e-5f,
                    "$adjustments on luminance $input"
                )
            }
            assertEquals(1f, out[3])
        }
    }

    @Test
    fun `tonal offset preserves channel differences and alpha`() = runTest {
        // The delta is a single luminance offset added equally to RGB, so
        // chroma differences survive exactly and alpha passes through.
        val frame = frame(2, 1, 0.4f, 0.2f, 0.6f, 1f, 0.1f, 0.05f, 0.02f, 0f)
        val renderer = CpuFrameRenderer(MapSource(mapOf("asset-a" to frame)))
        val adjustments = ColorAdjustments(shadows = 0.6f, blacks = 0.5f)
        val result = renderer.render(
            request("c", source("n1", "asset-a"), color("c", "n1", adjustments))
        )
        val out = result.copyPixels()
        val input = floatArrayOf(0.4f, 0.2f, 0.6f, 1f, 0.1f, 0.05f, 0.02f, 0f)
        for (px in 0..1) {
            val base = px * 4
            val delta = out[base] - input[base]
            assertEquals(delta, out[base + 1] - input[base + 1], 1e-6f)
            assertEquals(delta, out[base + 2] - input[base + 2], 1e-6f)
            // Alpha — including fully transparent — is bit-identical.
            assertEquals(input[base + 3].toRawBits(), out[base + 3].toRawBits())
        }
    }

    @Test
    fun `stacked effects run in exposure contrast saturation order`() = runTest {
        // Grey 0.3 -> exposure +1 -> 0.6 -> contrast 2 -> (0.6-0.18)*2+0.18 = 1.02
        // saturation does not change a grey pixel's chroma.
        val frame = frame(1, 1, 0.3f, 0.3f, 0.3f, 1.0f)
        val renderer = CpuFrameRenderer(MapSource(mapOf("asset-a" to frame)))
        val result = renderer.render(
            request(
                "c2",
                source("n1", "asset-a"),
                color("c1", "n1", ColorAdjustments(exposureStops = 1f)),
                color("c2", "c1", ColorAdjustments(contrast = 2f, saturation = 0.5f))
            )
        )
        val expected = (0.3f * 2f - 0.18f) * 2f + 0.18f
        assertContentEquals(floatArrayOf(expected, expected, expected, 1.0f), result.copyPixels())
    }

    @Test
    fun `neutral stages bypass and preserve bits exactly`() = runTest {
        val pixels = floatArrayOf(
            1e-10f, -1e-10f, 3e-10f, 0f,
            -0f, 0.5f, 1e30f, 1f
        )
        val frame = frame(2, 1, *pixels)
        val renderer = CpuFrameRenderer(MapSource(mapOf("asset-a" to frame)))

        // Pure exposure +1: contrast/saturation stages are skipped entirely,
        // so very small values double exactly instead of being rounded
        // through the 0.18 pivot. -0 doubles to -0.
        val exposed = renderer.render(
            request(
                "c", source("n1", "asset-a"),
                color("c", "n1", ColorAdjustments(exposureStops = 1f))
            )
        )
        val out = exposed.copyPixels()
        assertEquals(2e-10f, out[0])
        assertEquals(-2e-10f, out[1])
        assertEquals(6e-10f, out[2])
        assertTrue(out[4] == 0f && out[4].toRawBits() < 0)
        assertEquals(2e30f, out[6])
        assertEquals(1f, out[7])

        // Pure contrast on tiny values is identity-free but must not be
        // skipped when contrast != 1: (1e-10 - 0.18) * 2 + 0.18.
        val contrasted = renderer.render(
            request(
                "c", source("n1", "asset-a"),
                color("c", "n1", ColorAdjustments(contrast = 2f))
            )
        )
        val expected = (1e-10f - 0.18f) * 2f + 0.18f
        assertEquals(expected, contrasted.copyPixels()[0])
    }

    @Test
    fun `full identity preserves every bit including negative zero`() = runTest {
        val pixels = floatArrayOf(1e-10f, -1e-10f, 3e-10f, 0f, -0f, Float.MAX_VALUE, 1e-30f, 0.75f)
        val frame = frame(2, 1, *pixels)
        val renderer = CpuFrameRenderer(MapSource(mapOf("asset-a" to frame)))
        val result = renderer.render(
            request("c", source("n1", "asset-a"), color("c", "n1"))
        )
        assertSame(frame, result)
        val raw = pixels.map { it.toRawBits() }
        val out = result.copyPixels().map { it.toRawBits() }
        assertEquals(raw, out)
    }

    @Test
    fun `out-of-range scene colour is not clamped`() = runTest {
        val frame = frame(1, 1, 2.0f, -0.25f, 1.5f, 0.0f)
        val renderer = CpuFrameRenderer(MapSource(mapOf("asset-a" to frame)))
        val result = renderer.render(
            request(
                "c", source("n1", "asset-a"),
                color("c", "n1", ColorAdjustments(exposureStops = -1f))
            )
        )
        assertContentEquals(floatArrayOf(1.0f, -0.125f, 0.75f, 0.0f), result.copyPixels())
    }

    @Test
    fun `non-finite computation fails clearly`() = runTest {
        val frame = frame(1, 1, 1e38f, 0.5f, 0.5f, 1.0f)
        val renderer = CpuFrameRenderer(MapSource(mapOf("asset-a" to frame)))
        assertFailsWith<IllegalStateException> {
            renderer.render(
                request(
                    "c", source("n1", "asset-a"),
                    color("c", "n1", ColorAdjustments(exposureStops = 10f))
                )
            )
        }
    }

    @Test
    fun `parameter type mismatch on color effect is rejected`() = runTest {
        val frame = frame(1, 1, 0.5f, 0.5f, 0.5f, 1.0f)
        val renderer = CpuFrameRenderer(MapSource(mapOf("asset-a" to frame)))
        val badParams = color("c", "n1").copy(
            parameters = EffectParameters.Extension(persistentMapOf())
        )
        assertFailsWith<UnsupportedOperationException> {
            renderer.render(request("c", source("n1", "asset-a"), badParams))
        }
        val badSource = source("n1", "asset-a").copy(
            parameters = EffectParameters.Color(ColorAdjustments())
        )
        assertFailsWith<UnsupportedOperationException> {
            renderer.render(request("n1", badSource))
        }
    }

    @Test
    fun `source errors propagate unchanged`() = runTest {
        val boom = IllegalStateException("decode failed")
        val failing = object : FrameSource {
            override suspend fun read(sourceId: String): LinearRgbaFrame = throw boom
        }
        val renderer = CpuFrameRenderer(failing)
        val error = assertFailsWith<IllegalStateException> {
            renderer.render(request("n1", source("n1", "asset-a")))
        }
        assertSame(boom, error)
    }

    @Test
    fun `invalid graphs fail before rendering`() = runTest {
        val renderer = CpuFrameRenderer(MapSource(emptyMap()))
        assertFailsWith<IllegalArgumentException> {
            renderer.render(request("c", color("c", "ghost")))
        }
    }

    @Test
    fun `cancellation between rows aborts the kernel`() = runTest {
        // The source cancels the dedicated render job after producing pixels;
        // the kernel's per-row cancellation check must abort before finishing.
        // A standalone Job is cancelled (never the ambient test job).
        val frame = frame(4, 4, *FloatArray(64) { 0.5f })
        val renderJob = Job()
        val cancelling = object : FrameSource {
            override suspend fun read(sourceId: String): LinearRgbaFrame {
                renderJob.cancel()
                return frame
            }
        }
        val renderer = CpuFrameRenderer(cancelling)
        assertFailsWith<CancellationException> {
            withContext(renderJob) {
                renderer.render(
                    request(
                        "c", source("n1", "asset-a"),
                        color("c", "n1", ColorAdjustments(1f))
                    )
                )
            }
        }
    }
}
