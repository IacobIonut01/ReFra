/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.location

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.foundation.shape.RoundedCornerShape
import com.dot.gallery.core.presentation.components.ExpressiveEmptyState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import com.dot.gallery.core.presentation.components.SheetButton
import com.dot.gallery.core.presentation.components.SheetButtonStyle
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.work.workScheduler
import com.dot.gallery.core.logging.printError
import kotlinx.coroutines.launch
import com.github.panpf.sketch.AsyncImage
import com.github.panpf.sketch.request.ComposableImageRequest
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.navigate
import com.dot.gallery.domain.model.GeoMedia
import com.dot.gallery.domain.model.LocationMedia
import com.dot.gallery.domain.model.Media
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.util.getUri
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import com.dot.gallery.core.workers.enqueueRedactedLocationRepair
import com.dot.gallery.feature.library.ui.components.LibrarySmallItem
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.presentation.util.Screen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.dot.gallery.feature.editor.adjustments.Crop
import com.dot.gallery.core.presentation.components.util.advancedShadow
import com.dot.gallery.domain.util.isCloud
import com.dot.gallery.domain.model.locationLabelKey
import com.dot.gallery.domain.model.locationCoordinateGroupKey
import com.dot.gallery.domain.model.definedTimestamp
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.access_media_location_summary
import com.dot.gallery.generated.resources.grant_access
import com.dot.gallery.generated.resources.locations
import com.dot.gallery.generated.resources.map_panel_open
import com.dot.gallery.generated.resources.no_locations_found
import com.dot.gallery.generated.resources.s_items
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.outlined.Map
import androidx.compose.runtime.saveable.rememberSaveable
import com.dot.gallery.core.presentation.components.GalleryPageTopBarAction
import com.dot.gallery.generated.resources.place_detail_map


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
    // The map's places action swaps in the list page; its back (bar or system) returns to the map.
    var showPlaces by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showPlaces) { showPlaces = false }
    Box(modifier = Modifier.fillMaxSize()) {
        if (showPlaces) {
            ListLocationsContent(
                metadataState = metadataState,
                locations = locations,
                geoMedia = geoMedia,
                onShowMap = { showPlaces = false },
            )
        } else {
            MapLocationsContent(
                metadataState = metadataState,
                locations = locations,
                geoMedia = geoMedia,
                initialMediaId = initialMediaId,
                onShowPlaces = { showPlaces = true },
            )
        }

        // Without ACCESS_MEDIA_LOCATION the metadata pipeline only sees redacted
        // EXIF, so nothing can resolve to a location — surface that instead of
        // silently showing an empty screen. The places page asks for it itself.
        if (HasInteractiveMap && !showPlaces && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val scope = rememberCoroutineScope()
            val locationPermission = rememberPermissionState(
                Manifest.permission.ACCESS_MEDIA_LOCATION
            )
            // Rows parsed under redaction carry a 0,0 signature — re-parse them once the
            // permission lands. Fires on the status transition rather than the launcher
            // result so a grant made via app-details settings repairs the rows too.
            var repairEnqueued by remember { mutableStateOf(locationPermission.status.isGranted) }
            LaunchedEffect(locationPermission.status) {
                if (!repairEnqueued && locationPermission.status.isGranted) {
                    repairEnqueued = true
                    scope.launch {
                        runCatching { workScheduler().enqueueRedactedLocationRepair() }
                            .onFailure {
                                printError("ui.locations", "redacted location repair enqueue failed", it)
                            }
                    }
                }
            }
            if (!locationPermission.status.isGranted) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
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
                            text = stringResource(Res.string.access_media_location_summary),
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
                        Text(text = stringResource(Res.string.grant_access))
                    }
                }
            }
        }
    }
}

/** The places page; the offline build shows it in place of the map. */
@Composable
internal fun ListLocationsContent(
    metadataState: State<MediaMetadataState>,
    locations: List<LocationMedia> = emptyList(),
    geoMedia: List<GeoMedia> = emptyList(),
    /** Returns to the map this list was opened from; null on builds without a map. */
    onShowMap: (() -> Unit)? = null,
) {
    LocationPlacesContent(
        metadataState = metadataState,
        locations = locations,
        geoMedia = geoMedia,
        onShowMap = onShowMap,
    )
}

/** The named place the map's selected photo belongs to, shown above the map's media panel. */
internal data class MapPanelPlace(
    val title: String,
    val city: String?,
    val country: String?,
    val itemCount: Int,
    val latitude: Double,
    val longitude: Double,
)

/** The place of [selected], counting every photo in [all] that shares its city and country. */
internal fun mapPanelPlace(selected: GeoMedia, all: List<GeoMedia>): MapPanelPlace? {
    val city = selected.locationCity?.takeIf { it.isNotBlank() }
    val country = selected.locationCountry?.takeIf { it.isNotBlank() }
    val title = city ?: country ?: return null
    return MapPanelPlace(
        title = title,
        city = city,
        country = country,
        itemCount = all.count {
            it.locationCity?.takeIf(String::isNotBlank) == city &&
                it.locationCountry?.takeIf(String::isNotBlank) == country
        },
        latitude = selected.latitude,
        longitude = selected.longitude,
    )
}

/**
 * The map's media panel: the selected photo's place with an Open action, then every located
 * photo by month in a three-column grid of rounded tiles.
 */
@Composable
internal fun MediaGridPanel(
    modifier: Modifier = Modifier,
    gridState: LazyGridState,
    gridItems: List<MapGridItem>,
    place: MapPanelPlace?,
    onOpenPlace: (MapPanelPlace) -> Unit,
    selectedMediaId: Long,
    onMediaClick: (GeoMedia) -> Unit,
    onLocationClick: (LocationMedia) -> Unit,
) {
    Column(modifier = modifier) {
        if (place != null) {
            MapPanelPlaceHeader(place = place, onOpen = { onOpenPlace(place) })
        }
        MapMediaGrid(
            gridState = gridState,
            gridItems = gridItems,
            selectedMediaId = selectedMediaId,
            onMediaClick = onMediaClick,
            onLocationClick = onLocationClick,
        )
    }
}

@Composable
private fun MapPanelPlaceHeader(place: MapPanelPlace, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = place.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    stringResource(Res.string.s_items, place.itemCount.toString()),
                    place.country.takeIf { place.city != null },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SheetButton(
            text = stringResource(Res.string.map_panel_open),
            onClick = onOpen,
            style = SheetButtonStyle.Tonal,
        )
    }
}

@Composable
private fun MapMediaGrid(
    gridState: LazyGridState,
    gridItems: List<MapGridItem>,
    selectedMediaId: Long,
    onMediaClick: (GeoMedia) -> Unit,
    onLocationClick: (LocationMedia) -> Unit,
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
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
                    Text(
                        text = item.date,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 3.dp)
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

@Composable
private fun MapMediaCell(
    media: Media,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .then(
                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier
            )
    ) {
        AsyncImage(
            request = ComposableImageRequest(media.getUri().toString()) {
                setExtra(key = "mediaSignature", value = media.toString())
            },
            contentDescription = media.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
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
