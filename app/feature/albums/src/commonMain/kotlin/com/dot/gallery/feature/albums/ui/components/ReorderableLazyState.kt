/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.albums.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Visible item geometry in the lazy container's own coordinates. */
internal data class ReorderItem(val key: Any, val index: Int, val bounds: Rect)

/** The parts of a lazy grid or list that drag-to-reorder needs. */
internal interface ReorderLayout {
    val items: List<ReorderItem>
    /** Visible area between the content paddings, in container coordinates. */
    val visibleStart: Float
    val visibleEnd: Float
    val firstVisibleIndex: Int
    val firstVisibleOffset: Int
    suspend fun scrollBy(pixels: Float): Float
    suspend fun scrollToItem(index: Int, offset: Int)
}

private class GridReorderLayout(private val state: LazyGridState) : ReorderLayout {
    override val items: List<ReorderItem>
        get() {
            val info = state.layoutInfo
            val start = info.viewportStartOffset
            return info.visibleItemsInfo.map {
                val top = (it.offset.y - start).toFloat()
                val left = it.offset.x.toFloat()
                ReorderItem(
                    it.key, it.index,
                    Rect(left, top, left + it.size.width, top + it.size.height)
                )
            }
        }
    override val visibleStart: Float get() = state.layoutInfo.beforeContentPadding.toFloat()
    override val visibleEnd: Float
        get() = (state.layoutInfo.viewportSize.height - state.layoutInfo.afterContentPadding).toFloat()
    override val firstVisibleIndex: Int get() = state.firstVisibleItemIndex
    override val firstVisibleOffset: Int get() = state.firstVisibleItemScrollOffset
    override suspend fun scrollBy(pixels: Float): Float =
        state.scrollBy(pixels)
    override suspend fun scrollToItem(index: Int, offset: Int) = state.scrollToItem(index, offset)
}

private class ListReorderLayout(private val state: LazyListState) : ReorderLayout {
    override val items: List<ReorderItem>
        get() {
            val info = state.layoutInfo
            val start = info.viewportStartOffset
            val width = info.viewportSize.width.toFloat()
            return info.visibleItemsInfo.map {
                val top = (it.offset - start).toFloat()
                ReorderItem(it.key, it.index, Rect(0f, top, width, top + it.size))
            }
        }
    override val visibleStart: Float get() = state.layoutInfo.beforeContentPadding.toFloat()
    override val visibleEnd: Float
        get() = (state.layoutInfo.viewportSize.height - state.layoutInfo.afterContentPadding).toFloat()
    override val firstVisibleIndex: Int get() = state.firstVisibleItemIndex
    override val firstVisibleOffset: Int get() = state.firstVisibleItemScrollOffset
    override suspend fun scrollBy(pixels: Float): Float =
        state.scrollBy(pixels)
    override suspend fun scrollToItem(index: Int, offset: Int) = state.scrollToItem(index, offset)
}

/**
 * Drag-to-reorder for a lazy grid or list. A long press lifts the item under the finger; it
 * follows the finger, swaps with whichever item its centre enters, and scrolls the container
 * when held near an edge. [onMove] receives item keys and must move the backing data.
 */
@Stable
internal class ReorderableLazyState(
    private val scope: CoroutineScope,
    private val layout: ReorderLayout,
    private val edgeZone: Float,
    private val maxScrollPerFrame: Float,
    private val canDrag: (key: Any) -> Boolean,
    private val onMove: (from: Any, to: Any) -> Unit,
    private val haptics: () -> HapticFeedback,
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set

    val isDragging: Boolean get() = draggingKey != null

    private var startBounds = Rect.Zero
    private var startPointer = Offset.Zero
    private var dragDelta by mutableStateOf(Offset.Zero)
    private var expectedIndex = -1
    private var autoScrollJob: Job? = null

    /** Offset to apply to the dragged item so it stays under the finger. */
    fun translationOf(key: Any): Offset {
        if (key != draggingKey) return Offset.Zero
        val current = layout.items.firstOrNull { it.key == key } ?: return Offset.Zero
        return startBounds.topLeft + dragDelta - current.bounds.topLeft
    }

    internal fun startDrag(position: Offset): Boolean {
        val item = layout.items.firstOrNull { position in it.bounds && canDrag(it.key) } ?: return false
        draggingKey = item.key
        startBounds = item.bounds
        startPointer = position
        dragDelta = Offset.Zero
        expectedIndex = item.index
        haptics().performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        autoScrollJob = scope.launch { autoScroll() }
        return true
    }

    internal fun drag(delta: Offset) {
        if (!isDragging) return
        dragDelta += delta
        swapIfNeeded()
    }

    internal fun endDrag() {
        if (!isDragging) return
        autoScrollJob?.cancel()
        autoScrollJob = null
        draggingKey = null
        dragDelta = Offset.Zero
        haptics().performHapticFeedback(HapticFeedbackType.Confirm)
    }

    private fun swapIfNeeded() {
        val key = draggingKey ?: return
        val items = layout.items
        val dragged = items.firstOrNull { it.key == key } ?: return
        // Wait until the layout reflects the previous move, otherwise the stale geometry
        // makes the item bounce between two slots.
        if (dragged.index != expectedIndex) return
        val center = startBounds.translate(dragDelta).center
        val target = items.firstOrNull {
            it.key != key && center in it.bounds && canDrag(it.key)
        } ?: return
        val firstIndex = layout.firstVisibleIndex
        val firstOffset = layout.firstVisibleOffset
        val anchorsViewport = dragged.index == firstIndex || target.index == firstIndex
        expectedIndex = target.index
        onMove(key, target.key)
        haptics().performHapticFeedback(HapticFeedbackType.SegmentTick)
        // Lazy layouts keep the first visible item by key; when that item is part of the move
        // the viewport would jump with it, so pin the scroll position by index instead.
        if (anchorsViewport) scope.launch { layout.scrollToItem(firstIndex, firstOffset) }
    }

    private suspend fun autoScroll() {
        while (scope.isActive && isDragging) {
            withFrameNanos { }
            val pointerY = startPointer.y + dragDelta.y
            val speed = when {
                pointerY < layout.visibleStart + edgeZone ->
                    -maxScrollPerFrame * ((layout.visibleStart + edgeZone - pointerY) / edgeZone).coerceIn(0f, 1f)
                pointerY > layout.visibleEnd - edgeZone ->
                    maxScrollPerFrame * ((pointerY - (layout.visibleEnd - edgeZone)) / edgeZone).coerceIn(0f, 1f)
                else -> 0f
            }
            if (speed != 0f && layout.scrollBy(speed) != 0f) swapIfNeeded()
        }
    }
}

@Composable
internal fun rememberReorderableGridState(
    gridState: LazyGridState,
    canDrag: (key: Any) -> Boolean,
    onMove: (from: Any, to: Any) -> Unit,
): ReorderableLazyState = rememberReorderableState(
    layout = remember(gridState) { GridReorderLayout(gridState) },
    canDrag = canDrag,
    onMove = onMove,
)

@Composable
internal fun rememberReorderableListState(
    listState: LazyListState,
    canDrag: (key: Any) -> Boolean,
    onMove: (from: Any, to: Any) -> Unit,
): ReorderableLazyState = rememberReorderableState(
    layout = remember(listState) { ListReorderLayout(listState) },
    canDrag = canDrag,
    onMove = onMove,
)

@Composable
private fun rememberReorderableState(
    layout: ReorderLayout,
    canDrag: (key: Any) -> Boolean,
    onMove: (from: Any, to: Any) -> Unit,
): ReorderableLazyState {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics by rememberUpdatedState(LocalHapticFeedback.current)
    val currentCanDrag by rememberUpdatedState(canDrag)
    val currentOnMove by rememberUpdatedState(onMove)
    return remember(layout, scope, density) {
        ReorderableLazyState(
            scope = scope,
            layout = layout,
            edgeZone = with(density) { 72.dp.toPx() },
            maxScrollPerFrame = with(density) { 14.dp.toPx() },
            canDrag = { currentCanDrag(it) },
            onMove = { from, to -> currentOnMove(from, to) },
            haptics = { haptics },
        )
    }
}

/**
 * Lifts an item after a long press and drags it. Attach it to the lazy container itself so
 * pointer positions share the coordinates of its layout info. Events are read in the
 * [PointerEventPass.Initial] pass so the container's own scrolling never sees the drag.
 */
internal fun Modifier.reorderable(state: ReorderableLazyState): Modifier =
    pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val slop = viewConfiguration.touchSlop
            // Null means the press outlived the long-press timeout without moving or lifting.
            val endedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial)
                        .changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed || change.isConsumed ||
                        (change.position - down.position).getDistance() > slop
                    ) break
                }
            }
            if (endedEarly != null) return@awaitEachGesture
            if (!state.startDrag(down.position)) return@awaitEachGesture
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        change.consume()
                        break
                    }
                    val delta = change.positionChange()
                    change.consume()
                    if (delta != Offset.Zero) state.drag(delta)
                }
            } finally {
                state.endDrag()
            }
        }
    }

/** Swallows taps on an item while arranging so clicks and option sheets stay inert. */
internal fun Modifier.blockTaps(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).consume()
    }
}
