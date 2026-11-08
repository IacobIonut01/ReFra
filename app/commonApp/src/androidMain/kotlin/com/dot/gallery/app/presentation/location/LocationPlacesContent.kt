/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.location

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.logging.printError
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.components.ExpressiveEmptyState
import com.dot.gallery.core.presentation.components.GalleryFilterChip
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.GalleryPageTopBarAction
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.core.work.workScheduler
import com.dot.gallery.core.workers.enqueueRedactedLocationRepair
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.date.getDate
import com.dot.gallery.domain.model.GeoMedia
import com.dot.gallery.domain.model.LocationMedia
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.access_media_location_summary
import com.dot.gallery.generated.resources.grant_access
import com.dot.gallery.generated.resources.locations
import com.dot.gallery.generated.resources.place_detail_map
import com.dot.gallery.generated.resources.places_all
import com.dot.gallery.generated.resources.places_count
import com.dot.gallery.generated.resources.places_empty_body
import com.dot.gallery.generated.resources.places_empty_title
import com.dot.gallery.generated.resources.places_filter_all
import com.dot.gallery.generated.resources.places_filter_cities
import com.dot.gallery.generated.resources.places_filter_countries
import com.dot.gallery.generated.resources.places_in_countries
import com.dot.gallery.generated.resources.places_item_count
import com.dot.gallery.generated.resources.places_located_photos
import com.dot.gallery.generated.resources.places_open_map
import com.dot.gallery.generated.resources.places_permission_allow
import com.dot.gallery.generated.resources.places_permission_body
import com.dot.gallery.generated.resources.places_permission_title
import com.dot.gallery.generated.resources.places_recent_trips
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.text.NumberFormat

internal enum class PlacesFilter { ALL, CITIES, COUNTRIES, TRIPS }

private val TileShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 12.dp, bottomEnd = 12.dp)

/**
 * The Locations places page: how many photos carry a location (opening the map when there is
 * one), filters, recent trips and every place as a two-column grid of covers. Without the media
 * location permission and with nothing to show, it asks for the permission instead.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun LocationPlacesContent(
    metadataState: State<MediaMetadataState>,
    locations: List<LocationMedia>,
    geoMedia: List<GeoMedia>,
    /** Returns to the map this page was opened from; null on builds without a map. */
    onShowMap: (() -> Unit)?,
) {
    val eventHandler = LocalEventHandler.current
    val hazeState = LocalHazeState.current
    val gridState = rememberLazyGridState()
    val overview by produceState<PlacesOverview?>(null, locations, geoMedia) {
        value = withContext(Dispatchers.Default) {
            buildPlacesOverview(locations, geoMedia.map { it.toLocatedPhoto() })
        }
    }
    var filter by rememberSaveable { mutableStateOf(PlacesFilter.ALL) }

    val permissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val scope = rememberCoroutineScope()
        val permission = rememberPermissionState(Manifest.permission.ACCESS_MEDIA_LOCATION)
        // Rows parsed under redaction carry a 0,0 signature; re-parse them once access lands,
        // including a grant made from the app's system settings page.
        var repairEnqueued by remember { mutableStateOf(permission.status.isGranted) }
        LaunchedEffect(permission.status) {
            if (!repairEnqueued && permission.status.isGranted) {
                repairEnqueued = true
                scope.launch {
                    runCatching { workScheduler().enqueueRedactedLocationRepair() }
                        .onFailure { printError("ui.locations", "redacted location repair enqueue failed", it) }
                }
            }
        }
        PermissionRequest(permission.status.isGranted, permission::launchPermissionRequest)
    } else PermissionRequest(true) {}

    val openPlace: (PlaceSummary) -> Unit = { place ->
        if (place.city != null || place.country != null || place.latitude != null && place.longitude != null) {
            eventHandler.navigate(
                Screen.LocationTimelineScreen.location(
                    gpsLocationNameCity = place.city.orEmpty(),
                    gpsLocationNameCountry = place.country.orEmpty(),
                    latitude = place.latitude.takeIf { place.city != null || place.country == null },
                    longitude = place.longitude.takeIf { place.city != null || place.country == null },
                )
            )
        } else {
            eventHandler.navigate(Screen.MediaViewScreen.idAndAlbum(place.cover.id, -1L))
        }
    }

    Scaffold(
        topBar = {
            GalleryPageTopBar(
                title = stringResource(Res.string.locations),
                count = overview?.places?.size?.takeIf { it > 0 }?.toString(),
                onBack = onShowMap,
                hazeState = hazeState,
                scrolled = rememberScrolledUnderTopBar(gridState),
                actions = onShowMap?.let { showMap ->
                    {
                        GalleryPageTopBarAction(
                            icon = Icons.Outlined.Map,
                            contentDescription = stringResource(Res.string.place_detail_map),
                            onClick = showMap,
                        )
                    }
                },
            )
        }
    ) { paddingValues ->
        val current = overview
        val isLoading = metadataState.value.isLoading
        when {
            current == null -> Unit
            current.isEmpty && !permissionGranted.granted -> Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                ExpressiveEmptyState(
                    icon = Icons.Outlined.LocationOn,
                    title = stringResource(Res.string.places_permission_title),
                    body = stringResource(Res.string.places_permission_body),
                    action = {
                        Button(onClick = permissionGranted.request) {
                            Text(stringResource(Res.string.places_permission_allow))
                        }
                    },
                )
            }
            current.isEmpty && !isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                ExpressiveEmptyState(
                    icon = Icons.Outlined.LocationOff,
                    title = stringResource(Res.string.places_empty_title),
                    body = stringResource(Res.string.places_empty_body),
                )
            }
            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding() + 128.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                fullWidth("banner") {
                    PlacesBanner(overview = current, onShowMap = onShowMap)
                }
                if (!permissionGranted.granted) {
                    fullWidth("permission") {
                        PermissionBanner(onGrant = permissionGranted.request)
                    }
                }
                fullWidth("filters") {
                    PlacesFilters(
                        selected = filter,
                        hasTrips = current.trips.isNotEmpty(),
                        onSelect = { filter = it },
                    )
                }
                placesSections(
                    overview = current,
                    filter = if (filter == PlacesFilter.TRIPS && current.trips.isEmpty()) PlacesFilter.ALL else filter,
                    onOpenPlace = openPlace,
                )
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.BottomEnd,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(16.dp).size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private class PermissionRequest(val granted: Boolean, val request: () -> Unit)

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

private fun LazyGridScope.placesSections(
    overview: PlacesOverview,
    filter: PlacesFilter,
    onOpenPlace: (PlaceSummary) -> Unit,
) {
    when (filter) {
        PlacesFilter.ALL -> {
            if (overview.trips.isNotEmpty()) {
                fullWidth("trips_header") {
                    PlacesSectionHeader(stringResource(Res.string.places_recent_trips), overview.trips.size)
                }
                fullWidth("trips_row") {
                    LazyRow(
                        // Bleed through the grid's side padding so the row scrolls edge to edge.
                        modifier = Modifier.layout { measurable, constraints ->
                            val bleed = 32.dp.roundToPx()
                            val placeable = measurable.measure(
                                constraints.copy(
                                    minWidth = constraints.minWidth + bleed,
                                    maxWidth = constraints.maxWidth + bleed,
                                )
                            )
                            layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed / 2, 0) }
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(overview.trips, key = { it.key }) { trip ->
                            TripCard(
                                trip = trip,
                                onClick = { onOpenPlace(trip.place) },
                                modifier = Modifier.width(164.dp).height(206.dp),
                            )
                        }
                    }
                }
            }
            placeGrid("places", Res.string.places_all, overview.places, onOpenPlace)
        }
        PlacesFilter.CITIES -> placeGrid("cities", Res.string.places_filter_cities, overview.cities, onOpenPlace)
        PlacesFilter.COUNTRIES -> placeGrid("countries", Res.string.places_filter_countries, overview.countries, onOpenPlace)
        PlacesFilter.TRIPS -> {
            fullWidth("trips_header") {
                PlacesSectionHeader(stringResource(Res.string.places_recent_trips), overview.trips.size)
            }
            items(overview.trips, key = { it.key }) { trip ->
                TripCard(
                    trip = trip,
                    onClick = { onOpenPlace(trip.place) },
                    modifier = Modifier.fillMaxWidth().aspectRatio(164f / 206f),
                )
            }
        }
    }
}

private fun LazyGridScope.placeGrid(
    keyPrefix: String,
    title: StringResource,
    places: List<PlaceSummary>,
    onOpenPlace: (PlaceSummary) -> Unit,
) {
    fullWidth("${keyPrefix}_header") {
        PlacesSectionHeader(stringResource(title), places.size)
    }
    items(places, key = { "${keyPrefix}_${it.key}" }) { place ->
        PlaceTile(place = place, onClick = { onOpenPlace(place) })
    }
}

@Composable
private fun PlacesBanner(overview: PlacesOverview, onShowMap: (() -> Unit)?) {
    val countryCount = overview.countries.size
    val summary = buildList {
        val places = pluralStringResource(Res.plurals.places_count, overview.places.size, overview.places.size)
        add(
            if (countryCount > 0) {
                "$places ${pluralStringResource(Res.plurals.places_in_countries, countryCount, countryCount)}"
            } else places
        )
        if (onShowMap != null) add(stringResource(Res.string.places_open_map))
    }.joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .then(if (onShowMap != null) Modifier.clickable(role = Role.Button, onClick = onShowMap) else Modifier)
            .padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        Icon(imageVector = Icons.Outlined.LocationOn, contentDescription = null, tint = contentColor)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pluralStringResource(
                    Res.plurals.places_located_photos,
                    overview.locatedCount,
                    NumberFormat.getIntegerInstance().format(overview.locatedCount),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onShowMap != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = contentColor,
            )
        }
    }
}

@Composable
private fun PermissionBanner(onGrant: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.access_media_location_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onGrant) {
            Text(stringResource(Res.string.grant_access))
        }
    }
}

@Composable
private fun PlacesFilters(selected: PlacesFilter, hasTrips: Boolean, onSelect: (PlacesFilter) -> Unit) {
    LazyRow(
        modifier = Modifier.layout { measurable, constraints ->
            val bleed = 32.dp.roundToPx()
            val placeable = measurable.measure(
                constraints.copy(minWidth = constraints.minWidth + bleed, maxWidth = constraints.maxWidth + bleed)
            )
            layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed / 2, 0) }
        },
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val filters = PlacesFilter.entries.filter { it != PlacesFilter.TRIPS || hasTrips }
        items(filters, key = { it.name }) { filter ->
            GalleryFilterChip(
                label = stringResource(
                    when (filter) {
                        PlacesFilter.ALL -> Res.string.places_filter_all
                        PlacesFilter.CITIES -> Res.string.places_filter_cities
                        PlacesFilter.COUNTRIES -> Res.string.places_filter_countries
                        PlacesFilter.TRIPS -> Res.string.places_recent_trips
                    }
                ),
                selected = filter == selected,
                onClick = { onSelect(filter) },
            )
        }
    }
}

@Composable
private fun PlacesSectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp),
        )
    }
}

@Composable
private fun PlaceCover(media: Media, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        LocalMediaImageRenderer.current.RenderImage(
            modifier = Modifier.fillMaxSize(),
            model = media.getUri(),
            contentScale = ContentScale.Crop,
            contentDescription = null,
            signature = media,
        )
    }
}

@Composable
private fun PlaceTile(place: PlaceSummary, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            // Tighter bottom corners so the press ripple never cuts into the caption.
            .clip(TileShape)
            .clickable(onClick = onClick)
            .padding(bottom = 6.dp),
    ) {
        PlaceCover(media = place.cover, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(8.dp))
        Text(
            text = place.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            text = pluralStringResource(Res.plurals.places_item_count, place.itemCount, place.itemCount.toString()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun TripCard(trip: TripSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
    ) {
        PlaceCover(media = trip.cover, modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.65f)))
        )
        Box(
            modifier = Modifier
                .padding(12.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = trip.place.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(
                    pluralStringResource(Res.plurals.places_item_count, trip.itemCount, trip.itemCount.toString()),
                    trip.start.getDate("MMM yyyy"),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
