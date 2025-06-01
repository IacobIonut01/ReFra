package com.dot.gallery.feature_node.presentation.common.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridItemInfo
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset

@Composable
fun StickyHeaderGrid(
    state: LazyGridState,
    modifier: Modifier,
    headerMatcher: @DisallowComposableCalls (LazyGridItemInfo) -> Boolean,
    searchBarOffset: @DisallowComposableCalls () -> Int,
    toolbarOffset: @DisallowComposableCalls () -> Int,
    stickyHeader: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    StickyHeaderLayout(
        lazyState = state,
        modifier = modifier,
        viewportStart = { state.layoutInfo.viewportStartOffset },
        lazyItems = { state.layoutInfo.visibleItemsInfo },
        lazyItemOffset = { offset.y },
        lazyItemHeight = { size.height },
        headerMatcher = headerMatcher,
        stickyHeader = stickyHeader,
        searchBarOffset = searchBarOffset,
        toolbarOffset = toolbarOffset,
        content = content,
    )
}

@Composable
inline fun StickyHeaderStaggeredGrid(
    state: LazyStaggeredGridState,
    modifier: Modifier,
    crossinline headerMatcher: @DisallowComposableCalls (LazyStaggeredGridItemInfo) -> Boolean,
    crossinline searchBarOffset: @DisallowComposableCalls () -> Int,
    crossinline toolbarOffset: @DisallowComposableCalls () -> Int,
    stickyHeader: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    StickyHeaderLayout(
        lazyState = state,
        modifier = modifier,
        viewportStart = { state.layoutInfo.viewportStartOffset },
        lazyItems = { state.layoutInfo.visibleItemsInfo },
        lazyItemOffset = { offset.y },
        lazyItemHeight = { size.height },
        headerMatcher = headerMatcher,
        stickyHeader = stickyHeader,
        searchBarOffset = searchBarOffset,
        toolbarOffset = toolbarOffset,
        content = content,
    )
}

@Composable
inline fun <LazyState : ScrollableState, LazyItem> StickyHeaderLayout(
    lazyState: LazyState,
    modifier: Modifier = Modifier,
    crossinline viewportStart: @DisallowComposableCalls (LazyState) -> Int,
    crossinline lazyItems: @DisallowComposableCalls (LazyState) -> List<LazyItem>,
    crossinline lazyItemOffset: @DisallowComposableCalls LazyItem.() -> Int,
    crossinline lazyItemHeight: @DisallowComposableCalls LazyItem.() -> Int,
    crossinline searchBarOffset: @DisallowComposableCalls () -> Int,
    crossinline toolbarOffset: @DisallowComposableCalls () -> Int,
    crossinline headerMatcher: @DisallowComposableCalls LazyItem.() -> Boolean,
    stickyHeader: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val headerOffset by remember(lazyState) {
        derivedStateOf {
            val searchBarOffsetValue = searchBarOffset()
            val startOffset = viewportStart(lazyState)
            val visibleItems = lazyItems(lazyState)
            val firstCompletelyVisibleItem = visibleItems.firstOrNull { lazyItem ->
                lazyItemOffset(lazyItem) >= startOffset
            } ?: return@derivedStateOf 0

            when (headerMatcher(firstCompletelyVisibleItem)) {
                false -> 0
                true -> lazyItemHeight(firstCompletelyVisibleItem)
                    .minus(lazyItemOffset(firstCompletelyVisibleItem))
                    .let { difference -> if (difference < 0) 0 else -difference - searchBarOffsetValue}
            }
        }
    }
    val toolbarOffsetValue by remember(headerOffset, toolbarOffset()) {
        derivedStateOf {
            toolbarOffset()
        }
    }

    val offsetAnimation by animateIntOffsetAsState(
        remember(headerOffset, toolbarOffsetValue) {
            IntOffset(
                x = 0,
                y = headerOffset + toolbarOffsetValue
            )
        }, label = "offsetAnimation"
    )

    val alphaAnimation by animateFloatAsState(
        targetValue = remember(offsetAnimation) { if (offsetAnimation.y < -100) 0f else 1f },
        label = "alphaAnimation",
        animationSpec = tween(100, 10),
    )

    Box(modifier = modifier) {
        content()
        Box(
            modifier = Modifier
                .graphicsLayer {
                    alpha = alphaAnimation
                }
                .offset { offsetAnimation }
        ) {
            stickyHeader()
        }
    }
}