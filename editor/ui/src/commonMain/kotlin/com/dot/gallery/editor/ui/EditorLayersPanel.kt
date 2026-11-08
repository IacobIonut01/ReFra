package com.dot.gallery.editor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.MAX_PHOTO_IMAGE_LAYERS
import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoImageLayer
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_cancel
import com.dot.gallery.editor.ui.generated.resources.editor_dismiss
import com.dot.gallery.editor.ui.generated.resources.editor_layer_add
import com.dot.gallery.editor.ui.generated.resources.editor_layer_adjustment
import com.dot.gallery.editor.ui.generated.resources.editor_layer_adjustment_sub
import com.dot.gallery.editor.ui.generated.resources.editor_layer_base_locked
import com.dot.gallery.editor.ui.generated.resources.editor_layer_blend_opacity
import com.dot.gallery.editor.ui.generated.resources.editor_layer_delete
import com.dot.gallery.editor.ui.generated.resources.editor_layer_duplicate
import com.dot.gallery.editor.ui.generated.resources.editor_layer_group
import com.dot.gallery.editor.ui.generated.resources.editor_layer_hide
import com.dot.gallery.editor.ui.generated.resources.editor_layer_import_error
import com.dot.gallery.editor.ui.generated.resources.editor_layer_importing
import com.dot.gallery.editor.ui.generated.resources.editor_layer_lock
import com.dot.gallery.editor.ui.generated.resources.editor_layer_locked
import com.dot.gallery.editor.ui.generated.resources.editor_layer_move_down
import com.dot.gallery.editor.ui.generated.resources.editor_layer_move_up
import com.dot.gallery.editor.ui.generated.resources.editor_layer_name_label
import com.dot.gallery.editor.ui.generated.resources.editor_layer_edit_mask
import com.dot.gallery.editor.ui.generated.resources.editor_layer_options
import com.dot.gallery.editor.ui.generated.resources.editor_layer_rename
import com.dot.gallery.editor.ui.generated.resources.editor_layer_rename_title
import com.dot.gallery.editor.ui.generated.resources.editor_tool_blend
import com.dot.gallery.editor.ui.generated.resources.editor_tool_transform
import com.dot.gallery.editor.ui.generated.resources.editor_layer_show
import com.dot.gallery.editor.ui.generated.resources.editor_layer_unlock
import com.dot.gallery.editor.ui.generated.resources.editor_retry
import com.dot.gallery.editor.ui.icons.Brush
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.InvertColors
import com.dot.gallery.editor.ui.icons.MoreVert
import com.dot.gallery.editor.ui.icons.Transform
import com.dot.gallery.editor.ui.icons.Tune
import kotlin.math.roundToInt
import kotlinx.collections.immutable.PersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

internal const val TAG_LAYERS_DONE = "editor.layers.done"
internal const val TAG_LAYERS_TOOLBAR = "editor.layers.toolbar"
internal const val TAG_LAYERS_LIST = "editor.layers.list"
internal const val TAG_LAYER_ROW_PREFIX = "editor.layer.row."
internal const val TAG_LAYER_VISIBILITY_PREFIX = "editor.layer.visibility."
internal const val TAG_LAYER_MORE_PREFIX = "editor.layer.more."
internal const val TAG_LAYER_ROUTE_TRANSFORM = "editor.layer.route.transform"
internal const val TAG_LAYER_ROUTE_BLEND = "editor.layer.route.blend"
internal const val TAG_LAYER_ROUTE_MASK = "editor.layer.route.mask"
internal const val TAG_LAYER_BASE = "editor.layer.base"
internal const val TAG_LAYER_ADJUSTMENT = "editor.layer.adjustment"
internal const val TAG_LAYER_ADD = "editor.layer.add"
internal const val TAG_LAYER_GROUP = "editor.layer.group"
internal const val TAG_LAYER_ADD_HEADER = "editor.layer.add.header"
internal const val TAG_LAYER_SELECTION = "editor.layer.selection"
internal const val TAG_LAYER_IMPORT_CANCEL = "editor.layer.import.cancel"
internal const val TAG_LAYER_IMPORT_RETRY = "editor.layer.import.retry"
internal const val TAG_LAYER_IMPORT_DISMISS = "editor.layer.import.dismiss"
internal const val TAG_LAYER_RENAME_FIELD = "editor.layer.rename.field"
internal const val TAG_LAYER_RENAME_CONFIRM = "editor.layer.rename.confirm"

/**
 * Whether a new image layer can be requested right now — the same gate
 * the session applies (`importingLayer`, export, drafts, the 16-layer
 * cap), mirrored on the picker buttons so a refused request is never
 * fired.
 */
internal fun canAddLayerImage(state: PhotoEditorState): Boolean {
    val document = state.document ?: return false
    return !state.importingLayer && !state.exporting && !state.isDrafting &&
        !state.toolEditing && document.layers.size < MAX_PHOTO_IMAGE_LAYERS
}

/**
 * The Layers workspace's list pane, shared by the phone inspector and the
 * wide inspector. Top-to-bottom it shows the recipe's image layers in
 * reverse order (top of stack first), a pseudo "Light adjustment" row
 * while a global recipe is non-neutral, and the always-present locked
 * base row. Every row action lands through [PhotoEditorActions.applyEdits]
 * as one undoable global step — the pane owns no private recipe state.
 *
 * [lazyList] picks the scroller: the compact inspector owns bounded
 * height so the list is a real [LazyColumn]; the wide inspector is
 * already a scrolling column, so rows emit inline there. [chrome]
 * controls the phone anatomy — the 48 dp Add/Group toolbar is a
 * compact-surface control the desktop pane replaces with its own
 * pinned Add button, and [rowHeight]/[listSpacing] let the desktop
 * list use the taller 72 dp rows at the wider 8 dp pitch.
 */
@Composable
internal fun PhotoLayersPanel(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    workspace: EditorWorkspaceState,
    baseThumbnail: ImageBitmap?,
    modifier: Modifier = Modifier,
    lazyList: Boolean = true,
    chrome: Boolean = true,
    rowHeight: Dp = 64.dp,
    listSpacing: Dp = 6.dp
) {
    val document = state.document
    val layers = document?.layers.orEmpty()

    // The selection tracks the recipe: a removed layer falls back to the
    // topmost visible layer, then the base (null). Never written when the
    // id is still valid — user selection is not churned by republishes.
    LaunchedEffect(layers) {
        val selected = workspace.selectedLayerId
        if (selected != null && layers.none { it.id == selected }) {
            workspace.selectedLayerId =
                layers.lastOrNull { it.visible }?.id
        }
    }

    var renamingId by rememberSaveable { mutableStateOf<String?>(null) }
    val canAdd = canAddLayerImage(state)
    val showAdjustment = document != null &&
        (document.adjustments != ColorAdjustments() ||
            document.geometry != PhotoGeometry())

    Column(modifier = modifier) {
        // The fixed 48 dp toolbar: Add layer left, Group right — a real
        // disabled control until the grouping backend lands.
        if (chrome) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag(TAG_LAYERS_TOOLBAR),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LayerToolbarAction(
                text = stringResource(Res.string.editor_layer_add),
                icon = Icons.Rounded.Add,
                container = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                enabled = canAdd,
                width = 111.dp,
                onClick = actions.requestAddImage,
                modifier = Modifier.testTag(TAG_LAYER_ADD)
            )
            Spacer(modifier = Modifier.weight(1f))
            LayerToolbarAction(
                text = stringResource(Res.string.editor_layer_group),
                icon = null,
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = 0.38f
                ),
                enabled = false,
                width = 93.dp,
                onClick = {},
                modifier = Modifier.testTag(TAG_LAYER_GROUP)
            )
        }
        }
        if (state.importingLayer) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.editor_layer_importing),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    )
                }
                TextButton(
                    onClick = actions.cancelLayerImport,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .padding(start = 4.dp)
                        .testTag(TAG_LAYER_IMPORT_CANCEL)
                ) {
                    Text(stringResource(Res.string.editor_cancel))
                }
            }
        }
        if (state.layerImportError) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringResource(Res.string.editor_layer_import_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
                TextButton(
                    onClick = actions.requestAddImage,
                    enabled = canAdd,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(TAG_LAYER_IMPORT_RETRY)
                ) {
                    Text(stringResource(Res.string.editor_retry))
                }
                TextButton(
                    onClick = actions.clearError,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(TAG_LAYER_IMPORT_DISMISS)
                ) {
                    Text(stringResource(Res.string.editor_dismiss))
                }
            }
        }
        if (lazyList) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag(TAG_LAYERS_LIST),
                verticalArrangement = Arrangement.spacedBy(listSpacing)
            ) {
                items(
                    items = layers.asReversed(),
                    key = { it.id },
                    contentType = { "layer" }
                ) { layer ->
                    ImageLayerRow(
                        state = state,
                        actions = actions,
                        workspace = workspace,
                        layer = layer,
                        rowHeight = rowHeight,
                        onRename = { renamingId = layer.id }
                    )
                }
                if (showAdjustment) {
                    item(key = "adjustment", contentType = "adjustment") {
                        AdjustmentRow(rowHeight = rowHeight)
                    }
                }
                item(key = "base", contentType = "base") {
                    BaseRow(
                        state = state,
                        workspace = workspace,
                        baseThumbnail = baseThumbnail,
                        rowHeight = rowHeight
                    )
                }
            }
        } else {
            // The wide inspector scrolls as a whole — rows emit inline so
            // the parent scroll container owns the gesture.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_LAYERS_LIST),
                verticalArrangement = Arrangement.spacedBy(listSpacing)
            ) {
                layers.asReversed().forEach { layer ->
                    ImageLayerRow(
                        state = state,
                        actions = actions,
                        workspace = workspace,
                        layer = layer,
                        rowHeight = rowHeight,
                        onRename = { renamingId = layer.id }
                    )
                }
                if (showAdjustment) AdjustmentRow(rowHeight = rowHeight)
                BaseRow(
                    state = state,
                    workspace = workspace,
                    baseThumbnail = baseThumbnail,
                    rowHeight = rowHeight
                )
            }
        }
    }

    val renaming = layers.firstOrNull { it.id == renamingId }
    if (renaming != null && document != null) {
        RenameLayerDialog(
            layer = renaming,
            onDismiss = { renamingId = null },
            onConfirm = { newName ->
                mutateLayerList(state, actions, "Rename layer") { list ->
                    val index = list.indexOfFirst { it.id == renaming.id }
                    if (index < 0) {
                        list
                    } else {
                        list.replacingAt(index, list[index].copy(name = newName))
                    }
                }
                renamingId = null
            }
        )
    }
}

/** Applies one committed recipe step that rewrites the layer list. */
private fun mutateLayerList(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    label: String,
    transform: (PersistentList<PhotoImageLayer>) -> PersistentList<PhotoImageLayer>
) {
    val document = state.document ?: return
    val next = transform(document.layers)
    if (next === document.layers || next == document.layers) return
    actions.applyEdits(label, document.toEdits().copy(layers = next))
}

/**
 * The first free `<id>-copy[-N]` id across layer ids, layer source ids
 * and the base source id — a duplicated layer shares its immutable
 * [PhotoImageLayer.source] but must keep a unique id.
 */
private fun nextCopyLayerId(document: PhotoDocument, baseId: String): String {
    val used = HashSet<String>(document.layers.size * 2 + 1)
    used += document.source.id
    for (layer in document.layers) {
        used += layer.id
        used += layer.source.id
    }
    var candidate = "$baseId-copy"
    var n = 2
    while (candidate in used) {
        candidate = "$baseId-copy-$n"
        n += 1
    }
    return candidate
}

/**
 * One inspector toolbar action: the Figma pill is 36 dp tall inside the
 * 48 dp toolbar row, so the visible control sits on a full 48 dp hit box
 * — the paint matches the spec and the touch target stays accessible.
 * [icon] draws 18 dp with a 6 dp gap before the label.
 */
@Composable
private fun LayerToolbarAction(
    text: String,
    icon: ImageVector?,
    container: Color,
    contentColor: Color,
    enabled: Boolean,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(width)
            .height(48.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = text,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .width(width)
                .height(36.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(container),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

/** One 64 dp image-layer row — thumb, name, blend/opacity, eye, More. */
@Composable
private fun ImageLayerRow(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    workspace: EditorWorkspaceState,
    layer: PhotoImageLayer,
    rowHeight: Dp,
    onRename: () -> Unit
) {
    val selected = workspace.selectedLayerId == layer.id
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .then(
                if (selected) {
                    Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier
                }
            )
            .selectable(selected = selected) {
                workspace.selectedLayerId = layer.id
            }
            .testTag(TAG_LAYER_ROW_PREFIX + layer.id)
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LayerThumbnail(
            pixels = state.layerPreviews[layer.id],
            contentDescription = null
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = layer.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 13.sp
                    ),
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (layer.locked) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription =
                            stringResource(Res.string.editor_layer_locked),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(14.dp)
                    )
                }
            }
            Text(
                text = stringResource(
                    Res.string.editor_layer_blend_opacity,
                    layer.blendMode.name,
                    "${(layer.opacity * 100f).roundToInt()}%"
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        WorkbenchIconButton(
            icon = if (layer.visible) {
                Icons.Rounded.Visibility
            } else {
                Icons.Rounded.VisibilityOff
            },
            label = stringResource(
                if (layer.visible) {
                    Res.string.editor_layer_hide
                } else {
                    Res.string.editor_layer_show
                }
            ),
            onClick = {
                mutateLayerList(state, actions, "Toggle layer visibility") { list ->
                    val index = list.indexOfFirst { it.id == layer.id }
                    if (index < 0) {
                        list
                    } else {
                        list.replacingAt(index, layer.copy(visible = !layer.visible))
                    }
                }
            },
            modifier = Modifier.testTag(TAG_LAYER_VISIBILITY_PREFIX + layer.id)
        )
        LayerMoreMenu(
            state = state,
            actions = actions,
            layer = layer,
            onRename = onRename
        )
    }
}

/** Decodes the session's published layer preview into the 48 dp thumb. */
@Composable
private fun LayerThumbnail(
    pixels: SrgbPixels?,
    contentDescription: String?
) {
    val bitmap by produceState<ImageBitmap?>(null, pixels) {
        value = pixels?.let {
            withContext(Dispatchers.Default) { it.toEditorImageBitmap() }
        }
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** The row's More menu — the honest recipe actions a layer supports. */
@Composable
private fun LayerMoreMenu(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    layer: PhotoImageLayer,
    onRename: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val document = state.document
    val layers = document?.layers.orEmpty()
    val index = layers.indexOfFirst { it.id == layer.id }
    // Display order is the reversed recipe: "up" in the list is the
    // higher stack index, "down" is lower.
    val canMoveUp = index >= 0 && index < layers.lastIndex
    val canMoveDown = index > 0
    // The contextual tool routes — same openTool path the catalogue
    // takes; this layer becomes the selection first so the tool opens
    // on the row the user clicked, not the topmost.
    val layerContext = LocalPhotoLayerToolContext.current

    Box {
        WorkbenchIconButton(
            icon = EditorSymbols.MoreVert,
            label = stringResource(Res.string.editor_layer_options),
            onClick = { open = true },
            modifier = Modifier.testTag(TAG_LAYER_MORE_PREFIX + layer.id)
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            fun dismissAnd(action: () -> Unit) {
                open = false
                action()
            }
            fun routeTo(tool: EditorToolId) {
                layerContext?.selectLayer(layer.id)
                layerContext?.openTool(tool)
            }
            DropdownMenuItem(
                text = {
                    Text(stringResource(Res.string.editor_tool_transform))
                },
                leadingIcon = {
                    Icon(
                        EditorSymbols.Transform,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                enabled = layerContext != null,
                onClick = { dismissAnd { routeTo(EditorToolIds.PhotoTransform) } },
                modifier = Modifier.testTag(TAG_LAYER_ROUTE_TRANSFORM)
            )
            DropdownMenuItem(
                text = {
                    Text(stringResource(Res.string.editor_tool_blend))
                },
                leadingIcon = {
                    Icon(
                        EditorSymbols.InvertColors,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                enabled = layerContext != null,
                onClick = { dismissAnd { routeTo(EditorToolIds.PhotoBlend) } },
                modifier = Modifier.testTag(TAG_LAYER_ROUTE_BLEND)
            )
            DropdownMenuItem(
                text = {
                    Text(stringResource(Res.string.editor_layer_edit_mask))
                },
                leadingIcon = {
                    Icon(
                        EditorSymbols.Brush,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                enabled = layerContext != null,
                onClick = { dismissAnd { routeTo(EditorToolIds.PhotoLayerMask) } },
                modifier = Modifier.testTag(TAG_LAYER_ROUTE_MASK)
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.editor_layer_rename)) },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                enabled = !layer.locked,
                onClick = { dismissAnd(onRename) }
            )
            DropdownMenuItem(
                text = {
                    Text(stringResource(Res.string.editor_layer_duplicate))
                },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    dismissAnd {
                        mutateLayerList(
                            state, actions, "Duplicate layer"
                        ) { list ->
                            val at = list.indexOfFirst { it.id == layer.id }
                            val doc = state.document ?: return@mutateLayerList list
                            if (at < 0) {
                                list
                            } else {
                                list.addingAt(
                                    at + 1,
                                    layer.copy(
                                        id = nextCopyLayerId(doc, layer.id)
                                    )
                                )
                            }
                        }
                    }
                }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.editor_layer_move_up)) },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.ArrowUpward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                enabled = canMoveUp && !layer.locked,
                onClick = {
                    dismissAnd {
                        mutateLayerList(state, actions, "Move layer") { list ->
                            list.removingAt(index).addingAt(index + 1, layer)
                        }
                    }
                }
            )
            DropdownMenuItem(
                text = {
                    Text(stringResource(Res.string.editor_layer_move_down))
                },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.ArrowDownward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                enabled = canMoveDown && !layer.locked,
                onClick = {
                    dismissAnd {
                        mutateLayerList(state, actions, "Move layer") { list ->
                            list.removingAt(index).addingAt(index - 1, layer)
                        }
                    }
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (layer.locked) {
                                Res.string.editor_layer_unlock
                            } else {
                                Res.string.editor_layer_lock
                            }
                        )
                    )
                },
                leadingIcon = {
                    Icon(
                        if (layer.locked) {
                            Icons.Rounded.LockOpen
                        } else {
                            Icons.Rounded.Lock
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    dismissAnd {
                        mutateLayerList(state, actions, "Toggle layer lock") { list ->
                            val at = list.indexOfFirst { it.id == layer.id }
                            if (at < 0) {
                                list
                            } else {
                                list.replacingAt(at, layer.copy(locked = !layer.locked))
                            }
                        }
                    }
                }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(Res.string.editor_layer_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                },
                enabled = !layer.locked,
                onClick = {
                    dismissAnd {
                        mutateLayerList(state, actions, "Delete layer") { list ->
                            list.removingAt(index)
                        }
                    }
                }
            )
        }
    }
}

/**
 * The pseudo adjustment row — a read-only marker that a global
 * colour/geometry recipe is in effect. It is never selectable: it owns
 * no layer and the canvas draws no outline for it.
 */
@Composable
private fun AdjustmentRow(rowHeight: Dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .testTag(TAG_LAYER_ADJUSTMENT)
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = EditorSymbols.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = stringResource(Res.string.editor_layer_adjustment),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )
            Text(
                text = stringResource(Res.string.editor_layer_adjustment_sub),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/**
 * The locked base row — always last. Selecting it clears the layer
 * selection (null): the base is the canvas's own content, so the
 * selection outline stays hidden.
 */
@Composable
private fun BaseRow(
    state: PhotoEditorState,
    workspace: EditorWorkspaceState,
    baseThumbnail: ImageBitmap?,
    rowHeight: Dp
) {
    val selected = workspace.selectedLayerId == null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .then(
                if (selected) {
                    Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier
                }
            )
            .selectable(selected = selected) {
                workspace.selectedLayerId = null
            }
            .testTag(TAG_LAYER_BASE)
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            if (baseThumbnail != null) {
                Image(
                    bitmap = baseThumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = state.info?.displayName.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )
            Text(
                text = stringResource(Res.string.editor_layer_base_locked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = stringResource(Res.string.editor_layer_locked),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(end = 12.dp)
                .size(20.dp)
        )
    }
}

/** The rename dialog — a nonblank name lands as one undoable step. */
@Composable
private fun RenameLayerDialog(
    layer: PhotoImageLayer,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by rememberSaveable(layer.id) { mutableStateOf(layer.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.editor_layer_rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = {
                    Text(stringResource(Res.string.editor_layer_name_label))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_LAYER_RENAME_FIELD)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(TAG_LAYER_RENAME_CONFIRM)
            ) {
                Text(stringResource(Res.string.editor_layer_rename))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(Res.string.editor_cancel))
            }
        }
    )
}
