package com.dot.gallery.editor.render

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Latest-request-wins preview scheduling over a [FrameRenderer].
 *
 * One render is in flight at a time; the conflated mailbox holds at most one
 * pending request, so rapid submissions collapse to the newest. A new request
 * cancels the in-flight render and waits for that cancellation to complete
 * before the next render starts — there is never more than one renderer call
 * active, even against a backend that ignores cancellation. A generation
 * check guards publication, so a superseded render that still returns cannot
 * overwrite a newer result.
 *
 * [submit] and [close] are confined to the thread that owns [scope]
 * (typically the UI thread); the worker coroutine runs on the scope's
 * dispatcher so [state] is published there, while the render itself runs on
 * [dispatcher]. [generation] and [closed] are confined to that thread too.
 *
 * Lifecycle:
 * - An unexpected render failure ([Exception]) becomes [PreviewState.Failed]
 *   and the scheduler stays usable for the next request; reporting/logging is
 *   the subscriber's responsibility.
 * - A fatal [Error] (e.g. OOM) propagates out of the worker to the owner
 *   scope's exception handling, the worker ends, [state] resets to
 *   [PreviewState.Idle], and further [submit]s throw [IllegalStateException]
 *   because there is no live worker left to honour them.
 * - If the owner scope's job is cancelled the worker ends the same way:
 *   state resets to [PreviewState.Idle] asynchronously and later submissions
 *   are rejected the same way.
 * - [close] cancels only the scheduler's own child job — the owning scope and
 *   its siblings keep running — discards any buffered request, releases the
 *   retained frame and resets [state] to [PreviewState.Idle] synchronously.
 *   Submissions after [close] are ignored.
 */
class PreviewScheduler(
    scope: CoroutineScope,
    private val renderer: FrameRenderer,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private class Pending(val generation: Long, val request: RenderRequest)

    private val mailbox = Channel<Pending>(Channel.CONFLATED)

    // Confined to the owner thread: written by submit/close, read by the
    // worker. The mailbox handoff publishes the write to the worker.
    private var generation = 0L
    private var closed = false

    private val _state = MutableStateFlow<PreviewState>(PreviewState.Idle)
    val state: StateFlow<PreviewState> = _state.asStateFlow()

    // Own supervisor child: cancelling it never touches the owner scope or its
    // other children, and worker failures cannot propagate into it either.
    private val workerJob = SupervisorJob(scope.coroutineContext[Job])
    private val workerScope =
        CoroutineScope(scope.coroutineContext.minusKey(Job) + workerJob)

    private val worker = workerScope.launch {
        try {
            mailbox.receiveAsFlow().collectLatest { pending ->
                if (pending.generation != generation) return@collectLatest
                _state.value = PreviewState.Rendering(pending.request, lastFrame())
                try {
                    val frame = withContext(dispatcher) {
                        renderer.render(pending.request)
                    }
                    if (pending.generation == generation) {
                        _state.value = PreviewState.Ready(pending.request, frame)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (pending.generation == generation) {
                        _state.value =
                            PreviewState.Failed(pending.request, lastFrame(), e)
                    }
                }
                // Error (and Throwable non-Exception) escapes: the worker ends
                // and the owner scope's exception handling sees it.
            }
        } finally {
            // Worker lifetime actually ended (owner cancelled, fatal failure):
            // discard any buffered request and release the retained frame.
            mailbox.cancel()
            _state.value = PreviewState.Idle
        }
    }

    /**
     * Queues [request] for rendering, superseding any in-flight or pending
     * request. Ignored after [close]; throws [IllegalStateException] if the
     * worker has already ended (owner cancelled or fatal failure) because a
     * queued request could never execute.
     */
    fun submit(request: RenderRequest) {
        if (closed) return
        check(worker.isActive) {
            "PreviewScheduler worker has ended; the scheduler can no longer accept work"
        }
        check(generation < Long.MAX_VALUE) { "request generation overflow" }
        generation += 1L
        mailbox.trySend(Pending(generation, request))
    }

    fun close() {
        if (closed) return
        closed = true
        mailbox.cancel()
        workerJob.cancel()
        _state.value = PreviewState.Idle
    }

    private fun lastFrame(): LinearRgbaFrame? = when (val s = _state.value) {
        is PreviewState.Ready -> s.frame
        is PreviewState.Rendering -> s.previous
        is PreviewState.Failed -> s.previous
        PreviewState.Idle -> null
    }
}
