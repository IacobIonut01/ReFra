package com.dot.gallery.editor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_available_tools
import com.dot.gallery.editor.ui.generated.resources.editor_edit_pins
import com.dot.gallery.editor.ui.generated.resources.editor_layer_move_down
import com.dot.gallery.editor.ui.generated.resources.editor_layer_move_up
import com.dot.gallery.editor.ui.generated.resources.editor_layers
import com.dot.gallery.editor.ui.generated.resources.editor_pin
import com.dot.gallery.editor.ui.generated.resources.editor_pin_limit
import com.dot.gallery.editor.ui.generated.resources.editor_pinned_section
import com.dot.gallery.editor.ui.generated.resources.editor_unpin
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.KeyboardArrowDown
import com.dot.gallery.editor.ui.icons.KeyboardArrowUp
import com.dot.gallery.editor.ui.icons.Layers
import com.dot.gallery.editor.ui.icons.PushPin
import org.jetbrains.compose.resources.stringResource

internal const val TAG_PIN_MANAGER = "editor.pinManager"

/**
 * The V3 pin manager — a real [ModalBottomSheet] over the home shell
 * listing the pinned destinations as settings rows (icon, title, the
 * 48 dp Move up / Move down / Unpin actions) then every catalogued tool
 * that can still be pinned with its Pin action. The pill holds at most
 * [MAX_PHOTO_PINS] favourites; Tools is permanent and never listed.
 * Dismiss keeps the pin state — there is no apply step.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoPinManagerSheet(
    workspace: EditorWorkspaceState,
    entries: List<EditorToolEntry<PhotoEditorState, PhotoEditorActions>>,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        ),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        val layersTitle = stringResource(Res.string.editor_layers)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
                .testTag(TAG_PIN_MANAGER)
        ) {
            Text(
                text = stringResource(Res.string.editor_edit_pins),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Text(
                text = stringResource(
                    Res.string.editor_pin_limit, MAX_PHOTO_PINS
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = 20.dp, end = 20.dp, top = 2.dp, bottom = 8.dp
                )
            )
            val pinned = workspace.pinnedPhotoDestinations
            if (pinned.isNotEmpty()) {
                PinSectionTitle(
                    stringResource(Res.string.editor_pinned_section)
                )
                pinned.forEachIndexed { index, destination ->
                    EditorSettingRow(
                        title = pinDestinationTitle(
                            destination, entries, layersTitle
                        ),
                        leading = {
                            Icon(
                                imageVector = pinDestinationIcon(
                                    destination, entries
                                ),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme
                                    .onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        trailing = {
                            PinAction(
                                icon = EditorSymbols.KeyboardArrowUp,
                                label = stringResource(
                                    Res.string.editor_layer_move_up
                                ),
                                enabled = index > 0,
                                onClick = {
                                    workspace.movePhotoPin(
                                        destination, -1
                                    )
                                }
                            )
                            PinAction(
                                icon = EditorSymbols.KeyboardArrowDown,
                                label = stringResource(
                                    Res.string.editor_layer_move_down
                                ),
                                enabled = index < pinned.lastIndex,
                                onClick = {
                                    workspace.movePhotoPin(
                                        destination, 1
                                    )
                                }
                            )
                            PinAction(
                                icon = EditorSymbols.PushPin,
                                label = stringResource(
                                    Res.string.editor_unpin
                                ),
                                enabled = true,
                                onClick = {
                                    workspace.togglePhotoPin(destination)
                                }
                            )
                        }
                    )
                }
            }
            val unpinnedTools = entries.filter {
                it.catalogued &&
                    it.descriptor.id.value in PHOTO_PINNABLE_DESTINATIONS &&
                    it.descriptor.id.value !in pinned
            }
            val layersUnpinned = PHOTO_PIN_LAYERS !in pinned
            if (unpinnedTools.isNotEmpty() || layersUnpinned) {
                PinSectionTitle(
                    stringResource(Res.string.editor_available_tools)
                )
                if (layersUnpinned) {
                    PinnableRow(
                        title = layersTitle,
                        icon = EditorSymbols.Layers,
                        enabled = pinned.size < MAX_PHOTO_PINS,
                        onPin = {
                            workspace.togglePhotoPin(PHOTO_PIN_LAYERS)
                        }
                    )
                }
                unpinnedTools.forEach { entry ->
                    PinnableRow(
                        title = stringResource(entry.descriptor.title),
                        icon = entry.descriptor.icon,
                        enabled = pinned.size < MAX_PHOTO_PINS,
                        onPin = {
                            workspace.togglePhotoPin(
                                entry.descriptor.id.value
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PinSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp
        )
    )
}

@Composable
private fun PinnableRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onPin: () -> Unit
) {
    EditorSettingRow(
        title = title,
        leading = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        },
        trailing = {
            PinAction(
                icon = EditorSymbols.PushPin,
                label = stringResource(Res.string.editor_pin),
                enabled = enabled,
                onClick = onPin
            )
        }
    )
}

@Composable
private fun PinAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    WorkbenchIconButton(
        icon = icon,
        label = label,
        onClick = onClick,
        enabled = enabled
    )
}

@Composable
private fun pinDestinationTitle(
    destination: String,
    entries: List<EditorToolEntry<PhotoEditorState, PhotoEditorActions>>,
    layersTitle: String
): String = if (destination == PHOTO_PIN_LAYERS) {
    layersTitle
} else {
    entries.firstOrNull { it.descriptor.id.value == destination }
        ?.let { stringResource(it.descriptor.title) } ?: destination
}

private fun pinDestinationIcon(
    destination: String,
    entries: List<EditorToolEntry<PhotoEditorState, PhotoEditorActions>>
): androidx.compose.ui.graphics.vector.ImageVector =
    if (destination == PHOTO_PIN_LAYERS) {
        EditorSymbols.Layers
    } else {
        entries.firstOrNull { it.descriptor.id.value == destination }
            ?.descriptor?.icon ?: EditorSymbols.Layers
    }

/**
 * The local settings row — the label/summary/trailing hierarchy of the
 * app's setting rows without a dependency on `app:core:ui` (the editor's
 * desktop target cannot consume it). Rows are 56 dp minimum with a
 * 16 dp horizontal inset and an optional leading slot.
 */
@Composable
internal fun EditorSettingRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = title,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}
