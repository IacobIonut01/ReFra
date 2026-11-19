package com.dot.gallery.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoRawDevelop
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_all_tools
import com.dot.gallery.editor.ui.generated.resources.editor_close
import com.dot.gallery.editor.ui.generated.resources.editor_compare
import com.dot.gallery.editor.ui.generated.resources.editor_done
import com.dot.gallery.editor.ui.generated.resources.editor_edit_count_many
import com.dot.gallery.editor.ui.generated.resources.editor_edit_count_one
import com.dot.gallery.editor.ui.generated.resources.editor_layer_add_image
import com.dot.gallery.editor.ui.generated.resources.editor_layers
import com.dot.gallery.editor.ui.generated.resources.editor_more_options
import com.dot.gallery.editor.ui.generated.resources.editor_no_edits
import com.dot.gallery.editor.ui.generated.resources.editor_redo
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_open_in
import com.dot.gallery.editor.ui.generated.resources.editor_save_copy
import com.dot.gallery.editor.ui.generated.resources.editor_settings
import com.dot.gallery.editor.ui.generated.resources.editor_undo
import com.dot.gallery.editor.ui.icons.AddPhotoAlternate
import com.dot.gallery.editor.ui.icons.Close
import com.dot.gallery.editor.ui.icons.Compare
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.Layers
import com.dot.gallery.editor.ui.icons.MoreVert
import com.dot.gallery.editor.ui.icons.Redo
import com.dot.gallery.editor.ui.icons.Undo
import org.jetbrains.compose.resources.stringResource

internal const val TAG_SIMPLE_RAIL = "editor.simple.rail"
internal const val TAG_SIMPLE_CARD = "editor.simple.card"
internal const val TAG_SIMPLE_HEADER = "editor.simple.header"
internal const val TAG_TABLET_LAYERS = "editor.tablet.layers"

/**
 * The Simple composition on large windows: the blurred photo backdrop,
 * the flat 64 dp header (Close, the file's title and edit count, the
 * Undo/Redo, Compare and Save), then a
 * body of the vertical [EditorDock] rail, the canvas and — while a tool
 * or pane is open — the 360 dp glass tool card. The same dock items as
 * the phone's Simple dock serve the rail; More hosts the tool catalogue
 * inside the card.
 */
@Composable
internal fun PhotoSimpleLargeWorkspace(args: PhotoDesktopArgs) {
    val state = args.state
    val workspace = args.workspace
    Box(Modifier.fillMaxSize()) {
        EditorBlurredBackdrop(state.originalPreview, Modifier.fillMaxSize())
        // The chrome lifts clear of the status bar and cutout — the
        // backdrop still bleeds edge to edge behind it.
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            val toolOpen = workspace.toolPanelExpanded && !workspace.catalogueOpen &&
                workspace.panel == EditorWorkspacePanel.Edit
            val layersOpen = workspace.toolPanelExpanded && !workspace.catalogueOpen &&
                workspace.panel == EditorWorkspacePanel.Layers
            PhotoWorkbenchHeader(
                args,
                toolActions = if (toolOpen) {
                    {
                        if (workspace.activeTool == EditorToolIds.PhotoMarkup &&
                            args.toolUiState.markupMode != MarkupMode.Text
                        ) {
                            MarkupInkPill(args.toolUiState, Modifier.padding(end = 8.dp))
                        }
                        ToolHeaderActions(
                            resetLabel = args.toolMenu.resetParameterLabel,
                            resetEnabled = args.toolMenu.resetParameterEnabled &&
                                !state.interactionActive,
                            onReset = args.toolMenu.onResetParameter,
                            resetTag = args.toolMenu.resetParameterFooterTag,
                            doneEnabled = !state.interactionActive,
                            onDone = {
                                args.actions.applyToolEdit()
                                workspace.collapseContext()
                                args.onCanvasRefocus()
                            }
                        )
                    }
                } else if (layersOpen) {
                    {
                        ToolHeaderActions(
                            resetLabel = null,
                            resetEnabled = false,
                            onReset = null,
                            resetTag = TAG_RESET_TOOL,
                            doneEnabled = !state.interactionActive,
                            onDone = {
                                workspace.collapseContext()
                                args.onCanvasRefocus()
                            }
                        )
                    }
                } else null
            )
            StatusStrips(
                state = state,
                actions = args.actions,
                onOpenExport = args.onOpenExport
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EditorDock(
                    items = photoDockItems(
                        workspace = workspace,
                        state = state,
                        entries = args.toolEntries,
                        auto = args.autoDock,
                        enabled = args.imageCapable
                    ),
                    orientation = Orientation.Vertical,
                    onSelect = { id ->
                        photoDockSelect(
                            id = id,
                            workspace = workspace,
                            onSelectTool = args.onSelectTool,
                            onShowTools = { workspace.toggleCatalogue() },
                            auto = args.autoDock
                        )
                    },
                    modifier = Modifier.testTag(TAG_SIMPLE_RAIL)
                )
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    EditorCanvas(
                        state = state,
                        actions = args.actions,
                        viewport = args.viewport,
                        bitmap = args.bitmap,
                        imageDescription = args.imageDescription,
                        compactUtility = false,
                        showFooter = false,
                        mediaPadding = PaddingValues(24.dp),
                        focusRequester = args.canvasFocusRequester,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(TAG_PHOTO_CANVAS),
                        gpuRenderer = if (args.shaderDrawing) {
                            args.shaderRenderer
                        } else {
                            null
                        },
                        gpuEdits = if (args.shaderDrawing) {
                            args.shaderEdits
                        } else {
                            null
                        },
                        gpuImageSize = if (args.shaderDrawing) {
                            args.shaderImageSize
                        } else {
                            null
                        },
                        onGpuFrameFailed = args.onGpuFrameFailed,
                        imageOverlay = { layout ->
                            PhotoToolImageOverlay(
                                layout = layout,
                                state = state,
                                actions = args.actions,
                                workspace = workspace,
                                toolUiState = args.toolUiState,
                                viewport = args.viewport,
                                onCanvasRefocus = args.onCanvasRefocus
                            )
                        }
                    )
                }
                // More opens the catalogue in the card even when no tool
                // is open yet.
                if (workspace.toolPanelExpanded || workspace.catalogueOpen) {
                    SimpleToolCard(args)
                }
            }
        }
    }
    if (workspace.pinEditorOpen) {
        PhotoPinManagerSheet(
            workspace = workspace,
            entries = args.toolEntries,
            onDismiss = { workspace.pinEditorOpen = false }
        )
    }
}

/**
 * The large-window header — Close, the file's name over its edit count, the
 * Undo/Redo, the hold-to-compare button, the
 * More menu of real secondary surfaces and the one filled Save copy
 * action. Flat on the backdrop: no fill, no divider.
 */
@Composable
internal fun PhotoWorkbenchHeader(
    args: PhotoDesktopArgs,
    /** The line under the file name; the edit count when unset. */
    subtitle: String? = null,
    /** Entries added after the shared ones in the More menu. */
    extraMenuItems: (@Composable ColumnScope.(dismiss: () -> Unit) -> Unit)? = null,
    /** The open tool's controls (mode pill, Reset, Done), shown before Save. */
    toolActions: (@Composable () -> Unit)? = null
) {
    val state = args.state
    val displayName = state.info?.displayName.orEmpty()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(EditorV2Layout.Header)
            .testTag(TAG_SIMPLE_HEADER)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WorkbenchIconButton(
                icon = EditorSymbols.Close,
                label = stringResource(Res.string.editor_close),
                onClick = args.onRequestClose,
                modifier = Modifier.testTag(TAG_BACK)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 8.dp)
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle ?: simpleEditCountLabel(state.document),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
            SimpleCompareButton(args)
            SimpleHeaderMenu(args, extraMenuItems)
            val saveEnabled = state.document != null && !state.exporting &&
                !state.isDrafting && !state.opening
            if (toolActions != null) {
                toolActions()
                // Done is the filled action while a tool is open; Save steps back.
                FilledTonalButton(
                    onClick = args.actions.saveCopy,
                    enabled = saveEnabled,
                    modifier = Modifier.padding(start = 8.dp).testTag(TAG_SAVE)
                ) {
                    Text(stringResource(Res.string.editor_save_copy), maxLines = 1)
                }
            } else {
                WorkbenchPrimaryAction(
                    label = stringResource(Res.string.editor_save_copy),
                    onClick = args.actions.saveCopy,
                    enabled = saveEnabled,
                    modifier = Modifier.testTag(TAG_SAVE)
                )
            }
        }
    }
}

/**
 * The header's Compare control: a tap toggles the split compare, a
 * press-and-hold shows the full Before frame until release — the same
 * contract the canvas's long-press drives.
 */
@Composable
private fun SimpleCompareButton(args: PhotoDesktopArgs) {
    val viewport = args.viewport
    val imageReady = args.imageCapable && args.state.originalPreview != null
    var holding by remember { mutableStateOf(false) }
    val compareLabel = stringResource(Res.string.editor_compare)
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val active = viewport.compareSplit || viewport.compareBefore
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics {
                role = Role.Button
                contentDescription = compareLabel
                selected = active
                this[SemanticsActions.OnClick] = AccessibilityAction(
                    label = compareLabel
                ) {
                    if (imageReady) {
                        viewport.compareSplit = !viewport.compareSplit
                        true
                    } else {
                        false
                    }
                }
            }
            .pointerInput(imageReady) {
                detectTapGestures(
                    onPress = {
                        tryAwaitRelease()
                        if (holding) {
                            holding = false
                            viewport.compareBefore = false
                        }
                    },
                    onLongPress = {
                        if (imageReady) {
                            holding = true
                            viewport.compareBefore = true
                        }
                    },
                    onTap = {
                        if (!holding && imageReady) {
                            viewport.compareSplit = !viewport.compareSplit
                        }
                    }
                )
            }
            .testTag(TAG_BEFORE_CHIP),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .then(
                    if (focused) {
                        Modifier.border(
                            2.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(8.dp)
                        )
                    } else {
                        Modifier
                    }
                )
                .clip(RoundedCornerShape(8.dp))
                .background(
                    when {
                        pressed -> MaterialTheme.colorScheme.onSurface
                            .copy(alpha = 0.14f)
                        hovered -> MaterialTheme.colorScheme.onSurface
                            .copy(alpha = 0.08f)
                        active -> MaterialTheme.colorScheme.surfaceContainerHigh
                        else -> Color.Transparent
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = EditorSymbols.Compare,
                contentDescription = null,
                tint = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    LocalContentColor.current
                },
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** The header's More menu — the same secondary surfaces the capsules carry. */
@Composable
private fun SimpleHeaderMenu(
    args: PhotoDesktopArgs,
    extraItems: (@Composable ColumnScope.(dismiss: () -> Unit) -> Unit)? = null
) {
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
            extraItems?.invoke(this) { menuOpen = false }
        }
    }
}

/**
 * The 360 dp tool card — the panelGlass surface with 24 dp corners, a
 * header naming the open surface (the tool's title, All tools or Layers)
 * plus the live tool's published extra action, the
 * scrolling body and, for an open tool, the Reset/Done foot. Done lands
 * the tool transaction as one history step and collapses the card.
 */
@Composable
private fun SimpleToolCard(args: PhotoDesktopArgs) {
    val state = args.state
    val workspace = args.workspace
    val catalogueOpen = workspace.catalogueOpen
    val panel = workspace.panel
    val glass = editorGlassColors()
    Column(
        modifier = Modifier
            .width(360.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(24.dp))
            .chromeBlur(RoundedCornerShape(24.dp), glass.panelGlass)
            .testTag(TAG_SIMPLE_CARD)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = simpleCardTitle(args, catalogueOpen),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            val compareLabel = args.toolMenu.compareLabel
            val compareAction = args.toolMenu.onCompare
            if (!catalogueOpen && panel == EditorWorkspacePanel.Edit &&
                compareLabel != null && compareAction != null
            ) {
                TextButton(
                    onClick = compareAction,
                    enabled = args.toolMenu.compareEnabled,
                    modifier = Modifier.testTag(args.toolMenu.compareTag)
                ) {
                    Text(compareLabel, maxLines = 1)
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                catalogueOpen -> SimpleCatalogue(args)
                panel == EditorWorkspacePanel.Layers -> SimpleLayers(args)
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    CompositionLocalProvider(
                        LocalPhotoToolMenu provides args.toolMenu,
                        LocalPhotoHistogramPrefs provides args.histogramPrefs
                    ) {
                        PhotoToolPanel(
                            state = state,
                            actions = args.actions,
                            tool = workspace.activeTool
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun simpleCardTitle(args: PhotoDesktopArgs, catalogueOpen: Boolean): String =
    when {
        catalogueOpen -> stringResource(Res.string.editor_all_tools)
        args.workspace.panel == EditorWorkspacePanel.Layers ->
            stringResource(Res.string.editor_layers)
        else -> photoToolTitle(args.workspace.activeTool)
    }

/** The Layers surface inside the card — the shared rows plus Add image. */
@Composable
private fun SimpleLayers(args: PhotoDesktopArgs) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
        ) {
            Icon(
                imageVector = EditorSymbols.AddPhotoAlternate,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(Res.string.editor_layer_add_image),
                modifier = Modifier.padding(start = 8.dp),
                maxLines = 1
            )
        }
    }
}

/**
 * The tool catalogue inside the card — the same search, group filters,
 * pin toggles and Edit pins affordance the phone sheet carries, plus the
 * non-tool surfaces (Layers, settings, open-in) as extra cells.
 */
@Composable
private fun SimpleCatalogue(args: PhotoDesktopArgs) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .testTag(TAG_EDITOR_CATALOGUE_SHEET)
    ) {
        EditorToolCatalogueSheet(
            entries = args.toolEntries,
            state = args.state,
            activeTool = args.workspace.activeTool,
            enabled = args.imageCapable,
            onSelect = { tool -> args.onSelectTool(tool) },
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
                args.workspace.pinFromCatalogue(id.value)
            },
            isPinnable = { id ->
                id.value in PHOTO_PINNABLE_DESTINATIONS
            },
            onEditPins = {
                args.workspace.pinEditorOpen = true
            },
            showDragHandle = false,
            extraCells = simpleCatalogueExtras(args)
        )
    }
}

/** The non-tool surfaces that ride the card's catalogue, as in Compact. */
@Composable
private fun simpleCatalogueExtras(
    args: PhotoDesktopArgs
): List<EditorCatalogueExtraCell> {
    val workspace = args.workspace
    val layers = stringResource(Res.string.editor_layers)
    val openIn = stringResource(Res.string.editor_retouch_open_in)
    val settingsLabel = stringResource(Res.string.editor_settings)
    val openExternally = LocalEditorHostTools.current.openInExternalEditor
    fun selected(panel: EditorWorkspacePanel) =
        workspace.panel == panel && workspace.toolPanelExpanded
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
                workspace.togglePanel(EditorWorkspacePanel.Layers)
            }
        ),
        EditorCatalogueExtraCell(
            icon = Icons.Outlined.Settings,
            label = settingsLabel,
            tag = TAG_CATALOGUE_SETTINGS,
            onClick = {
                workspace.catalogueOpen = false
                workspace.settingsOpen = true
            }
        )
    )
}

/**
 * The header subtitle's edit count — one per non-neutral recipe part:
 * each touched colour field, geometry, spatial effects, each finishing
 * stage, retouch, every added layer and a non-default RAW develop.
 */
@Composable
internal fun simpleEditCountLabel(document: PhotoDocument?): String {
    val count = document?.let { photoEditCount(it) } ?: 0
    return when {
        count <= 0 -> stringResource(Res.string.editor_no_edits)
        count == 1 -> stringResource(Res.string.editor_edit_count_one)
        else -> stringResource(Res.string.editor_edit_count_many, count)
    }
}

private fun photoEditCount(document: PhotoDocument): Int {
    var count = 0
    val adjustments = document.adjustments
    if (adjustments != ColorAdjustments()) {
        if (adjustments.exposureStops != 0f) count++
        if (adjustments.contrast != 1f) count++
        if (adjustments.saturation != 1f) count++
        if (adjustments.highlights != 0f) count++
        if (adjustments.shadows != 0f) count++
        if (adjustments.whites != 0f) count++
        if (adjustments.blacks != 0f) count++
    }
    if (document.geometry != PhotoGeometry()) count++
    if (!document.spatial.isNeutral) count++
    val finishing = document.finishing
    if (!finishing.isNeutral) {
        if (!finishing.curves.isNeutral) count++
        if (!finishing.whiteBalance.isNeutral) count++
        if (!finishing.grade.isNeutral) count++
        if (!finishing.vignette.isNeutral) count++
        if (!finishing.grain.isNeutral) count++
        if (!finishing.look.isNeutral) count++
    }
    if (!document.retouch.isNeutral) count++
    count += document.layers.size
    if (document.develop != PhotoRawDevelop()) count++
    return count
}
