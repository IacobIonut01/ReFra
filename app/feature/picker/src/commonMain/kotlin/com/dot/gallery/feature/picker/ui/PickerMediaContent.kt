/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.picker.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.image.thumbnail.LocalThumbnailMotion
import com.dot.gallery.core.image.thumbnail.rememberThumbnailMotionState
import com.dot.gallery.core.presentation.components.MediaImage
import com.dot.gallery.core.presentation.components.MediaItemHeader
import com.dot.gallery.core.presentation.util.mediaSharedElement
import com.dot.gallery.core.presentation.util.photoGridDragHandler
import com.dot.gallery.core.ui.util.rememberedDerivedState
import com.dot.gallery.core.util.rememberFeedbackManager
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.MediaItem
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.isHeaderKey
import com.dot.gallery.feature.picker.ui.components.PickerEmptyState
import com.dot.gallery.feature.picker.ui.components.PickerPlaceholderGrid
import com.dot.gallery.ui.theme.Dimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.picker.generated.resources.Res
import com.dot.gallery.feature.picker.generated.resources.header_today
import com.dot.gallery.feature.picker.generated.resources.header_yesterday
import androidx.compose.foundation.lazy.grid.LazyGridState


/**
 * Drag-to-select media grid for every picker source (timeline, album, private
 * folder, cloud, vault), built from the gallery's own [MediaImage] cells and
 * [MediaItemHeader] day headers. Selection is shared through [LocalMediaSelector];
 * the pick result itself is delivered by the dock in [PickerContent].
 *
 * [header] spans the grid above the first day (source notices); [emptyTitle] and
 * [emptySummary] fill the screen once the source has loaded with nothing in it.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun <T: Media> PickerMediaContent(
    mediaState: StateFlow<MediaState<T>>,
    metadataState: State<MediaMetadataState>,
    allowSelection: Boolean,
    contentPadding: PaddingValues,
    emptyTitle: String,
    emptySummary: String? = null,
    header: (@Composable () -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    val stringToday = stringResource(Res.string.header_today)
    val stringYesterday = stringResource(Res.string.header_yesterday)
    val state by mediaState.collectAsStateWithLifecycle()
    val feedbackManager = rememberFeedbackManager()

    if (state.media.isEmpty()) {
        if (state.isLoading) PickerPlaceholderGrid(contentPadding = contentPadding)
        else PickerEmptyState(
            title = emptyTitle,
            summary = emptySummary,
            modifier = Modifier.padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            ),
        )
        return
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
    // Media keys only, as in the timeline: the drag handler indexes these against media ids, so
    // header keys in the list would shift every drag range by the headers above it.
    val allKeys by rememberedDerivedState {
        state.mappedMedia.filterIsInstance<MediaItem.MediaViewItem<T>>().map { it.key }
    }
    val selector = LocalMediaSelector.current
    val selectedMedia = selector.selectedMedia.collectAsStateWithLifecycle()
    // cheap MOTION tier while the picker grid is flinging.
    val thumbnailMotion = rememberThumbnailMotionState({ gridState.isScrollInProgress })
    val onMediaTap: (Media) -> Unit = {
        feedbackManager.vibrate()
        selector.rawUpdateSelection(
            PickerSelection.tap(selectedMedia.value, it.id, allowSelection)
        )
    }

    CompositionLocalProvider(LocalThumbnailMotion provides thumbnailMotion) {
    LazyVerticalGrid(
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .then(
                // Single pick never drags out a range.
                if (allowSelection) {
                    Modifier.photoGridDragHandler(
                        lazyGridState = gridState,
                        haptics = LocalHapticFeedback.current,
                        selectedIds = selectedMedia,
                        updateSelectedIds = selector::rawUpdateSelection,
                        autoScrollSpeed = autoScrollSpeed,
                        autoScrollThreshold = with(LocalDensity.current) { 40.dp.toPx() },
                        scrollGestureActive = scrollGestureActive,
                        layoutDirection = LocalLayoutDirection.current,
                        contentPadding = contentPadding,
                        allKeys = allKeys
                    )
                } else Modifier
            ),
        columns = GridCells.Adaptive(Dimens.Photo()),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        if (header != null) {
            item(key = "picker_header", span = { GridItemSpan(maxLineSpan) }) { header() }
        }
        items(
            items = state.mappedMedia,
            // Same keys as the timeline: the drag handler resolves media ids from them.
            key = { it.key },
            contentType = { it.key.startsWith("media_") },
            span = { item ->
                GridItemSpan(if (item.key.isHeaderKey) maxLineSpan else 1)
            }
        ) { item ->
            when (item) {
                is MediaItem.Header -> {
                    val isChecked = rememberSaveable { mutableStateOf(false) }
                    if (allowSelection) {
                        LaunchedEffect(selectedMedia.value.size) {
                            // Partial check of media items should not check the header
                            isChecked.value = selectedMedia.value.containsAll(item.data)
                        }
                    }
                    val title = item.text
                        .replace("Today", stringToday)
                        .replace("Yesterday", stringYesterday)
                    MediaItemHeader(
                        date = title,
                        showAsBig = item.key.contains("big"),
                        isChecked = isChecked,
                        onChecked = if (allowSelection) {
                            {
                                feedbackManager.vibrate()
                                val dayIds = state.media.map { it.id }.filter { it in item.data }
                                selector.rawUpdateSelection(
                                    PickerSelection.toggleDay(selectedMedia.value, dayIds)
                                )
                            }
                        } else null
                    )
                }

                is MediaItem.MediaViewItem -> {
                    val sharedElementModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                        with(sharedTransitionScope) {
                            Modifier.mediaSharedElement(
                                media = item.media,
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        }
                    } else Modifier
                    MediaImage(
                        modifier = Modifier.animateItem().then(sharedElementModifier),
                        media = item.media,
                        canClick = { true },
                        metadataState = metadataState,
                        onMediaClick = onMediaTap,
                        onItemSelect = onMediaTap,
                    )
                }
            }
        }
    }
    }
}
