package com.dot.gallery.editor.render

/**
 * State of the preview pipeline published by [PreviewScheduler].
 * [Rendering] and [Failed] retain the last successfully rendered frame so the
 * UI can keep showing it while work refines.
 */
sealed interface PreviewState {

    data object Idle : PreviewState

    data class Rendering(
        val request: RenderRequest,
        val previous: LinearRgbaFrame?
    ) : PreviewState

    data class Ready(
        val request: RenderRequest,
        val frame: LinearRgbaFrame
    ) : PreviewState

    data class Failed(
        val request: RenderRequest,
        val previous: LinearRgbaFrame?,
        val error: Throwable
    ) : PreviewState
}
