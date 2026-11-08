package com.dot.gallery.editor.core.graph

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList

/**
 * An immutable directed acyclic graph of effects. [nodes] is declared in no
 * particular order; [outputId] names the node whose result is the frame.
 *
 * [orderedNodes] plans execution: it validates the whole graph (unique node
 * ids, resolvable inputs, declared arity, acyclicity including disconnected
 * cycles, known output) and returns the output's ancestor set in dependency
 * order, stable by declared input order. Disconnected subtrees that are
 * themselves valid but unused by the output are not evaluated. Validation
 * failures throw [IllegalArgumentException] with the offending identity.
 *
 * Both traversals are iterative — deep graphs cannot overflow the call stack.
 */
data class RenderGraph(
    val nodes: PersistentList<EffectNode>,
    val outputId: String
) {
    init {
        require(outputId.isNotBlank()) { "output id must be non-blank" }
    }

    private class Frame(val id: String, var nextInput: Int)

    fun orderedNodes(): PersistentList<EffectNode> {
        val byId = validate()
        val emitted = HashSet<String>()
        val ordered = ArrayList<EffectNode>(nodes.size)
        val stack = ArrayDeque<Frame>()
        stack.addLast(Frame(outputId, 0))
        while (stack.isNotEmpty()) {
            val frame = stack.last()
            if (frame.id in emitted) {
                stack.removeLast()
                continue
            }
            val node = byId.getValue(frame.id)
            if (frame.nextInput < node.inputs.size) {
                stack.addLast(Frame(node.inputs[frame.nextInput], 0))
                frame.nextInput += 1
            } else {
                emitted += frame.id
                ordered += node
                stack.removeLast()
            }
        }
        return ordered.toPersistentList()
    }

    private fun validate(): Map<String, EffectNode> {
        val byId = LinkedHashMap<String, EffectNode>(nodes.size)
        for (node in nodes) {
            require(byId.put(node.id, node) == null) { "duplicate node id: ${node.id}" }
            require(node.inputs.size == node.effect.inputCount) {
                "node ${node.id} declares ${node.inputs.size} inputs but " +
                    "${node.effect.id} v${node.effect.version} takes ${node.effect.inputCount}"
            }
        }
        require(outputId in byId) { "output id does not name a node: $outputId" }
        for (node in nodes) {
            for (input in node.inputs) {
                require(input in byId) { "node ${node.id} has unknown input: $input" }
            }
        }
        rejectCycles(byId)
        return byId
    }

    private fun rejectCycles(byId: Map<String, EffectNode>) {
        val state = HashMap<String, Int>(nodes.size) // 1 = visiting, 2 = done
        for (start in nodes) {
            if (state.containsKey(start.id)) continue
            val stack = ArrayDeque<Frame>()
            stack.addLast(Frame(start.id, 0))
            state[start.id] = 1
            while (stack.isNotEmpty()) {
                val frame = stack.last()
                val node = byId.getValue(frame.id)
                if (frame.nextInput < node.inputs.size) {
                    val input = node.inputs[frame.nextInput]
                    frame.nextInput += 1
                    when (state[input]) {
                        1 -> throw IllegalArgumentException(
                            "graph contains a cycle at node: $input"
                        )
                        2 -> {}
                        else -> {
                            state[input] = 1
                            stack.addLast(Frame(input, 0))
                        }
                    }
                } else {
                    state[frame.id] = 2
                    stack.removeLast()
                }
            }
        }
    }
}
