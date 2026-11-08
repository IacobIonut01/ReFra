package com.dot.gallery.location

import com.dot.gallery.app.presentation.location.LocatedPhoto
import com.dot.gallery.app.presentation.location.TRIP_GAP_SECONDS
import com.dot.gallery.app.presentation.location.buildPlacesOverview
import com.dot.gallery.domain.model.EncryptedMedia
import com.dot.gallery.domain.model.LocationMedia
import com.dot.gallery.domain.model.Media
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationPlacesTest {

    private val day = 24L * 60 * 60

    @Test
    fun placesCountEveryLocatedPhotoAndSortByCount() {
        val overview = buildPlacesOverview(
            locations = listOf(
                place(1, "Sintra", "Portugal"),
                place(2, "Bucharest", "Romania"),
            ),
            photos = photos("Bucharest", "Romania", count = 5, start = 0) +
                photos("Sintra", "Portugal", count = 2, start = 100 * day),
        )

        assertEquals(listOf("Bucharest", "Sintra"), overview.places.map { it.title })
        assertEquals(listOf(5, 2), overview.places.map { it.itemCount })
        assertEquals(7, overview.locatedCount)
    }

    @Test
    fun countriesSumTheirPlaces() {
        val overview = buildPlacesOverview(
            locations = listOf(
                place(1, "Lisbon", "Portugal"),
                place(2, "Sintra", "Portugal"),
                place(3, "Bucharest", "Romania"),
            ),
            photos = photos("Lisbon", "Portugal", 3, 0) +
                photos("Sintra", "Portugal", 2, 0) +
                photos("Bucharest", "Romania", 4, 0),
        )

        assertEquals(listOf("Portugal", "Romania"), overview.countries.map { it.title })
        assertEquals(listOf(5, 4), overview.countries.map { it.itemCount })
    }

    @Test
    fun countryOnlyPlacesAreNotCities() {
        val overview = buildPlacesOverview(
            locations = listOf(place(1, null, "Iceland"), place(2, "Reykjavik", "Iceland")),
            photos = photos(null, "Iceland", 2, 0) + photos("Reykjavik", "Iceland", 1, 0),
        )

        assertEquals(listOf("Reykjavik"), overview.cities.map { it.title })
        assertEquals(2, overview.places.size)
    }

    @Test
    fun tripsAreRunsAwayFromHomeSplitByHomePhotosAndGaps() {
        val home = photos("Bucharest", "Romania", 10, 0)
        val madeira = photos("Funchal", "Portugal", 4, 10 * day, step = 3600)
        val backHome = photos("Bucharest", "Romania", 1, 12 * day)
        val banff = photos("Banff", "Canada", 3, 40 * day, step = 3600)
        val lateBanff = photos("Banff", "Canada", 3, 40 * day + TRIP_GAP_SECONDS + day, step = 3600)

        val overview = buildPlacesOverview(
            locations = listOf(
                place(1, "Bucharest", "Romania"),
                place(2, "Funchal", "Portugal"),
                place(3, "Banff", "Canada"),
            ),
            photos = home + madeira + backHome + banff + lateBanff,
        )

        assertEquals(listOf("Banff", "Banff", "Funchal"), overview.trips.map { it.place.title })
        assertEquals(listOf(3, 3, 4), overview.trips.map { it.itemCount })
    }

    @Test
    fun shortRunsAndSinglePlaceLibrariesHaveNoTrips() {
        val onlyHome = buildPlacesOverview(
            locations = listOf(place(1, "Bucharest", "Romania")),
            photos = photos("Bucharest", "Romania", 20, 0),
        )
        assertTrue(onlyHome.trips.isEmpty())

        val shortHop = buildPlacesOverview(
            locations = listOf(place(1, "Bucharest", "Romania"), place(2, "Ploiesti", "Romania")),
            photos = photos("Bucharest", "Romania", 5, 0) + photos("Ploiesti", "Romania", 2, 5 * day),
        )
        assertTrue(shortHop.trips.isEmpty())
    }

    @Test
    fun theNextTownOverIsNotATrip() {
        val overview = buildPlacesOverview(
            locations = listOf(place(1, "Floresti", "Romania"), place(2, "Cluj-Napoca", "Romania")),
            photos = photos("Floresti", "Romania", 10, 0) + photos("Cluj-Napoca", "Romania", 5, 10 * day),
        )

        assertTrue(overview.trips.isEmpty())
    }

    private val coordinates = mapOf(
        "Bucharest" to (44.43 to 26.10),
        "Ploiesti" to (44.94 to 26.03),
        "Funchal" to (32.65 to -16.91),
        "Banff" to (51.18 to -115.57),
        "Floresti" to (46.75 to 23.49),
        "Cluj-Napoca" to (46.77 to 23.59),
    )

    private fun coordinatesOf(city: String?) = coordinates[city] ?: (10.0 to 20.0)

    private var nextId = 1000L

    private fun photos(city: String?, country: String, count: Int, start: Long, step: Long = 60): List<LocatedPhoto> =
        (0 until count).map { index ->
            LocatedPhoto(
                media = media(nextId++, start + index * step),
                city = city,
                country = country,
                latitude = coordinatesOf(city).first,
                longitude = coordinatesOf(city).second,
            )
        }

    private fun place(id: Long, city: String?, country: String) = LocationMedia(
        media = media(id, 0),
        location = listOfNotNull(city, country).joinToString(", "),
        city = city,
        country = country,
        latitude = coordinatesOf(city).first,
        longitude = coordinatesOf(city).second,
    )

    private fun media(id: Long, timestamp: Long): Media = EncryptedMedia(
        id = id,
        label = "media$id",
        bytes = byteArrayOf(),
        path = "",
        relativePath = "",
        albumID = 0L,
        albumLabel = "",
        timestamp = timestamp,
        fullDate = "",
        mimeType = "image/jpeg",
        favorite = 0,
        trashed = 0,
        size = 0L,
    )
}
