package com.dot.gallery.editor.ui

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.dot.gallery.editor.core.model.PhotoEdits

/**
 * Runtime-shader preview backend for one source bitmap. [draw] fills the
 * whole scope rectangle with the recipe-applied image — crop, quarter
 * turns, flips and colour come from [edits] — and returns true on success.
 * False means the canvas/backend could not draw this frame (software
 * canvas, disposed state); the caller falls back to the CPU preview. The
 * backend never paints CPU pixels while claiming to be the shader path.
 */
internal interface PhotoShaderRenderer : AutoCloseable {
    fun draw(scope: DrawScope, edits: PhotoEdits): Boolean
    override fun close()
}
