package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.graph.EffectDescriptor
import com.dot.gallery.editor.core.graph.EffectNode
import com.dot.gallery.editor.core.graph.EffectParameters
import com.dot.gallery.editor.core.graph.RenderGraph
import com.dot.gallery.editor.core.model.PixelSize
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest

class FallbackFrameRendererTest {

    private val graph = RenderGraph(
        persistentListOf(
            EffectNode(
                "s",
                EffectDescriptor.SourceImage,
                persistentListOf(),
                EffectParameters.Source("asset-a")
            )
        ),
        "s"
    )

    private fun request() = RenderRequest(1, graph)

    private fun frame() = LinearRgbaFrame(
        PixelSize(1, 1),
        floatArrayOf(0.5f, 0.5f, 0.5f, 1.0f)
    )

    private class StubRenderer(
        private val outcome: suspend (RenderRequest) -> LinearRgbaFrame
    ) : FrameRenderer {
        val calls = mutableListOf<RenderRequest>()
        override suspend fun render(request: RenderRequest): LinearRgbaFrame {
            calls += request
            return outcome(request)
        }
    }

    @Test
    fun `backend unavailable falls back to CPU and reports once`() = runTest {
        val boom = BackendUnavailableException("no GPU")
        val accelerated = StubRenderer { throw boom }
        val cpuFrame = frame()
        val cpu = StubRenderer { cpuFrame }
        val reported = mutableListOf<Throwable>()
        val fallback = FallbackFrameRenderer(accelerated, cpu) { reported += it }

        val result = fallback.render(request())

        assertSame(cpuFrame, result)
        assertEquals(1, accelerated.calls.size)
        assertEquals(1, cpu.calls.size)
        assertEquals(listOf<Throwable>(boom), reported)
    }

    @Test
    fun `unsupported effect falls back`() = runTest {
        val accelerated = StubRenderer { throw UnsupportedOperationException("no such effect") }
        val cpuFrame = frame()
        val cpu = StubRenderer { cpuFrame }
        val reported = mutableListOf<Throwable>()
        val fallback = FallbackFrameRenderer(accelerated, cpu) { reported += it }

        assertSame(cpuFrame, fallback.render(request()))
        assertEquals(1, reported.size)
        assertEquals(1, cpu.calls.size)
    }

    @Test
    fun `other failures from accelerated propagate without fallback`() = runTest {
        val boom = IllegalStateException("arbitrary failure")
        val accelerated = StubRenderer { throw boom }
        val cpu = StubRenderer { frame() }
        val reported = mutableListOf<Throwable>()
        val fallback = FallbackFrameRenderer(accelerated, cpu) { reported += it }

        val error = assertFailsWith<IllegalStateException> { fallback.render(request()) }
        assertSame(boom, error)
        assertEquals(0, cpu.calls.size)
        assertEquals(0, reported.size)
    }

    @Test
    fun `cancellation always propagates`() = runTest {
        val boom = CancellationException("cancelled")
        val accelerated = StubRenderer { throw boom }
        val cpu = StubRenderer { frame() }
        val reported = mutableListOf<Throwable>()
        val fallback = FallbackFrameRenderer(accelerated, cpu) { reported += it }

        val error = assertFailsWith<CancellationException> { fallback.render(request()) }
        assertSame(boom, error)
        assertEquals(0, cpu.calls.size)
        assertEquals(0, reported.size)
    }

    @Test
    fun `no accelerated backend goes straight to CPU`() = runTest {
        val cpuFrame = frame()
        val cpu = StubRenderer { cpuFrame }
        val reported = mutableListOf<Throwable>()
        val fallback = FallbackFrameRenderer(null, cpu) { reported += it }

        assertSame(cpuFrame, fallback.render(request()))
        assertEquals(1, cpu.calls.size)
        assertEquals(0, reported.size)
    }

    @Test
    fun `CPU failure after fallback propagates`() = runTest {
        val accelerated = StubRenderer { throw BackendUnavailableException("no GPU") }
        val cpuBoom = IllegalStateException("cpu failed")
        val cpu = StubRenderer { throw cpuBoom }
        val reported = mutableListOf<Throwable>()
        val fallback = FallbackFrameRenderer(accelerated, cpu) { reported += it }

        val error = assertFailsWith<IllegalStateException> { fallback.render(request()) }
        assertSame(cpuBoom, error)
        assertEquals(1, reported.size)
        assertEquals(1, cpu.calls.size)
    }
}
