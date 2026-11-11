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
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.dot.gallery.core.util.rememberFeedbackManager
import com.dot.gallery.core.ui.components.dashedBorder
import com.dot.gallery.feature.library.ui.LibraryShortcut
import com.dot.gallery.feature.library.ui.LibraryShortcutPref
import com.dot.gallery.feature.library.ui.LibraryShortcutSpan
import com.dot.gallery.feature.library.ui.RuntimeShortcut
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.library.generated.resources.Res
import com.dot.gallery.feature.library.generated.resources.library_section_hidden
import com.dot.gallery.feature.library.generated.resources.library_shortcut_hide
import com.dot.gallery.feature.library.generated.resources.library_shortcut_show
import com.dot.gallery.feature.library.generated.resources.library_shortcut_size_full
import com.dot.gallery.feature.library.generated.resources.library_shortcut_size_half
import com.dot.gallery.feature.library.generated.resources.library_shortcuts_less
import com.dot.gallery.feature.library.generated.resources.library_shortcuts_more


private val TILE_HEIGHT = 56.dp
private val TILE_SPACING = 8.dp
private const val COLLAPSED_VISIBLE_TILES = 3
/** Below this, an icon and a label no longer fit, so a narrow pane stacks its tiles instead. */
private val MIN_TILE_WIDTH = 150.dp
private val TILE_SHAPE = RoundedCornerShape(28.dp)

private data class Slot(val x: Float, val y: Float, val width: Float)

/** Mutable, non-reactive holder for the latest computed geometry so drag
 *  callbacks always read fresh values without triggering recomposition. */
private class GeoHolder {
    var slots: List<Slot> = emptyList()
    var visibleIds: List<String> = emptyList()
    var tileHeightPx: Float = 0f
    var unitPx: Float = 0f
}

private fun computeSlots(
    spans: List<Int>,
    unitPx: Float,
    spacingPx: Float,
    tileHeightPx: Float,
    columns: Int,
): Pair<List<Slot>, Float> {
    if (spans.isEmpty()) return emptyList<Slot>() to 0f
    val slots = ArrayList<Slot>(spans.size)
    var x = 0f
    var y = 0f
    var used = 0
    for (s in spans) {
        val span = s.coerceIn(1, minOf(LibraryShortcutSpan.FULL, columns))
        if (used > 0 && used + span > columns) {
            x = 0f; y += tileHeightPx + spacingPx; used = 0
        }
        val w = if (span >= 2) unitPx * 2 + spacingPx else unitPx
        slots += Slot(x, y, w)
        x += w + spacingPx
        used += span
    }
    return slots to (y + tileHeightPx)
}

/**
 * Quick-Settings-style, in-place editable shortcut grid.
 *
 * Normal mode: tiles fill the row and tap navigates; editing opens from the Library overflow
 * menu. With [collapsible], long lists fold behind a "Show more" tile so the shortcuts do not
 * push the albums off screen.
 * Edit mode: drag to reorder live, drag the side handle to resize half/full,
 * tap the × to remove, and re-add hidden tiles from the tray below.
 *
 * [working] is the controlled list of every AVAILABLE shortcut (visible +
 * hidden), in order. Mutations are reported via [onChange]; the caller persists.
 */
@Composable
fun EditableLibraryShortcutsGrid(
    working: List<LibraryShortcutPref>,
    runtime: Map<LibraryShortcut, RuntimeShortcut>,
    editMode: Boolean,
    onClick: (String) -> Unit,
    onChange: (List<LibraryShortcutPref>) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = LibraryShortcutSpan.COLUMNS,
    collapsible: Boolean = false,
) {
    val feedbackManager = rememberFeedbackManager()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val tileHeight = TILE_HEIGHT

    val tiles = remember {
        mutableStateListOf<LibraryShortcutPref>().also { it.addAll(working) }
    }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(working, draggingId) {
        if (draggingId == null && working != tiles.toList()) {
            tiles.clear()
            tiles.addAll(working)
        }
    }

    fun emit() = onChange(tiles.toList())

    fun reorder(fromId: String, toVisible: Int) {
        val visible = tiles.filter { it.visible }
        val fromV = visible.indexOfFirst { it.id == fromId }
        if (fromV < 0 || toVisible !in visible.indices || fromV == toVisible) return
        val moved = visible.toMutableList()
        moved.add(toVisible, moved.removeAt(fromV))
        val hidden = tiles.filter { !it.visible }
        tiles.clear(); tiles.addAll(moved + hidden); emit()
    }

    fun setSpan(id: String, span: Int) {
        val i = tiles.indexOfFirst { it.id == id }
        if (i >= 0 && tiles[i].span != span) { tiles[i] = tiles[i].copy(span = span); emit() }
    }

    fun setVisible(id: String, visible: Boolean) {
        val i = tiles.indexOfFirst { it.id == id }
        if (i < 0 || tiles[i].visible == visible) return
        val pref = tiles[i].copy(visible = visible)
        tiles.removeAt(i)
        if (visible) {
            val at = tiles.indexOfFirst { !it.visible }.let { if (it < 0) tiles.size else it }
            tiles.add(at, pref)
        } else tiles.add(pref)
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
            centerX in sl.x..(sl.x + sl.width) && centerY in sl.y..(sl.y + geo.tileHeightPx)
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
        val canCollapse = !editMode && collapsible && allVisibleTiles.size > COLLAPSED_VISIBLE_TILES + 1
        val visibleTiles = if (canCollapse && !expanded) {
            allVisibleTiles.take(COLLAPSED_VISIBLE_TILES)
        } else allVisibleTiles

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val spacingPx = with(density) { TILE_SPACING.toPx() }
            val tileHeightPx = with(density) { tileHeight.toPx() }
            val maxWidthPx = with(density) { maxWidth.toPx() }
            val fittingColumns = ((maxWidth + TILE_SPACING) / (MIN_TILE_WIDTH + TILE_SPACING)).toInt()
                .coerceIn(1, columns)
            val unitPx = (maxWidthPx - spacingPx * (fittingColumns - 1)) / fittingColumns

            val spans = visibleTiles.map { it.span }.toMutableList()
            if (canCollapse) spans.add(COLLAPSED_VISIBLE_TILES, LibraryShortcutSpan.HALF)
            val (slots, totalHeightPx) = computeSlots(
                spans, unitPx, spacingPx, tileHeightPx, fittingColumns
            )
            // The toggle keeps the slot right after the collapsed tiles, so it does not move when
            // the list expands; the extra tiles flow in after it.
            val tileSlots = if (canCollapse) {
                slots.filterIndexed { index, _ -> index != COLLAPSED_VISIBLE_TILES }
            } else slots
            // Keep the geometry holder fresh for drag hit-testing.
            geo.slots = tileSlots
            geo.visibleIds = visibleTiles.map { it.id }
            geo.tileHeightPx = tileHeightPx
            geo.unitPx = unitPx

            Box(modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { totalHeightPx.toDp() })
            ) {
                if (canCollapse) {
                    val slot = slots[COLLAPSED_VISIBLE_TILES]
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                translationX = slot.x
                                translationY = slot.y
                            }
                            .width(with(density) { slot.width.toDp() })
                            .height(tileHeight)
                            .testTag("library-shortcuts-more")
                            .clip(TILE_SHAPE)
                            .clickable { expanded = !expanded }
                    ) {
                        LibrarySmallItem(
                            title = if (expanded) {
                                stringResource(Res.string.library_shortcuts_less)
                            } else {
                                stringResource(Res.string.library_shortcuts_more)
                            },
                            icon = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            shape = TILE_SHAPE,
                            modifier = Modifier.fillMaxWidth()
                        )
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

                    val widthDp = with(density) { slot.width.toDp() }
                    val renderOffset = if (isDragging) dragOffset else anim.value
                    Box(
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                translationX = renderOffset.x
                                translationY = renderOffset.y
                            }
                            .width(widthDp)
                            .height(tileHeight)
                            .testTag("library-shortcut-${pref.id}")
                            .then(
                                if (editMode) Modifier.pointerInput(pref.id) {
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
                                            val w = geo.slots.getOrNull(v)?.width ?: slot.width
                                            val cx = dragOffset.x + w / 2f
                                            val cy = dragOffset.y + geo.tileHeightPx / 2f
                                            reorderByHover(cx, cy, pref.id)
                                        }
                                    )
                                } else Modifier.clickable { onClick(data.route) }
                            )
                    ) {
                        ShortcutTile(
                            data = data,
                            showIndicator = !editMode,
                            modifier = Modifier.fillMaxWidth().height(tileHeight)
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
                            // Resize handle (center-end): a clear vertical grabber.
                            // Drag horizontally to resize, or tap to toggle.
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .size(width = 48.dp, height = tileHeight)
                                    .pointerInput(pref.id, pref.span) {
                                        var dx = 0f
                                        val threshold = geo.unitPx * 0.35f
                                        detectDragGestures(
                                            onDragStart = { dx = 0f },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dx += amount.x
                                                if (dx > threshold && pref.span < LibraryShortcutSpan.FULL) {
                                                    setSpan(pref.id, LibraryShortcutSpan.FULL); dx = 0f
                                                    feedbackManager.vibrate()
                                                } else if (dx < -threshold && pref.span > LibraryShortcutSpan.HALF) {
                                                    setSpan(pref.id, LibraryShortcutSpan.HALF); dx = 0f
                                                    feedbackManager.vibrate()
                                                }
                                            }
                                        )
                                    }
                                    .clickable {
                                        setSpan(
                                            pref.id,
                                            if (pref.span == LibraryShortcutSpan.FULL) LibraryShortcutSpan.HALF
                                            else LibraryShortcutSpan.FULL
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 20.dp, height = 38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(data.contentColor.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.UnfoldMore,
                                        contentDescription = stringResource(
                                            if (pref.span == LibraryShortcutSpan.FULL) {
                                                Res.string.library_shortcut_size_half
                                            } else {
                                                Res.string.library_shortcut_size_full
                                            }
                                        ),
                                        tint = data.contentColor,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .graphicsLayer { rotationZ = 90f }
                                    )
                                }
                            }
                        }
                    }
                    }
                }
            }
        }

        // Hidden tiles tray (edit mode only)
        AnimatedVisibility(visible = editMode && hiddenTiles.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(Res.string.library_section_hidden),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                hiddenTiles.chunked(columns).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(TILE_SPACING)
                    ) {
                        row.forEach { pref ->
                            val data = LibraryShortcut.fromId(pref.id)?.let { runtime[it] }
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(tileHeight)
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
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutTile(
    data: RuntimeShortcut,
    showIndicator: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.clip(TILE_SHAPE)) {
        LibrarySmallItem(
            title = data.title,
            icon = data.icon,
            contentColor = data.contentColor,
            shape = TILE_SHAPE,
            useIndicator = data.useIndicator && showIndicator,
            indicatorCounter = data.indicatorCounter,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
