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

private fun LazyGridState.hitKeyAt(
    raw: Offset,
    padL: Float,
    padT: Float
): String? {
    val contentOffset = raw - Offset(padL, padT)
    return layoutInfo.visibleItemsInfo
        .find { info ->
            info.size.toIntRect()
                .contains((contentOffset.round() - info.offset))
        }
        ?.key as? String
}

private data class HitInfo(val key: String, val normalizedX: Float, val normalizedY: Float)

private fun LazyGridState.hitInfoAt(
    raw: Offset,
    padL: Float,
    padT: Float
): HitInfo? {
    val contentOffset = raw - Offset(padL, padT)
    val info = layoutInfo.visibleItemsInfo
        .find { it.size.toIntRect().contains((contentOffset.round() - it.offset)) }
        ?: return null
    val key = info.key as? String ?: return null
    val rel = contentOffset - Offset(info.offset.x.toFloat(), info.offset.y.toFloat())
    return HitInfo(
        key = key,
        normalizedX = (rel.x / info.size.width).coerceIn(0f, 1f),
        normalizedY = (rel.y / info.size.height).coerceIn(0f, 1f)
    )
}

private fun resolveHitIds(
    hit: HitInfo,
    allIds: List<Long>
): List<Long> = when {
    hit.key.startsWith("mosaic_pair_") && allIds.size == 2 ->
        listOf(if (hit.normalizedY < 0.5f) allIds[0] else allIds[1])
    hit.key.startsWith("mosaic_quad_") && allIds.size == 4 -> {
        val col = if (hit.normalizedX < 0.5f) 0 else 1
        val row = if (hit.normalizedY < 0.5f) 0 else 1
        listOf(allIds[row * 2 + col])
    }
    else -> allIds
}

fun Modifier.mosaicGridDragHandler(
    lazyGridState: LazyGridState,
    haptics: HapticFeedback,
    selectedIds: State<Set<Long>>,
    updateSelectedIds: (Set<Long>) -> Unit,
    autoScrollSpeed: MutableState<Float>,
    autoScrollThreshold: Float,
    scrollGestureActive: MutableState<Boolean>,
    layoutDirection: LayoutDirection,
    contentPadding: PaddingValues,
    orderedGridKeys: List<String>,
    gridKeyToMediaIds: Map<String, List<Long>>
) = pointerInput(Unit) {
    val padL = contentPadding.calculateLeftPadding(layoutDirection).toPx()
    val padT = contentPadding.calculateTopPadding().toPx()

    var initialIndex: Int? = null
    var currentIndex: Int? = null

    detectDragGesturesAfterLongPress(
        onDragStart = { raw ->
            scrollGestureActive.value = true
            lazyGridState.hitInfoAt(raw, padL, padT)?.let { hit ->
                val idx = orderedGridKeys.indexOf(hit.key)
                val allIds = gridKeyToMediaIds[hit.key] ?: emptyList()
                if (idx >= 0 && allIds.isNotEmpty()) {
                    val hitIds = resolveHitIds(hit, allIds).toSet()
                    if (!selectedIds.value.containsAll(hitIds)) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        updateSelectedIds(selectedIds.value + hitIds)
                    }
                    initialIndex = idx
                    currentIndex = idx
                }
            }
        },
        onDragCancel = {
            scrollGestureActive.value = false
            initialIndex = null
            autoScrollSpeed.value = 0f
        },
        onDragEnd = {
            scrollGestureActive.value = false
            initialIndex = null
            autoScrollSpeed.value = 0f
        },
        onDrag = { change, _ ->
            val raw = change.position
            if (initialIndex != null) {
                val distB = lazyGridState.layoutInfo.viewportSize.height - raw.y
                val distT = raw.y
                autoScrollSpeed.value = when {
                    distB < autoScrollThreshold -> autoScrollThreshold - distB
                    distT < autoScrollThreshold -> -(autoScrollThreshold - distT)
                    else -> 0f
                }

                lazyGridState.hitKeyAt(raw, padL, padT)?.let { key ->
                    val newIdx = orderedGridKeys.indexOf(key)
                    if (newIdx >= 0 && newIdx != currentIndex) {
                        val start = initialIndex!!
                        val oldEnd = currentIndex!!
                        val oldRange = if (oldEnd >= start) start..oldEnd else oldEnd..start
                        val newRange = if (newIdx >= start) start..newIdx else newIdx..start

                        val oldIds = oldRange.flatMapTo(mutableSetOf()) {
                            gridKeyToMediaIds[orderedGridKeys.getOrNull(it)] ?: emptyList()
                        }
                        val newIds = newRange.flatMapTo(mutableSetOf()) {
                            gridKeyToMediaIds[orderedGridKeys.getOrNull(it)] ?: emptyList()
                        }

                        updateSelectedIds(selectedIds.value - oldIds + newIds)
                        currentIndex = newIdx
                    }
                }
            }
        },
    )
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

    val padL = contentPadding.calculateLeftPadding(layoutDirection).toPx()
    val padT = contentPadding.calculateTopPadding().toPx()

    var initialMediaIndex: Int? = null
    var currentMediaIndex: Int? = null

    detectDragGesturesAfterLongPress(
        onDragStart = { raw ->
            scrollGestureActive.value = true
            lazyGridState.hitKeyAt(raw, padL, padT)?.let { key ->
                val idx = allKeys.indexOf(key)
                val id = key.mediaIdFromKey
                if (idx >= 0 && id != null) {
                    if (id !in selectedIds.value) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        updateSelectedIds(selectedIds.value + id)
                    }
                    initialMediaIndex = idx
                    currentMediaIndex = idx
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

                lazyGridState.hitKeyAt(raw, padL, padT)?.let { key ->
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