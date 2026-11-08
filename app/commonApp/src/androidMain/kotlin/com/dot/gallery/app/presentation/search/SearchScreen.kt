package com.dot.gallery.app.presentation.search

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dot.gallery.ui.theme.Spacing
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Position
import com.dot.gallery.core.Settings.Misc.rememberGridSize
import com.dot.gallery.core.Settings.Misc.rememberMosaicGridSize
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.navigate
import com.dot.gallery.core.navigateUp
import com.dot.gallery.core.presentation.components.SelectionSheet
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.feature.classifier.ui.components.CategoryCarouselItem
import com.dot.gallery.feature.classifier.ui.search.SearchActions
import com.dot.gallery.feature.classifier.ui.search.SearchContent
import com.dot.gallery.feature.classifier.ui.search.SearchState
import com.dot.gallery.feature.help.domain.HelpSearchItem
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.feature.help.toScreenRoute
import com.dot.gallery.feature.search.SearchViewModel
import com.dot.gallery.domain.uri.query
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.help_search_see_all
import com.dot.gallery.generated.resources.help_tips_hide
import com.dot.gallery.generated.resources.help_tips_matches
import com.dot.gallery.generated.resources.help_tips_show
import com.dot.gallery.generated.resources.help_tips_section_title


/**
 * App shell for `:feature:classifier`'s [SearchContent] — collects the
 * [SearchViewModel] state, wires routes through `Screen`, and supplies the
 * "Help & Tips" block (kept app-side: it depends on `:feature:help` types).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SearchScreen(
    isScrolling: MutableState<Boolean>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    viewModel: SearchViewModel,
) {
    val eventHandler = LocalEventHandler.current
    val searchResults by viewModel.searchResultsState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val tipResults by viewModel.tipResults.collectAsStateWithLifecycle()
    val selectedImageMedia by viewModel.selectedImageMedia.collectAsStateWithLifecycle()
    val isModelAvailable by viewModel.isModelAvailable.collectAsStateWithLifecycle()
    val searchableMediaIds by viewModel.searchableMediaIds.collectAsStateWithLifecycle()
    val searchIndexerState by viewModel.searchIndexerState.collectAsStateWithLifecycle()
    val topCategories by viewModel.topCategories.collectAsStateWithLifecycle()
    val topLocations by viewModel.topLocations.collectAsStateWithLifecycle()
    val topMimeTypes by viewModel.topMimeTypes.collectAsStateWithLifecycle()
    val topLensModels by viewModel.topLensModels.collectAsStateWithLifecycle()
    val topMediaModes by viewModel.topMediaModes.collectAsStateWithLifecycle()
    val topGroupTypes by viewModel.topGroupTypes.collectAsStateWithLifecycle()
    val typedSuggestions by viewModel.typedSuggestions.collectAsStateWithLifecycle()

    SearchContent(
        state = SearchState(
            query = query,
            searchResults = searchResults,
            selectedImageMedia = selectedImageMedia,
            isModelAvailable = isModelAvailable,
            searchableMediaIds = searchableMediaIds,
            searchIndexerState = searchIndexerState,
            topCategories = topCategories.map { categoryMedia ->
                CategoryCarouselItem(
                    id = categoryMedia.category.id,
                    name = categoryMedia.category.name,
                    mediaCount = categoryMedia.category.mediaCount,
                    thumbnailMedia = categoryMedia.thumbnailMedia,
                )
            },
            topLocations = topLocations,
            topMimeTypes = topMimeTypes,
            topLensModels = topLensModels,
            topMediaModes = topMediaModes,
            topGroupTypes = topGroupTypes,
            typedSuggestions = typedSuggestions,
        ),
        actions = SearchActions(
            onBack = { eventHandler.navigateUp() },
            onClearQuery = viewModel::clearQuery,
            onQueryChange = viewModel::setQuery,
            onSubmitSearch = { newQuery ->
                viewModel.setQuery(newQuery, apply = true)
                viewModel.addHistory(newQuery)
            },
            onRestoreImageSearch = viewModel::restoreImageSearch,
            onRemoveHistory = viewModel::removeHistory,
            onRemoveImageHistory = viewModel::removeImageHistory,
            onImageSelected = viewModel::setSelectedMedia,
            onClearSelectedMedia = viewModel::clearSelectedMedia,
            onMimeTypeFilter = viewModel::setMimeTypeQuery,
            onLensModelFilter = viewModel::setLensModelQuery,
            onMediaModeFilter = viewModel::setMediaModeQuery,
            onGroupTypeFilter = viewModel::setGroupTypeQuery,
            onRetrySearch = viewModel::retrySearch,
            onOpenCategory = { categoryId ->
                eventHandler.navigate(Screen.CategoryViewScreen.categoryId(categoryId))
            },
            onOpenLocation = { city, country, latitude, longitude ->
                eventHandler.navigate(
                    Screen.LocationTimelineScreen.location(
                        gpsLocationNameCity = city,
                        gpsLocationNameCountry = country,
                        latitude = latitude,
                        longitude = longitude,
                    )
                )
            },
            onOpenMedia = { mediaId ->
                eventHandler.navigate(Screen.MediaViewScreen.idAndAlbum(mediaId, -1L))
            },
            onOpenSearchResult = { mediaId ->
                eventHandler.navigate(Screen.MediaViewScreen.idAndQuery(mediaId))
            },
            onClearHistory = viewModel::clearHistory,
            onOpenAlbum = { albumId, label ->
                eventHandler.navigate(Screen.AlbumViewScreen.album(albumId, label))
            },
            onFavoritesFilter = viewModel::setFavoritesQuery,
            onSetUpSmartSearch = {
                eventHandler.navigate(Screen.IntelligenceFeatureScreen.feature("SMART_SEARCH"))
            },
            onSeeAllCategories = { eventHandler.navigate(Screen.CategoriesScreen()) },
            onSeeAllPlaces = { eventHandler.navigate(Screen.LocationsScreen()) },
        ),
        isScrolling = isScrolling,
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
        tipsContent = if (tipResults.isEmpty()) null else {
            {
                HelpTipsSearchSection(
                    tips = tipResults,
                    onTipClick = { tip -> tip.route?.let { eventHandler.navigate(it.toScreenRoute()) } },
                    onSeeAll = { eventHandler.navigate(Screen.HelpScreen()) }
                )
            }
        },
        selectionSheetContent = { allMedia, selectedMediaList ->
            SelectionSheet(
                modifier = Modifier.align(Alignment.BottomEnd),
                allMedia = allMedia,
                selectedMedia = selectedMediaList
            )
        },
        lastCellIndex = rememberGridSize(),
        lastMosaicCellIndex = rememberMosaicGridSize(),
    )
}

/**
 * "Help & Tips" for the current query, shown above the media results. It starts as a single
 * compact row with the match count, since tips are rarely what a search is for; tapping it
 * expands the top matching tips and a "See all" row that opens the full Help screen. Tapping
 * a tip navigates to its detail (never mutates search state).
 */
@Composable
private fun HelpTipsSearchSection(
    tips: List<HelpSearchItem>,
    onTipClick: (HelpSearchItem) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "helpTipsChevron")
    val toggleLabel = stringResource(if (expanded) Res.string.help_tips_hide else Res.string.help_tips_show)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.Small)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(onClickLabel = toggleLabel, role = Role.Button) { expanded = !expanded }
                .padding(start = 12.dp, end = Spacing.Medium, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.help_tips_section_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = pluralStringResource(Res.plurals.help_tips_matches, tips.size, tips.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                modifier = Modifier.rotate(chevronRotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            HelpTipsList(tips = tips, onTipClick = onTipClick, onSeeAll = onSeeAll)
        }
    }
}

@Composable
private fun HelpTipsList(
    tips: List<HelpSearchItem>,
    onTipClick: (HelpSearchItem) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Slim Preference rows rendered through SettingsItem so the grouping, corners and
    // typography match the rest of the app's grouped lists.
    val entities = buildList {
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
                    title = stringResource(Res.string.help_search_see_all, tips.size),
                    onClick = onSeeAll
                )
            )
        }
    }
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        entities.forEachIndexed { index, item ->
            val position = when {
                entities.size == 1 -> Position.Alone
                index == 0 -> Position.Top
                index == entities.lastIndex -> Position.Bottom
                else -> Position.Middle
            }
            val newItem = item.copy(screenPosition = position)
            SettingsItem(
                item = newItem,
                slimLayout = true,
                tintIcon = newItem.iconUri == null
            )
        }
    }
}
