/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.mediaview.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.R
import com.dot.gallery.core.presentation.components.CheckBox
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isCloud
import com.dot.gallery.feature_node.presentation.mediaview.LocalMediaViewerVisualPolicy
import com.github.panpf.sketch.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.abs

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
    showCloudLabels: Boolean = false,
    multiSelectMode: Boolean = false,
    multiSelectedIds: Set<Long> = emptySet(),
    onEnterMultiSelect: (Long) -> Unit = {},
    onToggleMultiSelect: (Long) -> Unit = {},
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    // "Animate media items" off — selection borders appear instantly.
    val animationsEnabled = LocalMediaViewerVisualPolicy.current.animationsEnabled
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

    // Auto-select center-most visible item while user is scrolling (only in normal mode)
    LaunchedEffect(members, multiSelectMode) {
        if (multiSelectMode) return@LaunchedEffect
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
            val isCurrent = member.id == selectedId
            val isMultiSelected = multiSelectMode && member.id in multiSelectedIds
            val showBorder = isCurrent || isMultiSelected
            val borderColor = if (isMultiSelected) {
                Color(0xFF90CAF9) // light blue for multi-select
            } else {
                Color.White // white for current viewing item
            }
            val borderWidth by animateDpAsState(
                targetValue = if (showBorder) SELECTED_BORDER_WIDTH else 0.dp,
                animationSpec = if (animationsEnabled) spring() else snap(),
                label = "thumbnailBorder"
            )
            Box(
                modifier = Modifier
                    .animateItem()
                    .size(THUMBNAIL_SIZE)
                    .clip(THUMBNAIL_SHAPE)
                    .then(
                        if (showBorder) {
                            Modifier.border(
                                width = borderWidth,
                                color = borderColor,
                                shape = THUMBNAIL_SHAPE
                            )
                        } else Modifier
                    )
                    .combinedClickable(
                        onClick = {
                            if (multiSelectMode) {
                                onToggleMultiSelect(member.id)
                            } else {
                                val index = members.indexOfFirst { it.id == member.id }
                                if (index >= 0) {
                                    scope.launch {
                                        listState.animateScrollToItem(index)
                                    }
                                }
                            }
                        },
                        onLongClick = if (multiSelectMode) null else {
                            { onEnterMultiSelect(member.id) }
                        }
                    )
            ) {
                AsyncImage(
                    uri = member.getUri().toString(),
                    contentDescription = member.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(THUMBNAIL_SHAPE)
                )
                if (multiSelectMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(2.dp)
                    ) {
                        CheckBox(isChecked = isMultiSelected)
                    }
                }
                if (showCloudLabels) {
                    val labelShape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                color = Color.Black.copy(alpha = 0.55f),
                                shape = labelShape
                            )
                            .padding(vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (member.isCloud) "Cloud" else "Local",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GroupMemberSelectionBar(
    selectedCount: Int,
    totalCount: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
                shape = shape
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.selection_dialog_close_cd),
                tint = Color.White
            )
        }
        Text(
            text = selectedCount.toString(),
            color = Color.White,
            style = MaterialTheme.typography.titleSmall
        )
        IconButton(onClick = onSelectAll) {
            Icon(
                imageVector = Icons.Outlined.SelectAll,
                contentDescription = stringResource(R.string.select_all),
                tint = Color.White
            )
        }
        IconButton(
            onClick = onShare,
            enabled = selectedCount > 0
        ) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = stringResource(R.string.share),
                tint = if (selectedCount > 0) Color.White else Color.White.copy(alpha = 0.38f)
            )
        }
    }
}
