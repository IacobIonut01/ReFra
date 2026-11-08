package com.dot.gallery.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import com.dot.gallery.editor.ui.generated.resources.editor_compare
import com.dot.gallery.editor.ui.generated.resources.editor_export_heif
import com.dot.gallery.editor.ui.generated.resources.editor_export_jpeg
import com.dot.gallery.editor.ui.generated.resources.editor_export_jxl
import com.dot.gallery.editor.ui.generated.resources.editor_raw_export_action
import com.dot.gallery.editor.ui.generated.resources.editor_raw_export_tiff16
import com.dot.gallery.editor.ui.generated.resources.editor_raw_export_tiff8
import com.dot.gallery.editor.ui.generated.resources.editor_export_png
import com.dot.gallery.editor.ui.generated.resources.editor_export_webp
import com.dot.gallery.editor.ui.generated.resources.editor_more_options
import com.dot.gallery.editor.ui.generated.resources.editor_redo
import com.dot.gallery.editor.ui.generated.resources.editor_reset_edits
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
import com.dot.gallery.editor.ui.icons.MoreVert
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
 * The photo top chrome on the shared 56 dp workbench header: Back, source
 * name with the output dimensions of the current geometry, Undo, Redo, the
 * overflow (Reset edits stays reachable there) and the one filled action —
 * Save copy. At ≥1.5× font on a narrow window the title and actions stack
 * into two rows so Save is never pushed off the edge.
 */
@Composable
internal fun EditorTopBar(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    widthDp: Float,
    compact: Boolean,
    onBack: () -> Unit
) {
    val fontScale = LocalDensity.current.fontScale
    val stacked = fontScale >= 1.5f && widthDp < 700f
    WorkbenchHeader(
        title = state.info?.displayName.orEmpty(),
        onBack = onBack,
        backTag = TAG_BACK,
        stacked = stacked
    ) {
        var menuOpen by remember { mutableStateOf(false) }
        if (!compact) {
            WorkbenchIconButton(
                icon = EditorSymbols.Undo,
                label = stringResource(Res.string.editor_undo),
                onClick = actions.undo,
                enabled = state.canUndo,
                modifier = Modifier.testTag(TAG_UNDO)
            )
            WorkbenchIconButton(
                icon = EditorSymbols.Redo,
                label = stringResource(Res.string.editor_redo),
                onClick = actions.redo,
                enabled = state.canRedo,
                modifier = Modifier.testTag(TAG_REDO)
            )
        }
        Box {
            WorkbenchIconButton(
                icon = EditorSymbols.MoreVert,
                label = stringResource(Res.string.editor_more_options),
                onClick = { menuOpen = true },
                enabled = state.document != null,
                modifier = Modifier.testTag(TAG_OVERFLOW)
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                DropdownMenuItem(
                    text = {
                        Text(stringResource(Res.string.editor_reset_edits))
                    },
                    enabled = state.document != null &&
                        !state.interactionActive,
                    onClick = {
                        menuOpen = false
                        actions.reset()
                    },
                    modifier = Modifier.testTag(TAG_RESET_ALL)
                )
                PhotoExportMenuItems(actions) { menuOpen = false }
            }
        }
        if (compact) {
            WorkbenchFilledIconButton(
                icon = EditorSymbols.Check,
                label = stringResource(Res.string.editor_save_copy),
                onClick = actions.saveCopy,
                enabled = state.document != null && !state.exporting &&
                    !state.isDrafting && !state.opening,
                modifier = Modifier.testTag(TAG_SAVE)
            )
        } else {
            WorkbenchPrimaryAction(
                label = stringResource(Res.string.editor_save_copy),
                onClick = actions.saveCopy,
                enabled = state.document != null && !state.exporting &&
                    !state.isDrafting && !state.opening,
                modifier = Modifier.testTag(TAG_SAVE)
            )
        }
    }
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
 * 48 dp glass Close circle, the commands capsule (Undo, Redo, a 1 dp
 * divider, Compare) and the single filled Save copy pill. Close funnels
 * the shared [onBack] unwind (draft, dirty confirm, export); Undo/Redo
 * gate on the session's history; the compare toggle drives the
 * viewport's split compare.
 *
 * The same slot carries the document's Save (enabled only while the
 * session is dirty, D8) and — while a tool owns the draft — that tool's
 * single commit, so the card itself never needs a footer row. Tap the Save
 * pill for its options; tap Apply to commit and collapse.
 */
@Composable
internal fun EditorHomeHeader(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    viewport: EditorViewportState,
    imageReady: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    /** Present while a tool owns the draft: the commit label and target. */
    onApply: (() -> Unit)? = null,
    applyLabel: String? = null,
    /** Optional second save action: write the copy and open it. */
    onSaveAndOpen: (() -> Unit)? = null
) {
    val closeLabel = stringResource(Res.string.editor_close)
    val saveLabel = stringResource(Res.string.editor_save)
    val saveEnabled = state.document != null && state.isDirty &&
        !state.exporting && !state.isDrafting && !state.opening
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
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
        EditorFloatingPanel(
            shape = CircleShape,
            modifier = Modifier.height(48.dp)
        ) {
            Row(
                modifier = Modifier
                    .height(48.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WorkbenchIconButton(
                    icon = EditorSymbols.Undo,
                    label = stringResource(Res.string.editor_undo),
                    onClick = actions.undo,
                    enabled = state.canUndo,
                    modifier = Modifier.testTag(TAG_UNDO)
                )
                WorkbenchIconButton(
                    icon = EditorSymbols.Redo,
                    label = stringResource(Res.string.editor_redo),
                    onClick = actions.redo,
                    enabled = state.canRedo,
                    modifier = Modifier.testTag(TAG_REDO)
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = 1.dp, height = 20.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurface
                                .copy(alpha = 0.24f)
                        )
                )
                // The compare toggle — the Before chip's tag and switch
                // semantics ride the icon so the viewport compare contract
                // stays one node.
                WorkbenchIconButton(
                    icon = EditorSymbols.Compare,
                    label = stringResource(Res.string.editor_compare),
                    onClick = {
                        viewport.compareSplit = !viewport.compareSplit
                    },
                    enabled = imageReady,
                    selected = viewport.compareSplit,
                    modifier = Modifier.testTag(TAG_BEFORE_CHIP)
                )
            }
        }
        if (onApply != null && applyLabel != null) {
            EditorApplyPill(
                label = applyLabel,
                enabled = !state.interactionActive,
                onApply = onApply
            )
        } else {
            EditorSavePill(
                actions = actions,
                enabled = saveEnabled,
                onSave = onSave,
                onSaveAndOpen = onSaveAndOpen,
                rawSource = state.info?.isRaw == true,
                label = saveLabel
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
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = EditorSymbols.Check,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
        Text(
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
    rawSource: Boolean = false
) {
    val container = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        // Dark chrome, not a light overlay: the pill sits on the photo.
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (enabled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    }
    // At large text the label would push the pill under the commands
    // capsule; the pill keeps its icon and the action's name stays on the
    // icon's content description.
    val showLabel = LocalDensity.current.fontScale < 1.5f
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
                .padding(horizontal = if (showLabel) 16.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = EditorSymbols.Check,
                contentDescription = if (showLabel) null else menuLabel,
                tint = content,
                modifier = Modifier.size(20.dp)
            )
            if (showLabel) Text(
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
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.editor_save_copy)) },
                onClick = {
                    menuOpen = false
                    onSave()
                },
                modifier = Modifier.testTag(TAG_SAVE_MENU_COPY)
            )
            PhotoExportMenuItems(actions) { menuOpen = false }
            if (onSaveAndOpen != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.editor_save_and_open)) },
                    onClick = {
                        menuOpen = false
                        onSaveAndOpen()
                    },
                    modifier = Modifier.testTag(TAG_SAVE_MENU_OPEN)
                )
            }
        }
    }
}
