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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_tool_desc_blend
import com.dot.gallery.editor.ui.generated.resources.editor_tool_desc_borders
import com.dot.gallery.editor.ui.generated.resources.editor_tool_desc_colour
import com.dot.gallery.editor.ui.generated.resources.editor_tool_desc_colour_tone
import com.dot.gallery.editor.ui.generated.resources.editor_tool_desc_crop
import com.dot.gallery.editor.ui.generated.resources.editor_tool_desc_curves
import com.dot.gallery.editor.ui.generated.resources.editor_tool_desc_cutout
import com.dot.gallery.editor.ui.generated.resources.editor_tool_desc_details
import com.dot.gallery.editor.ui.generated.resources.editor_tool_desc_develop
import com.dot.gallery.editor.ui.generated.resources.editor_tool_desc_double_exposure
import com.dot.gallery.editor.ui.generated.resources.editor_tool_desc_edges
import com.dot.gallery.editor.ui.generated.resources.editor_tool_desc_grain
import com.dot.gallery.editor.ui.generated.resources.editor_tool_desc_light
import com.dot.gallery.editor.ui.generated.resources.editor_tool_desc_lens_blur
import com.dot.gallery.editor.ui.generated.resources.editor_tool_desc_looks
import com.dot.gallery.editor.ui.generated.resources.editor_tool_desc_markup
import com.dot.gallery.editor.ui.generated.resources.editor_tool_desc_mask
import com.dot.gallery.editor.ui.generated.resources.editor_tool_desc_perspective
import com.dot.gallery.editor.ui.generated.resources.editor_tool_desc_posterize
import com.dot.gallery.editor.ui.generated.resources.editor_tool_desc_remove_background
import com.dot.gallery.editor.ui.generated.resources.editor_tool_desc_transform
import com.dot.gallery.editor.ui.generated.resources.editor_tool_desc_vignette
import com.dot.gallery.editor.ui.generated.resources.editor_tool_desc_white_balance
import com.dot.gallery.editor.ui.generated.resources.editor_catalogue_needs_layer
import com.dot.gallery.editor.ui.generated.resources.editor_catalogue_section_adjust
import com.dot.gallery.editor.ui.generated.resources.editor_catalogue_section_crop
import com.dot.gallery.editor.ui.generated.resources.editor_catalogue_section_draw
import com.dot.gallery.editor.ui.generated.resources.editor_catalogue_section_effects
import com.dot.gallery.editor.ui.generated.resources.editor_catalogue_section_layers
import com.dot.gallery.editor.ui.generated.resources.editor_catalogue_section_more
import com.dot.gallery.editor.ui.generated.resources.editor_edit_pins
import com.dot.gallery.editor.ui.generated.resources.editor_group_all
import com.dot.gallery.editor.ui.generated.resources.editor_group_audio
import com.dot.gallery.editor.ui.generated.resources.editor_group_deliver
import com.dot.gallery.editor.ui.generated.resources.editor_group_edit
import com.dot.gallery.editor.ui.generated.resources.editor_group_picture
import com.dot.gallery.editor.ui.generated.resources.editor_pin_to_dock
import com.dot.gallery.editor.ui.generated.resources.editor_search_tools
import com.dot.gallery.editor.ui.generated.resources.editor_unpin_from_dock
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.PushPin
import com.dot.gallery.editor.ui.icons.ZoomIn
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal const val TAG_CATALOGUE_SEARCH = "editor.catalogue.search"
internal const val TAG_CATALOGUE_GRID = "editor.catalogue.grid"
internal const val TAG_CATALOGUE_HANDLE = "editor.catalogue.handle"
internal const val TAG_CATALOGUE_LAYERS = "editor.catalogue.layers"
internal const val TAG_CATALOGUE_CHIPS = "editor.catalogue.chips"

/** The catalogue's list sections, in display order. */
internal enum class CatalogueSection {
    Adjust, Crop, Effects, Draw, Layers,
    // The video editor's task groups.
    Edit, Picture, Audio, Deliver,
    More
}

/** The photo catalogue's filter chips, after All. */
internal val PHOTO_CATALOGUE_CHIPS = listOf(
    CatalogueSection.Adjust, CatalogueSection.Crop, CatalogueSection.Effects,
    CatalogueSection.Draw, CatalogueSection.Layers
)

/** The section a photo tool lists under; other ids fall back to their [EditorToolGroup]. */
internal fun photoCatalogueSection(id: EditorToolId): CatalogueSection? = when (id) {
    EditorToolIds.PhotoLight, EditorToolIds.PhotoColour, EditorToolIds.PhotoCurves,
    EditorToolIds.PhotoWhiteBalance, EditorToolIds.PhotoDetail, EditorToolIds.PhotoDevelop ->
        CatalogueSection.Adjust
    EditorToolIds.PhotoCrop, EditorToolIds.PhotoPerspective -> CatalogueSection.Crop
    EditorToolIds.PhotoLooks, EditorToolIds.PhotoVignette, EditorToolIds.PhotoLensBlur,
    EditorToolIds.PhotoGrain, EditorToolIds.PhotoColourTone, EditorToolIds.PhotoPosterize,
    EditorToolIds.PhotoEdges, EditorToolIds.PhotoBorders -> CatalogueSection.Effects
    EditorToolIds.PhotoMarkup, EditorToolIds.PhotoCutout, EditorToolIds.PhotoRemoveBackground ->
        CatalogueSection.Draw
    EditorToolIds.PhotoDoubleExposure, EditorToolIds.PhotoTransform, EditorToolIds.PhotoBlend,
    EditorToolIds.PhotoLayerMask -> CatalogueSection.Layers
    else -> null
}

private fun groupSection(group: EditorToolGroup): CatalogueSection = when (group) {
    EditorToolGroup.Adjust -> CatalogueSection.Adjust
    EditorToolGroup.Geometry -> CatalogueSection.Crop
    EditorToolGroup.Retouch -> CatalogueSection.Draw
    EditorToolGroup.Compose -> CatalogueSection.Layers
    EditorToolGroup.Edit -> CatalogueSection.Edit
    EditorToolGroup.Picture -> CatalogueSection.Picture
    EditorToolGroup.Audio -> CatalogueSection.Audio
    EditorToolGroup.Deliver -> CatalogueSection.Deliver
    else -> CatalogueSection.More
}

/** The short description a catalogue row shows, or null for tools without one. */
internal fun photoToolDescription(id: EditorToolId): StringResource? = when (id) {
    EditorToolIds.PhotoLight -> Res.string.editor_tool_desc_light
    EditorToolIds.PhotoColour -> Res.string.editor_tool_desc_colour
    EditorToolIds.PhotoCurves -> Res.string.editor_tool_desc_curves
    EditorToolIds.PhotoWhiteBalance -> Res.string.editor_tool_desc_white_balance
    EditorToolIds.PhotoDetail -> Res.string.editor_tool_desc_details
    EditorToolIds.PhotoDevelop -> Res.string.editor_tool_desc_develop
    EditorToolIds.PhotoCrop -> Res.string.editor_tool_desc_crop
    EditorToolIds.PhotoPerspective -> Res.string.editor_tool_desc_perspective
    EditorToolIds.PhotoLooks -> Res.string.editor_tool_desc_looks
    EditorToolIds.PhotoVignette -> Res.string.editor_tool_desc_vignette
    EditorToolIds.PhotoLensBlur -> Res.string.editor_tool_desc_lens_blur
    EditorToolIds.PhotoGrain -> Res.string.editor_tool_desc_grain
    EditorToolIds.PhotoColourTone -> Res.string.editor_tool_desc_colour_tone
    EditorToolIds.PhotoPosterize -> Res.string.editor_tool_desc_posterize
    EditorToolIds.PhotoEdges -> Res.string.editor_tool_desc_edges
    EditorToolIds.PhotoBorders -> Res.string.editor_tool_desc_borders
    EditorToolIds.PhotoMarkup -> Res.string.editor_tool_desc_markup
    EditorToolIds.PhotoCutout -> Res.string.editor_tool_desc_cutout
    EditorToolIds.PhotoRemoveBackground -> Res.string.editor_tool_desc_remove_background
    EditorToolIds.PhotoDoubleExposure -> Res.string.editor_tool_desc_double_exposure
    EditorToolIds.PhotoTransform -> Res.string.editor_tool_desc_transform
    EditorToolIds.PhotoBlend -> Res.string.editor_tool_desc_blend
    EditorToolIds.PhotoLayerMask -> Res.string.editor_tool_desc_mask
    else -> null
}

/** The illustration a catalogue row's tile draws for a photo tool. */
internal fun photoPreviewKind(id: EditorToolId): CataloguePreview? = when (id) {
    EditorToolIds.PhotoLight -> CataloguePreview.Light
    EditorToolIds.PhotoColour -> CataloguePreview.Colour
    EditorToolIds.PhotoCurves -> CataloguePreview.Curves
    EditorToolIds.PhotoWhiteBalance -> CataloguePreview.WhiteBalance
    EditorToolIds.PhotoDetail -> CataloguePreview.Details
    EditorToolIds.PhotoDevelop -> CataloguePreview.Develop
    EditorToolIds.PhotoCrop -> CataloguePreview.Crop
    EditorToolIds.PhotoPerspective -> CataloguePreview.Perspective
    EditorToolIds.PhotoLooks -> CataloguePreview.Looks
    EditorToolIds.PhotoVignette -> CataloguePreview.Vignette
    EditorToolIds.PhotoLensBlur -> CataloguePreview.LensBlur
    EditorToolIds.PhotoGrain -> CataloguePreview.Grain
    EditorToolIds.PhotoColourTone -> CataloguePreview.ColourTone
    EditorToolIds.PhotoPosterize -> CataloguePreview.Posterize
    EditorToolIds.PhotoEdges -> CataloguePreview.Edges
    EditorToolIds.PhotoBorders -> CataloguePreview.Borders
    EditorToolIds.PhotoMarkup -> CataloguePreview.Markup
    EditorToolIds.PhotoCutout -> CataloguePreview.Cutout
    EditorToolIds.PhotoRemoveBackground -> CataloguePreview.RemoveBackground
    EditorToolIds.PhotoDoubleExposure -> CataloguePreview.DoubleExposure
    EditorToolIds.PhotoTransform -> CataloguePreview.Transform
    EditorToolIds.PhotoBlend -> CataloguePreview.Blend
    EditorToolIds.PhotoLayerMask -> CataloguePreview.Mask
    else -> null
}

/**
 * A non-registry row the sheet can host — the Layers workspace, Open in,
 * History and Editor settings are surfaces rather than [EditorToolId]s.
 */
internal data class EditorCatalogueExtraCell(
    val icon: ImageVector,
    val label: String,
    val tag: String,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
    val section: CatalogueSection = CatalogueSection.More,
    val description: String? = null,
    val preview: CataloguePreview? = null
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

/** One row of the list, flattened with its section for the grouped render. */
private class CatalogueRowModel(
    val section: CatalogueSection,
    val id: EditorToolId?,
    val title: String,
    val description: String?,
    val icon: ImageVector,
    val preview: CataloguePreview?,
    val selected: Boolean,
    val enabled: Boolean,
    val pinnable: Boolean,
    val pinned: Boolean,
    val unavailableReason: String?,
    val tag: String,
    val onClick: () -> Unit
)

/**
 * The phone catalogue sheet as a vertical list grouped by task: a title row
 * with the pin manager, the search field, a filter-chip row and the sections
 * (Adjust, Crop & rotate, Effects, Draw & cut out, Layers, More). Search
 * matches title or description across every section. A long press on a
 * pinnable row toggles its dock pin.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun <S, A> EditorToolCatalogueSheet(
    entries: List<EditorToolEntry<S, A>>,
    state: S,
    activeTool: EditorToolId,
    enabled: Boolean,
    onSelect: (EditorToolId) -> Unit,
    modifier: Modifier = Modifier,
    // Null = All; the video catalogue opens on a task group.
    initialGroup: EditorToolGroup? = null,
    // The sheet title (Figma's "All tools") — absent on surfaces that
    // carry no title.
    title: String? = null,
    // V3 pinning — the destinations already on the compact pill, the
    // toggle for a row's long press and the title row's Edit pins affordance.
    pinnedIds: Set<EditorToolId> = emptySet(),
    onTogglePin: ((EditorToolId) -> Unit)? = null,
    onEditPins: (() -> Unit)? = null,
    // Modal hosts draw the real DragHandle themselves.
    showDragHandle: Boolean = true,
    // Kept for the video catalogue call sites; search always spans sections.
    searchAllGroups: Boolean = false,
    // Real non-tool surfaces (Layers, Open in, History, Settings) in the list.
    extraCells: List<EditorCatalogueExtraCell> = emptyList(),
    // Destinations that the pin gesture may offer — fixed slots (the dock's
    // Colour) are real tools but not pinnable.
    isPinnable: (EditorToolId) -> Boolean = { true },
    // Chrome that floats over the sheet's foot (the home dock), kept clear
    // of the list's last row.
    bottomChrome: Dp = 0.dp,
    // The filter chips after All, and each tool's one-line description.
    chips: List<CatalogueSection> = PHOTO_CATALOGUE_CHIPS,
    describe: (EditorToolId) -> StringResource? = ::photoToolDescription,
    // Told whether the list has scrolled away from its first row, so a
    // host sheet can grow while the user browses.
    onScrolledFromTop: ((Boolean) -> Unit)? = null
) {
    val listState = rememberLazyListState()
    val latestOnScrolled by rememberUpdatedState(onScrolledFromTop)
    LaunchedEffect(listState) {
        snapshotFlow { listState.canScrollBackward }
            .collect { scrolled -> latestOnScrolled?.invoke(scrolled) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable { mutableStateOf(initialGroup?.let(::groupSection)) }
    val haptics = LocalHapticFeedback.current
    val catalogued = entries.filter { it.catalogued }
    val titles = catalogued.associate {
        it.descriptor.id to stringResource(it.descriptor.title)
    }
    val descriptions = catalogued.associate {
        it.descriptor.id to describe(it.descriptor.id)?.let { res -> stringResource(res) }
    }
    val needsLayer = stringResource(Res.string.editor_catalogue_needs_layer)
    val pinToDock = stringResource(Res.string.editor_pin_to_dock)
    val unpinFromDock = stringResource(Res.string.editor_unpin_from_dock)
    val sectionLabels = mapOf(
        CatalogueSection.Adjust to stringResource(Res.string.editor_catalogue_section_adjust),
        CatalogueSection.Crop to stringResource(Res.string.editor_catalogue_section_crop),
        CatalogueSection.Effects to stringResource(Res.string.editor_catalogue_section_effects),
        CatalogueSection.Draw to stringResource(Res.string.editor_catalogue_section_draw),
        CatalogueSection.Layers to stringResource(Res.string.editor_catalogue_section_layers),
        CatalogueSection.Edit to stringResource(Res.string.editor_group_edit),
        CatalogueSection.Picture to stringResource(Res.string.editor_group_picture),
        CatalogueSection.Audio to stringResource(Res.string.editor_group_audio),
        CatalogueSection.Deliver to stringResource(Res.string.editor_group_deliver),
        CatalogueSection.More to stringResource(Res.string.editor_catalogue_section_more)
    )
    val allLabel = stringResource(Res.string.editor_group_all)
    val chipSections: List<Pair<String, CatalogueSection?>> =
        listOf(allLabel to null) + chips.map { sectionLabels.getValue(it) to it }
    val rows = buildList {
        catalogued.forEach { entry ->
            val id = entry.descriptor.id
            val sec = photoCatalogueSection(id) ?: groupSection(entry.descriptor.group)
            val available = entry.available(state)
            val pinnable = isPinnable(id)
            val layerTool = id == EditorToolIds.PhotoTransform || id == EditorToolIds.PhotoBlend
            add(
                CatalogueRowModel(
                    section = sec,
                    id = id,
                    title = titles[id].orEmpty(),
                    description = descriptions[id],
                    icon = entry.descriptor.icon,
                    preview = photoPreviewKind(id),
                    selected = activeTool == id,
                    enabled = enabled && available,
                    pinnable = pinnable && onTogglePin != null,
                    pinned = id in pinnedIds,
                    unavailableReason = if (!available && layerTool) needsLayer else null,
                    tag = entry.tag,
                    onClick = { onSelect(id) }
                )
            )
        }
        extraCells.forEach { cell ->
            add(
                CatalogueRowModel(
                    section = cell.section,
                    id = null,
                    title = cell.label,
                    description = cell.description,
                    icon = cell.icon,
                    preview = cell.preview,
                    selected = cell.selected,
                    enabled = enabled && cell.enabled,
                    pinnable = false,
                    pinned = false,
                    unavailableReason = null,
                    tag = cell.tag,
                    onClick = cell.onClick
                )
            )
        }
    }
    val searching = query.isNotBlank()
    val visible = rows.filter { row ->
        if (searching) {
            row.title.contains(query, ignoreCase = true) ||
                row.description?.contains(query, ignoreCase = true) == true
        } else {
            section == null || row.section == section
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)
    ) {
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
        EditorChipRow(
            modifier = Modifier.padding(top = 8.dp).testTag(TAG_CATALOGUE_CHIPS),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            chipSections.forEach { (label, target) ->
                EditorChip(
                    label = label,
                    selected = !searching && section == target,
                    onClick = {
                        query = ""
                        section = target
                    },
                    tag = "editor.catalogue.chip." + (target?.name ?: "all")
                )
            }
        }
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(top = 8.dp)
                .testTag(TAG_CATALOGUE_GRID),
            contentPadding = PaddingValues(bottom = navBottom + bottomChrome + 16.dp)
        ) {
            val grouped = visible.groupBy { it.section }
            CatalogueSection.entries.forEach { sec ->
                val sectionRows = grouped[sec].orEmpty()
                if (sectionRows.isEmpty()) return@forEach
                item(key = "header:${sec.name}") {
                    Text(
                        text = sectionLabels.getValue(sec),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp)
                    )
                }
                items(sectionRows, key = { "row:${it.tag}" }) { row ->
                    CatalogueRow(
                        row = row,
                        pinLabel = if (row.pinned) unpinFromDock else pinToDock,
                        onLongClick = if (row.pinnable && row.id != null) {
                            {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTogglePin?.invoke(row.id)
                            }
                        } else {
                            null
                        },
                        enabledLabel = needsLayer
                    )
                }
            }
        }
    }
}

/**
 * One 72 dp catalogue row: a 48 dp preview tile, the title and a one-line
 * description, a pin glyph when pinned. The active row takes a faded
 * secondary container; a disabled row paints at 0.38 and says why.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CatalogueRow(
    row: CatalogueRowModel,
    pinLabel: String,
    onLongClick: (() -> Unit)?,
    enabledLabel: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (row.selected) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                } else {
                    Color.Transparent
                }
            )
            .combinedClickable(
                enabled = row.enabled,
                role = Role.Button,
                onClickLabel = row.title,
                onLongClickLabel = if (onLongClick != null) pinLabel else null,
                onLongClick = onLongClick,
                onClick = row.onClick
            )
            .semantics { this.selected = row.selected }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(if (row.enabled) 1f else 0.38f)
            .testTag(row.tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PhotoToolPreviewTile(
            kind = row.preview,
            icon = row.icon,
            enabled = row.enabled
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val description = row.unavailableReason ?: row.description
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (row.pinned) {
            Icon(
                imageVector = EditorSymbols.PushPin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
