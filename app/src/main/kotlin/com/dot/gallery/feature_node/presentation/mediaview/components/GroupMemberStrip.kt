/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.mediaview.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.getUri
import com.github.panpf.sketch.AsyncImage
import kotlin.math.abs
import kotlinx.coroutines.launch

private val THUMBNAIL_SIZE = 56.dp
private val ITEM_SPACING = 6.dp
private val SELECTED_BORDER_WIDTH = 2.dp
private val THUMBNAIL_SHAPE = RoundedCornerShape(8.dp)

@Composable
fun <T : Media> GroupMemberStrip(
    members: List<T>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val selectedIndex = remember(members, selectedId) {
        members.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    }

    // Track strip width to compute center padding
    var stripWidthPx by rememberSaveable { mutableIntStateOf(0) }
    val thumbnailPx = with(density) { THUMBNAIL_SIZE.roundToPx() }
    val centerPaddingPx = ((stripWidthPx - thumbnailPx) / 2).coerceAtLeast(0)
    val centerPadding = with(density) { centerPaddingPx.toDp() }

    // Initialize scroll to center the selected item
    LaunchedEffect(selectedIndex, stripWidthPx) {
        if (stripWidthPx > 0 && !listState.isScrollInProgress) {
            listState.scrollToItem(selectedIndex)
        }
    }

    // Auto-select center-most visible item while user is scrolling
    LaunchedEffect(members) {
        snapshotFlow {
            if (!listState.isScrollInProgress) return@snapshotFlow null
            val layoutInfo = listState.layoutInfo
            val viewportCenter =
                (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            layoutInfo.visibleItemsInfo.minByOrNull {
                abs((it.offset + it.size / 2) - viewportCenter)
            }?.index
        }.collect { centerIndex ->
            if (centerIndex != null) {
                val memberId = members.getOrNull(centerIndex)?.id
                if (memberId != null) {
                    onSelect(memberId)
                }
            }
        }
    }

    LazyRow(
        modifier = modifier.onSizeChanged { stripWidthPx = it.width },
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(ITEM_SPACING),
        contentPadding = PaddingValues(horizontal = centerPadding),
        verticalAlignment = Alignment.CenterVertically,
        flingBehavior = rememberSnapFlingBehavior(
            lazyListState = listState,
            snapPosition = SnapPosition.Center
        )
    ) {
        items(
            items = members,
            key = { it.id }
        ) { member ->
            val isSelected = member.id == selectedId
            val borderWidth by animateDpAsState(
                targetValue = if (isSelected) SELECTED_BORDER_WIDTH else 0.dp,
                label = "thumbnailBorder"
            )
            Box(
                modifier = Modifier
                    .animateItem()
                    .size(THUMBNAIL_SIZE)
                    .clip(THUMBNAIL_SHAPE)
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                width = borderWidth,
                                color = Color.White,
                                shape = THUMBNAIL_SHAPE
                            )
                        } else Modifier
                    )
                    .clickable {
                        val index = members.indexOfFirst { it.id == member.id }
                        if (index >= 0) {
                            scope.launch {
                                listState.animateScrollToItem(index)
                            }
                        }
                    }
            ) {
                AsyncImage(
                    uri = member.getUri().toString(),
                    contentDescription = member.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(THUMBNAIL_SHAPE)
                )
            }
        }
    }
}
