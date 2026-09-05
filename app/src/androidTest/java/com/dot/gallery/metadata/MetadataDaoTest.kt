package com.dot.gallery.metadata

import android.location.Address
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
import com.dot.gallery.feature_node.data.data_source.MetadataDao
import com.dot.gallery.feature_node.domain.model.MediaMetadataCore
import com.dot.gallery.feature_node.presentation.util.locationGroupName
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MetadataDaoTest {
    private lateinit var database: InternalDatabase
    private lateinit var dao: MetadataDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.getMetadataDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun pendingLocationsAreRepairedWithoutOverwritingResolvedRows() = runBlocking {
        dao.upsertCore(metadata(1L, locationName = null, country = null, city = null))
        dao.upsertCore(metadata(2L, locationName = "Cardiff, United Kingdom", country = "United Kingdom", city = "Cardiff"))
        dao.upsertCore(metadata(3L, locationName = " ", country = "", city = null))
        dao.upsertCore(metadata(4L, locationName = "Existing formatted address", country = null, city = null))

        assertEquals(listOf(1L, 3L, 4L), dao.getPendingMetadataLocations(10).map { it.mediaId }.sorted())
        assertEquals(
            0,
            dao.updateGeocodedLocation(
                mediaId = 2L,
                latitude = 51.5,
                longitude = -0.1,
                locationName = "Replacement",
                country = "Replacement",
                city = "Replacement",
            )
        )
        assertEquals(
            1,
            dao.updateGeocodedLocation(
                mediaId = 1L,
                latitude = 51.5,
                longitude = -0.1,
                locationName = "London, United Kingdom",
                country = "United Kingdom",
                city = "London",
            )
        )

        assertEquals(
            1,
            dao.updateGeocodedLocation(
                mediaId = 4L,
                latitude = 51.5,
                longitude = -0.1,
                locationName = "Replacement formatted address",
                country = "France",
                city = "Paris",
            )
        )

        val repaired = dao.getCoreMetadata(1L)
        assertEquals("London, United Kingdom", repaired?.gpsLocationName)
        assertEquals("United Kingdom", repaired?.gpsLocationNameCountry)
        assertEquals("London", repaired?.gpsLocationNameCity)
        assertEquals("Cardiff", dao.getCoreMetadata(2L)?.gpsLocationNameCity)
        assertEquals("Existing formatted address", dao.getCoreMetadata(4L)?.gpsLocationName)
        assertEquals("Paris", dao.getCoreMetadata(4L)?.gpsLocationNameCity)

        dao.upsertCore(metadata(3L, locationName = null, country = null, city = null).copy(gpsLatitude = 40.0))
        assertEquals(
            0,
            dao.updateGeocodedLocation(
                mediaId = 3L,
                latitude = 51.5,
                longitude = -0.1,
                locationName = "Stale",
                country = "Stale",
                city = "Stale",
            )
        )
        assertEquals(listOf(3L), dao.getPendingMetadataLocations(10).map { it.mediaId })
    }

    @Test
    fun locationGroupNameFallsBackToAdministrativeArea() {
        val address = Address(Locale.US).apply {
            locality = null
            subAdminArea = "Constanța"
            adminArea = "Dobrogea"
        }

        assertEquals("Constanța", address.locationGroupName)
    }

    private fun metadata(
        mediaId: Long,
        locationName: String?,
        country: String?,
        city: String?,
    ) = MediaMetadataCore(
        mediaId = mediaId,
        imageDescription = null,
        dateTimeOriginal = null,
        manufacturerName = null,
        modelName = null,
        aperture = null,
        exposureTime = null,
        iso = null,
        gpsLatitude = 51.5,
        gpsLongitude = -0.1,
        gpsLocationName = locationName,
        gpsLocationNameCountry = country,
        gpsLocationNameCity = city,
        imageWidth = 1,
        imageHeight = 1,
        imageResolutionX = null,
        imageResolutionY = null,
        resolutionUnit = null,
    )
}
