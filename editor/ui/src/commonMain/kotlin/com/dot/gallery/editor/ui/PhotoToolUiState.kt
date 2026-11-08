package com.dot.gallery.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.dot.gallery.editor.core.geometry.CropFitting
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoHslBand
import com.dot.gallery.editor.core.model.PhotoMaskMode
import com.dot.gallery.editor.core.model.PixelSize

/**
 * UI-only state shared by the Geometry tools — the crop preset selection
 * and the Perspective tool's grid toggle. None of it is part of the
 * recipe: it lives beside the workspace state, is remembered per source,
 * and never enters [PhotoEdits]/export.
 *
 * [cropPreset] is seeded lazily from the document's current crop via
 * [seedPreset] the first time the crop surface composes with a loaded
 * document; afterwards it is user state (preset clicks, resets).
 */
@Stable
internal class PhotoToolUiState {
    var cropPreset by mutableStateOf(CropAspectPreset.Original)
    var presetSeeded by mutableStateOf(false)
    var showGrid by mutableStateOf(true)

    // Crop & straighten: the strip's orientation (true = the reciprocal,
    // portrait form of the preset), the active ruler tab, guided-upright
    // drawing and its guides, and whether a ruler drag is live (the frame
    // switches from thirds to the dense grid while it is).
    var cropPortrait by mutableStateOf(false)
    var cropTab by mutableStateOf(CropTab.Straighten)
    var guidesMode by mutableStateOf(false)
    val guides = mutableStateListOf<CropGuide>()
    var cropAdjusting by mutableStateOf(false)

    // White balance: the neutral-point picker owns canvas input while
    // on; the canvas overlay clears it after a pick, and Back/Escape or
    // the tool closing clear it too. Never saved.
    var whiteBalancePicking by mutableStateOf(false)

    // Colour: the open mode and each mode's selection. Shared across sheet
    // forms so a recomposition keeps the same ruler; never saved.
    var colourMode by mutableStateOf(ColourMode.Basics)
    var colourBasicsParam by mutableStateOf("saturation")
    var colourWhiteBalanceParam by mutableStateOf("temperature")
    var colourHslParam by mutableStateOf("saturation")
    var colourBand by mutableStateOf(PhotoHslBand.Red)
    var colourRegion by mutableStateOf(ColourRegion.Shadows)

    // Curves: the open channel and each channel's selected point index.
    // A selection past the curve's end, after an undo, reads as none.
    var curvesChannel by mutableStateOf("rgb")
    val curvesSelection = mutableStateMapOf<String, Int>()

    // Light: the active parameter, shared by the sheet and the focus
    // chrome so entering or leaving focus keeps the same ruler.
    var lightParamKey by mutableStateOf(LightParameters.first().key)

    // Markup: the open tab, the brush settings stamped on each stroke and
    // the text selection. Tool settings only; the recipe keeps the strokes.
    var markupMode by mutableStateOf(MarkupMode.Pen)
    var markupInk by mutableStateOf(MarkupInkMode.Draw)
    var markupSize by mutableStateOf(30f)
    var markupStrength by mutableStateOf(70f)
    var markupColor by mutableStateOf(com.dot.gallery.editor.core.model.PhotoMarkupStroke.DEFAULT_COLOR)
    var markupEditingStrength by mutableStateOf(false)
    var markupSelectedText by mutableStateOf<Long?>(null)
    var markupTextEditing by mutableStateOf<MarkupTextDraft?>(null)
    var markupFindingFaces by mutableStateOf(false)
    var markupNotice by mutableStateOf<String?>(null)

    // Cutout: the open selection, shared by its panel and canvas overlay.
    var cutout by mutableStateOf<CutoutController?>(null)
    var cutoutPublish: ((com.dot.gallery.editor.core.model.PhotoCutoutMatte?) -> Unit)? = null

    /** The canvas camera the crop window drives; never saved. */
    val cropView = CropViewTransform()

    /** The locked displayed ratio for [geometry], or null for Free. */
    fun cropAspect(geometry: PhotoGeometry, upright: PixelSize): Double? {
        val cp = CropFitting.planeOf(upright, geometry)
        return cropPreset.ratio(cp.width, cp.height, cropPortrait)
    }

    // Mask-brush settings — tool UI state only; the recipe carries the
    // values stamped onto each stroke, not the brush that made them.
    // A fresh full mask starts on Erase: the brush first cuts the hole.
    var brushMode by mutableStateOf(PhotoMaskMode.Erase)
    var brushSizePx by mutableStateOf(32f)
    var brushHardness by mutableStateOf(20f)
    var brushFlow by mutableStateOf(35f)

    /** Seeds the preset from the document once — later calls are no-ops. */
    fun seedPreset(geometry: PhotoGeometry, upright: PixelSize) {
        if (!presetSeeded) {
            val (preset, portrait) = CropAspectPreset.derive(geometry, upright)
            cropPreset = preset
            cropPortrait = portrait
            presetSeeded = true
        }
    }

    companion object {
        val Saver = listSaver<PhotoToolUiState, Any>(
            save = {
                listOf(
                    it.cropPreset.name,
                    it.presetSeeded,
                    it.showGrid,
                    it.brushMode.name,
                    it.brushSizePx,
                    it.brushHardness,
                    it.brushFlow,
                    it.cropPortrait,
                    it.cropTab.name,
                    it.lightParamKey
                )
            },
            restore = { saved ->
                PhotoToolUiState().apply {
                    cropPreset = CropAspectPreset.entries.firstOrNull {
                        it.name == saved[0] as String
                    } ?: CropAspectPreset.Original
                    presetSeeded = saved[1] as Boolean
                    showGrid = saved[2] as Boolean
                    // Added later — a 3-item save restores the defaults.
                    brushMode = (saved.getOrNull(3) as? String)
                        ?.let { name ->
                            PhotoMaskMode.entries.firstOrNull {
                                it.name == name
                            }
                        } ?: PhotoMaskMode.Erase
                    brushSizePx = saved.getOrNull(4) as? Float ?: 32f
                    brushHardness = saved.getOrNull(5) as? Float ?: 20f
                    brushFlow = saved.getOrNull(6) as? Float ?: 35f
                    cropPortrait = saved.getOrNull(7) as? Boolean ?: false
                    cropTab = (saved.getOrNull(8) as? String)?.let { name ->
                        CropTab.entries.firstOrNull { it.name == name }
                    } ?: CropTab.Straighten
                    // Unknown keys restore Exposure.
                    lightParamKey = (saved.getOrNull(9) as? String)
                        ?.takeIf { key -> LightParameters.any { it.key == key } }
                        ?: LightParameters.first().key
                }
            }
        )
    }
}

/**
 * One [PhotoToolUiState] per source — the preset/grid selections reset
 * when the document changes, survive layout flips and recreation.
 */
@Composable
internal fun rememberPhotoToolUiState(
    sourceReference: String?
): PhotoToolUiState = rememberSaveable(
    sourceReference,
    saver = PhotoToolUiState.Saver
) {
    PhotoToolUiState()
}

/**
 * The tool surface's UI state, provided by [PhotoEditorContent] so both
 * the inspector content and the canvas overlay see one instance. Null
 * outside the editor (previews, isolated panel tests) — tool content
 * falls back to a private default.
 */
internal val LocalPhotoToolUiState =
    staticCompositionLocalOf<PhotoToolUiState?> { null }
