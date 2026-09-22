/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.location

import android.Manifest
import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.dot.gallery.R
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.feature_node.domain.model.GeoMedia
import com.dot.gallery.feature_node.domain.model.LocationMedia
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.locationCoordinateGroupKey
import com.dot.gallery.feature_node.domain.model.locationLabelKey
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isCloud
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import com.dot.gallery.core.presentation.components.util.advancedShadow
import com.dot.gallery.feature_node.presentation.library.components.LibrarySmallItem
import com.dot.gallery.feature_node.presentation.util.GlideInvalidation
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.Screen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import dev.chrisbanes.haze.LocalHazeStyle
import dev.chrisbanes.haze.hazeEffect

internal sealed interface MapGridItem {
    data class Header(val date: String) : MapGridItem
    data class MediaCell(val geoMedia: GeoMedia) : MapGridItem
    data class LocationCell(val locationMedia: LocationMedia) : MapGridItem
}

internal fun mapMediaViewerRoute(mediaId: Long): String =
    Screen.MediaViewScreen.idAndAlbum(mediaId, -1L)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LocationsScreen(
    metadataState: State<MediaMetadataState>,
    locations: List<LocationMedia> = emptyList(),
    geoMedia: List<GeoMedia> = emptyList(),
    initialMediaId: Long = -1L,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        MapLocationsContent(metadataState = metadataState, locations = locations, geoMedia = geoMedia, initialMediaId = initialMediaId)

        // Without ACCESS_MEDIA_LOCATION the metadata pipeline only sees redacted
        // EXIF, so nothing can resolve to a location — surface that instead of
        // silently showing an empty screen.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val locationPermission = rememberPermissionState(
                Manifest.permission.ACCESS_MEDIA_LOCATION
            )
            if (!locationPermission.status.isGranted) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.access_media_location_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    TextButton(
                        onClick = { locationPermission.launchPermissionRequest() },
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(end = 8.dp, bottom = 4.dp)
                    ) {
                        Text(text = stringResource(R.string.grant_access))
                    }
                }
            }
        }
    }
}

@Suppress("DerivedStateOfCandidate")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListLocationsContent(
    metadataState: State<MediaMetadataState>,
    locations: List<LocationMedia> = emptyList(),
) {
    val eventHandler = LocalEventHandler.current

    val grouped = remember(locations) {
        locations.groupBy {
            if (!it.city.isNullOrBlank() || !it.country.isNullOrBlank()) {
                "name:${locationLabelKey(it.location)}"
            } else {
                locationCoordinateGroupKey(it.latitude, it.longitude)
                    ?.let { key -> "coordinates:$key" }
                    ?: "label:${locationLabelKey(it.location)}"
            }
        }
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
                ) { (_, mediaList) ->
                    val representative = mediaList.maxByOrNull { it.media.definedTimestamp }
                    LibrarySmallItem(
                        modifier = Modifier.clickable(enabled = representative != null) {
                            val item = representative ?: return@clickable
                            val city = item.city
                            val country = item.country
                            val latitude = item.latitude
                            val longitude = item.longitude
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
                                eventHandler.navigate(Screen.MediaViewScreen.idAndAlbum(item.media.id, -1L))
                            }
                        },
                        title = representative?.location.orEmpty(),
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

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
internal fun MediaGridPanel(
    modifier: Modifier = Modifier,
    gridState: LazyGridState,
    gridItems: List<MapGridItem>,
    stringToday: String,
    stringYesterday: String,
    selectedMediaId: Long,
    onMediaClick: (GeoMedia) -> Unit,
    onLocationClick: (LocationMedia) -> Unit,
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
                    is MapGridItem.LocationCell -> "location_${item.locationMedia.media.id}"
                }
            },
            span = { item ->
                GridItemSpan(
                    when (item) {
                        is MapGridItem.Header -> maxLineSpan
                        is MapGridItem.MediaCell -> 1
                        is MapGridItem.LocationCell -> 1
                    }
                )
            },
            contentType = { item ->
                when (item) {
                    is MapGridItem.Header -> "header"
                    is MapGridItem.MediaCell -> "media"
                    is MapGridItem.LocationCell -> "location"
                }
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
                    MapMediaCell(
                        media = item.geoMedia.media,
                        selected = item.geoMedia.mediaId == selectedMediaId,
                        onClick = { onMediaClick(item.geoMedia) },
                    )
                }

                is MapGridItem.LocationCell -> {
                    MapMediaCell(
                        media = item.locationMedia.media,
                        selected = false,
                        onClick = { onLocationClick(item.locationMedia) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun MapMediaCell(
    media: Media,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick)
            .then(
                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary)
                else Modifier
            )
    ) {
        GlideImage(
            model = media.getUri(),
            contentDescription = media.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            requestBuilderTransform = {
                it.centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .signature(GlideInvalidation.signature(media))
            }
        )
        if (media.isCloud) {
            Icon(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .size(10.dp)
                    .advancedShadow(
                        cornersRadius = 5.dp,
                        shadowBlurRadius = 4.dp,
                        alpha = 0.3f
                    ),
                imageVector = Icons.Outlined.Cloud,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}
