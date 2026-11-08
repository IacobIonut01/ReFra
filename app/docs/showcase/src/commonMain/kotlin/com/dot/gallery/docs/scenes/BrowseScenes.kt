/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.docs.scenes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Constants
import com.dot.gallery.core.presentation.components.ClassicNavigationBarContent
import com.dot.gallery.core.presentation.components.GalleryNavigationBarContent
import com.dot.gallery.core.presentation.components.MainSearchBar
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.domain.model.LastSort
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.TimelineFilter
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.settings.LAYOUT_GRID
import com.dot.gallery.domain.settings.LAYOUT_MOSAIC
import com.dot.gallery.domain.settings.MapSettingsStore
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.domain.util.OrderType
import com.dot.gallery.feature.albums.ui.collections.CollectionViewContent
import com.dot.gallery.feature.classifier.ui.components.CategoryCarouselItem
import com.dot.gallery.feature.classifier.ui.search.SearchActions
import com.dot.gallery.feature.classifier.ui.search.SearchContent
import com.dot.gallery.feature.classifier.ui.search.SearchResultsState
import com.dot.gallery.feature.classifier.ui.search.SearchState
import com.dot.gallery.feature.help.ui.HelpActions
import com.dot.gallery.feature.help.ui.HelpContent
import com.dot.gallery.feature.help.ui.HelpState
import com.dot.gallery.feature.library.ui.FavoriteContent
import com.dot.gallery.feature.timeline.TimelineFilterSheet
import com.dot.gallery.docs.DocsGalleryData
import com.dot.gallery.docs.DocsGalleryHost
import com.dot.gallery.docs.DocsGalleryPage
import com.dot.gallery.ui.core.icons.Library
import com.dot.gallery.core.ui.settings.LocalSettingsStore
import com.dot.gallery.docs.LocalDocsGallerySession
import com.dot.gallery.docs.LocalDocsNavigator
import com.dot.gallery.core.presentation.components.FilterKind
import com.dot.gallery.ui.core.icons.Albums

/**
 * Minimal real shared-element scopes for standalone screens that require them
 * (collection grid, search). Single-target AnimatedContent — no animation runs,
 * but every `sharedElement`/`boundsTransform` call resolves against the true
 * scopes instead of a fabricated stand-in.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun DocsSharedScopes(
    content: @Composable (SharedTransitionScope, AnimatedContentScope) -> Unit
) {
    SharedTransitionLayout {
        AnimatedContent(targetState = Unit, label = "docs-shared-scopes") {
            content(this@SharedTransitionLayout, this)
        }
    }
}

/** Seeds docs-only prefs synchronously so the opening frame is already right. */
@Composable
internal fun seedDocsPrefs(vararg pairs: Pair<com.dot.gallery.domain.settings.PrefKey<*>, *>) {
    val store = LocalSettingsStore.current as? MapSettingsStore ?: return
    remember(*pairs.map { it.first }.toTypedArray()) { store.seed(*pairs) }
}

@Composable
fun DocsTimelineScene(state: String?) {
    val layout = when (state) {
        "mosaic" -> LAYOUT_MOSAIC
        else -> LAYOUT_GRID
    }
    seedDocsPrefs(SettingsKeys.timelineLayoutType to layout)
    DocsGalleryHost(DocsGalleryPage.TIMELINE, state)
}

@Composable
fun DocsAlbumsScene(state: String?) {
    DocsGalleryHost(DocsGalleryPage.LIBRARY, state)
}

@Composable
fun DocsLibraryScene(state: String?) {
    DocsGalleryHost(DocsGalleryPage.LIBRARY, state)
}

/**
 * The real [FavoriteContent]: `filled` reads the session distributor's
 * `favoritesMediaFlow` (in-memory heart toggles republish it, so the live
 * preview stays interactive), `empty` renders the component's own
 * `EmptyFavorites`. The search-bar slot is unused for the favorites target —
 * [com.dot.gallery.core.presentation.components.MediaScreen] swaps it for the
 * "Favorites" app bar — but the real shared bar is still supplied.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DocsFavoritesScene(state: String?) {
    val session = LocalDocsGallerySession.current!!
    val distributor = session.distributor
    val favoritesState = distributor.favoritesMediaFlow.collectAsState()
    val emptyState = remember { mutableStateOf(MediaState<UriMedia>(isLoading = false)) }
    // "scrolled" hearts the whole demo library, twice over, so the grid is tall enough to sit
    // part-way down under the transparent blur bar.
    val scrolledState = remember {
        val all = DocsGalleryData.mediaItems().map { it.copy(favorite = 1) }
        mutableStateOf(DocsGalleryData.mediaStateFor(all + all.map { it.copy(id = it.id + 100L) }))
    }
    val metadataState = distributor.metadataFlow.collectAsState(
        initial = com.dot.gallery.data.model.MediaMetadataState()
    )
    DocsSharedScopes { sts, acs ->
        FavoriteContent(
            paddingValues = WindowInsets.systemBars.asPaddingValues(),
            mediaState = when (state) {
                "empty" -> emptyState
                "scrolled" -> scrolledState
                else -> favoritesState
            },
            metadataState = metadataState,
            lastCellIndex = rememberSaveable {
                mutableIntStateOf(Constants.cellsList.indexOf(GridCells.Fixed(3)))
            },
            lastMosaicCellIndex = rememberSaveable {
                mutableIntStateOf(Constants.mosaicColumnsList.indexOf(4).coerceAtLeast(0))
            },
            // Same route constant the app wrapper supplies (Screen.MediaViewScreen).
            mediaViewerRoute = "media_screen",
            searchBar = { scrolling, menuItems ->
                MainSearchBar(
                    isScrolling = scrolling,
                    sharedTransitionScope = sts,
                    animatedContentScope = acs,
                    onSearchClick = {},
                    menuItems = menuItems,
                )
            },
            // No selection active in the captured pose — the selection sheet
            // slot stays empty rather than fabricating a toolbar.
            selectionSheetContent = { _, _ -> },
            sharedTransitionScope = sts,
            animatedContentScope = acs,
            initialFirstVisibleItemIndex = if (state == "scrolled") 6 else 0,
        )
    }
}

/**
 * The real bottom navigation bars extracted to core/ui — floating (default)
 * and classic style, at the component width the site embeds.
 */
@Composable
fun DocsNavigationBarScene(state: String?) {
    val navigator = LocalDocsNavigator.current
    var selected by rememberSaveable { mutableStateOf(com.dot.gallery.core.navigation.Routes.TIMELINE) }
    val items = remember {
        listOf(
            com.dot.gallery.core.presentation.util.NavigationItem(
                name = "Timeline",
                route = com.dot.gallery.core.navigation.Routes.TIMELINE,
                icon = androidx.compose.material.icons.Icons.Outlined.Photo,
            ),
            com.dot.gallery.core.presentation.util.NavigationItem(
                name = "Library",
                route = com.dot.gallery.core.navigation.Routes.LIBRARY,
                icon = com.dot.gallery.ui.core.Icons.Library,
            ),
        )
    }
    Box(Modifier.fillMaxSize().padding(top = 16.dp), contentAlignment = Alignment.BottomCenter) {
        if (state == "classic") {
            ClassicNavigationBarContent(
                selectedRoute = selected,
                navigationItems = items,
                onClick = { selected = it },
            )
        } else {
            GalleryNavigationBarContent(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                selectedRoute = selected,
                navigationItems = items,
                onClick = { selected = it },
                onSearchClick = { navigator?.navigateTo("search-library") },
            )
        }
    }
}

/**
 * Timeline with the real sort-and-filter ModalBottomSheet forced open — the
 * scene declares `captureWindow` so the capture harness photographs the whole
 * window (sheets live outside the tagged root).
 */
@Composable
fun DocsTimelineFiltersScene(state: String?) {
    seedDocsPrefs(SettingsKeys.timelineLayoutType to LAYOUT_GRID)
    DocsGalleryHost(DocsGalleryPage.TIMELINE, null)
    val sheetState = rememberAppBottomSheetState()
    LaunchedEffect(Unit) { sheetState.show() }
    TimelineFilterSheet(
        sheetState = sheetState,
        currentFilter = TimelineFilter(),
        currentSort = LastSort(OrderType.Descending, FilterKind.DATE),
        availableYears = listOf(2025, 2024),
        availableAlbums = DocsGalleryData.albums(),
        indexProgress = null,
        onApply = { _, _ -> },
    )
}

/**
 * Real search screen over the demo library — filename filtering is done in
 * memory so typing in the live preview changes results without a model index.
 */
@Composable
fun DocsSearchScene(state: String?) {
    // Keep the keyboard closed for captures; the live field focuses on tap.
    seedDocsPrefs(SettingsKeys.autoOpenSearchKeyboard to false)
    val session = LocalDocsGallerySession.current
        ?: error("DocsSearchScene requires LocalDocsGallerySession")
    val media = remember { DocsGalleryData.mediaItems() }
    var query by rememberSaveable {
        mutableStateOf(
            when (state) {
                "results" -> "Lake"
                "empty" -> "Receipt"
                else -> ""
            }
        )
    }
    val results = remember(query) {
        if (query.isBlank()) emptyList()
        else media.filter { it.label.contains(query, ignoreCase = true) }
    }
    DocsSharedScopes { sts, acs ->
        SearchContent(
            state = SearchState(
                query = query,
                searchResults = SearchResultsState(
                    hasSearched = query.isNotBlank(),
                    isSearching = false,
                    results = DocsGalleryData.mediaStateFor(results),
                ),
                isModelAvailable = true,
                searchableMediaIds = media.mapTo(HashSet()) { it.id },
                topCategories = listOf(
                    CategoryCarouselItem(
                        id = 1,
                        name = "Nature",
                        mediaCount = media.count { it.albumLabel == "Camera" },
                        thumbnailMedia = media.first { it.id == 1L },
                    ),
                    CategoryCarouselItem(
                        id = 2,
                        name = "Space",
                        mediaCount = media.count { it.label.startsWith("Earth") },
                        thumbnailMedia = media.first { it.id == 5L },
                    ),
                ),
            ),
            actions = SearchActions(
                onBack = {},
                onClearQuery = { query = "" },
                onQueryChange = { q, _ -> query = q },
                onSubmitSearch = { q -> query = q },
                onRestoreImageSearch = {},
                onRemoveHistory = {},
                onRemoveImageHistory = {},
                onImageSelected = {},
                onClearSelectedMedia = {},
                onMimeTypeFilter = { _, _ -> },
                onLensModelFilter = {},
                onMediaModeFilter = {},
                onGroupTypeFilter = {},
                onRetrySearch = {},
                onOpenCategory = {},
                onOpenLocation = { _, _, _, _ -> },
                onOpenMedia = {},
                onOpenSearchResult = {},
            ),
            isScrolling = remember { mutableStateOf(false) },
            sharedTransitionScope = sts,
            animatedContentScope = acs,
            lastCellIndex = rememberSaveable {
                mutableIntStateOf(Constants.cellsList.indexOf(GridCells.Fixed(3)))
            },
            lastMosaicCellIndex = rememberSaveable {
                mutableIntStateOf(Constants.mosaicColumnsList.indexOf(4).coerceAtLeast(0))
            },
        )
    }
}

@Composable
fun DocsHelpScene(state: String?) {
    HelpContent(
        state = HelpState(),
        actions = HelpActions(),
    )
}

@Composable
fun DocsCollectionGridScene(state: String?) {
    DocsSharedScopes { sts, acs ->
        val session = LocalDocsGallerySession.current!!
        val metadataState = session.distributor.metadataFlow.collectAsState(
            initial = com.dot.gallery.data.model.MediaMetadataState()
        )
        CollectionViewContent(
            collectionId = when (state) {
                "empty" -> -1L
                "scrolled" -> DocsGalleryData.COLLECTION_ALL
                else -> DocsGalleryData.COLLECTION_TRIP
            },
            collectionName = if (state == "empty") "New collection" else DocsGalleryData.tripCollection.label,
            paddingValues = PaddingValues(0.dp),
            isScrolling = remember { mutableStateOf(false) },
            metadataState = metadataState,
            gridSizeIndex = rememberSaveable {
                mutableIntStateOf(Constants.cellsList.indexOf(GridCells.Fixed(3)))
            },
            mosaicGridSizeIndex = rememberSaveable {
                mutableIntStateOf(Constants.mosaicColumnsList.indexOf(4).coerceAtLeast(0))
            },
            onMediaClick = {},
            onEditAlbums = {},
            onSlideshow = {},
            selectionSheet = { _, _ -> },
            sharedTransitionScope = sts,
            animatedContentScope = acs,
            initialFirstVisibleItemIndex = if (state == "scrolled") 5 else 0,
        )
    }
}
