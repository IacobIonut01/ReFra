package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.graph.EffectDescriptor
import com.dot.gallery.editor.core.graph.EffectNode
import com.dot.gallery.editor.core.graph.EffectParameters
import com.dot.gallery.editor.core.graph.RenderGraph
import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PhotoMapping
import com.dot.gallery.editor.core.model.PixelSize
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

/**
 * The render graph of a photo document: base source image, then
 * `color.basic`, then `geometry.photo`. Every *visible* image layer, in
 * bottom-to-top recipe order, adds one `source.image` node (keyed by
 * `layer.source.id`), one `photo.layer.place` node that puts it on the
 * base output canvas, and one binary `photo.composite` node over the
 * accumulated result. Invisible layers are omitted entirely. The output
 * is the last composite, or `geometry` with no visible layers.
 *
 * Preview frames arrive already upright, so the geometry stage uses
 * orientation 1 and layer placement does the same through each layer's
 * own decoded frame. A non-neutral spatial recipe appends one
 * `photo.spatial` node after the last composite; a non-neutral
 * finishing then appends `photo.finishing` — the output is the last
 * appended node. [outputSize] is the canvas this render targets —
 * the geometry node's output size at the resolution the sources are
 * decoded; null falls back to the document-resolution size.
 */
fun PhotoDocument.renderGraph(outputSize: PixelSize? = null): RenderGraph {
    // Layer translations live in full-resolution base-output pixels; the
    // placement node gets both sizes so it can scale them to this canvas.
    val referenceOutputSize = PhotoMapping(
        source.size, orientation = 1, geometry
    ).outputSize
    val canvas = outputSize ?: referenceOutputSize
    val nodes = ArrayList<EffectNode>(3 + layers.size * 3)
    nodes += EffectNode(
        id = "source",
        effect = EffectDescriptor.SourceImage,
        inputs = persistentListOf(),
        parameters = EffectParameters.Source(source.id)
    )
    nodes += EffectNode(
        id = "color",
        effect = EffectDescriptor.ColorBasic,
        inputs = persistentListOf("source"),
        parameters = EffectParameters.Color(adjustments)
    )
    nodes += EffectNode(
        id = "geometry",
        effect = EffectDescriptor.GeometryPhoto,
        inputs = persistentListOf("color"),
        parameters = EffectParameters.Geometry(geometry)
    )
    var previous = "geometry"
    for (layer in layers) {
        if (!layer.visible) continue
        val sourceId = "layer-source-${layer.id}"
        val placementId = "layer-place-${layer.id}"
        val compositeId = "layer-composite-${layer.id}"
        nodes += EffectNode(
            id = sourceId,
            effect = EffectDescriptor.SourceImage,
            inputs = persistentListOf(),
            parameters = EffectParameters.Source(layer.source.id)
        )
        nodes += EffectNode(
            id = placementId,
            effect = EffectDescriptor.PhotoLayerPlacement,
            inputs = persistentListOf(sourceId),
            parameters = EffectParameters.LayerPlacement(
                layer, canvas, referenceOutputSize
            )
        )
        nodes += EffectNode(
            id = compositeId,
            effect = EffectDescriptor.PhotoComposite,
            inputs = persistentListOf(previous, placementId),
            parameters = EffectParameters.Composite(
                layer.blendMode, layer.opacity, layer.clipToBelow
            )
        )
        previous = compositeId
    }
    // The spatial stage runs over the composited output ahead of
    // finishing — its focus mask uses absolute output coordinates, and
    // a neutral recipe costs no node.
    if (!spatial.isNeutral) {
        nodes += EffectNode(
            id = "spatial",
            effect = EffectDescriptor.PhotoSpatial,
            inputs = persistentListOf(previous),
            parameters = EffectParameters.Spatial(
                spatial, originY = 0, fullSize = canvas
            )
        )
        previous = "spatial"
    }
    // The finishing stage runs once over the composited output —
    // absolute coordinates over the full frame, appended only when the
    // recipe needs it so a neutral finishing costs no node.
    if (!finishing.isNeutral) {
        nodes += EffectNode(
            id = "finishing",
            effect = EffectDescriptor.PhotoFinishing,
            inputs = persistentListOf(previous),
            parameters = EffectParameters.Finishing(
                finishing, originY = 0, fullSize = canvas
            )
        )
        previous = "finishing"
    }
    // Retouch paints over the finished picture, again only when needed.
    if (!retouch.isNeutral) {
        nodes += EffectNode(
            id = "retouch",
            effect = EffectDescriptor.PhotoRetouch,
            inputs = persistentListOf(previous),
            parameters = EffectParameters.Retouch(
                retouch, originY = 0, fullSize = canvas
            )
        )
        previous = "retouch"
    }
    return RenderGraph(nodes = nodes.toPersistentList(), outputId = previous)
}
