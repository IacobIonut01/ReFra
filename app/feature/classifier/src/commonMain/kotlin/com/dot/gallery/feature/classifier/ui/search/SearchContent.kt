/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.classifier.ui.search

import com.dot.gallery.core.presentation.components.NavigationBackButton
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Constants.cellsList
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.presentation.components.EmptyMedia
import com.dot.gallery.core.presentation.components.Error
import com.dot.gallery.core.presentation.components.GridPinchZoomLayout
import com.dot.gallery.core.presentation.components.MediaGridView
import com.dot.gallery.core.presentation.components.MosaicMediaGrid
import com.dot.gallery.core.presentation.components.MosaicPinchZoomLayout
import com.dot.gallery.core.presentation.components.SettingsOptionLayout
import com.dot.gallery.core.presentation.components.TimelineScroller
import com.dot.gallery.core.presentation.components.rememberGridPinchZoomState
import com.dot.gallery.core.presentation.components.rememberMosaicMonthSegments
import com.dot.gallery.core.presentation.components.rememberMosaicPinchZoomState
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.presentation.util.selectedMedia
import com.dot.gallery.core.presentation.util.sharedSearchBounds
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.core.ui.util.rememberedDerivedState
import com.dot.gallery.data.entity.SmartScanStatus
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.model.MediaItem
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.settings.LAYOUT_MOSAIC
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.feature.classifier.ui.components.CategoryCarousel
import com.dot.gallery.feature.classifier.ui.components.LocationCarousel
import com.dot.gallery.feature.classifier.ui.components.SearchCarousel
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.classifier.generated.resources.Res
import com.dot.gallery.feature.classifier.generated.resources.browse_categories
import com.dot.gallery.feature.classifier.generated.resources.camera_models
import com.dot.gallery.feature.classifier.generated.resources.find_similar
import com.dot.gallery.feature.classifier.generated.resources.locations
import com.dot.gallery.feature.classifier.generated.resources.media_groupings
import com.dot.gallery.feature.classifier.generated.resources.media_types
import com.dot.gallery.feature.classifier.generated.resources.no_results_found
import com.dot.gallery.feature.classifier.generated.resources.search_by_image
import com.dot.gallery.feature.classifier.generated.resources.search_images_videos
import com.dot.gallery.feature.classifier.generated.resources.smart_scan_eta_hours
import com.dot.gallery.feature.classifier.generated.resources.smart_scan_eta_minutes
import com.dot.gallery.feature.classifier.generated.resources.smart_scan_eta_under_minute
import com.dot.gallery.feature.classifier.generated.resources.smart_search_index_preparing
import com.dot.gallery.feature.classifier.generated.resources.smart_search_index_progress
import com.dot.gallery.feature.classifier.generated.resources.smart_search_index_title
import com.dot.gallery.feature.classifier.generated.resources.smart_search_index_waiting
import com.dot.gallery.feature.classifier.generated.resources.sort_by_date
import com.dot.gallery.feature.classifier.generated.resources.sort_most_accurate
import com.dot.gallery.feature.classifier.generated.resources.special_modes
import com.dot.gallery.feature.classifier.generated.resources.visual_search


/**
 * The timeline search surface — query field, history/suggestions, discovery
 * carousels and result grids (grid or mosaic).
 *
 * App shell collects the [state] from the search ViewModel and supplies:
 * - [tipsContent] — optional "Help & Tips" results block, rendered inline in
 *   whichever scroll container is active; `null` hides it entirely.
 * - [selectionSheetContent] — the platform selection overlay (e.g. the app's
 *   `SelectionSheet`), anchored bottom-end.
 * - [lastCellIndex]/[lastMosaicCellIndex] — persisted grid cell indices
 *   (platform store — Android keeps them in SharedPreferences).
 */
@OptIn(
    ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class
)
@Composable
fun SearchContent(
    state: SearchState,
    actions: SearchActions,
    isScrolling: MutableState<Boolean>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    tipsContent: (@Composable () -> Unit)? = null,
    selectionSheetContent: @Composable BoxScope.(
        allMedia: MediaState<UriMedia>,
        selectedMediaList: SnapshotStateList<UriMedia>,
    ) -> Unit = { _, _ -> },
    lastCellIndex: MutableState<Int>,
    lastMosaicCellIndex: MutableState<Int>,
) = with(sharedTransitionScope) {
    val distributor = LocalMediaDistributor.current
    val searchResults = state.searchResults
    val query = state.query
    val selectedImageMedia = state.selectedImageMedia
    val shouldClearOnBack = shouldClearSearchOnBack(query, selectedImageMedia != null)
    val searchHistory by rememberPreference(SettingsKeys.searchHistory)

    BackHandler(enabled = shouldClearOnBack) {
        actions.onClearQuery()
    }

    val searchFieldFocus = remember { FocusRequester() }
    val autoOpenKeyboard by rememberPreference(SettingsKeys.autoOpenSearchKeyboard)
    // Requesting focus while the shared-bounds morph is still running is dropped by
    // the IME — wait for the transition to settle, then open the keyboard on its own.
    // The request can still race the post-transition layout pass, so retry briefly.
    LaunchedEffect(autoOpenKeyboard) {
        if (!autoOpenKeyboard) return@LaunchedEffect
        snapshotFlow { isTransitionActive }.first { active -> !active }
        repeat(3) { attempt ->
            val focused = runCatching { searchFieldFocus.requestFocus() }.isSuccess
            if (focused) return@LaunchedEffect
            if (attempt < 2) delay(150)
        }
    }

    // Image-to-image search UI state
    var showPickerSheet by rememberSaveable { mutableStateOf(false) }
    var showPreviewDialog by rememberSaveable { mutableStateOf(false) }

    val visualSearchLabel = stringResource(Res.string.visual_search)
    // Carousel/header strings — LazyListScope blocks are not composable, so the
    // resources must be resolved up front.
    val browseCategoriesTitle = stringResource(Res.string.browse_categories)
    val locationsTitle = stringResource(Res.string.locations)
    val mediaTypesTitle = stringResource(Res.string.media_types)
    val cameraModelsTitle = stringResource(Res.string.camera_models)
    val specialModesTitle = stringResource(Res.string.special_modes)
    val mediaGroupingsTitle = stringResource(Res.string.media_groupings)
    val historyItems by rememberedDerivedState {
        val visibleHistory = searchHistory.filter { entry ->
            entry.mediaId == null || entry.mediaId in state.searchableMediaIds
        }.take(5)
        if (visibleHistory.isEmpty()) emptyList() else {
            listOf(SettingsEntity.Header("History")) + visibleHistory.map { entry ->
                val mediaId = entry.mediaId
                if (mediaId != null) {
                    SettingsEntity.Preference(
                        icon = if (entry.mediaUri == null) Icons.Outlined.ImageSearch else null,
                        iconUri = entry.mediaUri,
                        title = entry.mediaLabel ?: entry.query,
                        summary = visualSearchLabel,
                        onClick = { actions.onRestoreImageSearch(mediaId) },
                        tag = mediaId
                    )
                } else {
                    SettingsEntity.Preference(
                        title = entry.query,
                        onClick = { actions.onQueryChange(entry.query, true) }
                    )
                }
            }
        }
    }
    val suggestionProviders = rememberSearchSuggestionProviders()
    val suggestionItems = remember(suggestionProviders) {
        suggestionProviders.toSettingsEntities(actions)
    }
    val searchIndexerState = state.searchIndexerState
    // Shared "Help & Tips" search results, rendered inline in whichever scroll
    // container is active (history list, media grid, or empty state) so tips and
    // results scroll together as one surface.
    val helpTipsContent: @Composable () -> Unit = {
        tipsContent?.invoke()
    }
    Scaffold(
        modifier = Modifier.sharedSearchBounds(sharedTransitionScope, animatedContentScope),
        topBar = {
            Box(
                modifier = Modifier.statusBarsPadding()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .padding(start = 4.dp, top = 4.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Moves into the wide-window rail like every other screen's back button.
                        NavigationBackButton(
                            forcedAction = {
                                if (shouldClearOnBack) {
                                    actions.onClearQuery()
                                } else {
                                    actions.onBack()
                                }
                            }
                        )
                        val outlineColor = Color.Transparent
                        OutlinedTextField(
                            modifier = Modifier
                                .focusRequester(searchFieldFocus)
                                .fillMaxWidth(),
                            value = query,
                            onValueChange = { newQuery ->
                                if (newQuery != " ") {
                                    actions.onQueryChange(newQuery, false)
                                }
                            },
                            shape = CircleShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = outlineColor,
                                focusedBorderColor = outlineColor,
                                errorBorderColor = outlineColor,
                                disabledBorderColor = outlineColor
                            ),
                            keyboardOptions = KeyboardOptions.Default.copy(
                                imeAction = ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = { actions.onSubmitSearch(query) },
                                onDone = { actions.onSubmitSearch(query) },
                                onGo = { actions.onSubmitSearch(query) },
                                onSend = { actions.onSubmitSearch(query) }
                            ),
                            leadingIcon = selectedImageMedia?.let { media ->
                                {
                                    ImageSearchChip(
                                        media = media,
                                        onClick = { showPreviewDialog = true },
                                        onRemove = { actions.onClearSelectedMedia() }
                                    )
                                }
                            },
                            placeholder = {
                                Text(
                                    text = if (selectedImageMedia != null)
                                        stringResource(Res.string.find_similar)
                                    else
                                        stringResource(Res.string.search_images_videos),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            },
                            singleLine = true,
                            trailingIcon = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AnimatedVisibility(
                                        visible = query.isNotBlank() && !searchResults.isSearching && !searchResults.hasSearched,
                                        enter = fadeIn() + slideInHorizontally { it },
                                        exit = fadeOut() + slideOutHorizontally { it }
                                    ) {
                                        IconButton(
                                            modifier = Modifier
                                                .padding(horizontal = 4.dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                                    shape = CircleShape
                                                ),
                                            onClick = { actions.onSubmitSearch(query) }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Search,
                                                contentDescription = stringResource(Res.string.search_images_videos),
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    AnimatedVisibility(
                                        visible = state.isModelAvailable && selectedImageMedia == null && !searchResults.isSearching,
                                        enter = fadeIn() + slideInHorizontally { it },
                                        exit = fadeOut() + slideOutHorizontally { it }
                                    ) {
                                        IconButton(
                                            modifier = Modifier
                                                .padding(end = 4.dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                                    shape = CircleShape
                                                ),
                                            onClick = { showPickerSheet = true }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.ImageSearch,
                                                contentDescription = stringResource(Res.string.search_by_image),
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
            ) {
                // "Help & Tips" results scroll inline with each state (history list,
                // media grid, empty state) so everything is one seamless scroll.
                AnimatedVisibility(
                    modifier = Modifier.padding(top = 16.dp),
                    visible = !searchResults.isSearching && !searchResults.hasSearched,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        if (searchIndexerState.isIndexing) {
                            item(key = "search_index_status") {
                                SearchIndexStatusCard(
                                    state = searchIndexerState,
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                        if (tipsContent != null) {
                            item(key = "help_tips") { helpTipsContent() }
                        }
                        // History and Suggestions
                        SettingsOptionLayout(
                            optionList = historyItems,
                            slimLayout = true,
                            swipeToDismiss = true,
                            itemKey = { index, item -> when {
                                item is SettingsEntity.Header -> "search-history-header-$index"
                                item.tag is Long -> "search-history-image-${item.tag}"
                                else -> "search-history-text-${item.title}"
                            } },
                            onDismiss = { item ->
                                val mediaId = item.tag as? Long
                                if (mediaId != null) {
                                    actions.onRemoveImageHistory(mediaId)
                                } else {
                                    actions.onRemoveHistory(item.title)
                                }
                                actions.onClearQuery()
                            }
                        )
                        SettingsOptionLayout(
                            optionList = suggestionItems,
                            slimLayout = true,
                            itemKey = { index, _ -> "search-suggestion-$index" }
                        )

                        // Category Carousel
                        if (state.topCategories.isNotEmpty()) {
                            SettingsOptionLayout(
                                modifier = Modifier.padding(top = 12.dp),
                                optionList = listOf(SettingsEntity.Header(browseCategoriesTitle)),
                                slimLayout = true,
                                itemKey = { _, _ -> "search-section-categories" }
                            )
                            item {
                                CategoryCarousel(
                                    categories = state.topCategories,
                                    onCategoryClick = { item ->
                                        actions.onOpenCategory(item.id)
                                    },
                                    title = null
                                )
                            }
                        }

                        // Location Carousel
                        if (state.topLocations.isNotEmpty()) {
                            SettingsOptionLayout(
                                modifier = Modifier.padding(top = 12.dp),
                                optionList = listOf(SettingsEntity.Header(locationsTitle)),
                                slimLayout = true,
                                itemKey = { _, _ -> "search-section-locations" }
                            )
                            item {
                                LocationCarousel(
                                    locations = state.topLocations,
                                    onLocationClick = { locationMedia ->
                                        val city = locationMedia.city
                                        val country = locationMedia.country
                                        val latitude = locationMedia.latitude
                                        val longitude = locationMedia.longitude
                                        if (!city.isNullOrBlank() || !country.isNullOrBlank() ||
                                            latitude != null && longitude != null
                                        ) {
                                            actions.onOpenLocation(
                                                city.orEmpty(),
                                                country.orEmpty(),
                                                latitude,
                                                longitude,
                                            )
                                        } else {
                                            actions.onOpenMedia(locationMedia.media.id)
                                        }
                                    },
                                    title = null
                                )
                            }
                        }

                        // Media Types Carousel
                        if (state.topMimeTypes.isNotEmpty()) {
                            SettingsOptionLayout(
                                modifier = Modifier.padding(top = 12.dp),
                                optionList = listOf(SettingsEntity.Header(mediaTypesTitle)),
                                slimLayout = true,
                                itemKey = { _, _ -> "search-section-media-types" }
                            )
                            item {
                                SearchCarousel(
                                    items = state.topMimeTypes,
                                    onItemClick = { item ->
                                        actions.onMimeTypeFilter(item.key, false)
                                    }
                                )
                            }
                        }

                        // Camera Models Carousel
                        if (state.topLensModels.isNotEmpty()) {
                            SettingsOptionLayout(
                                modifier = Modifier.padding(top = 12.dp),
                                optionList = listOf(SettingsEntity.Header(cameraModelsTitle)),
                                slimLayout = true,
                                itemKey = { _, _ -> "search-section-camera-models" }
                            )
                            item {
                                SearchCarousel(
                                    items = state.topLensModels,
                                    onItemClick = { item ->
                                        actions.onLensModelFilter(item.key)
                                    }
                                )
                            }
                        }

                        // Media Modes Carousel (Night Mode, Panoramas, etc.)
                        if (state.topMediaModes.isNotEmpty()) {
                            SettingsOptionLayout(
                                modifier = Modifier.padding(top = 12.dp),
                                optionList = listOf(SettingsEntity.Header(specialModesTitle)),
                                slimLayout = true,
                                itemKey = { _, _ -> "search-section-special-modes" }
                            )
                            item {
                                SearchCarousel(
                                    items = state.topMediaModes,
                                    onItemClick = { item ->
                                        actions.onMediaModeFilter(item.key)
                                    }
                                )
                            }
                        }

                        // Media Groupings Carousel (Bursts, RAW+JPG, Edits)
                        if (state.topGroupTypes.isNotEmpty()) {
                            SettingsOptionLayout(
                                modifier = Modifier.padding(top = 12.dp),
                                optionList = listOf(SettingsEntity.Header(mediaGroupingsTitle)),
                                slimLayout = true,
                                itemKey = { _, _ -> "search-section-media-groupings" }
                            )
                            item {
                                SearchCarousel(
                                    items = state.topGroupTypes,
                                    onItemClick = { item ->
                                        actions.onGroupTypeFilter(item.key)
                                    }
                                )
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = searchResults.isSearching,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        if (searchIndexerState.isIndexing) {
                            item(key = "loading_search_index_status") {
                                SearchIndexStatusCard(searchIndexerState)
                            }
                        }
                        item(key = "search_loading") {
                            Box(
                                modifier = Modifier
                                    .animateContentSize()
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                LoadingIndicator(
                                    modifier = Modifier.size(128.dp),
                                )
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = stringResource(Res.string.search_images_videos),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = searchResults.results.error.isEmpty()
                            && !searchResults.isSearching
                            && searchResults.results.media.isNotEmpty()
                            && searchResults.hasSearched,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    val metadataState =
                        distributor.metadataFlow.collectAsStateWithLifecycle(MediaMetadataState())
                    var canScroll by rememberSaveable { mutableStateOf(true) }
                    var lastCellIndexState by lastCellIndex
                    val timelineLayoutType by rememberPreference(SettingsKeys.timelineLayoutType)

                    var sortByRelevance by rememberSaveable { mutableStateOf(true) }
                    val isRelevanceSearch = searchResults.isRelevanceSearch
                    val useRelevanceOrder = isRelevanceSearch && sortByRelevance

                    val dpCacheWindow = LazyLayoutCacheWindow(ahead = 200.dp, behind = 100.dp)
                    val pinchState = rememberGridPinchZoomState(
                        cellsList = cellsList,
                        initialCellsIndex = lastCellIndexState,
                        gridState = rememberLazyGridState(
                            cacheWindow = dpCacheWindow
                        )
                    )

                    LaunchedEffect(pinchState.isZooming) {
                        withContext(Dispatchers.Default) {
                            canScroll = !pinchState.isZooming
                            lastCellIndexState = cellsList.indexOf(pinchState.currentCells)
                        }
                    }

                    val dateGroupedState = rememberedDerivedState(
                        searchResults.results,
                        searchResults.dateResults
                    ) {
                        searchResults.dateResults ?: searchResults.results
                    }
                    val relevanceOrderedState = rememberedDerivedState(searchResults.results) {
                        val results = searchResults.results
                        val flatMapped = results.media.map { media ->
                            MediaItem.MediaViewItem<UriMedia>(
                                key = "media_${media.id}",
                                media = media
                            )
                        }
                        results.copy(
                            mappedMedia = flatMapped,
                            mappedMediaWithMonthly = flatMapped,
                            headers = emptyList()
                        )
                    }
                    val mediaState = if (useRelevanceOrder) relevanceOrderedState else dateGroupedState

                    val isMosaicLayout = timelineLayoutType == LAYOUT_MOSAIC && !useRelevanceOrder

                    val sortChipsContent: @Composable (() -> Unit)? = if (isRelevanceSearch) {
                        {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .height(48.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = sortByRelevance,
                                    onClick = { sortByRelevance = true },
                                    label = { Text(stringResource(Res.string.sort_most_accurate)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                                FilterChip(
                                    selected = !sortByRelevance,
                                    onClick = { sortByRelevance = false },
                                    label = { Text(stringResource(Res.string.sort_by_date)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    } else null

                    // Help & Tips ride above the grid as a single full-width leading
                    // item so they scroll seamlessly with the media results.
                    val aboveGridContent: (@Composable () -> Unit)? =
                        if (searchIndexerState.isIndexing || tipsContent != null || sortChipsContent != null) {
                            {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    if (searchIndexerState.isIndexing) {
                                        SearchIndexStatusCard(searchIndexerState)
                                    }
                                    helpTipsContent()
                                    sortChipsContent?.invoke()
                                }
                            }
                        } else null

                    if (isMosaicLayout) {
                        var lastMosaicCellIndexState by lastMosaicCellIndex
                        val mosaicPinchState = rememberMosaicPinchZoomState(
                            initialColumnsIndex = lastMosaicCellIndexState,
                            gridState = rememberLazyGridState(
                                cacheWindow = dpCacheWindow
                            )
                        )
                        val mosaicGridState = mosaicPinchState.gridState

                        LaunchedEffect(mosaicPinchState.isZooming) {
                            lastMosaicCellIndexState = mosaicPinchState.currentColumnsIndex
                        }

                        val mappedData by rememberedDerivedState(mediaState.value) {
                            mediaState.value.mappedMedia
                        }
                        val headers by rememberedDerivedState(mediaState.value) {
                            mediaState.value.headers
                        }
                        val mosaicPaddingValues = remember(contentPadding) {
                            PaddingValues(
                                bottom = contentPadding.calculateBottomPadding() + 128.dp
                            )
                        }
                        MosaicPinchZoomLayout(
                            state = mosaicPinchState,
                            indicatorTopPadding = contentPadding.calculateTopPadding() + 16.dp,
                        ) { currentColumns ->
                        TimelineScroller(
                            modifier = Modifier
                                .padding(mosaicPaddingValues)
                                .padding(top = 32.dp)
                                .padding(vertical = 32.dp),
                            segments = rememberMosaicMonthSegments(
                                mappedData = mappedData,
                                columns = currentColumns,
                                allowHeaders = true,
                                leadingItemCount = if (aboveGridContent != null) 1 else 0,
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
                                allowHeaders = true,
                                aboveGridContent = aboveGridContent,
                                isScrolling = isScrolling,
                                emptyContent = { EmptyMedia() },
                                sharedTransitionScope = sharedTransitionScope,
                                animatedContentScope = animatedContentScope,
                                onRetry = actions.onRetrySearch,
                                onMediaClick = {
                                    actions.onOpenSearchResult(it.id)
                                },
                            )
                        }
                        }
                    } else {
                        GridPinchZoomLayout(
                            state = pinchState,
                            modifier = Modifier.hazeSource(LocalHazeState.current),
                            indicatorTopPadding = contentPadding.calculateTopPadding() + 16.dp,
                        ) {
                            MediaGridView(
                                mediaState = mediaState,
                                metadataState = metadataState,
                                allowSelection = true,
                                showSearchBar = false,
                                enableStickyHeaders = !useRelevanceOrder,
                                hasToolbarOffset = false,
                                paddingValues = remember(contentPadding) {
                                    PaddingValues(
                                        bottom = contentPadding.calculateBottomPadding() + 128.dp
                                    )
                                },
                                canScroll = canScroll,
                                allowHeaders = !useRelevanceOrder,

                                aboveGridContent = aboveGridContent,
                                isScrolling = isScrolling,
                                emptyContent = { EmptyMedia() },
                                sharedTransitionScope = sharedTransitionScope,
                                animatedContentScope = animatedContentScope,
                                onRetry = actions.onRetrySearch,
                            ) {
                                actions.onOpenSearchResult(it.id)
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = searchResults.results.error.isNotEmpty(),
                    enter = enterAnimation,
                    exit = exitAnimation,
                ) {
                    Error(
                        errorMessage = searchResults.results.error,
                        onRetry = actions.onRetrySearch,
                    )
                }
                AnimatedVisibility(
                    visible = searchResults.results.error.isEmpty()
                            && !searchResults.isSearching
                            && (query.isNotEmpty() || selectedImageMedia != null)
                            && searchResults.results.media.isEmpty()
                            && searchResults.hasSearched,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        if (searchIndexerState.isIndexing) {
                            item(key = "empty_search_index_status") {
                                SearchIndexStatusCard(searchIndexerState)
                            }
                        }
                        if (tipsContent != null) {
                            item(key = "empty_help_tips") { helpTipsContent() }
                        }
                        item(key = "empty_results") {
                            EmptyMedia(
                                title = stringResource(Res.string.no_results_found),
                            )
                        }
                    }
                }
            }
            val selector = LocalMediaSelector.current
            val selectedMedia = selector.selectedMedia.collectAsStateWithLifecycle()
            val selectedMediaList by selectedMedia(
                media = searchResults.results.media,
                selectedSet = selectedMedia
            )
            selectionSheetContent(searchResults.results, selectedMediaList)
        }

        // Image search picker bottom sheet
        if (showPickerSheet) {
            ImageSearchPickerSheet(
                onMediaSelected = { media ->
                    showPickerSheet = false
                    actions.onImageSelected(media)
                },
                onDismiss = { showPickerSheet = false }
            )
        }

        // Image search preview dialog
        if (showPreviewDialog && selectedImageMedia != null) {
            ImageSearchPreviewDialog(
                media = selectedImageMedia,
                onDismiss = { showPreviewDialog = false },
                onRemove = {
                    showPreviewDialog = false
                    actions.onClearSelectedMedia()
                },
                onPickAnother = {
                    showPreviewDialog = false
                    showPickerSheet = true
                }
            )
        }
    }
}

@Composable
private fun searchEtaLabel(estimatedRemainingMillis: Long): String {
    val totalMinutes = (estimatedRemainingMillis / 60_000L).coerceAtLeast(0L)
    if (totalMinutes < 1) return stringResource(Res.string.smart_scan_eta_under_minute)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        stringResource(Res.string.smart_scan_eta_hours, hours, minutes)
    } else {
        stringResource(Res.string.smart_scan_eta_minutes, totalMinutes)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchIndexStatusCard(
    state: SearchIndexerState,
    modifier: Modifier = Modifier
) {
    ListItem(
        modifier = modifier
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp)),
        headlineContent = {
            Text(text = stringResource(Res.string.smart_search_index_title))
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Outlined.ImageSearch,
                contentDescription = stringResource(Res.string.smart_search_index_title),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        },
        supportingContent = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    text = when {
                        state.status == SmartScanStatus.RUNNING && state.total > 0 -> stringResource(
                            Res.string.smart_search_index_progress,
                            state.processed,
                            state.total,
                            state.stageNumber,
                            state.stageCount
                        )
                        state.status == SmartScanStatus.RUNNING -> stringResource(
                            Res.string.smart_search_index_preparing,
                            state.stageNumber,
                            state.stageCount
                        )
                        else -> stringResource(
                            Res.string.smart_search_index_waiting,
                            state.stageNumber,
                            state.stageCount
                        )
                    }
                )
                state.estimatedRemainingMillis?.let {
                    Text(
                        text = searchEtaLabel(it),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                if (state.status == SmartScanStatus.RUNNING && state.total > 0) {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        drawStopIndicator = {},
                        gapSize = 0.dp,
                        strokeCap = StrokeCap.Round
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        gapSize = 0.dp,
                        strokeCap = StrokeCap.Round
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.primary,
            headlineColor = MaterialTheme.colorScheme.onPrimary,
            supportingColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
        )
    )
}
