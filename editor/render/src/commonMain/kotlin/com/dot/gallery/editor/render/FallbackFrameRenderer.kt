package com.dot.gallery.editor.render

import kotlin.coroutines.cancellation.CancellationException

/**
 * Routes a request to an accelerated backend first and falls back to [cpu] for
 * the whole request when, and only when, the accelerated renderer fails to take
 * it — [BackendUnavailableException] or [UnsupportedOperationException].
 * [onFallback] is reported once per failed accelerated attempt before the CPU
 * re-render.
 *
 * [kotlin.coroutines.cancellation.CancellationException] always propagates
 * untouched; so do CPU failures, source errors and any other exception or
 * [Error] — only the two listed backend-availability signals trigger fallback.
 * A null [accelerated] renders directly on CPU.
 */
class FallbackFrameRenderer(
    private val accelerated: FrameRenderer?,
    private val cpu: FrameRenderer,
    private val onFallback: (Throwable) -> Unit
) : FrameRenderer {

    override suspend fun render(request: RenderRequest): LinearRgbaFrame {
        if (accelerated != null) {
            try {
                return accelerated.render(request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackendUnavailableException) {
                onFallback(e)
            } catch (e: UnsupportedOperationException) {
                onFallback(e)
            }
        }
        return cpu.render(request)
    }
}
