/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.albums

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.Icon
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.ui.descriptor.ProviderBrandIcon
import com.dot.gallery.feature_node.presentation.common.components.GridPinchZoomLayout
import com.dot.gallery.feature_node.presentation.common.components.rememberGridPinchZoomState
import com.dot.gallery.R
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Constants.albumCellsList
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.ScrollToTopHandler
import androidx.compose.runtime.CompositionLocalProvider
import com.dot.gallery.core.image.thumbnail.LocalThumbnailMotion
import com.dot.gallery.core.image.thumbnail.rememberThumbnailMotionState
import com.dot.gallery.core.animateOrJumpToTop
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Album.rememberAlbumGridSize
import com.dot.gallery.core.Settings.Album.rememberLastSort
import com.dot.gallery.core.Settings.Album.rememberLastViewType
import com.dot.gallery.core.Settings.Album.rememberPinnedAlbumsAsGrid
import com.dot.gallery.core.Settings.Album.rememberShowMediaTypeAlbums
import com.dot.gallery.core.presentation.components.EmptyAlbum
import com.dot.gallery.core.presentation.components.Error
import com.dot.gallery.core.presentation.components.FilterButton
import com.dot.gallery.core.presentation.components.FilterKind
import com.dot.gallery.core.presentation.components.FilterOption
import com.dot.gallery.core.presentation.components.LoadingAlbum
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.AlbumGroupWithAlbums
import com.dot.gallery.feature_node.domain.model.AlbumSectionWithAlbums
import com.dot.gallery.feature_node.presentation.albums.components.AlbumSectionHeader
import com.dot.gallery.feature_node.domain.model.CollectionWithCount
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.MediaTypeAlbum
import com.dot.gallery.feature_node.domain.util.MediaOrder
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.albums.components.AlbumComponent
import com.dot.gallery.feature_node.presentation.albums.components.AlbumGroupComponent
import com.dot.gallery.feature_node.presentation.albums.components.AlbumGroupRowComponent
import com.dot.gallery.feature_node.presentation.albums.components.AlbumRowComponent
import com.dot.gallery.feature_node.presentation.albums.components.CarouselPinnedAlbums
import com.dot.gallery.feature_node.presentation.common.components.MediaContentState
import com.dot.gallery.feature_node.presentation.common.components.mediaContentState
import com.dot.gallery.feature_node.presentation.collection.components.CollectionComponent
import com.dot.gallery.feature_node.presentation.collection.components.CollectionRowComponent
import com.dot.gallery.feature_node.presentation.collection.components.CreateCollectionComponent
import com.dot.gallery.feature_node.presentation.collection.components.CreateCollectionRowComponent
import com.dot.gallery.feature_node.presentation.search.MainSearchBar
import com.dot.gallery.feature_node.presentation.timeline.components.TimelineNavActions
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.feature_node.presentation.util.mediaSharedElement
import com.dot.gallery.feature_node.presentation.util.rememberActivityResult
import com.dot.gallery.feature_node.presentation.util.rememberBottomBarInset
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlinx.coroutines.Dispatchers

internal fun albumScreenHorizontalInsets(
    paddingValues: PaddingValues,
    layoutDirection: LayoutDirection,
): PaddingValues = PaddingValues(
    start = paddingValues.calculateStartPadding(layoutDirection),
    end = paddingValues.calculateEndPadding(layoutDirection),
)

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun AlbumsScreen(
    paddingValues: PaddingValues,
    filterOptions: SnapshotStateList<FilterOption>,
    isScrolling: MutableState<Boolean>,
    onAlbumClick: (Album) -> Unit,
    onAlbumLongClick: (Album) -> Unit,
    onMoveAlbumToTrash: (ActivityResultLauncher<IntentSenderRequest>, Album) -> Unit,
    onIgnoreAlbum: (Album) -> Unit,
    onLockAlbum: (Album) -> Unit,
    onGroupClick: (AlbumGroupWithAlbums) -> Unit = {},
    onRenameGroup: (AlbumGroupWithAlbums) -> Unit = {},
    onDeleteGroup: (AlbumGroupWithAlbums) -> Unit = {},
    onEditGroup: (AlbumGroupWithAlbums) -> Unit = {},
    onAddToGroup: ((Album) -> Unit)? = null,
    onToggleMergeSubfolders: ((Album) -> Unit)? = null,
    onMergeParentSubfolders: ((Album) -> Unit)? = null,
    onToggleMergedSubfolderDisplayMode: ((Album) -> Unit)? = null,
    onCollectionClick: (CollectionWithCount) -> Unit = {},
    onCollectionRename: (CollectionWithCount) -> Unit = {},
    onCollectionDelete: (CollectionWithCount) -> Unit = {},
    onCollectionTogglePin: (CollectionWithCount) -> Unit = {},
    onCollectionEditAlbums: (CollectionWithCount) -> Unit = {},
    onCreateCollection: () -> Unit = {},
    onMoveToSection: ((Album) -> Unit)? = null,
    onToggleSectionExpanded: (AlbumSectionWithAlbums, Boolean) -> Unit = { _, _ -> },
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    val distributor = LocalMediaDistributor.current
    val mergedSubfolderAlbums by distributor.mergedSubfolderAlbumsFlow.collectAsStateWithLifecycle()
    val mergedSubfolderIds = remember(mergedSubfolderAlbums) {
        mergedSubfolderAlbums.mapTo(HashSet()) { it.id }
    }
    val subGalleryIds = remember(mergedSubfolderAlbums) {
        mergedSubfolderAlbums.filter {
            it.displayMode == com.dot.gallery.feature_node.domain.model.MergedSubfolderAlbum.DISPLAY_MODE_SUB_GALLERY
        }.mapTo(HashSet()) { it.id }
    }
    val mediaState = distributor.timelineMediaFlow.collectAsStateWithLifecycle(
        context = Dispatchers.IO,
        initialValue = MediaState()
    )
    val albumsState = distributor.albumsFlow.collectAsStateWithLifecycle()

    // Cloud albums grouped by provider (e.g. SMB, IMMICH) for their dedicated section.
    val cloudProviderGroups = remember(albumsState.value.albumsCloud) {
        albumsState.value.albumsCloud
            .groupBy { it.relativePath.removePrefix("cloud/").substringBefore("/") }
            .toList()
    }

    // Virtual "albums" grouping the whole library by media type (Videos/Photos/GIFs/Raw).
    // Derived from the already-collected timeline media; empty types are dropped.
    val mediaTypeLabels = MediaTypeAlbum.entries.associateWith { stringResource(it.labelRes) }
    val showMediaTypeAlbums by rememberShowMediaTypeAlbums()
    val mediaTypeAlbums = remember(mediaState.value.media, showMediaTypeAlbums, mediaTypeLabels) {
        if (!showMediaTypeAlbums) emptyList()
        else MediaTypeAlbum.entries.mapNotNull { type ->
            val matches = mediaState.value.media.filter { type.matches(it) }
            if (matches.isEmpty()) return@mapNotNull null
            Album(
                id = type.albumId,
                label = mediaTypeLabels.getValue(type),
                uri = matches.first().getUri(),
                pathToThumbnail = "",
                relativePath = "",
                timestamp = 0,
                count = matches.size.toLong()
            )
        }
    }

    var lastCellIndex by rememberAlbumGridSize()

    val pinchState = rememberGridPinchZoomState(
        cellsList = albumCellsList,
        initialCellsIndex = lastCellIndex
    )
    val listState = rememberLazyListState()
    var viewType by rememberLastViewType()
    val pinnedAlbumsAsGrid by rememberPinnedAlbumsAsGrid()
    // Phase 3 (#1076): album covers render through the shared RenderImage, so publishing the
    // active view's debounced scroll-motion makes them use the cheap MOTION tier while scrolling.
    val thumbnailMotion = rememberThumbnailMotionState({
        when (viewType) {
            Settings.Album.ViewType.LIST -> listState.isScrollInProgress
            else -> pinchState.gridState.isScrollInProgress
        }
    })

    LaunchedEffect(pinchState.isZooming) {
        lastCellIndex = albumCellsList.indexOf(pinchState.currentCells)
    }
    val lastSort by rememberLastSort()
    LaunchedEffect(lastSort) {
        val selectedFilter = filterOptions.first { it.filterKind == lastSort.kind }
        selectedFilter.onClick(
            when (selectedFilter.filterKind) {
                FilterKind.DATE -> MediaOrder.Date(lastSort.orderType)
                FilterKind.DATE_MODIFIED -> MediaOrder.DateModified(lastSort.orderType)
                FilterKind.NAME -> MediaOrder.Label(lastSort.orderType)
            }
        )
    }

    // Re-tapping the Albums tab scrolls the active view back to the top (#1039).
    ScrollToTopHandler(Screen.AlbumsScreen.route) {
        when (viewType) {
            Settings.Album.ViewType.GRID -> pinchState.gridState.animateOrJumpToTop()
            Settings.Album.ViewType.LIST -> listState.animateOrJumpToTop()
        }
    }

    val horizontalInsets = albumScreenHorizontalInsets(
        paddingValues = paddingValues,
        layoutDirection = LocalLayoutDirection.current,
    )
    Scaffold(
        modifier = Modifier
            .padding(horizontalInsets)
            .consumeWindowInsets(horizontalInsets),
        topBar = {
            MainSearchBar(
                isScrolling = isScrolling,
                sharedTransitionScope = sharedTransitionScope,
                animatedContentScope = animatedContentScope,
                menuItems = { TimelineNavActions() },
            )
        }
    ) { innerPaddingValues ->
        when (mediaContentState(
            isLoading = albumsState.value.isLoading,
            error = albumsState.value.error,
            isEmpty = albumsState.value.albums.isEmpty() && albumsState.value.collections.isEmpty(),
        )) {
            MediaContentState.ERROR -> Error(
                modifier = Modifier.padding(innerPaddingValues),
                errorMessage = albumsState.value.error,
            )
            MediaContentState.LOADING -> LoadingAlbum()
            MediaContentState.EMPTY -> EmptyAlbum()
            MediaContentState.CONTENT -> CompositionLocalProvider(LocalThumbnailMotion provides thumbnailMotion) {
        when (viewType) {
            Settings.Album.ViewType.GRID -> {
                with(sharedTransitionScope) {
                    GridPinchZoomLayout(
                        state = pinchState,
                        modifier = Modifier.hazeSource(LocalHazeState.current),
                        indicatorTopPadding = innerPaddingValues.calculateTopPadding() + 16.dp,
                    ) {
                        LaunchedEffect(gridState.isScrollInProgress) {
                            isScrolling.value = gridState.isScrollInProgress
                        }
                        val bottomBarInset = rememberBottomBarInset(innerPaddingValues)
                        LazyVerticalGrid(
                            state = gridState,
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .fillMaxSize(),
                            columns = gridCells,
                            contentPadding = PaddingValues(
                                top = innerPaddingValues.calculateTopPadding(),
                                bottom = bottomBarInset + 16.dp + 64.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item(
                                span = { GridItemSpan(maxLineSpan) },
                                key = "pinnedAlbums"
                            ) {
                                AnimatedVisibility(
                                    visible = albumsState.value.albumsPinned.isNotEmpty(),
                                    enter = enterAnimation,
                                    exit = exitAnimation
                                ) {
                                    if (pinnedAlbumsAsGrid) {
                                        Text(
                                            modifier = Modifier
                                                .pinchItem(key = "pinnedAlbums")
                                                .padding(horizontal = 8.dp)
                                                .padding(vertical = 24.dp),
                                            text = stringResource(R.string.pinned_albums_title),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                    } else {
                                        Column {
                                            Text(
                                                modifier = Modifier
                                                    .pinchItem(key = "pinnedAlbums")
                                                    .padding(horizontal = 8.dp)
                                                    .padding(vertical = 24.dp),
                                                text = stringResource(R.string.pinned_albums_title),
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium
                                            )
                                            CarouselPinnedAlbums(
                                                albumList = albumsState.value.albumsPinned,
                                                onAlbumClick = onAlbumClick,
                                                onAlbumLongClick = onAlbumLongClick,
                                                mergedSubfolderIds = mergedSubfolderIds,
                                                subGalleryIds = subGalleryIds,
                                                onToggleMergeSubfolders = onToggleMergeSubfolders,
                                                onToggleMergedSubfolderDisplayMode = onToggleMergedSubfolderDisplayMode
                                            )
                                        }
                                    }
                                }
                            }
                            if (pinnedAlbumsAsGrid) {
                                items(
                                    items = albumsState.value.albumsPinned,
                                    key = { item -> "pinned_${item.id}" }
                                ) { item ->
                                    val trashResult = rememberActivityResult()
                                    AlbumComponent(
                                        modifier = Modifier
                                            .pinchItem(key = "pinned_${item.id}")
                                            .animateItem(),
                                        album = item,
                                        onItemClick = onAlbumClick,
                                        onTogglePinClick = onAlbumLongClick,
                                        onMoveAlbumToTrash = {
                                            onMoveAlbumToTrash(trashResult, it)
                                        },
                                        onToggleIgnoreClick = onIgnoreAlbum,
                                        onToggleLockClick = onLockAlbum,
                                        onAddToGroup = onAddToGroup,
                                        onMoveToSection = onMoveToSection,
                                        onToggleMergeSubfolders = onToggleMergeSubfolders,
                                        onMergeParentSubfolders = onMergeParentSubfolders,
                                        onToggleMergedSubfolderDisplayMode = onToggleMergedSubfolderDisplayMode,
                                        isMergedSubfolder = item.id in mergedSubfolderIds,
                                        isSubGallery = item.id in subGalleryIds
                                    )
                                }
                            }
                            item(
                                span = { GridItemSpan(maxLineSpan) },
                                key = "filterButton"
                            ) {
                                AnimatedVisibility(
                                    visible = albumsState.value.albumsUnpinned.isNotEmpty() || albumsState.value.albumSections.isNotEmpty(),
                                    enter = enterAnimation,
                                    exit = exitAnimation
                                ) {
                                    FilterButton(
                                        modifier = Modifier.pinchItem(key = "filterButton"),
                                        filterOptions = filterOptions.toTypedArray(),
                                        viewType = viewType,
                                        onViewTypeChange = { viewType = it }
                                    )
                                }
                            }
                            items(
                                items = albumsState.value.albumGroups,
                                key = { group -> "group_${group.group.id}" }
                            ) { group ->
                                AlbumGroupComponent(
                                    modifier = Modifier
                                        .pinchItem(key = "group_${group.group.id}")
                                        .animateItem(),
                                    groupWithAlbums = group,
                                    onGroupClick = onGroupClick,
                                    onRenameGroup = onRenameGroup,
                                    onDeleteGroup = onDeleteGroup,
                                    onEditGroup = onEditGroup
                                )
                            }
                            // Album Sections (when enabled)
                            albumsState.value.albumSections.forEach { sectionWithAlbums ->
                                item(
                                    span = { GridItemSpan(maxLineSpan) },
                                    key = "section_header_${sectionWithAlbums.section.id}"
                                ) {
                                    AlbumSectionHeader(
                                        modifier = Modifier.animateItem(),
                                        sectionWithAlbums = sectionWithAlbums,
                                        onToggleExpanded = { expanded ->
                                            onToggleSectionExpanded(sectionWithAlbums, expanded)
                                        }
                                    )
                                }
                                if (sectionWithAlbums.section.isExpanded) {
                                    items(
                                        items = sectionWithAlbums.albums,
                                        key = { item -> "section_${sectionWithAlbums.section.id}_${item.id}" }
                                    ) { item ->
                                        val trashResult = rememberActivityResult()
                                        with(sharedTransitionScope) {
                                            AlbumComponent(
                                                modifier = Modifier
                                                    .pinchItem(key = "section_${sectionWithAlbums.section.id}_${item.id}")
                                                    .animateItem(),
                                                thumbnailModifier = Modifier
                                                    .mediaSharedElement(
                                                        album = item,
                                                        animatedVisibilityScope = animatedContentScope
                                                    ),
                                                album = item,
                                                onItemClick = onAlbumClick,
                                                onTogglePinClick = onAlbumLongClick,
                                                onMoveAlbumToTrash = {
                                                    onMoveAlbumToTrash(trashResult, it)
                                                },
                                                onToggleIgnoreClick = onIgnoreAlbum,
                                                onToggleLockClick = onLockAlbum,
                                                onAddToGroup = onAddToGroup,
                                                onMoveToSection = onMoveToSection,
                                                onToggleMergeSubfolders = onToggleMergeSubfolders,
                                                onMergeParentSubfolders = onMergeParentSubfolders,
                                                onToggleMergedSubfolderDisplayMode = onToggleMergedSubfolderDisplayMode,
                                                isMergedSubfolder = item.id in mergedSubfolderIds,
                                                isSubGallery = item.id in subGalleryIds
                                            )
                                        }
                                    }
                                }
                            }
                            // Unpinned albums (flat list, when sections are disabled)
                            items(
                                items = albumsState.value.albumsUnpinned,
                                key = { item -> "album_${item.id}" }
                            ) { item ->
                                val trashResult = rememberActivityResult()
                                with(sharedTransitionScope) {
                                    AlbumComponent(
                                        modifier = Modifier
                                            .pinchItem(key = "album_${item.id}")
                                            .animateItem(),
                                        thumbnailModifier = Modifier
                                            .mediaSharedElement(
                                                album = item,
                                                animatedVisibilityScope = animatedContentScope
                                            ),
                                        album = item,
                                        onItemClick = onAlbumClick,
                                        onTogglePinClick = onAlbumLongClick,
                                        onMoveAlbumToTrash = {
                                            onMoveAlbumToTrash(trashResult, it)
                                        },
                                        onToggleIgnoreClick = onIgnoreAlbum,
                                        onToggleLockClick = onLockAlbum,
                                        onAddToGroup = onAddToGroup,
                                        onMoveToSection = onMoveToSection,
                                        onToggleMergeSubfolders = onToggleMergeSubfolders,
                                        onMergeParentSubfolders = onMergeParentSubfolders,
                                        onToggleMergedSubfolderDisplayMode = onToggleMergedSubfolderDisplayMode,
                                        isMergedSubfolder = item.id in mergedSubfolderIds,
                                        isSubGallery = item.id in subGalleryIds
                                    )
                                }
                            }
                            items(
                                items = albumsState.value.collections,
                                key = { "collection_${it.collection.id}" }
                            ) { collectionWithCount ->
                                CollectionComponent(
                                    modifier = Modifier
                                        .pinchItem(key = "collection_${collectionWithCount.collection.id}")
                                        .animateItem(),
                                    collectionWithCount = collectionWithCount,
                                    onItemClick = onCollectionClick,
                                    onRename = onCollectionRename,
                                    onDelete = onCollectionDelete,
                                    onTogglePin = onCollectionTogglePin,
                                    onEditAlbums = onCollectionEditAlbums
                                )
                            }
                            item(
                                key = "createCollection"
                            ) {
                                AnimatedVisibility(
                                    visible = albumsState.value.albums.isNotEmpty(),
                                    enter = enterAnimation,
                                    exit = exitAnimation
                                ) {
                                    CreateCollectionComponent(
                                        modifier = Modifier
                                            .pinchItem(key = "createCollection")
                                            .animateItem(),
                                        onClick = onCreateCollection
                                    )
                                }
                            }
                            // Dedicated cloud section, grouped per provider with its logo.
                            cloudProviderGroups.forEach { (providerName, cloudAlbums) ->
                                item(
                                    span = { GridItemSpan(maxLineSpan) },
                                    key = "cloud_header_$providerName"
                                ) {
                                    CloudAlbumSectionHeader(
                                        modifier = Modifier.animateItem(),
                                        providerName = providerName,
                                        count = cloudAlbums.size
                                    )
                                }
                                items(
                                    items = cloudAlbums,
                                    key = { "cloud_${it.id}" }
                                ) { item ->
                                    val trashResult = rememberActivityResult()
                                    with(sharedTransitionScope) {
                                        AlbumComponent(
                                            modifier = Modifier
                                                .pinchItem(key = "cloud_${item.id}")
                                                .animateItem(),
                                            thumbnailModifier = Modifier
                                                .mediaSharedElement(
                                                    album = item,
                                                    animatedVisibilityScope = animatedContentScope
                                                ),
                                            album = item,
                                            onItemClick = onAlbumClick,
                                            onTogglePinClick = onAlbumLongClick,
                                            onMoveAlbumToTrash = {
                                                onMoveAlbumToTrash(trashResult, it)
                                            },
                                            onToggleIgnoreClick = onIgnoreAlbum,
                                            isMergedSubfolder = false
                                        )
                                    }
                                }
                            }
                            // Media type albums (Videos/Photos/GIFs/Raw) sit at the very
                            // bottom, below every real and cloud album.
                            item(
                                span = { GridItemSpan(maxLineSpan) },
                                key = "mediaTypesHeader"
                            ) {
                                AnimatedVisibility(
                                    visible = mediaTypeAlbums.isNotEmpty(),
                                    enter = enterAnimation,
                                    exit = exitAnimation
                                ) {
                                    Text(
                                        modifier = Modifier
                                            .pinchItem(key = "mediaTypesHeader")
                                            .padding(horizontal = 8.dp)
                                            .padding(vertical = 24.dp),
                                        text = stringResource(R.string.media_type_albums_section),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            items(
                                items = mediaTypeAlbums,
                                key = { item -> "mediatype_${item.id}" }
                            ) { item ->
                                AlbumComponent(
                                    modifier = Modifier
                                        .pinchItem(key = "mediatype_${item.id}")
                                        .animateItem(),
                                    album = item,
                                    onItemClick = onAlbumClick,
                                    isMergedSubfolder = false
                                )
                            }

                            item(
                                span = { GridItemSpan(maxLineSpan) },
                                key = "albumDetails"
                            ) {
                                AnimatedVisibility(
                                    visible = mediaState.value.media.isNotEmpty() && albumsState.value.albums.isNotEmpty(),
                                    enter = enterAnimation,
                                    exit = exitAnimation
                                ) {
                                    Text(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pinchItem(key = "albumDetails")
                                            .padding(horizontal = 8.dp)
                                            .padding(vertical = 24.dp),
                                        text = stringResource(
                                            R.string.images_videos,
                                            mediaState.value.media.size
                                        ),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            item(
                                span = { GridItemSpan(maxLineSpan) },
                                key = "emptyAlbums"
                            ) {
                                AnimatedVisibility(
                                    visible = albumsState.value.albums.isEmpty() && albumsState.value.error.isEmpty() && !albumsState.value.isLoading,
                                    enter = enterAnimation,
                                    exit = exitAnimation
                                ) {
                                    EmptyAlbum()
                                }
                            }

                            item(
                                span = { GridItemSpan(maxLineSpan) },
                                key = "loadingAlbums"
                            ) {
                                AnimatedVisibility(
                                    visible = albumsState.value.isLoading,
                                    enter = enterAnimation,
                                    exit = exitAnimation
                                ) {
                                    LoadingAlbum()
                                }
                            }
                        }
                    }
                }
            }

            Settings.Album.ViewType.LIST -> {
                with(sharedTransitionScope) {
                    LaunchedEffect(listState.isScrollInProgress) {
                        isScrolling.value = listState.isScrollInProgress
                    }
                    val bottomBarInset = rememberBottomBarInset(innerPaddingValues)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .hazeSource(LocalHazeState.current)
                            .padding(horizontal = 8.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = innerPaddingValues.calculateTopPadding(),
                            bottom = bottomBarInset + 16.dp + 64.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item("pinnedAlbums") {
                            AnimatedVisibility(
                                visible = albumsState.value.albumsPinned.isNotEmpty(),
                                enter = enterAnimation,
                                exit = exitAnimation
                            ) {
                                if (pinnedAlbumsAsGrid) {
                                    Text(
                                        modifier = Modifier
                                            .padding(horizontal = 8.dp)
                                            .padding(vertical = 24.dp),
                                        text = stringResource(R.string.pinned_albums_title),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else {
                                    Column {
                                        Text(
                                            modifier = Modifier
                                                .padding(horizontal = 8.dp)
                                                .padding(vertical = 24.dp),
                                            text = stringResource(R.string.pinned_albums_title),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        CarouselPinnedAlbums(
                                            albumList = albumsState.value.albumsPinned,
                                            onAlbumClick = onAlbumClick,
                                            onAlbumLongClick = onAlbumLongClick,
                                            mergedSubfolderIds = mergedSubfolderIds,
                                            subGalleryIds = subGalleryIds,
                                            onToggleMergeSubfolders = onToggleMergeSubfolders,
                                            onToggleMergedSubfolderDisplayMode = onToggleMergedSubfolderDisplayMode
                                        )
                                    }
                                }
                            }
                        }

                        if (pinnedAlbumsAsGrid) {
                            items(
                                items = albumsState.value.albumsPinned,
                                key = { item -> "pinned_list_${item.id}" }
                            ) { item ->
                                val trashResult = rememberActivityResult()
                                AlbumRowComponent(
                                    modifier = Modifier.animateItem(),
                                    album = item,
                                    onItemClick = onAlbumClick,
                                    onTogglePinClick = onAlbumLongClick,
                                    onMoveAlbumToTrash = {
                                        onMoveAlbumToTrash(trashResult, it)
                                    },
                                    onToggleIgnoreClick = onIgnoreAlbum,
                                    onToggleLockClick = onLockAlbum,
                                    onAddToGroup = onAddToGroup,
                                    onMoveToSection = onMoveToSection,
                                    onToggleMergeSubfolders = onToggleMergeSubfolders,
                                    onMergeParentSubfolders = onMergeParentSubfolders,
                                    onToggleMergedSubfolderDisplayMode = onToggleMergedSubfolderDisplayMode,
                                    isMergedSubfolder = item.id in mergedSubfolderIds,
                                    isSubGallery = item.id in subGalleryIds
                                )
                            }
                        }

                        item("filterButton") {
                            AnimatedVisibility(
                                visible = albumsState.value.albumsUnpinned.isNotEmpty() || albumsState.value.albumSections.isNotEmpty(),
                                enter = enterAnimation,
                                exit = exitAnimation
                            ) {
                                FilterButton(
                                    modifier = Modifier,
                                    filterOptions = filterOptions.toTypedArray(),
                                    viewType = viewType,
                                    onViewTypeChange = { viewType = it }
                                )
                            }
                        }

                        items(
                            items = albumsState.value.albumGroups,
                            key = { group -> "group_${group.group.id}" }
                        ) { group ->
                            AlbumGroupRowComponent(
                                modifier = Modifier.animateItem(),
                                groupWithAlbums = group,
                                onGroupClick = onGroupClick,
                                onRenameGroup = onRenameGroup,
                                onDeleteGroup = onDeleteGroup,
                                onEditGroup = onEditGroup
                            )
                        }

                        // Album Sections (when enabled) - LIST view
                        albumsState.value.albumSections.forEach { sectionWithAlbums ->
                            item(key = "section_header_${sectionWithAlbums.section.id}") {
                                AlbumSectionHeader(
                                    modifier = Modifier.animateItem(),
                                    sectionWithAlbums = sectionWithAlbums,
                                    onToggleExpanded = { expanded ->
                                        onToggleSectionExpanded(sectionWithAlbums, expanded)
                                    }
                                )
                            }
                            if (sectionWithAlbums.section.isExpanded) {
                                items(
                                    items = sectionWithAlbums.albums,
                                    key = { item -> "section_${sectionWithAlbums.section.id}_${item.id}" }
                                ) { item ->
                                    val trashResult = rememberActivityResult()
                                    with(sharedTransitionScope) {
                                        AlbumRowComponent(
                                            modifier = Modifier.animateItem(),
                                            thumbnailModifier = Modifier
                                                .mediaSharedElement(
                                                    album = item,
                                                    animatedVisibilityScope = animatedContentScope
                                                ),
                                            album = item,
                                            onItemClick = onAlbumClick,
                                            onTogglePinClick = onAlbumLongClick,
                                            onMoveAlbumToTrash = {
                                                onMoveAlbumToTrash(trashResult, it)
                                            },
                                            onToggleIgnoreClick = onIgnoreAlbum,
                                            onToggleLockClick = onLockAlbum,
                                            onAddToGroup = onAddToGroup,
                                            onMoveToSection = onMoveToSection,
                                            onToggleMergeSubfolders = onToggleMergeSubfolders,
                                            onMergeParentSubfolders = onMergeParentSubfolders,
                                            onToggleMergedSubfolderDisplayMode = onToggleMergedSubfolderDisplayMode,
                                            isMergedSubfolder = item.id in mergedSubfolderIds,
                                            isSubGallery = item.id in subGalleryIds
                                        )
                                    }
                                }
                            }
                        }

                        // Unpinned albums (flat list, when sections are disabled) - LIST view
                        items(
                            items = albumsState.value.albumsUnpinned,
                            key = { "album_list_${it.id}" }
                        ) { item ->
                            val trashResult = rememberActivityResult()
                            with(sharedTransitionScope) {
                                AlbumRowComponent(
                                    modifier = Modifier
                                        .animateItem(),
                                    thumbnailModifier = Modifier
                                        .mediaSharedElement(
                                            album = item,
                                            animatedVisibilityScope = animatedContentScope
                                        ),
                                    album = item,
                                    onItemClick = onAlbumClick,
                                    onTogglePinClick = onAlbumLongClick,
                                    onMoveAlbumToTrash = {
                                        onMoveAlbumToTrash(trashResult, it)
                                    },
                                    onToggleIgnoreClick = onIgnoreAlbum,
                                    onToggleLockClick = onLockAlbum,
                                    onAddToGroup = onAddToGroup,
                                    onMoveToSection = onMoveToSection,
                                    onToggleMergeSubfolders = onToggleMergeSubfolders,
                                    onMergeParentSubfolders = onMergeParentSubfolders,
                                    onToggleMergedSubfolderDisplayMode = onToggleMergedSubfolderDisplayMode,
                                    isMergedSubfolder = item.id in mergedSubfolderIds,
                                    isSubGallery = item.id in subGalleryIds
                                )
                            }
                        }

                        items(
                            items = albumsState.value.collections,
                            key = { "collection_list_${it.collection.id}" }
                        ) { collectionWithCount ->
                            CollectionRowComponent(
                                modifier = Modifier.animateItem(),
                                collectionWithCount = collectionWithCount,
                                onItemClick = onCollectionClick,
                                onRename = onCollectionRename,
                                onDelete = onCollectionDelete,
                                onTogglePin = onCollectionTogglePin,
                                onEditAlbums = onCollectionEditAlbums
                            )
                        }
                        item("createCollection_list") {
                            AnimatedVisibility(
                                visible = albumsState.value.albums.isNotEmpty(),
                                enter = enterAnimation,
                                exit = exitAnimation
                            ) {
                                CreateCollectionRowComponent(
                                    modifier = Modifier.animateItem(),
                                    onClick = onCreateCollection
                                )
                            }
                        }
                        // Dedicated cloud section, grouped per provider with its logo.
                        cloudProviderGroups.forEach { (providerName, cloudAlbums) ->
                            item(key = "cloud_header_list_$providerName") {
                                CloudAlbumSectionHeader(
                                    modifier = Modifier.animateItem(),
                                    providerName = providerName,
                                    count = cloudAlbums.size
                                )
                            }
                            items(
                                items = cloudAlbums,
                                key = { "cloud_list_${it.id}" }
                            ) { item ->
                                val trashResult = rememberActivityResult()
                                with(sharedTransitionScope) {
                                    AlbumRowComponent(
                                        modifier = Modifier.animateItem(),
                                        thumbnailModifier = Modifier
                                            .mediaSharedElement(
                                                album = item,
                                                animatedVisibilityScope = animatedContentScope
                                            ),
                                        album = item,
                                        onItemClick = onAlbumClick,
                                        onTogglePinClick = onAlbumLongClick,
                                        onMoveAlbumToTrash = {
                                            onMoveAlbumToTrash(trashResult, it)
                                        },
                                        onToggleIgnoreClick = onIgnoreAlbum,
                                        isMergedSubfolder = false
                                    )
                                }
                            }
                        }
                        // Media type albums (Videos/Photos/GIFs/Raw) sit at the very
                        // bottom, below every real and cloud album.
                        item("mediaTypesHeader") {
                            AnimatedVisibility(
                                visible = mediaTypeAlbums.isNotEmpty(),
                                enter = enterAnimation,
                                exit = exitAnimation
                            ) {
                                Text(
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp)
                                        .padding(vertical = 24.dp),
                                    text = stringResource(R.string.media_type_albums_section),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        items(
                            items = mediaTypeAlbums,
                            key = { item -> "mediatype_list_${item.id}" }
                        ) { item ->
                            AlbumRowComponent(
                                modifier = Modifier.animateItem(),
                                album = item,
                                onItemClick = onAlbumClick,
                                isMergedSubfolder = false
                            )
                        }

                        item(key = "albumDetails") {
                            AnimatedVisibility(
                                visible = mediaState.value.media.isNotEmpty() && albumsState.value.albums.isNotEmpty(),
                                enter = enterAnimation,
                                exit = exitAnimation
                            ) {
                                Text(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp)
                                        .padding(vertical = 24.dp),
                                    text = stringResource(
                                        R.string.images_videos,
                                        mediaState.value.media.size
                                    ),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        item(key = "emptyAlbums") {
                            AnimatedVisibility(
                                visible = albumsState.value.albums.isEmpty() && albumsState.value.error.isEmpty() && !albumsState.value.isLoading,
                                enter = enterAnimation,
                                exit = exitAnimation
                            ) {
                                EmptyAlbum()
                            }
                        }

                        item(key = "loadingAlbums") {
                            AnimatedVisibility(
                                visible = albumsState.value.isLoading,
                                enter = enterAnimation,
                                exit = exitAnimation
                            ) {
                                LoadingAlbum()
                            }
                        }
                    }
                }
            }
        }
        }
    }
}
}

/**
 * Header for the dedicated cloud albums section, showing the provider's brand logo
 * (falling back to a generic cloud glyph) and its display name.
 */
@Composable
private fun CloudAlbumSectionHeader(
    modifier: Modifier = Modifier,
    providerName: String,
    count: Int
) {
    val providerType = remember(providerName) {
        try { ProviderType.valueOf(providerName) } catch (_: Exception) { null }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .padding(top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (providerType != null) {
            ProviderBrandIcon(
                providerType = providerType,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Cloud,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = providerType?.displayName ?: providerName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = stringResource(R.string.n_albums, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}