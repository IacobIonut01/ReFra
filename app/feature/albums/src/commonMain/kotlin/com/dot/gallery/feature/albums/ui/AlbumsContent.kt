/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.albums.ui

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
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
import com.dot.gallery.core.presentation.components.LocalGalleryPaneChrome
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
import com.dot.gallery.feature.albums.ui.components.AlbumComponent
import com.dot.gallery.feature.albums.ui.components.AlbumGroupComponent
import com.dot.gallery.feature.albums.ui.components.AlbumGroupRowComponent
import com.dot.gallery.feature.albums.ui.components.AlbumRowComponent
import com.dot.gallery.feature.albums.ui.components.AlbumSectionHeader
import com.dot.gallery.feature.albums.ui.components.CarouselPinnedAlbums
import com.dot.gallery.feature.albums.ui.components.CloudAlbumSectionHeader
import com.dot.gallery.feature.albums.ui.components.ProviderIconSlot
import com.dot.gallery.feature.albums.ui.util.AlbumArrangement
import com.dot.gallery.feature.albums.ui.util.AlbumsFilter
import com.dot.gallery.feature.albums.ui.util.AlbumsFilterKind
import com.dot.gallery.feature.albums.ui.util.KEY_ALBUMS_HEADING
import com.dot.gallery.feature.albums.ui.util.KEY_FILTER_BAR
import com.dot.gallery.feature.albums.ui.util.KEY_LIBRARY_HEADER
import com.dot.gallery.feature.albums.ui.util.KEY_PINNED_ALBUMS
import com.dot.gallery.feature.albums.ui.util.albumsFilterBarPinned
import com.dot.gallery.feature.albums.ui.util.bleedHorizontally
import com.dot.gallery.feature.albums.ui.util.availableAlbumFilters
import com.dot.gallery.feature.albums.ui.util.resolveAlbumFilter
import com.dot.gallery.feature.albums.ui.util.albumScreenHorizontalInsets
import com.dot.gallery.feature.albums.ui.util.rememberBottomBarInset
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.albums.generated.resources.Res
import com.dot.gallery.feature.albums.generated.resources.album_filter_all
import com.dot.gallery.feature.albums.generated.resources.album_filter_cloud
import com.dot.gallery.feature.albums.generated.resources.album_filter_collections
import com.dot.gallery.feature.albums.generated.resources.album_filter_device
import com.dot.gallery.feature.albums.generated.resources.albums_heading
import com.dot.gallery.feature.albums.generated.resources.albums_others_section
import com.dot.gallery.feature.albums.generated.resources.new_collection
import com.dot.gallery.feature.albums.generated.resources.images_videos
import com.dot.gallery.feature.albums.generated.resources.media_type_albums_section

/**
 * Pure-Compose port of the Albums tab.
 *
 * Platform seams:
 * - [topBar] supplies the app search bar slot (MainSearchBar + nav actions). It receives the
 *   callback that opens album arranging, or null while arranging is unavailable.
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
 * - [header] carries the Library content: Albums and Library are one tab, and this page owns the
 *   scroller because the album grid is the only unbounded part.
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
    topBar: @Composable (onArrange: (() -> Unit)?) -> Unit,
    /** Library shortcuts and sections, scrolled above the albums. Null when they sit in a side pane. */
    header: (@Composable () -> Unit)? = null,
    /** Keeps cards readable where the page is narrower than the window its saved column count suits. */
    minCellWidth: Dp? = null,
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
    val timelineMedia = mediaState.value.media
    // Scans the whole timeline, so it stays off the main thread.
    val mediaTypeAlbums by produceState(emptyList<Album>(), timelineMedia, showMediaTypeAlbums, mediaTypeLabels) {
        value = withContext(Dispatchers.Default) {
        if (!showMediaTypeAlbums) emptyList()
        else MediaTypeAlbum.entries.mapNotNull { type ->
            val matches = timelineMedia.filter { type.matches(it) }
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
    }

    val showPinnedAlbums by rememberPreference(SettingsKeys.showPinnedAlbums)
    val visiblePinned = if (showPinnedAlbums) albumsState.value.albumsPinned else emptyList()

    val availableFilters = remember(albumsState.value, cloudProviderGroups, mediaTypeAlbums) {
        availableAlbumFilters(
            hasDevice = albumsState.value.albumsUnpinned.isNotEmpty() ||
                albumsState.value.albumGroups.isNotEmpty(),
            sectionIds = albumsState.value.albumSections.map { it.section.id },
            hasCollections = albumsState.value.collections.isNotEmpty(),
            hasCloud = cloudProviderGroups.isNotEmpty(),
            hasMediaTypes = mediaTypeAlbums.isNotEmpty(),
        )
    }
    var selectedFilter by rememberSaveable(stateSaver = AlbumsFilter.Saver) { mutableStateOf(AlbumsFilter.All) }
    val activeFilter = resolveAlbumFilter(selectedFilter, availableFilters)
    val activeKind = activeFilter.kind
    val showAll = activeKind == AlbumsFilterKind.ALL
    val showDevice = showAll || activeKind == AlbumsFilterKind.DEVICE
    val showCollections = showAll || activeKind == AlbumsFilterKind.COLLECTIONS
    val showCloud = showAll || activeKind == AlbumsFilterKind.CLOUD
    val showMediaTypes = showAll || activeKind == AlbumsFilterKind.MEDIA_TYPES
    val visibleGroups = if (showDevice) albumsState.value.albumGroups else emptyList()
    // A chip already names its section, so that view drops the header and ignores collapsing.
    val showSectionHeaders = activeKind != AlbumsFilterKind.SECTION
    val visibleSections = remember(activeFilter, albumsState.value.albumSections) {
        when {
            showDevice -> albumsState.value.albumSections
            activeKind == AlbumsFilterKind.SECTION -> albumsState.value.albumSections
                .filter { it.section.id == activeFilter.sectionId }
                .map { it.copy(section = it.section.copy(isExpanded = true)) }
            else -> emptyList()
        }
    }
    val visibleCollections = if (showCollections) albumsState.value.collections else emptyList()
    val visibleCloudGroups = if (showCloud) cloudProviderGroups else emptyList()
    val visibleMediaTypes = if (showMediaTypes) mediaTypeAlbums else emptyList()
    val filterChips: (@Composable (bleed: Dp) -> Unit)? = remember(
        availableFilters, activeFilter, albumsState.value.albumSections
    ) {
        if (availableFilters.isEmpty()) null else {
            { bleed ->
                AlbumsFilterChips(
                    bleed = bleed,
                    filters = availableFilters,
                    sections = albumsState.value.albumSections,
                    selected = activeFilter,
                    onSelect = { selectedFilter = it },
                )
            }
        }
    }

    var lastCellIndex by gridSizeIndex

    val pinchState = rememberGridPinchZoomState(
        cellsList = albumCellsList,
        initialCellsIndex = lastCellIndex
    )
    val listState = rememberLazyListState()
    var viewType by rememberPreference(SettingsKeys.albumViewType)
    // album covers render through the shared RenderImage, so publishing the
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
    // Folding or unfolding keeps this composition, but each window size stores its own column count.
    LaunchedEffect(lastCellIndex) {
        if (!pinchState.isZooming) pinchState.updateCellsIndex(lastCellIndex)
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

    // Manual arrangement: arranged albums keep the stored order, the rest fall under
    // "Others" and follow the active sort.
    var customOrder by rememberPreference(SettingsKeys.albumCustomOrder)
    val arrangement = remember(albumsState.value.albumsUnpinned, customOrder) {
        AlbumArrangement.split(albumsState.value.albumsUnpinned, customOrder)
    }
    val visibleArranged = if (showDevice) arrangement.arranged else emptyList()
    val visibleOthers = if (showDevice) arrangement.others else emptyList()
    val showOthersHeader = arrangement.hasArrangement && visibleOthers.isNotEmpty()
    val canArrange = albumsState.value.albumSections.isEmpty() &&
            albumsState.value.albumsUnpinned.size > 1
    var arranging by rememberSaveable { mutableStateOf(false) }
    if (arranging && !canArrange) arranging = false

    // Re-tapping the Albums tab scrolls the active view back to the top.
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
    // In a rail or split layout the page sits in a tinted pane and must not paint over it.
    val pageColor = if (LocalGalleryPaneChrome.current) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else MaterialTheme.colorScheme.background
    Scaffold(
        modifier = Modifier
            .padding(horizontalInsets)
            .consumeWindowInsets(horizontalInsets),
        containerColor = pageColor,
        topBar = { topBar(if (canArrange && !arranging) ({ arranging = true }) else null) }
    ) { innerPaddingValues ->
        // hazeSource stays attached in every content state so the search bar keeps its
        // frosted effect when the grid is empty/loading/error (#areas.isEmpty = no draw).
        Box(modifier = Modifier.fillMaxSize().hazeSource(LocalHazeState.current)) {
        when (mediaContentState(
            isLoading = albumsState.value.isLoading,
            error = albumsState.value.error,
            isEmpty = albumsState.value.albums.isEmpty() && albumsState.value.collections.isEmpty(),
        )) {
            MediaContentState.ERROR -> HeaderAbove(header, innerPaddingValues) {
                Error(
                    modifier = if (header == null) Modifier.padding(innerPaddingValues) else Modifier,
                    errorMessage = albumsState.value.error,
                )
            }
            MediaContentState.LOADING -> HeaderAbove(header, innerPaddingValues) {
                LoadingAlbum(grid = albumCellsList.size - lastCellIndex)
            }
            MediaContentState.EMPTY -> HeaderAbove(header, innerPaddingValues) { EmptyAlbum() }
            MediaContentState.CONTENT -> CompositionLocalProvider(LocalThumbnailMotion provides thumbnailMotion) {
        Box(modifier = Modifier.fillMaxSize()) {
        if (arranging) {
            AlbumsArrangeContent(
                albums = arrangement.arranged + arrangement.others,
                viewType = viewType,
                gridCells = pinchState.currentCells,
                contentPadding = PaddingValues(
                    top = innerPaddingValues.calculateTopPadding(),
                    bottom = rememberBottomBarInset(innerPaddingValues) + 16.dp + 64.dp
                ),
                hasArrangement = arrangement.hasArrangement,
                mergedSubfolderIds = mergedSubfolderIds,
                subGalleryIds = subGalleryIds,
                providerIcon = providerIcon,
                onCancel = { arranging = false },
                onReset = {
                    customOrder = emptyList()
                    arranging = false
                },
                onDone = { draft, changed ->
                    if (changed) customOrder = AlbumArrangement.mergeOrder(customOrder, draft)
                    arranging = false
                },
            )
        } else when (viewType) {
            AlbumViewType.GRID -> {
                with(sharedTransitionScope) {
                    GridPinchZoomLayout(
                        state = pinchState,
                        indicatorTopPadding = innerPaddingValues.calculateTopPadding() + 16.dp,
                    ) {
                        LaunchedEffect(gridState) {
                            snapshotFlow { gridState.isScrollInProgress }.collect { isScrolling.value = it }
                        }
                        val bottomBarInset = rememberBottomBarInset(innerPaddingValues)
                        LazyVerticalGrid(
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            columns = minCellWidth?.let { minWidth ->
                                ReadableGridCells(albumCellsList.size - albumCellsList.indexOf(gridCells), minWidth)
                            } ?: gridCells,
                            contentPadding = PaddingValues(
                                start = PageSidePadding,
                                end = PageSidePadding,
                                top = innerPaddingValues.calculateTopPadding(),
                                bottom = bottomBarInset + 16.dp + 64.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (header != null) {
                                item(
                                    span = { GridItemSpan(maxLineSpan) },
                                    key = KEY_LIBRARY_HEADER
                                ) {
                                    Box(Modifier.pinchItem(key = KEY_LIBRARY_HEADER)) { header() }
                                }
                            }
                            item(
                                span = { GridItemSpan(maxLineSpan) },
                                key = KEY_ALBUMS_HEADING
                            ) {
                                AlbumsHeading(
                                    modifier = Modifier.pinchItem(key = KEY_ALBUMS_HEADING),
                                    albumCount = albumsState.value.albums.size,
                                    filterOptions = filterOptions,
                                    viewType = viewType,
                                    onViewTypeChange = { viewType = it },
                                    onCreateCollection = onCreateCollection,
                                )
                            }
                            if (visiblePinned.isNotEmpty()) item(
                                span = { GridItemSpan(maxLineSpan) },
                                key = KEY_PINNED_ALBUMS
                            ) {
                                AnimatedVisibility(
                                    visible = visiblePinned.isNotEmpty(),
                                    enter = enterAnimation,
                                    exit = exitAnimation
                                ) {
                                    CarouselPinnedAlbums(
                                        bleed = PageSidePadding,
                                        albumList = visiblePinned,
                                        onAlbumClick = onAlbumClick,
                                        onAlbumLongClick = onAlbumLongClick,
                                        mergedSubfolderIds = mergedSubfolderIds,
                                        subGalleryIds = subGalleryIds,
                                        onToggleMergeSubfolders = onToggleMergeSubfolders,
                                        onToggleMergedSubfolderDisplayMode = onToggleMergedSubfolderDisplayMode
                                    )
                                }
                            }
                            if (filterChips != null) {
                                item(
                                    span = { GridItemSpan(maxLineSpan) },
                                    key = KEY_FILTER_BAR
                                ) {
                                    Box(Modifier.pinchItem(key = KEY_FILTER_BAR)) { filterChips(PageSidePadding) }
                                }
                            }
                            items(
                                items = visibleGroups,
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
                            visibleSections.forEach { sectionWithAlbums ->
                                if (showSectionHeaders) item(
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
                            // Unpinned albums (flat list, when sections are disabled): the
                            // arranged block first, then the unarranged ones under "Others".
                            items(
                                items = visibleArranged,
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
                            if (showOthersHeader) {
                                item(
                                    span = { GridItemSpan(maxLineSpan) },
                                    key = "othersHeader"
                                ) {
                                    Text(
                                        modifier = Modifier
                                            .pinchItem(key = "othersHeader")
                                            .animateItem()
                                            .padding(horizontal = 8.dp)
                                            .padding(vertical = 24.dp),
                                        text = stringResource(Res.string.albums_others_section),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            items(
                                items = visibleOthers,
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
                                items = visibleCollections,
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
                            // Dedicated cloud section, grouped per provider with its logo.
                            visibleCloudGroups.forEach { (providerName, cloudAlbums) ->
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
                                    visible = visibleMediaTypes.isNotEmpty(),
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
                                items = visibleMediaTypes,
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
                    LaunchedEffect(listState) {
                        snapshotFlow { listState.isScrollInProgress }.collect { isScrolling.value = it }
                    }
                    val bottomBarInset = rememberBottomBarInset(innerPaddingValues)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = PageSidePadding,
                            end = PageSidePadding,
                            top = innerPaddingValues.calculateTopPadding(),
                            bottom = bottomBarInset + 16.dp + 64.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (header != null) {
                            item(KEY_LIBRARY_HEADER) { header() }
                        }
                        item(KEY_ALBUMS_HEADING) {
                            AlbumsHeading(
                                albumCount = albumsState.value.albums.size,
                                filterOptions = filterOptions,
                                viewType = viewType,
                                onViewTypeChange = { viewType = it },
                                onCreateCollection = onCreateCollection,
                            )
                        }
                        if (visiblePinned.isNotEmpty()) item(KEY_PINNED_ALBUMS) {
                            AnimatedVisibility(
                                visible = visiblePinned.isNotEmpty(),
                                enter = enterAnimation,
                                exit = exitAnimation
                            ) {
                                CarouselPinnedAlbums(
                                    bleed = PageSidePadding,
                                    albumList = visiblePinned,
                                    onAlbumClick = onAlbumClick,
                                    onAlbumLongClick = onAlbumLongClick,
                                    mergedSubfolderIds = mergedSubfolderIds,
                                    subGalleryIds = subGalleryIds,
                                    onToggleMergeSubfolders = onToggleMergeSubfolders,
                                    onToggleMergedSubfolderDisplayMode = onToggleMergedSubfolderDisplayMode
                                )
                            }
                        }


                        if (filterChips != null) {
                            item(KEY_FILTER_BAR) { filterChips(PageSidePadding) }
                        }

                        items(
                            items = visibleGroups,
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
                        visibleSections.forEach { sectionWithAlbums ->
                            if (showSectionHeaders) item(key = "section_header_${sectionWithAlbums.section.id}") {
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

                        // Unpinned albums (flat list, when sections are disabled) - LIST view:
                        // the arranged block first, then the unarranged ones under "Others".
                        items(
                            items = visibleArranged,
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

                        if (showOthersHeader) {
                            item(key = "othersHeader_list") {
                                Text(
                                    modifier = Modifier
                                        .animateItem()
                                        .padding(horizontal = 8.dp)
                                        .padding(vertical = 24.dp),
                                    text = stringResource(Res.string.albums_others_section),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        items(
                            items = visibleOthers,
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
                            items = visibleCollections,
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
                        // Dedicated cloud section, grouped per provider with its logo.
                        visibleCloudGroups.forEach { (providerName, cloudAlbums) ->
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
                                visible = visibleMediaTypes.isNotEmpty(),
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
                            items = visibleMediaTypes,
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
        AlbumsTopChrome(
            isScrolling = isScrolling,
            isGrid = viewType == AlbumViewType.GRID,
            gridState = pinchState.gridState,
            listState = listState,
            screenTopPadding = paddingValues.calculateTopPadding(),
            contentTopPadding = innerPaddingValues.calculateTopPadding(),
            pageColor = pageColor,
            filterChips = if (arranging) null else filterChips,
        )
        }
    }
        }
    }
}
}

/**
 * The pinned chips and the fade under the top bar. Its own restart scope: these follow every scroll
 * start, stop and pin change, which must not recompose the page and rebuild the grid's content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumsTopChrome(
    isScrolling: MutableState<Boolean>,
    isGrid: Boolean,
    gridState: LazyGridState,
    listState: LazyListState,
    screenTopPadding: Dp,
    contentTopPadding: Dp,
    pageColor: Color,
    filterChips: (@Composable (bleed: Dp) -> Unit)?,
) {
    val hideSearchBarSetting by rememberPreference(SettingsKeys.autoHideSearchBar)
    val barVisible = !isScrolling.value || !hideSearchBarSetting
    if (filterChips != null) {
        val barPinned by remember(isGrid, gridState, listState) {
            derivedStateOf {
                if (isGrid) {
                    val items = gridState.layoutInfo.visibleItemsInfo
                    albumsFilterBarPinned(
                        barOffset = items.firstOrNull { it.key == KEY_FILTER_BAR }?.offset?.y,
                        firstVisibleKey = items.firstOrNull()?.key,
                    )
                } else {
                    val items = listState.layoutInfo.visibleItemsInfo
                    albumsFilterBarPinned(
                        barOffset = items.firstOrNull { it.key == KEY_FILTER_BAR }?.offset,
                        firstVisibleKey = items.firstOrNull()?.key,
                    )
                }
            }
        }
        // Swaps in exactly where the in-list bar leaves, so it must not fade.
        if (barPinned) {
            val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val pinnedBarTop by animateDpAsState(
                targetValue = if (barVisible) contentTopPadding else statusBarTop,
                label = "albumsFilterBarTop"
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(pageColor)
                    .padding(top = pinnedBarTop)
            ) {
                filterChips(0.dp)
            }
        }
    }
    val topBarScrimZone by animateDpAsState(
        targetValue = if (barVisible) {
            SearchBarDefaults.InputFieldHeight + screenTopPadding + 8.dp
        } else screenTopPadding,
        label = "topBarScrimZone"
    )
    FloatingTopBarScrim(
        barZoneHeight = topBarScrimZone,
        visibility = {
            topScrimVisibility(
                if (isGrid) gridState.firstVisibleItemIndex else listState.firstVisibleItemIndex,
                if (isGrid) gridState.firstVisibleItemScrollOffset else listState.firstVisibleItemScrollOffset,
                TopBarScrimScrollFade.toPx(),
            )
        },
    )
}

/** Lives in the lists' content padding, not on the lists, so scrolling reaches the screen edge. */
private val PageSidePadding = 8.dp

/** [count] columns, or as many as fit at [minWidth] when that is fewer. */
private class ReadableGridCells(private val count: Int, private val minWidth: Dp) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val fitting = (availableSize + spacing) / (minWidth.roundToPx() + spacing)
        return with(GridCells.Fixed(count.coerceAtMost(fitting).coerceAtLeast(1))) {
            calculateCrossAxisCellSizes(availableSize, spacing)
        }
    }
}

/** The state screens keep the Library [header] reachable: shortcuts like Trash work without albums. */
@Composable
private fun HeaderAbove(
    header: (@Composable () -> Unit)?,
    contentPadding: PaddingValues,
    content: @Composable () -> Unit,
) {
    if (header == null) {
        content()
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding())
                .padding(horizontal = 8.dp)
        ) {
            header()
            Box(modifier = Modifier.weight(1f)) { content() }
        }
    }
}

/** Title with the page's actions, so the filter row below can give its whole width to chips. */
@Composable
private fun AlbumsHeading(
    albumCount: Int,
    filterOptions: SnapshotStateList<FilterOption>,
    viewType: AlbumViewType,
    onViewTypeChange: (AlbumViewType) -> Unit,
    onCreateCollection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterButton(
        modifier = modifier,
        filterOptions = filterOptions.toTypedArray(),
        viewType = viewType,
        onViewTypeChange = onViewTypeChange,
        title = {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.albums_heading),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = albumCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = {
            FilledTonalIconButton(
                onClick = onCreateCollection,
                shape = RoundedCornerShape(14.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(Res.string.new_collection),
                )
            }
        },
    )
}

/**
 * Tonal chips whose shape answers selection: a pill at rest, a tighter rounded square when picked.
 * [bleed] lets the row scroll to the screen edge from inside the page's side padding.
 */
@Composable
private fun AlbumsFilterChips(
    bleed: Dp,
    filters: List<AlbumsFilter>,
    sections: List<AlbumSectionWithAlbums>,
    selected: AlbumsFilter,
    onSelect: (AlbumsFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .bleedHorizontally(bleed)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        filters.forEach { filter ->
            val isSelected = filter == selected
            val corner by animateDpAsState(if (isSelected) 12.dp else 20.dp, label = "albumsChipCorner")
            val container by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                label = "albumsChipContainer"
            )
            val content by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "albumsChipContent"
            )
            Surface(
                selected = isSelected,
                onClick = { onSelect(filter) },
                shape = RoundedCornerShape(corner),
                color = container,
                contentColor = content,
            ) {
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        text =
                        when (filter.kind) {
                            AlbumsFilterKind.ALL -> stringResource(Res.string.album_filter_all)
                            AlbumsFilterKind.DEVICE -> stringResource(Res.string.album_filter_device)
                            AlbumsFilterKind.SECTION -> sections
                                .firstOrNull { it.section.id == filter.sectionId }
                                ?.section?.label.orEmpty()
                            AlbumsFilterKind.COLLECTIONS -> stringResource(Res.string.album_filter_collections)
                            AlbumsFilterKind.CLOUD -> stringResource(Res.string.album_filter_cloud)
                            AlbumsFilterKind.MEDIA_TYPES -> stringResource(Res.string.media_type_albums_section)
                        }
                    )
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
