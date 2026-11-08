package com.dot.gallery.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_tool_blend
import com.dot.gallery.editor.ui.generated.resources.editor_tool_borders
import com.dot.gallery.editor.ui.generated.resources.editor_tool_colour_tone
import com.dot.gallery.editor.ui.generated.resources.editor_tool_cutout
import com.dot.gallery.editor.ui.generated.resources.editor_tool_develop
import com.dot.gallery.editor.ui.generated.resources.editor_tool_edges
import com.dot.gallery.editor.ui.generated.resources.editor_tool_effects
import com.dot.gallery.editor.ui.generated.resources.editor_tool_markup
import com.dot.gallery.editor.ui.generated.resources.editor_tool_posterize
import com.dot.gallery.editor.ui.generated.resources.editor_tool_remove_background
import com.dot.gallery.editor.ui.generated.resources.editor_tool_colour
import com.dot.gallery.editor.ui.generated.resources.editor_tool_crop
import com.dot.gallery.editor.ui.generated.resources.editor_tool_curves
import com.dot.gallery.editor.ui.generated.resources.editor_tool_details
import com.dot.gallery.editor.ui.generated.resources.editor_tool_double_exposure
import com.dot.gallery.editor.ui.generated.resources.editor_tool_grain
import com.dot.gallery.editor.ui.generated.resources.editor_tool_lens_blur
import com.dot.gallery.editor.ui.generated.resources.editor_tool_light
import com.dot.gallery.editor.ui.generated.resources.editor_tool_looks
import com.dot.gallery.editor.ui.generated.resources.editor_tool_vignette
import com.dot.gallery.editor.ui.generated.resources.editor_tool_white_balance
import com.dot.gallery.editor.ui.generated.resources.editor_tool_mask
import com.dot.gallery.editor.ui.generated.resources.editor_tool_perspective
import com.dot.gallery.editor.ui.generated.resources.editor_tool_transform
import com.dot.gallery.editor.ui.generated.resources.editor_tool_transform_layer
import com.dot.gallery.editor.ui.icons.Brush
import com.dot.gallery.editor.ui.icons.ContentCut
import com.dot.gallery.editor.ui.icons.Layers
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.ColorLens
import com.dot.gallery.editor.ui.icons.Crop
import com.dot.gallery.editor.ui.icons.Filter
import com.dot.gallery.editor.ui.icons.Grain
import com.dot.gallery.editor.ui.icons.InvertColors
import com.dot.gallery.editor.ui.icons.LensBlur
import com.dot.gallery.editor.ui.icons.LightMode
import com.dot.gallery.editor.ui.icons.Perspective
import com.dot.gallery.editor.ui.icons.ShowChart
import com.dot.gallery.editor.ui.icons.Style
import com.dot.gallery.editor.ui.icons.Transform
import com.dot.gallery.editor.ui.icons.Tune
import com.dot.gallery.editor.ui.icons.Vignette
import com.dot.gallery.editor.ui.icons.WbSunny
import org.jetbrains.compose.resources.stringResource

/**
 * The photo editor's registered tools — the canonical source of truth
 * behind the Tools catalogue, the wide tool rail and the context panel.
 * Every entry that ships is a working surface: selector + property list,
 * slider row or crop presets — never a stub or a disabled placeholder.
 */
internal fun photoToolEntries(
    host: EditorHostTools = EditorHostTools(),
    rawSource: Boolean = false
): List<EditorToolEntry<PhotoEditorState, PhotoEditorActions>> =
    listOf(
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.PhotoLight,
                title = Res.string.editor_tool_light,
                icon = EditorSymbols.LightMode,
                group = EditorToolGroup.Adjust
            ),
            tag = TAG_CATEGORY_LIGHT,
            presentation = EditorToolPresentation.AdjustmentSheet
        ) { state, actions -> LightToolContent(state, actions) },
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.PhotoColour,
                title = Res.string.editor_tool_colour,
                icon = EditorSymbols.ColorLens,
                group = EditorToolGroup.Adjust
            ),
            tag = TAG_CATEGORY_COLOUR,
            presentation = EditorToolPresentation.AdjustmentSheet
        ) { state, actions -> ColourToolContent(state, actions) },
        // All compact photo tools share the V4 expandable sheet.
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.PhotoCurves,
                title = Res.string.editor_tool_curves,
                icon = EditorSymbols.ShowChart,
                group = EditorToolGroup.Adjust
            ),
            tag = TAG_CATEGORY_CURVES,
            presentation = EditorToolPresentation.AdjustmentSheet
        ) { state, actions -> CurvesToolContent(state, actions) },
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.PhotoWhiteBalance,
                title = Res.string.editor_tool_white_balance,
                icon = EditorSymbols.WbSunny,
                group = EditorToolGroup.Adjust
            ),
            tag = TAG_CATEGORY_WHITE_BALANCE,
            presentation = EditorToolPresentation.AdjustmentSheet
        ) { state, actions -> WhiteBalanceToolContent(state, actions) },
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.PhotoDetail,
                title = Res.string.editor_tool_details,
                icon = EditorSymbols.Tune,
                group = EditorToolGroup.Adjust
            ),
            tag = TAG_CATEGORY_DETAILS,
            presentation = EditorToolPresentation.AdjustmentSheet
        ) { state, actions -> DetailsToolContent(state, actions) },
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.PhotoVignette,
                title = Res.string.editor_tool_vignette,
                icon = EditorSymbols.Vignette,
                group = EditorToolGroup.Adjust
            ),
            tag = TAG_CATEGORY_VIGNETTE,
            presentation = EditorToolPresentation.AdjustmentSheet
        ) { state, actions -> VignetteToolContent(state, actions) },
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.PhotoLensBlur,
                title = Res.string.editor_tool_lens_blur,
                icon = EditorSymbols.LensBlur,
                group = EditorToolGroup.Adjust
            ),
            tag = TAG_CATEGORY_LENS_BLUR,
            presentation = EditorToolPresentation.AdjustmentSheet
        ) { state, actions -> LensBlurToolContent(state, actions) },
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.PhotoGrain,
                title = Res.string.editor_tool_grain,
                icon = EditorSymbols.Grain,
                group = EditorToolGroup.Adjust
            ),
            tag = TAG_CATEGORY_GRAIN,
            presentation = EditorToolPresentation.AdjustmentSheet
        ) { state, actions -> GrainToolContent(state, actions) },
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.PhotoLooks,
                title = Res.string.editor_tool_looks,
                icon = EditorSymbols.Style,
                group = EditorToolGroup.Adjust
            ),
            tag = TAG_CATEGORY_LOOKS,
            presentation = EditorToolPresentation.AdjustmentSheet
        ) { state, actions -> LooksToolContent(state, actions) },
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.PhotoCrop,
                title = Res.string.editor_tool_crop,
                icon = EditorSymbols.Crop,
                group = EditorToolGroup.Geometry
            ),
            tag = TAG_CATEGORY_CROP,
            presentation = EditorToolPresentation.AdjustmentSheet
        ) { state, actions -> CropToolContent(state, actions) },
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.PhotoPerspective,
                title = Res.string.editor_tool_perspective,
                icon = EditorSymbols.Perspective,
                group = EditorToolGroup.Geometry
            ),
            tag = TAG_CATEGORY_PERSPECTIVE,
            presentation = EditorToolPresentation.AdjustmentSheet
            // Merged into Crop & straighten: the V4 catalogue keeps the
            // Perspective cell, which opens that tool on its Vertical tab.
        ) { state, actions -> PerspectiveToolContent(state, actions) },
        // The Compose group — contextual layer tools. Double exposure is
        // always selectable: its no-layer surface is the real Add image
        // action. Transform and Blend need an image layer to edit, so
        // the catalogue/rail paints them disabled without one. The mask
        // brush is contextual-only: registered so dispatch and titles
        // resolve, but reached from the layer row's More menu, never a
        // catalogue cell.
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.PhotoDoubleExposure,
                title = Res.string.editor_tool_double_exposure,
                icon = EditorSymbols.Filter,
                group = EditorToolGroup.Compose
            ),
            tag = TAG_CATEGORY_DOUBLE_EXPOSURE,
            presentation = EditorToolPresentation.AdjustmentSheet
        ) { state, actions -> DoubleExposureToolContent(state, actions) },
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.PhotoTransform,
                title = Res.string.editor_tool_transform,
                icon = EditorSymbols.Transform,
                group = EditorToolGroup.Compose
            ),
            tag = TAG_CATEGORY_TRANSFORM,
            presentation = EditorToolPresentation.AdjustmentSheet,
            available = { it.document?.layers?.isNotEmpty() == true }
        ) { state, actions -> LayerTransformToolContent(state, actions) },
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.PhotoBlend,
                title = Res.string.editor_tool_blend,
                icon = EditorSymbols.InvertColors,
                group = EditorToolGroup.Compose
            ),
            tag = TAG_CATEGORY_BLEND,
            presentation = EditorToolPresentation.AdjustmentSheet,
            available = { it.document?.layers?.isNotEmpty() == true }
        ) { state, actions -> LayerBlendToolContent(state, actions) },
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.PhotoLayerMask,
                title = Res.string.editor_tool_mask,
                icon = EditorSymbols.Brush,
                group = EditorToolGroup.Compose
            ),
            tag = "editor.category.mask",
            presentation = EditorToolPresentation.AdjustmentSheet,
            catalogued = false
        ) { state, actions -> LayerMaskToolContent(state, actions) },
        // The Retouch group: Markup always, the selection tools only where
        // the host carries a segmenter with its models installed.
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.PhotoMarkup,
                title = Res.string.editor_tool_markup,
                icon = RetouchSymbols.Draw,
                group = EditorToolGroup.Retouch
            ),
            tag = TAG_CATEGORY_MARKUP,
            presentation = EditorToolPresentation.AdjustmentSheet
        ) { state, actions -> MarkupToolContent(state, actions) },
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.PhotoCutout,
                title = Res.string.editor_tool_cutout,
                icon = EditorSymbols.ContentCut,
                group = EditorToolGroup.Retouch
            ),
            tag = TAG_CATEGORY_CUTOUT,
            presentation = EditorToolPresentation.AdjustmentSheet,
            catalogued = host.openSegmenter != null
        ) { state, actions -> CutoutToolContent(state, actions, removal = false) },
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.PhotoRemoveBackground,
                title = Res.string.editor_tool_remove_background,
                icon = EditorSymbols.Layers,
                group = EditorToolGroup.Retouch
            ),
            tag = TAG_CATEGORY_REMOVE_BACKGROUND,
            presentation = EditorToolPresentation.AdjustmentSheet,
            catalogued = host.openSegmenter != null
        ) { state, actions -> CutoutToolContent(state, actions, removal = true) },
        // The finish tools from the old editor.
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.PhotoColourTone,
                title = Res.string.editor_tool_colour_tone,
                icon = EditorSymbols.InvertColors,
                group = EditorToolGroup.Adjust
            ),
            tag = TAG_CATEGORY_COLOUR_TONE,
            presentation = EditorToolPresentation.AdjustmentSheet
        ) { state, actions -> ColourToneToolContent(state, actions) },
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.PhotoPosterize,
                title = Res.string.editor_tool_posterize,
                icon = RetouchSymbols.Texture,
                group = EditorToolGroup.Adjust
            ),
            tag = TAG_CATEGORY_POSTERIZE,
            presentation = EditorToolPresentation.AdjustmentSheet
        ) { state, actions -> EffectsToolContent(state, actions, EffectsMode.Posterize) },
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.PhotoEdges,
                title = Res.string.editor_tool_edges,
                icon = RetouchSymbols.GridOn,
                group = EditorToolGroup.Adjust
            ),
            tag = TAG_CATEGORY_EDGES,
            presentation = EditorToolPresentation.AdjustmentSheet
        ) { state, actions -> EffectsToolContent(state, actions, EffectsMode.Edges) },
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.PhotoBorders,
                title = Res.string.editor_tool_borders,
                icon = RetouchSymbols.CropDin,
                group = EditorToolGroup.Adjust
            ),
            tag = TAG_CATEGORY_BORDERS,
            presentation = EditorToolPresentation.AdjustmentSheet
        ) { state, actions -> EffectsToolContent(state, actions, EffectsMode.Borders) },
        // Develop works on the decode itself, so only a RAW source lists it.
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.PhotoDevelop,
                title = Res.string.editor_tool_develop,
                icon = RetouchSymbols.RawOn,
                group = EditorToolGroup.Adjust
            ),
            tag = TAG_CATEGORY_DEVELOP,
            presentation = EditorToolPresentation.AdjustmentSheet,
            catalogued = rawSource
        ) { state, actions -> DevelopToolContent(state, actions) }
    )

/** Tools whose canvas layer is [RetouchCanvasOverlay]. */
internal val RETOUCH_CANVAS_TOOLS = setOf(
    EditorToolIds.PhotoMarkup,
    EditorToolIds.PhotoCutout,
    EditorToolIds.PhotoRemoveBackground
)

/** Tools with the design's Reset row floating under the phone header. */
internal val RETOUCH_RESET_ROW_TOOLS = RETOUCH_CANVAS_TOOLS + setOf(
    EditorToolIds.PhotoColourTone,
    EditorToolIds.PhotoPosterize,
    EditorToolIds.PhotoEdges,
    EditorToolIds.PhotoBorders,
    EditorToolIds.PhotoDevelop
)

/** The canvas layer of the tools that take input on the photo itself. */
@Composable
internal fun RetouchCanvasOverlay(
    layout: EditorImageLayout,
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    toolUiState: PhotoToolUiState,
    tool: EditorToolId
) {
    if (tool == EditorToolIds.PhotoMarkup) {
        MarkupCanvasOverlay(layout, state, actions, toolUiState)
    } else {
        CutoutCanvasOverlay(layout, toolUiState)
    }
}

/**
 * The active tool's controls — the same panel serves the compact context
 * area and the wide inspector, dispatched through the registry. The
 * callbacks handed to the tool are scoped: `beginEdit`/`applyEdits`/
 * `reset` first ensure the tool owns the transaction, so a panel restored
 * without a `selectTool` call still edits inside a tool draft rather than
 * escaping to the document history.
 */
@Composable
internal fun PhotoToolPanel(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    tool: EditorToolId
) {
    val entry = photoToolEntries().firstOrNull { it.descriptor.id == tool }
        ?: photoToolEntries().first()
    val title = stringResource(entry.descriptor.title)
    val scoped = remember(actions, title) {
        actions.copy(
            beginEdit = { label ->
                actions.beginToolEdit(title)
                actions.beginEdit(label)
            },
            applyEdits = { label, edits ->
                actions.beginToolEdit(title)
                actions.applyEdits(label, edits)
            },
            reset = {
                actions.beginToolEdit(title)
                actions.reset()
            }
        )
    }
    entry.content(state, scoped)
}

@Composable
internal fun photoToolTitle(tool: EditorToolId): String {
    val descriptor = photoToolEntries().firstOrNull { it.descriptor.id == tool }
        ?.descriptor
        ?: photoToolEntries().first().descriptor
    // The p-transform context header reads "Transform layer"; the
    // catalogue cell and the layer row's route menu keep the short
    // "Transform" name.
    return stringResource(
        when (tool) {
            EditorToolIds.PhotoTransform -> Res.string.editor_tool_transform_layer
            // The three cells open one Effects tool on their own tab.
            EditorToolIds.PhotoPosterize,
            EditorToolIds.PhotoEdges,
            EditorToolIds.PhotoBorders -> Res.string.editor_tool_effects
            else -> descriptor.title
        }
    )
}
