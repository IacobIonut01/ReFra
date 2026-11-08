package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.graph.EffectDescriptor
import com.dot.gallery.editor.core.graph.EffectNode
import com.dot.gallery.editor.core.graph.EffectParameters
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Reference renderer evaluating a [com.dot.gallery.editor.core.graph.RenderGraph]
 * on the CPU in linear-light straight-alpha RGBA. It defines the observable
 * behaviour the native kernels and future GPU backends must match.
 *
 * Execution is modular: each node is resolved to a *plan entry* before any
 * work starts, so an unsupported downstream stage rejects the whole request
 * before a single source is fetched. Two node kinds exist:
 * - a node whose descriptor equals [EffectDescriptor.SourceImage] exactly
 *   (every field, not just id/version) reads pixels through the
 *   [FrameSource] port using [EffectParameters.Source.sourceId] — the node id
 *   is graph identity only and never reaches the source port;
 * - any other node must resolve against the [FrameEffect] registry keyed by
 *   (descriptor id, version) *and* demand full descriptor equality, so a
 *   forged `color.basic` descriptor with different arity or halo/temporal
 *   requirements cannot ride the registered implementation.
 *
 * Anything unresolvable throws [UnsupportedOperationException] carrying the
 * effect identity — unknown nodes are never silently dropped. Intermediate
 * frames are released as soon as their last consumer has run. Cancellation is
 * checked per node (per-row inside effects).
 *
 * [effects] is snapshotted at construction; registrations must have unique
 * (id, version) keys and may not claim the reserved `source.image` id.
 */
class CpuFrameRenderer(
    private val sources: FrameSource,
    effects: List<FrameEffect> = listOf(
        BasicColorEffect(),
        GeometryPhotoEffect(),
        PhotoLayerPlacementEffect(),
        PhotoCompositeEffect(),
        PhotoSpatialEffect(),
        PhotoFinishingEffect(),
        PhotoRetouchEffect()
    )
) : FrameRenderer {

    private val effectRegistry: Map<Pair<String, Int>, FrameEffect>

    init {
        val registry = HashMap<Pair<String, Int>, FrameEffect>(effects.size)
        for (effect in effects) {
            val key = effect.descriptor.id to effect.descriptor.version
            require(key.first != EffectDescriptor.SourceImage.id) {
                "effect id ${EffectDescriptor.SourceImage.id} is reserved for " +
                    "the source port and cannot be registered"
            }
            require(registry.put(key, effect) == null) {
                "duplicate effect registration: ${key.first} v${key.second}"
            }
        }
        effectRegistry = registry
    }

    private sealed interface PlannedNode

    private class PlannedSource(val sourceId: String) : PlannedNode

    private class PlannedEffect(
        val effect: FrameEffect,
        val parameters: EffectParameters
    ) : PlannedNode

    override suspend fun render(request: RenderRequest): LinearRgbaFrame {
        val ordered = request.graph.orderedNodes()

        // resolve every reachable node before touching a source, so
        // an unsupported stage fails fast instead of starting a costly decode.
        val plan = ArrayList<PlannedNode>(ordered.size)
        for (node in ordered) {
            plan += resolve(node)
        }

        // execute, releasing intermediates once their last consumer
        // has run.
        val remainingConsumers = HashMap<String, Int>(ordered.size)
        for (node in ordered) {
            for (input in node.inputs) {
                remainingConsumers[input] = (remainingConsumers[input] ?: 0) + 1
            }
        }
        val frames = HashMap<String, LinearRgbaFrame>()
        for ((index, node) in ordered.withIndex()) {
            coroutineContext.ensureActive()
            val result = when (val entry = plan[index]) {
                is PlannedSource -> sources.read(entry.sourceId)
                is PlannedEffect -> {
                    val inputs = node.inputs.map { frames.getValue(it) }
                    entry.effect.render(inputs, entry.parameters)
                }
            }
            for (input in node.inputs) {
                val remaining = remainingConsumers.getValue(input) - 1
                if (remaining == 0) {
                    frames.remove(input)
                    remainingConsumers.remove(input)
                } else {
                    remainingConsumers[input] = remaining
                }
            }
            frames[node.id] = result
        }
        return frames.getValue(request.graph.outputId)
    }

    private fun resolve(node: EffectNode): PlannedNode {
        if (node.effect == EffectDescriptor.SourceImage) {
            val parameters = node.parameters as? EffectParameters.Source
                ?: throw unsupported(node)
            return PlannedSource(parameters.sourceId)
        }
        val effect = effectRegistry[node.effect.id to node.effect.version]
            ?: throw unsupported(node)
        if (effect.descriptor != node.effect) throw unsupported(node)
        return PlannedEffect(effect, node.parameters)
    }

    private fun unsupported(node: EffectNode): UnsupportedOperationException =
        UnsupportedOperationException(
            "unsupported effect ${node.effect.id} v${node.effect.version} " +
                "with parameters ${node.parameters}"
        )
}
