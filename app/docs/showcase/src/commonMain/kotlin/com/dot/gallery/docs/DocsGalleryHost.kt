/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.docs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Photo
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Constants
import com.dot.gallery.core.LocalScrollToTop
import com.dot.gallery.core.navigation.Routes
import com.dot.gallery.core.presentation.components.FilterKind
import com.dot.gallery.core.presentation.components.FilterOption
import com.dot.gallery.core.presentation.components.GalleryNavigationBarContent
import com.dot.gallery.core.presentation.components.GalleryHomeTopBar
import com.dot.gallery.core.presentation.util.NavigationItem
import com.dot.gallery.core.ui.generated.resources.filter_type_date
import com.dot.gallery.core.ui.generated.resources.filter_type_name
import com.dot.gallery.domain.model.Album
import com.dot.gallery.feature.albums.ui.AlbumsContent
import com.dot.gallery.feature.albums.ui.components.ProviderIconSlot
import com.dot.gallery.feature.library.ui.LibraryActions
import com.dot.gallery.feature.library.ui.LibraryContent
import com.dot.gallery.domain.model.LibraryIndicatorState
import com.dot.gallery.feature.library.ui.LibraryRoutes
import com.dot.gallery.feature.library.ui.LibrarySnapshot
import com.dot.gallery.feature.library.ui.LibraryState
import com.dot.gallery.feature.library.ui.LibraryCategoryPreview
import com.dot.gallery.feature.timeline.TimelineContent

/** Bottom chrome height the real app reserves (bar + floating padding). */
private val NAV_BAR_INSET = 112.dp

@Composable
private fun DocsHomeTopBar(
    statusBarInsets: WindowInsets,
    filter: (@Composable () -> Unit)? = null,
) {
    val navigator = LocalDocsNavigator.current
    GalleryHomeTopBar(
        onBrandClick = { navigator?.navigateTo("settings-hub") },
        brandDescription = "Settings",
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
    ALBUMS(Routes.ALBUMS),
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
    NavigationItem(name = "Albums", route = Routes.ALBUMS, icon = Icons.Outlined.FolderOpen),
    NavigationItem(name = "Library", route = Routes.LIBRARY, icon = Icons.AutoMirrored.Outlined.LibraryBooks),
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

                        DocsGalleryPage.ALBUMS -> DocsAlbumsPage(
                            isScrolling = isScrolling,
                            pinned = state == "pinned",
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedContentScope = this@AnimatedContent,
                            onCollectionClick = { navigator?.navigateTo("collection-grid") },
                            providerIcon = providerIcon,
                        )

                        DocsGalleryPage.LIBRARY -> DocsLibraryPage(
                            isScrolling = isScrolling,
                            editing = state == "editing",
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedContentScope = this@AnimatedContent,
                        )
                    }
                }
            }
            GalleryNavigationBarContent(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
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
        selectionOverlay = { _, _ -> },
        topBar = { trailing ->
            DocsHomeTopBar(
                filter = trailing,
                statusBarInsets = WindowInsets(top = padding.calculateTopPadding()),
            )
        },
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DocsAlbumsPage(
    isScrolling: MutableState<Boolean>,
    pinned: Boolean,
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

    AlbumsContent(
        paddingValues = padding,
        filterOptions = albumsFilterOptions,
        isScrolling = isScrolling,
        gridSizeIndex = gridSizeIndex,
        scrollToTopRoute = Routes.ALBUMS,
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
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DocsLibraryPage(
    isScrolling: MutableState<Boolean>,
    editing: Boolean,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope,
    animatedContentScope: androidx.compose.animation.AnimatedContentScope,
) {
    val session = LocalDocsGallerySession.current!!
    val navigator = LocalDocsNavigator.current
    val media = remember { DocsGalleryData.mediaItems() }
    val favorites = media.count { it.favorite == 1 }

    LibraryContent(
        state = LibraryState(
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
            bottomBarInset = NAV_BAR_INSET,
        ),
        actions = LibraryActions(
            onNavigate = { route -> navigator?.navigateTo(route) },
            onOpenLocation = { _, _, _, _ -> },
            onOpenMedia = {},
            onOpenPerson = { _, _ -> },
            onOpenCategory = {},
            onEditCategory = {},
            onViewportChanged = {},
            onCellsIndexChange = {},
        ),
        routes = LibraryRoutes(
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
            cloudSharedLinks = "route://cloud-shared-links",
            cloudBackup = "route://cloud-backup",
            cloudAccounts = "route://cloud-accounts",
        ),
        paddingValues = docsGalleryPadding(),
        isScrolling = isScrolling,
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
        searchBar = { insets, _ ->
            DocsHomeTopBar(
                statusBarInsets = insets.add(
                    WindowInsets(top = LocalDocsContentPadding.current.calculateTopPadding()),
                ),
            )
        },
        mapContent = null,
        initialEditMode = editing,
    )
}
