package com.dot.gallery.editor.ui

import androidx.compose.material.icons.outlined.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationEventHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoLayerMask
import com.dot.gallery.editor.core.model.PhotoMapping
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.render.rasterizePhotoMask
import com.dot.gallery.editor.render.photo.PhotoErrorKind
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.render.photo.PhotoExportResult
import com.dot.gallery.editor.ui.generated.resources.Res
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.LocalHazePerformanceMode
import com.dot.gallery.editor.ui.generated.resources.editor_canvas_image
import com.dot.gallery.editor.ui.generated.resources.editor_canvas_image_before
import com.dot.gallery.editor.ui.generated.resources.editor_cancel_export
import com.dot.gallery.editor.ui.generated.resources.editor_cancel_export_confirm
import com.dot.gallery.editor.ui.generated.resources.editor_crop_reset_short
import com.dot.gallery.editor.ui.generated.resources.editor_cancel_export_title
import com.dot.gallery.editor.ui.generated.resources.editor_discard_confirm
import com.dot.gallery.editor.ui.generated.resources.editor_discard_title
import com.dot.gallery.editor.ui.generated.resources.editor_dismiss
import com.dot.gallery.editor.ui.generated.resources.editor_layer_add_image
import com.dot.gallery.editor.ui.generated.resources.editor_layers
import com.dot.gallery.editor.ui.generated.resources.editor_error_export
import com.dot.gallery.editor.ui.generated.resources.editor_error_open
import com.dot.gallery.editor.ui.generated.resources.editor_error_preview
import com.dot.gallery.editor.ui.generated.resources.editor_error_too_large
import com.dot.gallery.editor.ui.generated.resources.editor_error_unsupported
import com.dot.gallery.editor.ui.generated.resources.editor_export_cancelling
import com.dot.gallery.editor.ui.generated.resources.editor_exported
import com.dot.gallery.editor.ui.generated.resources.editor_exporting
import com.dot.gallery.editor.ui.generated.resources.editor_keep_editing
import com.dot.gallery.editor.ui.generated.resources.editor_open_export
import com.dot.gallery.editor.ui.generated.resources.editor_adjustments
import com.dot.gallery.editor.ui.generated.resources.editor_all_tools
import com.dot.gallery.editor.ui.generated.resources.editor_apply
import com.dot.gallery.editor.ui.generated.resources.editor_apply_crop
import com.dot.gallery.editor.ui.generated.resources.editor_back
import com.dot.gallery.editor.ui.generated.resources.editor_done
import com.dot.gallery.editor.ui.generated.resources.editor_panel_edits
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_open_in
import com.dot.gallery.editor.ui.generated.resources.editor_settings
import com.dot.gallery.editor.ui.generated.resources.editor_panel_source
import com.dot.gallery.editor.ui.generated.resources.editor_retry
import com.dot.gallery.editor.ui.generated.resources.editor_save_copy
import com.dot.gallery.editor.ui.generated.resources.editor_selected_layer
import com.dot.gallery.editor.ui.generated.resources.editor_tools
import com.dot.gallery.editor.ui.generated.resources.editor_zoom_fit
import com.dot.gallery.editor.ui.generated.resources.editor_output_info
import com.dot.gallery.editor.ui.generated.resources.editor_reset_edits
import com.dot.gallery.editor.ui.generated.resources.editor_source_info
import com.dot.gallery.editor.ui.generated.resources.editor_tool_colour
import com.dot.gallery.editor.ui.generated.resources.editor_tool_detail
import com.dot.gallery.editor.ui.generated.resources.editor_tool_light
import com.dot.gallery.editor.ui.icons.AddPhotoAlternate
import com.dot.gallery.editor.ui.icons.ArrowBack
import com.dot.gallery.editor.ui.icons.Check
import com.dot.gallery.editor.ui.icons.Info
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.Layers
import com.dot.gallery.editor.ui.icons.RestartAlt
import com.dot.gallery.editor.ui.icons.ViewList
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

internal const val TAG_ERROR = "editor.error"
internal const val TAG_ERROR_RETRY = "editor.error.retry"
internal const val TAG_ERROR_DISMISS = "editor.error.dismiss"
internal const val TAG_EXPORT_CANCEL = "editor.exportCancel"
internal const val TAG_EXPORT_OPEN = "editor.exportOpen"
internal const val TAG_DISCARD_CONFIRM = "editor.discard.confirm"
internal const val TAG_DISCARD_KEEP = "editor.discard.keep"
internal const val TAG_EXPORT_DIALOG_CANCEL = "editor.exportDialog.cancel"
internal const val TAG_EXPORT_DIALOG_KEEP = "editor.exportDialog.keep"
internal const val TAG_PANEL_DONE = "editor.panel.done"
internal const val TAG_CATALOGUE_OPEN_IN = "editor.catalogue.openIn"
/** The Light sub-tool reset in the crop reset's slot under the header. */
internal const val TAG_RESET_TOOL = "editor.reset.tool"

/** Tools that act on the selected image layer — selection auto-fixes
 *  to the topmost layer when the current one is invalid. */
internal val CONTEXTUAL_PHOTO_TOOLS = setOf(
    EditorToolIds.PhotoDoubleExposure,
    EditorToolIds.PhotoTransform,
    EditorToolIds.PhotoBlend,
    EditorToolIds.PhotoLayerMask
)

/** Tools whose open transaction owns the canvas geometry — the split
 *  compare steps aside for them (their frame is not the source frame). */
private val SPLIT_BLOCKING_TOOLS = CONTEXTUAL_PHOTO_TOOLS + RETOUCH_CANVAS_TOOLS + setOf(
    EditorToolIds.PhotoCrop,
    EditorToolIds.PhotoPerspective
)

/**
 * Atomic (source reference, decoded bitmap) pair — the value records which
 * reference its pixels came from so a caller can refuse stale frames the
 * instant the source switches, before the producer has re-run.
 */
private class DecodedFrame(val reference: String?, val bitmap: ImageBitmap?)

/**
 * The decoded composite-shader inputs for one reference, stamped with the
 * (layer id, source id, mask) triples they were produced for so a stale
 * decode — layer stack or mask already moved on — is refused at read
 * time. [sources] null means a visible layer's preview hasn't decoded
 * yet: no usable stack, keep the CPU pipeline.
 */
private class DecodedComposite(
    val reference: String?,
    val keys: List<Triple<String, String, PhotoLayerMask>>,
    val sources: List<PhotoShaderLayerSource>?
)

/**
 * Builds the ordered bottom-to-top [PhotoShaderLayerSource] list off the
 * UI thread. Duplicate layers share one decoded bitmap under their own
 * ids; each layer's mask is rasterized at its preview's size — the same
 * upright pixel space the shader's mask texture addresses. Returns null
 * sources the moment a visible layer has no decoded preview.
 */
private fun decodeCompositeSources(
    reference: String?,
    keys: List<Triple<String, String, PhotoLayerMask>>,
    previews: Map<String, SrgbPixels>
): DecodedComposite {
    if (keys.isEmpty()) return DecodedComposite(reference, keys, emptyList())
    val bitmaps = HashMap<SrgbPixels, ImageBitmap>()
    val sources = ArrayList<PhotoShaderLayerSource>(keys.size)
    for ((layerId, sourceId, mask) in keys) {
        val pixels = previews[layerId]
            ?: return DecodedComposite(reference, keys, null)
        val bitmap = bitmaps.getOrPut(pixels) { pixels.toEditorImageBitmap() }
        val maskBitmap = rasterizePhotoMask(mask, pixels.size)
            .toEditorImageBitmap()
        sources += PhotoShaderLayerSource(layerId, sourceId, bitmap, maskBitmap)
    }
    return DecodedComposite(reference, keys, sources)
}

/**
 * The photo-editor workspace, driven entirely by [state] and [actions].
 * Compact windows stack top bar, canvas, a contextual tool panel and a
 * bottom category row; wide landscape windows move the tools into a
 * scrollable inspector beside the canvas. Selection, comparison, zoom, pan
 * and the close prompts are saveable view state owned here — the session
 * stays the source of truth for the document.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PhotoEditorContent(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    modifier: Modifier = Modifier,
    onOpenExport: ((PhotoExportResult) -> Unit)? = null,
    closeRequest: Int = 0,
    renderPreference: PhotoRenderPreference = PhotoRenderPreference.Auto
) {
    // Saveable workspace view state, keyed on the source — a new document
    // resets selection/panels, resize and rotation keep them.
    val workspace = rememberEditorWorkspaceState(state.info?.reference)
    // Geometry-tool UI state (crop preset, perspective grid) — UI-only,
    // per source, never part of the recipe.
    val toolUiState = rememberPhotoToolUiState(state.info?.reference)
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var showCancelExportDialog by rememberSaveable { mutableStateOf(false) }
    var pendingCloseAfterExport by rememberSaveable { mutableStateOf(false) }

    // One viewport per source — survives compact<->sidebar flips and
    // recreation; a new reference starts fresh.
    val viewport = rememberSaveable(
        state.info?.reference,
        saver = EditorViewportState.Saver
    ) { EditorViewportState() }

    // Bitmap conversion lives here, not in a layout branch: keyed on source
    // + shown pixels. Each produced value is stamped with the reference it
    // was decoded from, so a stale frame is dropped at read time the moment
    // a new source lands — not whenever the producer coroutine restarts.
    // The split compare only lines up pixel for pixel while the recipe
    // keeps the source frame — no crop, turn, flip or warp — and no
    // geometry or layer tool owns the canvas; otherwise the toggle falls
    // back to the full Before frame. A guarded write: it only changes
    // when the recipe or the open tool does.
    val splitAvailable = state.originalPreview != null &&
        state.document?.geometry == PhotoGeometry() &&
        !toolUiState.cropView.active &&
        !(state.toolEditing && workspace.activeTool in SPLIT_BLOCKING_TOOLS)
    if (viewport.splitAvailable != splitAvailable) {
        viewport.splitAvailable = splitAvailable
    }
    val shown = if (viewport.showsBefore && state.originalPreview != null) {
        state.originalPreview
    } else {
        state.preview
    }
    val reference = state.info?.reference
    val decodedShown by produceState<DecodedFrame?>(null, reference, shown) {
        value = DecodedFrame(
            reference,
            shown?.let { pixels ->
                withContext(Dispatchers.Default) { pixels.toEditorImageBitmap() }
            }
        )
    }

    // The runtime-shader path samples the upright original — a stable
    // bitmap converted once per source, not per edit, guarded by the same
    // reference stamp. "Before" compares against this unmodified image.
    val decodedOriginal by produceState<DecodedFrame?>(
        null, reference, state.originalPreview
    ) {
        value = DecodedFrame(
            reference,
            state.originalPreview?.let { pixels ->
                withContext(Dispatchers.Default) { pixels.toEditorImageBitmap() }
            }
        )
    }
    // Identity guard: only a holder stamped with the live reference may be
    // drawn or feed the shader factory. Same-reference refinement keeps the
    // previous frame until the new decode lands.
    val bitmap = decodedShown?.takeIf { it.reference == reference }?.bitmap
    val originalBitmap =
        decodedOriginal?.takeIf { it.reference == reference }?.bitmap

    // The composite shader's layer inputs decoded off the UI thread: each
    // visible layer's upright preview bitmap plus its mask rasterized at
    // the same size. The produceState key is exactly what the shader
    // consumes — (layer id, source id, mask) triples — so transform,
    // blend and opacity edits stay uniform uploads and never re-decode or
    // recompile. While any visible layer is undecoded [compositeSources]
    // is null, no renderer is created and the CPU pipeline keeps the
    // canvas honest.
    val documentLayers = state.document?.layers.orEmpty()
    val visibleLayers = documentLayers.filter { it.visible }
    val layerKeys = visibleLayers.map { Triple(it.id, it.source.id, it.mask) }
    val decodedComposite by produceState<DecodedComposite?>(
        null, reference, layerKeys, state.layerPreviews
    ) {
        value = withContext(Dispatchers.Default) {
            decodeCompositeSources(reference, layerKeys, state.layerPreviews)
        }
    }
    val compositeSources = when {
        visibleLayers.isEmpty() -> emptyList()
        else -> decodedComposite
            ?.takeIf { it.reference == reference && it.keys == layerKeys }
            ?.sources
    }

    // One compiled renderer per source bitmap + visible-layer signature —
    // mask bitmap identities ride inside compositeSources, so a mask
    // change rebuilds the program once. A compile failure lands in the
    // callback; a draw-time failure (software canvas, backend loss, a
    // stale signature the recipe outgrew) surfaces through the canvas's
    // onGpuFrameFailed — both fold into shaderFailure and hand the canvas
    // back to the CPU pipeline.
    var shaderRenderer by remember { mutableStateOf<PhotoShaderRenderer?>(null) }
    var shaderFailure by remember { mutableStateOf<Throwable?>(null) }
    DisposableEffect(originalBitmap, compositeSources, renderPreference) {
        shaderFailure = null
        val source = originalBitmap
        val sources = compositeSources
        val created = if (
            renderPreference == PhotoRenderPreference.Auto &&
            source != null && sources != null
        ) {
            compositePhotoShaderRendererFactory(
                source, sources,
                state.document?.source?.size
                    ?: PixelSize(source.width, source.height)
            ) { error -> shaderFailure = error }
        } else {
            null
        }
        shaderRenderer = created
        onDispose {
            shaderRenderer = null
            created?.close()
        }
    }

    // The mask tool paints through the CPU pipeline for its whole
    // transaction — stroke drafts would otherwise re-rasterize the mask
    // texture per point, and a stale texture would hide the stroke under
    // the brush. Transform/Blend keep drawing through the shader: they
    // only move uniforms. The transaction state — not the panel — is the
    // gate: activeTool still names the mask tool after Apply/Cancel
    // collapsed its surface, and the shader must resume there.
    val maskToolActive = state.toolEditing &&
        workspace.activeTool == EditorToolIds.PhotoLayerMask

    val shaderUsable = shaderRenderer != null && shaderFailure == null
    val latestActions by rememberUpdatedState(actions)
    LaunchedEffect(shaderFailure) {
        val error = shaderFailure
        if (error != null) latestActions.reportPreviewFallback(error)
    }
    // Never strand the session with CPU previews off after disposal.
    DisposableEffect(Unit) {
        onDispose { latestActions.setCpuPreviewEnabled(true) }
    }

    // What the canvas actually draws this frame: the shader recipe, the
    // unmodified original under "Before", or the CPU preview bitmap.
    // The crop tool's preview mode renders its crop-dropped display
    // document — the full source behind the crop window.
    val shaderEdits = (state.displayDocument ?: state.document)
        ?.let { it.toEdits() }
    val shaderImageSize = if (shaderUsable && shaderEdits != null) {
        originalBitmap?.let { bmp ->
            PhotoMapping(
                PixelSize(bmp.width, bmp.height), orientation = 1,
                shaderEdits.geometry
            ).outputSize
        }
    } else {
        null
    }
    // The runtime shader never approximates grain — its noise hash has
    // no portable bit-exact shader form — so a recipe with active grain
    // hands the canvas back to the CPU preview, which applies the
    // deterministic field through the finishing processor. Lens blur
    // gates the same way: its variable-radius disk over the Details
    // result has no honest fixed-cost shader form, so the CPU preview
    // draws it exactly while strength is non-zero.
    val grainActive =
        (shaderEdits?.finishing?.grain?.amount ?: 0f) != 0f
    val lensBlurActive = shaderEdits?.spatial?.lensBlur?.isNeutral == false
    // The retouch stage is CPU only: markup, the matte and neighbourhood
    // effects have no fixed-cost shader form, so the CPU preview draws them.
    // A RAW's develop rewrites the source itself, which the shader's bitmap
    // never sees, so RAW sources always draw through the CPU preview.
    val retouchActive = shaderEdits?.retouch?.isNeutral == false ||
        state.info?.isRaw == true
    val shaderDrawing = shaderUsable && !viewport.showsBefore &&
        shaderEdits != null && shaderImageSize != null && !maskToolActive &&
        !grainActive && !lensBlurActive && !retouchActive
    // While the shader owns the canvas the CPU preview pipeline sleeps;
    // any loss of it (failure, preference switch, new source, an
    // undecoded layer) wakes it on the current recipe — and the mask
    // tool keeps it awake for the stroke preview. An open Light, Colour or
    // Curves transaction keeps it awake too, and so does the opened
    // histogram: they read the same current-recipe preview the shader
    // displays.
    // One histogram preference instance serves the whole photo content:
    // the pill, the opened panel and the sheet's chart and settings rows
    // read the same channel/clipping state; Back closes the panel.
    // The histogram channel/clipping and the straighten grid follow the
    // editor settings both ways: the settings seed them, and a change made
    // in place (graph tap, grid toggle) becomes the stored default.
    val settingsStore = rememberEditorSettingsStore()
    val histogramPrefs = remember {
        PhotoHistogramPrefs().apply {
            mode = settingsStore.settings.histogramMode
            showClipping = settingsStore.settings.histogramClipping
        }
    }
    val editorSettings = settingsStore.settings
    LaunchedEffect(editorSettings.histogramMode, editorSettings.histogramClipping) {
        histogramPrefs.mode = editorSettings.histogramMode
        histogramPrefs.showClipping = editorSettings.histogramClipping
    }
    LaunchedEffect(editorSettings.straightenGrid, toolUiState) {
        toolUiState.showGrid = editorSettings.straightenGrid
    }
    LaunchedEffect(histogramPrefs, toolUiState, settingsStore) {
        snapshotFlow {
            Triple(histogramPrefs.mode, histogramPrefs.showClipping, toolUiState.showGrid)
        }.collect { (mode, clipping, grid) ->
            settingsStore.update {
                it.copy(histogramMode = mode, histogramClipping = clipping, straightenGrid = grid)
            }
        }
    }
    val histogramToolActive = state.toolEditing &&
        workspace.activeTool in setOf(
            EditorToolIds.PhotoLight, EditorToolIds.PhotoColour, EditorToolIds.PhotoCurves
        )
    // The save modal's thumbnail needs the current recipe's preview too.
    val cpuPreviewWanted = !shaderUsable || maskToolActive || state.exporting ||
        histogramToolActive || histogramPrefs.expanded || grainActive || lensBlurActive ||
        retouchActive || viewport.zoom > 1.001f
    LaunchedEffect(cpuPreviewWanted) {
        latestActions.setCpuPreviewEnabled(cpuPreviewWanted)
    }
    val displayBitmap = when {
        shaderDrawing -> null // the shader fills the image box
        viewport.showsBefore -> originalBitmap ?: bitmap
        else -> bitmap
    }
    // A renderer replaced mid-frame can still be drawn once — its closed
    // draw returns false honestly, but that is disposal, not a canvas
    // failure: only the live renderer's miss owns the CPU fallback.
    val onGpuFrameFailed = { failed: PhotoShaderRenderer ->
        if (shaderFailure == null && failed === shaderRenderer) {
            shaderFailure = ShaderCanvasUnavailableException()
        }
    }
    // Focus and the floating pill only make sense on a live image.
    val imageCapable = shaderDrawing || displayBitmap != null

    val imageDescription = stringResource(
        if (viewport.showsBefore) Res.string.editor_canvas_image_before
        else Res.string.editor_canvas_image,
        state.info?.displayName.orEmpty()
    )

    val latestState by rememberUpdatedState(state)

    // The canvas keeps the workspace's keyboard focus. Chrome transitions
    // that dispose the focused control (a selected catalogue cell, the
    // Apply/Cancel bar, a closed pane) bump this tick so focus returns to
    // the canvas after the new composition — never on an interaction or
    // recomposition, so an open numeric field is never stolen.
    val canvasFocusRequester = remember { FocusRequester() }
    var canvasRefocusTick by remember { mutableIntStateOf(0) }
    var focusPanelHeight by remember { mutableStateOf(0.dp) }
    LaunchedEffect(canvasRefocusTick) {
        if (canvasRefocusTick > 0) canvasFocusRequester.requestFocus()
    }
    fun collapseContextAndRefocus() {
        workspace.collapseContext()
        canvasRefocusTick++
    }

    fun requestClose() {
        val current = latestState
        when {
            showDiscardDialog || showCancelExportDialog -> Unit
            workspace.pinEditorOpen && workspace.settingsOpen ->
                workspace.pinEditorOpen = false
            workspace.settingsOpen -> {
                workspace.settingsOpen = false
                canvasRefocusTick++
            }
            // Back unwinds the shallowest surface first: catalogue, the
            // opened histogram, an
            // in-flight interaction, focus, the open tool transaction,
            // then the open context — only after all of those does the
            // export/dirty-close flow run.
            workspace.pinEditorOpen -> {
                workspace.pinEditorOpen = false
                canvasRefocusTick++
            }
            workspace.catalogueOpen -> {
                workspace.catalogueOpen = false
                canvasRefocusTick++
            }
            histogramPrefs.expanded -> {
                histogramPrefs.expanded = false
                canvasRefocusTick++
            }
            current.interactionActive -> latestActions.cancelEdit()
            toolUiState.whiteBalancePicking ->
                toolUiState.whiteBalancePicking = false
            workspace.focusMode -> {
                workspace.focusMode = false
                canvasRefocusTick++
            }
            current.toolEditing -> {
                latestActions.cancelToolEdit()
                collapseContextAndRefocus()
            }
            workspace.collapseContext() -> canvasRefocusTick++
            current.exporting -> showCancelExportDialog = true
            current.isDirty -> showDiscardDialog = true
            else -> latestActions.close()
        }
    }

    // Host-driven close (a desktop window's X, for instance) funnels through
    // the same draft/export/dirty prompts as Back.
    LaunchedEffect(closeRequest) {
        if (closeRequest != 0) requestClose()
    }

    // The whole workspace lives on a Surface so every control inherits a
    // readable content colour from the scheme — with no Surface,
    // LocalContentColor defaults to black-on-dark for all text.
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        // One shared HazeState per editor surface — the canvas marks itself
        // as the source and every EditorFloatingPanel samples it.
        val hazeState = remember { HazeState() }
        CompositionLocalProvider(
            LocalEditorHazeState provides hazeState,
            LocalHazePerformanceMode provides HazePerformanceMode.Performance
        ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val windowWidthDp = maxWidth.value
            val windowHeightDp = maxHeight.value
            // Zero/degenerate constraints (mid-resize) resolve as Compact —
            // the resolver only accepts positive finite dims.
            val mode =
                if (windowWidthDp > 0f && windowHeightDp > 0f &&
                    windowWidthDp.isFinite() && windowHeightDp.isFinite()
                ) {
                    EditorLayoutMode.resolve(windowWidthDp, windowHeightDp)
                } else {
                    EditorLayoutMode.Compact
                }

            // An interaction that outlives its controls (layout flip,
            // disposal) is cancelled — never left dangling. The tool's
            // outer draft is not an interaction: a mode flip keeps it.
            DisposableEffect(mode) {
                onDispose {
                    if (latestState.interactionActive) latestActions.cancelEdit()
                }
            }

            val toolTitles = photoToolEntries(LocalEditorHostTools.current, state.info?.isRaw == true).associate {
                it.descriptor.id to stringResource(it.descriptor.title)
            }

            fun selectTool(tool: EditorToolId) {
                val current = latestState
                if (current.interactionActive) latestActions.cancelEdit()
                val sameTool = workspace.activeTool == tool
                if (!sameTool && current.toolEditing) {
                    // Switching tools applies the outgoing tool as one
                    // history entry before opening the next — a no-op
                    // transaction records nothing, so routed contextual
                    // switches (Double exposure -> Transform) settle
                    // cleanly and drafts never nest.
                    latestActions.applyToolEdit()
                }
                workspace.selectTool(tool)
                // A contextual composition tool edits the selected image
                // layer: when the workspace selection doesn't resolve to
                // a live layer, take the topmost one. With no image
                // layer the tools' own surfaces decide (Double exposure
                // shows its real Add action; the rest can't be selected
                // through the catalogue anyway).
                if (tool in CONTEXTUAL_PHOTO_TOOLS) {
                    val layers = current.document?.layers.orEmpty()
                    if (layers.none { it.id == workspace.selectedLayerId }) {
                        workspace.selectedLayerId = layers.lastOrNull()?.id
                    }
                }
                if (!(sameTool && current.toolEditing)) {
                    // A tool begins on selection; re-selecting the live
                    // tool is a no-op.
                    toolTitles[tool]?.let(latestActions.beginToolEdit)
                }
                // The clicked catalogue cell is gone — hand the keys back
                // to the canvas so F/arrows never die with the disposal.
                canvasRefocusTick++
            }

            // The contextual seam for layer-aware surfaces — the Layers
            // list rows, the composition tools and their canvas overlays
            // share selection, the host picker and tool routing through
            // it. Provided around the mode branches below.
            val layerToolContext = PhotoLayerToolContext(
                selectedLayerId = workspace.selectedLayerId,
                selectLayer = { id -> workspace.selectedLayerId = id },
                requestAddImage = actions.requestAddImage,
                openTool = ::selectTool,
                enterFocusMode = { workspace.focusMode = true }
            )

            // The export finished winding down after the user confirmed the
            // close — leave now.
            LaunchedEffect(pendingCloseAfterExport, state.exporting) {
                if (pendingCloseAfterExport && !state.exporting) {
                    pendingCloseAfterExport = false
                    latestActions.close()
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Escape unwinds in the preview phase: an unwind never
                    // belongs to a text field, and preview still fires when
                    // a child holds focus. The workspace shortcuts below
                    // bubble instead, so a focused numeric/text field
                    // consumes its own keys before they reach here.
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            event.key == Key.Escape
                        ) {
                            requestClose()
                            true
                        } else {
                            false
                        }
                    }
                    .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val meta = event.isCtrlPressed || event.isMetaPressed
                    when {
                        meta && event.key == Key.Z && !event.isShiftPressed -> {
                            if (state.canUndo) {
                                actions.undo()
                                true
                            } else false
                        }
                        meta && event.key == Key.Z && event.isShiftPressed -> {
                            if (state.canRedo) {
                                actions.redo()
                                true
                            } else false
                        }
                        meta && event.key == Key.S -> {
                            if (state.document != null && !state.exporting &&
                                !state.isDrafting && !state.opening
                            ) {
                                actions.saveCopy()
                                true
                            } else false
                        }
                        event.key == Key.F && !meta -> {
                            // F toggles full-screen focus on a live image.
                            if (imageCapable) {
                                workspace.focusMode = !workspace.focusMode
                                true
                            } else false
                        }
                        else -> false
                    }
                }
            ) {
                // Switching tools always leaves focus: the focused card
                // belongs to the tool that entered it, and the full-screen
                // focus chrome must never be the landing surface for a new
                // tool. Keyed on a change so a restored focus survives.
                var focusedTool by remember {
                    mutableStateOf(workspace.activeTool)
                }
                LaunchedEffect(workspace.activeTool) {
                    if (workspace.activeTool != focusedTool) {
                        focusedTool = workspace.activeTool
                        if (workspace.focusMode) workspace.focusMode = false
                    }
                }
                // Focus = canvas only + floating chrome; everything else
                // (header, strips, panels, pill) is hidden, not rebuilt.
                // On a phone, a focused Light tool is not a separate
                // surface: the document header and the six-slot dock stay
                // in place and the floating card shows the minimal ruler.
                val compactLightFocus = mode == EditorLayoutMode.Compact &&
                    workspace.toolPanelExpanded &&
                    workspace.panel == EditorWorkspacePanel.Edit &&
                    workspace.activeTool == EditorToolIds.PhotoLight
                val focused = workspace.focusMode && imageCapable &&
                    !compactLightFocus
                // The composite focus chrome takes over when a real image
                // layer is the selection on a layer-aware surface: on the
                // Layers surface the selected row is the truth (base =
                // generic chrome); on a contextual tool surface the layer
                // the tool resolves is. A Light/Crop focus keeps the
                // generic navigator chrome.
                val layerFocusLayer = when {
                    workspace.panel == EditorWorkspacePanel.Layers ->
                        state.document?.layers?.firstOrNull {
                            it.id == workspace.selectedLayerId
                        }
                    workspace.panel == EditorWorkspacePanel.Edit &&
                        workspace.activeTool in CONTEXTUAL_PHOTO_TOOLS ->
                        resolveSelectedLayer(state, workspace.selectedLayerId)
                    else -> null
                }
                val fontScale = LocalDensity.current.fontScale
                val toolEntries = photoToolEntries(LocalEditorHostTools.current, state.info?.isRaw == true)
                // The tool surface's More menu extras — filled by the live
                // tool content (e.g. the Light parameter reset).
                val toolMenu = remember { PhotoToolMenuState() }
                // "Save and open": the copy is written first, and the
                // exported result is opened when it lands.
                var openAfterSave by remember { mutableStateOf(false) }
                LaunchedEffect(state.exported, openAfterSave) {
                    val exported = state.exported
                    if (openAfterSave && exported != null && !state.exporting) {
                        openAfterSave = false
                        onOpenExport?.invoke(exported)
                    }
                }
                // Shared inputs for the Figma V2 compact states — each
                // state owns its own header, so the bundle travels to the
                // per-state composables below.
                val compactArgs = PhotoCompactArgs(
                    state = state,
                    actions = actions,
                    workspace = workspace,
                    toolEntries = toolEntries,
                    toolUiState = toolUiState,
                    viewport = viewport,
                    bitmap = displayBitmap,
                    originalBitmap = originalBitmap,
                    imageDescription = imageDescription,
                    imageCapable = imageCapable,
                    shaderDrawing = shaderDrawing,
                    shaderRenderer = shaderRenderer,
                    shaderEdits = shaderEdits,
                    shaderImageSize = shaderImageSize,
                    onGpuFrameFailed = onGpuFrameFailed,
                    canvasFocusRequester = canvasFocusRequester,
                    widthDp = windowWidthDp,
                    stacked = fontScale >= 1.5f && windowWidthDp < 700f,
                    histogramPrefs = histogramPrefs,
                    toolMenu = toolMenu,
                    onSelectTool = ::selectTool,
                    onCollapseContext = ::collapseContextAndRefocus,
                    onRequestClose = ::requestClose,
                    onOpenExport = onOpenExport,
                    onSaveAndOpen = if (onOpenExport == null) null else {
                        { openAfterSave = true; actions.saveCopy() }
                    }
                )
                CompositionLocalProvider(
                    LocalPhotoLayerToolContext provides layerToolContext,
                    LocalPhotoToolUiState provides toolUiState
                ) {
                if (focused) {
                    EditorCanvas(
                        state = state,
                        actions = actions,
                        viewport = viewport,
                        bitmap = displayBitmap,
                        imageDescription = imageDescription,
                        compactUtility = mode == EditorLayoutMode.Compact,
                        showFooter = false,
                        focusMode = true,
                        splitLabelBottomInset = focusPanelHeight,
                        focusRequester = canvasFocusRequester,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag(TAG_PHOTO_CANVAS),
                        gpuRenderer = if (shaderDrawing) shaderRenderer else null,
                        gpuEdits = if (shaderDrawing) shaderEdits else null,
                        gpuImageSize = if (shaderDrawing) shaderImageSize else null,
                        onGpuFrameFailed = onGpuFrameFailed,
                        imageOverlay = { layout ->
                            // In composite focus the tool's own overlay
                            // stays interactive — the Transform quad, the
                            // mask brush surface, the Blend/Double
                            // selection outline; the Layers surface keeps
                            // its selection frame. Generic focus paints
                            // no layer overlay.
                            if (layerFocusLayer != null) {
                                if (workspace.panel == EditorWorkspacePanel.Edit &&
                                    workspace.activeTool in CONTEXTUAL_PHOTO_TOOLS
                                ) {
                                    LayerToolCanvasOverlay(
                                        layout = layout,
                                        state = state,
                                        actions = actions,
                                        workspace = workspace,
                                        toolUiState = toolUiState,
                                        tool = workspace.activeTool
                                    )
                                } else {
                                    PhotoLayerSelectionOverlay(
                                        layout = layout,
                                        state = state,
                                        selectedLayerId = layerFocusLayer.id
                                    )
                                }
                            }
                        },
                        overlay = {
                            if (layerFocusLayer != null) {
                                EditorLayerFocusChrome(
                                    layer = layerFocusLayer,
                                    state = state,
                                    actions = actions,
                                    onExitFocus = {
                                        workspace.focusMode = false
                                        canvasRefocusTick++
                                    },
                                    onOpenLayers = {
                                        workspace.panel =
                                            EditorWorkspacePanel.Layers
                                        workspace.toolPanelExpanded = true
                                        workspace.focusMode = false
                                        canvasRefocusTick++
                                    },
                                    // Apply commits an open tool
                                    // transaction, then exits; with no
                                    // transaction it simply exits.
                                    onApply = {
                                        if (state.toolEditing) {
                                            actions.applyToolEdit()
                                        }
                                        workspace.focusMode = false
                                        canvasRefocusTick++
                                    }
                                )
                            } else {
                                EditorFocusChrome(
                                    title = state.info?.displayName.orEmpty(),
                                    zoomLabel =
                                        if (viewport.zoom <= 1.001f) {
                                            stringResource(Res.string.editor_zoom_fit)
                                        } else {
                                            formatZoom(viewport.zoom)
                                        },
                                    state = state,
                                    actions = actions,
                                    onExitFocus = {
                                        workspace.focusMode = false
                                        canvasRefocusTick++
                                    },
                                    onToolCancel = {
                                        actions.cancelToolEdit()
                                        canvasRefocusTick++
                                    },
                                    onToolApply = {
                                        actions.applyToolEdit()
                                        canvasRefocusTick++
                                    },
                                    // Only the active ruler and its label
                                    // stay in focus: Light keeps its
                                    // parameter through the shared tool
                                    // UI state.
                                    toolContent = if (state.toolEditing &&
                                        workspace.activeTool == EditorToolIds.PhotoLight
                                    ) {
                                        {
                                            LightToolContent(
                                                state = state,
                                                actions = actions,
                                                minimal = true
                                            )
                                        }
                                    } else null,
                                    onBottomPanelHeight = { focusPanelHeight = it },
                                    zoomMenu = {
                                        ZoomMenu(
                                            zoom = viewport.zoom,
                                            imageReady = imageCapable,
                                            onFit = {
                                                viewport.zoom = 1f
                                                viewport.panX = 0f
                                                viewport.panY = 0f
                                            },
                                            onZoom2x = { viewport.zoom = 2f }
                                        )
                                    }
                                )
                            }
                        }
                    )
                } else if (mode == EditorLayoutMode.Compact) {
                    // Figma V3 phone composition — one explicit surface per
                    // workspace state, each owning its header: the document
                    // workspace (with the modal Tools catalogue and pin
                    // manager over it), the tool surface, the Layers
                    // workspace or a Source/Edits pane.
                    when {
                        workspace.toolPanelExpanded &&
                            workspace.panel == EditorWorkspacePanel.Edit ->
                            PhotoToolCompact(compactArgs)
                        workspace.toolPanelExpanded &&
                            workspace.panel == EditorWorkspacePanel.Layers ->
                            PhotoLayersCompact(compactArgs)
                        workspace.toolPanelExpanded ->
                            PhotoPaneCompact(compactArgs)
                        else -> PhotoWorkspaceCompact(compactArgs)
                    }
                } else if (mode == EditorLayoutMode.Desktop ||
                    mode == EditorLayoutMode.MasterDetail
                ) {
                    // The two large-window compositions share one bundle:
                    // the four-pane workbench on wide desktops, the
                    // master-detail tablet (canvas + 380 dp inspector) on
                    // near-square windows.
                    val workbenchArgs = PhotoDesktopArgs(
                        state = state,
                        actions = actions,
                        workspace = workspace,
                        toolUiState = toolUiState,
                        viewport = viewport,
                        bitmap = displayBitmap,
                        originalBitmap = originalBitmap,
                        imageDescription = imageDescription,
                        imageCapable = imageCapable,
                        shaderDrawing = shaderDrawing,
                        shaderRenderer = shaderRenderer,
                        shaderEdits = shaderEdits,
                        shaderImageSize = shaderImageSize,
                        onGpuFrameFailed = onGpuFrameFailed,
                        canvasFocusRequester = canvasFocusRequester,
                        histogramPrefs = histogramPrefs,
                        toolEntries = toolEntries,
                        toolMenu = toolMenu,
                        onSelectTool = ::selectTool,
                        onRequestClose = ::requestClose,
                        onOpenExport = onOpenExport,
                        onCanvasRefocus = { canvasRefocusTick++ }
                    )
                    if (mode == EditorLayoutMode.Desktop) {
                        PhotoDesktopWorkspace(workbenchArgs)
                    } else {
                        PhotoMasterDetailWorkspace(workbenchArgs)
                    }
                } else {
                    EditorTopBar(
                        state = state,
                        actions = actions,
                        widthDp = windowWidthDp,
                        compact = false,
                        onBack = ::requestClose
                    )
                    StatusStrips(
                        state = state,
                        actions = actions,
                        onOpenExport = onOpenExport
                    )
                    Row(modifier = Modifier.weight(1f)) {
                        EditorToolRail(
                            entries = toolEntries,
                            state = state,
                            workspace = workspace,
                            enabled = imageCapable,
                            onSelectTool = ::selectTool,
                            modifier = Modifier.fillMaxHeight()
                        )
                        EditorCanvas(
                            state = state,
                            actions = actions,
                            viewport = viewport,
                            bitmap = displayBitmap,
                            imageDescription = imageDescription,
                            compactUtility = false,
                            focusRequester = canvasFocusRequester,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 0.dp)
                                .testTag(TAG_PHOTO_CANVAS),
                            gpuRenderer = if (shaderDrawing) shaderRenderer else null,
                            gpuEdits = if (shaderDrawing) shaderEdits else null,
                            gpuImageSize = if (shaderDrawing) shaderImageSize else null,
                            onGpuFrameFailed = onGpuFrameFailed,
                            imageOverlay = { layout ->
                                if (workspace.panel ==
                                    EditorWorkspacePanel.Layers
                                ) {
                                    PhotoLayerSelectionOverlay(
                                        layout = layout,
                                        state = state,
                                        selectedLayerId =
                                            workspace.selectedLayerId
                                    )
                                } else if (
                                    workspace.panel ==
                                        EditorWorkspacePanel.Edit &&
                                    (workspace.activeTool == EditorToolIds.PhotoCrop ||
                                        workspace.activeTool == EditorToolIds.PhotoPerspective)
                                ) {
                                    EditorCropWindow(
                                        layout = layout,
                                        state = state,
                                        actions = actions,
                                        upright = state.info?.uprightSize,
                                        onCommit = {
                                            actions.applyToolEdit()
                                            canvasRefocusTick++
                                        }
                                    )
                                } else if (
                                    workspace.panel ==
                                        EditorWorkspacePanel.Edit &&
                                    photoToolOwnsWhiteBalancePick(workspace.activeTool) &&
                                    toolUiState.whiteBalancePicking
                                ) {
                                    WhiteBalancePickOverlay(
                                        layout = layout,
                                        state = state,
                                        actions = actions,
                                        toolUiState = toolUiState,
                                        viewport = viewport
                                    )
                                } else if (
                                    workspace.panel ==
                                        EditorWorkspacePanel.Edit &&
                                    workspace.activeTool ==
                                        EditorToolIds.PhotoLensBlur
                                ) {
                                    LensBlurOverlay(
                                        layout = layout,
                                        state = state,
                                        actions = actions
                                    )
                                } else if (
                                    workspace.panel ==
                                        EditorWorkspacePanel.Edit &&
                                    workspace.activeTool in RETOUCH_CANVAS_TOOLS
                                ) {
                                    RetouchCanvasOverlay(
                                        layout = layout,
                                        state = state,
                                        actions = actions,
                                        toolUiState = toolUiState,
                                        tool = workspace.activeTool
                                    )
                                } else if (
                                    workspace.panel ==
                                        EditorWorkspacePanel.Edit &&
                                    workspace.activeTool in CONTEXTUAL_PHOTO_TOOLS
                                ) {
                                    LayerToolCanvasOverlay(
                                        layout = layout,
                                        state = state,
                                        actions = actions,
                                        workspace = workspace,
                                        toolUiState = toolUiState,
                                        tool = workspace.activeTool
                                    )
                                }
                            }
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .width(mode.inspectorWidthDp(windowHeightDp).dp)
                                .fillMaxHeight()
                        ) {
                            Column(Modifier.fillMaxSize()) {
                                Text(
                                    text = when (workspace.panel) {
                                        EditorWorkspacePanel.Source ->
                                            stringResource(Res.string.editor_panel_source)
                                        EditorWorkspacePanel.Edits ->
                                            stringResource(Res.string.editor_panel_edits)
                                        EditorWorkspacePanel.Layers ->
                                            stringResource(Res.string.editor_layers)
                                        EditorWorkspacePanel.Edit ->
                                            photoToolTitle(workspace.activeTool)
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(
                                        start = 16.dp, top = 12.dp, bottom = 4.dp
                                    )
                                )
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    when (workspace.panel) {
                                        EditorWorkspacePanel.Source ->
                                            PhotoSourcePanel(
                                                state = state,
                                                originalBitmap = originalBitmap
                                            )
                                        EditorWorkspacePanel.Edits ->
                                            PhotoEditsPanel(
                                                state = state,
                                                actions = actions
                                            )
                                        EditorWorkspacePanel.Layers ->
                                            PhotoLayersPanel(
                                                state = state,
                                                actions = actions,
                                                workspace = workspace,
                                                baseThumbnail =
                                                    originalBitmap ?: bitmap,
                                                lazyList = false,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp)
                                            )
                                        EditorWorkspacePanel.Edit ->
                                            PhotoToolPanel(
                                                state = state,
                                                actions = actions,
                                                tool = workspace.activeTool
                                            )
                                    }
                                }
                                // While a tool owns the draft the wide
                                // inspector's bottom carries Cancel/Apply;
                                // otherwise the source/output footer.
                                if (state.toolEditing) {
                                    EditorToolActionsBar(
                                        onCancel = {
                                            actions.cancelToolEdit()
                                            collapseContextAndRefocus()
                                        },
                                        onApply = {
                                            actions.applyToolEdit()
                                            collapseContextAndRefocus()
                                        },
                                        applyEnabled = !state.interactionActive
                                    )
                                } else {
                                    PhotoInspectorFooter(state, actions)
                                }
                            }
                        }
                    }
                }
                }
                // The settings page covers every layout mode; the pin
                // manager it opens is a sheet above it.
                if (workspace.settingsOpen) {
                    PhotoEditorSettingsPage(
                        store = settingsStore,
                        workspace = workspace,
                        entries = toolEntries,
                        exportFormats = actions.exportFormats,
                        onBack = {
                            workspace.settingsOpen = false
                            canvasRefocusTick++
                        }
                    )
                    // Compact layouts host the pin manager themselves.
                    if (workspace.pinEditorOpen && mode != EditorLayoutMode.Compact) {
                        PhotoPinManagerSheet(
                            workspace = workspace,
                            entries = toolEntries,
                            onDismiss = { workspace.pinEditorOpen = false }
                        )
                    }
                }
            }

            if (showDiscardDialog) {
                AlertDialog(
                    onDismissRequest = { showDiscardDialog = false },
                    title = {
                        Text(stringResource(Res.string.editor_discard_title))
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDiscardDialog = false
                                actions.close()
                            },
                            modifier = Modifier.testTag(TAG_DISCARD_CONFIRM)
                        ) {
                            Text(stringResource(Res.string.editor_discard_confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showDiscardDialog = false },
                            modifier = Modifier.testTag(TAG_DISCARD_KEEP)
                        ) {
                            Text(stringResource(Res.string.editor_keep_editing))
                        }
                    }
                )
            }
            // The save modal sits under the cancel-export confirmation, which
            // Back raises on top of it.
            if (state.exporting) {
                PhotoSaveProgressDialog(
                    state = state,
                    onCancel = actions.cancelExport,
                    onBack = { requestClose() }
                )
            }
            if (showCancelExportDialog) {
                AlertDialog(
                    onDismissRequest = { showCancelExportDialog = false },
                    title = {
                        Text(stringResource(Res.string.editor_cancel_export_title))
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showCancelExportDialog = false
                                pendingCloseAfterExport = true
                                actions.cancelExport()
                            },
                            modifier = Modifier.testTag(TAG_EXPORT_DIALOG_CANCEL)
                        ) {
                            Text(
                                stringResource(Res.string.editor_cancel_export_confirm)
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showCancelExportDialog = false },
                            modifier = Modifier.testTag(TAG_EXPORT_DIALOG_KEEP)
                        ) {
                            Text(stringResource(Res.string.editor_keep_editing))
                        }
                    }
                )
            }

            // A blocked project restore owns the whole surface — the
            // recipe stays pending behind it, never silently dropped.
            val recovery = state.recovery
            if (recovery != null) {
                EditorRecoverySurface(
                    missingReferences = recovery.missingReferences,
                    onRetry = actions.retryRecovery,
                    onDiscard = actions.discardRecovery,
                    onRelink = actions.requestRelink
                )
            }

            NavigationEventHandler(
                state = rememberNavigationEventState(NavigationEventInfo.None),
                isForwardEnabled = false,
                onBackCompleted = { requestClose() }
            )
        }
        }
    }
}

/**
 * The preview pixels when they describe the image on screen: the preview
 * was rendered from the current recipe, or a live interaction is running
 * (the CPU preview then trails the gesture by one render). While the GPU
 * shader owns the canvas the CPU preview sleeps and can hold an older
 * recipe; histograms then show no data rather than another image's bins.
 */
internal fun currentPreviewPixels(state: PhotoEditorState): SrgbPixels? {
    val current = state.previewDocument == (state.displayDocument ?: state.document)
    return state.preview.takeIf { current || state.interactionActive }
}

/** Export result and error banners between the top bar and canvas. */
@Composable
internal fun StatusStrips(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    onOpenExport: ((PhotoExportResult) -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
    // In-flight export progress is the save modal; the strips carry the
    // published result and errors.
    if (!state.exporting && state.exported != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(Res.string.editor_exported),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
            val result = state.exported
            if (onOpenExport != null && result != null) {
                TextButton(
                    onClick = { onOpenExport(result) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(TAG_EXPORT_OPEN)
                ) {
                    Text(stringResource(Res.string.editor_open_export))
                }
            }
        }
    }
    val error = state.error
    if (error != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag(TAG_ERROR),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = errorMessage(state.errorKind),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
            when (state.errorKind) {
                // Capability failures can't be retried into success.
                PhotoErrorKind.Unsupported, PhotoErrorKind.TooLarge -> Unit
                else -> TextButton(
                    onClick = {
                        when (state.errorKind) {
                            PhotoErrorKind.Preview -> actions.retryPreview()
                            PhotoErrorKind.Export -> actions.saveCopy()
                            else -> actions.retryOpen()
                        }
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(TAG_ERROR_RETRY)
                ) {
                    Text(stringResource(Res.string.editor_retry))
                }
            }
            TextButton(
                onClick = actions.clearError,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(TAG_ERROR_DISMISS)
            ) {
                Text(stringResource(Res.string.editor_dismiss))
            }
        }
    }
    }
}

@Composable
private fun errorMessage(kind: PhotoErrorKind?): String = when (kind) {
    PhotoErrorKind.Preview -> stringResource(Res.string.editor_error_preview)
    PhotoErrorKind.Export -> stringResource(Res.string.editor_error_export)
    PhotoErrorKind.Unsupported ->
        stringResource(Res.string.editor_error_unsupported)
    PhotoErrorKind.TooLarge -> stringResource(Res.string.editor_error_too_large)
    else -> stringResource(Res.string.editor_error_open)
}

/**
 * The inspector's bottom context: the source's upright size and the size
 * the current geometry exports — real numbers only, no fabricated
 * histogram or layer list.
 */
@Composable
internal fun PhotoInspectorFooter(
    state: PhotoEditorState,
    actions: PhotoEditorActions
) {
    val upright = state.info?.uprightSize
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        if (upright != null) {
            val document = state.document
            val output = document?.let {
                PhotoMapping(it.source.size, 1, it.geometry).outputSize
            }
            Text(
                text = stringResource(
                    Res.string.editor_source_info,
                    "${upright.width} × ${upright.height}"
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            if (output != null) {
                Text(
                    text = stringResource(
                        Res.string.editor_output_info,
                        "${output.width} × ${output.height}"
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        // The wide layout has no More menu — reset lives in the inspector
        // footer so it stays reachable in every mode.
        TextButton(
            onClick = actions.reset,
            enabled = state.document != null && !state.interactionActive,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text(stringResource(Res.string.editor_reset_edits))
        }
    }
}

/**
 * The shared inputs of the Figma V2 phone states — every compact surface
 * draws the same canvas/session wiring and only differs in chrome, so the
 * bundle travels whole rather than repeating a dozen parameters per state.
 */
private class PhotoCompactArgs(
    val state: PhotoEditorState,
    val actions: PhotoEditorActions,
    val workspace: EditorWorkspaceState,
    val toolEntries: List<EditorToolEntry<PhotoEditorState, PhotoEditorActions>>,
    val toolUiState: PhotoToolUiState,
    val viewport: EditorViewportState,
    val bitmap: ImageBitmap?,
    val originalBitmap: ImageBitmap?,
    val imageDescription: String,
    val imageCapable: Boolean,
    val shaderDrawing: Boolean,
    val shaderRenderer: PhotoShaderRenderer?,
    val shaderEdits: PhotoEdits?,
    val shaderImageSize: PixelSize?,
    val onGpuFrameFailed: (PhotoShaderRenderer) -> Unit,
    val canvasFocusRequester: FocusRequester,
    val widthDp: Float,
    val stacked: Boolean,
    val histogramPrefs: PhotoHistogramPrefs,
    val toolMenu: PhotoToolMenuState,
    val onSelectTool: (EditorToolId) -> Unit,
    val onCollapseContext: () -> Unit,
    val onRequestClose: () -> Unit,
    val onOpenExport: ((PhotoExportResult) -> Unit)?,
    /** Save the copy and open it once the export lands. */
    val onSaveAndOpen: (() -> Unit)?
)

/**
 * The Figma V4 workspace home — a single full-bleed media field with all
 * chrome floating over it: the [Back]/[Before]/[Check] command row, the
 * 48 dp utility row at 72, and the pinned 70 dp toolbar region at the
 * foot carrying the 304×68 pill. The fit card clears the floating lanes
 * inside the field; zooming past fit expands the media edge to edge
 * behind every chrome element.
 *
 * The Tools catalogue is the media-viewer's bottom-bar anatomy: a real
 * scaffold sheet hidden at rest — the pill sits attached over it and
 * slides/fades away as the sheet scrolls up, while the sheet itself
 * (scaled haze blur, its own drag) leaves the home visible above it.
 * The pin manager stays a true modal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoWorkspaceCompact(args: PhotoCompactArgs) {
    val density = LocalDensity.current
    // Edge to edge: the photo is the window, the chrome lifts itself
    // clear of the status and navigation bars.
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val topInset = safeInsets.calculateTopPadding()
    val bottomInset = safeInsets.calculateBottomPadding()
    val sheetFocusRequester = remember { FocusRequester() }
    val toolsSheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.Hidden,
        skipHiddenState = false
    )
    val sheetScaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = toolsSheetState
    )
    // The workspace flag drives the sheet; a drag-to-shut settles back
    // off-screen and reads out as the flag clearing so the header, the
    // pill and the Escape unwind all agree on what is open.
    LaunchedEffect(args.workspace.catalogueOpen) {
        if (args.workspace.catalogueOpen) {
            toolsSheetState.expand()
            sheetFocusRequester.requestFocus()
        } else {
            toolsSheetState.hide()
            args.canvasFocusRequester.requestFocus()
        }
    }
    LaunchedEffect(toolsSheetState) {
        // The flag only clears when the sheet genuinely LEAVES
        // Expanded — a user drag to a lower anchor. The transition is
        // stamped at emission time inside the flow: a settle emission
        // is collected a frame or two late, so a parked (Hidden,
        // PartiallyExpanded) pair reaching the collector after a
        // reopen has already flipped the flag back on must not read
        // the live flag and kill that reopen.
        flow {
            var previous = toolsSheetState.currentValue
            snapshotFlow { toolsSheetState.currentValue }
                .collect { value ->
                    emit(previous to value)
                    previous = value
                }
        }.collect { (previous, value) ->
            if (args.workspace.catalogueOpen &&
                previous == SheetValue.Expanded &&
                value != SheetValue.Expanded
            ) {
                args.workspace.catalogueOpen = false
            }
        }
    }
    // The pill follows the finite sheet's offset down and out, matching
    // the media viewer's bottom bar. Progress is measured
    // from the resting anchor (height - the 1 dp peek), so a parked
    // sheet is exactly 0 — an invisible scrim must never eat input.
    val toolsSheetHeightPx = with(density) {
        (32.dp + EditorV2Layout.PhoneToolsSheetContent).toPx()
    }
    val sheetPeekPx = with(density) { 1.dp.toPx() }
    var fieldHeightPx by remember { mutableIntStateOf(0) }
    val sheetProgress: () -> Float = {
        if (fieldHeightPx <= 0 || toolsSheetHeightPx <= 0f) {
            0f
        } else {
            val restOffset = fieldHeightPx - sheetPeekPx
            runCatching {
                (restOffset - toolsSheetState.requireOffset()) /
                    toolsSheetHeightPx
            }.getOrDefault(0f).coerceIn(0f, 1f)
        }
    }
    // The sheet's body only carries semantics while it is on screen — a
    // hidden sheet is an empty surface for tests and readers.
    val toolsSheetVisible by remember {
        derivedStateOf {
            args.workspace.catalogueOpen || sheetProgress() > 0f
        }
    }
    BottomSheetScaffold(
        scaffoldState = sheetScaffoldState,
        // The sheet hides at rest — 1 dp is the smallest legal peek,
        // an invisible sliver of the transparent container's top edge.
        sheetPeekHeight = 1.dp,
        sheetSwipeEnabled = true,
        sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        // The body carries its own scaled haze blur — the container
        // stays clear so the blur reads as the sheet's material.
        sheetContainerColor = Color.Transparent,
        sheetTonalElevation = 0.dp,
        sheetDragHandle = null,
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .editorSheetBlur(
                        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                    )
                    .focusRequester(sheetFocusRequester)
                    .focusable(enabled = toolsSheetVisible)
                    .testTag(TAG_EDITOR_CATALOGUE_SHEET)
            ) {
                // The 32 dp M3 handle area — centred 32×4 pill, tagged
                // so tests can locate the real sheet edge. The sheet's
                // own swipeable drags it; no default DragHandle pads
                // this finite sheet above its ≈191 dp top.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .testTag(TAG_MODAL_DRAG_HANDLE),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 32.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant
                                    .copy(alpha = 0.4f)
                            )
                    )
                }
                // Keep the measured extent while hidden. Removing this body
                // moves the scaffold's anchors and can cancel a quick reopen.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(EditorV2Layout.PhoneToolsSheetContent)
                        .testTag(TAG_CATALOGUE_CONTENT)
                ) {
                    if (toolsSheetVisible) {
                        EditorToolCatalogueSheet(
                            entries = args.toolEntries,
                            state = args.state,
                            activeTool = args.workspace.activeTool,
                            enabled = args.imageCapable,
                            onSelect = args.onSelectTool,
                            title = stringResource(Res.string.editor_all_tools),
                            pinnedIds = args.workspace.pinnedPhotoDestinations
                                .mapNotNull { destination ->
                                    if (destination == PHOTO_PIN_LAYERS) {
                                        null
                                    } else {
                                        EditorToolId(destination)
                                    }
                                }
                                .toSet(),
                            onTogglePin = { id ->
                                args.workspace.togglePhotoPin(id.value)
                            },
                            isPinnable = { id ->
                                id.value in PHOTO_PINNABLE_DESTINATIONS
                            },
                            onEditPins = {
                                args.workspace.pinEditorOpen = true
                            },
                            showDragHandle = false,
                            extraCells = photoPhoneCatalogueExtras(args)
                        )
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        // The v4 field: media edge to edge under every floating chrome
        // element — the header included, so past-fit zoom fills the
        // whole window exactly like the design's zoomed frame.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { fieldHeightPx = it.height }
        ) {
            CompactCanvasCell(
                args = args,
                // The design's frame: the photo is the window. No inset
                // field, no rounded card — the image fits the whole
                // surface and the chrome floats over it.
                mediaPadding = PaddingValues.Zero,
                mediaCornerRadius = 0.dp,
                showFooter = false,
                // The compare pill lives in the header — the canvas
                // keeps only the hold-compare label.
                showBeforeChip = false,
                fitFieldInsets = PaddingValues.Zero,
                expandFieldOnZoom = true,
                fieldCornerRadius = 0.dp,
                surroundColor = Color.Black,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(TAG_PHOTO_CANVAS)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .testTag(TAG_PHOTO_HEADER)
            ) {
                // The Stitch command capsule — Close, Undo/Redo, the
                // compare toggle and the Save split pill, floating over
                // the media on the shared 12 dp window inset. There is no
                // title and no overflow on the home.
                EditorHomeHeader(
                    state = args.state,
                    actions = args.actions,
                    viewport = args.viewport,
                    imageReady = args.imageCapable,
                    onBack = args.onRequestClose,
                    onSave = args.actions.saveCopy,
                    onSaveAndOpen = args.onSaveAndOpen,
                    modifier = Modifier.padding(top = topInset + 12.dp)
                )
            }
            // The error/export banners float clear of the chrome stack —
            // inside the header column they would sit under the utility
            // lane (72+48) and lose every tap to the Undo/Redo chips.
            StatusStrips(
                state = args.state,
                actions = args.actions,
                onOpenExport = args.onOpenExport,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = topInset + 124.dp)
            )
            // No zoom chip on the home: the photo is zoomed by pinch (the
            // crop tool owns its own camera). Undo/Redo moved into the
            // header capsule; the histogram floats at 72 on the right.
            // The histogram is one surface that morphs: the pill grows into
            // the panel in place (and folds back), instead of a swap.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = topInset + 72.dp,
                        end = 12.dp,
                        start = 12.dp,
                        bottom = EditorV2Layout.PhoneToolbarRegion +
                            bottomInset + 16.dp
                    )
            ) {
                HistogramMorph(
                    expanded = args.histogramPrefs.expanded,
                    pixels = { currentPreviewPixels(args.state) },
                    prefs = args.histogramPrefs,
                    enabled = args.imageCapable
                )
            }
            // The modal-parity scrim the scaffold's sheet does not
            // draw: the home dims under the sheet and a tap outside
            // collapses it back to the pill. The clickable only exists
            // while the sheet is up — a disabled clickable still
            // consumes the down event, which would blind every chrome
            // element beneath it at rest.
            val scrimInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = sheetProgress() * 0.32f }
                    .background(Color.Black)
                    .then(
                        if (toolsSheetVisible) {
                            Modifier.clickable(
                                indication = null,
                                interactionSource = scrimInteraction,
                                onClick = {
                                    args.workspace.catalogueOpen = false
                                }
                            )
                        } else {
                            Modifier
                        }
                    )
            )
            // The toolbar cluster rides the field's foot — the 70 dp
            // region keeps its canonical measure while the pill (the
            // sheet's attached bar) slides down and fades away with the
            // sheet's expansion.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomInset)
                    .height(EditorV2Layout.PhoneToolbarRegion)
                    .testTag(TAG_PHOTO_TOOLBAR_REGION),
                contentAlignment = Alignment.Center
            ) {
                // The dock spans the lane minus its 10 dp side insets —
                // the inset rides this wrapper so the pill's tag bounds
                // stay the capsule itself.
                Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                    EditorFloatingToolbar(
                        workspace = args.workspace,
                        state = args.state,
                        entries = args.toolEntries,
                        enabled = args.imageCapable &&
                            !args.workspace.catalogueOpen,
                        onSelectTool = args.onSelectTool,
                        onShowTools = { args.workspace.toggleCatalogue() },
                        modifier = Modifier.graphicsLayer {
                            val progress = sheetProgress()
                            alpha = 1f - progress
                            translationY = size.height * progress
                        }
                    )
                }
            }
        }
    }
    if (args.workspace.pinEditorOpen) {
        PhotoPinManagerSheet(
            workspace = args.workspace,
            entries = args.toolEntries,
            onDismiss = { args.workspace.pinEditorOpen = false }
        )
    }
}

/**
 * The V4 photo tool surface: context header, media field and a shared
 * [BottomSheetScaffold]. Graphs and preset lists choose a usable peek.
 * Controls have no enclosing background at rest; material appears
 * continuously with expansion. Back cancels
 * the transaction; the header's filled Check commits it and collapses —
 * there is no bottom Cancel/Apply row.
 */
/**
 * The approved phone tool surface: the document header and the six-slot
 * dock stay in place while the active tool's card floats above the dock.
 * The card owns Apply — the header has no tool title, no overflow and no
 * top-right Check, and Back cancels the open transaction. Dragging the
 * card up still reveals the tool's secondary details; the document chrome
 * never moves.
 */
/**
 * The approved phone tool surface: the document header and the six-slot
 * dock stay in place while the active tool's card floats above the dock.
 * The card owns Apply — the header has no tool title, no overflow and no
 * top-right Check, and Back cancels the open transaction. Dragging the
 * card up reveals the tool's secondary details; the card's foot stays
 * glued above the dock and the document chrome never moves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoToolCompact(args: PhotoCompactArgs) {
    val activeTool = args.workspace.activeTool
    val isCrop = activeTool == EditorToolIds.PhotoCrop
    val isPerspective = activeTool == EditorToolIds.PhotoPerspective
    val isLensBlur = activeTool == EditorToolIds.PhotoLensBlur
    // Tools that own a fixed interactive canvas surface (the crop frame,
    // the layer transform quad, the mask brush, the lens-blur disc) fit
    // the media into the field above the card, so no handle or brush
    // target ever sits under the card. Every other tool is full bleed.
    val boundedField = isCrop || isPerspective || isLensBlur ||
        activeTool in CONTEXTUAL_PHOTO_TOOLS || activeTool in RETOUCH_RESET_ROW_TOOLS
    val density = LocalDensity.current
    // Edge to edge: the header lifts clear of the status bar, the dock and
    // the card clear of the navigation bar, and the photo runs under both.
    // Text entry happens in dialogs, so the keyboard never reflows this surface.
    val safeInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime).asPaddingValues()
    val topInset = safeInsets.calculateTopPadding()
    val bottomInset = safeInsets.calculateBottomPadding()
    val dockLane = EditorV2Layout.PhoneToolbarRegion + bottomInset
    // Large text grows the card's rows; the resting card reserves that
    // growth instead of clipping the ruler or pushing it under the pill.
    val fontScale = LocalDensity.current.fontScale
    // The crop control row stacks at large text (controls, then a
    // full-width ladder), so its card needs more growth than the others.
    val textGrowth = (
        (fontScale - 1f).coerceAtLeast(0f) *
            if (isCrop || isPerspective) 96f else 56f
        ).dp
    // Focus keeps the document chrome: the same header and dock, with the
    // card reduced to the single parameter's ruler. It never expands and
    // owns no commit row — its edits land per interaction.
    val focusMode = args.workspace.focusMode &&
        activeTool == EditorToolIds.PhotoLight
    // Two card heights: the resting card carries the tool's primary
    // controls; the expanded card adds its secondary details. Entering or
    // leaving focus morphs between them — the card's height and the
    // sheet's peek animate together, so the focus card grows out of the
    // full one instead of snapping.
    // A single-stage card measures its content; only tools with real
    // collapsed/expanded stages keep a declared height.
    val autoSized = !focusMode && photoToolSheetIsAutoSized(activeTool)
    var measuredCardHeight by remember(activeTool, fontScale) {
        mutableStateOf<Dp?>(null)
    }
    val targetCardPeek = when {
        focusMode -> PhotoToolFocusCardHeight + textGrowth
        autoSized -> measuredCardHeight ?: AutoSizedSheetFallback
        else -> photoToolSheetPeek(activeTool) + textGrowth
    }
    val animatedCardPeek by animateDpAsState(
        targetValue = targetCardPeek,
        animationSpec = SheetHeightSpring,
        label = "cardPeek"
    )
    val animatedCardMax by animateDpAsState(
        targetValue = when {
            focusMode || autoSized -> targetCardPeek
            else -> maxOf(
                photoToolSheetHeight(activeTool) + textGrowth,
                targetCardPeek
            )
        },
        animationSpec = SheetHeightSpring,
        label = "cardMax"
    )
    // The measured card *is* its content height, so it must not animate:
    // the spring would feed its overshoot back into the layout that is
    // being measured, retargeting itself forever. Only declared-height
    // cards morph.
    val cardPeek = if (autoSized) {
        measuredCardHeight ?: AutoSizedSheetFallback
    } else {
        animatedCardPeek
    }
    val cardMax = if (autoSized) cardPeek else animatedCardMax
    val sheetPeekHeight = cardPeek + dockLane
    var scaffoldHeightPx by remember { mutableIntStateOf(0) }
    val sheetScaffoldState = rememberBottomSheetScaffoldState()
    val sheetState = sheetScaffoldState.bottomSheetState
    val peekPx = with(density) { sheetPeekHeight.toPx() }
    val sheetHeightPx = with(density) { (cardMax + dockLane).toPx() }
    val expansion: () -> Float = {
        photoToolSheetExpansion(
            runCatching { sheetState.requireOffset() }.getOrDefault(Float.NaN),
            scaffoldHeightPx.toFloat(), sheetHeightPx, peekPx
        )
    }
    val expanded by remember(sheetState, peekPx) {
        derivedStateOf { expansion() > 0.5f }
    }
    val histogramPrefs = args.histogramPrefs
    // Focus belongs to the Light card: switching tools drops it so the
    // next Light visit starts on the full card.
    LaunchedEffect(activeTool) {
        if (activeTool != EditorToolIds.PhotoLight) {
            args.workspace.focusMode = false
        }
        // A tool switch starts on the new tool's resting peek — never on
        // the previous tool's expansion (which can be left behind when
        // the peek height changes under an expanded sheet).
        runCatching { sheetState.partialExpand() }
    }

    // The media fills the window: the header, the utility lane, the
    // tool card and the dock all float over the photo, exactly like the
    // design's frame. Only the card and the dock keep their own lane.
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            BottomSheetScaffold(
                scaffoldState = sheetScaffoldState,
                sheetPeekHeight = sheetPeekHeight,
                sheetSwipeEnabled = !args.state.interactionActive && !focusMode,
                sheetShape = RoundedCornerShape(28.dp),
                // The card carries its own material; the sheet container
                // stays clear so the photo reads behind the dock lane.
                sheetContainerColor = Color.Transparent,
                sheetTonalElevation = 0.dp,
                sheetShadowElevation = 0.dp,
                sheetDragHandle = null,
                sheetContent = {
                    CompositionLocalProvider(
                        LocalPhotoHistogramVisible provides expanded,
                        LocalPhotoToolSheetExpanded provides expanded,
                        LocalPhotoHistogramPrefs provides histogramPrefs
                    ) {
                        PhotoSheetContent(
                            args = args,
                            cardHeight = cardPeek +
                                (cardMax - cardPeek) * expansion(),
                            cardMax = cardMax,
                            dockLane = dockLane,
                            expansion = expansion(),
                            focusMode = focusMode,
                            // A fixed card (Light, Looks) has nothing to
                            // drag open, so it paints no grab pill.
                            showHandle = cardMax > cardPeek + 0.5.dp,
                            autoHeight = autoSized,
                            onMeasuredHeight = { height ->
                                if (measuredCardHeight != height) {
                                    measuredCardHeight = height
                                }
                            },
                            canFocus = activeTool == EditorToolIds.PhotoLight,
                            atRest = { expansion() < 0.05f },
                            onEnterFocus = { args.workspace.focusMode = true },
                            onExitFocus = { args.workspace.focusMode = false }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { scaffoldHeightPx = it.height }
                    .testTag(TAG_V3_ADJUSTMENT_SHEET)
            ) {
                Box(Modifier.fillMaxSize()) {
                    CompactCanvasCell(
                        args = args,
                        mediaPadding = PaddingValues.Zero,
                        mediaCornerRadius = 0.dp,
                        showFooter = false,
                        showBeforeChip = false,
                        // Tools that own a fixed interactive surface keep
                        // a handle-safe field above the card. The crop
                        // family is the exception: its media runs full
                        // bleed under the header and its action row,
                        // while the frame frames its own field below them
                        // (the row is 48 dp plus two 8 dp gaps).
                        fitFieldInsets = if (boundedField && !isCrop && !isPerspective) {
                            PaddingValues(
                                start = 0.dp,
                                top = topInset +
                                    if (activeTool in RETOUCH_RESET_ROW_TOOLS) 128.dp else 68.dp,
                                end = 0.dp,
                                bottom = sheetPeekHeight
                            )
                        } else {
                            PaddingValues.Zero
                        },
                        frameFieldInsets = if (boundedField) {
                            PaddingValues(
                                start = 0.dp,
                                // The retouch tools' Reset and ink pill share a
                                // row under the header, clear of the photo.
                                top = topInset + when {
                                    isCrop || isPerspective -> 124.dp
                                    activeTool in RETOUCH_RESET_ROW_TOOLS -> 128.dp
                                    else -> 68.dp
                                },
                                end = 0.dp,
                                bottom = sheetPeekHeight
                            )
                        } else {
                            PaddingValues.Zero
                        },
                        expandFieldOnZoom = true,
                        fieldCornerRadius = 0.dp,
                        surroundColor = Color.Black,
                        // The compare stage labels ride above the card
                        // instead of hiding behind it.
                        splitLabelBottomInset = sheetPeekHeight + 4.dp,
                        imageOverlay = { layout ->
                            when {
                                // Perspective is the merged tool's Vertical tab.
                                isCrop || isPerspective -> EditorCropWindow(
                                    layout = layout,
                                    state = args.state,
                                    actions = args.actions,
                                    upright = args.state.info?.uprightSize,
                                    onCommit = {
                                        args.actions.applyToolEdit()
                                        args.onCollapseContext()
                                    }
                                )
                                isLensBlur -> LensBlurOverlay(
                                    layout = layout,
                                    state = args.state,
                                    actions = args.actions
                                )
                                photoToolOwnsWhiteBalancePick(activeTool) &&
                                    args.toolUiState.whiteBalancePicking ->
                                    WhiteBalancePickOverlay(
                                        layout = layout,
                                        state = args.state,
                                        actions = args.actions,
                                        toolUiState = args.toolUiState,
                                        viewport = args.viewport
                                    )
                                activeTool in RETOUCH_CANVAS_TOOLS ->
                                    RetouchCanvasOverlay(
                                        layout = layout,
                                        state = args.state,
                                        actions = args.actions,
                                        toolUiState = args.toolUiState,
                                        tool = activeTool
                                    )
                                activeTool in CONTEXTUAL_PHOTO_TOOLS ->
                                    LayerToolCanvasOverlay(
                                        layout = layout,
                                        state = args.state,
                                        actions = args.actions,
                                        workspace = args.workspace,
                                        toolUiState = args.toolUiState,
                                        tool = activeTool,
                                        toolTitle = photoToolTitle(activeTool)
                                    )
                            }
                        },
                        // The adjust tools float the histogram card over the
                        // photo's top end; crop's interactive chrome keeps
                        // the canvas clear. Both sit below the document
                        // header on the same 12 dp window inset.
                        canvasOverlay = {
                            if (activeTool == EditorToolIds.PhotoLight ||
                                activeTool == EditorToolIds.PhotoColour
                            ) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(
                                            top = topInset + 72.dp,
                                            start = 12.dp, end = 12.dp,
                                            // Clear the tool card: the
                                            // panel reads above it.
                                            bottom = sheetPeekHeight + 8.dp
                                        )
                                ) {
                                    HistogramMorph(
                                        expanded = args.histogramPrefs.expanded,
                                        pixels = {
                                            currentPreviewPixels(args.state)
                                        },
                                        prefs = args.histogramPrefs,
                                        enabled = args.imageCapable
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(TAG_PHOTO_CANVAS)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // The document header, unchanged from the home:
                        // Back cancels the open draft and collapses back
                        // to the document.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = topInset + 12.dp)
                        ) {
                            EditorHomeHeader(
                                state = args.state,
                                actions = args.actions,
                                viewport = args.viewport,
                                imageReady = args.imageCapable,
                                onBack = {
                                    if (focusMode) {
                                        // Focus is a precision view over
                                        // the same tool — Back returns to
                                        // the full Light card, it does not
                                        // throw the tool away.
                                        args.workspace.focusMode = false
                                    } else {
                                        args.actions.cancelToolEdit()
                                        args.onCollapseContext()
                                    }
                                },
                                onSave = args.actions.saveCopy,
                                onSaveAndOpen = args.onSaveAndOpen,
                                // Crop owns a full-screen frame and its
                                // commit sits in the card's foot beside
                                // Reset (D40); every other tool commits
                                // from the header.
                                applyLabel = if (isCrop || isPerspective) {
                                    null
                                } else {
                                    stringResource(Res.string.editor_apply)
                                },
                                onApply = if (isCrop || isPerspective) {
                                    null
                                } else {
                                    {
                                        // Apply ends the tool's focus too:
                                        // the result is the document home,
                                        // never the retired full-screen
                                        // focus chrome.
                                        args.actions.applyToolEdit()
                                        args.workspace.focusMode = false
                                        args.onCollapseContext()
                                    }
                                },
                                modifier = Modifier.testTag(TAG_PHOTO_HEADER)
                            )
                        }
                        // The crop actions float directly under the header
                        // (the reference's Reset / Apply Crop pair), so the
                        // card carries only its controls.
                        if (!focusMode && (isCrop || isPerspective)) {
                            CropActionRow(
                                enabled = !args.state.interactionActive,
                                resetLabel = args.toolMenu.resetParameterLabel,
                                resetEnabled = args.toolMenu.resetParameterEnabled &&
                                    !args.state.interactionActive,
                                onReset = args.toolMenu.onResetParameter,
                                onApply = {
                                    args.actions.applyToolEdit()
                                    args.workspace.focusMode = false
                                    args.onCollapseContext()
                                },
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        // Light's, Colour's and Curves' reset sit in the crop
                        // reset's slot: a floating row under the header. The
                        // header keeps the tool's Apply.
                        if (activeTool == EditorToolIds.PhotoLight ||
                            activeTool == EditorToolIds.PhotoColour ||
                            activeTool == EditorToolIds.PhotoCurves ||
                            activeTool in RETOUCH_RESET_ROW_TOOLS
                        ) {
                            LightResetRow(
                                resetLabel = args.toolMenu.resetParameterLabel,
                                resetEnabled = args.toolMenu.resetParameterEnabled &&
                                    !args.state.interactionActive,
                                onReset = args.toolMenu.onResetParameter,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        // The contextual tools name the layer they act
                        // on; Double exposure's context is the document
                        // itself, so its line keeps the source name.
                        if (activeTool in CONTEXTUAL_PHOTO_TOOLS) {
                            PhotoBreadcrumb(
                                label = if (activeTool ==
                                    EditorToolIds.PhotoDoubleExposure
                                ) {
                                    args.state.info?.displayName.orEmpty()
                                } else {
                                    stringResource(
                                        Res.string.editor_selected_layer,
                                        resolveSelectedLayer(
                                            args.state,
                                            args.workspace.selectedLayerId
                                        )?.name.orEmpty()
                                    )
                                },
                                floating = true
                            )
                        }
                        StatusStrips(
                            state = args.state,
                            actions = args.actions,
                            onOpenExport = args.onOpenExport
                        )
                    }
                }
            }
            // The six-slot design dock never leaves the tool surface; it
            // rides the same 70 dp lane as the home, over the sheet's
            // transparent foot.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomInset)
                    .height(EditorV2Layout.PhoneToolbarRegion)
                    .testTag(TAG_PHOTO_TOOLBAR_REGION),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                    EditorFloatingToolbar(
                        workspace = args.workspace,
                        state = args.state,
                        entries = args.toolEntries,
                        enabled = args.imageCapable &&
                            !args.workspace.catalogueOpen,
                        onSelectTool = args.onSelectTool,
                        onShowTools = { args.workspace.toggleCatalogue() }
                    )
                }
            }
        }
    }
    if (args.workspace.catalogueOpen) {
        ModalBottomSheet(
            onDismissRequest = { args.workspace.catalogueOpen = false },
            sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true
            ),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(Modifier.testTag(TAG_EDITOR_CATALOGUE_SHEET)) {
                EditorToolCatalogueSheet(
                    entries = args.toolEntries,
                    state = args.state,
                    activeTool = activeTool,
                    enabled = args.imageCapable,
                    onSelect = { tool ->
                        args.actions.cancelToolEdit()
                        args.onSelectTool(tool)
                    },
                    title = stringResource(Res.string.editor_all_tools),
                    pinnedIds = args.workspace.pinnedPhotoDestinations
                        .mapNotNull { destination ->
                            if (destination == PHOTO_PIN_LAYERS) {
                                null
                            } else {
                                EditorToolId(destination)
                            }
                        }
                        .toSet(),
                    onTogglePin = { id ->
                        args.workspace.togglePhotoPin(id.value)
                    },
                    isPinnable = { id ->
                        id.value in PHOTO_PINNABLE_DESTINATIONS
                    },
                    onEditPins = {
                        args.workspace.pinEditorOpen = true
                    },
                    showDragHandle = false,
                    extraCells = photoPhoneCatalogueExtras(args)
                )
            }
        }
    }
    if (args.workspace.pinEditorOpen) {
        PhotoPinManagerSheet(
            workspace = args.workspace,
            entries = args.toolEntries,
            onDismiss = { args.workspace.pinEditorOpen = false }
        )
    }
}

/** The card's top drag strip — no painted handle in the approved card. */
private val PhotoToolCardHandleHeight = 12.dp

/** The focus card's fixed height — the carousel title plus its ruler,
 *  with the same 16 dp breathing room below the scrubber as at the sides. */
private val PhotoToolFocusCardHeight = 150.dp
/** The normal ⇄ focus card morph. */
private const val FOCUS_MORPH_MS = 240

/** Sheet height changes — tool switches and the measured card — settle
 *  with a small bounce instead of a cut. */
private val SheetHeightSpring = spring<Dp>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow
)

/**
 * The crop actions: a floating row directly under the document header —
 * the reference's Reset / Apply Crop pair, lifted out of the card so the
 * card is just the aspect row and the control row.
 */
@Composable
private fun CropActionRow(
    enabled: Boolean,
    resetLabel: String?,
    resetEnabled: Boolean,
    onReset: (() -> Unit)?,
    onApply: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (resetLabel != null && onReset != null) {
            Row(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(
                        enabled = resetEnabled,
                        role = Role.Button,
                        onClickLabel = resetLabel,
                        onClick = onReset
                    )
                    .semantics { contentDescription = resetLabel }
                    .padding(horizontal = 10.dp)
                    .testTag(TAG_RESET_CROP),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = EditorSymbols.RestartAlt,
                    contentDescription = null,
                    tint = if (resetEnabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(Res.string.editor_crop_reset_short),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (resetEnabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    maxLines = 1
                )
            }
        } else {
            Spacer(Modifier.width(1.dp))
        }
        WorkbenchPrimaryAction(
            label = stringResource(Res.string.editor_apply_crop),
            onClick = onApply,
            enabled = enabled,
            icon = EditorSymbols.Check,
            compact = true,
            modifier = Modifier.testTag(TAG_TOOL_APPLY)
        )
    }
}

/**
 * The Light sub-tool reset in the crop reset's slot: a floating row under
 * the document header, the same 40 dp scale and pill anatomy. The label
 * names the active sub-tool for talkback; the visible text is "Reset".
 */
@Composable
private fun LightResetRow(
    resetLabel: String?,
    resetEnabled: Boolean,
    onReset: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (resetLabel != null && onReset != null) {
            Row(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(
                        enabled = resetEnabled,
                        role = Role.Button,
                        onClickLabel = resetLabel,
                        onClick = onReset
                    )
                    .semantics { contentDescription = resetLabel }
                    .padding(horizontal = 10.dp)
                    .testTag(TAG_RESET_TOOL),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = EditorSymbols.RestartAlt,
                    contentDescription = null,
                    tint = if (resetEnabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(Res.string.editor_crop_reset_short),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (resetEnabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * The floating tool card body. The sheet lane reserves the card, the
 * Apply row and the dock's transparent foot; the card itself grows
 * between [cardHeight] and its tool's full height while its bottom edge
 * stays anchored above the dock.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.PhotoSheetContent(
    args: PhotoCompactArgs,
    cardHeight: Dp,
    cardMax: Dp,
    dockLane: Dp,
    expansion: Float,
    focusMode: Boolean,
    showHandle: Boolean,
    autoHeight: Boolean,
    onMeasuredHeight: (Dp) -> Unit,
    canFocus: Boolean,
    atRest: () -> Boolean,
    onEnterFocus: () -> Unit,
    onExitFocus: () -> Unit
) {
    val histogramPrefs = LocalPhotoHistogramPrefs.current
    val density = LocalDensity.current
    // The sheet lane always measures the expanded card so the scaffold
    // owns a real travel range; the card inside only paints [cardHeight],
    // which keeps its foot glued above the dock at every offset.
    // A measured card also declares its height: if the lane followed a grown
    // card before the peek did, the scaffold would briefly gain an expanded
    // anchor and read its offset uninitialized. The card measures unbounded
    // inside the lane, and the peek catches up on the next frame.
    Column(modifier = Modifier.fillMaxWidth().height(cardMax + dockLane)) {
        PhotoToolSheetSurface(
            expansion = expansion,
            modifier = if (autoHeight) {
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .onSizeChanged {
                        onMeasuredHeight(with(density) { it.height.toDp() })
                    }
                    .testTag(TAG_V3_SHEET_BODY)
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(cardHeight)
                    .testTag(TAG_V3_SHEET_BODY)
            }
        ) {
            Column(
                Modifier
                    .then(
                        if (autoHeight) Modifier.fillMaxWidth()
                        else Modifier.fillMaxSize()
                    )
                    // The card's own 28 dp shape is the content's clip —
                    // a scrolled strip never paints outside the corners.
                    .clip(RoundedCornerShape(28.dp))
            ) {
                if (!focusMode && showHandle) Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PhotoToolCardHandleHeight)
                        .testTag(TAG_V3_SHEET_HANDLE),
                    contentAlignment = Alignment.Center
                ) {
                    // The card's grab affordance — the same 32×4 pill the
                    // sheets use — except on the crop card, whose reference
                    // draws none.
                    if (args.workspace.activeTool != EditorToolIds.PhotoCrop &&
                        args.workspace.activeTool != EditorToolIds.PhotoPerspective
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 32.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                        .copy(alpha = 0.4f)
                                )
                        )
                    }
                }
                val scrollState = rememberScrollState()
                var focusDrag by remember { mutableFloatStateOf(0f) }
                val focusDragThreshold = with(density) { 26.dp.toPx() }
                Box(
                    modifier = if (autoHeight) {
                        // The measured card has no scroll child to raise
                        // the focus overscroll, so a vertical drag on the
                        // card body enters/leaves focus directly.
                        Modifier
                            .fillMaxWidth()
                            .clipToBounds()
                            .pointerInput(canFocus, focusMode) {
                                if (!canFocus) return@pointerInput
                                var total = 0f
                                detectVerticalDragGestures(
                                    onDragStart = { total = 0f },
                                    onVerticalDrag = { change, amount ->
                                        total += amount
                                        change.consume()
                                    },
                                    onDragEnd = {
                                        if (!focusMode && total > focusDragThreshold) {
                                            onEnterFocus()
                                        } else if (focusMode &&
                                            total < -focusDragThreshold
                                        ) {
                                            onExitFocus()
                                        }
                                    }
                                )
                            }
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clipToBounds()
                            .nestedScroll(
                                remember(canFocus, focusMode, atRest) {
                                    object : NestedScrollConnection {
                                        override fun onPostScroll(
                                            consumed: Offset,
                                            available: Offset,
                                            source: NestedScrollSource
                                        ): Offset {
                                            // The sheet owns the drag; the
                                            // card's own scroller sees the
                                            // leftover first. A downward
                                            // overscroll at rest focuses the
                                            // tool; an upward one leaves focus.
                                            val threshold =
                                                with(density) { 26.dp.toPx() }
                                            if (!focusMode && canFocus &&
                                                atRest() && available.y > 0f
                                            ) {
                                                focusDrag += available.y
                                                if (focusDrag > threshold) {
                                                    focusDrag = 0f
                                                    onEnterFocus()
                                                }
                                                return Offset(0f, available.y)
                                            }
                                            if (focusMode && available.y < 0f) {
                                                focusDrag += available.y
                                                if (focusDrag < -threshold) {
                                                    focusDrag = 0f
                                                    onExitFocus()
                                                }
                                                return Offset(0f, available.y)
                                            }
                                            return Offset.Zero
                                        }
                                    }
                                }
                            )
                            .verticalScroll(scrollState)
                    }
                ) {
                    CompositionLocalProvider(
                        LocalPhotoToolMenu provides args.toolMenu
                    ) {
                        Column {
                            // The card's height morphs with FOCUS_MORPH_MS;
                            // the content crossfades over the same beat so
                            // the focus card grows out of the full one.
                            Crossfade(
                                targetState = focusMode,
                                animationSpec = tween(FOCUS_MORPH_MS),
                                label = "focusContent"
                            ) { focused ->
                                if (focused) {
                                    Box(Modifier.padding(top = 8.dp)) {
                                        LightToolContent(
                                            state = args.state,
                                            actions = args.actions,
                                            minimal = true
                                        )
                                    }
                                } else PhotoToolPanel(
                                    state = args.state,
                                    actions = args.actions,
                                    tool = args.workspace.activeTool
                                )
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(dockLane))
    }
}

/**
 * The compact Layers workspace — the floating context header
 * (Back/Layers/Add image), the composite canvas with the selected
 * layer's outline, then the flat inline inspector carrying the layer
 * list. There is no breadcrumb, no Done strip and no Apply/Cancel:
 * layer actions land as immediate global history steps.
 */
@Composable
private fun PhotoLayersCompact(args: PhotoCompactArgs) {
    Column(Modifier.fillMaxSize()) {
        // Same 12 dp window inset as every other compact context header;
        // the tag rides the capsule so bounds report the 56 dp bar.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, start = 12.dp, end = 12.dp)
        ) {
            WorkbenchFloatingContextHeader(
                title = stringResource(Res.string.editor_layers),
                onBack = args.onCollapseContext,
                backTag = TAG_BACK,
                stacked = args.stacked,
                modifier = Modifier.testTag(TAG_PHOTO_HEADER),
                menuContent = { PhotoContextMenuItems(args) },
                primaryIcon = EditorSymbols.AddPhotoAlternate,
                primaryLabel = stringResource(Res.string.editor_layer_add_image),
                onPrimaryAction = args.actions.requestAddImage,
                primaryEnabled = canAddLayerImage(args.state),
                primaryTag = TAG_LAYER_ADD_HEADER
            )
        }
        StatusStrips(
            state = args.state,
            actions = args.actions,
            onOpenExport = args.onOpenExport
        )
        CompactCanvas(
            args = args,
            mediaPadding = PaddingValues(
                horizontal = 8.dp,
                vertical = 4.dp
            ),
            showFooter = false,
            showBeforeChip = false,
            imageOverlay = { layout ->
                PhotoLayerSelectionOverlay(
                    layout = layout,
                    state = args.state,
                    selectedLayerId = args.workspace.selectedLayerId
                )
            }
        )
        PhotoInlineInspector(
            args = args,
            contentScrollable = false
        ) {
            PhotoLayersPanel(
                state = args.state,
                actions = args.actions,
                workspace = args.workspace,
                baseThumbnail = args.originalBitmap ?: args.bitmap,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 10.dp, start = 12.dp, end = 12.dp)
            )
        }
    }
}

/**
 * The compact Source/Edits pane — the tool surface's scaffold (context
 * header, canvas, breadcrumb, flat inline inspector) with the pane's
 * real content inside the scroll region. The header's Back is the only
 * exit — the V3 anatomy has no bottom Done strip.
 */
@Composable
private fun PhotoPaneCompact(args: PhotoCompactArgs) {
    val title = stringResource(
        if (args.workspace.panel == EditorWorkspacePanel.Source) {
            Res.string.editor_panel_source
        } else {
            Res.string.editor_panel_edits
        }
    )
    Column(Modifier.fillMaxSize()) {
        PhotoContextHeader(
            args = args,
            title = title,
            onBack = args.onCollapseContext
        )
        StatusStrips(
            state = args.state,
            actions = args.actions,
            onOpenExport = args.onOpenExport
        )
        CompactCanvas(
            args = args,
            mediaPadding = PaddingValues(
                horizontal = 8.dp,
                vertical = 4.dp
            ),
            showFooter = false,
            showBeforeChip = false
        )
        PhotoBreadcrumb(label = title)
        PhotoInlineInspector(args = args) {
            when (args.workspace.panel) {
                EditorWorkspacePanel.Source -> PhotoSourcePanel(
                    state = args.state,
                    originalBitmap = args.originalBitmap
                )
                else -> PhotoEditsPanel(
                    state = args.state,
                    actions = args.actions
                )
            }
        }
    }
}

/**
 * The real non-tool surfaces that ride the phone catalogue next to the
 * tool grid: the Layers workspace (which lost its default dock slot to
 * the six-slot design) and the Source/Edits panes (which lost the tool
 * header's More menu). They are cells, never fake tools.
 */
@Composable
private fun photoPhoneCatalogueExtras(
    args: PhotoCompactArgs
): List<EditorCatalogueExtraCell> {
    val layers = stringResource(Res.string.editor_layers)
    val source = stringResource(Res.string.editor_panel_source)
    val edits = stringResource(Res.string.editor_panel_edits)
    val openIn = stringResource(Res.string.editor_retouch_open_in)
    val settingsLabel = stringResource(Res.string.editor_settings)
    val openExternally = LocalEditorHostTools.current.openInExternalEditor
    fun selected(panel: EditorWorkspacePanel) =
        args.workspace.panel == panel && args.workspace.toolPanelExpanded
    return listOfNotNull(
        openExternally?.let { open ->
            EditorCatalogueExtraCell(
                icon = RetouchSymbols.OpenInNew,
                label = openIn,
                tag = TAG_CATALOGUE_OPEN_IN,
                onClick = open
            )
        },
        EditorCatalogueExtraCell(
            icon = EditorSymbols.Layers,
            label = layers,
            tag = TAG_CATALOGUE_LAYERS,
            selected = selected(EditorWorkspacePanel.Layers),
            enabled = args.imageCapable,
            onClick = {
                args.workspace.togglePanel(EditorWorkspacePanel.Layers)
            }
        ),
        EditorCatalogueExtraCell(
            icon = EditorSymbols.Info,
            label = source,
            tag = TAG_PANEL_SOURCE,
            selected = selected(EditorWorkspacePanel.Source),
            enabled = args.imageCapable,
            onClick = {
                args.workspace.togglePanel(EditorWorkspacePanel.Source)
            }
        ),
        EditorCatalogueExtraCell(
            icon = EditorSymbols.ViewList,
            label = edits,
            tag = TAG_PANEL_EDITS,
            selected = selected(EditorWorkspacePanel.Edits),
            enabled = args.imageCapable,
            onClick = {
                args.workspace.togglePanel(EditorWorkspacePanel.Edits)
            }
        ),
        EditorCatalogueExtraCell(
            icon = Icons.Outlined.Settings,
            label = settingsLabel,
            tag = TAG_CATALOGUE_SETTINGS,
            onClick = {
                args.workspace.catalogueOpen = false
                args.workspace.settingsOpen = true
            }
        )
    )
}

internal const val TAG_CATALOGUE_SETTINGS = "editor.catalogue.settings"

/** The shared context header — Close/context title/More, floating capsule. */
@Composable
private fun PhotoContextHeader(
    args: PhotoCompactArgs,
    title: String,
    onBack: () -> Unit,
    primaryIcon: ImageVector? = null,
    primaryLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    primaryEnabled: Boolean = true
) {
    // The capsule floats on the shared 12 dp window inset; the tag rides
    // the capsule itself so bounds report the 56 dp bar, not the inset.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, start = 12.dp, end = 12.dp)
    ) {
        WorkbenchFloatingContextHeader(
            title = title,
            onBack = onBack,
            backTag = TAG_BACK,
            stacked = args.stacked,
            modifier = Modifier.testTag(TAG_PHOTO_HEADER),
            menuContent = { PhotoContextMenuItems(args) },
            primaryIcon = primaryIcon,
            primaryLabel = primaryLabel,
            onPrimaryAction = onPrimaryAction,
            primaryEnabled = primaryEnabled,
            primaryTag = TAG_TOOL_APPLY
        )
    }
}

/**
 * Extra More-menu actions a live tool publishes — the compact tool
 * surface's one secondary surface. Fields are plain mutable state the
 * tool content refreshes on composition and clears on disposal.
 */
internal class PhotoToolMenuState {
    // Observable: the crop actions live outside the card's subtree, so a
    // plain var would leave their enabled state stale after an edit.
    var resetParameterLabel by mutableStateOf<String?>(null)
    var resetParameterTag by mutableStateOf("editor.reset.param")
    // The floating card's foot action. It has its own tag so a tool that
    // also paints an inline reset never exposes two nodes with one tag
    // (the inline control and the foot are different targets).
    var resetParameterFooterTag by mutableStateOf("editor.reset.param.foot")
    var resetParameterEnabled by mutableStateOf(false)
    var onResetParameter by mutableStateOf<(() -> Unit)?>(null)
    // The instance that published the reset: the Light card swaps between
    // its full and focus forms, and the departing form must not clear the
    // arriving form's reset on disposal.
    var resetSource by mutableStateOf<Any?>(null)
    // A tool's second published action — Blend's View original compare
    // toggle lives here instead of a visible inspector row.
    var compareLabel by mutableStateOf<String?>(null)
    var compareTag by mutableStateOf("editor.compare.param")
    var compareEnabled by mutableStateOf(false)
    var onCompare by mutableStateOf<(() -> Unit)?>(null)
}

/** Present only inside the compact tool inspector. */
internal val LocalPhotoToolMenu =
    compositionLocalOf<PhotoToolMenuState?> { null }

/** The More menu of a compact context header — real secondary surfaces. */
@Composable
private fun ColumnScope.PhotoContextMenuItems(args: PhotoCompactArgs) {
    PhotoHeaderMenuItems(
        toolMenu = args.toolMenu,
        state = args.state,
        actions = args.actions,
        workspace = args.workspace,
        imageCapable = args.imageCapable
    )
}

/**
 * The real secondary surfaces a photo header's More menu carries: the live
 * tool's published reset, its published compare toggle, the Source and
 * Edits panes and the whole-recipe reset. Shared by the compact context
 * header and the tablet document capsule so the two menus never drift.
 */
@Composable
internal fun ColumnScope.PhotoHeaderMenuItems(
    toolMenu: PhotoToolMenuState,
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    workspace: EditorWorkspaceState,
    imageCapable: Boolean,
    onDismiss: (() -> Unit)? = null
) {
    val resetLabel = toolMenu.resetParameterLabel
    val resetAction = toolMenu.onResetParameter
    if (resetLabel != null && resetAction != null) {
        // The visible Light row drops its reset icon per p-light — the
        // action lives here instead, per-parameter.
        DropdownMenuItem(
            text = { Text(resetLabel) },
            enabled = toolMenu.resetParameterEnabled,
            onClick = {
                resetAction()
                onDismiss?.invoke()
            },
            modifier = Modifier.testTag(toolMenu.resetParameterTag)
        )
    }
    val compareLabel = toolMenu.compareLabel
    val compareAction = toolMenu.onCompare
    if (compareLabel != null && compareAction != null) {
        DropdownMenuItem(
            text = { Text(compareLabel) },
            enabled = toolMenu.compareEnabled,
            onClick = {
                compareAction()
                onDismiss?.invoke()
            },
            modifier = Modifier.testTag(toolMenu.compareTag)
        )
    }
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.editor_panel_source)) },
        enabled = imageCapable,
        onClick = {
            workspace.togglePanel(EditorWorkspacePanel.Source)
            onDismiss?.invoke()
        },
        modifier = Modifier.testTag(TAG_PANEL_SOURCE)
    )
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.editor_panel_edits)) },
        enabled = imageCapable,
        onClick = {
            workspace.togglePanel(EditorWorkspacePanel.Edits)
            onDismiss?.invoke()
        },
        modifier = Modifier.testTag(TAG_PANEL_EDITS)
    )
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.editor_reset_edits)) },
        enabled = state.document != null && !state.interactionActive,
        onClick = {
            actions.reset()
            onDismiss?.invoke()
        },
        modifier = Modifier.testTag(TAG_RESET_ALL)
    )
    PhotoExportMenuItems(actions) { onDismiss?.invoke() }
}

/**
 * One compact canvas call — the media field as the V3 card (4 dp off
 * the window surface, 12 dp corners on the always-dark field) with the
 * state's insets, the shader/bitmap swap and the workspace keyboard
 * focus all wired identically in every phone surface.
 */
@Composable
private fun ColumnScope.CompactCanvas(
    args: PhotoCompactArgs,
    mediaPadding: PaddingValues,
    showFooter: Boolean,
    showBeforeChip: Boolean,
    mediaCornerRadius: Dp = EditorV2Layout.PhotoCorner,
    // The tool surface fits its image in the strip above the floating
    // card, so the field insets and the crop corner travel through.
    fitFieldInsets: PaddingValues = PaddingValues.Zero,
    expandFieldOnZoom: Boolean = false,
    fieldCornerRadius: Dp = 0.dp,
    surroundColor: Color? = null,
    imageOverlay: @Composable BoxScope.(EditorImageLayout) -> Unit = {},
    // Canvas-region chrome above the media (the histogram pill the
    // adjust tools float over their photo) — full BoxScope, not tied
    // to the image layout.
    canvasOverlay: @Composable BoxScope.() -> Unit = {}
) {
    CompactCanvasCell(
        args = args,
        mediaPadding = mediaPadding,
        showFooter = showFooter,
        showBeforeChip = showBeforeChip,
        mediaCornerRadius = mediaCornerRadius,
        fitFieldInsets = fitFieldInsets,
        expandFieldOnZoom = expandFieldOnZoom,
        fieldCornerRadius = fieldCornerRadius,
        surroundColor = surroundColor,
        imageOverlay = imageOverlay,
        canvasOverlay = canvasOverlay,
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            // The region tag wraps the whole canvas cell — the 4 dp
            // card margin stays inside it so bounds report the
            // layout region, not the inset card. No cell-level clip:
            // the card's rounded corners are painted by the backdrop
            // and media themselves inside EditorCanvas, and a clip
            // here would shave the hit region off the interactive
            // overlays (the layer transform's 48 dp corner handles)
            // wherever a handle rides a card edge.
            .testTag(TAG_PHOTO_CANVAS)
            .padding(EditorV2Layout.PhoneFieldMargin)
    )
}

/**
 * The scope-free canvas cell — the v4 home hosts it inside its field
 * Box with the floating chrome over it; the other surfaces keep the
 * [CompactCanvas] ColumnScope wrapper.
 */
@Composable
private fun CompactCanvasCell(
    args: PhotoCompactArgs,
    mediaPadding: PaddingValues,
    showFooter: Boolean,
    showBeforeChip: Boolean,
    mediaCornerRadius: Dp = EditorV2Layout.PhotoCorner,
    // The v4 home lifts the card inside the canvas region so the
    // floating chrome lanes stay clear at fit — and lets the card clip
    // expand to the region edge once the zoom passes 1x.
    fitFieldInsets: PaddingValues = PaddingValues.Zero,
    // The crop frame's own field: it keeps clearing the action row and the
    // card while the media itself runs full bleed behind the chrome.
    frameFieldInsets: PaddingValues = fitFieldInsets,
    expandFieldOnZoom: Boolean = false,
    fieldCornerRadius: Dp = 0.dp,
    surroundColor: Color? = null,
    modifier: Modifier = Modifier,
    splitLabelBottomInset: Dp = 0.dp,
    imageOverlay: @Composable BoxScope.(EditorImageLayout) -> Unit = {},
    canvasOverlay: @Composable BoxScope.() -> Unit = {}
) {
    with(args) {
        EditorCanvas(
            state = state,
            actions = actions,
            viewport = viewport,
            bitmap = bitmap,
            imageDescription = imageDescription,
            compactUtility = true,
            compactUtilityLabels = true,
            showFooter = showFooter,
            mediaPadding = mediaPadding,
            mediaCornerRadius = mediaCornerRadius,
            fitFieldInsets = fitFieldInsets,
            frameFieldInsets = frameFieldInsets,
            expandFieldOnZoom = expandFieldOnZoom,
            fieldCornerRadius = fieldCornerRadius,
            surroundColor = surroundColor,
            showBeforeChip = showBeforeChip,
            splitLabelBottomInset = splitLabelBottomInset,
            imageOverlay = imageOverlay,
            overlay = canvasOverlay,
            focusRequester = canvasFocusRequester,
            modifier = modifier,
            gpuRenderer = if (shaderDrawing) shaderRenderer else null,
            gpuEdits = if (shaderDrawing) shaderEdits else null,
            gpuImageSize = if (shaderDrawing) shaderImageSize else null,
            onGpuFrameFailed = onGpuFrameFailed
        )
    }
}

/** The 32 dp context line under the canvas — layers glyph + section title.
 * On a tool surface it floats as a pill over the photo ([floating]); on the
 * Source/Edits panes it stays a plain section line under the canvas. */
@Composable
private fun PhotoBreadcrumb(label: String, floating: Boolean = false) {
    Row(
        modifier = Modifier
            .then(
                if (floating) {
                    Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .chromeBlur(RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp)
                }
            )
            .height(EditorV2Layout.PhoneBreadcrumb)
            .testTag(TAG_PHOTO_BREADCRUMB),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = EditorSymbols.Layers,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

/**
 * One dispatch for the contextual tools' canvas layer: Transform owns
 * the interactive quad, the mask owns the brush surface, and Blend /
 * Double exposure keep the selection outline display-only — every
 * surface nulls itself without a visible selected layer.
 */
@Composable
internal fun LayerToolCanvasOverlay(
    layout: EditorImageLayout,
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    workspace: EditorWorkspaceState,
    toolUiState: PhotoToolUiState,
    tool: EditorToolId,
    toolTitle: String? = null
) {
    val title = toolTitle ?: photoToolTitle(tool)
    when (tool) {
        EditorToolIds.PhotoTransform -> LayerTransformOverlay(
            layout = layout,
            state = state,
            actions = actions,
            selectedLayerId = workspace.selectedLayerId,
            toolTitle = title
        )
        EditorToolIds.PhotoLayerMask -> LayerMaskBrushSurface(
            layout = layout,
            state = state,
            actions = actions,
            selectedLayerId = workspace.selectedLayerId,
            toolUiState = toolUiState,
            toolTitle = title
        )
        else -> PhotoLayerSelectionOverlay(
            layout = layout,
            state = state,
            selectedLayerId = workspace.selectedLayerId
        )
    }
}

/**
 * The V3 flat inline inspector: an ordinary `surface` panel with a 1 dp
 * `outlineVariant` top divider — no rounded top, no drag handle —
 * capped at 450 dp so the canvas keeps its field above. The content
 * region scrolls unless the panel owns its own scrolling (the Layers
 * list).
 */
@Composable
private fun PhotoInlineInspector(
    args: PhotoCompactArgs,
    contentScrollable: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = EditorV2Layout.PhoneInlineInspectorMax)
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .editorSheetBlur(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .testTag(TAG_V3_INLINE_INSPECTOR)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clipToBounds()
                        .then(
                            if (contentScrollable) {
                                Modifier.verticalScroll(
                                    rememberScrollState()
                                )
                            } else {
                                Modifier
                            }
                        )
                ) {
                    CompositionLocalProvider(
                        LocalPhotoToolMenu provides args.toolMenu
                    ) {
                        content()
                    }
                }
            }
        }
    }
}
