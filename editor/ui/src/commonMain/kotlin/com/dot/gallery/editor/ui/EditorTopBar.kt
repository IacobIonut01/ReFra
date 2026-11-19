package com.dot.gallery.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.PhotoExportFormat
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_close
import com.dot.gallery.editor.ui.generated.resources.editor_show_history
import com.dot.gallery.editor.ui.generated.resources.editor_compare
import com.dot.gallery.editor.ui.generated.resources.editor_export_heif
import com.dot.gallery.editor.ui.generated.resources.editor_export_jpeg
import com.dot.gallery.editor.ui.generated.resources.editor_export_jxl
import com.dot.gallery.editor.ui.generated.resources.editor_raw_export_action
import com.dot.gallery.editor.ui.generated.resources.editor_raw_export_tiff16
import com.dot.gallery.editor.ui.generated.resources.editor_raw_export_tiff8
import com.dot.gallery.editor.ui.generated.resources.editor_export_png
import com.dot.gallery.editor.ui.generated.resources.editor_export_webp
import com.dot.gallery.editor.ui.generated.resources.editor_redo
import com.dot.gallery.editor.ui.generated.resources.editor_save
import com.dot.gallery.editor.ui.generated.resources.editor_save_and_open
import com.dot.gallery.editor.ui.generated.resources.editor_save_copy
import com.dot.gallery.editor.ui.generated.resources.editor_save_menu
import com.dot.gallery.editor.ui.generated.resources.editor_undo
import com.dot.gallery.editor.ui.icons.Check
import com.dot.gallery.editor.ui.icons.Close
import com.dot.gallery.editor.ui.icons.Compare
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.KeyboardArrowDown
import com.dot.gallery.editor.ui.icons.Redo
import com.dot.gallery.editor.ui.icons.Undo
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal const val TAG_BACK = "editor.back"
internal const val TAG_UNDO = "editor.undo"
internal const val TAG_REDO = "editor.redo"
internal const val TAG_SAVE = "editor.save"
internal const val TAG_SAVE_MENU_COPY = "editor.save.menu.copy"
internal const val TAG_SAVE_MENU_OPEN = "editor.save.menu.open"
internal const val TAG_SAVE_CHEVRON = "editor.save.chevron"

/** One explicit-format save item per [PhotoExportFormat], tagged by name. */
internal const val TAG_SAVE_MENU_FORMAT_PREFIX = "editor.save.menu.format."
internal const val TAG_OVERFLOW = "editor.overflow"
internal const val TAG_RESET_ALL = "editor.resetAll"

/**
 * The explicit export containers as menu items, one per
 * [PhotoEditorActions.exportFormats], in a stable order. Shared by every
 * save/overflow menu so the format list never drifts between surfaces; a
 * host that writes only the source container contributes nothing.
 */
@Composable
internal fun ColumnScope.PhotoExportMenuItems(
    actions: PhotoEditorActions,
    onDismiss: () -> Unit
) {
    for (format in actions.exportFormats.sortedBy { it.ordinal }) {
        DropdownMenuItem(
            text = { Text(stringResource(exportFormatLabel(format))) },
            onClick = {
                onDismiss()
                actions.exportCopy(format)
            },
            modifier = Modifier.testTag(TAG_SAVE_MENU_FORMAT_PREFIX + format.name.lowercase())
        )
    }
}

private fun exportFormatLabel(format: PhotoExportFormat): StringResource = when (format) {
    PhotoExportFormat.JPEG -> Res.string.editor_export_jpeg
    PhotoExportFormat.PNG -> Res.string.editor_export_png
    PhotoExportFormat.WEBP -> Res.string.editor_export_webp
    PhotoExportFormat.HEIF -> Res.string.editor_export_heif
    PhotoExportFormat.JXL -> Res.string.editor_export_jxl
    PhotoExportFormat.TIFF_8 -> Res.string.editor_raw_export_tiff8
    PhotoExportFormat.TIFF_16 -> Res.string.editor_raw_export_tiff16
}

/**
 * The floating glass capsule the compact chrome rides in — one 56 dp
 * capsule material for every compact header variant (the home command
 * header, the tool context header). The shape and blur are shared; the
 * slots differ per surface, so callers own their row content.
 */
@Composable
internal fun EditorFloatingHeaderCapsule(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .chromeBlur(RoundedCornerShape(26.dp))
            .heightIn(min = 56.dp)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * The compact home's header — the approved Stitch anatomy: a separate
 * 48 dp glass Close circle and the mode switch leading, the commands
 * (an Undo/Redo capsule and a Compare circle) centred, and the save
 * action trailing — the labelled split pill on wide windows, the split
 * check + chevron under 400 dp or at 1.5× text. Close funnels the
 * shared [onBack] unwind (draft, dirty confirm, export); Undo/Redo
 * gate on the session's history; the compare toggle drives the
 * viewport's split compare.
 *
 * The same slot carries the document's Save (enabled only while the
 * session is dirty, D8) and — while a tool owns the draft — that tool's
 * single commit, so the card itself never needs a footer row. Tap the
 * chevron for the save options; the check saves the copy directly.
 */
@Composable
internal fun EditorHomeHeader(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    viewport: EditorViewportState,
    imageReady: Boolean,
    widthDp: Float,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    /** Present while a tool owns the draft: the commit label and target. */
    onApply: (() -> Unit)? = null,
    applyLabel: String? = null,
    /** Optional second save action: write the copy and open it. */
    onSaveAndOpen: (() -> Unit)? = null,
    /** Long-pressing Undo opens the history sheet. */
    onShowHistory: (() -> Unit)? = null
) {
    val closeLabel = stringResource(Res.string.editor_close)
    val saveLabel = stringResource(Res.string.editor_save)
    // An open tool does not block Save: saving keeps the tool's changes
    // first. Only a gesture still in flight does. While a tool is open,
    // canUndo is the tool's own history, so it says whether the tool has
    // changed anything yet.
    val saveEnabled = state.document != null &&
        (state.isDirty || (state.toolEditing && state.canUndo)) &&
        !state.exporting && !state.interactionActive && !state.opening
    // Under 400 dp — or at 1.5× text on any compact window — the fixed
    // controls leave no room for the labelled pill, so the action
    // collapses to its icon form.
    val labelledAction = LocalDensity.current.fontScale < 1.5f && widthDp >= 400f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EditorFloatingPanel(
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                WorkbenchIconButton(
                    icon = EditorSymbols.Close,
                    label = closeLabel,
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag(TAG_BACK)
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EditorFloatingPanel(
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Row(modifier = Modifier.height(48.dp)) {
                    WorkbenchIconButton(
                        icon = EditorSymbols.Undo,
                        label = stringResource(Res.string.editor_undo),
                        onClick = actions.undo,
                        enabled = state.canUndo,
                        modifier = Modifier.testTag(TAG_UNDO),
                        onLongClick = onShowHistory,
                        onLongClickLabel = stringResource(Res.string.editor_show_history)
                    )
                    WorkbenchIconButton(
                        icon = EditorSymbols.Redo,
                        label = stringResource(Res.string.editor_redo),
                        onClick = actions.redo,
                        enabled = state.canRedo,
                        modifier = Modifier.testTag(TAG_REDO)
                    )
                }
            }
            EditorFloatingPanel(
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                // The compare toggle — the Before chip's tag and switch
                // semantics ride the icon so the viewport compare
                // contract stays one node.
                WorkbenchIconButton(
                    icon = EditorSymbols.Compare,
                    label = stringResource(Res.string.editor_compare),
                    onClick = {
                        viewport.compareSplit = !viewport.compareSplit
                    },
                    enabled = imageReady,
                    selected = viewport.compareSplit,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag(TAG_BEFORE_CHIP)
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (onApply != null && applyLabel != null) {
            EditorApplyPill(
                label = applyLabel,
                enabled = !state.interactionActive,
                onApply = onApply,
                showLabel = labelledAction
            )
        } else if (labelledAction) {
            EditorSavePill(
                actions = actions,
                enabled = saveEnabled,
                onSave = onSave,
                onSaveAndOpen = onSaveAndOpen,
                rawSource = state.info?.isRaw == true,
                label = saveLabel,
                tonal = state.toolEditing
            )
        } else {
            EditorSaveIconSplit(
                actions = actions,
                enabled = saveEnabled,
                onSave = onSave,
                onSaveAndOpen = onSaveAndOpen,
                rawSource = state.info?.isRaw == true,
                label = saveLabel,
                tonal = state.toolEditing
            )
        }
    }
}

/**
 * The card's commit, owned by the document header while a tool is open:
 * a filled pill that applies the draft and collapses the tool. The card
 * therefore carries no footer row of its own.
 */
@Composable
private fun EditorApplyPill(
    label: String,
    enabled: Boolean,
    onApply: () -> Unit,
    showLabel: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (enabled) MaterialTheme.colorScheme.primary
                // The header floats over the photo: a light disabled
                // overlay washes out against a bright image, so the
                // disabled pill keeps the chrome's dark tonal surface.
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .testTag(TAG_TOOL_APPLY)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
                onClick = onApply
            )
            .padding(horizontal = if (showLabel) 16.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = EditorSymbols.Check,
            contentDescription = if (showLabel) null else label,
            tint = if (enabled) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
        if (showLabel) Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * The document's save action: a filled pill that opens its options on a
 * tap — the copy itself, or the copy plus opening it. There is no
 * overwrite (D2).
 */
@Composable
private fun EditorSavePill(
    actions: PhotoEditorActions,
    enabled: Boolean,
    onSave: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    onSaveAndOpen: (() -> Unit)? = null,
    rawSource: Boolean = false,
    tonal: Boolean = false
) {
    val filled = enabled && !tonal
    val container = if (filled) {
        MaterialTheme.colorScheme.primary
    } else {
        // Dark chrome, not a light overlay: the pill sits on the photo.
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (filled) {
        MaterialTheme.colorScheme.onPrimary
    } else if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    }
    val menuLabel = stringResource(Res.string.editor_save_menu)
    var menuOpen by remember { mutableStateOf(false) }
    val pillLabel = if (rawSource) stringResource(Res.string.editor_raw_export_action) else label
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(container)
                .testTag(TAG_SAVE)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = menuLabel,
                    // A RAW has no container to follow: its one action asks.
                    onClick = { if (rawSource) onSave() else menuOpen = true }
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = EditorSymbols.Check,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = pillLabel,
                style = MaterialTheme.typography.labelLarge,
                color = content,
                maxLines = 1,
                softWrap = false
            )
            if (!rawSource) Icon(
                imageVector = EditorSymbols.KeyboardArrowDown,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            EditorSaveMenuItems(
                actions = actions,
                onSave = onSave,
                onSaveAndOpen = onSaveAndOpen,
                onDismiss = { menuOpen = false }
            )
        }
    }
}

/**
 * The save action's narrow form: a filled 48 dp check that saves the
 * copy directly and a 32 dp chevron that opens the same options menu —
 * the two halves carry the pill's split-corner radii so they read as
 * one control. Same tag, enabled rules and semantics labels as the
 * labelled pill.
 */
@Composable
private fun EditorSaveIconSplit(
    actions: PhotoEditorActions,
    enabled: Boolean,
    onSave: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    onSaveAndOpen: (() -> Unit)? = null,
    rawSource: Boolean = false,
    tonal: Boolean = false
) {
    val filled = enabled && !tonal
    val container = if (filled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (filled) {
        MaterialTheme.colorScheme.onPrimary
    } else if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    }
    val menuLabel = stringResource(Res.string.editor_save_menu)
    var menuOpen by remember { mutableStateOf(false) }
    val pillLabel = if (rawSource) stringResource(Res.string.editor_raw_export_action) else label
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 24.dp, topEnd = 6.dp,
                        bottomEnd = 6.dp, bottomStart = 24.dp
                    )
                )
                .background(container)
                .testTag(TAG_SAVE)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = pillLabel,
                    onClick = onSave
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = EditorSymbols.Check,
                contentDescription = pillLabel,
                tint = content,
                modifier = Modifier.size(24.dp)
            )
        }
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(48.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 6.dp, topEnd = 24.dp,
                        bottomEnd = 24.dp, bottomStart = 6.dp
                    )
                )
                .background(container)
                .testTag(TAG_SAVE_CHEVRON)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = menuLabel,
                    onClick = { menuOpen = true }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = EditorSymbols.KeyboardArrowDown,
                contentDescription = menuLabel,
                tint = content,
                modifier = Modifier.size(20.dp)
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                EditorSaveMenuItems(
                    actions = actions,
                    onSave = onSave,
                    onSaveAndOpen = onSaveAndOpen,
                    onDismiss = { menuOpen = false }
                )
            }
        }
    }
}

/** The save options both save forms open: the copy, the formats, open. */
@Composable
private fun ColumnScope.EditorSaveMenuItems(
    actions: PhotoEditorActions,
    onSave: () -> Unit,
    onSaveAndOpen: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.editor_save_copy)) },
        onClick = {
            onDismiss()
            onSave()
        },
        modifier = Modifier.testTag(TAG_SAVE_MENU_COPY)
    )
    PhotoExportMenuItems(actions, onDismiss)
    if (onSaveAndOpen != null) {
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.editor_save_and_open)) },
            onClick = {
                onDismiss()
                onSaveAndOpen()
            },
            modifier = Modifier.testTag(TAG_SAVE_MENU_OPEN)
        )
    }
}
