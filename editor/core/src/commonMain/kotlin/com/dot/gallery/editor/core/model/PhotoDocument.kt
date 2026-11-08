package com.dot.gallery.editor.core.model

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

/**
 * Non-destructive photo edit: an image source plus its adjustment recipe
 * and the image layers composited above it (ordered bottom to top, at
 * most [MAX_PHOTO_IMAGE_LAYERS], ids unique across the composition.
 * Duplicated layers may share one source id; no layer source id may equal
 * the base source id).
 */
data class PhotoDocument(
    val source: MediaAsset.Image,
    val adjustments: ColorAdjustments = ColorAdjustments(),
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
            "a photo document holds at most $MAX_PHOTO_IMAGE_LAYERS layers: ${layers.size}"
        }
        require(layers.distinctBy { it.id }.size == layers.size) {
            "layer ids must be unique across the composition"
        }
        require(layers.none { it.source.id == source.id }) {
            "layer source ids must differ from the base source id: ${source.id}"
        }
    }
}

/** Extracts the editable recipe — every reconstruction must go through this. */
fun PhotoDocument.toEdits(): PhotoEdits =
    PhotoEdits(
        color = adjustments,
        geometry = geometry,
        layers = layers,
        spatial = spatial,
        finishing = finishing,
        retouch = retouch,
        develop = develop
    )
