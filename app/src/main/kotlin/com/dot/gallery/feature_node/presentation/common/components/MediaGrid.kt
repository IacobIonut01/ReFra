package com.dot.gallery.feature_node.presentation.common.components

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.image.thumbnail.LocalThumbnailMotion
import com.dot.gallery.core.image.thumbnail.rememberThumbnailMotionState
import com.dot.gallery.core.Settings.Misc.rememberFavoriteIconPosition
import com.dot.gallery.core.presentation.components.Error
import com.dot.gallery.core.presentation.components.LoadingMedia
import com.dot.gallery.core.presentation.components.MediaItemHeader
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaItem
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.isBigHeaderKey
import com.dot.gallery.feature_node.domain.model.isHeaderKey
import com.dot.gallery.feature_node.presentation.mediaview.LocalMediaViewerOverlayController
import com.dot.gallery.feature_node.presentation.mediaview.LocalViewerDismissBridge
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.util.mediaSharedElement
import com.dot.gallery.feature_node.presentation.util.photoGridDragHandler
import com.dot.gallery.feature_node.presentation.util.rememberFeedbackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class MediaContentState {
    ERROR,
    LOADING,
    EMPTY,
    CONTENT,
}

internal fun mediaContentState(
    isLoading: Boolean,
    error: String,
    isEmpty: Boolean,
): MediaContentState = when {
    error.isNotEmpty() -> MediaContentState.ERROR
    isLoading -> MediaContentState.LOADING
    isEmpty -> MediaContentState.EMPTY
    else -> MediaContentState.CONTENT
}

/**
 * Scrolls the least distance needed to bring [index] into the viewport: an item above
 * it lands at the top edge, an item below lands at the bottom edge. Used to keep the
 * media-viewer return-flight target composed without jumping the grid to the top of
 * the screen behind the open viewer (issue #960).
 */
internal suspend fun LazyGridState.scrollMinimallyToItem(index: Int) {
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return
    val first = visible.first().index
    val last = visible.last().index
    when {
        index in first..last -> Unit
        index < first -> scrollToItem(index)
        else -> {
            // A reference row height is only an estimate (mosaic tiles vary); if the
            // item is taller it still composes, which is all the return flight needs.
            val rowHeight = visible.minOf { it.size.height }
            scrollToItem(index, scrollOffset = -(info.viewportSize.height - rowHeight))
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun <T : Media> GridPinchZoomScope.MediaGrid(
    modifier: Modifier = Modifier,
    gridState: LazyGridState,
    mediaState: State<MediaState<T>>,
    metadataState: State<MediaMetadataState>,
    mappedData: List<MediaItem<T>>,
    paddingValues: PaddingValues,
    allowSelection: Boolean,
    canScroll: Boolean,
    allowHeaders: Boolean,
    bigHeaders: Boolean = false,
    aboveGridContent: @Composable() (() -> Unit)?,
    isScrolling: MutableState<Boolean>,
    emptyContent: @Composable () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    allowSharedElements: Boolean = true,
    onRetry: (() -> Unit)? = null,
    onMediaClick: @DisallowComposableCalls (media: T) -> Unit
) {
    LaunchedEffect(gridState.isScrollInProgress) {
        snapshotFlow {
            gridState.isScrollInProgress
        }.collectLatest {
            isScrolling.value = it
        }
    }

    val topContent: LazyGridScope.() -> Unit = remember(aboveGridContent) {
        {
            if (aboveGridContent != null) {
                item(
                    span = { GridItemSpan(maxLineSpan) },
                    key = "aboveGrid"
                ) {
                    aboveGridContent.invoke()
                }
            }
        }
    }
    val contentState = mediaContentState(
        isLoading = mediaState.value.isLoading,
        error = mediaState.value.error,
        isEmpty = mediaState.value.media.isEmpty(),
    )
    if (contentState != MediaContentState.CONTENT) {
        Column(
            modifier = Modifier.padding(paddingValues).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (contentState) {
                    MediaContentState.ERROR -> Error(
                        errorMessage = mediaState.value.error,
                        onRetry = onRetry,
                    )
                    MediaContentState.LOADING -> LoadingMedia()
                    MediaContentState.EMPTY -> emptyContent()
                    MediaContentState.CONTENT -> Unit
                }
            }
        }
        return
    }

    if (allowHeaders) {
        MediaGridContentWithHeaders(
            modifier = modifier,
            mediaState = mediaState,
            mappedData = mappedData,
            paddingValues = paddingValues,
            allowSelection = allowSelection,
            canScroll = canScroll,
            bigHeaders = bigHeaders,
            onMediaClick = onMediaClick,
            topContent = topContent,
            leadingItemCount = if (aboveGridContent != null) 1 else 0,
            sharedTransitionScope = sharedTransitionScope,
            animatedContentScope = animatedContentScope,
            allowSharedElements = allowSharedElements,
            metadataState = metadataState
        )
    } else {
        MediaGridContent(
            modifier = modifier,
            mediaState = mediaState,
            paddingValues = paddingValues,
            allowSelection = allowSelection,
            canScroll = canScroll,
            onMediaClick = onMediaClick,
            topContent = topContent,
            leadingItemCount = if (aboveGridContent != null) 1 else 0,
            sharedTransitionScope = sharedTransitionScope,
            animatedContentScope = animatedContentScope,
            allowSharedElements = allowSharedElements,
            metadataState = metadataState
        )
    }

}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun <T : Media> GridPinchZoomScope.MediaGridContentWithHeaders(
    modifier: Modifier = Modifier,
    mediaState: State<MediaState<T>>,
    metadataState: State<MediaMetadataState>,
    mappedData: List<MediaItem<T>>,
    paddingValues: PaddingValues,
    allowSelection: Boolean,
    canScroll: Boolean,
    bigHeaders: Boolean,
    onMediaClick: @DisallowComposableCalls (media: T) -> Unit,
    topContent: LazyGridScope.() -> Unit,
    leadingItemCount: Int,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    allowSharedElements: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val stringToday = stringResource(id = R.string.header_today)
    val stringYesterday = stringResource(id = R.string.header_yesterday)
    val feedbackManager = rememberFeedbackManager()
    val headers by rememberedDerivedState(mediaState.value) {
        mediaState.value.headers
    }
    TimelineScroller(
        modifier = modifier
            .padding(paddingValues)
            .padding(top = 32.dp)
            .padding(vertical = 32.dp),
        segments = rememberMonthSegments(
            mappedData = mappedData,
            leadingItemCount = leadingItemCount,
            dateSource = mediaState.value.dateSource
        ),
        headers = headers,
        state = gridState,
    ) {
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
        val allKeys by rememberedDerivedState {
            mappedData.fastFilter { it is MediaItem.MediaViewItem<T> }.fastMap { it.key }
        }
        val isScrolling by rememberedDerivedState {
            gridState.isScrollInProgress
        }
        var canAnimate by rememberSaveable {
            mutableStateOf(!isScrolling)
        }
        LaunchedEffect(isScrolling) {
            if (!canAnimate) delay(500)
            canAnimate = !isScrolling
        }
        val mediaViewerOverlay = LocalMediaViewerOverlayController.current
        val dismissBridge = LocalViewerDismissBridge.current
        val returnMediaId = mediaViewerOverlay?.currentMediaId ?: -1L
        LaunchedEffect(
            mediaViewerOverlay?.visible,
            dismissBridge != null,
            returnMediaId,
            mappedData,
            mediaState.value.mediaGroups,
        ) {
            // Prepositioning exists only so a committed dismiss flight finds its target
            // cell composed — skip it entirely where no overlay host can run the flight.
            if (mediaViewerOverlay?.visible == true && dismissBridge != null && returnMediaId != -1L) {
                val localIndex = mappedData.indexOfFirst { item ->
                    item is MediaItem.MediaViewItem<T> && (
                        item.media.id == returnMediaId ||
                            mediaState.value.mediaGroups[item.media.id]
                                ?.any { it.id == returnMediaId } == true
                        )
                }
                val targetIndex = leadingItemCount + localIndex
                if (localIndex >= 0 && gridState.layoutInfo.visibleItemsInfo.none {
                        it.index == targetIndex
                    }
                ) {
                    gridState.scrollMinimallyToItem(targetIndex)
                }
            }
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

        val favoriteIconPosition by rememberFavoriteIconPosition()
        val cloudSyncStates by LocalMediaDistributor.current.cloudSyncStates.collectAsStateWithLifecycle()
        val cloudBackedUpIds by rememberedDerivedState(mediaState.value) { mediaState.value.cloudBackups.keys }
        val cellState = remember(isSelectionActive, selectedMedia.value, favoriteIconPosition, cloudSyncStates, cloudBackedUpIds) {
            MediaCellState(isSelectionActive, selectedMedia.value, favoriteIconPosition, cloudSyncStates, cloudBackedUpIds)
        }
        // Phase 3 (#1076): publish debounced scroll-motion so cells load the cheap MOTION tier
        // during a fling and refine only once the grid settles.
        val thumbnailMotion = rememberThumbnailMotionState({ gridState.isScrollInProgress })
        key(gridCells) {
        CompositionLocalProvider(
            LocalMediaCellState provides cellState,
            LocalThumbnailMotion provides thumbnailMotion,
        ) {
        LazyVerticalGrid(
            state = gridState,
            modifier = modifier
                .fillMaxSize()
                .testTag("media_grid")
                .photoGridDragHandler(
                    lazyGridState = gridState,
                    haptics = LocalHapticFeedback.current,
                    selectedIds = selectedMedia,
                    updateSelectedIds = groupAwareUpdateSelection,
                    autoScrollSpeed = autoScrollSpeed,
                    autoScrollThreshold = with(LocalDensity.current) { 40.dp.toPx() },
                    scrollGestureActive = scrollGestureActive,
                    layoutDirection = LocalLayoutDirection.current,
                    contentPadding = paddingValues,
                    allKeys = allKeys
                ),
            columns = gridCells,
            contentPadding = paddingValues,
            userScrollEnabled = canScroll,
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            topContent()

            //bottomContent()
            items(
                items = mappedData,
                key = { item -> item.key },
                contentType = { item -> item.key.startsWith("media_") },
                span = { item ->
                    GridItemSpan(if (item.key.isHeaderKey) maxLineSpan else 1)
                }
            ) { it ->
                if (it is MediaItem.Header) {
                    val isChecked = rememberSaveable { mutableStateOf(false) }
                    if (allowSelection) {
                        LaunchedEffect(isSelectionActive) {
                            // Uncheck if selectionState is set to false
                            isChecked.value = isChecked.value && isSelectionActive
                        }
                        LaunchedEffect(selectedMedia.value.size) {
                            withContext(Dispatchers.IO) {
                                // Partial check of media items should not check the header
                                isChecked.value = selectedMedia.value.containsAll(it.data)
                            }
                        }
                    }
                    MediaItemHeader(
                        modifier = Modifier
                            .then(
                                if (canScroll) Modifier.animateItem(fadeInSpec = null)
                                else Modifier
                            )
                            .pinchItem(key = it.key),
                        date = remember(it) {
                            it.text
                                .replace("Today", stringToday)
                                .replace("Yesterday", stringYesterday)
                        },
                        showAsBig = remember(it, bigHeaders) { it.key.isBigHeaderKey || bigHeaders },
                        bigHeaderOnly = bigHeaders,
                        isChecked = isChecked
                    ) {
                        if (allowSelection) {
                            feedbackManager.vibrate()
                            scope.launch {
                                isChecked.value = !isChecked.value
                                val list = mediaState.value.media.map { it.id }
                                    .filter { id -> id in it.data }
                                if (isChecked.value) {
                                    selector.addToSelection(list)
                                } else selector.removeFromSelection(list)
                            }
                        }
                    }
                } else if (it is MediaItem.MediaViewItem) {
                    val sharedElementModifier = if (allowSharedElements) {
                        with(sharedTransitionScope) {
                            Modifier.mediaSharedElement(
                                allowAnimation = canAnimate,
                                media = it.media,
                                animatedVisibilityScope = animatedContentScope
                            )
                        }
                    } else Modifier
                    MediaImage(
                        modifier = sharedElementModifier
                            .then(
                                if (canScroll) Modifier.animateItem(
                                    fadeInSpec = null,
                                    fadeOutSpec = if (isScrolling) null else spring()
                                ) else Modifier
                            )
                            .pinchItem(key = it.key),
                        media = it.media,
                        stackCount = it.stackCount,
                        isCloudGroup = it.isCloudGroup,
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun <T : Media> GridPinchZoomScope.MediaGridContent(
    modifier: Modifier = Modifier,
    mediaState: State<MediaState<T>>,
    metadataState: State<MediaMetadataState>,
    paddingValues: PaddingValues,
    allowSelection: Boolean,
    canScroll: Boolean,
    onMediaClick: @DisallowComposableCalls (media: T) -> Unit,
    topContent: LazyGridScope.() -> Unit,
    leadingItemCount: Int,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    allowSharedElements: Boolean = true,
) {
    val feedbackManager = rememberFeedbackManager()
    val items by rememberedDerivedState(mediaState.value) {
        mediaState.value.media
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
    val allKeys by rememberedDerivedState {
        mediaState.value.media.map { it.key }
    }
    val isScrolling by rememberedDerivedState {
        gridState.isScrollInProgress
    }
    var canAnimate by rememberSaveable {
        mutableStateOf(!isScrolling)
    }
    LaunchedEffect(isScrolling) {
        if (!canAnimate) delay(500)
        canAnimate = !isScrolling
    }
    val mediaViewerOverlay = LocalMediaViewerOverlayController.current
    val dismissBridge = LocalViewerDismissBridge.current
    val returnMediaId = mediaViewerOverlay?.currentMediaId ?: -1L
    LaunchedEffect(
        mediaViewerOverlay?.visible,
        dismissBridge != null,
        returnMediaId,
        items,
        mediaState.value.mediaGroups,
    ) {
        // Prepositioning exists only so a committed dismiss flight finds its target
        // cell composed — skip it entirely where no overlay host can run the flight.
        if (mediaViewerOverlay?.visible == true && dismissBridge != null && returnMediaId != -1L) {
            val localIndex = items.indexOfFirst { media ->
                media.id == returnMediaId ||
                    mediaState.value.mediaGroups[media.id]
                        ?.any { it.id == returnMediaId } == true
            }
            val targetIndex = leadingItemCount + localIndex
            if (localIndex >= 0 && gridState.layoutInfo.visibleItemsInfo.none {
                    it.index == targetIndex
                }
            ) {
                gridState.scrollMinimallyToItem(targetIndex)
            }
        }
    }
    val selector = LocalMediaSelector.current
    val selectionActive by selector.isSelectionActive.collectAsStateWithLifecycle()
    val selectedMedia = selector.selectedMedia.collectAsStateWithLifecycle()
    val favoriteIconPosition by rememberFavoriteIconPosition()
    val cloudSyncStates by LocalMediaDistributor.current.cloudSyncStates.collectAsStateWithLifecycle()
    val cellState = remember(selectionActive, selectedMedia.value, favoriteIconPosition, cloudSyncStates) {
        MediaCellState(selectionActive, selectedMedia.value, favoriteIconPosition, cloudSyncStates)
    }
    // Phase 3 (#1076): publish debounced scroll-motion for the header-less media grid too.
    val thumbnailMotion = rememberThumbnailMotionState({ gridState.isScrollInProgress })

    key(gridCells) {
    CompositionLocalProvider(
        LocalMediaCellState provides cellState,
        LocalThumbnailMotion provides thumbnailMotion,
    ) {
    LazyVerticalGrid(
        state = gridState,
        modifier = modifier
            .fillMaxSize()
            .photoGridDragHandler(
                lazyGridState = gridState,
                haptics = LocalHapticFeedback.current,
                selectedIds = selectedMedia,
                updateSelectedIds = selector::rawUpdateSelection,
                autoScrollSpeed = autoScrollSpeed,
                autoScrollThreshold = with(LocalDensity.current) { 40.dp.toPx() },
                scrollGestureActive = scrollGestureActive,
                layoutDirection = LocalLayoutDirection.current,
                contentPadding = paddingValues,
                allKeys = allKeys
            ),
        columns = gridCells,
        contentPadding = paddingValues,
        userScrollEnabled = canScroll,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        topContent()

        items(
            items = items,
            key = { item -> item.key },
            contentType = { item -> item.mimeType }
        ) { media ->
            val sharedElementModifier = if (allowSharedElements) {
                with(sharedTransitionScope) {
                    Modifier.mediaSharedElement(
                        allowAnimation = canAnimate,
                        media = media,
                        animatedVisibilityScope = animatedContentScope
                    )
                }
            } else Modifier
            MediaImage(
                modifier = sharedElementModifier
                    .then(
                        if (canScroll) Modifier.animateItem(
                            fadeInSpec = null,
                            fadeOutSpec = if (isScrolling) null else spring()
                        ) else Modifier
                    )
                    .pinchItem(key = media.key),
                media = media,
                metadataState = metadataState,
                canClick = { canScroll },
                onMediaClick = { onMediaClick(it) },
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