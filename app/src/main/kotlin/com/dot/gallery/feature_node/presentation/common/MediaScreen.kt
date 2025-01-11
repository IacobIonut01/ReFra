/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.common

import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.dokar.pinchzoomgrid.PinchZoomGridLayout
import com.dokar.pinchzoomgrid.rememberPinchZoomGridState
import com.dot.gallery.core.Constants.Target.TARGET_TRASH
import com.dot.gallery.core.Constants.cellsList
import com.dot.gallery.core.Settings.Misc.rememberGridSize
import com.dot.gallery.core.presentation.components.EmptyMedia
import com.dot.gallery.core.presentation.components.NavigationActions
import com.dot.gallery.core.presentation.components.NavigationButton
import com.dot.gallery.core.presentation.components.SelectionSheet
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.use_case.MediaHandleUseCase
import com.dot.gallery.feature_node.presentation.common.components.MediaGridView
import com.dot.gallery.feature_node.presentation.common.components.TwoLinedDateToolbarTitle
import com.dot.gallery.feature_node.presentation.search.MainSearchBar
import com.dot.gallery.feature_node.presentation.util.Screen

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun <T: Media> MediaScreen(
    paddingValues: PaddingValues = PaddingValues(0.dp),
    albumId: Long = remember { -1L },
    target: String? = remember { null },
    albumName: String,
    handler: MediaHandleUseCase,
    albumsState: State<AlbumState> = remember { mutableStateOf(AlbumState()) },
    mediaState: State<MediaState<T>>,
    selectionState: MutableState<Boolean>,
    selectedMedia: SnapshotStateList<T>,
    toggleSelection: (Int) -> Unit,
    allowHeaders: Boolean = true,
    showMonthlyHeader: Boolean = false,
    enableStickyHeaders: Boolean = true,
    allowNavBar: Boolean = false,
    customDateHeader: String? = null,
    customViewingNavigation: ((media: T) -> Unit)? = null,
    navActionsContent: @Composable (RowScope.(expandedDropDown: MutableState<Boolean>, result: ActivityResultLauncher<IntentSenderRequest>) -> Unit),
    emptyContent: @Composable () -> Unit = { EmptyMedia() },
    aboveGridContent: @Composable (() -> Unit)? = remember { null },
    navigate: (route: String) -> Unit,
    navigateUp: () -> Unit,
    toggleNavbar: (Boolean) -> Unit,
    isScrolling: MutableState<Boolean> = remember { mutableStateOf(false) },
    searchBarActive: MutableState<Boolean> = remember { mutableStateOf(false) },
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onActivityResult: (result: ActivityResult) -> Unit,
) {
    val showSearchBar = remember { albumId == -1L && target == null }
    var canScroll by rememberSaveable { mutableStateOf(true) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = rememberTopAppBarState(),
        canScroll = { canScroll },
        flingAnimationSpec = null
    )
    var lastCellIndex by rememberGridSize()

    val pinchState = rememberPinchZoomGridState(
        cellsList = cellsList,
        initialCellsIndex = lastCellIndex
    )

    LaunchedEffect(pinchState.isZooming) {
        canScroll = !pinchState.isZooming
        lastCellIndex = cellsList.indexOf(pinchState.currentCells)
    }

    LaunchedEffect(selectionState.value) {
        if (allowNavBar) {
            toggleNavbar(!selectionState.value)
        }
    }

    Box(
        modifier = Modifier
            .padding(
                start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
                end = paddingValues.calculateEndPadding(LocalLayoutDirection.current)
            )
    ) {
        Scaffold(
            modifier = Modifier
                .then(
                    if (!showSearchBar)
                        Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                    else Modifier
                ),
            topBar = {
                if (!showSearchBar) {
                    LargeTopAppBar(
                        title = {
                            TwoLinedDateToolbarTitle(
                                albumName = albumName,
                                dateHeader = customDateHeader ?: mediaState.value.dateHeader
                            )
                        },
                        navigationIcon = {
                            NavigationButton(
                                albumId = albumId,
                                target = target,
                                navigateUp = navigateUp,
                                clearSelection = {
                                    selectionState.value = false
                                    selectedMedia.clear()
                                },
                                selectionState = selectionState,
                                alwaysGoBack = true,
                            )
                        },
                        actions = {
                            NavigationActions(
                                actions = navActionsContent,
                                onActivityResult = onActivityResult
                            )
                        },
                        scrollBehavior = scrollBehavior
                    )
                } else {
                    MainSearchBar(
                        bottomPadding = paddingValues.calculateBottomPadding(),
                        navigate = navigate,
                        toggleNavbar = toggleNavbar,
                        selectionState = remember(selectedMedia) {
                            if (selectedMedia.isNotEmpty()) selectionState else null
                        },
                        isScrolling = isScrolling,
                        activeState = searchBarActive,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedContentScope = animatedContentScope,
                    ) {
                        NavigationActions(
                            actions = navActionsContent,
                            onActivityResult = onActivityResult
                        )
                    }
                }
            }
        ) { it ->
            PinchZoomGridLayout(state = pinchState) {
                MediaGridView(
                    mediaState = mediaState,
                    allowSelection = true,
                    showSearchBar = showSearchBar,
                    searchBarPaddingTop = remember(paddingValues) {
                        paddingValues.calculateTopPadding()
                    },
                    enableStickyHeaders = enableStickyHeaders,
                    paddingValues = remember(paddingValues, it) {
                        PaddingValues(
                            top = it.calculateTopPadding(),
                            bottom = paddingValues.calculateBottomPadding() + 128.dp
                        )
                    },
                    canScroll = canScroll,
                    selectionState = selectionState,
                    selectedMedia = selectedMedia,
                    allowHeaders = allowHeaders,
                    showMonthlyHeader = showMonthlyHeader,
                    toggleSelection = toggleSelection,
                    aboveGridContent = aboveGridContent,
                    isScrolling = isScrolling,
                    emptyContent = emptyContent,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope
                ) {
                    if (customViewingNavigation == null) {
                        val albumRoute = "albumId=$albumId"
                        val targetRoute = "target=$target"
                        val param =
                            if (target != null) targetRoute else albumRoute
                        navigate(Screen.MediaViewScreen.route + "?mediaId=${it.id}&$param")
                    } else {
                        customViewingNavigation(it)
                    }
                }
            }
        }
        if (target != TARGET_TRASH) {
            SelectionSheet(
                modifier = Modifier
                    .align(Alignment.BottomEnd),
                selectedMedia = selectedMedia,
                selectionState = selectionState,
                albumsState = albumsState,
                handler = handler
            )
        }
    }
}