package com.dot.gallery.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_focus
import com.dot.gallery.editor.ui.generated.resources.editor_layers
import com.dot.gallery.editor.ui.generated.resources.editor_panel_edits
import com.dot.gallery.editor.ui.generated.resources.editor_panel_source
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.Fullscreen
import com.dot.gallery.editor.ui.icons.Info
import com.dot.gallery.editor.ui.icons.Layers
import com.dot.gallery.editor.ui.icons.ViewList
import org.jetbrains.compose.resources.stringResource

/**
 * The 80 dp wide-layout tool rail — the registered tools stacked first,
 * then the Source/Edits destinations behind a divider, then Focus pinned
 * at the bottom. Icon controls on chrome; the selected destination keeps
 * its tinted field. The rail only reads descriptors, so it serves both
 * photo and video registries.
 */
@Composable
internal fun <S, A> EditorToolRail(
    entries: List<EditorToolEntry<S, A>>,
    state: S,
    workspace: EditorWorkspaceState,
    enabled: Boolean,
    onSelectTool: (EditorToolId) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(80.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        entries.filter { it.catalogued }.forEach { entry ->
            WorkbenchIconButton(
                icon = entry.descriptor.icon,
                label = stringResource(entry.descriptor.title),
                onClick = { onSelectTool(entry.descriptor.id) },
                enabled = enabled && entry.available(state),
                selected = workspace.panel == EditorWorkspacePanel.Edit &&
                    workspace.activeTool == entry.descriptor.id,
                modifier = Modifier
                    .testTag(entry.tag)
                    .padding(vertical = 4.dp)
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        WorkbenchIconButton(
            icon = EditorSymbols.Info,
            label = stringResource(Res.string.editor_panel_source),
            onClick = { workspace.togglePanel(EditorWorkspacePanel.Source) },
            enabled = enabled,
            selected = workspace.panel == EditorWorkspacePanel.Source,
            modifier = Modifier
                .testTag(TAG_PANEL_SOURCE)
                .padding(vertical = 4.dp)
        )
        WorkbenchIconButton(
            icon = EditorSymbols.ViewList,
            label = stringResource(Res.string.editor_panel_edits),
            onClick = { workspace.togglePanel(EditorWorkspacePanel.Edits) },
            enabled = enabled,
            selected = workspace.panel == EditorWorkspacePanel.Edits,
            modifier = Modifier
                .testTag(TAG_PANEL_EDITS)
                .padding(vertical = 4.dp)
        )
        WorkbenchIconButton(
            icon = EditorSymbols.Layers,
            label = stringResource(Res.string.editor_layers),
            onClick = { workspace.togglePanel(EditorWorkspacePanel.Layers) },
            enabled = enabled,
            selected = workspace.panel == EditorWorkspacePanel.Layers,
            modifier = Modifier
                .testTag(TAG_LAYERS)
                .padding(vertical = 4.dp)
        )
        Spacer(Modifier.weight(1f).height(8.dp))
        WorkbenchIconButton(
            icon = EditorSymbols.Fullscreen,
            label = stringResource(Res.string.editor_focus),
            onClick = { workspace.focusMode = true },
            enabled = enabled,
            modifier = Modifier
                .testTag(TAG_FOCUS)
                .padding(vertical = 4.dp)
        )
    }
}
