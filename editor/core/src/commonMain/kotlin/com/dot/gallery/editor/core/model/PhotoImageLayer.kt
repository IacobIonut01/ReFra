package com.dot.gallery.editor.core.model

/**
 * One image layer stacked above the base. Layers are immutable recipe
 * entries ordered bottom to top: [transform] places the source on the
 * base output canvas, [blendMode]/[opacity]/[clipToBelow] drive the
 * composite, [mask] modulates alpha, and [visible]/[locked] are
 * organisational flags. [source] carries a composition-local id so the
 * same host reference can appear under several layers.
 */
data class PhotoImageLayer(
    val id: String,
    val name: String,
    val source: MediaAsset.Image,
    val transform: PhotoLayerTransform = PhotoLayerTransform(),
    val blendMode: PhotoBlendMode = PhotoBlendMode.Normal,
    val opacity: Float = 1f,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val clipToBelow: Boolean = false,
    val mask: PhotoLayerMask = PhotoLayerMask(),
) {
    init {
        require(id.isNotBlank()) { "layer id must be non-blank" }
        require(name.isNotBlank()) { "layer name must be non-blank" }
        require(id != source.id) { "layer id must differ from its source id: $id" }
        require(opacity.isFinite() && opacity in 0f..1f) {
            "opacity must be in [0, 1]: $opacity"
        }
    }
}
