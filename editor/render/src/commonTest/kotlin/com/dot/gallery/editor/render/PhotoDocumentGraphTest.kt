package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.graph.EffectDescriptor
import com.dot.gallery.editor.core.graph.EffectParameters
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.PhotoBlendMode
import com.dot.gallery.editor.core.model.PhotoDetails
import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PhotoFinishing
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoImageLayer
import com.dot.gallery.editor.core.model.PhotoLensBlur
import com.dot.gallery.editor.core.model.PhotoSpatialEffects
import com.dot.gallery.editor.core.model.PhotoVignette
import com.dot.gallery.editor.core.model.PhotoLayerTransform
import com.dot.gallery.editor.core.model.PixelSize
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PhotoDocumentGraphTest {

    @Test
    fun `graph is source then colour then geometry rooted at geometry`() {
        val document = PhotoDocument(
            source = MediaAsset.Image("img-1", "content://m/1", PixelSize(8, 6)),
            geometry = PhotoGeometry(quarterTurns = 2)
        )
        val graph = document.renderGraph()
        val ordered = graph.orderedNodes()
        assertEquals("geometry", graph.outputId)
        assertEquals(
            listOf(
                EffectDescriptor.SourceImage,
                EffectDescriptor.ColorBasic,
                EffectDescriptor.GeometryPhoto
            ),
            ordered.map { it.effect }
        )
        assertEquals("img-1", (ordered[0].parameters as EffectParameters.Source).sourceId)
        assertEquals(
            PhotoGeometry(quarterTurns = 2),
            (ordered[2].parameters as EffectParameters.Geometry).geometry
        )
    }

    private fun layer(id: String, visible: Boolean = true) = PhotoImageLayer(
        id = id,
        name = "Layer $id",
        source = MediaAsset.Image("src-$id", "content://l/$id", PixelSize(8, 6)),
        visible = visible
    )

    @Test
    fun `visible layers add source placement and composite nodes bottom to top`() {
        val document = PhotoDocument(
            source = MediaAsset.Image("img-1", "content://m/1", PixelSize(8, 6)),
            layers = persistentListOf(
                layer("a").copy(blendMode = PhotoBlendMode.Multiply),
                layer("b").copy(
                    opacity = .5f,
                    transform = PhotoLayerTransform(scale = 2f)
                ),
                layer("hidden", visible = false)
            )
        )
        val graph = document.renderGraph()
        assertEquals("layer-composite-b", graph.outputId)
        val ordered = graph.orderedNodes()
        assertEquals(
            listOf(
                "source", "color", "geometry",
                "layer-source-a", "layer-place-a", "layer-composite-a",
                "layer-source-b", "layer-place-b", "layer-composite-b"
            ),
            ordered.map { it.id }
        )
        // The invisible layer contributes no nodes at all.
        assertEquals(9, ordered.size)
        assertEquals(
            EffectParameters.Source("src-b"),
            ordered[6].parameters
        )
        assertEquals(
            EffectParameters.Composite(PhotoBlendMode.Multiply, 1f, false),
            ordered[5].parameters
        )
        val composite = ordered[8]
        assertEquals(
            persistentListOf("layer-composite-a", "layer-place-b"),
            composite.inputs
        )
        // The placement canvas defaults to the document-resolution output;
        // with no explicit render size it is also the translation
        // reference, so the scale factor is 1.
        val placement = ordered[7].parameters as EffectParameters.LayerPlacement
        assertEquals(PixelSize(8, 6), placement.outputSize)
        assertEquals(PixelSize(8, 6), placement.referenceOutputSize)
        assertEquals(
            PhotoLayerTransform(scale = 2f),
            placement.layer.transform
        )
    }

    @Test
    fun `a render-resolution canvas flows into layer placement parameters`() {
        val document = PhotoDocument(
            source = MediaAsset.Image("img-1", "content://m/1", PixelSize(8, 6)),
            layers = persistentListOf(layer("a"))
        )
        val graph = document.renderGraph(PixelSize(4, 3))
        val placement = graph.orderedNodes()
            .single { it.id == "layer-place-a" }
            .parameters as EffectParameters.LayerPlacement
        assertEquals(PixelSize(4, 3), placement.outputSize)
        // The translation reference stays the full-resolution output.
        assertEquals(PixelSize(8, 6), placement.referenceOutputSize)
    }

    @Test
    fun `duplicated layers share one source frame through distinct nodes`() {
        // copy(id=...) keeps the same immutable source asset — the model
        // allows it, and the graph still derives per-layer node ids while
        // both source parameters resolve the single loaded frame.
        val shared = layer("a")
        val document = PhotoDocument(
            source = MediaAsset.Image("img-1", "content://m/1", PixelSize(8, 6)),
            layers = persistentListOf(shared, shared.copy(id = "layer-2"))
        )
        val ordered = document.renderGraph().orderedNodes()
        assertEquals(
            listOf(
                "source", "color", "geometry",
                "layer-source-a", "layer-place-a", "layer-composite-a",
                "layer-source-layer-2", "layer-place-layer-2",
                "layer-composite-layer-2"
            ),
            ordered.map { it.id }
        )
        val sourceParams = ordered
            .filter { it.effect == EffectDescriptor.SourceImage }
            .map { (it.parameters as EffectParameters.Source).sourceId }
        assertEquals(listOf("img-1", "src-a", "src-a"), sourceParams)
    }

    @Test
    fun `a non-neutral finishing appends one node after the last composite`() {
        val document = PhotoDocument(
            source = MediaAsset.Image("img-1", "content://m/1", PixelSize(8, 6)),
            layers = persistentListOf(layer("a")),
            finishing = PhotoFinishing(
                vignette = PhotoVignette(amount = -0.5f)
            )
        )
        val graph = document.renderGraph(PixelSize(4, 3))
        val ordered = graph.orderedNodes()
        assertEquals("finishing", graph.outputId)
        val finishing = ordered.last()
        assertEquals(EffectDescriptor.PhotoFinishing, finishing.effect)
        assertEquals(persistentListOf("layer-composite-a"), finishing.inputs)
        val params = finishing.parameters as EffectParameters.Finishing
        assertEquals(0, params.originY)
        // The vignette mask keys on the canvas this render targets.
        assertEquals(PixelSize(4, 3), params.fullSize)
        assertEquals(document.finishing, params.finishing)
    }

    @Test
    fun `a neutral finishing adds no node and keeps the composite output`() {
        val document = PhotoDocument(
            source = MediaAsset.Image("img-1", "content://m/1", PixelSize(8, 6)),
            layers = persistentListOf(layer("a")),
            finishing = PhotoFinishing()
        )
        val graph = document.renderGraph()
        assertEquals("layer-composite-a", graph.outputId)
        kotlin.test.assertTrue(
            graph.orderedNodes().none {
                it.effect == EffectDescriptor.PhotoFinishing
            }
        )
    }

    @Test
    fun `finishing without layers consumes the geometry output`() {
        val document = PhotoDocument(
            source = MediaAsset.Image("img-1", "content://m/1", PixelSize(8, 6)),
            finishing = PhotoFinishing(
                vignette = PhotoVignette(amount = -0.5f)
            )
        )
        val graph = document.renderGraph()
        val finishing = graph.orderedNodes().last()
        assertEquals("finishing", finishing.id)
        assertEquals(persistentListOf("geometry"), finishing.inputs)
    }

    @Test
    fun `spatial lands between the composite and finishing`() {
        val document = PhotoDocument(
            source = MediaAsset.Image("img-1", "content://m/1", PixelSize(8, 6)),
            layers = persistentListOf(layer("a")),
            spatial = PhotoSpatialEffects(
                details = PhotoDetails(sharpening = 0.5f),
                lensBlur = PhotoLensBlur(strength = 0.5f)
            ),
            finishing = PhotoFinishing(
                vignette = PhotoVignette(amount = -0.5f)
            )
        )
        val graph = document.renderGraph(PixelSize(4, 3))
        val ordered = graph.orderedNodes()
        assertEquals("finishing", graph.outputId)
        val spatial = ordered[ordered.size - 2]
        assertEquals(EffectDescriptor.PhotoSpatial, spatial.effect)
        assertEquals(persistentListOf("layer-composite-a"), spatial.inputs)
        val params = spatial.parameters as EffectParameters.Spatial
        assertEquals(0, params.originY)
        assertEquals(PixelSize(4, 3), params.fullSize)
        assertEquals(document.spatial, params.spatial)
        // Finishing consumes the spatial output — never the raw
        // composite.
        assertEquals(
            persistentListOf(spatial.id),
            ordered.last().inputs
        )
    }

    @Test
    fun `a neutral spatial adds no node even with finishing active`() {
        val document = PhotoDocument(
            source = MediaAsset.Image("img-1", "content://m/1", PixelSize(8, 6)),
            spatial = PhotoSpatialEffects(),
            finishing = PhotoFinishing(
                vignette = PhotoVignette(amount = -0.5f)
            )
        )
        val graph = document.renderGraph()
        kotlin.test.assertTrue(
            graph.orderedNodes().none {
                it.effect == EffectDescriptor.PhotoSpatial
            }
        )
        // And the finishing stage falls back to the geometry output.
        val finishing = graph.orderedNodes().last()
        assertEquals("finishing", finishing.id)
        assertEquals(persistentListOf("geometry"), finishing.inputs)
    }

    @Test
    fun `duplicate node ids are rejected by graph validation`() {
        // The model already forbids duplicate layer ids; the graph is the
        // second wall — a hand-built graph with a collision must not plan.
        assertFailsWith<IllegalArgumentException> {
            PhotoDocument(
                source = MediaAsset.Image("img-1", "content://m/1", PixelSize(8, 6)),
                layers = persistentListOf(layer("x"), layer("x", visible = false))
            )
        }
    }
}
