/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.albums.ui

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Constants.albumCellsList
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.ScrollToTopHandler
import com.dot.gallery.core.animateOrJumpToTop
import com.dot.gallery.core.image.thumbnail.LocalThumbnailMotion
import com.dot.gallery.core.image.thumbnail.rememberThumbnailMotionState
import com.dot.gallery.core.presentation.components.EmptyAlbum
import com.dot.gallery.core.presentation.components.Error
import com.dot.gallery.core.presentation.components.FilterButton
import com.dot.gallery.core.presentation.components.FilterKind
import com.dot.gallery.core.presentation.components.FilterOption
import com.dot.gallery.core.presentation.components.FloatingTopBarScrim
import com.dot.gallery.core.presentation.components.TopBarScrimScrollFade
import com.dot.gallery.core.presentation.components.topScrimVisibility
import com.dot.gallery.core.presentation.components.GridPinchZoomLayout
import com.dot.gallery.core.presentation.components.LoadingAlbumContent
import com.dot.gallery.core.presentation.components.MediaContentState
import com.dot.gallery.core.presentation.components.MediaTypeAlbum
import com.dot.gallery.core.presentation.components.mediaContentState
import com.dot.gallery.core.presentation.components.rememberGridPinchZoomState
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.presentation.util.mediaSharedElement
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.core.ui.util.supportsLoadingShimmer
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.AlbumGroupWithAlbums
import com.dot.gallery.domain.model.AlbumSectionWithAlbums
import com.dot.gallery.domain.model.AlbumViewType
import com.dot.gallery.domain.model.CollectionWithCount
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.MergedSubfolderAlbum
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.domain.util.MediaOrder
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.feature.albums.ui.collections.CollectionComponent
import com.dot.gallery.feature.albums.ui.collections.CollectionRowComponent
import com.dot.gallery.feature.albums.ui.collections.CollectionThumbnailModel
import com.dot.gallery.feature.albums.ui.collections.CreateCollectionComponent
import com.dot.gallery.feature.albums.ui.collections.CreateCollectionRowComponent
import com.dot.gallery.feature.albums.ui.components.AlbumComponent
import com.dot.gallery.feature.albums.ui.components.AlbumGroupComponent
import com.dot.gallery.feature.albums.ui.components.AlbumGroupRowComponent
import com.dot.gallery.feature.albums.ui.components.AlbumRowComponent
import com.dot.gallery.feature.albums.ui.components.AlbumSectionHeader
import com.dot.gallery.feature.albums.ui.components.CarouselPinnedAlbums
import com.dot.gallery.feature.albums.ui.components.CloudAlbumSectionHeader
import com.dot.gallery.feature.albums.ui.components.ProviderIconSlot
import com.dot.gallery.feature.albums.ui.util.albumScreenHorizontalInsets
import com.dot.gallery.feature.albums.ui.util.rememberBottomBarInset
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.albums.generated.resources.Res
import com.dot.gallery.feature.albums.generated.resources.images_videos
import com.dot.gallery.feature.albums.generated.resources.media_type_albums_section
import com.dot.gallery.feature.albums.generated.resources.pinned_albums_title

/**
 * Pure-Compose port of the Albums tab.
 *
 * Platform seams:
 * - [topBar] supplies the app search bar slot (MainSearchBar + nav actions).
 * - [scrollToTopRoute] keys [ScrollToTopHandler] against the app's route name.
 * - [startupGate] defers the media-type card data flow until first content.
 * - [startupReadyEffect] lets the app hook its `StartupContentEffect` tracker.
 * - [supportsTrash] gates recoverable-trash wording (`SdkCompat.supportsTrash`).
 * - [thumbnailActions] wires the "custom album cover" picker/handler flow.
 * - [providerIcon] renders provider brand logos for the cloud album section.
 * - [onMoveAlbumToTrash] receives an album to delete; the app owns the
 *   ActivityResult launcher used for the MediaStore delete/trash request.
 * - [gridSizeIndex] is the persisted album grid size index (the app keeps the
 *   orientation-aware SharedPreferences-backed state).
 * - [collectionThumbnailModel] maps a collection's thumbnail media id to an
 *   image-loadable model (the app resolves it to a MediaStore content URI).
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AlbumsContent(
    paddingValues: PaddingValues,
    filterOptions: SnapshotStateList<FilterOption>,
    isScrolling: MutableState<Boolean>,
    gridSizeIndex: MutableState<Int>,
    scrollToTopRoute: String,
    topBar: @Composable () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onAlbumLongClick: (Album) -> Unit,
    onMoveAlbumToTrash: (Album) -> Unit,
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
    startupGate: AlbumsStartupGate? = null,
    startupReadyEffect: @Composable (ready: Boolean) -> Unit = {},
    supportsTrash: Boolean = true,
    thumbnailActions: AlbumThumbnailActions? = null,
    providerIcon: ProviderIconSlot? = null,
    collectionThumbnailModel: CollectionThumbnailModel = { null },
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    val distributor = LocalMediaDistributor.current
    val mergedSubfolderAlbums by distributor.mergedSubfolderAlbumsFlow.collectAsState()
    val mergedSubfolderIds = remember(mergedSubfolderAlbums) {
        mergedSubfolderAlbums.mapTo(HashSet()) { it.id }
    }
    val subGalleryIds = remember(mergedSubfolderAlbums) {
        mergedSubfolderAlbums.filter {
            it.displayMode == MergedSubfolderAlbum.DISPLAY_MODE_SUB_GALLERY
        }.mapTo(HashSet()) { it.id }
    }
    val timelineForTypeCards = remember(startupGate, distributor) {
        if (startupGate == null) distributor.timelineMediaFlow
        else flow {
            startupGate.awaitFirstContent()
            emitAll(distributor.timelineMediaFlow)
        }
    }
    val mediaState = timelineForTypeCards.collectAsState(
        context = Dispatchers.Default,
        initial = MediaState<UriMedia>()
    )
    val albumsState = distributor.albumsFlow.collectAsState()
    startupReadyEffect(!albumsState.value.isLoading)

    // Cloud albums grouped by provider (e.g. SMB, IMMICH) for their dedicated section.
    val cloudProviderGroups = remember(albumsState.value.albumsCloud) {
        albumsState.value.albumsCloud
            .groupBy { it.relativePath.removePrefix("cloud/").substringBefore("/") }
            .toList()
    }

    // Virtual "albums" grouping the whole library by media type (Videos/Photos/GIFs/Raw).
    // Derived from the already-collected timeline media; empty types are dropped.
    val mediaTypeLabels = MediaTypeAlbum.entries.associateWith { stringResource(it.labelRes) }
    val showMediaTypeAlbums by rememberPreference(SettingsKeys.showMediaTypeAlbums)
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

    var lastCellIndex by gridSizeIndex

    val pinchState = rememberGridPinchZoomState(
        cellsList = albumCellsList,
        initialCellsIndex = lastCellIndex
    )
    val listState = rememberLazyListState()
    var viewType by rememberPreference(SettingsKeys.albumViewType)
    val pinnedAlbumsAsGrid by rememberPreference(SettingsKeys.pinnedAlbumsAsGrid)
    // Phase 3 (#1076): album covers render through the shared RenderImage, so publishing the
    // active view's debounced scroll-motion makes them use the cheap MOTION tier while scrolling.
    val thumbnailMotion = rememberThumbnailMotionState({
        when (viewType) {
            AlbumViewType.LIST -> listState.isScrollInProgress
            else -> pinchState.gridState.isScrollInProgress
        }
    })

    LaunchedEffect(pinchState.isZooming) {
        lastCellIndex = albumCellsList.indexOf(pinchState.currentCells)
    }
    val lastSort by rememberPreference(SettingsKeys.albumLastSort)
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
    ScrollToTopHandler(scrollToTopRoute) {
        when (viewType) {
            AlbumViewType.GRID -> pinchState.gridState.animateOrJumpToTop()
            AlbumViewType.LIST -> listState.animateOrJumpToTop()
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
        topBar = topBar
    ) { innerPaddingValues ->
        // hazeSource stays attached in every content state so the search bar keeps its
        // frosted effect when the grid is empty/loading/error (#areas.isEmpty = no draw).
        Box(modifier = Modifier.fillMaxSize().hazeSource(LocalHazeState.current)) {
        when (mediaContentState(
            isLoading = albumsState.value.isLoading,
            error = albumsState.value.error,
            isEmpty = albumsState.value.albums.isEmpty() && albumsState.value.collections.isEmpty(),
        )) {
            MediaContentState.ERROR -> Error(
                modifier = Modifier.padding(innerPaddingValues),
                errorMessage = albumsState.value.error,
            )
            MediaContentState.LOADING -> LoadingAlbum(
                grid = albumCellsList.size - lastCellIndex
            )
            MediaContentState.EMPTY -> EmptyAlbum()
            MediaContentState.CONTENT -> CompositionLocalProvider(LocalThumbnailMotion provides thumbnailMotion) {
        val hideSearchBarSetting by rememberPreference(SettingsKeys.autoHideSearchBar)
        val topBarScrimZone by animateDpAsState(
            targetValue = if (!isScrolling.value || !hideSearchBarSetting) {
                SearchBarDefaults.InputFieldHeight + paddingValues.calculateTopPadding() + 8.dp
            } else paddingValues.calculateTopPadding(),
            label = "topBarScrimZone"
        )
        Box(modifier = Modifier.fillMaxSize()) {
        when (viewType) {
            AlbumViewType.GRID -> {
                with(sharedTransitionScope) {
                    GridPinchZoomLayout(
                        state = pinchState,
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
                                            text = stringResource(Res.string.pinned_albums_title),
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
                                                text = stringResource(Res.string.pinned_albums_title),
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
                                    AlbumComponent(
                                        modifier = Modifier
                                            .pinchItem(key = "pinned_${item.id}")
                                            .animateItem(),
                                        album = item,
                                        onItemClick = onAlbumClick,
                                        onTogglePinClick = onAlbumLongClick,
                                        onMoveAlbumToTrash = onMoveAlbumToTrash,
                                        onToggleIgnoreClick = onIgnoreAlbum,
                                        onToggleLockClick = onLockAlbum,
                                        onAddToGroup = onAddToGroup,
                                        onMoveToSection = onMoveToSection,
                                        onToggleMergeSubfolders = onToggleMergeSubfolders,
                                        onMergeParentSubfolders = onMergeParentSubfolders,
                                        onToggleMergedSubfolderDisplayMode = onToggleMergedSubfolderDisplayMode,
                                        isMergedSubfolder = item.id in mergedSubfolderIds,
                                        isSubGallery = item.id in subGalleryIds,
                                        supportsTrash = supportsTrash,
                                        thumbnailActions = thumbnailActions,
                                        providerIcon = providerIcon,
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
                                            onMoveAlbumToTrash = onMoveAlbumToTrash,
                                            onToggleIgnoreClick = onIgnoreAlbum,
                                            onToggleLockClick = onLockAlbum,
                                            onAddToGroup = onAddToGroup,
                                            onMoveToSection = onMoveToSection,
                                            onToggleMergeSubfolders = onToggleMergeSubfolders,
                                            onMergeParentSubfolders = onMergeParentSubfolders,
                                            onToggleMergedSubfolderDisplayMode = onToggleMergedSubfolderDisplayMode,
                                            isMergedSubfolder = item.id in mergedSubfolderIds,
                                            isSubGallery = item.id in subGalleryIds,
                                            supportsTrash = supportsTrash,
                                            thumbnailActions = thumbnailActions,
                                            providerIcon = providerIcon,
                                        )
                                    }
                                }
                            }
                            // Unpinned albums (flat list, when sections are disabled)
                            items(
                                items = albumsState.value.albumsUnpinned,
                                key = { item -> "album_${item.id}" }
                            ) { item ->
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
                                    onMoveAlbumToTrash = onMoveAlbumToTrash,
                                    onToggleIgnoreClick = onIgnoreAlbum,
                                    onToggleLockClick = onLockAlbum,
                                    onAddToGroup = onAddToGroup,
                                    onMoveToSection = onMoveToSection,
                                    onToggleMergeSubfolders = onToggleMergeSubfolders,
                                    onMergeParentSubfolders = onMergeParentSubfolders,
                                    onToggleMergedSubfolderDisplayMode = onToggleMergedSubfolderDisplayMode,
                                    isMergedSubfolder = item.id in mergedSubfolderIds,
                                    isSubGallery = item.id in subGalleryIds,
                                    supportsTrash = supportsTrash,
                                    thumbnailActions = thumbnailActions,
                                    providerIcon = providerIcon,
                                )
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
                                    onEditAlbums = onCollectionEditAlbums,
                                    collectionThumbnailModel = collectionThumbnailModel
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
                                        count = cloudAlbums.size,
                                        providerIcon = providerIcon,
                                    )
                                }
                                items(
                                    items = cloudAlbums,
                                    key = { "cloud_${it.id}" }
                                ) { item ->
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
                                        onMoveAlbumToTrash = onMoveAlbumToTrash,
                                        onToggleIgnoreClick = onIgnoreAlbum,
                                        isMergedSubfolder = false,
                                        supportsTrash = supportsTrash,
                                        thumbnailActions = thumbnailActions,
                                        providerIcon = providerIcon,
                                    )
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
                                        text = stringResource(Res.string.media_type_albums_section),
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
                                    isMergedSubfolder = false,
                                    supportsTrash = supportsTrash,
                                    thumbnailActions = thumbnailActions,
                                    providerIcon = providerIcon,
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
                                            Res.string.images_videos,
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
                                    LoadingAlbum(grid = albumCellsList.size - lastCellIndex)
                                }
                            }
                        }
                    }
                }
            }

            AlbumViewType.LIST -> {
                with(sharedTransitionScope) {
                    LaunchedEffect(listState.isScrollInProgress) {
                        isScrolling.value = listState.isScrollInProgress
                    }
                    val bottomBarInset = rememberBottomBarInset(innerPaddingValues)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
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
                                        text = stringResource(Res.string.pinned_albums_title),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else {
                                    Column {
                                        Text(
                                            modifier = Modifier
                                                .padding(horizontal = 8.dp)
                                                .padding(vertical = 24.dp),
                                            text = stringResource(Res.string.pinned_albums_title),
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
                                AlbumRowComponent(
                                    modifier = Modifier.animateItem(),
                                    album = item,
                                    onItemClick = onAlbumClick,
                                    onTogglePinClick = onAlbumLongClick,
                                    onMoveAlbumToTrash = onMoveAlbumToTrash,
                                    onToggleIgnoreClick = onIgnoreAlbum,
                                    onToggleLockClick = onLockAlbum,
                                    onAddToGroup = onAddToGroup,
                                    onMoveToSection = onMoveToSection,
                                    onToggleMergeSubfolders = onToggleMergeSubfolders,
                                    onMergeParentSubfolders = onMergeParentSubfolders,
                                    onToggleMergedSubfolderDisplayMode = onToggleMergedSubfolderDisplayMode,
                                    isMergedSubfolder = item.id in mergedSubfolderIds,
                                    isSubGallery = item.id in subGalleryIds,
                                    supportsTrash = supportsTrash,
                                    thumbnailActions = thumbnailActions,
                                    providerIcon = providerIcon,
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
                                        onMoveAlbumToTrash = onMoveAlbumToTrash,
                                        onToggleIgnoreClick = onIgnoreAlbum,
                                        onToggleLockClick = onLockAlbum,
                                        onAddToGroup = onAddToGroup,
                                        onMoveToSection = onMoveToSection,
                                        onToggleMergeSubfolders = onToggleMergeSubfolders,
                                        onMergeParentSubfolders = onMergeParentSubfolders,
                                        onToggleMergedSubfolderDisplayMode = onToggleMergedSubfolderDisplayMode,
                                        isMergedSubfolder = item.id in mergedSubfolderIds,
                                        isSubGallery = item.id in subGalleryIds,
                                        supportsTrash = supportsTrash,
                                        thumbnailActions = thumbnailActions,
                                        providerIcon = providerIcon,
                                    )
                                }
                            }
                        }

                        // Unpinned albums (flat list, when sections are disabled) - LIST view
                        items(
                            items = albumsState.value.albumsUnpinned,
                            key = { "album_list_${it.id}" }
                        ) { item ->
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
                                onMoveAlbumToTrash = onMoveAlbumToTrash,
                                onToggleIgnoreClick = onIgnoreAlbum,
                                onToggleLockClick = onLockAlbum,
                                onAddToGroup = onAddToGroup,
                                onMoveToSection = onMoveToSection,
                                onToggleMergeSubfolders = onToggleMergeSubfolders,
                                onMergeParentSubfolders = onMergeParentSubfolders,
                                onToggleMergedSubfolderDisplayMode = onToggleMergedSubfolderDisplayMode,
                                isMergedSubfolder = item.id in mergedSubfolderIds,
                                isSubGallery = item.id in subGalleryIds,
                                supportsTrash = supportsTrash,
                                thumbnailActions = thumbnailActions,
                                providerIcon = providerIcon,
                            )
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
                                onEditAlbums = onCollectionEditAlbums,
                                collectionThumbnailModel = collectionThumbnailModel
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
                                    count = cloudAlbums.size,
                                    providerIcon = providerIcon,
                                )
                            }
                            items(
                                items = cloudAlbums,
                                key = { "cloud_list_${it.id}" }
                            ) { item ->
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
                                    onMoveAlbumToTrash = onMoveAlbumToTrash,
                                    onToggleIgnoreClick = onIgnoreAlbum,
                                    isMergedSubfolder = false,
                                    supportsTrash = supportsTrash,
                                    thumbnailActions = thumbnailActions,
                                    providerIcon = providerIcon,
                                )
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
                                    text = stringResource(Res.string.media_type_albums_section),
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
                                isMergedSubfolder = false,
                                supportsTrash = supportsTrash,
                                thumbnailActions = thumbnailActions,
                                providerIcon = providerIcon,
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
                                        Res.string.images_videos,
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
                                LoadingAlbum(grid = albumCellsList.size - lastCellIndex)
                            }
                        }
                    }
                }
            }
        }
        FloatingTopBarScrim(
            barZoneHeight = topBarScrimZone,
            visibility = {
                topScrimVisibility(
                    listState.firstVisibleItemIndex,
                    listState.firstVisibleItemScrollOffset,
                    TopBarScrimScrollFade.toPx(),
                )
            },
        )
        }
    }
        }
    }
}
}

@Composable
private fun LoadingAlbum(
    grid: Int,
    modifier: Modifier = Modifier,
) = LoadingAlbumContent(
    grid = grid,
    canShimmer = supportsLoadingShimmer,
    modifier = modifier,
)
