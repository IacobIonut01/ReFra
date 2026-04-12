/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.location

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.dot.gallery.BuildConfig
import com.dot.gallery.R
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.feature_node.domain.model.GeoMedia
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.classifier.CategoriesViewModel
import com.dot.gallery.feature_node.presentation.library.components.LibrarySmallItem
import com.dot.gallery.feature_node.presentation.util.GlideInvalidation
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.Screen
import dev.chrisbanes.haze.LocalHazeStyle
import dev.chrisbanes.haze.hazeEffect

internal sealed interface MapGridItem {
    data class Header(val date: String) : MapGridItem
    data class MediaCell(val geoMedia: GeoMedia) : MapGridItem
}

@Composable
fun LocationsScreen(
    metadataState: State<MediaMetadataState>,
) {
    val hasMapToken = remember(BuildConfig.MAPS_TOKEN) { BuildConfig.MAPS_TOKEN != "DEBUG" }

    if (hasMapToken) {
        MapLocationsContent(metadataState = metadataState)
    } else {
        ListLocationsContent(metadataState = metadataState)
    }
}

@Suppress("DerivedStateOfCandidate")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListLocationsContent(
    metadataState: State<MediaMetadataState>,
) {
    val eventHandler = LocalEventHandler.current
    val viewModel = hiltViewModel<CategoriesViewModel>()
    val locations by viewModel.locations.collectAsStateWithLifecycle()

    val grouped = remember(locations) {
        locations.groupBy { it.location }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = rememberTopAppBarState(),
        flingAnimationSpec = null
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                modifier = Modifier.hazeEffect(
                    state = LocalHazeState.current,
                    style = LocalHazeStyle.current
                ),
                title = {
                    Text(text = stringResource(R.string.locations))
                },
                navigationIcon = {
                    NavigationBackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { paddingValues ->
        if (grouped.isEmpty() && !metadataState.value.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_locations_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding() + 128.dp,
                    start = 8.dp,
                    end = 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(
                    items = grouped.entries.toList(),
                    key = { it.key }
                ) { (location, mediaList) ->
                    val parts = location.split(",").map { it.trim() }
                    val city = parts.getOrElse(0) { "" }
                    val country = parts.getOrElse(1) { "" }
                    LibrarySmallItem(
                        modifier = Modifier.clickable {
                            if (city.isNotEmpty() && country.isNotEmpty()) {
                                eventHandler.navigate(
                                    Screen.LocationTimelineScreen.location(city, country)
                                )
                            }
                        },
                        title = location,
                        subtitle = "${mediaList.size} ${if (mediaList.size == 1) "item" else "items"}",
                        icon = Icons.Outlined.LocationOn,
                        useIndicator = true,
                        indicatorCounter = mediaList.size
                    )
                }
            }
        }

        if (metadataState.value.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.BottomEnd
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(16.dp)
                        .size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// MapLocationsContent is provided by src/maps/kotlin or src/nomaps/kotlin
// (see build.gradle.kts conditional sourceSets)

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
internal fun MediaGridPanel(
    modifier: Modifier = Modifier,
    gridState: LazyGridState,
    gridItems: List<MapGridItem>,
    stringToday: String,
    stringYesterday: String,
    onMediaClick: (GeoMedia) -> Unit,
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(4),
        modifier = modifier,
        contentPadding = PaddingValues(start = 2.dp, end = 2.dp, bottom = 80.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(
            items = gridItems,
            key = { item ->
                when (item) {
                    is MapGridItem.Header -> "header_${item.date}"
                    is MapGridItem.MediaCell -> "media_${item.geoMedia.mediaId}"
                }
            },
            span = { item ->
                GridItemSpan(
                    when (item) {
                        is MapGridItem.Header -> maxLineSpan
                        is MapGridItem.MediaCell -> 1
                    }
                )
            }
        ) { item ->
            when (item) {
                is MapGridItem.Header -> {
                    val displayDate = remember(item.date) {
                        item.date
                            .replace("Today", stringToday)
                            .replace("Yesterday", stringYesterday)
                    }
                    Text(
                        text = displayDate,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 16.dp,
                                vertical = 24.dp
                            )
                    )
                }

                is MapGridItem.MediaCell -> {
                    GlideImage(
                        model = item.geoMedia.media.getUri(),
                        contentDescription = item.geoMedia.media.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable { onMediaClick(item.geoMedia) },
                        requestBuilderTransform = {
                            it.centerCrop()
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .signature(GlideInvalidation.signature(item.geoMedia.media))
                        }
                    )
                }
            }
        }
    }
}
