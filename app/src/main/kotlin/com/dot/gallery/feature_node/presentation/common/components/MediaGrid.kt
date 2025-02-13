package com.dot.gallery.feature_node.presentation.common.components

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dokar.pinchzoomgrid.PinchZoomGridScope
import com.dot.gallery.R
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.presentation.components.Error
import com.dot.gallery.core.presentation.components.LoadingMedia
import com.dot.gallery.core.presentation.components.MediaItemHeader
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaItem
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.isBigHeaderKey
import com.dot.gallery.feature_node.domain.model.isHeaderKey
import com.dot.gallery.feature_node.domain.util.isImage
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.util.mediaSharedElement
import com.dot.gallery.feature_node.presentation.util.rememberFeedbackManager
import com.dot.gallery.feature_node.presentation.util.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun <T: Media> PinchZoomGridScope.MediaGrid(
    gridState: LazyGridState,
    mediaState: State<MediaState<T>>,
    mappedData: SnapshotStateList<MediaItem<T>>,
    paddingValues: PaddingValues,
    allowSelection: Boolean,
    selectionState: MutableState<Boolean>,
    selectedMedia: SnapshotStateList<T>,
    toggleSelection: @DisallowComposableCalls (Int) -> Unit,
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
    val bottomContent: LazyGridScope.() -> Unit = remember {
        {
            item(
                span = { GridItemSpan(maxLineSpan) },
                key = "loading"
            ) {
                AnimatedVisibility(
                    visible = mediaState.value.isLoading,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    LoadingMedia()
                }
            }

            item(
                span = { GridItemSpan(maxLineSpan) },
                key = "empty"
            ) {
                AnimatedVisibility(
                    visible = mediaState.value.media.isEmpty() && !mediaState.value.isLoading,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    emptyContent()
                }
            }
            item(
                span = { GridItemSpan(maxLineSpan) },
                key = "error"
            ) {
                AnimatedVisibility(visible = mediaState.value.error.isNotEmpty()) {
                    Error(errorMessage = mediaState.value.error)
                }
            }
        }
    }

    AnimatedVisibility(
        visible = allowHeaders
    ) {
        MediaGridContentWithHeaders(
            mediaState = mediaState,
            mappedData = mappedData,
            paddingValues = paddingValues,
            allowSelection = allowSelection,
            selectionState = selectionState,
            selectedMedia = selectedMedia,
            toggleSelection = toggleSelection,
            canScroll = canScroll,
            onMediaClick = onMediaClick,
            topContent = topContent,
            bottomContent = bottomContent,
            sharedTransitionScope = sharedTransitionScope,
            animatedContentScope = animatedContentScope
        )
    }

    AnimatedVisibility(
        visible = !allowHeaders
    ) {
        MediaGridContent(
            mediaState = mediaState,
            paddingValues = paddingValues,
            allowSelection = allowSelection,
            selectionState = selectionState,
            selectedMedia = selectedMedia,
            toggleSelection = toggleSelection,
            canScroll = canScroll,
            onMediaClick = onMediaClick,
            topContent = topContent,
            bottomContent = bottomContent,
            sharedTransitionScope = sharedTransitionScope,
            animatedContentScope = animatedContentScope
        )
    }

}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun <T: Media> PinchZoomGridScope.MediaGridContentWithHeaders(
    mediaState: State<MediaState<T>>,
    mappedData: SnapshotStateList<MediaItem<T>>,
    paddingValues: PaddingValues,
    allowSelection: Boolean,
    selectionState: MutableState<Boolean>,
    selectedMedia: SnapshotStateList<T>,
    toggleSelection: @DisallowComposableCalls (Int) -> Unit,
    canScroll: Boolean,
    onMediaClick: @DisallowComposableCalls (media: T) -> Unit,
    topContent: LazyGridScope.() -> Unit,
    bottomContent: LazyGridScope.() -> Unit,
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
        modifier = Modifier
            .padding(paddingValues)
            .padding(top = 32.dp)
            .padding(vertical = 32.dp),
        mappedData = mappedData,
        headers = headers,
        state = gridState,
    ) {
        LazyVerticalGrid(
            state = gridState,
            modifier = Modifier.fillMaxSize().testTag("media_grid"),
            columns = gridCells,
            contentPadding = paddingValues,
            userScrollEnabled = canScroll,
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            topContent()

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
                        LaunchedEffect(selectionState.value) {
                            // Uncheck if selectionState is set to false
                            isChecked.value = isChecked.value && selectionState.value
                        }
                        LaunchedEffect(selectedMedia.size) {
                            withContext(Dispatchers.IO) {
                                // Partial check of media items should not check the header
                                isChecked.value = selectedMedia.map { it.id }.containsAll(it.data)
                            }
                        }
                    }
                    MediaItemHeader(
                        modifier = Modifier
                            .animateItem(
                                fadeInSpec = null
                            )
                            .pinchItem(key = it.key),
                        date = remember {
                            it.text
                                .replace("Today", stringToday)
                                .replace("Yesterday", stringYesterday)
                        },
                        showAsBig = remember { it.key.isBigHeaderKey },
                        isCheckVisible = selectionState,
                        isChecked = isChecked
                    ) {
                        if (allowSelection) {
                            feedbackManager.vibrate()
                            scope.launch {
                                isChecked.value = !isChecked.value
                                if (isChecked.value) {
                                    val toAdd = it.data.toMutableList().apply {
                                        // Avoid media from being added twice to selection
                                        removeIf {
                                            selectedMedia.map { media -> media.id }.contains(it)
                                        }
                                    }
                                    selectedMedia.addAll(mediaState.value.media.filter {
                                        toAdd.contains(
                                            it.id
                                        )
                                    })
                                } else selectedMedia.removeAll { media -> it.data.contains(media.id) }
                                selectionState.update(selectedMedia.isNotEmpty())
                            }
                        }
                    }
                } else if (it is MediaItem.MediaViewItem) {
                    with(sharedTransitionScope) {
                        MediaImage(
                            modifier = Modifier
                                .mediaSharedElement(
                                    media = it.media,
                                    animatedVisibilityScope = animatedContentScope
                                )
                                .animateItem(
                                    fadeInSpec = null
                                )
                                .pinchItem(key = it.key),
                            media = it.media,
                            selectionState = selectionState,
                            selectedMedia = selectedMedia,
                            canClick = canScroll,
                            onItemClick = {
                                if (selectionState.value && allowSelection) {
                                    feedbackManager.vibrate()
                                    toggleSelection(mediaState.value.media.indexOf(it))
                                } else onMediaClick(it)
                            }
                        ) {
                            if (allowSelection) {
                                feedbackManager.vibrate()
                                toggleSelection(mediaState.value.media.indexOf(it))
                            }
                        }
                    }
                }
            }


            bottomContent()
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun <T: Media> PinchZoomGridScope.MediaGridContent(
    mediaState: State<MediaState<T>>,
    paddingValues: PaddingValues,
    allowSelection: Boolean,
    selectionState: MutableState<Boolean>,
    selectedMedia: SnapshotStateList<T>,
    toggleSelection: @DisallowComposableCalls (Int) -> Unit,
    canScroll: Boolean,
    onMediaClick: @DisallowComposableCalls (media: T) -> Unit,
    topContent: LazyGridScope.() -> Unit,
    bottomContent: LazyGridScope.() -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    val feedbackManager = rememberFeedbackManager()
    LazyVerticalGrid(
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        columns = gridCells,
        contentPadding = paddingValues,
        userScrollEnabled = canScroll,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        topContent()

        itemsIndexed(
            items = mediaState.value.media,
            key = { _, item -> item.toString() },
            contentType = { _, item -> item.isImage }
        ) { index, media ->
            with(sharedTransitionScope) {
                MediaImage(
                    modifier = Modifier
                        .mediaSharedElement(
                            media = media,
                            animatedVisibilityScope = animatedContentScope
                        )
                        .animateItem(
                            fadeInSpec = null
                        )
                        .pinchItem(key = media.toString()),
                    media = media,
                    selectionState = selectionState,
                    selectedMedia = selectedMedia,
                    canClick = canScroll,
                    onItemClick = {
                        if (selectionState.value && allowSelection) {
                            feedbackManager.vibrate()
                            toggleSelection(index)
                        } else onMediaClick(it)
                    },
                    onItemLongClick = {
                        if (allowSelection) {
                            feedbackManager.vibrate()
                            toggleSelection(index)
                        }
                    }
                )
            }
        }

        bottomContent()
    }
}