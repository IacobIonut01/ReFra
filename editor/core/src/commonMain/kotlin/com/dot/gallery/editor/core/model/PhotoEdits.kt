package com.dot.gallery.editor.core.model

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

/**
 * The editable recipe of a photo document: colour plus geometry plus the
 * image layers composited above the base (ordered bottom to top, at most
 * [MAX_PHOTO_IMAGE_LAYERS], ids unique across the list — duplicated layers
 * may share one immutable source id), plus the spatial stage (details,
 * lens blur), the finishing stage applied after compositing and the retouch
 * stage (colour tone, effects, markup, background removal) on top.
 */
data class PhotoEdits(
    val color: ColorAdjustments = ColorAdjustments(),
    val geometry: PhotoGeometry = PhotoGeometry(),
    val layers: PersistentList<PhotoImageLayer> = persistentListOf(),
    val spatial: PhotoSpatialEffects = PhotoSpatialEffects(),
    val finishing: PhotoFinishing = PhotoFinishing(),
    val retouch: PhotoRetouch = PhotoRetouch(),
    /** How a RAW source is developed; ignored for other sources. */
    val develop: PhotoRawDevelop = PhotoRawDevelop()
) {
    init {
        require(layers.size <= MAX_PHOTO_IMAGE_LAYERS) {
            "a photo recipe holds at most $MAX_PHOTO_IMAGE_LAYERS layers: ${layers.size}"
        }
        require(layers.distinctBy { it.id }.size == layers.size) {
            "layer ids must be unique across the recipe"
        }
    }
}

/** Assembles the full document for [source] without dropping recipe state. */
fun PhotoEdits.toDocument(source: MediaAsset.Image): PhotoDocument =
    PhotoDocument(
        source = source,
        adjustments = color,
        geometry = geometry,
        layers = layers,
        spatial = spatial,
        finishing = finishing,
        retouch = retouch,
        develop = develop
    )
