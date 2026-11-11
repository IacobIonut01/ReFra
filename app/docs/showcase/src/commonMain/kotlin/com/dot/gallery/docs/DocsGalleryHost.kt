/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.docs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Constants
import com.dot.gallery.core.LocalScrollToTop
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.navigation.Routes
import com.dot.gallery.core.presentation.components.FilterKind
import com.dot.gallery.core.presentation.components.FilterOption
import com.dot.gallery.core.presentation.components.GalleryNavigationBarContent
import com.dot.gallery.core.presentation.components.GalleryHomeAction
import com.dot.gallery.core.presentation.components.GalleryHomeTopBar
import com.dot.gallery.core.presentation.components.SelectionAction
import com.dot.gallery.core.presentation.components.rememberSelectionSheetConfig
import com.dot.gallery.core.presentation.util.NavigationItem
import com.dot.gallery.core.ui.generated.resources.filter_type_date
import com.dot.gallery.core.ui.generated.resources.filter_type_name
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.feature.albums.ui.AlbumsContent
import com.dot.gallery.ui.core.Icons as GalleryIcons
import com.dot.gallery.ui.core.icons.Library
import com.dot.gallery.feature.albums.ui.components.ProviderIconSlot
import com.dot.gallery.feature.library.ui.LibraryActions
import com.dot.gallery.feature.library.ui.LibraryEditContent
import com.dot.gallery.feature.library.ui.LibraryHeader
import com.dot.gallery.feature.library.ui.LibraryHeaderLayout
import com.dot.gallery.domain.model.LibraryIndicatorState
import com.dot.gallery.feature.library.ui.LibraryRoutes
import com.dot.gallery.feature.library.ui.LibrarySnapshot
import com.dot.gallery.feature.library.ui.LibraryState
import com.dot.gallery.feature.library.ui.LibraryCategoryPreview
import com.dot.gallery.feature.settings.ui.SelectionSheetPreview
import com.dot.gallery.feature.timeline.TimelineContent

/** Bottom chrome height the real app reserves (bar + floating padding). */
private val NAV_BAR_INSET = 112.dp

@Composable
private fun DocsHomeTopBar(
    statusBarInsets: WindowInsets,
    filter: (@Composable () -> Unit)? = null,
    timelineShortcuts: Boolean = false,
) {
    val navigator = LocalDocsNavigator.current
    val selector = LocalMediaSelector.current
    val selectionActive by selector.isSelectionActive.collectAsState()
    // Mirrors MainScreenTopBar: Timeline shortcuts step aside while a selection is active and
    // Search takes their place, since the hidden dock holds the search pill.
    val showShortcuts = timelineShortcuts && !selectionActive
    GalleryHomeTopBar(
        onBrandClick = { navigator?.navigateTo("settings-hub") },
        brandDescription = "Settings",
        actions = listOf(
            GalleryHomeAction(
                key = "select",
                icon = Icons.Outlined.CheckCircle,
                contentDescription = "Select",
                visible = showShortcuts,
                onClick = selector::startSelection,
            ),
            GalleryHomeAction(
                key = "favorites",
                icon = Icons.Outlined.FavoriteBorder,
                contentDescription = "Favorites",
                visible = showShortcuts,
                onClick = { navigator?.navigateTo("browse-favorites") },
            ),
            GalleryHomeAction(
                key = "search",
                icon = Icons.Outlined.Search,
                contentDescription = "Search",
                visible = selectionActive,
                onClick = { navigator?.navigateTo("search-library") },
            ),
        ),
        filter = filter,
        menu = {
            IconButton(onClick = { navigator?.navigateTo("settings-hub") }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
            }
        },
        statusBarInsets = statusBarInsets,
    )
}

enum class DocsGalleryPage(val route: String) {
    TIMELINE(Routes.TIMELINE),
    LIBRARY(Routes.LIBRARY),
}

/**
 * System bars plus the simulated padding DocsSceneHost provides for
 * `handlesContentPadding` scenes — zero outside that path.
 */
@Composable
private fun docsGalleryPadding(): PaddingValues {
    val system = WindowInsets.systemBars.asPaddingValues()
    val extra = LocalDocsContentPadding.current
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = system.calculateStartPadding(direction) + extra.calculateStartPadding(direction),
        top = system.calculateTopPadding() + extra.calculateTopPadding(),
        end = system.calculateEndPadding(direction) + extra.calculateEndPadding(direction),
        bottom = system.calculateBottomPadding() + extra.calculateBottomPadding(),
    )
}

private fun docsNavigationItems(): List<NavigationItem> = listOf(
    NavigationItem(name = "Timeline", route = Routes.TIMELINE, icon = Icons.Outlined.Photo),
    NavigationItem(name = "Library", route = Routes.LIBRARY, icon = GalleryIcons.Library),
)

/**
 * The app shell for browse scenes: the real [TimelineContent] / [AlbumsContent]
 * / [LibraryContent] under the real floating [GalleryNavigationBarContent],
 * sharing one [DocsGallerySession] (store, distributor, selector, renderer)
 * provided by [DocsSceneHost]. Tab taps animate between pages; re-tapping the
 * active tab scrolls it to the top, mirroring the app's
 * [com.dot.gallery.core.presentation.components.AppBarContainer] behaviour.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DocsGalleryHost(
    initialPage: DocsGalleryPage,
    state: String?,
    providerIcon: ProviderIconSlot? = null,
) {
    val session = LocalDocsGallerySession.current
        ?: error("DocsGalleryHost requires LocalDocsGallerySession (provided by DocsSceneHost)")
    val distributor = session.distributor
    val navigator = LocalDocsNavigator.current
    val scrollToTop = LocalScrollToTop.current
    val isScrolling = remember { mutableStateOf(false) }
    var page by rememberSaveable(initialPage, state) { mutableStateOf(initialPage) }

    // Real selection state for the "selected" timeline state.
    if (initialPage == DocsGalleryPage.TIMELINE && state == "selected") {
        remember { session.selector.rawUpdateSelection(setOf(1L, 3L, 4L)) }
    }
    // The app hides the bottom navigation while a selection is active
    // (NavigationComp seeds bottomBarState with `!isSelectionActive`).
    val isSelectionActive by session.selector.isSelectionActive.collectAsState()

    SharedTransitionLayout {
        Box(Modifier.fillMaxSize()) {
            val stateHolder = rememberSaveableStateHolder()
            AnimatedContent(targetState = page, label = "docs-gallery-nav") { target ->
                stateHolder.SaveableStateProvider(target) {
                    when (target) {
                        DocsGalleryPage.TIMELINE -> DocsTimelinePage(
                            isScrolling = isScrolling,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedContentScope = this@AnimatedContent,
                        )

                        DocsGalleryPage.LIBRARY -> DocsAlbumsPage(
                            isScrolling = isScrolling,
                            pinned = state == "pinned",
                            editing = state == "editing",
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedContentScope = this@AnimatedContent,
                            onCollectionClick = { navigator?.navigateTo("collection-grid") },
                            providerIcon = providerIcon,
                        )
                    }
                }
            }
            AnimatedVisibility(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                visible = !isSelectionActive,
                enter = slideInVertically { it * 2 },
                exit = slideOutVertically { it * 2 },
            ) {
                GalleryNavigationBarContent(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    selectedRoute = page.route,
                    navigationItems = docsNavigationItems(),
                    onClick = { route ->
                        val target = DocsGalleryPage.entries.firstOrNull { it.route == route }
                            ?: return@GalleryNavigationBarContent
                        if (target == page) {
                            scrollToTop.requestScrollToTop(route)
                        } else {
                            page = target
                        }
                    },
                    onSearchClick = { navigator?.navigateTo("search-library") },
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DocsTimelinePage(
    isScrolling: MutableState<Boolean>,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope,
    animatedContentScope: androidx.compose.animation.AnimatedContentScope,
) {
    val session = LocalDocsGallerySession.current!!
    val navigator = LocalDocsNavigator.current
    val distributor = session.distributor
    val mediaState = distributor.timelineMediaFlow.collectAsState()
    val metadataState = distributor.metadataFlow.collectAsState(
        initial = com.dot.gallery.data.model.MediaMetadataState()
    )
    val albumState by distributor.albumsFlow.collectAsState()
    val dateFormats by distributor.dateFormatsFlow.collectAsState()
    val groupSimilar by distributor.groupSimilarMedia.collectAsState()
    val enabledGroups by distributor.enabledGroupTypes.collectAsState()
    val lastCellIndex = rememberSaveable {
        mutableIntStateOf(Constants.cellsList.indexOf(GridCells.Fixed(4)))
    }
    val lastMosaicCellIndex = rememberSaveable {
        mutableIntStateOf(Constants.mosaicColumnsList.indexOf(4).coerceAtLeast(0))
    }
    val padding = docsGalleryPadding()

    TimelineContent(
        paddingValues = padding,
        isScrolling = isScrolling,
        mediaState = mediaState,
        metadataState = metadataState,
        albums = albumState.albums,
        dateFormats = dateFormats,
        groupByMonth = distributor.groupByMonth,
        groupByYear = distributor.groupByYear,
        groupSimilarMedia = groupSimilar,
        enabledGroupTypes = enabledGroups,
        isRefreshing = false,
        onRefresh = {},
        aboveGridContent = null,
        bottomBarInset = NAV_BAR_INSET,
        lastCellIndex = lastCellIndex,
        lastMosaicCellIndex = lastMosaicCellIndex,
        indexProgress = null,
        scrollToTopRoute = Routes.TIMELINE,
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
        onMediaClick = {},
        selectionOverlay = { allMedia, selectedMediaList ->
            DocsTimelineSelectionSheet(
                allMedia = allMedia,
                selectedMediaList = selectedMediaList,
                bottomInset = padding.calculateBottomPadding(),
            )
        },
        topBar = { trailing ->
            DocsHomeTopBar(
                filter = trailing,
                statusBarInsets = WindowInsets(top = padding.calculateTopPadding()),
                timelineShortcuts = true,
            )
        },
    )
}

/**
 * The timeline selection sheet for docs previews: the real sheet's placement
 * and look (bottom-end pill stack, config-driven actions) built from
 * [SelectionSheetPreview], which mirrors SelectionSheet's dimensions without
 * its Android/DI-bound side effects. Close exits selection for real so the
 * live preview stays interactive; Favorite reuses the distributor's in-memory
 * override so hearts toggle on screen. Remaining actions are preview-only.
 */
@Composable
private fun BoxScope.DocsTimelineSelectionSheet(
    allMedia: MediaState<UriMedia>,
    selectedMediaList: SnapshotStateList<UriMedia>,
    bottomInset: Dp,
) {
    val selector = LocalMediaSelector.current
    val isSelectionActive by selector.isSelectionActive.collectAsState()
    val selectedIds by selector.selectedMedia.collectAsState()
    val distributor = LocalDocsGallerySession.current?.distributor
    val selectedAll = selectedIds.size == allMedia.media.size && allMedia.media.isNotEmpty()
    AnimatedVisibility(
        modifier = Modifier.align(Alignment.BottomEnd),
        visible = isSelectionActive,
        enter = slideInVertically { it * 2 },
        exit = slideOutVertically { it * 2 },
    ) {
        SelectionSheetPreview(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .padding(bottom = bottomInset),
            config = rememberSelectionSheetConfig().value.sanitized(),
            count = selectedMediaList.size,
            selectAllActive = selectedAll,
            infoVisible = selectedMediaList.size == 1,
            onClose = { selector.clearSelection() },
            onSelectAll = {
                if (selectedAll) selector.clearSelection()
                else selector.addToSelection(allMedia.media.map { it.id })
            },
            onAction = { action ->
                if (action == SelectionAction.FAVORITE) {
                    // Real heart feedback through the in-memory favorite override.
                    selectedMediaList.forEach {
                        distributor?.setFavoriteOverride(it.id, it.favorite == 0)
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DocsAlbumsPage(
    isScrolling: MutableState<Boolean>,
    pinned: Boolean,
    editing: Boolean,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope,
    animatedContentScope: androidx.compose.animation.AnimatedContentScope,
    onCollectionClick: () -> Unit,
    providerIcon: ProviderIconSlot? = null,
) {
    val session = LocalDocsGallerySession.current!!
    val navigator = LocalDocsNavigator.current
    val distributor = session.distributor
    remember(pinned) {
        if (pinned) {
            distributor.setPinned(DocsGalleryData.ALBUM_CAMERA, true)
            distributor.setPinned(DocsGalleryData.ALBUM_SCREENSHOTS, true)
        }
    }
    val albumsFilterOptions = remember {
        mutableStateListOf(
            FilterOption(
                titleRes = com.dot.gallery.core.ui.generated.resources.Res.string.filter_type_date,
                filterKind = FilterKind.DATE,
            ),
            FilterOption(
                titleRes = com.dot.gallery.core.ui.generated.resources.Res.string.filter_type_name,
                filterKind = FilterKind.NAME,
            ),
        )
    }
    val gridSizeIndex = rememberSaveable {
        mutableIntStateOf(Constants.albumCellsList.indexOf(GridCells.Fixed(3)))
    }
    val padding = docsGalleryPadding()
    val libraryState = docsLibraryState()
    val libraryRoutes = docsLibraryRoutes()
    val libraryActions = remember(navigator) {
        LibraryActions(onNavigate = { route -> navigator?.navigateTo(route) })
    }
    var editMode by rememberSaveable(editing) { mutableStateOf(editing) }

    AlbumsContent(
        paddingValues = padding,
        filterOptions = albumsFilterOptions,
        isScrolling = isScrolling,
        gridSizeIndex = gridSizeIndex,
        scrollToTopRoute = Routes.LIBRARY,
        header = {
            LibraryHeader(
                state = libraryState,
                actions = libraryActions,
                routes = libraryRoutes,
                layout = LibraryHeaderLayout.COMPACT,
                onEnterEditMode = { editMode = true },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        },
        topBar = {
            DocsHomeTopBar(
                statusBarInsets = WindowInsets(top = padding.calculateTopPadding()),
            )
        },
        onAlbumClick = {},
        onAlbumLongClick = {},
        onMoveAlbumToTrash = {},
        onIgnoreAlbum = { album: Album -> distributor.ignore(album.id, album.label) },
        onLockAlbum = {},
        onCollectionClick = { onCollectionClick() },
        onCollectionTogglePin = {},
        collectionThumbnailModel = { id ->
            DocsGalleryData.mediaItems().firstOrNull { it.id == id }?.uri
        },
        supportsTrash = true,
        providerIcon = providerIcon,
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
    )
    if (editMode) {
        LibraryEditContent(
            state = libraryState,
            routes = libraryRoutes,
            layout = LibraryHeaderLayout.COMPACT,
            statusBarInsets = WindowInsets(top = padding.calculateTopPadding()),
            onDone = { editMode = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
    }
}

@Composable
private fun docsLibraryState(): LibraryState {
    val media = remember { DocsGalleryData.mediaItems() }
    val favorites = media.count { it.favorite == 1 }
    return LibraryState(
        snapshot = LibrarySnapshot(
            categories = listOf(
                LibraryCategoryPreview(
                    id = 1,
                    name = "Nature",
                    mediaCount = media.count { it.albumLabel == "Camera" },
                    thumbnailMedia = media.first { it.id == 1L },
                ),
                LibraryCategoryPreview(
                    id = 2,
                    name = "Space",
                    mediaCount = media.count { it.label.startsWith("Earth") },
                    thumbnailMedia = media.first { it.id == 5L },
                ),
            ),
            categoryCount = 2,
            indicators = LibraryIndicatorState(favoriteCount = favorites),
        ),
        modelStatus = com.dot.gallery.core.ml.ModelStatus.READY,
        aiAvailable = true,
        mapsEnabled = false,
    )
}

@Composable
private fun docsLibraryRoutes(): LibraryRoutes = remember {
    LibraryRoutes(
            library = "library",
            settings = "settings-hub",
            locations = "route://locations",
            people = "route://people",
            categories = "route://categories",
            trash = "route://trash",
            // Real destination now exists — the Library "Favorites" entry
            // navigates to the browse-favorites scene via the docs navigator.
            favorites = "browse-favorites",
            vault = "vault-list",
            ignored = "ignored-rules",
            privateFolder = "settings-private-folder",
            cloudArchive = "route://cloud-archive",
            memories = "route://memories",
            cloudSharedLinks = "route://cloud-shared-links",
            cloudBackup = "route://cloud-backup",
            cloudAccounts = "route://cloud-accounts",
    )
}
