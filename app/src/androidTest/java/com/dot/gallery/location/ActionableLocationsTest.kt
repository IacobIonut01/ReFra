/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.location

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dot.gallery.feature_node.domain.model.GeoMedia
import com.dot.gallery.feature_node.domain.model.LocationMedia
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.presentation.location.buildActionableLocations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for #1235: media without a usable location must not form
 * an "Unknown Location" group (e.g. redacted EXIF without ACCESS_MEDIA_LOCATION
 * yielding 0,0 coordinates).
 */
@RunWith(AndroidJUnit4::class)
class ActionableLocationsTest {

    private fun media(id: Long) = Media.UriMedia(
        id = id,
        label = "photo-$id.jpg",
        uri = Uri.parse("content://media/external/images/media/$id"),
        path = "/storage/emulated/0/DCIM/photo-$id.jpg",
        relativePath = "DCIM/",
        albumID = 10L,
        albumLabel = "Camera",
        timestamp = 1_700_000_000L + id,
        fullDate = "2023-11-14",
        mimeType = "image/jpeg",
        favorite = 0,
        trashed = 0,
        size = 1_024L
    )

    private fun local(id: Long, lat: Double?, lon: Double?, city: String? = null, country: String? = null) =
        LocationMedia(
            media = media(id),
            location = "",
            city = city,
            country = country,
            latitude = lat,
            longitude = lon,
        )

    @Test
    fun noLocationItemsAreDropped() {
        val result = buildActionableLocations(
            localLocations = listOf(
                local(1, null, null),
                local(2, null, null),
                local(3, 44.4397, 26.0963),
            ),
            geoMedia = emptyList(),
            cachedCloudMedia = emptyList(),
        )
        assertEquals(1, result.size)
        assertEquals(3L, result.first().media.id)
        assertTrue(result.first().location.contains("44."))
    }

    @Test
    fun zeroZeroCoordinatesAreDropped() {
        // Redacted/0,0 EXIF produced geo entries that bucketed the whole
        // library under "Unknown Location".
        val result = buildActionableLocations(
            localLocations = emptyList(),
            geoMedia = listOf(
                GeoMedia(
                    mediaId = 5L,
                    latitude = 0.0,
                    longitude = 0.0,
                    locationCity = null,
                    locationCountry = null,
                    media = media(5L),
                ),
            ),
            cachedCloudMedia = emptyList(),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun coordinateOnlyAndNamedItemsAreKept() {
        val result = buildActionableLocations(
            localLocations = listOf(local(7, 44.4, 26.1)),
            geoMedia = listOf(
                GeoMedia(
                    mediaId = 8L,
                    latitude = 48.8566,
                    longitude = 2.3522,
                    locationCity = "Paris",
                    locationCountry = "France",
                    media = media(8L),
                ),
            ),
            cachedCloudMedia = emptyList(),
        )
        assertEquals(2, result.size)
        assertTrue(result.any { it.location.contains("44.") })
        assertTrue(result.any { it.location == "Paris, France" })
    }
}
