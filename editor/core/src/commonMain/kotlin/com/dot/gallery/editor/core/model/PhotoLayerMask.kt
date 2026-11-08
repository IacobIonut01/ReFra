package com.dot.gallery.editor.core.model

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

/**
 * A layer's alpha mask: starts at [baseAlpha], replays [strokes] in order,
 * then applies [inverted] last. Stroke and point counts are capped so CPU,
 * GPU and export bounds stay explicit.
 */
data class PhotoLayerMask(
    val baseAlpha: Float = 1f,
    val strokes: PersistentList<PhotoMaskStroke> = persistentListOf(),
    val inverted: Boolean = false,
) {
    init {
        require(baseAlpha.isFinite() && baseAlpha in 0f..1f) {
            "baseAlpha must be in [0, 1]: $baseAlpha"
        }
        require(strokes.size <= MAX_PHOTO_MASK_STROKES) {
            "a layer mask holds at most $MAX_PHOTO_MASK_STROKES strokes: ${strokes.size}"
        }
        val points = strokes.sumOf { it.points.size }
        require(points <= MAX_PHOTO_MASK_POINTS) {
            "a layer mask holds at most $MAX_PHOTO_MASK_POINTS points: $points"
        }
    }
}
