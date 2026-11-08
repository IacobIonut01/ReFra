package com.dot.gallery.editor.ui.video

import com.dot.gallery.editor.core.time.EditorTime
import kotlin.math.roundToLong

/**
 * The one time<->pixel mapping every part of the timeline shares. The whole
 * sequence occupies [contentWidthPx] regardless of clip count — a 1 s clip
 * in a 4 s timeline is exactly a quarter of the content width. [scrollPx] is
 * the viewport's scroll offset, so hit-testing adds it back before mapping.
 *
 * Pure: construct per frame/gesture, never store.
 */
internal class TimelineGeometry(
    val totalTicks: Long,
    val contentWidthPx: Float,
    val scrollPx: Float
) {
    init {
        require(totalTicks > 0L) { "totalTicks must be positive: $totalTicks" }
        require(contentWidthPx > 0f && contentWidthPx.isFinite()) {
            "contentWidthPx must be positive finite: $contentWidthPx"
        }
    }

    /** Timeline time -> x inside the scrolled content (0 at sequence start). */
    fun timeToX(time: EditorTime): Float =
        (time.ticks.toDouble() / totalTicks * contentWidthPx).toFloat()

    /**
     * Content-space x -> the timeline instant under it, clamped into the
     * sequence. Pointer handlers attached INSIDE the scrolled content see
     * content coordinates already — the scroll offset must not be added a
     * second time.
     */
    fun contentXToTime(contentXPx: Float): EditorTime {
        val fraction = contentXPx / contentWidthPx
        return EditorTime(
            (fraction.toDouble() * totalTicks).roundToLong()
                .coerceIn(0L, totalTicks)
        )
    }

    /**
     * Local (viewport) x -> the timeline instant under it, clamped into the
     * sequence. Inverse of [timeToX] offset by the scroll position — for
     * callers whose coordinates live in the viewport, not the content.
     */
    fun xToTime(localXPx: Float): EditorTime =
        contentXToTime(localXPx + scrollPx)
}
