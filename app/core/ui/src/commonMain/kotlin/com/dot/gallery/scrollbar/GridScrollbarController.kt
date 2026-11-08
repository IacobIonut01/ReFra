/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.scrollbar

import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlin.math.floor

/**
 * Remembers a [GridScrollbarController] bound to [state].
 *
 * Vertical-grid only by design: this is the exact path the Gallery timeline uses.
 */
@androidx.compose.runtime.Composable
internal fun rememberGridScrollbarController(
    state: LazyGridState,
    thumbMinLength: Float,
    thumbMaxLength: Float,
    alwaysShowScrollBar: Boolean,
    selectionMode: ScrollbarSelectionMode,
    snapIndices: IntArray,
    snapScrollOffset: Int,
    onSnap: () -> Unit,
): GridScrollbarController {
    val thumbMinLengthUpdated = rememberUpdatedState(thumbMinLength)
    val thumbMaxLengthUpdated = rememberUpdatedState(thumbMaxLength)
    val alwaysShowScrollBarUpdated = rememberUpdatedState(alwaysShowScrollBar)
    val selectionModeUpdated = rememberUpdatedState(selectionMode)
    val snapIndicesUpdated = rememberUpdatedState(snapIndices)
    val snapScrollOffsetUpdated = rememberUpdatedState(snapScrollOffset)
    val onSnapUpdated = rememberUpdatedState(onSnap)
    val reverseLayout = remember { derivedStateOf { state.layoutInfo.reverseLayout } }

    val isSelected = remember { mutableStateOf(false) }
    val dragOffset = remember { mutableFloatStateOf(0f) }
    val dragTargetIndex = remember { mutableIntStateOf(0) }

    val realFirstVisibleItem = remember {
        derivedStateOf {
            state.layoutInfo.visibleItemsInfo.firstOrNull {
                it.index == state.firstVisibleItemIndex
            }
        }
    }

    // LazyGridState doesn't expose the column count, so infer it from the first row.
    val nElementsMainAxis = remember {
        derivedStateOf {
            var count = 0
            for (item in state.layoutInfo.visibleItemsInfo) {
                val index = item.column
                if (index == -1) break
                if (count == index) count += 1 else break
            }
            count.coerceAtLeast(1)
        }
    }

    // NOTE: upstream divided by size.width for the vertical axis (a latent bug that
    // only worked for square cells). We correctly use the height here.
    fun LazyGridItemInfo.fractionHiddenTop(firstItemOffset: Int): Float =
        if (size.height == 0) 0f else firstItemOffset / size.height.toFloat()

    fun LazyGridItemInfo.fractionVisibleBottom(viewportEndOffset: Int): Float =
        if (size.height == 0) 0f else (viewportEndOffset - offset.y).toFloat() / size.height.toFloat()

    /**
     * Scroll progress in item units: the first visible row's index plus the hidden share of
     * that row, spread over the items in it. Unlike a row count derived from `index / columns`,
     * this keeps rising smoothly past full-width headers and mosaic tiles, which break the row
     * alignment and made the thumb jump back and change size.
     */
    fun rowProgress(item: LazyGridItemInfo, hiddenOrVisible: Float): Float {
        val rowItems = state.layoutInfo.visibleItemsInfo.count { it.offset.y == item.offset.y }
            .coerceAtLeast(1)
        val rowStart = state.layoutInfo.visibleItemsInfo
            .filter { it.offset.y == item.offset.y }
            .minOfOrNull { it.index } ?: item.index
        return rowStart + hiddenOrVisible.coerceIn(0f, 1f) * rowItems
    }

    val visibleStartProgress = remember {
        derivedStateOf {
            val firstItem = realFirstVisibleItem.value ?: return@derivedStateOf 0f
            rowProgress(firstItem, firstItem.fractionHiddenTop(state.firstVisibleItemScrollOffset))
        }
    }

    val thumbSizeNormalizedReal = remember {
        derivedStateOf {
            state.layoutInfo.let {
                if (it.totalItemsCount == 0 || it.visibleItemsInfo.isEmpty()) return@let 0f
                val lastItem = it.visibleItemsInfo.last()
                val end = rowProgress(lastItem, lastItem.fractionVisibleBottom(it.viewportEndOffset))
                ((end - visibleStartProgress.value) / it.totalItemsCount).coerceIn(0f, 1f)
            }
        }
    }

    val thumbSizeNormalized = remember {
        derivedStateOf {
            thumbSizeNormalizedReal.value.coerceIn(
                thumbMinLengthUpdated.value,
                thumbMaxLengthUpdated.value,
            )
        }
    }

    fun offsetCorrection(top: Float): Float {
        val topRealMax = (1f - thumbSizeNormalizedReal.value).coerceIn(0f, 1f)
        if (thumbSizeNormalizedReal.value >= thumbMinLengthUpdated.value) {
            return if (reverseLayout.value) topRealMax - top else top
        }
        val topMax = 1f - thumbMinLengthUpdated.value
        return if (reverseLayout.value) {
            (topRealMax - top) * topMax / topRealMax
        } else {
            top * topMax / topRealMax
        }
    }

    val layoutThumbOffsetNormalized = remember {
        derivedStateOf {
            state.layoutInfo.let {
                if (it.totalItemsCount == 0 || it.visibleItemsInfo.isEmpty()) return@let 0f
                offsetCorrection(visibleStartProgress.value / it.totalItemsCount)
            }
        }
    }

    // PERF: while dragging, drive the thumb straight from the drag offset so it tracks
    // the finger 1:1 instead of waiting for the list to lay out (visible fast-scroll lag).
    val thumbOffsetNormalized = remember {
        derivedStateOf {
            if (isSelected.value) {
                if (reverseLayout.value) {
                    (1f - thumbSizeNormalized.value - dragOffset.floatValue).coerceIn(0f, 1f)
                } else {
                    dragOffset.floatValue
                }
            } else {
                layoutThumbOffsetNormalized.value
            }
        }
    }

    val thumbIsInAction = remember {
        derivedStateOf {
            state.isScrollInProgress || isSelected.value || alwaysShowScrollBarUpdated.value
        }
    }

    return remember {
        GridScrollbarController(
            thumbSizeNormalized = thumbSizeNormalized,
            thumbSizeNormalizedReal = thumbSizeNormalizedReal,
            visibleStartProgress = visibleStartProgress,
            thumbOffsetNormalized = thumbOffsetNormalized,
            thumbIsInAction = thumbIsInAction,
            isSelected = isSelected,
            dragOffset = dragOffset,
            dragTargetIndex = dragTargetIndex,
            selectionMode = selectionModeUpdated,
            realFirstVisibleItem = realFirstVisibleItem,
            thumbMinLength = thumbMinLengthUpdated,
            reverseLayout = reverseLayout,
            nElementsMainAxis = nElementsMainAxis,
            snapIndices = snapIndicesUpdated,
            snapScrollOffset = snapScrollOffsetUpdated,
            onSnap = onSnapUpdated,
            state = state,
        )
    }
}

@Stable
internal class GridScrollbarController(
    val thumbSizeNormalized: State<Float>,
    val thumbOffsetNormalized: State<Float>,
    val thumbIsInAction: State<Boolean>,
    val isSelected: MutableState<Boolean>,
    private val thumbSizeNormalizedReal: State<Float>,
    private val visibleStartProgress: State<Float>,
    private val dragOffset: MutableFloatState,
    private val dragTargetIndex: MutableIntState,
    private val selectionMode: State<ScrollbarSelectionMode>,
    private val realFirstVisibleItem: State<LazyGridItemInfo?>,
    private val thumbMinLength: State<Float>,
    private val reverseLayout: State<Boolean>,
    private val nElementsMainAxis: State<Int>,
    private val snapIndices: State<IntArray>,
    private val snapScrollOffset: State<Int>,
    private val onSnap: State<() -> Unit>,
    private val state: LazyGridState,
) {
    /** Month start the thumb was last over, used to fire one haptic per month crossing. */
    private var lastSnappedIndex = -1
    /**
     * Latest coalesced scroll request, consumed by the owning composable via
     * `snapshotFlow { scrollTarget.value }.collectLatest { ... }`.
     */
    val scrollTarget: MutableState<GridScrollTarget?> = mutableStateOf(null)

    /**
     * The item index the indicator should describe. While dragging we return the
     * *intended* target (computed from the drag offset) so the label updates instantly
     * without waiting for the list to scroll/lay out; otherwise the real first item.
     */
    /** The grid's real position, in the same item units as [GridScrollTarget.progress]. */
    fun currentProgress(): Float = visibleStartProgress.value

    /** How many items' worth of progress the viewport shows; converts progress to pixels. */
    fun visibleSpan(): Float = thumbSizeNormalizedReal.value * state.layoutInfo.totalItemsCount

    fun indicatorValue(): Int =
        if (isSelected.value) dragTargetIndex.intValue else state.firstVisibleItemIndex

    fun onDraggableState(deltaPixels: Float, maxLengthPixels: Float) {
        if (maxLengthPixels <= 0f) return
        val displace = if (reverseLayout.value) -deltaPixels else deltaPixels
        if (isSelected.value) {
            setScrollOffset(dragOffset.floatValue + displace / maxLengthPixels)
        }
    }

    fun onDragStarted(offsetPixels: Float, maxLengthPixels: Float) {
        if (maxLengthPixels <= 0f) return
        val newOffset = if (reverseLayout.value) {
            (maxLengthPixels - offsetPixels) / maxLengthPixels
        } else {
            offsetPixels / maxLengthPixels
        }
        val currentOffset = if (reverseLayout.value) {
            1f - thumbOffsetNormalized.value - thumbSizeNormalized.value
        } else {
            thumbOffsetNormalized.value
        }

        when (selectionMode.value) {
            ScrollbarSelectionMode.Full -> {
                if (newOffset in currentOffset..(currentOffset + thumbSizeNormalized.value)) {
                    setDragOffset(currentOffset)
                } else {
                    setScrollOffset(newOffset)
                }
                isSelected.value = true
            }

            ScrollbarSelectionMode.Thumb -> {
                if (newOffset in currentOffset..(currentOffset + thumbSizeNormalized.value)) {
                    setDragOffset(currentOffset)
                    isSelected.value = true
                }
            }

            ScrollbarSelectionMode.Disabled -> Unit
        }

        // Seed the month origin so the first month crossing (not the grab itself) triggers haptics.
        if (isSelected.value) {
            dragTargetIndex.intValue = state.firstVisibleItemIndex
            if (snapIndices.value.isNotEmpty()) {
                lastSnappedIndex = monthStartAtOrBelow(state.firstVisibleItemIndex)
            }
        }
    }

    fun onDragStopped() {
        isSelected.value = false
        scrollTarget.value = null
        lastSnappedIndex = -1
    }

    private fun setScrollOffset(newOffset: Float) {
        // Keep the thumb tracking the finger 1:1 regardless of snapping.
        setDragOffset(newOffset)
        val totalItems = state.layoutInfo.totalItemsCount
        if (totalItems == 0) return
        // The inverse of the thumb's item-space progress, so letting go leaves the thumb where
        // the finger put it instead of jumping to a differently computed position.
        val exactIndex = (offsetCorrectionInverse(dragOffset.floatValue) * totalItems)
            .coerceIn(0f, (totalItems - 1).toFloat())
        val continuousIndex = floor(exactIndex).toInt()

        // The grid follows the thumb continuously; month starts only mark where a haptic tick
        // fires, once each time the thumb crosses into a different month.
        if (snapIndices.value.isNotEmpty()) {
            val monthStart = monthStartAtOrBelow(continuousIndex)
            if (monthStart != lastSnappedIndex) {
                lastSnappedIndex = monthStart
                onSnap.value()
            }
        }

        dragTargetIndex.intValue = continuousIndex
        scrollTarget.value = GridScrollTarget(progress = exactIndex)
    }

    /** Greatest month-start index that is &lt;= [target] (the month the thumb currently points at). */
    private fun monthStartAtOrBelow(target: Int): Int {
        val snaps = snapIndices.value
        if (snaps.isEmpty()) return target
        var lo = 0
        var hi = snaps.size - 1
        var result = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (snaps[mid] <= target) {
                result = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return snaps[result]
    }

    private fun setDragOffset(value: Float) {
        val maxValue = (1f - thumbSizeNormalized.value).coerceAtLeast(0f)
        dragOffset.floatValue = value.coerceIn(0f, maxValue)
    }

    private fun offsetCorrectionInverse(top: Float): Float {
        if (thumbSizeNormalizedReal.value >= thumbMinLength.value) return top
        val topRealMax = 1f - thumbSizeNormalizedReal.value
        val topMax = 1f - thumbMinLength.value
        return top * topRealMax / topMax
    }
}
