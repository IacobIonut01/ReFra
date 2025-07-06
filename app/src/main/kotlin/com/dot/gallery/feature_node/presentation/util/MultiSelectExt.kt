package com.dot.gallery.feature_node.presentation.util

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toIntRect
import com.dot.gallery.feature_node.domain.util.isHeaderKey
import com.dot.gallery.feature_node.domain.util.isIgnoredKey

private val String?.mediaIdFromKey: Long?
    get() = this?.let {
        if (isHeaderKey || isIgnoredKey) null
        else if (it.startsWith("{")) removePrefix("{").substringBefore(",").toLongOrNull()
        else removePrefix("media_").substringBefore("_").toLongOrNull()
    }


fun Modifier.photoGridDragHandler(
    lazyGridState: LazyGridState,
    haptics: HapticFeedback,
    selectedIds: State<Set<Long>>,
    updateSelectedIds: (Set<Long>) -> Unit,
    autoScrollSpeed: MutableState<Float>,
    autoScrollThreshold: Float,
    scrollGestureActive: MutableState<Boolean>,
    layoutDirection: LayoutDirection,
    contentPadding: PaddingValues,
    allKeys: List<String>
) = pointerInput(Unit) {
    // pre-compute the corresponding IDs
    val mediaIdsInOrder = allKeys.mapNotNull { it.mediaIdFromKey }

    // padding in px
    val padL = contentPadding.calculateLeftPadding(layoutDirection).toPx()
    val padT = contentPadding.calculateTopPadding().toPx()

    // helper: find the raw key under the finger
    fun LazyGridState.hitKeyAt(raw: Offset): String? {
        val contentOffset = raw - Offset(padL, padT)
        return layoutInfo.visibleItemsInfo
            .find { info ->
                info.size.toIntRect()
                    .contains((contentOffset.round() - info.offset))
            }
            ?.key as? String
    }

    var initialMediaIndex: Int? = null
    var currentMediaIndex: Int? = null

    detectDragGesturesAfterLongPress(
        onDragStart = { raw ->
            scrollGestureActive.value = true
            lazyGridState.hitKeyAt(raw)?.let { key ->
                val idx = allKeys.indexOf(key)
                val id = key.mediaIdFromKey
                if (idx >= 0 && id != null && id !in selectedIds.value) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    initialMediaIndex = idx
                    currentMediaIndex = idx
                    updateSelectedIds(selectedIds.value + id)
                }
            }
        },
        onDragCancel = {
            scrollGestureActive.value = false
            initialMediaIndex = null
            autoScrollSpeed.value = 0f
        },
        onDragEnd = {
            scrollGestureActive.value = false
            initialMediaIndex = null
            autoScrollSpeed.value = 0f
        },
        onDrag = { change, _ ->
            val raw = change.position
            if (initialMediaIndex != null) {
                val distB = lazyGridState.layoutInfo.viewportSize.height - raw.y
                val distT = raw.y
                autoScrollSpeed.value = when {
                    distB < autoScrollThreshold -> autoScrollThreshold - distB
                    distT < autoScrollThreshold -> -(autoScrollThreshold - distT)
                    else -> 0f
                }

                lazyGridState.hitKeyAt(raw)?.let { key ->
                    val newIdx = allKeys.indexOf(key)
                    if (newIdx >= 0 && newIdx != currentMediaIndex) {
                        val start = initialMediaIndex!!
                        val oldEnd = currentMediaIndex!!
                        val oldRange = if (oldEnd >= start) start..oldEnd else oldEnd..start
                        val newRange = if (newIdx >= start) start..newIdx else newIdx..start

                        // map to real IDs
                        val oldIds = oldRange.mapNotNull { mediaIdsInOrder.getOrNull(it) }.toSet()
                        val newIds = newRange.mapNotNull { mediaIdsInOrder.getOrNull(it) }.toSet()

                        // subtract oldRange, add newRange
                        updateSelectedIds(selectedIds.value - oldIds + newIds)
                        currentMediaIndex = newIdx
                    }
                }
            }
        },
    )
}