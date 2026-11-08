package com.dot.gallery.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.dot.gallery.editor.core.geometry.CropFitting
import com.dot.gallery.editor.core.geometry.GuidedUpright
import com.dot.gallery.editor.core.geometry.InscribedCrop
import com.dot.gallery.editor.core.geometry.UprightGuide
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoWarp
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.photo.PhotoEditorState

/**
 * Every Crop & straighten edit, shared by the inspector, the canvas frame
 * and the keyboard. Each operation produces a geometry that keeps the
 * crop inside the warped photo ([CropFitting]) and lands as exactly one
 * history step: discrete actions through `applyEdits`, ruler/wheel drags
 * through one `beginEdit`/`updateEdits`/`commitEdit` interaction whose
 * updates are all computed from the geometry at the gesture's start (so a
 * straighten that returns to 0 regrows the crop it shrank).
 */
@Stable
internal class CropToolOps(
    private val stateOf: () -> PhotoEditorState,
    private val actionsOf: () -> PhotoEditorActions,
    val ui: PhotoToolUiState
) {
    val state: PhotoEditorState get() = stateOf()
    private val actions: PhotoEditorActions get() = actionsOf()
    val upright: PixelSize? get() = state.info?.uprightSize
    val geometry: PhotoGeometry get() = state.document?.geometry ?: PhotoGeometry()

    private var dragStart: PhotoGeometry? = null

    val canEdit: Boolean
        get() = state.document != null && !state.opening && upright != null &&
            !state.interactionActive

    fun aspectFor(g: PhotoGeometry): Double? = upright?.let { ui.cropAspect(g, it) }

    /** One discrete edit; identical results record nothing. */
    fun apply(label: String, transform: (PhotoGeometry, PixelSize) -> PhotoGeometry): Boolean {
        val doc = state.document ?: return false
        val up = upright ?: return false
        if (state.interactionActive) return false
        val next = transform(doc.geometry, up)
        if (next == doc.geometry) return false
        actions.applyEdits(label, doc.toEdits().copy(geometry = next))
        return true
    }

    fun valueOf(tab: CropTab, warp: PhotoWarp = geometry.warp): Float = when (tab) {
        CropTab.Straighten -> warp.rotationDegrees
        CropTab.Vertical -> warp.verticalPerspective
        CropTab.Horizontal -> warp.horizontalPerspective
    }

    fun withValue(tab: CropTab, warp: PhotoWarp, v: Float): PhotoWarp = when (tab) {
        CropTab.Straighten -> warp.copy(rotationDegrees = v.coerceIn(-45f, 45f))
        CropTab.Vertical -> warp.copy(verticalPerspective = v.coerceIn(-100f, 100f))
        CropTab.Horizontal -> warp.copy(horizontalPerspective = v.coerceIn(-100f, 100f))
    }

    // Ruler / wheel / numeric interactions -----------------------------

    /** Opens the interaction; throws like `beginEdit` when another draft is live. */
    fun beginValue(label: String) {
        val doc = state.document ?: return
        actions.beginEdit(label)
        dragStart = doc.geometry
        ui.cropAdjusting = true
    }

    fun updateValue(tab: CropTab, v: Float) {
        val doc = state.document ?: return
        val up = upright ?: return
        val start = dragStart ?: doc.geometry
        val next = CropFitting.withWarp(up, start, withValue(tab, start.warp, v), aspectFor(start))
        actions.updateEdits(doc.toEdits().copy(geometry = next))
    }

    fun endValue(commit: Boolean) {
        dragStart = null
        ui.cropAdjusting = false
        if (commit) actions.commitEdit() else actions.cancelEdit()
    }

    /** Drops the bookkeeping when the draft ended elsewhere. */
    fun interactionEnded() {
        dragStart = null
        ui.cropAdjusting = false
    }

    fun stepValue(label: String, tab: CropTab, v: Float) = apply(label) { g, up ->
        CropFitting.withWarp(up, g, withValue(tab, g.warp, v), aspectFor(g))
    }

    // Aspect strip ----------------------------------------------------

    fun selectPreset(preset: CropAspectPreset) {
        ui.cropPreset = preset
        if (preset == CropAspectPreset.Free) return
        apply("Crop aspect") { g, up ->
            val ratio = ui.cropAspect(g, up) ?: return@apply g
            CropFitting.withAspect(up, g, ratio)
        }
    }

    fun swapOrientation() {
        ui.cropPortrait = !ui.cropPortrait
        apply("Swap crop orientation") { g, up ->
            val ratio = ui.cropAspect(g, up)
                ?: (1.0 / CropFitting.planeRect(up, g).aspect)
            CropFitting.withAspect(up, g, ratio)
        }
    }

    // Rotate / flip / reset --------------------------------------------

    fun rotateLeft() {
        if (apply("Rotate left") { g, up ->
                CropFitting.constrained(up, CropFitting.rotatedLeft(g), null)
            }
        ) {
            // The visible result turned: the strip's ratio follows it.
            if (!ui.cropPreset.isSquare) ui.cropPortrait = !ui.cropPortrait
            ui.guides.clear()
        }
    }

    fun flipHorizontal() {
        if (apply("Flip horizontal") { g, _ -> CropFitting.flippedHorizontally(g) }) ui.guides.clear()
    }

    fun flipVertical() {
        if (apply("Flip vertical") { g, _ -> CropFitting.flippedVertically(g) }) ui.guides.clear()
    }

    val canReset: Boolean get() = geometry != PhotoGeometry()

    fun reset() {
        val up = upright
        if (apply("Reset crop") { _, _ -> PhotoGeometry() }) {
            ui.cropPreset = CropAspectPreset.Original
            if (up != null) {
                ui.cropPortrait = CropAspectPreset.portraitOf(up.width.toDouble(), up.height.toDouble())
            }
            ui.guides.clear()
        }
    }

    // Frame nudges (keyboard) ------------------------------------------

    /** Moves the crop by plane pixels, stopping at the photo's edge. */
    fun nudge(dx: Double, dy: Double) = apply("Move crop") { g, up ->
        val from = CropFitting.planeRect(up, g)
        val to = from.translate(dx, dy)
        val rect = CropFitting.clampedRect(up, g, from, to)
        g.copy(crop = CropFitting.planeOf(up, g).cropOf(rect, CropFitting.minSide(up)))
    }

    // Guided upright -----------------------------------------------------

    fun solveGuides(): Boolean {
        val up = upright ?: return false
        val cp = CropFitting.planeOf(up, geometry)
        val guides = ui.guides.map { UprightGuide(it.a, it.b, it.axis) }
        val warp = GuidedUpright.solve(cp.width, cp.height, guides, geometry.warp) ?: return false
        return apply("Guided upright") { g, u ->
            CropFitting.maximized(u, g.copy(warp = warp), aspectFor(g))
        }
    }

    /** True when the crop leaves no blank edge. */
    fun cropIsLegal(): Boolean {
        val up = upright ?: return true
        return InscribedCrop.contains(
            CropFitting.planeOf(up, geometry).plane(geometry.warp).validRegion(),
            CropFitting.planeRect(up, geometry), 0.5
        )
    }
}

@Composable
internal fun rememberCropToolOps(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    ui: PhotoToolUiState
): CropToolOps {
    val latestState by rememberUpdatedState(state)
    val latestActions by rememberUpdatedState(actions)
    return remember(ui) { CropToolOps({ latestState }, { latestActions }, ui) }
}
