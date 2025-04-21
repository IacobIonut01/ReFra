package com.dot.gallery.feature_node.presentation.util

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.MutableState
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
        printDebug(
            "mediaIdFromKey: $it | ${
                removePrefix("{").substringBefore(",").toLongOrNull()
            } | ${removePrefix("media_").substringBefore("_").toLongOrNull()}"
        )
        if (isHeaderKey || isIgnoredKey) null
        else if (it.startsWith("{")) removePrefix("{").substringBefore(",").toLongOrNull()
        else removePrefix("media_").substringBefore("_").toLongOrNull()
    }


fun Modifier.photoGridDragHandler(
    lazyGridState: LazyGridState,
    haptics: HapticFeedback,
    selectedIds: MutableState<Set<Long>>,
    autoScrollSpeed: MutableState<Float>,
    autoScrollThreshold: Float,
    scrollGestureActive: MutableState<Boolean>,
    layoutDirection: LayoutDirection,
    contentPadding: PaddingValues,
    updateSelectionState: (Boolean) -> Unit
) = pointerInput(Unit) {
    val padLeftPx = contentPadding.calculateLeftPadding(layoutDirection).toPx()
    val padTopPx = contentPadding.calculateTopPadding().toPx()

    fun LazyGridState.gridItemKeyAtPosition(rawOffset: Offset): Long? {
        // bring the pointer from grid‑local → content‑local
        val contentOffset = rawOffset - Offset(padLeftPx, padTopPx)

        val info = layoutInfo.visibleItemsInfo.find { itemInfo ->
            // subtract the item's own offset from your content-aligned point
            itemInfo.size.toIntRect()
                .contains(contentOffset.round() - itemInfo.offset)
        }
        return (info?.key as? String).mediaIdFromKey
    }

    var initialKey: Long? = null
    var currentKey: Long? = null
    detectDragGesturesAfterLongPress(
        onDragStart = { offset ->
            scrollGestureActive.value = true
            lazyGridState.gridItemKeyAtPosition(offset)?.let { key ->
                if (!selectedIds.value.contains(key)) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    initialKey = key
                    currentKey = key
                    selectedIds.value += key
                }
            }
            updateSelectionState(selectedIds.value.isNotEmpty())
        },
        onDragCancel = {
            scrollGestureActive.value = false
            initialKey = null
            autoScrollSpeed.value = 0f
        },
        onDragEnd = {
            scrollGestureActive.value = false
            initialKey = null
            autoScrollSpeed.value = 0f
        },
        onDrag = { change, _ ->
            if (initialKey != null) {
                val distFromBottom =
                    lazyGridState.layoutInfo.viewportSize.height - change.position.y
                val distFromTop = change.position.y
                autoScrollSpeed.value = when {
                    distFromBottom < autoScrollThreshold -> autoScrollThreshold - distFromBottom
                    distFromTop < autoScrollThreshold -> -(autoScrollThreshold - distFromTop)
                    else -> 0f
                }

                lazyGridState.gridItemKeyAtPosition(change.position)?.let { key ->
                    if (currentKey != key) {
                        selectedIds.value = selectedIds.value
                            .minus(initialKey!!..currentKey!!)
                            .minus(currentKey!!..initialKey!!)
                            .plus(initialKey!!..key)
                            .plus(key..initialKey!!)
                        currentKey = key
                    }
                }

                updateSelectionState(selectedIds.value.isNotEmpty())
            }
        }
    )
}