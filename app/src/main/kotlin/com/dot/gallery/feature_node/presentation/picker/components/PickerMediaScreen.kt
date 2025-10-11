/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.feature_node.presentation.picker.components

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.presentation.components.MediaItemHeader
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaItem
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.isHeaderKey
import com.dot.gallery.feature_node.presentation.common.components.MediaImage
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.util.photoGridDragHandler
import com.dot.gallery.feature_node.presentation.util.rememberFeedbackManager
import com.dot.gallery.ui.theme.Dimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun <T: Media> PickerMediaScreen(
    mediaState: StateFlow<MediaState<T>>,
    metadataState: State<MediaMetadataState>,
    allowSelection: Boolean,
) {
    val scope = rememberCoroutineScope()
    val stringToday = stringResource(id = R.string.header_today)
    val stringYesterday = stringResource(id = R.string.header_yesterday)
    val state by mediaState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val feedbackManager = rememberFeedbackManager()

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
        state.mappedMedia.map { it.key }
    }
    val selector = LocalMediaSelector.current
    val selectedMedia = selector.selectedMedia.collectAsStateWithLifecycle()

    LazyVerticalGrid(
        state = gridState,
        modifier = Modifier
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
                contentPadding = PaddingValues(),
                allKeys = allKeys
            ),
        columns = GridCells.Adaptive(Dimens.Photo()),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        items(
            items = state.mappedMedia,
            key = { if (it is MediaItem.MediaViewItem) it.media.toString() else it.key },
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
                        isChecked = isChecked
                    ) {
                        if (allowSelection) {
                            feedbackManager.vibrate()
                            scope.launch {
                                isChecked.value = !isChecked.value
                                val list = mediaState.value.media.map { it.id }.filter { it in item.data }
                                if (isChecked.value) {
                                    selector.addToSelection(list)
                                } else selector.removeFromSelection(list)
                            }
                        }
                    }
                }

                is MediaItem.MediaViewItem -> {
                    MediaImage(
                        modifier = Modifier.animateItem(),
                        media = item.media,
                        canClick = { true },
                        metadataState = metadataState,
                        onMediaClick = {
                            feedbackManager.vibrate()
                            val id = it.id
                            if (allowSelection) {
                                if (selectedMedia.value.contains(id)) selector.removeFromSelection(listOf(id))
                                else selector.addToSelection(listOf(id))
                            } else {
                                if (selectedMedia.value.isEmpty()) {
                                    selector.addToSelection(listOf(id))
                                } else {
                                    selector.removeFromSelection(listOf(id))
                                }
                            }
                        },
                        onItemSelect = {
                            feedbackManager.vibrate()
                            val id = it.id
                            if (allowSelection) {
                                if (selectedMedia.value.contains(id))  selector.removeFromSelection(listOf(id))
                                else selector.addToSelection(listOf(id))
                            } else {
                                if (selectedMedia.value.isEmpty()) {
                                    selector.addToSelection(listOf(id))
                                } else {
                                    selector.removeFromSelection(listOf(id))
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}