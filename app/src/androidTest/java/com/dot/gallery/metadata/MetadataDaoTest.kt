package com.dot.gallery.metadata

import android.location.Address
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.BackoffPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dot.gallery.cloud.core.CloudMapMarker
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.data.entity.CloudMediaEntity
import android.content.ContentUris
import android.provider.MediaStore
import com.dot.gallery.core.workers.METADATA_LOCATION_REPAIR_WORK
import com.dot.gallery.core.workers.metadataLocationRepairRequest
import com.dot.gallery.core.workers.metadataLocationRepairResult
import com.dot.gallery.core.workers.repairPendingMetadataLocations
import com.dot.gallery.core.workers.repairRedactedMetadataLocations
import com.dot.gallery.feature_node.data.data_source.GeocodedMetadataLocation
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
import com.dot.gallery.feature_node.data.data_source.MediaFeature
import com.dot.gallery.feature_node.data.data_source.MediaFeatureStateEntity
import com.dot.gallery.feature_node.data.data_source.MediaFeatureStatus
import com.dot.gallery.feature_node.data.data_source.MetadataDao
import com.dot.gallery.feature_node.domain.model.GeoMedia
import com.dot.gallery.feature_node.domain.model.LocationMedia
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadata
import com.dot.gallery.feature_node.domain.model.MediaMetadataCore
import com.dot.gallery.feature_node.domain.model.MediaVersion
import com.dot.gallery.feature_node.presentation.location.AccountCloudMapMarker
import com.dot.gallery.feature_node.presentation.location.buildActionableLocations
import com.dot.gallery.feature_node.presentation.location.mergeAccountQualifiedGeoMedia
import com.dot.gallery.feature_node.presentation.util.locationGroupName
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
    fun startupEnqueuesLocationRepair() = runBlocking {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val infos = withTimeout(10_000) {
            WorkManager.getInstance(targetContext)
                .getWorkInfosForUniqueWorkFlow("MetadataLocationRepair")
                .first { it.isNotEmpty() }
        }
        assertTrue(infos.none { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun pendingLocationsAreRepairedWithoutOverwritingResolvedRows() = runBlocking {
        dao.upsertCore(metadata(1L, locationName = null, country = null, city = null))
        dao.upsertCore(metadata(2L, locationName = "Cardiff, United Kingdom", country = "United Kingdom", city = "Cardiff"))
        dao.upsertCore(metadata(3L, locationName = " ", country = "", city = null))
        dao.upsertCore(metadata(4L, locationName = "Existing formatted address", country = null, city = null))

        assertEquals(listOf(1L, 3L, 4L), dao.getPendingMetadataLocations(afterMediaId = Long.MIN_VALUE, limit = 10).map { it.mediaId })
        assertEquals(listOf(1L), dao.getPendingMetadataLocations(afterMediaId = Long.MIN_VALUE, limit = 1).map { it.mediaId })
        assertEquals(listOf(3L, 4L), dao.getPendingMetadataLocations(afterMediaId = 1L, limit = 10).map { it.mediaId })
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
        assertEquals(listOf(3L), dao.getPendingMetadataLocations(afterMediaId = Long.MIN_VALUE, limit = 10).map { it.mediaId })
    }

    @Test
    fun repairPendingMetadataLocationsResolvesAllPendingWithoutMetadataReset() = runBlocking {
        val unresolvedIds = listOf(-42L) + (1L..200L).toList()
        unresolvedIds.forEach { id ->
            dao.upsertCore(
                metadata(id, locationName = null, country = null, city = null)
                    .copy(imageDescription = "original-$id", imageWidth = 4000)
            )
        }
        dao.upsertCore(
            metadata(
                500L,
                locationName = "Cardiff, United Kingdom",
                country = "United Kingdom",
                city = "Cardiff",
            )
        )
        dao.setMediaVersion(MediaVersion("already-current"))
        val scanDao = database.getSmartScanDao()
        val featureState = MediaFeatureStateEntity(
            mediaId = -42L,
            feature = MediaFeature.METADATA,
            status = MediaFeatureStatus.SUCCEEDED,
            sourceRevision = "source",
            resultRevision = "metadata-v1",
            updatedAt = 1L,
        )
        scanDao.upsertFeatureState(featureState)

        var lookups = 0
        val needsRetry = repairPendingMetadataLocations(dao) { _, _ ->
            lookups++
            londonAddress()
        }

        assertFalse(needsRetry)
        assertEquals(1, lookups)
        unresolvedIds.forEach { id ->
            val row = dao.getCoreMetadata(id)
            assertEquals("London, United Kingdom", row?.gpsLocationName)
            assertEquals("United Kingdom", row?.gpsLocationNameCountry)
            assertEquals("London", row?.gpsLocationNameCity)
            assertEquals("original-$id", row?.imageDescription)
            assertEquals(4000, row?.imageWidth)
            assertEquals(51.5, row?.gpsLatitude ?: 0.0, 0.0)
            assertEquals(-0.1, row?.gpsLongitude ?: 0.0, 0.0)
        }
        val resolved = dao.getCoreMetadata(500L)
        assertEquals("Cardiff, United Kingdom", resolved?.gpsLocationName)
        assertEquals("Cardiff", resolved?.gpsLocationNameCity)
        assertTrue(dao.isMediaVersionUpToDate("already-current"))
        assertEquals(featureState, scanDao.getFeatureState(-42L, MediaFeature.METADATA))

        var secondLookups = 0
        assertFalse(repairPendingMetadataLocations(dao) { _, _ -> secondLookups++; null })
        assertEquals(0, secondLookups)
    }

    @Test
    fun repairPendingMetadataLocationsRetriesFailedCoordinatesAcrossPages() = runBlocking {
        val coordinateA = 10.0 to 20.0
        val coordinateB = 30.0 to 40.0
        val aIds = (1L..201L).toList()
        aIds.forEach { id ->
            dao.upsertCore(
                metadata(id, locationName = null, country = null, city = null)
                    .copy(gpsLatitude = coordinateA.first, gpsLongitude = coordinateA.second)
            )
        }
        dao.upsertCore(
            metadata(300L, locationName = null, country = null, city = null)
                .copy(gpsLatitude = coordinateB.first, gpsLongitude = coordinateB.second)
        )

        val calls = mutableListOf<Pair<Double, Double>>()
        var failA = true
        val firstRetry = repairPendingMetadataLocations(dao) { latitude, longitude ->
            calls += latitude to longitude
            if (failA && latitude == coordinateA.first) throw IOException("unavailable")
            londonAddress()
        }
        assertTrue(firstRetry)
        assertEquals(listOf(coordinateA, coordinateB), calls)
        aIds.forEach { id ->
            assertNull(dao.getCoreMetadata(id)?.gpsLocationNameCity)
        }
        assertEquals("London", dao.getCoreMetadata(300L)?.gpsLocationNameCity)
        assertEquals("United Kingdom", dao.getCoreMetadata(300L)?.gpsLocationNameCountry)

        calls.clear()
        failA = false
        val secondRetry = repairPendingMetadataLocations(dao) { latitude, longitude ->
            calls += latitude to longitude
            londonAddress()
        }
        assertFalse(secondRetry)
        assertEquals(listOf(coordinateA), calls)
        aIds.forEach { id ->
            assertEquals("London", dao.getCoreMetadata(id)?.gpsLocationNameCity)
        }
    }

    @Test
    fun repairPendingMetadataLocationsPropagatesCancellation() = runBlocking {
        dao.upsertCore(metadata(1L, locationName = null, country = null, city = null))
        try {
            repairPendingMetadataLocations(dao) { _, _ -> throw CancellationException("stop") }
            fail("CancellationException should propagate")
        } catch (expected: CancellationException) {
        }
        assertNull(dao.getCoreMetadata(1L)?.gpsLocationNameCity)
    }

    @Test
    fun repairPendingMetadataLocationsSkipsInvalidCoordinates() = runBlocking {
        dao.upsertCore(
            metadata(1L, locationName = null, country = null, city = null)
                .copy(gpsLatitude = 91.0)
        )
        dao.upsertCore(metadata(2L, locationName = null, country = null, city = null))

        val calls = mutableListOf<Pair<Double, Double>>()
        val needsRetry = repairPendingMetadataLocations(dao) { latitude, longitude ->
            calls += latitude to longitude
            londonAddress()
        }

        assertFalse(needsRetry)
        assertEquals(listOf(51.5 to -0.1), calls)
        assertNull(dao.getCoreMetadata(1L)?.gpsLocationNameCity)
        assertEquals("London", dao.getCoreMetadata(2L)?.gpsLocationNameCity)
    }

    @Test
    fun repairPendingMetadataLocationsDoesNotWriteStaleCoordinates() = runBlocking {
        dao.upsertCore(metadata(1L, locationName = null, country = null, city = null))
        val needsRetry = repairPendingMetadataLocations(dao) { _, _ ->
            dao.upsertCore(dao.getCoreMetadata(1L)!!.copy(gpsLatitude = 40.0))
            londonAddress()
        }

        assertFalse(needsRetry)
        val row = dao.getCoreMetadata(1L)
        assertEquals(40.0, row?.gpsLatitude ?: 0.0, 0.0)
        assertNull(row?.gpsLocationNameCity)
        assertNull(row?.gpsLocationNameCountry)
    }

    @Test
    fun repairPendingMetadataLocationsSkipsZeroCoordinatesWithoutRetry() = runBlocking {
        dao.upsertCore(
            metadata(1L, locationName = null, country = null, city = null)
                .copy(gpsLatitude = 0.0, gpsLongitude = 0.0)
        )
        dao.upsertCore(metadata(2L, locationName = null, country = null, city = null))

        val calls = mutableListOf<Pair<Double, Double>>()
        val needsRetry = repairPendingMetadataLocations(dao) { latitude, longitude ->
            calls += latitude to longitude
            londonAddress()
        }

        assertFalse(needsRetry)
        assertEquals(listOf(51.5 to -0.1), calls)
        assertNull(dao.getCoreMetadata(1L)?.gpsLocationNameCity)
        assertEquals("London", dao.getCoreMetadata(2L)?.gpsLocationNameCity)
    }

    @Test
    fun repairedCloudMetadataReachesMergedLocationsLive() = runBlocking {
        val entity = CloudMediaEntity(
            remoteId = "repair-fixture",
            providerType = ProviderType.IMMICH,
            serverConfigId = 1,
            timestamp = 1_700_000_000_000L,
            mimeType = "image/jpeg",
            latitude = 51.5,
            longitude = -0.1,
        )
        val cloudId = entity.globalMediaId
        dao.upsertCore(metadata(cloudId, locationName = null, country = null, city = null))

        val received = Channel<List<LocationMedia>>(Channel.CONFLATED)
        val job = launch {
            dao.getFullMetadata()
                .map { rows ->
                    val local = rows.mapNotNull { row ->
                        val core = row.core
                        val latitude = core.gpsLatitude ?: return@mapNotNull null
                        val longitude = core.gpsLongitude ?: return@mapNotNull null
                        GeoMedia(
                            mediaId = core.mediaId,
                            latitude = latitude,
                            longitude = longitude,
                            locationCity = core.gpsLocationNameCity,
                            locationCountry = core.gpsLocationNameCountry,
                            media = entity.toUriMedia(),
                        )
                    }
                    buildActionableLocations(
                        localLocations = emptyList(),
                        geoMedia = mergeAccountQualifiedGeoMedia(local, listOf(entity), emptyList()),
                        cachedCloudMedia = emptyList(),
                    )
                }
                .collect { received.send(it) }
        }

        try {
            val expectedCoordinates =
                String.format(Locale.getDefault(), "%.4f, %.4f", 51.5, -0.1)
            val initial = withTimeout(5_000) { received.receive() }
            assertEquals(1, initial.size)
            assertEquals(expectedCoordinates, initial.single().location)

            assertFalse(repairPendingMetadataLocations(dao) { _, _ -> londonAddress() })
            val repaired = withTimeout(5_000) { received.receive() }
            assertEquals("London, United Kingdom", repaired.single().location)
            assertEquals("London", repaired.single().city)
        } finally {
            job.cancelAndJoin()
            received.close()
        }
    }

    @Test
    fun mergedCloudLocationsPreserveRepairedNamesPerAccount() {
        val entity = CloudMediaEntity(
            remoteId = "repair-fixture",
            providerType = ProviderType.IMMICH,
            serverConfigId = 1,
            timestamp = 1_700_000_000_000L,
            mimeType = "image/jpeg",
            latitude = 51.5,
            longitude = -0.1,
        )
        val otherAccountEntity = CloudMediaEntity(
            remoteId = "repair-fixture",
            providerType = ProviderType.IMMICH,
            serverConfigId = 2,
            timestamp = 1_700_000_000_000L,
            mimeType = "image/jpeg",
            latitude = 51.5,
            longitude = -0.1,
        )
        val repairedLocal = GeoMedia(
            mediaId = entity.globalMediaId,
            latitude = 51.5,
            longitude = -0.1,
            locationCity = "London",
            locationCountry = "United Kingdom",
            media = entity.toUriMedia(),
        )
        val marker = CloudMapMarker(
            latitude = 51.5,
            longitude = -0.1,
            assetId = "repair-fixture",
            providerType = ProviderType.IMMICH,
        )

        val merged = mergeAccountQualifiedGeoMedia(
            localGeoMedia = listOf(repairedLocal),
            cachedCloudMedia = listOf(entity, otherAccountEntity),
            liveMarkers = listOf(
                AccountCloudMapMarker(configId = 1L, marker = marker),
                AccountCloudMapMarker(configId = 2L, marker = marker),
            ),
        )

        val repaired = merged.single { it.mediaId == entity.globalMediaId }
        assertEquals("London", repaired.locationCity)
        assertEquals("United Kingdom", repaired.locationCountry)
        val otherAccount = merged.single { it.mediaId == otherAccountEntity.globalMediaId }
        assertNull(otherAccount.locationCity)
        assertNull(otherAccount.locationCountry)
    }

    @Test
    fun updateGeocodedLocationsPreservesResolvedAndStaleRowsInOneBatch() = runBlocking {
        dao.upsertCore(metadata(1L, locationName = null, country = null, city = null))
        dao.upsertCore(
            metadata(
                2L,
                locationName = "Cardiff, United Kingdom",
                country = "United Kingdom",
                city = "Cardiff",
            )
        )
        dao.upsertCore(
            metadata(3L, locationName = null, country = null, city = null)
                .copy(gpsLatitude = 40.0)
        )

        val updated = dao.updateGeocodedLocations(
            listOf(
                GeocodedMetadataLocation(1L, 51.5, -0.1, "London, United Kingdom", "United Kingdom", "London"),
                GeocodedMetadataLocation(2L, 51.5, -0.1, "X", "X", "X"),
                GeocodedMetadataLocation(3L, 51.5, -0.1, "X", "X", "X"),
            )
        )

        assertEquals(1, updated)
        assertEquals("London", dao.getCoreMetadata(1L)?.gpsLocationNameCity)
        assertEquals("London, United Kingdom", dao.getCoreMetadata(1L)?.gpsLocationName)
        assertEquals("Cardiff", dao.getCoreMetadata(2L)?.gpsLocationNameCity)
        assertNull(dao.getCoreMetadata(3L)?.gpsLocationNameCity)
    }

    @Test
    fun repairPendingMetadataLocationsFlushesCompletedPageBeforeNextLookup() = runBlocking {
        (1L..33L).forEach { id ->
            dao.upsertCore(metadata(id, locationName = null, country = null, city = null))
        }
        dao.upsertCore(
            metadata(40L, locationName = null, country = null, city = null)
                .copy(gpsLatitude = 30.0, gpsLongitude = 40.0)
        )

        var firstPageDurable = false
        val needsRetry = repairPendingMetadataLocations(dao) { latitude, _ ->
            if (latitude == 30.0) {
                firstPageDurable = dao.getCoreMetadata(1L)?.gpsLocationNameCity == "London"
            }
            londonAddress()
        }

        assertFalse(needsRetry)
        assertTrue(firstPageDurable)
        assertEquals("London", dao.getCoreMetadata(40L)?.gpsLocationNameCity)
    }

    @Test
    fun mergedCloudLocationsDoNotInheritStaleNamesAcrossMovedCoordinates() {
        val entity = CloudMediaEntity(
            remoteId = "moved-asset",
            providerType = ProviderType.IMMICH,
            serverConfigId = 1,
            timestamp = 1_700_000_000_000L,
            mimeType = "image/jpeg",
            latitude = 48.85,
            longitude = 2.35,
        )
        val localRepaired = GeoMedia(
            mediaId = entity.globalMediaId,
            latitude = 51.5,
            longitude = -0.1,
            locationCity = "London",
            locationCountry = "United Kingdom",
            media = entity.toUriMedia(),
        )
        val marker = CloudMapMarker(
            latitude = 48.85,
            longitude = 2.35,
            assetId = "moved-asset",
            providerType = ProviderType.IMMICH,
        )

        val merged = mergeAccountQualifiedGeoMedia(
            localGeoMedia = listOf(localRepaired),
            cachedCloudMedia = listOf(entity),
            liveMarkers = listOf(AccountCloudMapMarker(configId = 1L, marker = marker)),
        ).single()

        assertEquals(48.85, merged.latitude, 0.0)
        assertEquals(2.35, merged.longitude, 0.0)
        assertNull(merged.locationCity)
        assertNull(merged.locationCountry)
    }

    @Test
    fun mergedGeoMediaOmitsLocalSeedsWithInvalidCoordinates() {
        val invalid = GeoMedia(
            mediaId = 7L,
            latitude = 91.0,
            longitude = 0.0,
            locationCity = "Nowhere",
            locationCountry = null,
            media = CloudMediaEntity(
                remoteId = "invalid-seed",
                providerType = ProviderType.IMMICH,
                serverConfigId = 1,
                timestamp = 1_700_000_000_000L,
                mimeType = "image/jpeg",
            ).toUriMedia().copy(id = 7L),
        )

        assertTrue(
            mergeAccountQualifiedGeoMedia(
                localGeoMedia = listOf(invalid),
                cachedCloudMedia = emptyList(),
                liveMarkers = emptyList(),
            ).isEmpty()
        )
    }

    @Test
    fun mergedGeoMediaReusesCanonicalMediaByIdInstances() {
        val entity = CloudMediaEntity(
            remoteId = "repair-fixture",
            providerType = ProviderType.IMMICH,
            serverConfigId = 1,
            timestamp = 1_700_000_000_000L,
            mimeType = "image/jpeg",
            latitude = 51.5,
            longitude = -0.1,
        )
        val canonical = entity.toUriMedia()

        val merged = mergeAccountQualifiedGeoMedia(
            localGeoMedia = emptyList(),
            cachedCloudMedia = listOf(entity),
            liveMarkers = emptyList(),
            mediaById = mapOf(entity.globalMediaId to canonical),
        )

        assertSame(canonical, merged.single().media)

        val fallback = mergeAccountQualifiedGeoMedia(
            localGeoMedia = emptyList(),
            cachedCloudMedia = listOf(entity),
            liveMarkers = emptyList(),
        )
        assertNotSame(canonical, fallback.single().media)
        assertEquals(canonical, fallback.single().media)
    }

    @Test
    fun metadataLocationRepairRequestRequiresConnectedNetworkOnly() {
        val request = metadataLocationRepairRequest()
        val spec = request.workSpec
        assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
        assertFalse(spec.constraints.requiresCharging())
        assertFalse(spec.constraints.requiresBatteryNotLow())
        assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
        assertEquals(60_000L, spec.backoffDelayDuration)
        assertTrue(request.tags.contains(METADATA_LOCATION_REPAIR_WORK))
    }

    @Test
    fun metadataLocationRepairResultMapsRetryAttempts() {
        assertEquals(
            ListenableWorker.Result.success(),
            metadataLocationRepairResult(needsRetry = false, runAttemptCount = 0)
        )
        assertEquals(
            ListenableWorker.Result.retry(),
            metadataLocationRepairResult(needsRetry = true, runAttemptCount = 0)
        )
        assertEquals(
            ListenableWorker.Result.retry(),
            metadataLocationRepairResult(needsRetry = true, runAttemptCount = 2)
        )
        assertEquals(
            ListenableWorker.Result.failure(),
            metadataLocationRepairResult(needsRetry = true, runAttemptCount = 3)
        )
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

    @Test
    fun zeroCoordinateRowsAreSelectedForRedactionRepair() = runBlocking {
        dao.upsertCore(metadata(1L, locationName = null, country = null, city = null)
            .copy(gpsLatitude = 0.0, gpsLongitude = 0.0))
        dao.upsertCore(metadata(2L, locationName = null, country = null, city = null))
        dao.upsertCore(metadata(3L, locationName = null, country = null, city = null)
            .copy(gpsLatitude = null, gpsLongitude = null))
        dao.upsertCore(metadata(4L, locationName = null, country = null, city = null)
            .copy(gpsLatitude = 0.0, gpsLongitude = 26.1))

        assertEquals(listOf(1L), dao.getZeroCoordinateMediaIds())
    }

    @Test
    fun redactedLocationRepairReparsesOnlyPoisonedRows() = runBlocking {
        val mediaDao = database.getMediaDao()
        mediaDao.addMediaList(listOf(uriMedia(1L), uriMedia(3L)))
        dao.upsertCore(metadata(1L, locationName = null, country = null, city = null)
            .copy(gpsLatitude = 0.0, gpsLongitude = 0.0))
        dao.upsertCore(metadata(2L, locationName = null, country = null, city = null)
            .copy(gpsLatitude = 0.0, gpsLongitude = 0.0))
        dao.upsertCore(metadata(3L, locationName = null, country = null, city = null)
            .copy(gpsLatitude = 0.0, gpsLongitude = 0.0))
        dao.upsertCore(metadata(4L, locationName = null, country = null, city = null))

        val parsedIds = mutableListOf<Long>()
        val repaired = repairRedactedMetadataLocations(mediaDao, dao) { media ->
            parsedIds += media.id
            if (media.id == 3L) null else parsedMetadata(media.id, 44.4, 26.1)
        }

        assertEquals(1, repaired)
        assertEquals(setOf(1L, 3L), parsedIds.toSet())
        assertEquals(44.4, dao.getCoreMetadata(1L)?.gpsLatitude ?: -1.0, 0.0)
        assertEquals(0.0, dao.getCoreMetadata(2L)?.gpsLatitude ?: -1.0, 0.0)
        assertEquals(0.0, dao.getCoreMetadata(3L)?.gpsLatitude ?: -1.0, 0.0)
        assertEquals(51.5, dao.getCoreMetadata(4L)?.gpsLatitude ?: -1.0, 0.0)
    }

    private fun uriMedia(id: Long) = Media.UriMedia(
        id = id,
        label = "IMG_$id.jpg",
        uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
        path = "/storage/emulated/0/DCIM/IMG_$id.jpg",
        relativePath = "DCIM/",
        albumID = -1L,
        albumLabel = "Camera",
        timestamp = 1_700_000_000L,
        fullDate = "",
        mimeType = "image/jpeg",
        favorite = 0,
        trashed = 0,
        size = 1_000_000L
    )

    private fun parsedMetadata(mediaId: Long, lat: Double?, lon: Double?) = MediaMetadata(
        mediaId = mediaId,
        imageDescription = null,
        dateTimeOriginal = null,
        manufacturerName = "Google",
        modelName = "Pixel",
        aperture = null,
        exposureTime = null,
        iso = null,
        gpsLatitude = lat,
        gpsLongitude = lon,
        gpsLocationName = null,
        gpsLocationNameCountry = null,
        gpsLocationNameCity = null,
        imageWidth = 4000,
        imageHeight = 3000,
        imageResolutionX = null,
        imageResolutionY = null,
        resolutionUnit = null,
        durationMs = null,
        videoWidth = null,
        videoHeight = null,
        frameRate = null,
        bitRate = null,
        isNightMode = false,
        isPanorama = false,
        isPhotosphere = false,
        isLongExposure = false,
        isMotionPhoto = false
    )

    private fun londonAddress() = Address(Locale.US).apply {
        locality = "London"
        countryName = "United Kingdom"
        setAddressLine(0, "London, United Kingdom")
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
