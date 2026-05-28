/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.common.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFilter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.presentation.components.Error
import com.dot.gallery.core.presentation.components.LoadingMedia
import com.dot.gallery.core.presentation.components.MediaItemHeader
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaItem
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.MosaicDisplayItem
import com.dot.gallery.feature_node.domain.model.MosaicTilePattern
import com.dot.gallery.feature_node.domain.model.isBigHeaderKey
import com.dot.gallery.feature_node.domain.model.isHeaderKey
import com.dot.gallery.feature_node.domain.model.mosaicPatternsForColumns
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.util.mediaSharedElement
import com.dot.gallery.feature_node.presentation.util.mosaicGridDragHandler
import com.dot.gallery.feature_node.presentation.util.rememberFeedbackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

fun <T : Media> buildMosaicDisplayItems(
    mappedData: List<MediaItem<T>>,
    columns: Int = 4,
): List<MosaicDisplayItem<T>> {
    val patterns = mosaicPatternsForColumns(columns)
    val result = ArrayList<MosaicDisplayItem<T>>(mappedData.size + mappedData.size / 4)
    val buffer = ArrayList<MediaItem.MediaViewItem<T>>(16)
    var blockIndex = 0
    var groupRandom = Random(0)

    fun pickPattern(): MosaicTilePattern? {
        if (patterns.isEmpty()) return null
        val bufSize = buffer.size
        val eligible = patterns.filter { it.itemsConsumed <= bufSize }
        if (eligible.isEmpty()) return null
        return eligible[groupRandom.nextInt(eligible.size)]
    }

    fun emitBlock(force: Boolean = false) {
        if (buffer.isEmpty()) return

        val pattern = pickPattern()

        if (pattern == null) {
            if (force) {
                for (item in buffer) result.add(MosaicDisplayItem.SingleItem(item))
                buffer.clear()
            }
            return
        }

        val leftSide = blockIndex % 2 == 0
        val consumedCount: Int

        when (pattern.companionType) {
            MosaicTilePattern.CompanionType.QUAD -> {
                val big = MosaicDisplayItem.BigTileItem(
                    mediaItem = buffer[0],
                    tileSize = com.dot.gallery.feature_node.domain.model.BigTileSize.TILE_2x2,
                    dynamicSpan = pattern.bigTileSpan,
                    dynamicAspectRatio = pattern.bigTileAspectRatio,
                )
                val quad = MosaicDisplayItem.QuadTileItem(
                    listOf(buffer[1], buffer[2], buffer[3], buffer[4])
                )
                if (leftSide) { result.add(big); result.add(quad) }
                else { result.add(quad); result.add(big) }
                consumedCount = 5
            }
            MosaicTilePattern.CompanionType.PAIR -> {
                val big = MosaicDisplayItem.BigTileItem(
                    mediaItem = buffer[0],
                    tileSize = com.dot.gallery.feature_node.domain.model.BigTileSize.TILE_3x2,
                    dynamicSpan = pattern.bigTileSpan,
                    dynamicAspectRatio = pattern.bigTileAspectRatio,
                )
                val pair = MosaicDisplayItem.PairTileItem(
                    listOf(buffer[1], buffer[2])
                )
                if (leftSide) { result.add(big); result.add(pair) }
                else { result.add(pair); result.add(big) }
                consumedCount = 3
            }
            MosaicTilePattern.CompanionType.SINGLES -> {
                val big = MosaicDisplayItem.BigTileItem(
                    mediaItem = buffer[0],
                    tileSize = com.dot.gallery.feature_node.domain.model.BigTileSize.TILE_2x1,
                    dynamicSpan = pattern.bigTileSpan,
                    dynamicAspectRatio = pattern.bigTileAspectRatio,
                )
                val singles = (1 until pattern.itemsConsumed).map {
                    MosaicDisplayItem.SingleItem(buffer[it])
                }
                if (leftSide) {
                    result.add(big)
                    result.addAll(singles)
                } else {
                    result.addAll(singles)
                    result.add(big)
                }
                consumedCount = pattern.itemsConsumed
            }
        }

        // Remove consumed items in-place (avoids intermediate list copies)
        buffer.subList(0, consumedCount).clear()

        // Emit complete rows of singles (multiples of columns)
        val completeRowItems = (buffer.size / columns) * columns
        for (i in 0 until completeRowItems) {
            result.add(MosaicDisplayItem.SingleItem(buffer[i]))
        }

        if (force) {
            // End of section: emit partial row too
            for (i in completeRowItems until buffer.size) {
                result.add(MosaicDisplayItem.SingleItem(buffer[i]))
            }
            buffer.clear()
        } else if (completeRowItems > 0) {
            buffer.subList(0, completeRowItems).clear()
        }

        blockIndex++
    }

    for (item in mappedData) {
        when (item) {
            is MediaItem.Header -> {
                emitBlock(force = true)
                blockIndex = 0
                groupRandom = Random(item.key.hashCode().toLong())
                result.add(MosaicDisplayItem.HeaderItem(item))
            }
            is MediaItem.MediaViewItem -> {
                buffer.add(item)
                if (buffer.size >= columns * 2 + 1) emitBlock()
            }
        }
    }
    emitBlock(force = true)
    return result
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun <T : Media> MosaicMediaGrid(
    modifier: Modifier = Modifier,
    gridState: LazyGridState,
    columns: Int = 4,
    mediaState: State<MediaState<T>>,
    metadataState: State<MediaMetadataState>,
    mappedData: List<MediaItem<T>>,
    paddingValues: PaddingValues,
    allowSelection: Boolean,
    canScroll: Boolean,
    allowHeaders: Boolean,
    aboveGridContent: @Composable (() -> Unit)?,
    isScrolling: MutableState<Boolean>,
    emptyContent: @Composable () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onMediaClick: @DisallowComposableCalls (media: T) -> Unit
) {
    LaunchedEffect(gridState.isScrollInProgress) {
        snapshotFlow { gridState.isScrollInProgress }.collectLatest {
            isScrolling.value = it
        }
    }

    val displayItems = remember(mappedData, allowHeaders, columns) {
        val items = if (allowHeaders) mappedData
            else mappedData.fastFilter { it is MediaItem.MediaViewItem<*> }
        buildMosaicDisplayItems(items, columns)
    }

    val bottomContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(paddingValues).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (aboveGridContent != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawWithContent { }
                ) {
                    aboveGridContent()
                }
            }
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AnimatedVisibility(visible = mediaState.value.isLoading, enter = enterAnimation, exit = exitAnimation) {
                    LoadingMedia()
                }
                AnimatedVisibility(visible = mediaState.value.media.isEmpty() && !mediaState.value.isLoading, enter = enterAnimation, exit = exitAnimation) {
                    emptyContent()
                }
                AnimatedVisibility(visible = mediaState.value.error.isNotEmpty()) {
                    Error(errorMessage = mediaState.value.error)
                }
            }
        }
    }

    val scope = rememberCoroutineScope()
    val stringToday = stringResource(id = R.string.header_today)
    val stringYesterday = stringResource(id = R.string.header_yesterday)
    val feedbackManager = rememberFeedbackManager()
    val scrolling by rememberedDerivedState { gridState.isScrollInProgress }
    var canAnimate by rememberSaveable { mutableStateOf(!scrolling) }
    LaunchedEffect(scrolling) {
        if (!canAnimate) delay(500)
        canAnimate = !scrolling
    }
    val selector = LocalMediaSelector.current
    val isSelectionActive by selector.isSelectionActive.collectAsStateWithLifecycle()
    val selectedMedia = selector.selectedMedia.collectAsStateWithLifecycle()

    // Prune stale selection IDs when media list changes (e.g. external file deletion)
    val mediaIds = remember(mediaState.value.media) {
        mediaState.value.media.mapTo(HashSet()) { it.id }
    }
    LaunchedEffect(mediaIds) {
        val currentSelection = selector.selectedMedia.value
        if (currentSelection.isNotEmpty()) {
            val pruned = currentSelection.intersect(mediaIds)
            if (pruned.size != currentSelection.size) {
                selector.rawUpdateSelection(pruned)
            }
        }
    }

    val autoScrollSpeed = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(autoScrollSpeed.floatValue) {
        if (autoScrollSpeed.floatValue != 0f) {
            while (isActive) {
                gridState.scrollBy(autoScrollSpeed.floatValue)
                delay(10)
            }
        }
    }
    val scrollGestureActive = remember { mutableStateOf(false) }

    val orderedGridKeys = remember(displayItems) {
        displayItems.mapNotNull { item ->
            when (item) {
                is MosaicDisplayItem.HeaderItem -> null
                else -> item.key
            }
        }
    }
    val gridKeyToMediaIds = remember(displayItems) {
        buildMap<String, List<Long>> {
            for (item in displayItems) {
                when (item) {
                    is MosaicDisplayItem.HeaderItem -> {}
                    is MosaicDisplayItem.BigTileItem -> put(item.key, listOf(item.mediaItem.media.id))
                    is MosaicDisplayItem.QuadTileItem -> put(item.key, item.mediaItems.map { it.media.id })
                    is MosaicDisplayItem.PairTileItem -> put(item.key, item.mediaItems.map { it.media.id })
                    is MosaicDisplayItem.SingleItem -> put(item.key, listOf(item.mediaItem.media.id))
                }
            }
        }
    }

    val groupAwareUpdateSelection: (Set<Long>) -> Unit = remember(mediaState) {
        { ids ->
            val groups = mediaState.value.mediaGroups
            if (groups.isEmpty()) {
                selector.rawUpdateSelection(ids)
            } else {
                val expanded = mutableSetOf<Long>()
                for (id in ids) {
                    val group = groups[id]
                    if (group != null) {
                        group.forEach { expanded.add(it.id) }
                    } else {
                        expanded.add(id)
                    }
                }
                selector.rawUpdateSelection(expanded)
            }
        }
    }

    BackHandler(
        enabled = isSelectionActive && allowSelection
    ) {
        feedbackManager.vibrate()
        selector.clearSelection()
    }

    Box {
        bottomContent()
        LazyVerticalGrid(
            state = gridState,
            modifier = modifier
                .fillMaxSize()
                .testTag("media_grid_mosaic")
                .mosaicGridDragHandler(
                    lazyGridState = gridState,
                    haptics = LocalHapticFeedback.current,
                    selectedIds = selectedMedia,
                    updateSelectedIds = groupAwareUpdateSelection,
                    autoScrollSpeed = autoScrollSpeed,
                    autoScrollThreshold = with(LocalDensity.current) { 40.dp.toPx() },
                    scrollGestureActive = scrollGestureActive,
                    layoutDirection = LocalLayoutDirection.current,
                    contentPadding = paddingValues,
                    orderedGridKeys = orderedGridKeys,
                    gridKeyToMediaIds = gridKeyToMediaIds
                ),
            columns = GridCells.Fixed(columns),
            contentPadding = paddingValues,
            userScrollEnabled = canScroll,
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            if (aboveGridContent != null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "aboveGrid") {
                    aboveGridContent()
                }
            }

            items(
                count = displayItems.size,
                key = { displayItems[it].key },
                contentType = { idx ->
                    when (displayItems[idx]) {
                        is MosaicDisplayItem.HeaderItem -> "header"
                        is MosaicDisplayItem.BigTileItem -> "big"
                        is MosaicDisplayItem.QuadTileItem -> "quad"
                        is MosaicDisplayItem.PairTileItem -> "pair"
                        is MosaicDisplayItem.SingleItem -> "single"
                    }
                },
                span = { idx ->
                    when (val item = displayItems[idx]) {
                        is MosaicDisplayItem.HeaderItem -> GridItemSpan(maxLineSpan)
                        is MosaicDisplayItem.BigTileItem -> GridItemSpan(item.dynamicSpan)
                        is MosaicDisplayItem.QuadTileItem -> GridItemSpan(2)
                        is MosaicDisplayItem.PairTileItem -> GridItemSpan(1)
                        is MosaicDisplayItem.SingleItem -> GridItemSpan(1)
                    }
                }
            ) { idx ->
                when (val item = displayItems[idx]) {
                    is MosaicDisplayItem.HeaderItem -> {
                        val header = item.header
                        val isChecked = rememberSaveable { mutableStateOf(false) }
                        if (allowSelection) {
                            LaunchedEffect(isSelectionActive) {
                                isChecked.value = isChecked.value && isSelectionActive
                            }
                            LaunchedEffect(selectedMedia.value.size) {
                                withContext(Dispatchers.IO) {
                                    isChecked.value = selectedMedia.value.containsAll(header.data)
                                }
                            }
                        }
                        MediaItemHeader(
                            modifier = Modifier.animateItem(fadeInSpec = null),
                            date = remember(header) {
                                header.text
                                    .replace("Today", stringToday)
                                    .replace("Yesterday", stringYesterday)
                            },
                            showAsBig = remember(header) { header.key.isBigHeaderKey },
                            isChecked = isChecked
                        ) {
                            if (allowSelection) {
                                feedbackManager.vibrate()
                                scope.launch {
                                    isChecked.value = !isChecked.value
                                    val list = mediaState.value.media.map { it.id }
                                        .filter { id -> id in header.data }
                                    if (isChecked.value) selector.addToSelection(list)
                                    else selector.removeFromSelection(list)
                                }
                            }
                        }
                    }
                    is MosaicDisplayItem.BigTileItem -> {
                        val mi = item.mediaItem
                        with(sharedTransitionScope) {
                            MediaImage(
                                modifier = Modifier
                                    .mediaSharedElement(
                                        allowAnimation = canAnimate,
                                        media = mi.media,
                                        animatedVisibilityScope = animatedContentScope
                                    )
                                    .animateItem(fadeInSpec = null, fadeOutSpec = spring()),
                                media = mi.media,
                                stackCount = mi.stackCount,
                                aspectRatio = item.dynamicAspectRatio,
                                canClick = { canScroll },
                                onMediaClick = { onMediaClick(it) },
                                metadataState = metadataState,
                                onItemSelect = {
                                    if (allowSelection) {
                                        feedbackManager.vibrate()
                                        selector.toggleSelectionById(
                                            mediaState = mediaState.value,
                                            mediaId = it.id
                                        )
                                    }
                                }
                            )
                        }
                    }
                    is MosaicDisplayItem.QuadTileItem -> {
                        val items = item.mediaItems
                        Box(modifier = Modifier.aspectRatio(1f)) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(modifier = Modifier.weight(1f)) {
                                    for (i in 0..1) {
                                        if (i > 0) Spacer(Modifier.width(1.dp))
                                        val mi = items.getOrNull(i)
                                        if (mi != null) {
                                            with(sharedTransitionScope) {
                                                MediaImage(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .aspectRatio(1f)
                                                        .mediaSharedElement(
                                                            allowAnimation = canAnimate,
                                                            media = mi.media,
                                                            animatedVisibilityScope = animatedContentScope
                                                        ),
                                                    media = mi.media,
                                                    stackCount = mi.stackCount,
                                                    canClick = { canScroll },
                                                    onMediaClick = { onMediaClick(it) },
                                                    metadataState = metadataState,
                                                    onItemSelect = {
                                                        if (allowSelection) {
                                                            feedbackManager.vibrate()
                                                            selector.toggleSelectionById(
                                                                mediaState = mediaState.value,
                                                                mediaId = it.id
                                                            )
                                                        }
                                                    }
                                                )
                                            }
                                        } else Spacer(Modifier.weight(1f))
                                    }
                                }
                                Spacer(Modifier.height(1.dp))
                                Row(modifier = Modifier.weight(1f)) {
                                    for (i in 2..3) {
                                        if (i > 2) Spacer(Modifier.width(1.dp))
                                        val mi = items.getOrNull(i)
                                        if (mi != null) {
                                            with(sharedTransitionScope) {
                                                MediaImage(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .aspectRatio(1f)
                                                        .mediaSharedElement(
                                                            allowAnimation = canAnimate,
                                                            media = mi.media,
                                                            animatedVisibilityScope = animatedContentScope
                                                        ),
                                                    media = mi.media,
                                                    stackCount = mi.stackCount,
                                                    canClick = { canScroll },
                                                    onMediaClick = { onMediaClick(it) },
                                                    metadataState = metadataState,
                                                    onItemSelect = {
                                                        if (allowSelection) {
                                                            feedbackManager.vibrate()
                                                            selector.toggleSelectionById(
                                                                mediaState = mediaState.value,
                                                                mediaId = it.id
                                                            )
                                                        }
                                                    }
                                                )
                                            }
                                        } else Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                    is MosaicDisplayItem.PairTileItem -> {
                        val pairItems = item.mediaItems
                        Box(modifier = Modifier.aspectRatio(0.5f)) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                for (i in 0..1) {
                                    if (i > 0) Spacer(Modifier.height(1.dp))
                                    val mi = pairItems.getOrNull(i)
                                    if (mi != null) {
                                        with(sharedTransitionScope) {
                                            MediaImage(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .mediaSharedElement(
                                                        allowAnimation = canAnimate,
                                                        media = mi.media,
                                                        animatedVisibilityScope = animatedContentScope
                                                    ),
                                                media = mi.media,
                                                stackCount = mi.stackCount,
                                                canClick = { canScroll },
                                                onMediaClick = { onMediaClick(it) },
                                                metadataState = metadataState,
                                                onItemSelect = {
                                                    if (allowSelection) {
                                                        feedbackManager.vibrate()
                                                        selector.toggleSelectionById(
                                                            mediaState = mediaState.value,
                                                            mediaId = it.id
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    } else Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    is MosaicDisplayItem.SingleItem -> {
                        val mi = item.mediaItem
                        with(sharedTransitionScope) {
                            MediaImage(
                                modifier = Modifier
                                    .mediaSharedElement(
                                        allowAnimation = canAnimate,
                                        media = mi.media,
                                        animatedVisibilityScope = animatedContentScope
                                    )
                                    .animateItem(fadeInSpec = null, fadeOutSpec = spring()),
                                media = mi.media,
                                stackCount = mi.stackCount,
                                canClick = { canScroll },
                                onMediaClick = { onMediaClick(it) },
                                metadataState = metadataState,
                                onItemSelect = {
                                    if (allowSelection) {
                                        feedbackManager.vibrate()
                                        selector.toggleSelectionById(
                                            mediaState = mediaState.value,
                                            mediaId = it.id
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
