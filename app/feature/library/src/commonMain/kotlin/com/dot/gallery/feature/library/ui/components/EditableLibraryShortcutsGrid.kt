/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.dot.gallery.core.util.rememberFeedbackManager
import com.dot.gallery.core.ui.components.dashedBorder
import com.dot.gallery.core.ui.util.excludeSystemGestures
import com.dot.gallery.feature.library.ui.COLLAPSED_SHORTCUT_ROWS
import com.dot.gallery.feature.library.ui.LibraryShortcut
import com.dot.gallery.feature.library.ui.LibraryShortcutPref
import com.dot.gallery.feature.library.ui.LibraryShortcutSize
import com.dot.gallery.feature.library.ui.RuntimeShortcut
import com.dot.gallery.feature.library.ui.ShortcutCell
import com.dot.gallery.feature.library.ui.foldShortcutGrid
import com.dot.gallery.feature.library.ui.nextShortcutHeight
import com.dot.gallery.feature.library.ui.nextShortcutWidth
import com.dot.gallery.feature.library.ui.packShortcutGrid
import com.dot.gallery.feature.library.ui.resizeShortcutHeight
import com.dot.gallery.feature.library.ui.resizeShortcutWidth
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.library.generated.resources.Res
import com.dot.gallery.feature.library.generated.resources.library_section_hidden
import com.dot.gallery.feature.library.generated.resources.library_shortcut_cover
import com.dot.gallery.feature.library.generated.resources.library_shortcut_cover_latest
import com.dot.gallery.feature.library.generated.resources.library_shortcut_cover_oldest
import com.dot.gallery.feature.library.generated.resources.library_shortcut_cover_shuffle
import com.dot.gallery.feature.library.generated.resources.library_shortcut_layout
import com.dot.gallery.feature.library.generated.resources.library_shortcut_layout_grid
import com.dot.gallery.feature.library.generated.resources.library_shortcut_layout_scrapbook
import com.dot.gallery.feature.library.generated.resources.library_shortcut_hide
import com.dot.gallery.feature.library.generated.resources.library_shortcut_resize_height
import com.dot.gallery.feature.library.generated.resources.library_shortcut_resize_width
import com.dot.gallery.feature.library.generated.resources.library_shortcut_select
import com.dot.gallery.feature.library.generated.resources.library_shortcut_show
import com.dot.gallery.feature.library.generated.resources.library_shortcuts_less
import com.dot.gallery.feature.library.generated.resources.library_shortcuts_more


private val ROW_HEIGHT = 56.dp
private val TILE_SPACING = 8.dp
/** Below this a half-row tile (two units) no longer fits an icon and a label, so a narrow pane uses fewer units. */
private val MIN_UNIT_WIDTH = 71.dp
private val SELECTION_INSET = 4.dp
private val HANDLE_TOUCH = 48.dp

private data class Slot(val x: Float, val y: Float, val width: Float, val height: Float)

/** Mutable, non-reactive holder for the latest computed geometry so drag
 *  callbacks always read fresh values without triggering recomposition. */
private class GeoHolder {
    var slots: List<Slot> = emptyList()
    var visibleIds: List<String> = emptyList()
    var unitPx: Float = 0f
    var rowPx: Float = 0f
    var spacingPx: Float = 0f
    var columns: Int = LibraryShortcutSize.COLUMNS
}

private fun slotFor(cell: ShortcutCell, unitPx: Float, rowPx: Float, spacingPx: Float) = Slot(
    x = cell.column * (unitPx + spacingPx),
    y = cell.row * (rowPx + spacingPx),
    width = cell.width * unitPx + (cell.width - 1) * spacingPx,
    height = cell.height * rowPx + (cell.height - 1) * spacingPx,
)

/** The widest power-of-two unit count up to [columns] whose units stay at least [MIN_UNIT_WIDTH]. */
private fun fittingColumns(maxWidth: Dp, columns: Int): Int {
    val fit = ((maxWidth + TILE_SPACING) / (MIN_UNIT_WIDTH + TILE_SPACING)).toInt().coerceIn(1, columns)
    var units = 1
    while (units * 2 <= fit) units *= 2
    return units
}

/**
 * Quick-Settings-style, in-place editable shortcut grid.
 *
 * Tiles take a [LibraryShortcutSize] on a grid [columns] units wide (four on a phone): a 1×1
 * icon, half or full rows, and taller cards. Normal mode: tap navigates; editing opens from the
 * Library overflow menu. With [collapsible], long lists fold behind a "Show more" tile so the
 * shortcuts do not push the albums off screen.
 * Edit mode: drag to reorder live, tap a tile to select it and drag its side or bottom handle to
 * resize, tap the × to remove, and re-add hidden tiles from the tray below.
 *
 * [working] is the controlled list of every AVAILABLE shortcut (visible +
 * hidden), in order. Mutations are reported via [onChange]; the caller persists. A drag
 * (reorder or resize) reports once, when it ends, so it does not write the preferences on
 * every step.
 */
@Composable
fun EditableLibraryShortcutsGrid(
    working: List<LibraryShortcutPref>,
    runtime: Map<LibraryShortcut, RuntimeShortcut>,
    editMode: Boolean,
    onClick: (String) -> Unit,
    onChange: (List<LibraryShortcutPref>) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = LibraryShortcutSize.COLUMNS,
    collapsible: Boolean = false,
    collapsedRows: Int = COLLAPSED_SHORTCUT_ROWS,
    mapContent: ShortcutMapContent? = null,
) {
    val feedbackManager = rememberFeedbackManager()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val tiles = remember {
        mutableStateListOf<LibraryShortcutPref>().also { it.addAll(working) }
    }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var resizingId by remember { mutableStateOf<String?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(working, draggingId) {
        if (draggingId == null && resizingId == null && working != tiles.toList()) {
            tiles.clear()
            tiles.addAll(working)
        }
    }
    LaunchedEffect(editMode) { if (!editMode) selectedId = null }

    fun emit() = onChange(tiles.toList())

    fun reorder(fromId: String, toVisible: Int) {
        val visible = tiles.filter { it.visible }
        val fromV = visible.indexOfFirst { it.id == fromId }
        if (fromV < 0 || toVisible !in visible.indices || fromV == toVisible) return
        val moved = visible.toMutableList()
        moved.add(toVisible, moved.removeAt(fromV))
        val hidden = tiles.filter { !it.visible }
        tiles.clear(); tiles.addAll(moved + hidden)
    }

    fun sizeOf(id: String): LibraryShortcutSize? = tiles.firstOrNull { it.id == id }?.size

    fun setSize(id: String, size: LibraryShortcutSize) {
        val i = tiles.indexOfFirst { it.id == id }
        if (i >= 0 && tiles[i].size != size) {
            tiles[i] = tiles[i].withSize(size)
            if (resizingId == null) emit()
            feedbackManager.vibrate()
        }
    }

    fun update(id: String, change: (LibraryShortcutPref) -> LibraryShortcutPref) {
        val i = tiles.indexOfFirst { it.id == id }
        if (i < 0) return
        val updated = change(tiles[i])
        if (updated != tiles[i]) {
            tiles[i] = updated; emit()
        }
    }

    fun setVisible(id: String, visible: Boolean) {
        val i = tiles.indexOfFirst { it.id == id }
        if (i < 0 || tiles[i].visible == visible) return
        val pref = tiles[i].copy(visible = visible)
        tiles.removeAt(i)
        if (visible) {
            val at = tiles.indexOfFirst { !it.visible }.let { if (it < 0) tiles.size else it }
            tiles.add(at, pref)
        } else {
            tiles.add(pref)
            if (selectedId == id) selectedId = null
        }
        emit()
    }

    val offsets = remember { mutableStateMapOf<String, Animatable<Offset, AnimationVector2D>>() }
    // Tracks tiles that have been positioned at least once, so the very first
    // layout SNAPS into place (no slide-in animation when entering the screen);
    // only later target changes (reorder/resize) animate.
    val placed = remember { mutableStateMapOf<String, Boolean>() }
    val geo = remember { GeoHolder() }
    // Absolute offset of the actively-dragged tile, updated synchronously on the
    // main thread inside onDrag (no coroutine) to avoid snapTo/animateTo races.
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    fun reorderByHover(centerX: Float, centerY: Float, id: String) {
        val to = geo.slots.indexOfFirst { sl ->
            centerX in sl.x..(sl.x + sl.width) && centerY in sl.y..(sl.y + sl.height)
        }
        if (to < 0) return
        val targetId = geo.visibleIds.getOrNull(to) ?: return
        if (targetId == id) return
        val fromV = tiles.filter { it.visible }.indexOfFirst { it.id == id }
        if (fromV < 0 || to == fromV) return
        reorder(id, to)
        feedbackManager.vibrate()
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TILE_SPACING)) {
        val allVisibleTiles = tiles.filter { it.visible }
        val hiddenTiles = tiles.filter { !it.visible }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val units = fittingColumns(maxWidth, columns)
            val spacingPx = with(density) { TILE_SPACING.toPx() }
            val rowPx = with(density) { ROW_HEIGHT.toPx() }
            val maxWidthPx = with(density) { maxWidth.toPx() }
            val unitPx = (maxWidthPx - spacingPx * (units - 1)) / units

            val sizes = allVisibleTiles.map { it.size }
            // The toggle keeps the slot right after the collapsed tiles, so it does not move when
            // the list expands; the extra tiles flow in after it.
            val fold = if (!editMode && collapsible) foldShortcutGrid(sizes, units, collapsedRows) else null
            val toggleAt = fold?.count
            val packed = packShortcutGrid(
                if (fold != null) {
                    sizes.toMutableList().apply { add(fold.count, fold.toggle) }
                } else sizes,
                units
            )
            val shownCells = if (toggleAt != null && !expanded) packed.take(toggleAt + 1) else packed
            val totalHeightPx = shownCells.maxOfOrNull { it.bottom }?.let { rows ->
                rows * rowPx + (rows - 1) * spacingPx
            } ?: 0f
            val toggleSlot = toggleAt?.let { slotFor(packed[it], unitPx, rowPx, spacingPx) }
            val visibleTiles = if (toggleAt != null && !expanded) allVisibleTiles.take(toggleAt) else allVisibleTiles
            val tileSlots = packed.filterIndexed { index, _ -> index != toggleAt }
                .take(visibleTiles.size)
                .map { slotFor(it, unitPx, rowPx, spacingPx) }
            // Keep the geometry holder fresh for drag hit-testing.
            geo.slots = tileSlots
            geo.visibleIds = visibleTiles.map { it.id }
            geo.unitPx = unitPx
            geo.rowPx = rowPx
            geo.spacingPx = spacingPx
            geo.columns = units

            Box(modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { totalHeightPx.toDp() })
            ) {
                // Tiles move by graphics layer, which does not report a new position, so the
                // selected tile's resize handles keep the back gesture off through this
                // layout-placed zone instead: the tile plus its handles, which reach past the
                // grid towards the screen edge.
                val selectedSlot = selectedId?.takeIf { editMode }
                    ?.let { id -> tileSlots.getOrNull(visibleTiles.indexOfFirst { it.id == id }) }
                if (selectedSlot != null) {
                    val reach = with(density) { (HANDLE_TOUCH + SELECTION_INSET).toPx() }
                    Box(
                        modifier = Modifier
                            // Tile translations are not mirrored, so neither is the zone.
                            .absoluteOffset {
                                IntOffset((selectedSlot.x - reach).roundToInt(), selectedSlot.y.roundToInt())
                            }
                            // Unbounded so a full-width tile's zone is not clamped and re-centred.
                            .wrapContentSize(Alignment.TopStart, unbounded = true)
                            .size(
                                width = with(density) { (selectedSlot.width + reach * 2).toDp() },
                                height = with(density) { (selectedSlot.height + reach).toDp() },
                            )
                            .excludeSystemGestures()
                    )
                }
                if (toggleSlot != null) {
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                translationX = toggleSlot.x
                                translationY = toggleSlot.y
                            }
                            .width(with(density) { toggleSlot.width.toDp() })
                            .height(with(density) { toggleSlot.height.toDp() })
                            .testTag("library-shortcuts-more")
                            .clip(TILE_SHAPE)
                            .clickable { expanded = !expanded }
                    ) {
                        val toggleTitle = if (expanded) {
                            stringResource(Res.string.library_shortcuts_less)
                        } else {
                            stringResource(Res.string.library_shortcuts_more)
                        }
                        val toggleIcon = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore
                        if (fold?.toggle == LibraryShortcutSize.ICON) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(imageVector = toggleIcon, contentDescription = toggleTitle)
                            }
                        } else {
                            LibrarySmallItem(
                                title = toggleTitle,
                                icon = toggleIcon,
                                shape = TILE_SHAPE,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
                visibleTiles.forEachIndexed { index, pref ->
                    val data = LibraryShortcut.fromId(pref.id)?.let { runtime[it] } ?: return@forEachIndexed
                    val slot = tileSlots.getOrNull(index) ?: return@forEachIndexed
                    // Identity-keyed so each tile's gesture/state moves WITH it
                    // across reorders instead of being torn down (which froze the
                    // active drag the moment a tile collided with another).
                    key(pref.id) {
                    val target = Offset(slot.x, slot.y)
                    val anim = remember(pref.id) {
                        offsets.getOrPut(pref.id) { Animatable(target, Offset.VectorConverter) }
                    }
                    val isDragging = draggingId == pref.id
                    val isSelected = editMode && selectedId == pref.id

                    // Non-dragged tiles reflow to their slot. The dragged tile is
                    // driven directly by [dragOffset] and is skipped here.
                    //
                    // Reflow only ANIMATES for user-driven edits (drag-reorder /
                    // resize in edit mode). In normal mode every target change
                    // comes from async data settling in after the screen opens
                    // (saved layout order loading from DataStore, cached cloud
                    // shortcuts appearing) — those must SNAP so tiles don't do a
                    // shaky spring into place on open. The first placement always
                    // snaps too.
                    LaunchedEffect(target, isDragging, editMode) {
                        if (isDragging) return@LaunchedEffect
                        if (placed[pref.id] != true || !editMode) {
                            anim.snapTo(target)
                            placed[pref.id] = true
                        } else {
                            anim.animateTo(
                                target, spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                            )
                        }
                    }
                    val sizeSpec = if (editMode) spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy) else snap()
                    val width by animateFloatAsState(slot.width, sizeSpec)
                    val height by animateFloatAsState(slot.height, sizeSpec)
                    val widthDp = with(density) { width.toDp() }
                    val heightDp = with(density) { height.toDp() }
                    val renderOffset = if (isDragging) dragOffset else anim.value
                    val size = pref.size
                    val selectLabel = stringResource(Res.string.library_shortcut_select)
                    Box(
                        modifier = Modifier
                            .zIndex(if (isDragging || isSelected) 1f else 0f)
                            .graphicsLayer {
                                translationX = renderOffset.x
                                translationY = renderOffset.y
                            }
                            .width(widthDp)
                            .height(heightDp)
                            .testTag("library-shortcut-${pref.id}")
                            .then(
                                if (editMode) Modifier
                                    .pointerInput(pref.id) {
                                        detectDragGestures(
                                            onDragStart = {
                                                val v = geo.visibleIds.indexOf(pref.id)
                                                val sl = geo.slots.getOrNull(v)
                                                dragOffset = if (sl != null) Offset(sl.x, sl.y) else anim.value
                                                draggingId = pref.id
                                                feedbackManager.vibrateStrong()
                                            },
                                            onDragEnd = {
                                                val id = draggingId
                                                if (id != null) scope.launch {
                                                    val v = geo.visibleIds.indexOf(id)
                                                    val sl = geo.slots.getOrNull(v)
                                                    if (sl != null) {
                                                        val dest = Offset(sl.x, sl.y)
                                                        animate(
                                                            typeConverter = Offset.VectorConverter,
                                                            initialValue = dragOffset,
                                                            targetValue = dest,
                                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                                        ) { value, _ -> dragOffset = value }
                                                        offsets[id]?.snapTo(dest)
                                                    }
                                                    draggingId = null
                                                    emit()
                                                } else {
                                                    draggingId = null
                                                    emit()
                                                }
                                            },
                                            onDragCancel = { draggingId = null; emit() },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragOffset += amount
                                                val v = geo.visibleIds.indexOf(pref.id)
                                                val sl = geo.slots.getOrNull(v) ?: slot
                                                val cx = dragOffset.x + sl.width / 2f
                                                val cy = dragOffset.y + sl.height / 2f
                                                reorderByHover(cx, cy, pref.id)
                                            }
                                        )
                                    }
                                    .clickable(onClickLabel = selectLabel) {
                                        selectedId = if (isSelected) null else pref.id
                                    }
                                else Modifier.clickable { onClick(data.route) }
                            )
                    ) {
                        ShortcutTile(
                            data = data,
                            pref = pref,
                            showIndicator = !editMode,
                            modifier = Modifier.fillMaxSize(),
                            mapContent = mapContent,
                        )
                        if (editMode) {
                            // Hiding is reversible from the tray below, so the badge stays
                            // neutral and sits on the corner, leaving the tile's icon visible.
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset(x = (-14).dp, y = (-14).dp)
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable { setVisible(pref.id, false) },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurface),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(Res.string.library_shortcut_hide),
                                        tint = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                        if (isSelected) {
                            SelectionOutline(
                                id = pref.id,
                                size = size,
                                width = widthDp,
                                height = heightDp,
                                resizing = resizingId == pref.id,
                                onWidthDrag = { startUnits, dx ->
                                    val delta = (if (rtl) -dx else dx) / (geo.unitPx + geo.spacingPx)
                                    sizeOf(pref.id)?.let { current ->
                                        setSize(pref.id, resizeShortcutWidth(current, startUnits + delta, geo.columns))
                                    }
                                },
                                onHeightDrag = { startRows, dy ->
                                    val delta = dy / (geo.rowPx + geo.spacingPx)
                                    sizeOf(pref.id)?.let { current ->
                                        setSize(pref.id, resizeShortcutHeight(current, startRows + delta))
                                    }
                                },
                                onResizingChange = { resizing ->
                                    resizingId = if (resizing) pref.id else null
                                    if (!resizing) emit()
                                },
                                onWidthTap = { setSize(pref.id, nextShortcutWidth(size, geo.columns)) },
                                onHeightTap = { setSize(pref.id, nextShortcutHeight(size)) },
                                displayedWidth = minOf(size.width, units),
                            )
                        }
                    }
                    }
                }
            }
        }

        val selected = selectedId?.let { id -> tiles.firstOrNull { it.id == id && it.visible } }
        val selectedData = selected?.let { LibraryShortcut.fromId(it.id) }?.let { runtime[it] }
        AnimatedVisibility(
            visible = editMode && selected != null && selectedData?.hasCoverOrder == true &&
                selected.size != LibraryShortcutSize.ICON && selected.size != LibraryShortcutSize.HALF
        ) {
            if (selected != null) {
                CoverPicker(
                    pref = selected,
                    canShuffle = selectedData?.hasShuffle == true,
                    onChange = { change -> update(selected.id, change) },
                )
            }
        }

        // Hidden tiles tray (edit mode only)
        AnimatedVisibility(visible = editMode && hiddenTiles.isNotEmpty()) {
            val perRow = (columns / 2).coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(Res.string.library_section_hidden),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                hiddenTiles.chunked(perRow).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(TILE_SPACING)
                    ) {
                        row.forEach { pref ->
                            val data = LibraryShortcut.fromId(pref.id)?.let { runtime[it] }
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(ROW_HEIGHT)
                                    .dashedBorder(
                                        color = MaterialTheme.colorScheme.outline,
                                        shape = TILE_SHAPE,
                                        gapLength = 8.dp
                                    )
                                    .clip(TILE_SHAPE)
                                    .clickable { setVisible(pref.id, true) }
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = stringResource(Res.string.library_shortcut_show),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = data?.title.orEmpty(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                        repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/**
 * The outline of the selected tile with its resize handles: the side handle changes the width
 * and, where the width allows more than one height, the bottom handle changes the height. Drags
 * report the starting size in units plus the distance moved; a tap steps to the next size.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.SelectionOutline(
    id: String,
    size: LibraryShortcutSize,
    width: Dp,
    height: Dp,
    resizing: Boolean,
    onWidthDrag: (startUnits: Float, dx: Float) -> Unit,
    onHeightDrag: (startRows: Float, dy: Float) -> Unit,
    onResizingChange: (Boolean) -> Unit,
    onWidthTap: () -> Unit,
    onHeightTap: () -> Unit,
    displayedWidth: Int,
) {
    val primary = MaterialTheme.colorScheme.primary
    val radius = if (size.isCard) 24.dp else 28.dp
    val currentWidth by rememberUpdatedState(displayedWidth)
    val currentHeight by rememberUpdatedState(size.height)
    val widthDrag by rememberUpdatedState(onWidthDrag)
    val heightDrag by rememberUpdatedState(onHeightDrag)
    val resizingChange by rememberUpdatedState(onResizingChange)
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .requiredSize(width + SELECTION_INSET * 2, height + SELECTION_INSET * 2)
            .border(2.dp, primary, RoundedCornerShape(radius + SELECTION_INSET))
    )
    val widthLabel = stringResource(Res.string.library_shortcut_resize_width, size.label)
    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .offset(x = HANDLE_TOUCH / 2 + SELECTION_INSET)
            .size(HANDLE_TOUCH)
            .testTag("library-shortcut-resize-width-$id")
            .pointerInput(id) {
                var dx = 0f
                var start = 0f
                detectDragGestures(
                    onDragStart = { dx = 0f; start = currentWidth.toFloat(); resizingChange(true) },
                    onDragEnd = { resizingChange(false) },
                    onDragCancel = { resizingChange(false) },
                    onDrag = { change, amount ->
                        change.consume()
                        dx += amount.x
                        widthDrag(start, dx)
                    }
                )
            }
            .clickable(onClickLabel = widthLabel, onClick = onWidthTap),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 6.dp, height = 28.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(primary)
        )
    }
    if (LibraryShortcutSize.heightsFor(size.width).size > 1) {
        val heightLabel = stringResource(Res.string.library_shortcut_resize_height, size.label)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = HANDLE_TOUCH / 2 + SELECTION_INSET)
                .size(HANDLE_TOUCH)
                .testTag("library-shortcut-resize-height-$id")
                .pointerInput(id) {
                    var dy = 0f
                    var start = 0f
                    detectDragGestures(
                        onDragStart = { dy = 0f; start = currentHeight.toFloat(); resizingChange(true) },
                        onDragEnd = { resizingChange(false) },
                        onDragCancel = { resizingChange(false) },
                        onDrag = { change, amount ->
                            change.consume()
                            dy += amount.y
                            heightDrag(start, dy)
                        }
                    )
                }
                .clickable(onClickLabel = heightLabel, onClick = onHeightTap),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = 28.dp, height = 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(primary)
            )
        }
    }
    if (resizing) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = -(SELECTION_INSET + 26.dp))
                .clip(RoundedCornerShape(11.dp))
                .background(primary)
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Text(
                text = size.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/** Which favourites the tile shows and, on a card, whether they sit in a grid or a scrapbook. */
@Composable
private fun CoverPicker(
    pref: LibraryShortcutPref,
    canShuffle: Boolean,
    onChange: ((LibraryShortcutPref) -> LibraryShortcutPref) -> Unit,
) {
    val orders = listOfNotNull(
        Res.string.library_shortcut_cover_latest to { p: LibraryShortcutPref -> p.copy(oldestFirst = false, shuffle = false) },
        Res.string.library_shortcut_cover_oldest to { p: LibraryShortcutPref -> p.copy(oldestFirst = true, shuffle = false) },
        if (canShuffle) {
            Res.string.library_shortcut_cover_shuffle to { p: LibraryShortcutPref -> p.copy(shuffle = true) }
        } else null,
    )
    val selectedOrder = when {
        pref.shuffle && canShuffle -> 2
        pref.oldestFirst -> 1
        else -> 0
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(Res.string.library_shortcut_cover),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.testTag("library-shortcut-cover-order")) {
            orders.forEachIndexed { index, (label, change) ->
                SegmentedButton(
                    selected = index == selectedOrder,
                    onClick = { onChange(change) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = orders.size)
                ) {
                    Text(stringResource(label))
                }
            }
        }
        if (pref.size.isCard) {
            Text(
                text = stringResource(Res.string.library_shortcut_layout),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.testTag("library-shortcut-cover-layout")) {
                listOf(
                    Res.string.library_shortcut_layout_grid to false,
                    Res.string.library_shortcut_layout_scrapbook to true,
                ).forEachIndexed { index, (label, scrapbook) ->
                    SegmentedButton(
                        selected = pref.scrapbook == scrapbook,
                        onClick = { onChange { it.copy(scrapbook = scrapbook) } },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                    ) {
                        Text(stringResource(label))
                    }
                }
            }
        }
    }
}
