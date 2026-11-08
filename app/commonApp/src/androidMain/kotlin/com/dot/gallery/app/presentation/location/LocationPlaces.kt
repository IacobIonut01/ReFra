/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.location

import androidx.compose.runtime.Immutable
import com.dot.gallery.domain.model.GeoMedia
import com.dot.gallery.domain.model.LocationMedia
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.definedTimestamp
import com.dot.gallery.domain.model.locationCoordinateGroupKey
import com.dot.gallery.domain.model.locationLabelKey
import com.dot.gallery.feature.library.location.locationLabel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One named place, or one unnamed coordinate cluster, on the places page. */
@Immutable
internal data class PlaceSummary(
    val key: String,
    val title: String,
    val city: String?,
    val country: String?,
    val latitude: Double?,
    val longitude: Double?,
    val cover: Media,
    val itemCount: Int,
)

/** A run of photos taken away from the most photographed place, close together in time. */
@Immutable
internal data class TripSummary(
    val key: String,
    val place: PlaceSummary,
    val cover: Media,
    val itemCount: Int,
    /** Epoch seconds of the trip's first photo. */
    val start: Long,
    /** Epoch seconds of the trip's last photo. */
    val end: Long,
)

@Immutable
internal data class PlacesOverview(
    val places: List<PlaceSummary>,
    val cities: List<PlaceSummary>,
    val countries: List<PlaceSummary>,
    val trips: List<TripSummary>,
    val locatedCount: Int,
) {
    val isEmpty: Boolean get() = places.isEmpty()
}

/** Photos more than this far apart start a new trip. */
internal const val TRIP_GAP_SECONDS = 3L * 24 * 60 * 60
internal const val MIN_TRIP_ITEMS = 3
internal const val MAX_RECENT_TRIPS = 10
/** Photos closer than this to home are not part of a trip. */
internal const val TRIP_MIN_DISTANCE_KM = 50.0

/** Great-circle distance in kilometres. */
internal fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 6371.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
}

/**
 * Same identity the Library uses to dedupe locations, so each [LocationMedia] row matches the
 * photos counted for it.
 */
internal fun placeKey(city: String?, country: String?, latitude: Double?, longitude: Double?): String? {
    val cleanCity = city?.trim()?.takeIf(String::isNotBlank)
    val cleanCountry = country?.trim()?.takeIf(String::isNotBlank)
    if (cleanCity != null || cleanCountry != null) {
        return "name:${locationLabelKey(locationLabel(cleanCity, cleanCountry, latitude, longitude))}"
    }
    return locationCoordinateGroupKey(latitude, longitude)?.let { "coordinates:$it" }
}

/** A photo with coordinates, as the places page needs it. */
@Immutable
internal data class LocatedPhoto(
    val media: Media,
    val city: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
)

internal fun GeoMedia.toLocatedPhoto() = LocatedPhoto(media, locationCity, locationCountry, latitude, longitude)

private fun LocationMedia.placeKey(): String =
    placeKey(city, country, latitude, longitude) ?: "label:${locationLabelKey(location)}"

private fun LocatedPhoto.placeKey(): String? = placeKey(city, country, latitude, longitude)

/**
 * Builds the places page from the Library's per-place [locations] (one row per place, holding
 * its newest photo) and every located photo in [photos], which supplies the counts and trips.
 */
internal fun buildPlacesOverview(
    locations: List<LocationMedia>,
    photos: List<LocatedPhoto>,
): PlacesOverview {
    val countsByKey = HashMap<String, Int>()
    photos.forEach { item -> item.placeKey()?.let { countsByKey.merge(it, 1, Int::plus) } }

    val places = locations
        .map { location ->
            val key = location.placeKey()
            val city = location.city?.trim()?.takeIf(String::isNotBlank)
            val country = location.country?.trim()?.takeIf(String::isNotBlank)
            PlaceSummary(
                key = key,
                title = city ?: country ?: location.location,
                city = city,
                country = country,
                latitude = location.latitude,
                longitude = location.longitude,
                cover = location.media,
                itemCount = (countsByKey[key] ?: 0).coerceAtLeast(1),
            )
        }
        .distinctBy { it.key }
        .sortedWith(compareByDescending<PlaceSummary> { it.itemCount }.thenBy { it.title.lowercase() })

    val countries = places
        .filter { it.country != null }
        .groupBy { it.country!!.lowercase() }
        .map { (_, members) ->
            val newest = members.maxBy { it.cover.definedTimestamp }
            PlaceSummary(
                key = "country:${newest.country!!.lowercase()}",
                title = newest.country,
                city = null,
                country = newest.country,
                latitude = newest.latitude,
                longitude = newest.longitude,
                cover = newest.cover,
                itemCount = members.sumOf { it.itemCount },
            )
        }
        .sortedWith(compareByDescending<PlaceSummary> { it.itemCount }.thenBy { it.title.lowercase() })

    return PlacesOverview(
        places = places,
        cities = places.filter { it.city != null },
        countries = countries,
        trips = buildTrips(places, photos),
        locatedCount = photos.size,
    )
}

/**
 * Groups photos taken away from home into trips. Home is the place with the most photos, and a
 * photo is away when it was taken more than [TRIP_MIN_DISTANCE_KM] from it, so the next town
 * over is not a trip. A trip is a time-ordered run of away photos that ends at a photo taken near
 * home or at a gap longer than [TRIP_GAP_SECONDS]. Runs shorter than [MIN_TRIP_ITEMS] are
 * dropped, and each trip is named after the place it holds the most photos of. Newest first.
 */
internal fun buildTrips(places: List<PlaceSummary>, photos: List<LocatedPhoto>): List<TripSummary> {
    val placeByKey = places.associateBy { it.key }
    val homePlace = places.maxByOrNull { it.itemCount } ?: return emptyList()
    val home = homePlace.key
    val homeLatitude = homePlace.latitude
    val homeLongitude = homePlace.longitude
    fun isAway(photo: LocatedPhoto, key: String): Boolean =
        if (homeLatitude != null && homeLongitude != null) {
            distanceKm(homeLatitude, homeLongitude, photo.latitude, photo.longitude) > TRIP_MIN_DISTANCE_KM
        } else key != home
    val trips = ArrayList<TripSummary>()
    var run = ArrayList<Pair<LocatedPhoto, String>>()

    fun closeRun() {
        if (run.size >= MIN_TRIP_ITEMS) {
            val mainKey = run.groupingBy { it.second }.eachCount().maxBy { it.value }.key
            placeByKey[mainKey]?.let { place ->
                val first = run.first().first
                val last = run.last().first
                trips += TripSummary(
                    key = "trip:$mainKey:${first.media.definedTimestamp}",
                    place = place,
                    cover = last.media,
                    itemCount = run.size,
                    start = first.media.definedTimestamp,
                    end = last.media.definedTimestamp,
                )
            }
        }
        run = ArrayList()
    }

    photos
        .mapNotNull { item -> item.placeKey()?.takeIf(placeByKey::containsKey)?.let { item to it } }
        .sortedBy { it.first.media.definedTimestamp }
        .forEach { entry ->
            val (item, key) = entry
            if (!isAway(item, key)) {
                closeRun()
                return@forEach
            }
            val previous = run.lastOrNull()?.first
            if (previous != null && item.media.definedTimestamp - previous.media.definedTimestamp > TRIP_GAP_SECONDS) {
                closeRun()
            }
            run += entry
        }
    closeRun()
    return trips.sortedByDescending { it.end }.take(MAX_RECENT_TRIPS)
}
