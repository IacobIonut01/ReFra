/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.common.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.pinchzoomgrid.PinchZoomGridScope
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.Settings.Misc.rememberAutoHideSearchBar
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.isHeaderKey
import com.dot.gallery.feature_node.domain.model.isIgnoredKey
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.util.roundDpToPx
import com.dot.gallery.feature_node.presentation.util.roundSpToPx
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun <T : Media> PinchZoomGridScope.MediaGridView(
    modifier: Modifier = Modifier,
    mediaState: State<MediaState<T>>,
    metadataState: State<MediaMetadataState>,
    paddingValues: PaddingValues = PaddingValues(0.dp),
    searchBarPaddingTop: Dp = 0.dp,
    showSearchBar: Boolean = remember { false },
    allowSelection: Boolean = remember { false },
    canScroll: Boolean = true,
    allowHeaders: Boolean = true,
    enableStickyHeaders: Boolean = false,
    showMonthlyHeader: Boolean = false,
    aboveGridContent: @Composable (() -> Unit)? = null,
    isScrolling: MutableState<Boolean>,
    emptyContent: @Composable () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onMediaClick: @DisallowComposableCalls (media: T) -> Unit = {},
) {
    val mappedData by rememberedDerivedState(mediaState, showMonthlyHeader) {
        (if (showMonthlyHeader) mediaState.value.mappedMediaWithMonthly
        else mediaState.value.mappedMedia).toMutableStateList()
    }
    val selector = LocalMediaSelector.current
    val isSelectionActive by selector.isSelectionActive.collectAsStateWithLifecycle()

    BackHandler(
        enabled = isSelectionActive && allowSelection,
        onBack = selector::clearSelection
    )

    /**
     * Workaround for a small bug
     * That shows the grid at the bottom after content is loaded
     */
    var hasScrolledToTop by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(gridState, mediaState.value) {
        snapshotFlow { mediaState.value.isLoading }
            .collectLatest { isLoading ->
                if (!isLoading  && !hasScrolledToTop) {
                    hasScrolledToTop = true
                    gridState.scrollToItem(0)
                }
            }
    }

    if (enableStickyHeaders) {
        val headers by rememberedDerivedState(mediaState.value) {
            mediaState.value.headers.toMutableStateList()
        }
        val stickyHeaderItem by rememberStickyHeaderItem(
            gridState = gridState,
            headers = headers,
            mappedData = mappedData
        )

        val hideSearchBarSetting by rememberAutoHideSearchBar()
        val searchBarPadding by animateDpAsState(
            targetValue = remember(
                isScrolling.value,
                showSearchBar,
                searchBarPaddingTop,
                hideSearchBarSetting
            ) {
                if (showSearchBar && (!isScrolling.value || !hideSearchBarSetting)) {
                    SearchBarDefaults.InputFieldHeight + searchBarPaddingTop + 8.dp
                } else if (showSearchBar && isScrolling.value) searchBarPaddingTop else 0.dp
            },
            label = "searchBarPadding"
        )

        val density = LocalDensity.current
        val searchBarHeightPx = WindowInsets.statusBars.getTop(density)
        val searchBarPaddingPx by remember(density, searchBarPadding) {
            derivedStateOf { with(density) { searchBarPadding.roundToPx() } }
        }

        StickyHeaderGrid(
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            headerMatcher = { item -> item.key.isHeaderKey || item.key.isIgnoredKey },
            searchBarOffset = { if (showSearchBar) 28.roundSpToPx(density) + searchBarPaddingPx else 0 },
            toolbarOffset = { if (showSearchBar) 0 else 64.roundDpToPx(density) + searchBarHeightPx },
            stickyHeader = {
                val show by remember(
                    mediaState,
                    stickyHeaderItem
                ) {
                    derivedStateOf {
                        mediaState.value.media.isNotEmpty() && stickyHeaderItem != null
                    }
                }
                AnimatedVisibility(
                    visible = show,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    val text by rememberedDerivedState(stickyHeaderItem) { stickyHeaderItem ?: "" }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        // 3.dp is the elevation the LargeTopAppBar use
                                        MaterialTheme.colorScheme.surfaceColorAtElevation(
                                            3.dp
                                        ),
                                        Color.Transparent
                                    )
                                )
                            )
                            .padding(horizontal = 16.dp)
                            .padding(top = 24.dp + searchBarPadding, bottom = 24.dp)
                            .fillMaxWidth()
                    )
                }
            }
        ) {
            MediaGrid(
                modifier = modifier,
                gridState = gridState,
                mediaState = mediaState,
                metadataState = metadataState,
                mappedData = mappedData,
                paddingValues = paddingValues,
                allowSelection = allowSelection,
                canScroll = canScroll,
                allowHeaders = allowHeaders,
                aboveGridContent = aboveGridContent,
                isScrolling = isScrolling,
                emptyContent = emptyContent,
                onMediaClick = onMediaClick,
                sharedTransitionScope = sharedTransitionScope,
                animatedContentScope = animatedContentScope
            )
        }
    } else {
        MediaGrid(
            modifier = modifier,
            gridState = gridState,
            mediaState = mediaState,
            metadataState = metadataState,
            mappedData = mappedData,
            paddingValues = paddingValues,
            allowSelection = allowSelection,
            canScroll = canScroll,
            allowHeaders = allowHeaders,
            aboveGridContent = aboveGridContent,
            isScrolling = isScrolling,
            emptyContent = emptyContent,
            onMediaClick = onMediaClick,
            sharedTransitionScope = sharedTransitionScope,
            animatedContentScope = animatedContentScope
        )
    }

}