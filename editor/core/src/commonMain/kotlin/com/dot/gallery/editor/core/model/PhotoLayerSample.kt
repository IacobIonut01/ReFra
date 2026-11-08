package com.dot.gallery.editor.core.model

/**
 * Result of inverse-mapping a base output pixel into a layer: [raw] is
 * the continuous coordinate in the layer's stored raster, while
 * [normalizedX]/[normalizedY] are the same point in normalized
 * pre-EXIF upright coordinates (clamped to `[0, 1]`) for mask lookup.
 */
data class PhotoLayerSample(
    val raw: ContinuousPoint,
    val normalizedX: Double,
    val normalizedY: Double,
)
