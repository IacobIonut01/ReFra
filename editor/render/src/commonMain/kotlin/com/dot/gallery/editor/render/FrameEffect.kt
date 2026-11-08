package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.graph.EffectDescriptor
import com.dot.gallery.editor.core.graph.EffectParameters

/**
 * A pluggable effect implementation. [descriptor] pins the contract a node
 * must declare — a renderer only dispatches to an effect whose descriptor is
 * *equal* to the node's (id, version, arity, halo and temporal radius all
 * match), so a forged descriptor cannot smuggle a node into an unrelated
 * implementation.
 *
 * [render] receives the resolved input frames in the node's declared input
 * order plus the node's parameters. Unsupported arity or parameter type must
 * be rejected with [UnsupportedOperationException]; anything else propagates
 * as a render failure.
 */
interface FrameEffect {

    val descriptor: EffectDescriptor

    suspend fun render(
        inputs: List<LinearRgbaFrame>,
        parameters: EffectParameters
    ): LinearRgbaFrame
}
