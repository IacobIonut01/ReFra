/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.pinchzoomgrid.PinchZoomGridLayout
import com.dokar.pinchzoomgrid.rememberPinchZoomGridState
import com.dot.gallery.R
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Constants.cellsList
import com.dot.gallery.core.Settings.Misc.rememberAutoHideSearchBar
import com.dot.gallery.core.Settings.Misc.rememberGridSize
import com.dot.gallery.core.Settings.Search.rememberSearchHistory
import com.dot.gallery.core.presentation.components.EmptyMedia
import com.dot.gallery.core.presentation.components.LoadingMedia
import com.dot.gallery.feature_node.presentation.common.MediaViewModel
import com.dot.gallery.feature_node.presentation.common.components.MediaGridView
import com.dot.gallery.feature_node.presentation.search.components.SearchBarElevation.Collapsed
import com.dot.gallery.feature_node.presentation.search.components.SearchBarElevation.Expanded
import com.dot.gallery.feature_node.presentation.search.components.SearchHistory
import com.dot.gallery.feature_node.presentation.util.Screen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MainSearchBar(
    bottomPadding: Dp,
    selectionState: MutableState<Boolean>? = null,
    navigate: (String) -> Unit,
    toggleNavbar: (Boolean) -> Unit,
    isScrolling: MutableState<Boolean>,
    activeState: MutableState<Boolean>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    menuItems: @Composable (RowScope.() -> Unit)? = null,
) {
    var historySet by rememberSearchHistory()
    var canQuery by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val mediaViewModel: MediaViewModel = hiltViewModel<MediaViewModel>().also {
        it.mediaFlow.collectAsStateWithLifecycle()
    }
    val state = mediaViewModel.searchMediaState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.value.media) {
        if (query.isNotEmpty() && state.value.media.isEmpty() && canQuery) {
            mediaViewModel.queryMedia(query)
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (selectionState != null && selectionState.value) 0.6f else 1f,
        label = "alpha"
    )
    val elevation by animateDpAsState(
        targetValue = if (activeState.value) Expanded() else Collapsed(),
        label = "elevation"
    )
    LaunchedEffect(activeState.value) {
        if (selectionState == null) {
            toggleNavbar(!activeState.value)
        }
    }
    val hideSearchBarSetting by rememberAutoHideSearchBar()

    var shouldHide by remember { mutableStateOf(if (hideSearchBarSetting) isScrolling.value else false) }

    LaunchedEffect(isScrolling.value) {
        snapshotFlow { isScrolling.value }
            .distinctUntilChanged()
            .collectLatest {
                shouldHide = if (hideSearchBarSetting) it else false
            }
    }

    Box(
        modifier = Modifier
            .semantics { isTraversalGroup = true }
            .zIndex(1f)
            .alpha(alpha)
            .fillMaxWidth()
    ) {
        val searchBarAlpha by animateFloatAsState(
            targetValue = if (shouldHide) 0f else 1f,
            label = "searchBarAlpha"
        )
        val onActiveChange: (Boolean) -> Unit = { activeState.value = it }
        val colors = SearchBarDefaults.colors(
            dividerColor = Color.Transparent,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        )

        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = {
                        scope.launch {
                            query = it
                            if (it != mediaViewModel.lastQuery.value && mediaViewModel.lastQuery.value.isNotEmpty()) {
                                mediaViewModel.clearQuery()
                                canQuery = false
                            }
                        }
                    },
                    onSearch = {
                        if (it.isNotEmpty()) {
                            historySet = historySet.toMutableSet().apply {
                                removeIf { historyQuery -> historyQuery.contains(it) }
                                add("${System.currentTimeMillis()}/$it")
                            }
                        }
                        mediaViewModel.queryMedia(it)
                        canQuery = true
                    },
                    expanded = activeState.value,
                    onExpandedChange = onActiveChange,
                    enabled = remember(selectionState?.value, shouldHide) {
                        (selectionState == null || !selectionState.value) && !shouldHide
                    },
                    placeholder = {
                        Text(text = stringResource(id = R.string.searchbar_title))
                    },
                    leadingIcon = {
                        IconButton(
                            enabled = remember(selectionState) {
                                selectionState == null
                            },
                            onClick = {
                                scope.launch {
                                    activeState.value = !activeState.value
                                    if (query.isNotEmpty()) query = ""
                                    mediaViewModel.clearQuery()
                                }
                            }) {
                            val leadingIcon = remember(activeState.value) {
                                if (activeState.value)
                                    Icons.AutoMirrored.Outlined.ArrowBack else Icons.Outlined.Search
                            }
                            Icon(
                                imageVector = leadingIcon,
                                modifier = Modifier.fillMaxHeight(),
                                contentDescription = null
                            )
                        }
                    },
                    trailingIcon = {
                        Row {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = !activeState.value,
                            ) {
                                menuItems?.invoke(this@Row)
                            }
                        }
                    },
                    interactionSource = remember { MutableInteractionSource() },
                )
            },
            expanded = activeState.value,
            onExpandedChange = onActiveChange,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .alpha(searchBarAlpha),
            colors = colors,
            tonalElevation = elevation,
            content = {
                val lastQueryIsEmpty =
                    remember(mediaViewModel.lastQuery.value) { mediaViewModel.lastQuery.value.isEmpty() }
                AnimatedVisibility(
                    visible = lastQueryIsEmpty,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    SearchHistory {
                        canQuery = true
                        query = it
                        mediaViewModel.queryMedia(it)
                    }
                }

                AnimatedVisibility(
                    visible = !lastQueryIsEmpty,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    val mediaIsEmpty = remember(state) { state.value.media.isEmpty() && !state.value.isLoading }
                    if (mediaIsEmpty) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 72.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ImageSearch,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(64.dp)
                            )
                            Text(
                                text = stringResource(R.string.no_media_found),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    } else {
                        val pd = PaddingValues(
                            bottom = bottomPadding + 16.dp
                        )
                        var canScroll by rememberSaveable { mutableStateOf(true) }
                        var lastCellIndex by rememberGridSize()
                        val pinchState = rememberPinchZoomGridState(
                            cellsList = cellsList,
                            initialCellsIndex = lastCellIndex
                        )

                        LaunchedEffect(pinchState.currentCells) {
                            lastCellIndex = cellsList.indexOf(pinchState.currentCells)
                        }
                        LaunchedEffect(pinchState.isZooming) {
                            canScroll = !pinchState.isZooming
                        }

                        PinchZoomGridLayout(state = pinchState) {
                            MediaGridView(
                                mediaState = state,
                                paddingValues = pd,
                                canScroll = canScroll,
                                isScrolling = remember { mutableStateOf(false) },
                                onMediaClick = {
                                    navigate(Screen.MediaViewScreen.route + "?mediaId=${it.id}&query=true")
                                },
                                emptyContent = { EmptyMedia() },
                                animatedContentScope = animatedContentScope,
                                sharedTransitionScope = sharedTransitionScope
                            )
                            androidx.compose.animation.AnimatedVisibility(
                                visible = state.value.isLoading,
                                enter = enterAnimation,
                                exit = exitAnimation
                            ) {
                                LoadingMedia()
                            }
                        }

                    }
                }
            },
        )
    }

    BackHandler(activeState.value) {
        scope.launch {
            if (mediaViewModel.lastQuery.value.isEmpty()) {
                activeState.value = false
            }
            canQuery = false
            query = ""
            mediaViewModel.clearQuery()
        }
    }
}
