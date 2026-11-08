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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

// The scheduler's owner scope is the test scope itself (foreground), not
// backgroundScope: runTest's advanceUntilIdle does not drive coroutines
// rooted in backgroundScope, so a worker there would never progress.
// Every test closes the scheduler in a finally block — the worker is an
// infinite collect loop whose parent is the test job, so a leaked worker
// would block test completion.
@OptIn(ExperimentalCoroutinesApi::class)
class PreviewSchedulerTest {

    private fun frame(value: Float) = LinearRgbaFrame(
        PixelSize(1, 1),
        floatArrayOf(value, value, value, 1.0f)
    )

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

    private fun request(revision: Long) = RenderRequest(revision, graph)

    /**
     * Renderer that records every invocation, throws [failure] if set, then
     * parks on a per-call gate so tests control completion order. With
     * [swallowCancellation] the gate's cancellation is caught and a dummy
     * frame returned — simulating a backend that ignores cancellation.
     */
    private class GatedRenderer(
        private val swallowCancellation: Boolean = false
    ) : FrameRenderer {
        val calls = mutableListOf<RenderRequest>()
        val gates = mutableListOf<CompletableDeferred<LinearRgbaFrame>>()
        var failure: Throwable? = null

        override suspend fun render(request: RenderRequest): LinearRgbaFrame {
            calls += request
            val gate = CompletableDeferred<LinearRgbaFrame>()
            gates += gate
            failure?.let { throw it }
            if (swallowCancellation) {
                return try {
                    gate.await()
                } catch (_: CancellationException) {
                    LinearRgbaFrame(
                        PixelSize(1, 1),
                        floatArrayOf(-1f, -1f, -1f, 1.0f)
                    )
                }
            }
            return gate.await()
        }
    }

    @Test
    fun `submitted request renders and publishes Ready`() = runTest {
        val renderer = GatedRenderer()
        val scheduler =
            PreviewScheduler(this, renderer, StandardTestDispatcher(testScheduler))
        try {
            assertEquals(PreviewState.Idle, scheduler.state.value)
            scheduler.submit(request(1))
            advanceUntilIdle()

            assertEquals(listOf(1L), renderer.calls.map { it.revision })
            val rendering = assertIs<PreviewState.Rendering>(scheduler.state.value)
            assertNull(rendering.previous)

            val expected = frame(0.5f)
            renderer.gates[0].complete(expected)
            advanceUntilIdle()

            val ready = assertIs<PreviewState.Ready>(scheduler.state.value)
            assertEquals(request(1), ready.request)
            assertSame(expected, ready.frame)
        } finally {
            scheduler.close()
        }
    }

    @Test
    fun `pending submissions conflate to the newest`() = runTest {
        val renderer = GatedRenderer()
        val scheduler =
            PreviewScheduler(this, renderer, StandardTestDispatcher(testScheduler))
        val states = mutableListOf<PreviewState>()
        val collector = launch { scheduler.state.collect { states += it } }
        try {
            scheduler.submit(request(1))
            advanceUntilIdle()
            assertEquals(listOf(1L), renderer.calls.map { it.revision })

            scheduler.submit(request(2))
            scheduler.submit(request(3))
            advanceUntilIdle()

            // r2 was conflated away before ever reaching the renderer.
            assertEquals(listOf(1L, 3L), renderer.calls.map { it.revision })

            val expected = frame(3f)
            renderer.gates[1].complete(expected)
            advanceUntilIdle()
            val ready = assertIs<PreviewState.Ready>(scheduler.state.value)
            assertEquals(request(3), ready.request)
            assertSame(expected, ready.frame)

            // Cancellation of superseded work never surfaces as a failure.
            assertTrue(states.none { it is PreviewState.Failed })
        } finally {
            scheduler.close()
            collector.cancel()
        }
    }

    @Test
    fun `stale result from a renderer ignoring cancellation is dropped`() = runTest {
        val renderer = GatedRenderer(swallowCancellation = true)
        val scheduler =
            PreviewScheduler(this, renderer, StandardTestDispatcher(testScheduler))
        val states = mutableListOf<PreviewState>()
        val collector = launch { scheduler.state.collect { states += it } }
        try {
            scheduler.submit(request(1))
            advanceUntilIdle()
            scheduler.submit(request(2))
            advanceUntilIdle()

            // The r1 render swallowed its cancellation and returned a dummy
            // frame; the generation check must have prevented publication.
            assertEquals(listOf(1L, 2L), renderer.calls.map { it.revision })
            assertTrue(
                states.none { it is PreviewState.Ready && it.request == request(1) }
            )

            val expected = frame(2f)
            renderer.gates[1].complete(expected)
            advanceUntilIdle()
            val ready = assertIs<PreviewState.Ready>(scheduler.state.value)
            assertEquals(request(2), ready.request)
            assertSame(expected, ready.frame)
        } finally {
            scheduler.close()
            collector.cancel()
        }
    }

    @Test
    fun `render failure becomes Failed and the next request works`() = runTest {
        val renderer = GatedRenderer()
        val scheduler =
            PreviewScheduler(this, renderer, StandardTestDispatcher(testScheduler))
        try {
            val boom = IllegalStateException("decode exploded")
            renderer.failure = boom

            scheduler.submit(request(1))
            advanceUntilIdle()
            val failed = assertIs<PreviewState.Failed>(scheduler.state.value)
            // The throwable crosses the render dispatcher boundary, where
            // stack-trace recovery may copy it — assert identity of type and
            // message, not instance.
            assertIs<IllegalStateException>(failed.error)
            assertEquals("decode exploded", failed.error.message)
            assertNull(failed.previous)

            renderer.failure = null
            scheduler.submit(request(2))
            advanceUntilIdle()
            val expected = frame(0.7f)
            renderer.gates[1].complete(expected)
            advanceUntilIdle()
            val ready = assertIs<PreviewState.Ready>(scheduler.state.value)
            assertSame(expected, ready.frame)
        } finally {
            scheduler.close()
        }
    }

    @Test
    fun `last good frame is retained through rendering and failure`() = runTest {
        val renderer = GatedRenderer()
        val scheduler =
            PreviewScheduler(this, renderer, StandardTestDispatcher(testScheduler))
        try {
            scheduler.submit(request(1))
            advanceUntilIdle()
            val good = frame(0.9f)
            renderer.gates[0].complete(good)
            advanceUntilIdle()

            scheduler.submit(request(2))
            advanceUntilIdle()
            val rendering = assertIs<PreviewState.Rendering>(scheduler.state.value)
            assertSame(good, rendering.previous)

            renderer.gates[1].completeExceptionally(IllegalStateException("bad frame"))
            advanceUntilIdle()
            val failed = assertIs<PreviewState.Failed>(scheduler.state.value)
            assertSame(good, failed.previous)
        } finally {
            scheduler.close()
        }
    }

    /**
     * Renderer whose gate is awaited under [NonCancellable]: superseding it
     * cannot stop the call, only block the next one. Tracks concurrency to
     * prove the one-active-renderer bound is real.
     */
    private class NonCancellableRenderer : FrameRenderer {
        val calls = mutableListOf<RenderRequest>()
        val gates = mutableListOf<CompletableDeferred<LinearRgbaFrame>>()
        var active = 0
        var maxActive = 0

        override suspend fun render(request: RenderRequest): LinearRgbaFrame {
            calls += request
            active += 1
            if (active > maxActive) maxActive = active
            val gate = CompletableDeferred<LinearRgbaFrame>()
            gates += gate
            return try {
                withContext(NonCancellable) { gate.await() }
            } finally {
                active -= 1
            }
        }
    }

    @Test
    fun `superseding a noncancellable render waits for it before starting`() = runTest {
        val renderer = NonCancellableRenderer()
        val scheduler =
            PreviewScheduler(this, renderer, StandardTestDispatcher(testScheduler))
        val states = mutableListOf<PreviewState>()
        val collector = launch { scheduler.state.collect { states += it } }
        try {
            scheduler.submit(request(1))
            advanceUntilIdle()
            assertEquals(listOf(1L), renderer.calls.map { it.revision })

            // New requests supersede r1, but the blocked render holds the
            // single worker: the next call cannot start until it finishes.
            scheduler.submit(request(2))
            scheduler.submit(request(3))
            advanceUntilIdle()
            assertEquals(listOf(1L), renderer.calls.map { it.revision })
            assertEquals(1, renderer.maxActive)

            // Release r1; its result is stale and must not publish.
            renderer.gates[0].complete(frame(-1f))
            advanceUntilIdle()
            assertEquals(listOf(1L, 3L), renderer.calls.map { it.revision })
            assertEquals(1, renderer.maxActive)
            assertTrue(
                states.none { it is PreviewState.Ready && it.request == request(1) }
            )

            val expected = frame(3f)
            renderer.gates[1].complete(expected)
            advanceUntilIdle()
            val ready = assertIs<PreviewState.Ready>(scheduler.state.value)
            assertEquals(request(3), ready.request)
            assertSame(expected, ready.frame)
        } finally {
            scheduler.close()
            collector.cancel()
        }
    }

    @Test
    fun `owner job cancellation resets state and rejects later submits`() = runTest {
        val renderer = GatedRenderer()
        // Dedicated owner job, not the test scope's own job.
        val ownerJob = Job()
        val owner = CoroutineScope(coroutineContext + ownerJob)
        val scheduler =
            PreviewScheduler(owner, renderer, StandardTestDispatcher(testScheduler))
        try {
            scheduler.submit(request(1))
            advanceUntilIdle()
            val good = frame(0.9f)
            renderer.gates[0].complete(good)
            advanceUntilIdle()
            assertIs<PreviewState.Ready>(scheduler.state.value)

            ownerJob.cancel()
            advanceUntilIdle()

            // Worker lifetime ended asynchronously: state reset, frame released.
            assertEquals(PreviewState.Idle, scheduler.state.value)
            // A submit that could never execute is rejected, not accepted.
            assertFailsWith<IllegalStateException> { scheduler.submit(request(2)) }
        } finally {
            scheduler.close()
            ownerJob.cancel()
        }
    }

    @Test
    fun `fatal error reports to the owner handler and resets state`() = runTest {
        val renderer = GatedRenderer()
        val reported = mutableListOf<Throwable>()
        val handler = CoroutineExceptionHandler { _, error -> reported += error }
        // Independent owner scope — not the test scope itself.
        val owner = CoroutineScope(
            SupervisorJob() + StandardTestDispatcher(testScheduler) + handler
        )
        val scheduler =
            PreviewScheduler(owner, renderer, StandardTestDispatcher(testScheduler))
        try {
            renderer.failure = AssertionError("fatal backend bug")
            scheduler.submit(request(1))
            advanceUntilIdle()

            // The Error escaped the per-request recovery: reported through the
            // owner's handler, worker ended, state cleaned up — never Failed.
            assertTrue(reported.any { it is AssertionError })
            assertEquals(PreviewState.Idle, scheduler.state.value)
            assertFailsWith<IllegalStateException> { scheduler.submit(request(2)) }
        } finally {
            scheduler.close()
            owner.coroutineContext[Job]?.cancel()
        }
    }

    @Test
    fun `close releases state ignores submissions and spares siblings`() = runTest {
        val renderer = GatedRenderer()
        val scheduler =
            PreviewScheduler(this, renderer, StandardTestDispatcher(testScheduler))
        val states = mutableListOf<PreviewState>()
        val collector = launch { scheduler.state.collect { states += it } }
        val sibling = launch { CompletableDeferred<Unit>().await() }
        try {
            scheduler.submit(request(1))
            advanceUntilIdle()
            assertIs<PreviewState.Rendering>(scheduler.state.value)

            scheduler.close()
            assertEquals(PreviewState.Idle, scheduler.state.value)

            scheduler.submit(request(2))
            advanceUntilIdle()
            assertEquals(listOf(1L), renderer.calls.map { it.revision })
            assertTrue(states.none { it is PreviewState.Failed })
            assertEquals(PreviewState.Idle, scheduler.state.value)
            assertTrue(sibling.isActive)
        } finally {
            scheduler.close()
            collector.cancel()
            sibling.cancel()
        }
    }
}
