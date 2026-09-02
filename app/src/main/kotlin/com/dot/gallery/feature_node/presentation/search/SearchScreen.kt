package com.dot.gallery.feature_node.presentation.search

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.derivedStateOf

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.feature_node.presentation.common.components.GridPinchZoomLayout
import com.dot.gallery.feature_node.presentation.common.components.rememberGridPinchZoomState
import com.dot.gallery.R
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Constants.cellsList
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.rememberGridSize
import com.dot.gallery.core.Settings.Misc.rememberMosaicGridSize
import com.dot.gallery.core.Settings.Misc.rememberTimelineLayoutType
import com.dot.gallery.core.Settings.Search.rememberSearchHistory
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.navigate
import com.dot.gallery.core.navigateUp
import com.dot.gallery.core.presentation.components.EmptyMedia
import com.dot.gallery.core.presentation.components.Error
import com.dot.gallery.core.presentation.components.SelectionSheet
import com.dot.gallery.feature_node.data.data_source.SmartScanStatus
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaItem
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.presentation.help.data.HelpSearchItem
import com.dot.gallery.feature_node.presentation.classifier.components.CategoryCarousel
import com.dot.gallery.feature_node.presentation.classifier.components.LocationCarousel
import com.dot.gallery.feature_node.presentation.classifier.components.SearchCarousel
import com.dot.gallery.feature_node.presentation.common.components.MediaGridView
import com.dot.gallery.feature_node.presentation.common.components.MosaicMediaGrid
import com.dot.gallery.feature_node.presentation.common.components.MosaicPinchZoomLayout
import com.dot.gallery.feature_node.presentation.common.components.SettingsOptionLayout
import com.dot.gallery.feature_node.presentation.settings.components.SettingsItem
import com.dot.gallery.feature_node.presentation.common.components.TimelineScroller
import com.dot.gallery.feature_node.presentation.common.components.rememberMosaicMonthSegments
import com.dot.gallery.feature_node.presentation.common.components.rememberMosaicPinchZoomState
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.feature_node.presentation.util.selectedMedia
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


internal fun shouldClearSearchOnBack(query: String, hasSelectedImage: Boolean): Boolean =
    query.isNotEmpty() || hasSelectedImage

@OptIn(
    ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun SearchScreen(
    isScrolling: MutableState<Boolean>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    viewModel: SearchViewModel,
) = with(sharedTransitionScope) {
    val eventHandler = LocalEventHandler.current
    val distributor = LocalMediaDistributor.current
    val searchResults by viewModel.searchResultsState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val tipResults by viewModel.tipResults.collectAsStateWithLifecycle()
    val selectedImageMedia by viewModel.selectedImageMedia.collectAsStateWithLifecycle()
    val isModelAvailable by viewModel.isModelAvailable.collectAsStateWithLifecycle()
    val searchableMediaIds by viewModel.searchableMediaIds.collectAsStateWithLifecycle()
    val shouldClearOnBack = shouldClearSearchOnBack(query, selectedImageMedia != null)
    var searchHistory by rememberSearchHistory()

    BackHandler(enabled = shouldClearOnBack) {
        viewModel.clearQuery()
    }

    // Image-to-image search UI state
    var showPickerSheet by rememberSaveable { mutableStateOf(false) }
    var showPreviewDialog by rememberSaveable { mutableStateOf(false) }

    // Categories for the carousel
    val topCategories by viewModel.topCategories.collectAsStateWithLifecycle()
    val topLocations by viewModel.topLocations.collectAsStateWithLifecycle()
    val topMimeTypes by viewModel.topMimeTypes.collectAsStateWithLifecycle()
    val topLensModels by viewModel.topLensModels.collectAsStateWithLifecycle()
    val topMediaModes by viewModel.topMediaModes.collectAsStateWithLifecycle()
    val topGroupTypes by viewModel.topGroupTypes.collectAsStateWithLifecycle()

    val visualSearchLabel = stringResource(R.string.visual_search)
    val historyItems by rememberedDerivedState {
        val visibleHistory = searchHistory.filter { entry ->
            entry.mediaId == null || entry.mediaId in searchableMediaIds
        }.take(5)
        if (visibleHistory.isEmpty()) emptyList() else {
            listOf(SettingsEntity.Header("History")) + visibleHistory.map { entry ->
                if (entry.mediaId != null) {
                    SettingsEntity.Preference(
                        icon = if (entry.mediaUri == null) Icons.Outlined.ImageSearch else null,
                        iconUri = entry.mediaUri,
                        title = entry.mediaLabel ?: entry.query,
                        summary = visualSearchLabel,
                        onClick = { viewModel.restoreImageSearch(entry.mediaId) },
                        tag = entry.mediaId
                    )
                } else {
                    SettingsEntity.Preference(
                        title = entry.query,
                        onClick = { viewModel.setQuery(entry.query, apply = true) }
                    )
                }
            }
        }
    }
    val resources = LocalResources.current
    val suggestionProviders = remember(resources) {
        listOf(
            QuickSuggestionProvider(resources),
            // Add more providers here:
            // MimeTypeSuggestionProvider(resources, mimeTypes),
            // MediaTagSuggestionProvider(resources, tags),
        )
    }
    val suggestionItems = remember(suggestionProviders) {
        suggestionProviders.toSettingsEntities(viewModel)
    }
    val searchIndexerState by viewModel.searchIndexerState.collectAsStateWithLifecycle()
    // Shared "Help & Tips" search results, rendered inline in whichever scroll
    // container is active (history list, media grid, or empty state) so tips and
    // results scroll together as one surface.
    val helpTipsContent: @Composable () -> Unit = {
        if (tipResults.isNotEmpty()) {
            HelpTipsSearchSection(
                tips = tipResults,
                onTipClick = { tip -> tip.route?.let { eventHandler.navigate(it) } },
                onSeeAll = { eventHandler.navigate(Screen.HelpScreen()) }
            )
        }
    }
    Scaffold(
        modifier = Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "search_screen_bounds"),
            animatedVisibilityScope = animatedContentScope
        ),
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
                        IconButton(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = CircleShape
                                ),
                            onClick = {
                                if (shouldClearOnBack) {
                                    viewModel.clearQuery()
                                } else {
                                    eventHandler.navigateUp()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(id = R.string.back_cd),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        val outlineColor = Color.Transparent
                        OutlinedTextField(
                            modifier = Modifier
                                .fillMaxWidth(),
                            value = query,
                            onValueChange = { newQuery ->
                                if (newQuery != " ") {
                                    viewModel.setQuery(newQuery, apply = false)
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
                                onSearch = {
                                    viewModel.setQuery(query, apply = true)
                                    viewModel.addHistory(query)
                                },
                                onDone = {
                                    viewModel.setQuery(query, apply = true)
                                    viewModel.addHistory(query)
                                },
                                onGo = {
                                    viewModel.setQuery(query, apply = true)
                                    viewModel.addHistory(query)
                                },
                                onSend = {
                                    viewModel.setQuery(query, apply = true)
                                    viewModel.addHistory(query)
                                }
                            ),
                            leadingIcon = selectedImageMedia?.let { media ->
                                {
                                    ImageSearchChip(
                                        media = media,
                                        onClick = { showPreviewDialog = true },
                                        onRemove = { viewModel.clearSelectedMedia() }
                                    )
                                }
                            },
                            placeholder = {
                                Text(
                                    text = if (selectedImageMedia != null)
                                        stringResource(R.string.find_similar)
                                    else
                                        stringResource(R.string.search_images_videos),
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
                                            onClick = {
                                                viewModel.setQuery(query, apply = true)
                                                viewModel.addHistory(query)
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Search,
                                                contentDescription = stringResource(id = R.string.search_images_videos),
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    AnimatedVisibility(
                                        visible = isModelAvailable && selectedImageMedia == null && !searchResults.isSearching,
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
                                                contentDescription = stringResource(R.string.search_by_image),
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
                        if (tipResults.isNotEmpty()) {
                            item(key = "help_tips") { helpTipsContent() }
                        }
                        // History and Suggestions
                        SettingsOptionLayout(
                            optionList = historyItems,
                            slimLayout = true,
                            swipeToDismiss = true,
                            onDismiss = { item ->
                                val mediaId = item.tag as? Long
                                if (mediaId != null) {
                                    viewModel.removeImageHistory(mediaId)
                                } else {
                                    viewModel.removeHistory(item.title)
                                }
                                viewModel.clearQuery()
                            }
                        )
                        SettingsOptionLayout(
                            optionList = suggestionItems,
                            slimLayout = true
                        )

                        // Category Carousel
                        if (topCategories.isNotEmpty()) {
                            SettingsOptionLayout(
                                modifier = Modifier.padding(top = 12.dp),
                                optionList = listOf(SettingsEntity.Header(resources.getString(R.string.browse_categories))),
                                slimLayout = true
                            )
                            item {
                                CategoryCarousel(
                                    categories = topCategories,
                                    onCategoryClick = { categoryMedia ->
                                        eventHandler.navigate(
                                            Screen.CategoryViewScreen.categoryId(
                                                categoryMedia.category.id
                                            )
                                        )
                                    },
                                    title = null
                                )
                            }
                        }

                        // Location Carousel
                        if (topLocations.isNotEmpty()) {
                            SettingsOptionLayout(
                                modifier = Modifier.padding(top = 12.dp),
                                optionList = listOf(SettingsEntity.Header(resources.getString(R.string.locations))),
                                slimLayout = true
                            )
                            item {
                                LocationCarousel(
                                    locations = topLocations,
                                    onLocationClick = { locationMedia ->
                                        val city = locationMedia.city
                                        val country = locationMedia.country
                                        val latitude = locationMedia.latitude
                                        val longitude = locationMedia.longitude
                                        if (!city.isNullOrBlank() || !country.isNullOrBlank() ||
                                            latitude != null && longitude != null
                                        ) {
                                            eventHandler.navigate(
                                                Screen.LocationTimelineScreen.location(
                                                    gpsLocationNameCity = city.orEmpty(),
                                                    gpsLocationNameCountry = country.orEmpty(),
                                                    latitude = latitude,
                                                    longitude = longitude,
                                                )
                                            )
                                        } else {
                                            eventHandler.navigate(
                                                Screen.MediaViewScreen.idAndAlbum(locationMedia.media.id, -1L)
                                            )
                                        }
                                    },
                                    title = null
                                )
                            }
                        }

                        // Media Types Carousel
                        if (topMimeTypes.isNotEmpty()) {
                            SettingsOptionLayout(
                                modifier = Modifier.padding(top = 12.dp),
                                optionList = listOf(SettingsEntity.Header(resources.getString(R.string.media_types))),
                                slimLayout = true
                            )
                            item {
                                SearchCarousel(
                                    items = topMimeTypes,
                                    onItemClick = { item ->
                                        viewModel.setMimeTypeQuery(item.key)
                                    }
                                )
                            }
                        }

                        // Camera Models Carousel
                        if (topLensModels.isNotEmpty()) {
                            SettingsOptionLayout(
                                modifier = Modifier.padding(top = 12.dp),
                                optionList = listOf(SettingsEntity.Header(resources.getString(R.string.camera_models))),
                                slimLayout = true
                            )
                            item {
                                SearchCarousel(
                                    items = topLensModels,
                                    onItemClick = { item ->
                                        viewModel.setLensModelQuery(item.key)
                                    }
                                )
                            }
                        }

                        // Media Modes Carousel (Night Mode, Panoramas, etc.)
                        if (topMediaModes.isNotEmpty()) {
                            SettingsOptionLayout(
                                modifier = Modifier.padding(top = 12.dp),
                                optionList = listOf(SettingsEntity.Header(resources.getString(R.string.special_modes))),
                                slimLayout = true
                            )
                            item {
                                SearchCarousel(
                                    items = topMediaModes,
                                    onItemClick = { item ->
                                        viewModel.setMediaModeQuery(item.key)
                                    }
                                )
                            }
                        }

                        // Media Groupings Carousel (Bursts, RAW+JPG, Edits)
                        if (topGroupTypes.isNotEmpty()) {
                            SettingsOptionLayout(
                                modifier = Modifier.padding(top = 12.dp),
                                optionList = listOf(SettingsEntity.Header(resources.getString(R.string.media_groupings))),
                                slimLayout = true
                            )
                            item {
                                SearchCarousel(
                                    items = topGroupTypes,
                                    onItemClick = { item ->
                                        viewModel.setGroupTypeQuery(item.key)
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
                                    contentDescription = stringResource(id = R.string.search_images_videos),
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
                    var lastCellIndex by rememberGridSize()
                    val timelineLayoutType by rememberTimelineLayoutType()

                    var sortByRelevance by rememberSaveable { mutableStateOf(true) }
                    val isRelevanceSearch = searchResults.isRelevanceSearch
                    val useRelevanceOrder = isRelevanceSearch && sortByRelevance

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

                    val dateGroupedState = rememberedDerivedState(
                        searchResults.results,
                        searchResults.dateResults
                    ) {
                        searchResults.dateResults ?: searchResults.results
                    }
                    val relevanceOrderedState = rememberedDerivedState(searchResults.results) {
                        val results = searchResults.results
                        val flatMapped = results.media.map { media ->
                            MediaItem.MediaViewItem<Media.UriMedia>(
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

                    val isMosaicLayout = timelineLayoutType == Settings.Misc.LAYOUT_MOSAIC && !useRelevanceOrder

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
                                    label = { Text(stringResource(R.string.sort_most_accurate)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                                FilterChip(
                                    selected = !sortByRelevance,
                                    onClick = { sortByRelevance = false },
                                    label = { Text(stringResource(R.string.sort_by_date)) },
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
                        if (searchIndexerState.isIndexing || tipResults.isNotEmpty() || sortChipsContent != null) {
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
                                onRetry = viewModel::retrySearch,
                                onMediaClick = {
                                    eventHandler.navigate(Screen.MediaViewScreen.idAndQuery(it.id))
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
                                onRetry = viewModel::retrySearch,
                            ) {
                                eventHandler.navigate(Screen.MediaViewScreen.idAndQuery(it.id))
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
                        onRetry = viewModel::retrySearch,
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
                        if (tipResults.isNotEmpty()) {
                            item(key = "empty_help_tips") { helpTipsContent() }
                        }
                        item(key = "empty_results") {
                            EmptyMedia(
                                title = stringResource(R.string.no_results_found),
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
            SelectionSheet(
                modifier = Modifier.align(Alignment.BottomEnd),
                allMedia = searchResults.results,
                selectedMedia = selectedMediaList
            )
        }

        // Image search picker bottom sheet
        if (showPickerSheet) {
            ImageSearchPickerSheet(
                onMediaSelected = { media ->
                    showPickerSheet = false
                    viewModel.setSelectedMedia(media)
                },
                onDismiss = { showPickerSheet = false }
            )
        }

        // Image search preview dialog
        if (showPreviewDialog && selectedImageMedia != null) {
            ImageSearchPreviewDialog(
                media = selectedImageMedia!!,
                onDismiss = { showPreviewDialog = false },
                onRemove = {
                    showPreviewDialog = false
                    viewModel.clearSelectedMedia()
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
    if (totalMinutes < 1) return stringResource(R.string.smart_scan_eta_under_minute)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        stringResource(R.string.smart_scan_eta_hours, hours, minutes)
    } else {
        stringResource(R.string.smart_scan_eta_minutes, totalMinutes)
    }
}

@Composable
private fun SearchIndexStatusCard(
    state: SearchIndexerState,
    modifier: Modifier = Modifier
) {
    ListItem(
        modifier = modifier
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp)),
        leadingContent = {
            Icon(
                imageVector = Icons.Outlined.ImageSearch,
                contentDescription = stringResource(R.string.smart_search_index_title),
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
                            R.string.smart_search_index_progress,
                            state.processed,
                            state.total,
                            state.stageNumber,
                            state.stageCount
                        )
                        state.status == SmartScanStatus.RUNNING -> stringResource(
                            R.string.smart_search_index_preparing,
                            state.stageNumber,
                            state.stageCount
                        )
                        else -> stringResource(
                            R.string.smart_search_index_waiting,
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
    ) {
        Text(text = stringResource(R.string.smart_search_index_title))
    }
}

/**
 * Compact "Help & Tips" section shown above the media results in the timeline
 * search. Renders the top matching tips; a "See all" row opens the full Help
 * screen. Tapping a tip navigates to its detail (never mutates search state).
 */
@Composable
private fun HelpTipsSearchSection(
    tips: List<HelpSearchItem>,
    onTipClick: (HelpSearchItem) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Build the same settings-DSL structure the search history uses: a section
    // Header followed by slim Preference rows, rendered through SettingsItem so the
    // grouping, corners and typography match history exactly.
    val entities = buildList {
        add(SettingsEntity.Header(stringResource(R.string.help_tips_section_title)))
        tips.take(3).forEach { tip ->
            add(
                SettingsEntity.Preference(
                    icon = tip.icon,
                    title = tip.title,
                    summary = tip.subtitle.ifBlank { null },
                    onClick = { onTipClick(tip) }
                )
            )
        }
        if (tips.size > 3) {
            add(
                SettingsEntity.Preference(
                    title = stringResource(R.string.help_search_see_all, tips.size),
                    onClick = onSeeAll
                )
            )
        }
    }
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        entities.forEachIndexed { index, item ->
            val position = when (index) {
                0 -> if (entities.size == 1) Position.Alone else Position.Top
                entities.lastIndex ->
                    if (entities[index - 1] is SettingsEntity.Header) Position.Alone
                    else Position.Bottom

                else -> {
                    val previous = entities[index - 1]
                    val next = entities[index + 1]
                    when {
                        previous is SettingsEntity.Header && next is SettingsEntity.Header -> Position.Alone
                        previous is SettingsEntity.Header -> Position.Top
                        next is SettingsEntity.Header -> Position.Bottom
                        else -> Position.Middle
                    }
                }
            }
            val newItem = when (item) {
                is SettingsEntity.Preference -> item.copy(screenPosition = position)
                else -> item
            }
            SettingsItem(
                item = newItem,
                slimLayout = true,
                tintIcon = newItem.iconUri == null
            )
        }
    }
}
