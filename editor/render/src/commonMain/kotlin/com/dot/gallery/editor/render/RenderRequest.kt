package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.graph.RenderGraph

/**
 * One unit of render work: the [graph] to evaluate tagged with the document
 * [revision] it was built from, so schedulers can tell stale work apart.
 */
data class RenderRequest(val revision: Long, val graph: RenderGraph) {
    init {
        require(revision >= 0L) { "revision must be non-negative: $revision" }
    }
}
