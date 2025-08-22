package com.dot.gallery.feature_node.presentation.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Portrait
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.pinchzoomgrid.PinchZoomGridLayout
import com.dokar.pinchzoomgrid.rememberPinchZoomGridState
import com.dot.gallery.R
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Constants.cellsList
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.Settings.Misc.rememberGridSize
import com.dot.gallery.core.Settings.Search.rememberSearchHistory
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.navigate
import com.dot.gallery.core.navigateUp
import com.dot.gallery.core.presentation.components.EmptyMedia
import com.dot.gallery.core.presentation.components.SelectionSheet
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.presentation.common.components.MediaGridView
import com.dot.gallery.feature_node.presentation.common.components.MediaImage
import com.dot.gallery.feature_node.presentation.common.components.SettingsOptionLayout
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.settings.components.SettingsItem
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.feature_node.presentation.util.mediaSharedElement
import com.dot.gallery.feature_node.presentation.util.selectedMedia
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


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
    var searchHistory by rememberSearchHistory()

    val historyItems by rememberedDerivedState {
        if (searchHistory.isEmpty()) {
            emptyList()
        } else {
            listOf(SettingsEntity.Header("History")) +
                    searchHistory.map {
                        SettingsEntity.Preference(
                            title = it.query,
                            onClick = { viewModel.setQuery(it.query, apply = true) })
                    }.take(5)
        }
    }
    val suggestionItems = remember {
        listOf(
            SettingsEntity.Header("Suggestions"),
            SettingsEntity.Preference(
                title = "Screenshots",
                icon = Icons.Outlined.Screenshot,
                onClick = {
                    viewModel.setQuery("Screenshots", apply = true)
                }
            ),
            SettingsEntity.Preference(
                title = "Videos",
                icon = Icons.Outlined.PlayCircle,
                onClick = {
                    viewModel.setQuery("Videos", apply = true)
                }
            ),
            SettingsEntity.Preference(
                title = "Selfies",
                icon = Icons.Outlined.Portrait,
                onClick = {
                    viewModel.setQuery("Selfies", apply = true)
                }
            ),
        )
    }
    val searchIndexerState by viewModel.searchIndexerState.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    Scaffold(
        modifier = Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "search_screen_bounds"),
            animatedVisibilityScope = animatedContentScope
        ),
        topBar = {
            Box(
                modifier = Modifier.statusBarsPadding()
            ) {
                Column(
                    modifier = Modifier.statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val eventHandler = LocalEventHandler.current
                        IconButton(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = CircleShape
                                ),
                            onClick = {
                                if (query.isNotEmpty()) {
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
                            onValueChange = { viewModel.setQuery(it, apply = false) },
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
                                }
                            ),
                            placeholder = {
                                Text(
                                    text = "Search images & videos",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp)
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
                                        contentDescription = "Search",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
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
                AnimatedVisibility(
                    modifier = Modifier.padding(vertical = 16.dp),
                    visible = searchIndexerState.isIndexing && searchIndexerState.progress > 0.1f && searchIndexerState.progress < 100f,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    ListItem(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.ImageSearch,
                                contentDescription = "Search Indexer",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        },
                        headlineContent = {
                            Text(
                                text = "Search function limited"
                            )
                        },
                        supportingContent = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Text(
                                    text = "Search indexer is running. Results may not be accurate."
                                )
                                LinearProgressIndicator(
                                    progress = { searchIndexerState.progress / 100f },
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    trackColor = MaterialTheme.colorScheme.primaryContainer.copy(
                                        alpha = 0.5f
                                    ),
                                    drawStopIndicator = {},
                                    gapSize = 0.dp,
                                    strokeCap = StrokeCap.Round
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            headlineColor = MaterialTheme.colorScheme.onPrimary,
                            supportingColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        )
                    )
                }
                AnimatedVisibility(
                    modifier = Modifier.padding(top = 16.dp),
                    visible = !searchResults.isSearching && !searchResults.hasSearched,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        SettingsOptionLayout(
                            optionList = historyItems,
                            slimLayout = true,
                            swipeToDismiss = true,
                            onDismiss = { item ->
                                viewModel.removeHistory(item.title)
                                viewModel.clearQuery()
                            }
                        )
                        SettingsOptionLayout(
                            optionList = suggestionItems,
                            slimLayout = true
                        )

                        if (locations.isNotEmpty()) {
                            item {
                                SettingsItem(
                                    modifier = Modifier.animateItem(),
                                    item = SettingsEntity.Header("Locations"),
                                )
                            }
                        }
                        item {
                            LazyRow(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(
                                    items = locations,
                                    key = { it }
                                ) { (media, location) ->
                                    with(sharedTransitionScope) {
                                        Column(
                                            modifier = Modifier.width(116.dp),
                                            verticalArrangement = Arrangement.spacedBy(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            MediaImage(
                                                modifier = Modifier
                                                    .size(116.dp)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .mediaSharedElement(
                                                        media = media,
                                                        animatedVisibilityScope = animatedContentScope
                                                    ),
                                                media = media,
                                                onMediaClick = { media ->
                                                    /*eventHandler.navigate(
                                                        Screen.MediaViewScreen.idAndCategory(
                                                            media.id,
                                                            category!!
                                                        )
                                                    )*/
                                                },
                                                onItemSelect = {
                                                    /*eventHandler.navigate(
                                                        Screen.CategoryViewScreen.category(
                                                            category!!
                                                        )
                                                    )*/
                                                },
                                                canClick = { true }
                                            )
                                            Text(
                                                text = location,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                textAlign = TextAlign.Center,
                                                maxLines = 2,
                                                overflow = TextOverflow.MiddleEllipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = searchResults.isSearching,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingIndicator(
                            modifier = Modifier.size(128.dp),
                        )
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                AnimatedVisibility(
                    visible = !searchResults.isSearching
                            && searchResults.results.media.isNotEmpty()
                            && searchResults.hasSearched,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    val metadataState =
                        distributor.metadataFlow.collectAsStateWithLifecycle(MediaMetadataState())
                    var canScroll by rememberSaveable { mutableStateOf(true) }
                    var lastCellIndex by rememberGridSize()
                    val dpCacheWindow = LazyLayoutCacheWindow(ahead = 200.dp, behind = 100.dp)
                    val pinchState = rememberPinchZoomGridState(
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
                    PinchZoomGridLayout(
                        state = pinchState,
                        modifier = Modifier.hazeSource(LocalHazeState.current)
                    ) {
                        BackHandler {
                            viewModel.clearQuery()
                        }
                        val mediaState = rememberedDerivedState { searchResults.results }
                        MediaGridView(
                            mediaState = mediaState,
                            metadataState = metadataState,
                            allowSelection = true,
                            showSearchBar = false,
                            enableStickyHeaders = false,
                            paddingValues = remember(contentPadding) {
                                PaddingValues(
                                    bottom = contentPadding.calculateBottomPadding() + 128.dp
                                )
                            },
                            canScroll = canScroll,
                            allowHeaders = false,
                            showMonthlyHeader = false,
                            aboveGridContent = null,
                            isScrolling = isScrolling,
                            emptyContent = { EmptyMedia() },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedContentScope = animatedContentScope
                        ) {
                            eventHandler.navigate(Screen.MediaViewScreen.idAndQuery(it.id))
                        }
                    }
                }
                AnimatedVisibility(
                    visible = !searchResults.isSearching
                            && query.isNotEmpty()
                            && searchResults.results.media.isEmpty()
                            && searchResults.hasSearched,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    EmptyMedia(
                        title = "No results found",
                    )
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
    }
}
