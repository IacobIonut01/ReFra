package com.dot.gallery.editor.core.model

import kotlinx.collections.immutable.PersistentList

/**
 * One normalized brush stroke replayed into a [PhotoLayerMask]. [points]
 * is a polyline in normalized layer coordinates — a single point is a
 * dot. [diameter] is normalized against the layer source's shorter edge,
 * [hardness] is the fraction of the radius at full coverage, [flow] the
 * per-stroke opacity applied during replay.
 */
data class PhotoMaskStroke(
    val points: PersistentList<NormalizedPoint>,
    val diameter: Float,
    val hardness: Float = 1f,
    val flow: Float = 1f,
    val mode: PhotoMaskMode = PhotoMaskMode.Paint,
) {
    init {
        require(points.isNotEmpty()) { "a mask stroke needs at least one point" }
        require(points.size <= MAX_POINTS) {
            "a mask stroke holds at most $MAX_POINTS points: ${points.size}"
        }
        require(diameter.isFinite() && diameter in 0.001f..1f) {
            "diameter must be in [0.001, 1]: $diameter"
        }
        require(hardness.isFinite() && hardness in 0f..1f) {
            "hardness must be in [0, 1]: $hardness"
        }
        require(flow.isFinite() && flow in 0f..1f) {
            "flow must be in [0, 1]: $flow"
        }
    }

    private companion object {
        const val MAX_POINTS = 256
    }
}
