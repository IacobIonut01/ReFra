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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.pinchzoomgrid.PinchZoomGridScope
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
import com.dot.gallery.feature_node.domain.model.isBigHeaderKey
import com.dot.gallery.feature_node.domain.model.isHeaderKey
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun <T : Media> PinchZoomGridScope.MediaGrid(
    modifier: Modifier = Modifier,
    gridState: LazyGridState,
    mediaState: State<MediaState<T>>,
    metadataState: State<MediaMetadataState>,
    mappedData: SnapshotStateList<MediaItem<T>>,
    paddingValues: PaddingValues,
    allowSelection: Boolean,
    canScroll: Boolean,
    allowHeaders: Boolean,
    aboveGridContent: @Composable() (() -> Unit)?,
    isScrolling: MutableState<Boolean>,
    emptyContent: @Composable () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
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
    val bottomContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(
                visible = mediaState.value.isLoading,
                enter = enterAnimation,
                exit = exitAnimation
            ) {
                LoadingMedia()
            }
            AnimatedVisibility(
                visible = mediaState.value.media.isEmpty() && !mediaState.value.isLoading,
                enter = enterAnimation,
                exit = exitAnimation
            ) {
                emptyContent()
            }
            AnimatedVisibility(visible = mediaState.value.error.isNotEmpty()) {
                Error(errorMessage = mediaState.value.error)
            }
        }
    }

    Box {
        bottomContent()
        if (allowHeaders) {
            MediaGridContentWithHeaders(
                modifier = modifier,
                mediaState = mediaState,
                metadataState = metadataState,
                mappedData = mappedData,
                paddingValues = paddingValues,
                allowSelection = allowSelection,
                canScroll = canScroll,
                onMediaClick = onMediaClick,
                topContent = topContent,
                sharedTransitionScope = sharedTransitionScope,
                animatedContentScope = animatedContentScope
            )
        } else {
            MediaGridContent(
                modifier = modifier,
                mediaState = mediaState,
                metadataState = metadataState,
                paddingValues = paddingValues,
                allowSelection = allowSelection,
                canScroll = canScroll,
                onMediaClick = onMediaClick,
                topContent = topContent,
                sharedTransitionScope = sharedTransitionScope,
                animatedContentScope = animatedContentScope
            )
        }
    }

}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun <T : Media> PinchZoomGridScope.MediaGridContentWithHeaders(
    modifier: Modifier = Modifier,
    mediaState: State<MediaState<T>>,
    metadataState: State<MediaMetadataState>,
    mappedData: SnapshotStateList<MediaItem<T>>,
    paddingValues: PaddingValues,
    allowSelection: Boolean,
    canScroll: Boolean,
    onMediaClick: @DisallowComposableCalls (media: T) -> Unit,
    topContent: LazyGridScope.() -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    val scope = rememberCoroutineScope()
    val stringToday = stringResource(id = R.string.header_today)
    val stringYesterday = stringResource(id = R.string.header_yesterday)
    val feedbackManager = rememberFeedbackManager()
    val headers by rememberedDerivedState(mediaState.value) {
        mediaState.value.headers.toMutableStateList()
    }
    TimelineScroller(
        modifier = modifier
            .padding(paddingValues)
            .padding(top = 32.dp)
            .padding(vertical = 32.dp),
        mappedData = mappedData,
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
        val selector = LocalMediaSelector.current
        val isSelectionActive by selector.isSelectionActive.collectAsStateWithLifecycle()
        val selectedMedia = selector.selectedMedia.collectAsStateWithLifecycle()

        LazyVerticalGrid(
            state = gridState,
            modifier = modifier
                .fillMaxSize()
                .testTag("media_grid")
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
                            .animateItem(
                                fadeInSpec = null
                            )
                            .pinchItem(key = it.key),
                        date = remember(it) {
                            it.text
                                .replace("Today", stringToday)
                                .replace("Yesterday", stringYesterday)
                        },
                        showAsBig = remember(it) { it.key.isBigHeaderKey },
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
                    with(sharedTransitionScope) {
                        val metadata by rememberedDerivedState(metadataState.value, it.media) {
                            metadataState.value.metadata.fastFirstOrNull { mtd -> mtd.mediaId == it.media.id }
                        }
                        MediaImage(
                            modifier = Modifier
                                .mediaSharedElement(
                                    allowAnimation = canAnimate,
                                    media = it.media,
                                    animatedVisibilityScope = animatedContentScope
                                )
                                .animateItem(
                                    fadeInSpec = null,
                                    fadeOutSpec = spring()
                                )
                                .pinchItem(key = it.key),
                            media = it.media,
                            metadata = { metadata },
                            canClick = { canScroll },
                            onMediaClick = { onMediaClick(it) },
                            onItemSelect = {
                                if (allowSelection) {
                                    feedbackManager.vibrate()
                                    selector.toggleSelection(
                                        mediaState = mediaState.value,
                                        index = mediaState.value.media.indexOf(it)
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun <T : Media> PinchZoomGridScope.MediaGridContent(
    modifier: Modifier = Modifier,
    mediaState: State<MediaState<T>>,
    metadataState: State<MediaMetadataState>,
    paddingValues: PaddingValues,
    allowSelection: Boolean,
    canScroll: Boolean,
    onMediaClick: @DisallowComposableCalls (media: T) -> Unit,
    topContent: LazyGridScope.() -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
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
    val selector = LocalMediaSelector.current
    val selectedMedia = selector.selectedMedia.collectAsStateWithLifecycle()

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
            with(sharedTransitionScope) {
                val metadata = remember(metadataState.value, media) {
                    mutableStateOf(metadataState.value.metadata.firstOrNull { mtd -> mtd.mediaId == media.id })
                }
                MediaImage(
                    modifier = Modifier
                        .mediaSharedElement(
                            allowAnimation = canAnimate,
                            media = media,
                            animatedVisibilityScope = animatedContentScope
                        )
                        .animateItem(
                            fadeInSpec = null,
                            fadeOutSpec = spring()
                        )
                        .pinchItem(key = media.key),
                    media = media,
                    metadata = { metadata.value },
                    canClick = { canScroll },
                    onMediaClick = { onMediaClick(it) },
                    onItemSelect = {
                        if (allowSelection) {
                            val index = items.indexOf(it)
                            feedbackManager.vibrate()
                            selector.toggleSelection(
                                mediaState = mediaState.value,
                                index = index
                            )
                        }
                    }
                )
            }
        }
    }
}