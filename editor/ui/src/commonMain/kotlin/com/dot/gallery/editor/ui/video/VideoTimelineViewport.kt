package com.dot.gallery.editor.ui.video

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.dot.gallery.editor.core.time.EditorTime
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.first

/**
 * The timeline's own view state — zoom (1 = fit the whole sequence into the
 * viewport) plus horizontal scroll — hoisted into the content composable so
 * a layout flip or recreation keeps where the user was looking. The ruler,
 * clip lane and playhead all share [scrollState], so they can never drift
 * onto different time origins.
 */
internal class VideoTimelineViewport(
    zoom: Float = 1f,
    scrollState: ScrollState = ScrollState(0)
) {
    var zoom by mutableFloatStateOf(zoom.coerceIn(MIN_ZOOM, MAX_ZOOM))
        private set
    val scrollState: ScrollState = scrollState

    /**
     * The measured viewport width, written by the timeline layout — the
     * zoom commands need it to anchor the playhead. Zero until measured.
     */
    internal var lastViewportWidthPx = 0f

    /**
     * Zooms to [next] while keeping [anchorTime] at the same viewport x —
     * the playhead stays where it was visually instead of jumping. When the
     * anchor is off-screen it recentres; Fit resets to the start.
     */
    suspend fun zoomTo(
        next: Float,
        anchorTime: EditorTime,
        totalTicks: Long,
        viewportWidthPx: Float
    ) {
        val clamped = next.coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (totalTicks <= 0L || viewportWidthPx <= 0f) {
            zoom = clamped
            return
        }
        val fraction = anchorTime.ticks.toDouble() / totalTicks
        // Where the anchor sits in the viewport right now is judged on the
        // OLD scale — the anchor's current content x minus current scroll.
        // If it is off-screen the midpoint takes over instead.
        val oldAnchorX = (fraction * viewportWidthPx * zoom).toFloat()
        val chosenViewX = (oldAnchorX - scrollState.value)
            .takeIf { it in 0f..viewportWidthPx }
            ?: viewportWidthPx / 2f
        zoom = clamped
        val newContentWidth = viewportWidthPx * clamped
        val newAnchorX = (fraction * newContentWidth).toFloat()
        val target = (newAnchorX - chosenViewX)
            .coerceIn(0f, maxOf(0f, newContentWidth - viewportWidthPx))
        // scrollState.maxValue only reflects the wider content after the
        // relayout — scrollTo would clamp against the stale bound, so on
        // growth wait for the new bound first. Cancellation by the owning
        // scope handles disposal.
        val expectedMax = newContentWidth - viewportWidthPx
        if (expectedMax > scrollState.maxValue) {
            snapshotFlow { scrollState.maxValue }
                .first { it >= expectedMax.roundToInt() - 1 }
        }
        scrollState.scrollTo(target.roundToLong().toInt())
    }

    /** Fit: zoom back to 1 and return to the start of the sequence. */
    suspend fun fit() {
        zoom = 1f
        scrollState.scrollTo(0)
    }

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 8f

        val Saver: Saver<VideoTimelineViewport, Any> = listSaver(
            save = { listOf(it.zoom, it.scrollState.value) },
            restore = {
                VideoTimelineViewport(
                    zoom = it[0] as Float,
                    scrollState = ScrollState(it[1] as Int)
                )
            }
        )
    }
}
