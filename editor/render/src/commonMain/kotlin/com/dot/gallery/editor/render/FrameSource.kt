package com.dot.gallery.editor.render

/**
 * Port that resolves a source id (the
 * [com.dot.gallery.editor.core.graph.EffectParameters.Source.sourceId] of a
 * `source.image` node in a RenderGraph) into decoded pixels. Implementations
 * are platform-owned; errors propagate to the caller.
 */
interface FrameSource {
    suspend fun read(sourceId: String): LinearRgbaFrame
}
