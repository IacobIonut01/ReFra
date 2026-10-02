/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.albumtimeline

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.dot.gallery.feature_node.presentation.common.components.GridPinchZoomLayout
import com.dot.gallery.feature_node.presentation.common.components.rememberGridPinchZoomState
import com.dot.gallery.R
import com.dot.gallery.cloud.core.CloudAlbumIdentity
import com.dot.gallery.cloud.core.CloudRuntimeSettings
import com.dot.gallery.core.Constants.cellsList
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.cloudMemberKey
import com.dot.gallery.core.Settings.Album.rememberAlbumGroupByDate
import com.dot.gallery.core.Settings.Album.rememberAlbumMediaSort
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.rememberAlbumsGroupMethod
import com.dot.gallery.core.Settings.Misc.rememberGridSize
import com.dot.gallery.core.Settings.Misc.rememberMosaicGridSize
import com.dot.gallery.core.Settings.Misc.rememberTimelineLayoutType
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.components.EmptyMedia
import com.dot.gallery.core.presentation.components.ModalSheet
import com.dot.gallery.core.presentation.components.NavigationButton
import com.dot.gallery.core.presentation.components.SelectionSheet
import com.dot.gallery.core.presentation.components.SetupButton
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.presentation.albumtimeline.components.AlbumSortDropdown
import com.dot.gallery.feature_node.presentation.albumtimeline.components.SlideshowOptionsSheet
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.common.components.MediaGridView
import com.dot.gallery.feature_node.presentation.common.components.MosaicMediaGrid
import com.dot.gallery.feature_node.presentation.common.components.MosaicPinchZoomLayout
import com.dot.gallery.feature_node.presentation.common.components.TimelineScroller
import com.dot.gallery.feature_node.presentation.common.components.rememberMosaicMonthSegments
import com.dot.gallery.feature_node.presentation.common.components.rememberMosaicPinchZoomState
import com.dot.gallery.feature_node.presentation.common.components.TwoLinedDateToolbarTitle
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.feature_node.presentation.util.selectedMedia
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle
import com.dot.gallery.feature_node.presentation.util.hazeEffectScaled
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)
@Composable
fun AlbumTimelineScreen(
    albumId: Long,
    albumName: String,
    paddingValues: PaddingValues,
    isScrolling: MutableState<Boolean>,
    albumMediaState: State<MediaState<Media.UriMedia>>,
    metadataState: State<MediaMetadataState>,
    onAlbumClick: (Album) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    var canScroll by rememberSaveable { mutableStateOf(true) }
    var lastCellIndex by rememberGridSize()
    val snackbarHostState = remember { SnackbarHostState() }
    val eventHandler = LocalEventHandler.current
    val distributor = LocalMediaDistributor.current
    val isRefreshing by distributor.isRefreshing.collectAsStateWithLifecycle()
    val refreshScope = rememberCoroutineScope()
    val albumsState by distributor.albumsFlow.collectAsStateWithLifecycle()
    val mergedSubfolderConfigs by distributor.mergedSubfolderAlbumsFlow.collectAsStateWithLifecycle()
    val isSubGallery = mergedSubfolderConfigs.any {
        it.id == albumId &&
            it.displayMode == com.dot.gallery.feature_node.domain.model.MergedSubfolderAlbum.DISPLAY_MODE_SUB_GALLERY
    }
    val currentAlbum = remember(albumsState, albumId) {
        albumsState.albums.find { it.id == albumId }
            ?: albumsState.albumsWithBlacklisted.find { it.id == albumId }
    }
    val mergedAlbumIds = currentAlbum?.sourceAlbumIds ?: emptyList()
    val constituentAlbums = remember(albumsState, mergedAlbumIds, currentAlbum) {
        if (mergedAlbumIds.size > 1 || currentAlbum?.mergesSubfolders == true) {
            val rawById = albumsState.albumsWithBlacklisted.associateBy { it.id }
            mergedAlbumIds.mapNotNull(rawById::get).filterNot {
                currentAlbum?.mergesSubfolders == true && it.id == currentAlbum.id
            }
        } else emptyList()
    }
    val resolvedAlbumName = currentAlbum?.label ?: albumName
    val hasMergeInfo = currentAlbum?.isMerged == true && constituentAlbums.isNotEmpty()
    var showMergedBanner by rememberSaveable(albumId) { mutableStateOf(true) }
    val showMergeContent = hasMergeInfo && (isSubGallery || showMergedBanner)
    val context = androidx.compose.ui.platform.LocalContext.current
    val selector = LocalMediaSelector.current
    val selectedMedia = selector.selectedMedia.collectAsStateWithLifecycle()
    val cloudAlbumManageViewModel = hiltViewModel<CloudAlbumManageViewModel>()
    val cloudSettingsByConfigId by CloudRuntimeSettings.settingsByConfigId.collectAsStateWithLifecycle()

    // Sort preferences - saved to DataStore, read by MediaDistributor
    var albumMediaSort by rememberAlbumMediaSort()

    // Media state is already sorted by MediaDistributor based on albumMediaSort
    val mediaState = albumMediaState
    val selectedMediaList by selectedMedia(
        media = mediaState.value.media,
        selectedSet = selectedMedia
    )

    val slideshowSheetState = rememberAppBottomSheetState()
    val slideshowScope = rememberCoroutineScope()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = rememberTopAppBarState(),
        canScroll = { canScroll },
        flingAnimationSpec = null
    )

    val dpCacheWindow = LazyLayoutCacheWindow(ahead = 200.dp, behind = 100.dp)
    val pinchState = rememberGridPinchZoomState(
        cellsList = cellsList,
        initialCellsIndex = lastCellIndex,
        gridState = rememberLazyGridState(
            cacheWindow = dpCacheWindow
        )
    )

    LaunchedEffect(pinchState.isZooming) {
        withContext(Dispatchers.IO) {
            canScroll = !pinchState.isZooming
            lastCellIndex = cellsList.indexOf(pinchState.currentCells)
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
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                LargeTopAppBar(
                    modifier = Modifier.hazeEffectScaled(
                        state = LocalHazeState.current,
                        style = LocalHazeBlurStyle.current
                    ),
                    title = {
                        TwoLinedDateToolbarTitle(
                            albumName = resolvedAlbumName,
                            dateHeader = mediaState.value.dateHeader
                        )
                    },
                    navigationIcon = {
                        NavigationButton(
                            albumId = albumId,
                            target = null,
                            alwaysGoBack = true,
                        )
                    },
                    actions = {
                        IconButton(onClick = {
                            slideshowScope.launch { slideshowSheetState.show() }
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Slideshow,
                                contentDescription = stringResource(R.string.slideshow)
                            )
                        }
                        AlbumSortDropdown(
                            currentSort = albumMediaSort,
                            onSortChange = { newSort ->
                                albumMediaSort = newSort
                            }
                        )
                        currentAlbum?.let { album ->
                            album.cloudIdentity?.let { identity ->
                                CloudAlbumManageMenu(
                                    album = album,
                                    identity = identity,
                                    readOnly = cloudSettingsByConfigId[identity.serverConfigId]?.readOnlyMode == true,
                                    selection = selectedMediaList,
                                    viewModel = cloudAlbumManageViewModel,
                                    snackbarHostState = snackbarHostState,
                                    onDeleted = { eventHandler.navigateUpAction() },
                                    onSelectionHandled = { selector.clearSelection() }
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        ) { it ->
            val ptrState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { refreshScope.launch { distributor.invalidate() } },
                state = ptrState,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = ptrState,
                        isRefreshing = isRefreshing,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = it.calculateTopPadding())
                    )
                },
            ) {
            val albumGroupByDate by rememberAlbumGroupByDate()
            val albumsGroupMethod by rememberAlbumsGroupMethod()
            val timelineLayoutType by rememberTimelineLayoutType()
            val isMosaicLayout = timelineLayoutType == Settings.Misc.LAYOUT_MOSAIC
            if (isMosaicLayout) {
                var lastMosaicCellIndex by rememberMosaicGridSize()
                val mosaicPinchState = rememberMosaicPinchZoomState(
                    initialColumnsIndex = lastMosaicCellIndex,
                    gridState = rememberLazyGridState(
                        cacheWindow = dpCacheWindow
                    )
                )
                val mosaicGridState = mosaicPinchState.gridState

                LaunchedEffect(mosaicPinchState.isZooming) {
                    lastMosaicCellIndex = mosaicPinchState.currentColumnsIndex
                }

                val mappedData by remember(mediaState, albumsGroupMethod, albumGroupByDate) {
                    derivedStateOf {
                        if (!albumGroupByDate) {
                            mediaState.value.mappedMedia
                        } else {
                            when (albumsGroupMethod) {
                                Settings.Misc.GROUP_MONTHLY -> mediaState.value.mappedMediaWithMonthly
                                Settings.Misc.GROUP_YEARLY -> mediaState.value.mappedMediaWithYearly
                                else -> mediaState.value.mappedMedia
                            }
                        }.toMutableStateList()
                    }
                }
                val headers by remember(mediaState) {
                    derivedStateOf {
                        mediaState.value.headers.toMutableStateList()
                    }
                }
                val mosaicPaddingValues = PaddingValues(
                    top = it.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding() + 128.dp
                )
                MosaicPinchZoomLayout(
                    state = mosaicPinchState,
                    indicatorTopPadding = mosaicPaddingValues.calculateTopPadding() + 16.dp,
                ) { currentColumns ->
                TimelineScroller(
                    modifier = Modifier
                        .padding(mosaicPaddingValues)
                        .padding(top = 32.dp)
                        .padding(vertical = 32.dp),
                    segments = rememberMosaicMonthSegments(
                        mappedData = mappedData,
                        columns = currentColumns,
                        allowHeaders = albumGroupByDate,
                        leadingItemCount = if (showMergeContent) 1 else 0,
                        dateSource = mediaState.value.dateSource,
                    ),
                    headers = headers,
                    state = mosaicGridState,
                ) {
                    MosaicMediaGrid(
                        modifier = Modifier.hazeSource(LocalHazeState.current),
                        gridState = mosaicGridState,
                        columns = currentColumns,
                        mediaState = mediaState,
                        metadataState = metadataState,
                        mappedData = mappedData,
                        paddingValues = mosaicPaddingValues,
                        allowSelection = true,
                        canScroll = !mosaicPinchState.isZooming,
                        allowHeaders = albumGroupByDate,
                        aboveGridContent = if (showMergeContent) {
                            {
                                AlbumsMergedBanner(
                                    constituentAlbums = constituentAlbums,
                                    mergesSubfolders = currentAlbum.mergesSubfolders,
                                    mergesByName = currentAlbum.mergesByName,
                                    dismissible = !isSubGallery,
                                    onAlbumClick = onAlbumClick,
                                    onDismiss = { showMergedBanner = false }
                                )
                            }
                        } else null,
                        isScrolling = isScrolling,
                        emptyContent = {
                            if (!isSubGallery || constituentAlbums.isEmpty()) EmptyMedia()
                        },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedContentScope = animatedContentScope,
                        onMediaClick = {
                            eventHandler.navigate(Screen.MediaViewScreen.idAndAlbum(it.id, albumId))
                        },
                    )
                }
                }
            } else {
                GridPinchZoomLayout(
                    state = pinchState,
                    modifier = Modifier.hazeSource(LocalHazeState.current),
                    indicatorTopPadding = it.calculateTopPadding() + 16.dp,
                ) {
                    MediaGridView(
                        mediaState = mediaState,
                        metadataState = metadataState,
                        allowSelection = true,
                        showSearchBar = false,
                        enableStickyHeaders = albumGroupByDate,
                        groupMethod = if (albumGroupByDate) albumsGroupMethod else Settings.Misc.GROUP_NORMAL,
                        paddingValues = PaddingValues(
                            top = it.calculateTopPadding(),
                            bottom = paddingValues.calculateBottomPadding() + 128.dp
                        ),
                        canScroll = canScroll,
                        allowHeaders = albumGroupByDate,
                        aboveGridContent = if (showMergeContent) {
                            {
                                AlbumsMergedBanner(
                                    constituentAlbums = constituentAlbums,
                                    mergesSubfolders = currentAlbum.mergesSubfolders,
                                    mergesByName = currentAlbum.mergesByName,
                                    dismissible = !isSubGallery,
                                    onAlbumClick = onAlbumClick,
                                    onDismiss = { showMergedBanner = false }
                                )
                            }
                        } else null,
                        isScrolling = isScrolling,
                        emptyContent = {
                            if (!isSubGallery || constituentAlbums.isEmpty()) EmptyMedia()
                        },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedContentScope = animatedContentScope
                    ) {
                        eventHandler.navigate(Screen.MediaViewScreen.idAndAlbum(it.id, albumId))
                    }
                }
            }
            } // PullToRefreshBox
        }
        SelectionSheet(
            modifier = Modifier
                .align(Alignment.BottomEnd),
            allMedia = mediaState.value,
            selectedMedia = selectedMediaList
        )
        SlideshowOptionsSheet(
            state = slideshowSheetState,
            canStart = mediaState.value.media.isNotEmpty(),
            onStart = {
                val startId = mediaState.value.pagerMedia.firstOrNull()?.id
                    ?: mediaState.value.media.firstOrNull()?.id
                if (startId != null) {
                    eventHandler.navigate(
                        Screen.MediaViewScreen.idAndAlbumSlideshow(startId, albumId)
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun AlbumsMergedBanner(
    constituentAlbums: List<Album>,
    mergesSubfolders: Boolean,
    mergesByName: Boolean,
    dismissible: Boolean,
    onAlbumClick: (Album) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (mergesSubfolders) R.string.subfolders_merged_title
                            else R.string.albums_merged_title
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(
                            when {
                                mergesSubfolders && mergesByName -> R.string.folders_combined_description
                                mergesSubfolders -> R.string.subfolders_merged_description
                                else -> R.string.albums_merged_description
                            },
                            constituentAlbums.size
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (dismissible) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.dismiss),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = constituentAlbums,
                    key = { it.id }
                ) { album ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(72.dp)
                            .clickable { onAlbumClick(album) }
                    ) {
                        GlideImage(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            model = album.uri,
                            contentDescription = album.label,
                            contentScale = ContentScale.Crop,
                            requestBuilderTransform = {
                                it.centerCrop()
                                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                                    .thumbnail(it.clone().sizeMultiplier(0.4f))
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = album.relativePath.removeSuffix("/"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
@Composable
private fun CloudAlbumManageMenu(
    album: Album,
    identity: CloudAlbumIdentity,
    readOnly: Boolean,
    selection: List<Media>,
    viewModel: CloudAlbumManageViewModel,
    snackbarHostState: SnackbarHostState,
    onDeleted: () -> Unit,
    onSelectionHandled: () -> Unit
) {
    val canRenameDelete = album.isCloudOwned && !readOnly
    val canRemoveMembers = !readOnly && (album.isCloudOwned || album.cloudShareRole == "editor")
        && selection.isNotEmpty()
    if (!canRenameDelete && !canRemoveMembers) return

    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    val renameSheetState = rememberAppBottomSheetState()
    val originalName = album.cloudName.ifBlank { album.label }
    var newName by rememberSaveable(album) { mutableStateOf(originalName) }
    val nameFocusRequester = remember { FocusRequester() }
    var showDelete by remember { mutableStateOf(false) }
    val failureText = stringResource(R.string.cloud_album_action_failed)

    val confirmRename: () -> Unit = {
        val trimmed = newName.trim()
        if (trimmed.isNotEmpty() && trimmed != originalName) {
            scope.launch {
                renameSheetState.hide()
                viewModel.renameAlbum(identity, trimmed)
                    .onFailure { snackbarHostState.showSnackbar(failureText) }
            }
        }
    }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = null
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (canRenameDelete) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rename_album)) },
                    onClick = {
                        expanded = false
                        newName = originalName
                        scope.launch { renameSheetState.show() }
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.delete_remote_album),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        expanded = false
                        showDelete = true
                    }
                )
            }
            if (canRemoveMembers) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remove_from_album)) },
                    onClick = {
                        expanded = false
                        val remoteIds = selection.mapNotNull { media ->
                            cloudMemberKey(media.getUri().toString())
                                ?.takeIf {
                                    it.providerType == identity.providerType &&
                                        it.serverConfigId == identity.serverConfigId
                                }
                                ?.remoteId
                        }
                        if (remoteIds.isEmpty()) return@DropdownMenuItem
                        scope.launch {
                            viewModel.removeMembers(identity, remoteIds)
                                .onSuccess { onSelectionHandled() }
                                .onFailure { snackbarHostState.showSnackbar(failureText) }
                        }
                    }
                )
            }
        }
    }

    ModalSheet(
        sheetState = renameSheetState,
        title = stringResource(R.string.rename_album),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        content = {
        OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            singleLine = true,
            label = { Text(stringResource(R.string.album_name)) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(nameFocusRequester)
                .imePadding(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { confirmRename() })
        )
        Spacer(Modifier.height(16.dp))
        SetupButton(
            applyHorizontalPadding = false,
            applyBottomPadding = false,
            applyInsets = false,
            enabled = newName.trim().let { it.isNotEmpty() && it != originalName },
            text = stringResource(R.string.save),
            onClick = confirmRename
        )
        LaunchedEffect(renameSheetState.isVisible) {
            if (renameSheetState.isVisible) nameFocusRequester.requestFocus()
        }
        }
    )

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.delete_remote_album_confirm_title)) },
            text = { Text(stringResource(R.string.delete_remote_album_confirm_summary)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDelete = false
                        scope.launch {
                            viewModel.deleteAlbum(identity)
                                .onSuccess { onDeleted() }
                                .onFailure { snackbarHostState.showSnackbar(failureText) }
                        }
                    }
                ) {
                    Text(
                        stringResource(R.string.delete_remote_album),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
