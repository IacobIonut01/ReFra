/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import com.dot.gallery.ui.theme.isDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.domain.settings.GROUP_MONTHLY
import com.dot.gallery.domain.settings.GROUP_NORMAL
import com.dot.gallery.domain.settings.GROUP_YEARLY
import com.dot.gallery.domain.model.Media
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.core.presentation.util.roundDpToPx
import com.dot.gallery.core.presentation.util.roundSpToPx
import kotlinx.coroutines.flow.collectLatest
import com.dot.gallery.domain.model.isHeaderKey
import com.dot.gallery.domain.model.isIgnoredKey
import com.dot.gallery.core.ui.util.rememberedDerivedState
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.core.presentation.components.rememberStickyHeaderItem
import com.dot.gallery.core.presentation.components.StickyHeaderGrid


@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun <T : Media> GridPinchZoomScope.MediaGridView(
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
    hasToolbarOffset: Boolean = true,
    groupMethod: String = GROUP_NORMAL,
    aboveGridContent: @Composable (() -> Unit)? = null,
    isScrolling: MutableState<Boolean>,
    emptyContent: @Composable () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    allowSharedElements: Boolean = true,
    onRetry: (() -> Unit)? = null,
    onMediaClick: @DisallowComposableCalls (media: T) -> Unit = {},
) {
    val mappedData by rememberedDerivedState(mediaState, groupMethod) {
        when (groupMethod) {
            GROUP_MONTHLY -> mediaState.value.mappedMediaWithMonthly
            GROUP_YEARLY -> mediaState.value.mappedMediaWithYearly
            else -> mediaState.value.mappedMedia
        }
    }
    val selector = LocalMediaSelector.current
    val isSelectionActive by selector.isSelectionActive.collectAsStateWithLifecycle()

    @OptIn(ExperimentalComposeUiApi::class)
    BackHandler(
        enabled = isSelectionActive && allowSelection,
        onBack = selector::clearSelection
    )

    /**
     * Workaround for a small bug
     * That shows the grid at the bottom after content is loaded
     */
    var hasScrolledToTop by rememberSaveable { mutableStateOf(false) }
    // The "grid renders at the bottom after initial load" glitch only needs
    // correcting on a genuine cold load, where the grid starts at the very top
    // (index 0, offset 0). When returning from the media viewer the grid is
    // restored to a previous, non-top position - in that case we must NEVER
    // yank it back to the top, even if a transient empty/loading emission or a
    // re-sort (e.g. DATE_TAKEN rescan firing the MediaStore observer) happens
    // right after returning. Gating on the grid's own restored position - captured
    // once at first composition - is robust to all of those races (#919/#960/#965).
    val startedAtTop = remember {
        gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
    }
    LaunchedEffect(gridState, mediaState.value) {
        if (!startedAtTop || hasScrolledToTop) return@LaunchedEffect
        snapshotFlow { mediaState.value.isLoading }
            .collectLatest { isLoading ->
                if (!isLoading && !hasScrolledToTop) {
                    hasScrolledToTop = true
                    if (gridState.firstVisibleItemIndex != 0 ||
                        gridState.firstVisibleItemScrollOffset != 0
                    ) {
                        gridState.scrollToItem(0)
                    }
                }
            }
    }

    val hideSearchBarSetting by rememberPreference(SettingsKeys.autoHideSearchBar)
    val searchBarPadding by animateDpAsState(
        // Read padding during composition: Scaffold mutates its PaddingValues in place, so the
        // object identity cannot key a cache when the header grows or shrinks.
        targetValue = if (showSearchBar && (!isScrolling.value || !hideSearchBarSetting)) {
            maxOf(
                SearchBarDefaults.InputFieldHeight + searchBarPaddingTop + 8.dp,
                paddingValues.calculateTopPadding(),
            )
        } else if (showSearchBar && isScrolling.value) searchBarPaddingTop else 0.dp,
        label = "searchBarPadding"
    )

    if (enableStickyHeaders) {
        val stickyHeaderItem by rememberStickyHeaderItem(
            gridState = gridState,
            mediaState = mediaState
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
            toolbarOffset = { if (showSearchBar) 0 else if (hasToolbarOffset) 64.roundDpToPx(density) + searchBarHeightPx else 0 },
            stickyHeader = {
                val show by remember {
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
                    val darkTheme = isDarkTheme()
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge.let { style ->
                            if (!darkTheme) style.copy(
                                shadow = Shadow(
                                    color = Color.White,
                                    offset = Offset.Zero,
                                    blurRadius = 10f
                                )
                            ) else style
                        },
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
                            .padding(top = 20.dp + searchBarPadding, bottom = 20.dp)
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
                bigHeaders = groupMethod != GROUP_NORMAL,
                aboveGridContent = aboveGridContent,
                isScrolling = isScrolling,
                emptyContent = emptyContent,
                onMediaClick = onMediaClick,
                sharedTransitionScope = sharedTransitionScope,
                animatedContentScope = animatedContentScope,
                allowSharedElements = allowSharedElements,
                onRetry = onRetry,
            )
            FloatingTopBarScrim(
                barZoneHeight = searchBarPadding,
                visibility = {
                    topScrimVisibility(
                        gridState.firstVisibleItemIndex,
                        gridState.firstVisibleItemScrollOffset,
                        TopBarScrimScrollFade.toPx(),
                    )
                },
            )
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
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
                bigHeaders = groupMethod != GROUP_NORMAL,
                aboveGridContent = aboveGridContent,
                isScrolling = isScrolling,
                emptyContent = emptyContent,
                onMediaClick = onMediaClick,
                sharedTransitionScope = sharedTransitionScope,
                animatedContentScope = animatedContentScope,
                allowSharedElements = allowSharedElements,
                onRetry = onRetry,
            )
            FloatingTopBarScrim(
                barZoneHeight = searchBarPadding,
                visibility = {
                    topScrimVisibility(
                        gridState.firstVisibleItemIndex,
                        gridState.firstVisibleItemScrollOffset,
                        TopBarScrimScrollFade.toPx(),
                    )
                },
            )
        }
    }

}