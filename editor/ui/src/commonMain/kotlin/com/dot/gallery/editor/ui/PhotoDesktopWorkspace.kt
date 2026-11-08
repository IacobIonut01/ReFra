package com.dot.gallery.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.editor.core.model.PhotoBlendMode
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoImageLayer
import com.dot.gallery.editor.core.model.PhotoLayerMask
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.render.photo.PhotoExportResult
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_apply
import com.dot.gallery.editor.ui.generated.resources.editor_back
import com.dot.gallery.editor.ui.generated.resources.editor_layer_add_image
import com.dot.gallery.editor.ui.generated.resources.editor_layer_align_centre
import com.dot.gallery.editor.ui.generated.resources.editor_layer_blend
import com.dot.gallery.editor.ui.generated.resources.editor_layer_effects
import com.dot.gallery.editor.ui.generated.resources.editor_layer_opacity
import com.dot.gallery.editor.ui.generated.resources.editor_layer_position
import com.dot.gallery.editor.ui.generated.resources.editor_layer_section
import com.dot.gallery.editor.ui.generated.resources.editor_layers
import com.dot.gallery.editor.ui.generated.resources.editor_flip_horizontal
import com.dot.gallery.editor.ui.generated.resources.editor_flip_vertical
import com.dot.gallery.editor.ui.generated.resources.editor_focus
import com.dot.gallery.editor.ui.generated.resources.editor_panel_edit
import com.dot.gallery.editor.ui.generated.resources.editor_panel_edits
import com.dot.gallery.editor.ui.generated.resources.editor_panel_source
import com.dot.gallery.editor.ui.generated.resources.editor_reset_edits
import com.dot.gallery.editor.ui.generated.resources.editor_save_copy
import com.dot.gallery.editor.ui.generated.resources.editor_segment_compare
import com.dot.gallery.editor.ui.generated.resources.editor_title_edited
import com.dot.gallery.editor.ui.generated.resources.editor_tool_mask
import com.dot.gallery.editor.ui.generated.resources.editor_transform_rotation
import com.dot.gallery.editor.ui.generated.resources.editor_transform_x
import com.dot.gallery.editor.ui.generated.resources.editor_transform_y
import com.dot.gallery.editor.ui.generated.resources.editor_value_edited
import com.dot.gallery.editor.ui.generated.resources.editor_value_none
import com.dot.gallery.editor.ui.generated.resources.editor_zoom_fit
import com.dot.gallery.editor.ui.icons.AddPhotoAlternate
import com.dot.gallery.editor.ui.icons.ArrowBack
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.FitScreen
import com.dot.gallery.editor.ui.icons.Flip
import com.dot.gallery.editor.ui.icons.Fullscreen
import com.dot.gallery.editor.ui.icons.Layers
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

internal const val TAG_DESKTOP_RAIL = "editor.desktop.rail"
internal const val TAG_DESKTOP_LAYERS_PANE = "editor.desktop.layers"
internal const val TAG_DESKTOP_PROPS_PANE = "editor.desktop.props"
internal const val TAG_DESKTOP_SEGMENT = "editor.desktop.segment"
internal const val TAG_DESKTOP_SEGMENT_EDIT = "editor.desktop.segment.edit"
internal const val TAG_DESKTOP_SEGMENT_LAYERS = "editor.desktop.segment.layers"
internal const val TAG_DESKTOP_SEGMENT_COMPARE = "editor.desktop.segment.compare"
internal const val TAG_DESKTOP_ZOOM = "editor.desktop.zoom"
internal const val TAG_DESKTOP_ZOOM_FIT = "editor.desktop.zoom.fit"
internal const val TAG_DESKTOP_PROP_OPACITY = "editor.desktop.prop.opacity"
internal const val TAG_DESKTOP_PROP_BLEND = "editor.desktop.prop.blend"
internal const val TAG_DESKTOP_PROP_POSITION = "editor.desktop.prop.position"
internal const val TAG_DESKTOP_PROP_ROTATION = "editor.desktop.prop.rotation"
internal const val TAG_DESKTOP_PROP_ALIGN = "editor.desktop.prop.align"
internal const val TAG_DESKTOP_PROP_FLIP_H = "editor.desktop.prop.flipH"
internal const val TAG_DESKTOP_PROP_FLIP_V = "editor.desktop.prop.flipV"
internal const val TAG_DESKTOP_PROP_MASK = "editor.desktop.prop.mask"
internal const val TAG_DESKTOP_PROP_EFFECTS = "editor.desktop.prop.effects"
internal const val TAG_DESKTOP_PROP_RESET = "editor.desktop.prop.reset"
internal const val TAG_DESKTOP_BLEND_MENU = "editor.desktop.blend.menu"

/**
 * The shared inputs of the four-pane desktop composition — the same
 * session/canvas wiring the compact and wide states consume, plus the
 * callbacks the header segment, rail and property rows route through.
 */
internal class PhotoDesktopArgs(
    val state: PhotoEditorState,
    val actions: PhotoEditorActions,
    val workspace: EditorWorkspaceState,
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
    val histogramPrefs: PhotoHistogramPrefs,
    val toolEntries: List<EditorToolEntry<PhotoEditorState, PhotoEditorActions>>,
    val toolMenu: PhotoToolMenuState,
    val onSelectTool: (EditorToolId) -> Unit,
    val onRequestClose: () -> Unit,
    val onOpenExport: ((PhotoExportResult) -> Unit)?,
    val onCanvasRefocus: () -> Unit
)

/**
 * The `x-photo-desktop` composition: the 64 dp header (Back, dirty-aware
 * source name, the centred Edit/Layers/Compare segment and Save copy)
 * over a body of rail 80 | canvas weight | layers 280 | properties 304.
 * There is no collapsed single-inspector state — the rail and both panes
 * are always present; the segment and rail choose *which* content the
 * properties pane shows, never whether it exists.
 */
@Composable
internal fun PhotoDesktopWorkspace(args: PhotoDesktopArgs) {
    Column(Modifier.fillMaxSize()) {
        DesktopHeader(args)
        StatusStrips(
            state = args.state,
            actions = args.actions,
            onOpenExport = args.onOpenExport
        )
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            DesktopToolRail(args)
            DesktopCanvas(args)
            DesktopLayersPane(args)
            DesktopPropertiesPane(args)
        }
    }
}

/** The Edit/Layers/Compare mode the segment reflects. */
private enum class DesktopSegment { Edit, Layers, Compare }

/** The header segment's active cell from the live workspace/viewport. */
private fun desktopSegment(args: PhotoDesktopArgs): DesktopSegment = when {
    args.viewport.compareSplit -> DesktopSegment.Compare
    args.workspace.panel == EditorWorkspacePanel.Layers -> DesktopSegment.Layers
    else -> DesktopSegment.Edit
}

private fun selectEditSurface(args: PhotoDesktopArgs) {
    args.viewport.compareSplit = false
    args.workspace.panel = EditorWorkspacePanel.Edit
    args.workspace.toolPanelExpanded = true
    args.onCanvasRefocus()
}

private fun selectLayersSurface(args: PhotoDesktopArgs) {
    args.viewport.compareSplit = false
    args.workspace.panel = EditorWorkspacePanel.Layers
    args.workspace.toolPanelExpanded = true
    // A dead selection resolves to the topmost layer — the same
    // fallback the compact Layers workspace applies.
    val layers = args.state.document?.layers.orEmpty()
    if (layers.none { it.id == args.workspace.selectedLayerId }) {
        args.workspace.selectedLayerId = layers.lastOrNull()?.id
    }
    args.onCanvasRefocus()
}

/**
 * The 64 dp desktop header — Back, the source name with a `· Edited`
 * suffix while dirty, the centred 240×40 three-cell segment and the one
 * filled Save copy action. Undo/Redo stay keyboard-only here; the reset
 * lives in the properties pane.
 */
@Composable
private fun DesktopHeader(args: PhotoDesktopArgs) {
    val displayName = args.state.info?.displayName.orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EditorV2Layout.Header)
            .padding(horizontal = 16.dp)
            .testTag(TAG_PHOTO_HEADER),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        WorkbenchIconButton(
            icon = EditorSymbols.ArrowBack,
            label = stringResource(Res.string.editor_back),
            onClick = args.onRequestClose,
            modifier = Modifier.testTag(TAG_BACK)
        )
        Text(
            text = if (args.state.isDirty) {
                stringResource(Res.string.editor_title_edited, displayName)
            } else {
                displayName
            },
            style = MaterialTheme.typography.titleMedium,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // The segment centres in the space the title and Save leave —
        // it anchors to neither edge.
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            DesktopSegment(args)
        }
        WorkbenchPrimaryAction(
            label = stringResource(Res.string.editor_save_copy),
            onClick = args.actions.saveCopy,
            enabled = args.state.document != null && !args.state.exporting &&
                !args.state.isDrafting && !args.state.opening,
            modifier = Modifier.testTag(TAG_SAVE)
        )
    }
}

/** The centred 240×40 Edit/Layers/Compare segment, 2 dp inner padding. */
@Composable
private fun DesktopSegment(args: PhotoDesktopArgs) {
    val active = desktopSegment(args)
    Row(
        modifier = Modifier
            .size(240.dp, 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .testTag(TAG_DESKTOP_SEGMENT)
            .padding(2.dp)
    ) {
        DesktopSegmentCell(
            label = stringResource(Res.string.editor_panel_edit),
            tag = TAG_DESKTOP_SEGMENT_EDIT,
            selected = active == DesktopSegment.Edit,
            enabled = args.imageCapable
        ) { selectEditSurface(args) }
        DesktopSegmentCell(
            label = stringResource(Res.string.editor_layers),
            tag = TAG_DESKTOP_SEGMENT_LAYERS,
            selected = active == DesktopSegment.Layers,
            enabled = args.imageCapable
        ) { selectLayersSurface(args) }
        DesktopSegmentCell(
            label = stringResource(Res.string.editor_segment_compare),
            tag = TAG_DESKTOP_SEGMENT_COMPARE,
            selected = active == DesktopSegment.Compare,
            enabled = args.imageCapable &&
                args.state.originalPreview != null
        ) { args.viewport.compareSplit = !args.viewport.compareSplit }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.DesktopSegmentCell(
    label: String,
    tag: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                }
            )
            .clickable(
                enabled = enabled,
                role = Role.Tab,
                onClickLabel = label
            ) { onClick() }
            .semantics { this.selected = selected }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1
        )
    }
}

/**
 * The 80 dp desktop tool rail — the Layers destination first, then the
 * catalogued tools, then Focus: 56×56 icon-only cells at an 8 dp pitch
 * under a 12 dp head start, selected on `secondaryContainer` with 16 dp
 * corners. Unavailable contextual tools disable rather than hide.
 */
@Composable
private fun DesktopToolRail(args: PhotoDesktopArgs) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .verticalScroll(rememberScrollState())
            .padding(top = 12.dp, bottom = 12.dp)
            .testTag(TAG_DESKTOP_RAIL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DesktopRailCell(
            icon = EditorSymbols.Layers,
            label = stringResource(Res.string.editor_layers),
            tag = TAG_LAYERS,
            enabled = args.imageCapable,
            selected = args.workspace.panel == EditorWorkspacePanel.Layers
        ) { selectLayersSurface(args) }
        args.toolEntries.filter { it.catalogued }.forEach { entry ->
            DesktopRailCell(
                icon = entry.descriptor.icon,
                label = stringResource(entry.descriptor.title),
                tag = entry.tag,
                enabled = args.imageCapable && entry.available(args.state),
                selected = args.workspace.panel == EditorWorkspacePanel.Edit &&
                    args.workspace.activeTool == entry.descriptor.id
            ) { args.onSelectTool(entry.descriptor.id) }
        }
        DesktopRailCell(
            icon = EditorSymbols.Fullscreen,
            label = stringResource(Res.string.editor_focus),
            tag = TAG_FOCUS,
            enabled = args.imageCapable,
            selected = null
        ) { args.workspace.focusMode = true }
    }
}

/** One 56×56 icon-only rail cell — tooltip, tint and selected field. */
@Composable
private fun DesktopRailCell(
    icon: ImageVector,
    label: String,
    tag: String,
    enabled: Boolean,
    selected: Boolean?,
    onClick: () -> Unit
) {
    WorkbenchTooltip(label) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .testTag(tag)
                .then(
                    if (selected != null) {
                        Modifier.semantics { this.selected = selected }
                    } else {
                        Modifier
                    }
                )
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (selected == true) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    }
                )
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = label
                ) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface
                        .copy(alpha = 0.38f)
                    selected == true ->
                        MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * The desktop canvas column — the full composite/shader on the dark
 * surround, no footer, the media fit inside a 620 dp region centred at
 * body-y 120, and the bottom-left 120×40 zoom field. The layer overlay
 * is interactive for the contextual tools and a display outline on the
 * Layers surface.
 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.DesktopCanvas(
    args: PhotoDesktopArgs
) {
    with(args) {
        EditorCanvas(
            state = state,
            actions = actions,
            viewport = viewport,
            bitmap = bitmap,
            imageDescription = imageDescription,
            compactUtility = false,
            showFooter = false,
            mediaPadding = PaddingValues(top = 120.dp, bottom = 156.dp),
            focusRequester = canvasFocusRequester,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag(TAG_PHOTO_CANVAS),
            gpuRenderer = if (shaderDrawing) shaderRenderer else null,
            gpuEdits = if (shaderDrawing) shaderEdits else null,
            gpuImageSize = if (shaderDrawing) shaderImageSize else null,
            onGpuFrameFailed = onGpuFrameFailed,
            imageOverlay = { layout ->
                PhotoCanvasToolOverlay(
                    layout = layout,
                    state = state,
                    actions = actions,
                    workspace = workspace,
                    toolUiState = toolUiState,
                    viewport = viewport,
                    onCanvasRefocus = onCanvasRefocus
                )
            },
            overlay = {
                WorkbenchCanvasZoomField(
                    viewport = viewport,
                    tag = TAG_DESKTOP_ZOOM,
                    fitTag = TAG_DESKTOP_ZOOM_FIT
                )
            }
        )
    }
}

/**
 * The contextual tool overlay painted over the fit image box — the crop
 * window, the white-balance picker, the lens-blur handles and the
 * composition tools' layer overlays. Compare states paint nothing: the
 * split is the read-out, not an editing surface.
 */
@Composable
internal fun BoxScope.PhotoCanvasToolOverlay(
    layout: EditorImageLayout,
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    workspace: EditorWorkspaceState,
    toolUiState: PhotoToolUiState,
    viewport: EditorViewportState,
    onCanvasRefocus: () -> Unit
) {
    when {
        viewport.compareBefore || viewport.compareSplit -> Unit
        workspace.panel == EditorWorkspacePanel.Layers ->
            PhotoLayerSelectionOverlay(
                layout = layout,
                state = state,
                selectedLayerId = workspace.selectedLayerId
            )
        workspace.panel == EditorWorkspacePanel.Edit &&
            (workspace.activeTool == EditorToolIds.PhotoCrop ||
                workspace.activeTool == EditorToolIds.PhotoPerspective) ->
            EditorCropWindow(
                layout = layout,
                state = state,
                actions = actions,
                upright = state.info?.uprightSize,
                onCommit = {
                    actions.applyToolEdit()
                    onCanvasRefocus()
                }
            )
        workspace.panel == EditorWorkspacePanel.Edit &&
            photoToolOwnsWhiteBalancePick(workspace.activeTool) &&
            toolUiState.whiteBalancePicking ->
            WhiteBalancePickOverlay(
                layout = layout,
                state = state,
                actions = actions,
                toolUiState = toolUiState,
                viewport = viewport
            )
        workspace.panel == EditorWorkspacePanel.Edit &&
            workspace.activeTool == EditorToolIds.PhotoLensBlur ->
            LensBlurOverlay(
                layout = layout,
                state = state,
                actions = actions
            )
        workspace.panel == EditorWorkspacePanel.Edit &&
            workspace.activeTool in RETOUCH_CANVAS_TOOLS ->
            RetouchCanvasOverlay(
                layout = layout,
                state = state,
                actions = actions,
                toolUiState = toolUiState,
                tool = workspace.activeTool
            )
        workspace.panel == EditorWorkspacePanel.Edit &&
            workspace.activeTool in CONTEXTUAL_PHOTO_TOOLS ->
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

/**
 * The bottom-left zoom field — percent readout plus the Fit action. Shared
 * by the four-pane workbench and the tablet master-detail canvas, each
 * passing its own tag.
 */
@Composable
internal fun BoxScope.WorkbenchCanvasZoomField(
    viewport: EditorViewportState,
    tag: String,
    fitTag: String = "$tag.fit"
) {
    val fitLabel = stringResource(Res.string.editor_zoom_fit)
    EditorFloatingPanel(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = 16.dp, bottom = 16.dp)
            .size(120.dp, 40.dp)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatZoomPercent(viewport.zoom),
                style = MaterialTheme.typography.labelLarge,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            )
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                    .clickable(
                        role = Role.Button,
                        onClickLabel = fitLabel
                    ) {
                        viewport.zoom = 1f
                        viewport.panX = 0f
                        viewport.panY = 0f
                    }
                    .testTag(fitTag),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = EditorSymbols.FitScreen,
                    contentDescription = fitLabel,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * The 280 dp layers pane — title16, the desktop `PhotoLayersPanel`
 * variant (72 dp rows at an 8 dp pitch, no phone toolbar) and the
 * outlined 260×48 Add image pinned after the list.
 */
@Composable
private fun DesktopLayersPane(args: PhotoDesktopArgs) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
            .testTag(TAG_DESKTOP_LAYERS_PANE)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 10.dp, top = 16.dp, end = 10.dp, bottom = 16.dp)
        ) {
            Text(
                text = stringResource(Res.string.editor_layers),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            PhotoLayersPanel(
                state = args.state,
                actions = args.actions,
                workspace = args.workspace,
                baseThumbnail = args.originalBitmap ?: args.bitmap,
                lazyList = false,
                chrome = false,
                rowHeight = 72.dp,
                listSpacing = 8.dp,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                onClick = args.actions.requestAddImage,
                enabled = canAddLayerImage(args.state),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .width(260.dp)
                    .height(48.dp)
                    .testTag(TAG_LAYER_ADD)
            ) {
                Icon(
                    imageVector = EditorSymbols.AddPhotoAlternate,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(Res.string.editor_layer_add_image),
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

/**
 * The 304 dp properties pane — the selected layer's real property rows
 * on the Layers surface (title15 + the 48 dp field rows), the active
 * tool's controls on the Edit surface, the Source/Edits panes on theirs,
 * and the open transaction's Cancel/Apply pinned at the bottom. The
 * tool-published menu actions (per-parameter reset, compare toggle) and
 * the global Reset edits stay reachable at the column's foot.
 */
@Composable
private fun DesktopPropertiesPane(args: PhotoDesktopArgs) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .width(304.dp)
            .fillMaxHeight()
            .testTag(TAG_DESKTOP_PROPS_PANE)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 14.dp, bottom = 14.dp)
            ) {
                when (args.workspace.panel) {
                    EditorWorkspacePanel.Layers -> DesktopLayerProps(args)
                    EditorWorkspacePanel.Source -> {
                        DesktopPropsTitle(
                            stringResource(Res.string.editor_panel_source)
                        )
                        PhotoSourcePanel(
                            state = args.state,
                            originalBitmap = args.originalBitmap
                        )
                    }
                    EditorWorkspacePanel.Edits -> {
                        DesktopPropsTitle(
                            stringResource(Res.string.editor_panel_edits)
                        )
                        PhotoEditsPanel(
                            state = args.state,
                            actions = args.actions
                        )
                    }
                    EditorWorkspacePanel.Edit -> {
                        DesktopPropsTitle(photoToolTitle(args.workspace.activeTool))
                        CompositionLocalProvider(
                            LocalPhotoToolMenu provides args.toolMenu
                        ) {
                            PhotoToolPanel(
                                state = args.state,
                                actions = args.actions,
                                tool = args.workspace.activeTool
                            )
                        }
                    }
                }
                // Resets live with the tool surface they reset — the
                // Edit segment's real tool props. The layer, source and
                // edits properties end at their own content (Figma's
                // props bottom is the Effects row), so a stale tool
                // action never lingers under another segment.
                if (args.workspace.panel == EditorWorkspacePanel.Edit) {
                    DesktopPropsActions(args)
                }
            }
            if (args.state.toolEditing) {
                EditorToolActionsBar(
                    onCancel = {
                        args.actions.cancelToolEdit()
                        args.onCanvasRefocus()
                    },
                    onApply = {
                        args.actions.applyToolEdit()
                        args.onCanvasRefocus()
                    },
                    applyEnabled = !args.state.interactionActive
                )
            }
        }
    }
}

@Composable
private fun DesktopPropsTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontSize = 15.sp,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 4.dp)
    )
}

/**
 * The tool-published and global resets at the foot of the *tool*
 * properties scroll — emitted only while the Edit segment's real tool
 * surface owns the pane. The layer properties end at Effects, so no
 * reset reads on a surface it doesn't reset; undo/redo stay on the
 * keyboard in every mode.
 */
@Composable
private fun DesktopPropsActions(args: PhotoDesktopArgs) {
    Column(modifier = Modifier.padding(horizontal = 14.dp)) {
        args.toolMenu.resetParameterLabel?.let { label ->
            TextButton(
                onClick = { args.toolMenu.onResetParameter?.invoke() },
                enabled = args.toolMenu.resetParameterEnabled,
                modifier = Modifier.testTag(args.toolMenu.resetParameterTag)
            ) {
                Text(label, maxLines = 1)
            }
        }
        args.toolMenu.compareLabel?.let { label ->
            TextButton(
                onClick = { args.toolMenu.onCompare?.invoke() },
                enabled = args.toolMenu.compareEnabled,
                modifier = Modifier.testTag(args.toolMenu.compareTag)
            ) {
                Text(label, maxLines = 1)
            }
        }
        TextButton(
            onClick = args.actions.reset,
            enabled = args.state.document != null && !args.state.interactionActive,
            modifier = Modifier.testTag(TAG_DESKTOP_PROP_RESET)
        ) {
            Text(stringResource(Res.string.editor_reset_edits), maxLines = 1)
        }
    }
}

/** The exact-entry field an open dialog is editing, or null. */
private enum class DesktopPropField { Opacity, Position, Rotation }

/**
 * The selected layer's property surface — the p-desktop rows: Opacity
 * (0..100 %), Blend (the five modes through a dropdown), Position
 * (X·Y pair in output px), Rotation (±180°), the three equal align/flip
 * cells, then the Layer section's Mask (None/Edited → opens the mask
 * tool) and Effects (None, honestly disabled until effects exist). Every
 * mutation lands through `applyLayerEdit` — the same apply seam the
 * tools use, so it joins an open tool transaction as one local step and
 * otherwise records one global undo step.
 */
@Composable
private fun DesktopLayerProps(args: PhotoDesktopArgs) {
    val layer = resolveSelectedLayer(args.state, args.workspace.selectedLayerId)
    if (layer == null) {
        // The base's honest properties — the source name and the real
        // source/output sizes plus the shared reset footer.
        DesktopPropsTitle(args.state.info?.displayName.orEmpty())
        PhotoInspectorFooter(args.state, args.actions)
        return
    }
    val state = args.state
    val actions = args.actions
    val layerId = layer.id
    val outputSize = state.baseOutputSize()
    val canEdit = !layer.locked && state.document != null &&
        outputSize != null && !state.opening
    val enabled = canEdit && !state.interactionActive
    val sourceKey = state.info?.reference

    val owner = remember { NumericEntryOwner() }
    NumericEntryOwnerEffects(owner, state, actions)
    var dialogField by remember { mutableStateOf<DesktopPropField?>(null) }
    var blendMenuOpen by remember { mutableStateOf(false) }

    val outW = outputSize?.width?.toFloat() ?: 0f
    val outH = outputSize?.height?.toFloat() ?: 0f
    val xRange = -outW..outW
    val yRange = -outH..outH
    val rotationRange = -180f..180f
    val opacityLabel = stringResource(Res.string.editor_layer_opacity)
    val positionLabel = stringResource(Res.string.editor_layer_position)
    val rotationLabel = stringResource(Res.string.editor_transform_rotation)
    val xyLabel = stringResource(Res.string.editor_transform_x) + " · " +
        stringResource(Res.string.editor_transform_y)

    fun openDialog(field: DesktopPropField) {
        if (!enabled) return
        try {
            actions.beginEdit(
                when (field) {
                    DesktopPropField.Opacity -> "Layer opacity"
                    DesktopPropField.Position -> "Move layer"
                    DesktopPropField.Rotation -> "Rotate layer"
                }
            )
        } catch (rejected: IllegalStateException) {
            return
        }
        owner.open(sourceKey)
        dialogField = field
    }

    Column(
        modifier = Modifier.padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = layer.name,
            style = MaterialTheme.typography.titleSmall,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        DesktopPropRow(
            label = opacityLabel,
            value = "${(layer.opacity * 100f).roundToInt()}%",
            fieldWidth = 88.dp,
            enabled = enabled,
            tag = TAG_DESKTOP_PROP_OPACITY,
            onClick = { openDialog(DesktopPropField.Opacity) },
            onKey = { key, shift ->
                val step = if (shift) 10f else 1f
                val next = when (key) {
                    Key.DirectionLeft, Key.DirectionDown ->
                        layer.opacity * 100f - step
                    Key.DirectionRight, Key.DirectionUp ->
                        layer.opacity * 100f + step
                    Key.MoveHome -> 0f
                    Key.MoveEnd -> 100f
                    else -> return@DesktopPropRow false
                }
                actions.applyLayerEdit(state, layerId, opacityLabel) {
                    it.copy(opacity = (next / 100f).coerceIn(0f, 1f))
                }
                true
            }
        )
        Box {
            DesktopPropRow(
                label = stringResource(Res.string.editor_layer_blend),
                value = blendModeTitle(layer.blendMode),
                fieldWidth = 88.dp,
                enabled = enabled,
                tag = TAG_DESKTOP_PROP_BLEND,
                onClick = { if (enabled) blendMenuOpen = true },
                onKey = { _, _ -> false }
            )
            DropdownMenu(
                expanded = blendMenuOpen,
                onDismissRequest = { blendMenuOpen = false },
                modifier = Modifier.testTag(TAG_DESKTOP_BLEND_MENU)
            ) {
                PhotoBlendMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(blendModeTitle(mode)) },
                        onClick = {
                            blendMenuOpen = false
                            actions.applyLayerEdit(
                                state, layerId, "Blend mode"
                            ) { it.copy(blendMode = mode) }
                        }
                    )
                }
            }
        }
        DesktopPropRow(
            label = positionLabel,
            value = "${layer.transform.translationX.roundToInt()} px · " +
                "${layer.transform.translationY.roundToInt()} px",
            fieldWidth = 94.dp,
            enabled = enabled,
            tag = TAG_DESKTOP_PROP_POSITION,
            onClick = { openDialog(DesktopPropField.Position) },
            onKey = { key, shift ->
                val step = if (shift) 10f else 1f
                val (dx, dy) = when (key) {
                    Key.DirectionLeft -> -step to 0f
                    Key.DirectionRight -> step to 0f
                    Key.DirectionUp -> 0f to -step
                    Key.DirectionDown -> 0f to step
                    else -> return@DesktopPropRow false
                }
                actions.applyLayerEdit(state, layerId, positionLabel) {
                    it.copy(
                        transform = it.transform.copy(
                            translationX = (it.transform.translationX + dx)
                                .coerceIn(xRange.start, xRange.endInclusive),
                            translationY = (it.transform.translationY + dy)
                                .coerceIn(yRange.start, yRange.endInclusive)
                        )
                    )
                }
                true
            }
        )
        DesktopPropRow(
            label = rotationLabel,
            value = "${layer.transform.rotationDegrees.roundToInt()}°",
            fieldWidth = 88.dp,
            enabled = enabled,
            tag = TAG_DESKTOP_PROP_ROTATION,
            onClick = { openDialog(DesktopPropField.Rotation) },
            onKey = { key, shift ->
                val step = if (shift) 10f else 1f
                val next = when (key) {
                    Key.DirectionLeft, Key.DirectionDown ->
                        layer.transform.rotationDegrees - step
                    Key.DirectionRight, Key.DirectionUp ->
                        layer.transform.rotationDegrees + step
                    Key.MoveHome -> rotationRange.start
                    Key.MoveEnd -> rotationRange.endInclusive
                    else -> return@DesktopPropRow false
                }
                actions.applyLayerEdit(state, layerId, rotationLabel) {
                    it.copy(
                        transform = it.transform.copy(
                            rotationDegrees = next.coerceIn(
                                rotationRange.start,
                                rotationRange.endInclusive
                            )
                        )
                    )
                }
                true
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val alignLabel = stringResource(Res.string.editor_layer_align_centre)
            DesktopAlignCell(
                icon = EditorSymbols.FitScreen,
                label = alignLabel,
                enabled = enabled &&
                    (layer.transform.translationX != 0f ||
                        layer.transform.translationY != 0f),
                tag = TAG_DESKTOP_PROP_ALIGN
            ) {
                actions.applyLayerEdit(state, layerId, alignLabel) {
                    it.copy(
                        transform = it.transform.copy(
                            translationX = 0f,
                            translationY = 0f
                        )
                    )
                }
            }
            val flipHLabel = stringResource(Res.string.editor_flip_horizontal)
            DesktopAlignCell(
                icon = EditorSymbols.Flip,
                label = flipHLabel,
                enabled = enabled,
                tag = TAG_DESKTOP_PROP_FLIP_H
            ) {
                actions.applyLayerEdit(state, layerId, flipHLabel) {
                    it.copy(
                        transform = it.transform.copy(
                            flipHorizontal = !it.transform.flipHorizontal
                        )
                    )
                }
            }
            val flipVLabel = stringResource(Res.string.editor_flip_vertical)
            DesktopAlignCell(
                icon = EditorSymbols.Flip,
                label = flipVLabel,
                enabled = enabled,
                tag = TAG_DESKTOP_PROP_FLIP_V,
                iconRotation = 90f
            ) {
                actions.applyLayerEdit(state, layerId, flipVLabel) {
                    it.copy(
                        transform = it.transform.copy(
                            flipVertical = !it.transform.flipVertical
                        )
                    )
                }
            }
        }
        Text(
            text = stringResource(Res.string.editor_layer_section),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        DesktopPropRow(
            label = stringResource(Res.string.editor_tool_mask),
            value = stringResource(
                if (layer.mask == PhotoLayerMask()) {
                    Res.string.editor_value_none
                } else {
                    Res.string.editor_value_edited
                }
            ),
            fieldWidth = 88.dp,
            enabled = canEdit && !state.interactionActive,
            tag = TAG_DESKTOP_PROP_MASK,
            onClick = {
                if (enabled) args.onSelectTool(EditorToolIds.PhotoLayerMask)
            },
            onKey = { _, _ -> false }
        )
        DesktopPropRow(
            label = stringResource(Res.string.editor_layer_effects),
            value = stringResource(Res.string.editor_value_none),
            fieldWidth = 119.dp,
            enabled = false,
            tag = TAG_DESKTOP_PROP_EFFECTS,
            onClick = {},
            onKey = { _, _ -> false }
        )
    }

    when (dialogField) {
        DesktopPropField.Opacity -> if (owner.showDialog) {
            NumericEntryDialog(
                label = opacityLabel,
                initialValue = layer.opacity * 100f,
                range = 0f..100f,
                onConfirm = { entered ->
                    if (owner.ownsDraft && owner.sourceRef == sourceKey) {
                        actions.draftLayerEdit(state, layerId) {
                            it.copy(opacity = entered / 100f)
                        }
                        actions.commitEdit()
                    }
                    owner.hide()
                    dialogField = null
                },
                onDismiss = {
                    dialogField = null
                    owner.dismiss(sourceKey, actions.cancelEdit)
                }
            )
        }
        DesktopPropField.Position -> if (owner.showDialog) {
            NumericPairEntryDialog(
                label = xyLabel,
                firstLabel = stringResource(Res.string.editor_transform_x),
                firstInitial = layer.transform.translationX,
                firstRange = xRange,
                secondLabel = stringResource(Res.string.editor_transform_y),
                secondInitial = layer.transform.translationY,
                secondRange = yRange,
                onConfirm = { first, second ->
                    if (owner.ownsDraft && owner.sourceRef == sourceKey) {
                        actions.draftLayerEdit(state, layerId) {
                            it.copy(
                                transform = it.transform.copy(
                                    translationX = first,
                                    translationY = second
                                )
                            )
                        }
                        actions.commitEdit()
                    }
                    owner.hide()
                    dialogField = null
                },
                onDismiss = {
                    dialogField = null
                    owner.dismiss(sourceKey, actions.cancelEdit)
                }
            )
        }
        DesktopPropField.Rotation -> if (owner.showDialog) {
            NumericEntryDialog(
                label = rotationLabel,
                initialValue = layer.transform.rotationDegrees,
                range = rotationRange,
                onConfirm = { entered ->
                    if (owner.ownsDraft && owner.sourceRef == sourceKey) {
                        actions.draftLayerEdit(state, layerId) {
                            it.copy(
                                transform = it.transform.copy(
                                    rotationDegrees = entered
                                )
                            )
                        }
                        actions.commitEdit()
                    }
                    owner.hide()
                    dialogField = null
                },
                onDismiss = {
                    dialogField = null
                    owner.dismiss(sourceKey, actions.cancelEdit)
                }
            )
        }
        null -> Unit
    }
}

/**
 * One 48 dp property row — a `surfaceContainerHigh` card, the label
 * left and the [fieldWidth]×40 value field right (outlined, 8 dp
 * corners). The row is the keyboard target: Tab focuses it with the
 * visible 2 dp primary outline, the arrows nudge through [onKey], and a
 * click opens the field's exact editor through [onClick].
 */
@Composable
private fun DesktopPropRow(
    label: String,
    value: String,
    fieldWidth: Dp,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
    onKey: (Key, Boolean) -> Boolean
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag(tag)
            .then(
                if (focused && enabled) {
                    Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(10.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || !enabled) {
                    return@onKeyEvent false
                }
                onKey(event.key, event.isShiftPressed)
            }
            .focusable(interactionSource = interactionSource, enabled = enabled)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label
            ) { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(fieldWidth, 40.dp)
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (enabled) {
                        Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(8.dp)
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 14.sp,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** One of the three equal align/flip cells — icon-only, 48 dp tall. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.DesktopAlignCell(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tag: String,
    iconRotation: Float = 0f,
    onClick: () -> Unit
) {
    WorkbenchTooltip(label) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag(tag)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = label
                ) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
                modifier = Modifier
                    .size(20.dp)
                    .rotate(iconRotation)
            )
        }
    }
}
