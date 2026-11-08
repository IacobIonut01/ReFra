package com.dot.gallery.editor.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_edit_pins
import com.dot.gallery.editor.ui.generated.resources.editor_group_adjust
import com.dot.gallery.editor.ui.generated.resources.editor_group_all
import com.dot.gallery.editor.ui.generated.resources.editor_group_compose
import com.dot.gallery.editor.ui.generated.resources.editor_group_retouch
import com.dot.gallery.editor.ui.generated.resources.editor_pin
import com.dot.gallery.editor.ui.generated.resources.editor_search_tools
import com.dot.gallery.editor.ui.generated.resources.editor_unpin
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.PushPin
import com.dot.gallery.editor.ui.icons.ZoomIn
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal const val TAG_CATALOGUE_SEARCH = "editor.catalogue.search"
internal const val TAG_CATALOGUE_GROUPS = "editor.catalogue.groups"
internal const val TAG_CATALOGUE_HANDLE = "editor.catalogue.handle"
internal const val TAG_CATALOGUE_LAYERS = "editor.catalogue.layers"

/**
 * A non-registry cell the sheet can host next to the tool grid — the
 * phone catalogue uses it for the Layers workspace, which is a surface
 * rather than an [EditorToolId].
 */
internal data class EditorCatalogueExtraCell(
    val icon: ImageVector,
    val label: String,
    val tag: String,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/**
 * The Tools catalogue — the registered entries as large labelled cells.
 * Choosing a tool selects it and closes the catalogue; the active tool
 * keeps its selected tint so the grid reads as state, not a menu. The
 * grid only reads descriptors, so the same catalogue serves both photo
 * and video registries.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <S, A> EditorToolCatalogue(
    entries: List<EditorToolEntry<S, A>>,
    state: S,
    activeTool: EditorToolId,
    enabled: Boolean,
    onSelect: (EditorToolId) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_CATALOGUE_GRID)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        entries.filter { it.catalogued }.forEach { entry ->
            WorkbenchDockItem(
                icon = entry.descriptor.icon,
                label = stringResource(entry.descriptor.title),
                selected = activeTool == entry.descriptor.id,
                onClick = { onSelect(entry.descriptor.id) },
                enabled = enabled && entry.available(state),
                modifier = Modifier
                    .width(96.dp)
                    .testTag(entry.tag)
            )
        }
    }
}

/**
 * The Figma V3 phone catalogue sheet — an optional title over a 44 dp
 * search field, the 40 dp All/Adjust/Retouch/Compose category group and
 * the entries in three equal columns with 8 dp gaps on 76 dp cells; the
 * cell label is a centred two-line 12/14 sp text. The sheet is 16
 * dp-inset with an 8 dp top pad. Search and category filter the current
 * registry only — empty categories paint disabled, and unimplemented
 * tools are never listed.
 */
@Composable
internal fun <S, A> EditorToolCatalogueSheet(
    entries: List<EditorToolEntry<S, A>>,
    state: S,
    activeTool: EditorToolId,
    enabled: Boolean,
    onSelect: (EditorToolId) -> Unit,
    modifier: Modifier = Modifier,
    categories: List<Pair<StringResource, EditorToolGroup?>> = listOf(
        Res.string.editor_group_all to null,
        Res.string.editor_group_adjust to EditorToolGroup.Adjust,
        Res.string.editor_group_retouch to EditorToolGroup.Retouch,
        Res.string.editor_group_compose to EditorToolGroup.Compose
    ),
    initialGroup: EditorToolGroup? = null,
    // The sheet title (Figma's "All tools") — absent on surfaces that
    // carry no title.
    title: String? = null,
    // V3 pinning — the destinations already on the compact pill, the
    // toggle for each cell's 48 dp pin action and the row's Edit pins
    // affordance. Absent by default: the video catalogue never pins.
    pinnedIds: Set<EditorToolId> = emptySet(),
    onTogglePin: ((EditorToolId) -> Unit)? = null,
    onEditPins: (() -> Unit)? = null,
    // Modal hosts draw the real DragHandle themselves — the sheet's own
    // start handle only belongs on non-modal surfaces.
    showDragHandle: Boolean = true,
    // A non-blank query searches every category when true — the video
    // catalogue has no All category to fall back to.
    searchAllGroups: Boolean = false,
    // Real non-tool surfaces (the Layers workspace) that ride the grid.
    // They belong to the All category and follow the search field.
    extraCells: List<EditorCatalogueExtraCell> = emptyList(),
    // Destinations that the pin affordance may offer — fixed slots (the
    // dock's Colour) are real tools but not pinnable, so their cells
    // never show a dead toggle.
    isPinnable: (EditorToolId) -> Boolean = { true }
) {
    var query by rememberSaveable { mutableStateOf("") }
    // Null = All; the Figma categories map onto the registry's groups.
    var group by rememberSaveable { mutableStateOf(initialGroup) }
    // Contextual-only entries (the layer mask brush) dispatch through the
    // registry but never occupy a catalogue cell.
    val catalogued = entries.filter { it.catalogued }
    val titles = catalogued.associate {
        it.descriptor.id to stringResource(it.descriptor.title)
    }
    val extraShown = extraCells.filter { cell ->
        group == null && (query.isBlank() ||
            cell.label.contains(query, ignoreCase = true))
    }
    val shown = catalogued.filter { entry ->
        (group == null || entry.descriptor.group == group ||
            (searchAllGroups && query.isNotBlank())) &&
            (query.isBlank() ||
                titles[entry.descriptor.id]?.contains(query, ignoreCase = true)
                    == true)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)
    ) {
        // The start-aligned 36×4 handle (Figma has it at the sheet's
        // content start, not centred). Modal hosts suppress it — their
        // scaffold already draws the handle.
        if (showDragHandle) {
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .testTag(TAG_CATALOGUE_HANDLE)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant
                            .copy(alpha = 0.4f)
                    )
            )
        }
        if (title != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(48.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                if (onEditPins != null) {
                    WorkbenchIconButton(
                        icon = EditorSymbols.PushPin,
                        label = stringResource(Res.string.editor_edit_pins),
                        onClick = onEditPins,
                        modifier = Modifier.testTag(TAG_EDITOR_PINS_EDIT)
                    )
                } else {
                    Spacer(Modifier.width(48.dp))
                }
            }
        }
        // The 44 dp search field — a real filter over the registry, not a
        // decorative affordance.
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(22.dp))
                .editorSheetItemSurface(RoundedCornerShape(22.dp))
                .testTag(TAG_CATALOGUE_SEARCH),
            decorationBox = { inner ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = EditorSymbols.ZoomIn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(
                                    Res.string.editor_search_tools
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        inner()
                    }
                }
            }
        )
        // The 40 dp four-equal-width category group — All plus the Figma
        // categories — with the right-aligned Edit pins affordance on
        // sheets that wire pinning. An empty category stays visible but
        // disabled.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .heightIn(min = 48.dp)
                .testTag(TAG_CATALOGUE_GROUPS),
            verticalAlignment = Alignment.CenterVertically
        ) {
            categories.forEach { (labelRes, category) ->
                val selected = group == category
                val hasEntries =
                    category == null || catalogued.any {
                        it.descriptor.group == category
                    }
                CatalogueCategoryCell(
                    label = stringResource(labelRes),
                    selected = selected,
                    enabled = hasEntries,
                    onClick = { group = category },
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag(
                            "editor.catalogue.group." +
                                (category?.name ?: "all")
                        )
                )
            }
            if (onEditPins != null && title == null) {
                Text(
                    text = stringResource(Res.string.editor_edit_pins),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = stringResource(
                                Res.string.editor_edit_pins
                            ),
                            onClick = onEditPins
                        )
                        .testTag(TAG_EDITOR_PINS_EDIT)
                        .padding(start = 12.dp)
                )
            }
        }
        // Three equal columns, 8 dp gaps, 76 dp raised cells.
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 8.dp)
                .testTag(TAG_CATALOGUE_GRID),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(shown, key = { it.descriptor.id.value }) { entry ->
                Box {
                    CatalogueToolCell(
                        icon = entry.descriptor.icon,
                        label = titles[entry.descriptor.id].orEmpty(),
                        selected = activeTool == entry.descriptor.id,
                        onClick = { onSelect(entry.descriptor.id) },
                        // A long press on the cell is the pin gesture —
                        // the corner toggle stays as the visible
                        // affordance for the same action.
                        onLongClick = onTogglePin
                            ?.takeIf { isPinnable(entry.descriptor.id) }
                            ?.let { toggle ->
                                { toggle(entry.descriptor.id) }
                            },
                        enabled = enabled && entry.available(state),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                            .testTag(entry.tag)
                    )
                    if (onTogglePin != null &&
                        isPinnable(entry.descriptor.id)
                    ) {
                        val pinned = entry.descriptor.id in pinnedIds
                        val pinLabel = stringResource(
                            if (pinned) Res.string.editor_unpin
                            else Res.string.editor_pin
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                // The 40 dp toggle stays fully inside
                                // the cell — a 48 dp corner box reaches
                                // the centre at cell widths under ≈96 dp
                                // (a 320 dp window) and would swallow
                                // the tap that should select the tool.
                                .size(40.dp)
                                .semantics { this.selected = pinned }
                                .clickable(
                                    enabled = enabled,
                                    role = Role.Button,
                                    onClickLabel = pinLabel,
                                    onClick = {
                                        onTogglePin(entry.descriptor.id)
                                    }
                                )
                                .testTag(
                                    cataloguePinTag(
                                        entry.descriptor.id.value
                                    )
                                ),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            Icon(
                                imageVector = EditorSymbols.PushPin,
                                contentDescription = pinLabel,
                                tint = when {
                                    !enabled ->
                                        MaterialTheme.colorScheme
                                            .onSurface.copy(alpha = 0.38f)
                                    pinned ->
                                        MaterialTheme.colorScheme.primary
                                    else ->
                                        MaterialTheme.colorScheme
                                            .onSurfaceVariant
                                },
                                modifier = Modifier
                                    .padding(top = 6.dp, end = 6.dp)
                                    .size(16.dp)
                            )
                        }
                    }
                }
            }
            items(extraShown, key = { "extra:${it.tag}" }) { cell ->
                CatalogueToolCell(
                    icon = cell.icon,
                    label = cell.label,
                    selected = cell.selected,
                    onClick = cell.onClick,
                    enabled = enabled && cell.enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp)
                        .testTag(cell.tag)
                )
            }
        }
    }
}

/**
 * One equal-width cell of the catalogue's 40 dp category group — the
 * selected cell is the tighter 16 dp radius on the filled secondary
 * container, unselected cells are the 20 dp neutral shape.
 */
@Composable
private fun CatalogueCategoryCell(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(
                RoundedCornerShape(if (selected) 16.dp else 20.dp)
            )
            .editorSheetItemSurface(
                RoundedCornerShape(if (selected) 16.dp else 20.dp),
                baseColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .clickable(
                enabled = enabled,
                role = Role.Tab,
                onClickLabel = label,
                onClick = onClick
            )
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurface
                    .copy(alpha = 0.38f)
                selected -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1
        )
    }
}

/**
 * One 76 dp raised catalogue cell — filled `surfaceContainerHigh` with
 * 14 dp corners, the 24 dp tool icon over its visible label. The selected
 * state paints the secondary container; nothing else changes. This is
 * the sheet's own anatomy, deliberately not the shared dock item.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CatalogueToolCell(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .editorSheetItemSurface(
                RoundedCornerShape(14.dp),
                baseColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) {
                if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                lineHeight = 14.sp,
                textAlign = TextAlign.Center
            ),
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurface
                    .copy(alpha = 0.38f)
                selected -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            // Every current tool name fits two centred lines; only a
            // longer future name clips to an ellipsis on the second.
            maxLines = 2,
            softWrap = true,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .padding(top = 4.dp)
        )
    }
}
