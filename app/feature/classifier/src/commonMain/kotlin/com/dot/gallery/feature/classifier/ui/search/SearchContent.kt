/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.classifier.ui.search

import com.dot.gallery.core.presentation.components.NavigationBackButton
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.Constants.cellsList
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.presentation.components.EmptyMedia
import com.dot.gallery.core.presentation.components.GridPinchZoomLayout
import com.dot.gallery.core.presentation.components.MediaGridView
import com.dot.gallery.core.presentation.components.MosaicMediaGrid
import com.dot.gallery.core.presentation.components.MosaicPinchZoomLayout
import com.dot.gallery.core.presentation.components.TimelineScroller
import com.dot.gallery.core.presentation.components.rememberGridPinchZoomState
import com.dot.gallery.core.presentation.components.rememberMosaicMonthSegments
import com.dot.gallery.core.presentation.components.rememberMosaicPinchZoomState
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.presentation.util.selectedMedia
import com.dot.gallery.core.presentation.util.sharedSearchBounds
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.core.ui.util.rememberedDerivedState
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.model.LocationMedia
import com.dot.gallery.domain.model.MediaItem
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.SearchHistory
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.model.locationIdentityKey
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.domain.settings.LAYOUT_MOSAIC
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.ui.theme.Spacing
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.classifier.generated.resources.Res
import com.dot.gallery.feature.classifier.generated.resources.camera_models
import com.dot.gallery.feature.classifier.generated.resources.categories
import com.dot.gallery.feature.classifier.generated.resources.category_media_count
import com.dot.gallery.feature.classifier.generated.resources.find_similar
import com.dot.gallery.feature.classifier.generated.resources.media_groupings
import com.dot.gallery.feature.classifier.generated.resources.media_types
import com.dot.gallery.feature.classifier.generated.resources.no_results_found
import com.dot.gallery.feature.classifier.generated.resources.search_by_image
import com.dot.gallery.feature.classifier.generated.resources.search_clear_history
import com.dot.gallery.feature.classifier.generated.resources.search_clear_query
import com.dot.gallery.feature.classifier.generated.resources.search_error_title
import com.dot.gallery.feature.classifier.generated.resources.search_no_results_body
import com.dot.gallery.feature.classifier.generated.resources.search_no_results_body_basic
import com.dot.gallery.feature.classifier.generated.resources.search_no_results_title
import com.dot.gallery.feature.classifier.generated.resources.search_placeholder
import com.dot.gallery.feature.classifier.generated.resources.search_placeholder_basic
import com.dot.gallery.feature.classifier.generated.resources.search_places
import com.dot.gallery.feature.classifier.generated.resources.search_recent
import com.dot.gallery.feature.classifier.generated.resources.search_results_count
import com.dot.gallery.feature.classifier.generated.resources.search_searching
import com.dot.gallery.feature.classifier.generated.resources.search_see_all
import com.dot.gallery.feature.classifier.generated.resources.search_suggestion_album
import com.dot.gallery.feature.classifier.generated.resources.search_suggestion_category
import com.dot.gallery.feature.classifier.generated.resources.search_suggestion_place
import com.dot.gallery.feature.classifier.generated.resources.search_suggestion_recent
import com.dot.gallery.feature.classifier.generated.resources.search_suggestion_smart
import com.dot.gallery.feature.classifier.generated.resources.search_suggestion_text
import com.dot.gallery.feature.classifier.generated.resources.search_try
import com.dot.gallery.feature.classifier.generated.resources.search_try_again
import com.dot.gallery.feature.classifier.generated.resources.special_modes
import com.dot.gallery.feature.classifier.generated.resources.suggestions
import com.dot.gallery.feature.classifier.generated.resources.visual_search


/** Which body the search screen shows under its bar. */
enum class SearchPhase { Home, Typing, Searching, Error, NoResults, Results }

/** Pure mapping from the result pipeline and query to the visible [SearchPhase]. */
fun searchPhase(results: SearchResultsState, query: String): SearchPhase = when {
    results.isSearching -> SearchPhase.Searching
    results.results.error.isNotEmpty() -> SearchPhase.Error
    results.hasSearched && results.results.media.isEmpty() && results.textMatches.isEmpty() -> SearchPhase.NoResults
    results.hasSearched -> SearchPhase.Results
    query.isNotBlank() -> SearchPhase.Typing
    else -> SearchPhase.Home
}

/**
 * The timeline search surface — search bar, the browse home, typed suggestions and the result
 * grids (grid or mosaic) with a floating sort dock.
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
    var promoDismissed by rememberSaveable { mutableStateOf(false) }

    val recentHistory by rememberedDerivedState {
        searchHistory
            .filter { entry -> entry.mediaId == null || entry.mediaId in state.searchableMediaIds }
            .distinctBy { entry -> entry.mediaId?.toString() ?: entry.query.lowercase() }
            .take(10)
    }
    val historyMatches = remember(query, searchHistory) { matchingHistory(query, searchHistory) }
    val suggestionProviders = rememberSearchSuggestionProviders()
    val phase = searchPhase(searchResults, query)

    Scaffold(
        modifier = Modifier.sharedSearchBounds(sharedTransitionScope, animatedContentScope),
        topBar = {
            SearchTopBar(
                query = query,
                selectedImageMedia = selectedImageMedia,
                canSearchByImage = state.isModelAvailable && selectedImageMedia == null &&
                    !searchResults.isSearching,
                isModelAvailable = state.isModelAvailable,
                focusRequester = searchFieldFocus,
                onBack = { if (shouldClearOnBack) actions.onClearQuery() else actions.onBack() },
                actions = actions,
                onPreviewImage = { showPreviewDialog = true },
                onPickImage = { showPickerSheet = true },
            )
        }
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize(),
        ) {
            AnimatedContent(
                targetState = phase,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "searchPhase",
            ) { currentPhase ->
                when (currentPhase) {
                    SearchPhase.Home -> SearchHome(
                        state = state,
                        actions = actions,
                        recentHistory = recentHistory,
                        suggestionProviders = suggestionProviders,
                        tipsContent = tipsContent,
                        showPromo = !state.isModelAvailable && !promoDismissed,
                        onDismissPromo = { promoDismissed = true },
                    )

                    SearchPhase.Typing -> SearchTyping(
                        state = state,
                        actions = actions,
                        historyMatches = historyMatches,
                        tipsContent = tipsContent,
                    )

                    SearchPhase.Searching -> SearchStateColumn(state.searchIndexerState) {
                        item(key = "search_loading") {
                            SearchLoadingState(
                                status = stringResource(Res.string.search_searching, state.searchableMediaIds.size)
                            )
                        }
                        item(key = "search_skeleton") { SearchSkeletonGrid() }
                    }

                    SearchPhase.Error -> SearchStateColumn(state.searchIndexerState) {
                        item(key = "search_error") {
                            SearchMessageState(
                                icon = Icons.Outlined.ErrorOutline,
                                title = stringResource(Res.string.search_error_title),
                                body = searchResults.results.error,
                                iconContainer = MaterialTheme.colorScheme.errorContainer,
                                iconTint = MaterialTheme.colorScheme.onErrorContainer,
                            ) {
                                Button(onClick = actions.onRetrySearch) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(Spacing.Small))
                                    Text(stringResource(Res.string.search_try_again))
                                }
                            }
                        }
                    }

                    SearchPhase.NoResults -> SearchStateColumn(state.searchIndexerState) {
                        if (tipsContent != null) {
                            item(key = "empty_help_tips") { tipsContent() }
                        }
                        item(key = "empty_results") {
                            SearchNoResults(
                                state = state,
                                actions = actions,
                                onPickImage = { showPickerSheet = true },
                            )
                        }
                    }

                    SearchPhase.Results -> SearchResults(
                        state = state,
                        actions = actions,
                        contentPadding = contentPadding,
                        isScrolling = isScrolling,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedContentScope = animatedContentScope,
                        tipsContent = tipsContent,
                        lastCellIndex = lastCellIndex,
                        lastMosaicCellIndex = lastMosaicCellIndex,
                    )
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

/** Back button plus the pill search field: search glyph or image chip, clear or image search. */
@Composable
private fun SearchTopBar(
    query: String,
    selectedImageMedia: UriMedia?,
    canSearchByImage: Boolean,
    isModelAvailable: Boolean,
    focusRequester: FocusRequester,
    onBack: () -> Unit,
    actions: SearchActions,
    onPreviewImage: () -> Unit,
    onPickImage: () -> Unit,
) {
    Row(
        modifier = Modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .padding(start = Spacing.Small, end = Spacing.ScreenHorizontal, top = Spacing.Small, bottom = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Moves into the wide-window rail like every other screen's back button.
        NavigationBackButton(
            paddingModifier = Modifier.padding(start = Spacing.Small, end = Spacing.Small),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            forcedAction = onBack,
        )
        val fieldColor = MaterialTheme.colorScheme.surfaceContainerHigh
        TextField(
            modifier = Modifier
                .focusRequester(focusRequester)
                .weight(1f)
                .heightIn(min = 56.dp),
            value = query,
            onValueChange = { newQuery ->
                if (newQuery != " ") {
                    actions.onQueryChange(newQuery, false)
                }
            },
            shape = CircleShape,
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = fieldColor,
                unfocusedContainerColor = fieldColor,
                disabledContainerColor = fieldColor,
                errorContainerColor = fieldColor,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { if (query.isNotBlank()) actions.onSubmitSearch(query) },
            ),
            leadingIcon = if (selectedImageMedia != null) {
                {
                    ImageSearchChip(
                        media = selectedImageMedia,
                        onClick = onPreviewImage,
                        onRemove = actions.onClearSelectedMedia,
                    )
                }
            } else {
                {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            placeholder = {
                Text(
                    text = when {
                        selectedImageMedia != null -> stringResource(Res.string.find_similar)
                        isModelAvailable -> stringResource(Res.string.search_placeholder)
                        else -> stringResource(Res.string.search_placeholder_basic)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            },
            singleLine = true,
            trailingIcon = when {
                query.isNotEmpty() -> {
                    {
                        IconButton(onClick = actions.onClearQuery) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(Res.string.search_clear_query),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                canSearchByImage -> {
                    {
                        IconButton(onClick = onPickImage) {
                            Icon(
                                imageVector = Icons.Outlined.ImageSearch,
                                contentDescription = stringResource(Res.string.search_by_image),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                else -> null
            },
        )
    }
}

/** Column for transient states: index progress on top, then [content]. */
@Composable
private fun SearchStateColumn(
    indexerState: SearchIndexerState,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (indexerState.isIndexing) {
            item(key = "search_index_status") { SearchIndexStatusCard(indexerState) }
        }
        content()
    }
}

private fun openLocation(actions: SearchActions, location: LocationMedia) {
    val city = location.city
    val country = location.country
    val latitude = location.latitude
    val longitude = location.longitude
    if (!city.isNullOrBlank() || !country.isNullOrBlank() || latitude != null && longitude != null) {
        actions.onOpenLocation(city.orEmpty(), country.orEmpty(), latitude, longitude)
    } else {
        actions.onOpenMedia(location.media.id)
    }
}

private fun openHistory(actions: SearchActions, entry: SearchHistory) {
    val mediaId = entry.mediaId
    if (mediaId != null) actions.onRestoreImageSearch(mediaId) else actions.onQueryChange(entry.query, true)
}

private fun removeHistory(actions: SearchActions, entry: SearchHistory) {
    val mediaId = entry.mediaId
    if (mediaId != null) actions.onRemoveImageHistory(mediaId) else actions.onRemoveHistory(entry.query)
}

/** Browse home: recent searches, suggestions, then categories, places and metadata groups. */
@Composable
private fun SearchHome(
    state: SearchState,
    actions: SearchActions,
    recentHistory: List<SearchHistory>,
    suggestionProviders: List<SearchSuggestionProvider>,
    tipsContent: (@Composable () -> Unit)?,
    showPromo: Boolean,
    onDismissPromo: () -> Unit,
) {
    val visualSearchLabel = stringResource(Res.string.visual_search)
    val seeAll = stringResource(Res.string.search_see_all)
    val categoriesTitle = stringResource(Res.string.categories)
    val placesTitle = stringResource(Res.string.search_places)
    val mediaTypesTitle = stringResource(Res.string.media_types)
    val camerasTitle = stringResource(Res.string.camera_models)
    val modesTitle = stringResource(Res.string.special_modes)
    val groupsTitle = stringResource(Res.string.media_groupings)
    val recentTitle = stringResource(Res.string.search_recent)
    val clearLabel = stringResource(Res.string.search_clear_history)
    val suggestionsTitle = stringResource(Res.string.suggestions)
    val categoryCount: @Composable (Int) -> String = { stringResource(Res.string.category_media_count, it) }
    val showVisualHistory = state.isModelAvailable
    val visibleHistory = if (showVisualHistory) recentHistory else recentHistory.filter { it.mediaId == null }
    val suggestions = suggestionProviders.flatMap { it.suggestions }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.ExtraLarge),
    ) {
        if (state.searchIndexerState.isIndexing) {
            item(key = "search_index_status") { SearchIndexStatusCard(state.searchIndexerState) }
        }
        if (showPromo) {
            item(key = "smart_search_promo") {
                SmartSearchPromoCard(onTurnOn = actions.onSetUpSmartSearch, onDismiss = onDismissPromo)
            }
        }
        if (tipsContent != null) {
            item(key = "help_tips") { tipsContent() }
        }
        if (visibleHistory.isNotEmpty()) {
            item(key = "recent_header") {
                SearchSectionHeader(title = recentTitle, action = clearLabel, onAction = actions.onClearHistory)
            }
            item(key = "recent_chips") {
                SearchHorizontalRow {
                    items(
                        items = visibleHistory,
                        key = { entry -> entry.mediaId?.let { "image_$it" } ?: "text_${entry.query.lowercase()}" }
                    ) { entry ->
                        val isImage = entry.mediaId != null
                        SearchHistoryChip(
                            label = if (isImage) entry.mediaLabel ?: visualSearchLabel else entry.query,
                            icon = if (isImage) Icons.Outlined.ImageSearch else Icons.Outlined.History,
                            thumbnailUri = if (isImage) entry.mediaUri else null,
                            onClick = { openHistory(actions, entry) },
                            onRemove = { removeHistory(actions, entry) },
                        )
                    }
                }
            }
        }
        if (suggestions.isNotEmpty()) {
            item(key = "suggestions_header") { SearchSectionHeader(title = suggestionsTitle) }
            item(key = "suggestions_chips") {
                SearchHorizontalRow {
                    items(items = suggestions, key = { "suggestion_${it.title}" }) { suggestion ->
                        SearchSuggestionChip(
                            label = suggestion.title,
                            icon = suggestion.icon,
                            onClick = { suggestion.action(actions) },
                        )
                    }
                }
            }
        }
        if (state.isModelAvailable && state.topCategories.isNotEmpty()) {
            item(key = "categories_header") {
                SearchSectionHeader(
                    title = categoriesTitle,
                    count = state.topCategories.size.toString(),
                    action = seeAll,
                    onAction = actions.onSeeAllCategories,
                )
            }
            item(key = "categories_row") {
                SearchHorizontalRow(spacing = 12.dp) {
                    items(items = state.topCategories, key = { "category_${it.id}" }) { category ->
                        SearchCategoryTile(
                            category = category,
                            countLabel = categoryCount(category.mediaCount),
                            onClick = { actions.onOpenCategory(category.id) },
                        )
                    }
                }
            }
        }
        if (state.topLocations.isNotEmpty()) {
            item(key = "places_header") {
                SearchSectionHeader(
                    title = placesTitle,
                    count = state.topLocations.size.toString(),
                    action = seeAll,
                    onAction = actions.onSeeAllPlaces,
                )
            }
            item(key = "places_row") {
                SearchHorizontalRow(spacing = 12.dp) {
                    items(items = state.topLocations, key = { it.locationIdentityKey() }) { location ->
                        SearchPlaceCard(location = location, onClick = { openLocation(actions, location) })
                    }
                }
            }
        }
        typeSection(
            key = "media_types",
            title = mediaTypesTitle,
            items = state.topMimeTypes,
            onClick = { item -> actions.onMimeTypeFilter(item.key, false) },
        )
        if (state.topLensModels.isNotEmpty()) {
            item(key = "cameras_header") {
                SearchSectionHeader(title = camerasTitle, count = state.topLensModels.size.toString())
            }
            item(key = "cameras_chips") {
                SearchHorizontalRow {
                    items(items = state.topLensModels, key = { "lens_${it.key}" }) { lens ->
                        SearchFilterChip(label = lens.label, onClick = { actions.onLensModelFilter(lens.key) })
                    }
                }
            }
        }
        typeSection(
            key = "special_modes",
            title = modesTitle,
            items = state.topMediaModes,
            onClick = { item -> actions.onMediaModeFilter(item.key) },
        )
        typeSection(
            key = "media_groupings",
            title = groupsTitle,
            items = state.topGroupTypes,
            onClick = { item -> actions.onGroupTypeFilter(item.key) },
        )
    }
}

private fun LazyListScope.typeSection(
    key: String,
    title: String,
    items: List<SearchMediaItem>,
    onClick: (SearchMediaItem) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "${key}_header") { SearchSectionHeader(title = title) }
    item(key = "${key}_grid") {
        SearchTypeGrid(
            entries = items.map { item ->
                SearchTypeEntry(
                    key = item.key,
                    title = item.label,
                    count = item.count,
                    icon = searchTypeIcon(item.key),
                    onClick = { onClick(item) },
                )
            }
        )
    }
}

/** Live suggestions while typing: the query itself, matching history and library entities. */
@Composable
private fun SearchTyping(
    state: SearchState,
    actions: SearchActions,
    historyMatches: List<SearchHistory>,
    tipsContent: (@Composable () -> Unit)?,
) {
    val query = state.query.trim()
    val recentLabel = stringResource(Res.string.search_suggestion_recent)
    val placeLabel = stringResource(Res.string.search_suggestion_place)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = Spacing.Small, bottom = Spacing.ExtraLarge),
    ) {
        if (state.searchIndexerState.isIndexing) {
            item(key = "search_index_status") { SearchIndexStatusCard(state.searchIndexerState) }
        }
        item(key = "typed_query") {
            SearchSuggestionRow(
                title = query,
                subtitle = stringResource(
                    if (state.isModelAvailable) Res.string.search_suggestion_smart else Res.string.search_suggestion_text
                ),
                onClick = { actions.onSubmitSearch(query) },
                leading = { SearchRowIcon(Icons.Outlined.Search) },
            )
        }
        items(items = historyMatches, key = { "typed_history_${it.query.lowercase()}" }) { entry ->
            SearchSuggestionRow(
                title = entry.query,
                subtitle = recentLabel,
                onClick = { actions.onQueryChange(entry.query, true) },
                onFill = { actions.onQueryChange(entry.query, false) },
                leading = { SearchRowIcon(Icons.Outlined.History) },
            )
        }
        items(items = state.typedSuggestions, key = { it.key }) { suggestion ->
            when (suggestion) {
                is TypedSearchSuggestion.Category -> SearchSuggestionRow(
                    title = suggestion.title,
                    subtitle = stringResource(Res.string.search_suggestion_category, suggestion.item.mediaCount),
                    onClick = { actions.onOpenCategory(suggestion.item.id) },
                    leading = {
                        val thumbnail = suggestion.item.thumbnailMedia
                        if (thumbnail != null) {
                            SearchRowImage(model = thumbnail.getUri(), signature = thumbnail)
                        } else {
                            SearchRowIcon(Icons.Outlined.ImageSearch)
                        }
                    },
                )

                is TypedSearchSuggestion.Place -> SearchSuggestionRow(
                    title = suggestion.title,
                    subtitle = placeLabel,
                    onClick = { openLocation(actions, suggestion.location) },
                    leading = { SearchRowIcon(Icons.Outlined.LocationOn) },
                )

                is TypedSearchSuggestion.Album -> SearchSuggestionRow(
                    title = suggestion.title,
                    subtitle = stringResource(
                        Res.string.search_suggestion_album,
                        suggestion.album.count.toInt()
                    ),
                    onClick = { actions.onOpenAlbum(suggestion.album.id, suggestion.album.label) },
                    leading = {
                        if (suggestion.album.uri.toString().isNotEmpty()) {
                            SearchRowImage(model = suggestion.album.uri.toString(), signature = suggestion.album.uri)
                        } else {
                            SearchRowIcon(Icons.Outlined.PhotoAlbum)
                        }
                    },
                )
            }
        }
        if (tipsContent != null) {
            item(key = "help_tips") { tipsContent() }
        }
    }
}

/** No matches: explanation, image search when available, and category names to try. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchNoResults(
    state: SearchState,
    actions: SearchActions,
    onPickImage: () -> Unit,
) {
    val query = state.query
    val canSearchByImage = state.isModelAvailable && state.selectedImageMedia == null
    val tryTerms = state.topCategories
        .map { it.name }
        .filterNot { it.equals(query, ignoreCase = true) }
        .take(3)
    SearchMessageState(
        icon = Icons.Outlined.SearchOff,
        title = if (query.isNotBlank()) stringResource(Res.string.search_no_results_title, query)
        else stringResource(Res.string.no_results_found),
        body = stringResource(
            if (canSearchByImage) Res.string.search_no_results_body else Res.string.search_no_results_body_basic
        ),
    ) {
        if (canSearchByImage) {
            FilledTonalButton(onClick = onPickImage) {
                Icon(Icons.Outlined.ImageSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(Spacing.Small))
                Text(stringResource(Res.string.search_by_image))
            }
        }
        if (tryTerms.isNotEmpty()) {
            Text(
                text = stringResource(Res.string.search_try),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                tryTerms.forEach { term ->
                    SearchFilterChip(label = term, onClick = { actions.onSubmitSearch(term) })
                }
            }
        }
    }
}

/** Result grid (or mosaic) with the frosted sort-and-count dock floating over it. */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
private fun SearchResults(
    state: SearchState,
    actions: SearchActions,
    contentPadding: PaddingValues,
    isScrolling: MutableState<Boolean>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    tipsContent: (@Composable () -> Unit)?,
    lastCellIndex: MutableState<Int>,
    lastMosaicCellIndex: MutableState<Int>,
) {
    val distributor = LocalMediaDistributor.current
    val searchResults = state.searchResults
    val searchIndexerState = state.searchIndexerState
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

    // Index progress and Help & Tips ride above the grid as a single full-width leading
    // item so they scroll seamlessly with the media results.
    val textMatches = searchResults.textMatches
    val aboveGridContent: (@Composable () -> Unit)? =
        if (searchIndexerState.isIndexing || tipsContent != null || textMatches.isNotEmpty()) {
            {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (searchIndexerState.isIndexing) {
                        SearchIndexStatusCard(searchIndexerState)
                    }
                    tipsContent?.invoke()
                    if (textMatches.isNotEmpty()) {
                        TextInPhotosSection(
                            matches = textMatches,
                            otherCount = searchResults.results.media.size,
                            onOpen = { actions.onOpenTextMatch(it.media.id) },
                        )
                    }
                }
            }
        } else null

    Box(modifier = Modifier.fillMaxSize()) {
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

        val selector = LocalMediaSelector.current
        val isSelectionActive by selector.isSelectionActive.collectAsStateWithLifecycle()
        val resultCount = searchResults.results.media.size
        AnimatedVisibility(
            visible = !isSelectionActive,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.Medium),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            SearchResultsDock(
                countLabel = pluralStringResource(Res.plurals.search_results_count, resultCount, resultCount),
                showSort = isRelevanceSearch,
                sortByRelevance = sortByRelevance,
                onSortChange = { sortByRelevance = it },
            )
        }
    }
}
