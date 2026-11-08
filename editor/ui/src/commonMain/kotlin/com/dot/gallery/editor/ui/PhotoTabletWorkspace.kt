package com.dot.gallery.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_close
import com.dot.gallery.editor.ui.generated.resources.editor_compare
import com.dot.gallery.editor.ui.generated.resources.editor_focus
import com.dot.gallery.editor.ui.generated.resources.editor_histogram_title
import com.dot.gallery.editor.ui.generated.resources.editor_layers
import com.dot.gallery.editor.ui.generated.resources.editor_layer_add_image
import com.dot.gallery.editor.ui.generated.resources.editor_more_options
import com.dot.gallery.editor.ui.generated.resources.editor_panel_edits
import com.dot.gallery.editor.ui.generated.resources.editor_panel_source
import com.dot.gallery.editor.ui.generated.resources.editor_redo
import com.dot.gallery.editor.ui.generated.resources.editor_save_copy
import com.dot.gallery.editor.ui.generated.resources.editor_title_edited
import com.dot.gallery.editor.ui.generated.resources.editor_undo
import com.dot.gallery.editor.ui.icons.AddPhotoAlternate
import com.dot.gallery.editor.ui.icons.Close
import com.dot.gallery.editor.ui.icons.Compare
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.Fullscreen
import com.dot.gallery.editor.ui.icons.MoreVert
import com.dot.gallery.editor.ui.icons.Redo
import com.dot.gallery.editor.ui.icons.Undo
import org.jetbrains.compose.resources.stringResource

internal const val TAG_TABLET_INSPECTOR = "editor.tablet.inspector"
internal const val TAG_TABLET_TOOL_STRIP = "editor.tablet.toolStrip"
internal const val TAG_TABLET_HISTOGRAM = "editor.tablet.histogram"
internal const val TAG_TABLET_LAYERS = "editor.tablet.layers"
internal const val TAG_TABLET_ZOOM = "editor.tablet.zoom"

/**
 * The `tablet-master-detail` composition: the canvas fills the window with
 * one floating document capsule over its top edge, and a 380 dp inspector
 * holds the tool strip, the histogram read-out and the active tool's
 * controls. There is no rail and no collapsed inspector — the strip is the
 * only tool selector and the inspector is always present, so the canvas
 * keeps the master field.
 */
@Composable
internal fun PhotoMasterDetailWorkspace(args: PhotoDesktopArgs) {
    Column(Modifier.fillMaxSize()) {
        StatusStrips(
            state = args.state,
            actions = args.actions,
            onOpenExport = args.onOpenExport
        )
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            MasterDetailCanvas(args)
            MasterDetailInspector(args)
        }
    }
}

/**
 * The master field — the full composite on the dark surround with the
 * document capsule floating inside a 12 dp inset, the zoom field at the
 * bottom start and the focus destination at the bottom end. The fit card
 * clears both floating lanes.
 */
@Composable
private fun RowScope.MasterDetailCanvas(args: PhotoDesktopArgs) {
    Box(Modifier.weight(1f).fillMaxHeight()) {
        EditorCanvas(
            state = args.state,
            actions = args.actions,
            viewport = args.viewport,
            bitmap = args.bitmap,
            imageDescription = args.imageDescription,
            compactUtility = false,
            showFooter = false,
            mediaPadding = PaddingValues(top = 96.dp, bottom = 96.dp),
            focusRequester = args.canvasFocusRequester,
            modifier = Modifier
                .fillMaxSize()
                .testTag(TAG_PHOTO_CANVAS),
            gpuRenderer = if (args.shaderDrawing) args.shaderRenderer else null,
            gpuEdits = if (args.shaderDrawing) args.shaderEdits else null,
            gpuImageSize = if (args.shaderDrawing) args.shaderImageSize else null,
            onGpuFrameFailed = args.onGpuFrameFailed,
            imageOverlay = { layout ->
                PhotoCanvasToolOverlay(
                    layout = layout,
                    state = args.state,
                    actions = args.actions,
                    workspace = args.workspace,
                    toolUiState = args.toolUiState,
                    viewport = args.viewport,
                    onCanvasRefocus = args.onCanvasRefocus
                )
            },
            overlay = {
                WorkbenchCanvasZoomField(args.viewport, TAG_TABLET_ZOOM)
                MasterDetailFocusButton(args)
            }
        )
        MasterDetailHeader(
            args = args,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        )
    }
}

/**
 * The document capsule — Close, the dirty-aware source name, the edit
 * commands (Undo, Redo), the compare toggle, the More menu and the one
 * filled Save copy action. The capsule rides the canvas, so the inspector
 * never spends a row on document chrome.
 */
@Composable
private fun MasterDetailHeader(
    args: PhotoDesktopArgs,
    modifier: Modifier = Modifier
) {
    val state = args.state
    val displayName = state.info?.displayName.orEmpty()
    EditorFloatingHeaderCapsule(
        modifier = modifier.testTag(TAG_PHOTO_HEADER)
    ) {
        WorkbenchIconButton(
            icon = EditorSymbols.Close,
            label = stringResource(Res.string.editor_close),
            onClick = args.onRequestClose,
            modifier = Modifier.testTag(TAG_BACK)
        )
        Text(
            text = if (state.isDirty) {
                stringResource(Res.string.editor_title_edited, displayName)
            } else {
                displayName
            },
            style = MaterialTheme.typography.titleMedium,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )
        WorkbenchIconButton(
            icon = EditorSymbols.Undo,
            label = stringResource(Res.string.editor_undo),
            onClick = args.actions.undo,
            enabled = state.canUndo,
            modifier = Modifier.testTag(TAG_UNDO)
        )
        WorkbenchIconButton(
            icon = EditorSymbols.Redo,
            label = stringResource(Res.string.editor_redo),
            onClick = args.actions.redo,
            enabled = state.canRedo,
            modifier = Modifier.testTag(TAG_REDO)
        )
        // The compare toggle drives the real split view; the Before chip
        // tag keeps the viewport contract on one node.
        WorkbenchIconButton(
            icon = EditorSymbols.Compare,
            label = stringResource(Res.string.editor_compare),
            onClick = { args.viewport.compareSplit = !args.viewport.compareSplit },
            enabled = args.imageCapable && state.originalPreview != null,
            selected = args.viewport.compareSplit,
            modifier = Modifier.testTag(TAG_BEFORE_CHIP)
        )
        MasterDetailMenu(args)
        WorkbenchPrimaryAction(
            label = stringResource(Res.string.editor_save_copy),
            onClick = args.actions.saveCopy,
            enabled = state.document != null && !state.exporting &&
                !state.isDrafting && !state.opening,
            modifier = Modifier.testTag(TAG_SAVE)
        )
    }
}

/** The capsule's More menu — the real secondary surfaces, nothing else. */
@Composable
private fun RowScope.MasterDetailMenu(args: PhotoDesktopArgs) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        WorkbenchIconButton(
            icon = EditorSymbols.MoreVert,
            label = stringResource(Res.string.editor_more_options),
            onClick = { menuOpen = true },
            enabled = args.state.document != null,
            modifier = Modifier.testTag(TAG_OVERFLOW)
        )
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            PhotoHeaderMenuItems(
                toolMenu = args.toolMenu,
                state = args.state,
                actions = args.actions,
                workspace = args.workspace,
                imageCapable = args.imageCapable,
                onDismiss = { menuOpen = false }
            )
        }
    }
}

/** The bottom-end focus destination — the expand affordance over the field. */
@Composable
private fun BoxScope.MasterDetailFocusButton(args: PhotoDesktopArgs) {
    WorkbenchTooltip(stringResource(Res.string.editor_focus)) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(
                    enabled = args.imageCapable,
                    role = Role.Button,
                    onClickLabel = stringResource(Res.string.editor_focus)
                ) { args.workspace.focusMode = true }
                .testTag(TAG_FOCUS),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = EditorSymbols.Fullscreen,
                contentDescription = stringResource(Res.string.editor_focus),
                tint = if (args.imageCapable) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * The 380 dp inspector — the horizontal tool strip under the window's top
 * edge, then the active surface: the histogram read-out over the tool's
 * controls on the Edit surface, the layer list on Layers, the source or
 * edits pane on theirs. An open tool transaction pins Cancel/Apply at the
 * foot; the rest state keeps the design's empty foot and leaves reset to
 * the More menu.
 */
@Composable
private fun RowScope.MasterDetailInspector(args: PhotoDesktopArgs) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .width(EditorLayoutMode.MASTER_DETAIL_INSPECTOR_WIDTH_DP.dp)
            .fillMaxHeight()
            .testTag(TAG_TABLET_INSPECTOR)
    ) {
        Column(Modifier.fillMaxSize()) {
            MasterDetailToolStrip(args)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                CompositionLocalProvider(
                    LocalPhotoHistogramPrefs provides args.histogramPrefs
                ) {
                    when (args.workspace.panel) {
                        EditorWorkspacePanel.Layers -> MasterDetailLayers(args)
                        EditorWorkspacePanel.Source -> {
                            MasterDetailPaneTitle(
                                stringResource(Res.string.editor_panel_source)
                            )
                            PhotoSourcePanel(
                                state = args.state,
                                originalBitmap = args.originalBitmap
                            )
                        }
                        EditorWorkspacePanel.Edits -> {
                            MasterDetailPaneTitle(
                                stringResource(Res.string.editor_panel_edits)
                            )
                            PhotoEditsPanel(
                                state = args.state,
                                actions = args.actions
                            )
                        }
                        EditorWorkspacePanel.Edit -> {
                            MasterDetailHistogramCard(
                                pixels = currentPreviewPixels(args.state),
                                prefs = args.histogramPrefs
                            )
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

/**
 * The inspector's tool selector — every catalogued tool in registry order
 * behind one scrolling pill container, with Layers as the first
 * destination. The selected cell is the live tool, so the strip always
 * names the surface below it; no category label is invented.
 */
@Composable
private fun MasterDetailToolStrip(args: PhotoDesktopArgs) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .horizontalScroll(rememberScrollState())
            .padding(4.dp)
            .testTag(TAG_TABLET_TOOL_STRIP),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MasterDetailToolTab(
            label = stringResource(Res.string.editor_layers),
            tag = TAG_LAYERS,
            selected = args.workspace.panel == EditorWorkspacePanel.Layers,
            enabled = args.imageCapable
        ) {
            args.viewport.compareSplit = false
            args.workspace.panel = EditorWorkspacePanel.Layers
            args.workspace.toolPanelExpanded = true
            val layers = args.state.document?.layers.orEmpty()
            if (layers.none { it.id == args.workspace.selectedLayerId }) {
                args.workspace.selectedLayerId = layers.lastOrNull()?.id
            }
            args.onCanvasRefocus()
        }
        args.toolEntries.filter { it.catalogued }.forEach { entry ->
            MasterDetailToolTab(
                label = stringResource(entry.descriptor.title),
                tag = entry.tag,
                selected = args.workspace.panel == EditorWorkspacePanel.Edit &&
                    args.workspace.activeTool == entry.descriptor.id,
                enabled = args.imageCapable && entry.available(args.state)
            ) {
                args.viewport.compareSplit = false
                args.onSelectTool(entry.descriptor.id)
            }
        }
    }
}

/** One 48 dp strip cell — text-only, the design's tab rhythm. */
@Composable
private fun MasterDetailToolTab(
    label: String,
    tag: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
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
            .testTag(tag)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                selected -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * The histogram card — the displayed preview's real bins under the card's
 * title, with the chart's own legend naming the drawn channels and a tap
 * cycling them. No tone preset sits beside it: the recipe has none.
 */
@Composable
private fun MasterDetailHistogramCard(
    pixels: SrgbPixels?,
    prefs: PhotoHistogramPrefs
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 2.dp, end = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp)
            .testTag(TAG_TABLET_HISTOGRAM)
    ) {
        Text(
            text = stringResource(Res.string.editor_histogram_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        PhotoHistogramChart(
            pixels = pixels,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/** The Layers surface — the same rows and Add image the workbench uses. */
@Composable
private fun MasterDetailLayers(args: PhotoDesktopArgs) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .testTag(TAG_TABLET_LAYERS)
    ) {
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
                .fillMaxWidth()
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

@Composable
private fun MasterDetailPaneTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontSize = 15.sp,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 4.dp)
    )
}
