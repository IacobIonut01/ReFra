package com.dot.gallery.editor.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue

/**
 * The canvas's own view state — zoom, pan and the two comparison modes —
 * hoisted into [PhotoEditorContent] so switching between the compact and
 * sidebar composition does not reset the viewport, and saveable so
 * recreation keeps it. One instance per source; the owner keys it on the
 * source reference.
 *
 * [compareBefore] is the full-frame Before view (hold-to-compare).
 * [compareSplit] is the compare toggle: the original drawn left of a
 * draggable divider at [splitFraction] of the canvas width, the edit to
 * its right. The split only lines up pixel for pixel while the recipe has
 * no geometry, so the owner reports [splitAvailable] each frame; while it
 * is false the toggle falls back to the full Before frame ([showsBefore]).
 */
internal class EditorViewportState(
    zoom: Float = 1f,
    panX: Float = 0f,
    panY: Float = 0f,
    compareBefore: Boolean = false,
    compareSplit: Boolean = false,
    splitFraction: Float = 0.5f
) {
    var zoom by mutableFloatStateOf(zoom)
    var panX by mutableFloatStateOf(panX)
    var panY by mutableFloatStateOf(panY)
    var compareBefore by mutableStateOf(compareBefore)
    var compareSplit by mutableStateOf(compareSplit)
    var splitFraction by mutableFloatStateOf(splitFraction)
    // Derived from the recipe by the owner, never saved.
    var splitAvailable by mutableStateOf(true)
    // Transient feedback is deliberately not restored with the viewport.
    var zoomReadoutVisible by mutableStateOf(false)

    /** The whole frame shows the original — a hold, or a split that cannot align. */
    val showsBefore: Boolean
        get() = compareBefore || (compareSplit && !splitAvailable)

    /** The split divider is on screen. A hold overrides it full-frame. */
    val showsSplit: Boolean
        get() = compareSplit && splitAvailable && !compareBefore

    companion object {
        val Saver: Saver<EditorViewportState, Any> = listSaver(
            save = {
                listOf(
                    it.zoom, it.panX, it.panY, it.compareBefore,
                    it.compareSplit, it.splitFraction
                )
            },
            restore = {
                EditorViewportState(
                    zoom = it[0] as Float,
                    panX = it[1] as Float,
                    panY = it[2] as Float,
                    compareBefore = it[3] as Boolean,
                    compareSplit = it[4] as Boolean,
                    splitFraction = it[5] as Float
                )
            }
        )
    }
}
