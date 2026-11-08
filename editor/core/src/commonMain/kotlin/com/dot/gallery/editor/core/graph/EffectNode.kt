package com.dot.gallery.editor.core.graph

import kotlinx.collections.immutable.PersistentList

/**
 * One node in a [RenderGraph]: [inputs] are ids of other nodes, matched to
 * [effect]'s declared input count when the graph is planned.
 */
data class EffectNode(
    val id: String,
    val effect: EffectDescriptor,
    val inputs: PersistentList<String>,
    val parameters: EffectParameters
) {
    init {
        require(id.isNotBlank()) { "node id must be non-blank" }
        require(inputs.all { it.isNotBlank() }) { "node $id has a blank input id" }
    }
}
