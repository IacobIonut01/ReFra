package com.dot.gallery.editor.core.graph

import com.dot.gallery.editor.core.model.ColorAdjustments
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList

class RenderGraphTest {

    // Node ids are independent from the ids given to Source parameters.
    private fun source(id: String) = EffectNode(
        id = id,
        effect = EffectDescriptor.SourceImage,
        inputs = persistentListOf(),
        parameters = EffectParameters.Source("asset://$id")
    )

    private fun color(id: String, input: String) = EffectNode(
        id = id,
        effect = EffectDescriptor.ColorBasic,
        inputs = persistentListOf(input),
        parameters = EffectParameters.Color(ColorAdjustments())
    )

    private fun extension(id: String, input: String, inputCount: Int = 1) = EffectNode(
        id = id,
        effect = EffectDescriptor("ext.custom", 3, inputCount),
        inputs = persistentListOf(input),
        parameters = EffectParameters.Extension(persistentMapOf("strength" to "5"))
    )

    @Test
    fun `linear chain orders dependencies first`() {
        val graph = RenderGraph(
            nodes = persistentListOf(color("c", "s"), source("s")),
            outputId = "c"
        )
        assertEquals(listOf("s", "c"), graph.orderedNodes().map { it.id })
    }

    @Test
    fun `branched graph is stable in declared input order`() {
        val merge = EffectNode(
            id = "m",
            effect = EffectDescriptor("ext.merge", 1, 2),
            inputs = persistentListOf("a", "b"),
            parameters = EffectParameters.Extension(persistentMapOf())
        )
        val graph = RenderGraph(
            nodes = persistentListOf(merge, color("b", "s2"), color("a", "s1"), source("s2"), source("s1")),
            outputId = "m"
        )
        assertEquals(listOf("s1", "a", "s2", "b", "m"), graph.orderedNodes().map { it.id })
    }

    @Test
    fun `shared input is emitted once`() {
        val merge = EffectNode(
            id = "m",
            effect = EffectDescriptor("ext.merge", 1, 2),
            inputs = persistentListOf("a", "b"),
            parameters = EffectParameters.Extension(persistentMapOf())
        )
        val graph = RenderGraph(
            nodes = persistentListOf(merge, color("a", "s"), color("b", "s"), source("s")),
            outputId = "m"
        )
        assertEquals(listOf("s", "a", "b", "m"), graph.orderedNodes().map { it.id })
    }

    @Test
    fun `valid but disconnected nodes are not evaluated`() {
        val graph = RenderGraph(
            nodes = persistentListOf(color("c", "s"), source("s"), source("unused")),
            outputId = "c"
        )
        assertEquals(listOf("s", "c"), graph.orderedNodes().map { it.id })
    }

    @Test
    fun `duplicate node ids are rejected`() {
        val graph = RenderGraph(
            nodes = persistentListOf(source("s"), source("s")),
            outputId = "s"
        )
        assertFailsWith<IllegalArgumentException> { graph.orderedNodes() }
    }

    @Test
    fun `missing input is rejected`() {
        val graph = RenderGraph(
            nodes = persistentListOf(color("c", "ghost")),
            outputId = "c"
        )
        assertFailsWith<IllegalArgumentException> { graph.orderedNodes() }
    }

    @Test
    fun `cycles are rejected including disconnected ones`() {
        val a = EffectNode("a", EffectDescriptor("ext.x", 1, 1), persistentListOf("b"), EffectParameters.Extension(persistentMapOf()))
        val b = EffectNode("b", EffectDescriptor("ext.x", 1, 1), persistentListOf("a"), EffectParameters.Extension(persistentMapOf()))
        val reachable = RenderGraph(persistentListOf(a, b), "a")
        assertFailsWith<IllegalArgumentException> { reachable.orderedNodes() }
        val disconnected = RenderGraph(
            persistentListOf(a, b, source("s")),
            "s"
        )
        assertFailsWith<IllegalArgumentException> { disconnected.orderedNodes() }
    }

    @Test
    fun `input arity is checked`() {
        val wrong = EffectNode(
            id = "c",
            effect = EffectDescriptor.ColorBasic,
            inputs = persistentListOf("s", "extra"),
            parameters = EffectParameters.Color(ColorAdjustments())
        )
        val graph = RenderGraph(persistentListOf(source("s"), source("extra"), wrong), "c")
        assertFailsWith<IllegalArgumentException> { graph.orderedNodes() }
    }

    @Test
    fun `unknown output is rejected`() {
        val graph = RenderGraph(persistentListOf(source("s")), "nope")
        assertFailsWith<IllegalArgumentException> { graph.orderedNodes() }
    }

    @Test
    fun `blank output id is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            RenderGraph(persistentListOf(source("s")), " ")
        }
    }

    @Test
    fun `deep chains do not overflow the stack`() {
        val depth = 10_000
        val nodes = ArrayList<EffectNode>(depth)
        nodes += source("n0")
        for (i in 1 until depth) {
            nodes += color("n$i", "n${i - 1}")
        }
        val graph = RenderGraph(nodes.toPersistentList(), "n${depth - 1}")
        val ordered = graph.orderedNodes()
        assertEquals(depth, ordered.size)
        assertEquals("n0", ordered.first().id)
        assertEquals("n${depth - 1}", ordered.last().id)
        for (i in 1 until depth) {
            assertEquals("n$i", ordered[i].id)
        }
    }

    @Test
    fun `deep cycles are rejected without stack overflow`() {
        val depth = 5_000
        val nodes = ArrayList<EffectNode>(depth + 1)
        // Deep self-contained cycle: c0 <- c1 <- ... <- cN-1 <- c0
        for (i in 0 until depth) {
            val input = "c${(i + 1) % depth}"
            nodes += EffectNode(
                id = "c$i",
                effect = EffectDescriptor("ext.x", 1, 1),
                inputs = persistentListOf(input),
                parameters = EffectParameters.Extension(persistentMapOf())
            )
        }
        nodes += source("out")
        val graph = RenderGraph(nodes.toPersistentList(), "out")
        // The cycle is disconnected from the output; whole-graph validation
        // must still find it.
        assertFailsWith<IllegalArgumentException> { graph.orderedNodes() }
    }
}
