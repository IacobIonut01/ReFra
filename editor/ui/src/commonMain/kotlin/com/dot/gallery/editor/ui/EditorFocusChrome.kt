package com.dot.gallery.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_apply
import com.dot.gallery.editor.ui.generated.resources.editor_cancel
import com.dot.gallery.editor.ui.generated.resources.editor_done
import com.dot.gallery.editor.ui.generated.resources.editor_exit_focus
import com.dot.gallery.editor.ui.generated.resources.editor_redo
import com.dot.gallery.editor.ui.generated.resources.editor_undo
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.FullscreenExit
import com.dot.gallery.editor.ui.icons.Redo
import com.dot.gallery.editor.ui.icons.Undo
import org.jetbrains.compose.resources.stringResource

internal const val TAG_FOCUS_EXIT = "editor.focus.exit"
internal const val TAG_FOCUS_DONE = "editor.focus.done"

/**
 * The floating chrome of full-screen focus: a top pill with the exit
 * control, the source name and the live zoom readout; a bottom pill with
 * Undo/Redo, the zoom presets and an honest Done that exits focus. While a
 * tool owns the session draft the Done slot becomes Cancel/Apply for the
 * active tool instead — Undo/Redo keep working on the tool's local
 * history. The bottom row flows to a second line at large text rather
 * than clipping the actions. [toolContent], when given, is the active
 * tool's minimal control (the Light ruler and its label) riding the top
 * of the bottom pill. [onBottomPanelHeight] reports how much of the
 * canvas bottom the pill covers, margin included, so stage labels can
 * stay above it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BoxScope.EditorFocusChrome(
    title: String,
    zoomLabel: String,
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    onExitFocus: () -> Unit,
    zoomMenu: @Composable () -> Unit,
    onToolCancel: () -> Unit = actions.cancelToolEdit,
    onToolApply: () -> Unit = actions.applyToolEdit,
    toolContent: (@Composable () -> Unit)? = null,
    onBottomPanelHeight: (Dp) -> Unit = {}
) {
    val density = LocalDensity.current
    EditorFloatingPanel(
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(16.dp)
            .widthIn(max = 640.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WorkbenchIconButton(
                icon = EditorSymbols.FullscreenExit,
                label = stringResource(Res.string.editor_exit_focus),
                onClick = onExitFocus,
                modifier = Modifier.testTag(TAG_FOCUS_EXIT)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
            Text(
                text = zoomLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp)
            )
        }
    }
    EditorFloatingPanel(
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .onSizeChanged { onBottomPanelHeight(with(density) { it.height.toDp() } + 16.dp) }
            .padding(16.dp)
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .testTag(TAG_FOCUS_BOTTOM_PANEL)
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (toolContent != null) {
                Box(Modifier.padding(top = 12.dp)) { toolContent() }
            }
            // Two groups flowing as units: Undo/Redo/zoom stay start-anchored,
            // the transaction pair (or Done) end-anchored — at large text the
            // pair wraps to a second line together instead of splitting or
            // overflowing the 640 dp cap.
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.Center,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                    zoomMenu()
                }
                if (state.toolEditing) {
                    // The open tool's transaction buttons replace Done — Done
                    // must never silently apply a pending tool. Apply stays
                    // disabled while an interaction is in flight, matching the
                    // session's guard.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = onToolCancel,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag(TAG_TOOL_CANCEL)
                        ) {
                            Text(
                                stringResource(Res.string.editor_cancel),
                                maxLines = 1,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        Button(
                            onClick = onToolApply,
                            enabled = !state.interactionActive,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag(TAG_TOOL_APPLY)
                        ) {
                            Text(
                                stringResource(Res.string.editor_apply),
                                maxLines = 1,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onExitFocus,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(TAG_FOCUS_DONE)
                    ) {
                        Text(
                            stringResource(Res.string.editor_done),
                            maxLines = 1,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

internal const val TAG_FOCUS_BOTTOM_PANEL = "editor.focus.bottomPanel"
