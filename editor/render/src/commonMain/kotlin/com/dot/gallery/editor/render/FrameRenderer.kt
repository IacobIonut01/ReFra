package com.dot.gallery.editor.render

/** Evaluates a [RenderRequest] into a [LinearRgbaFrame]. */
interface FrameRenderer {
    suspend fun render(request: RenderRequest): LinearRgbaFrame
}
