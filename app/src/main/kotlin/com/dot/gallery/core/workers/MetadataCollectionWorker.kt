package com.dot.gallery.core.workers

import android.content.Context
import android.location.Address
import android.location.Geocoder
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastMap
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dot.gallery.BuildConfig
import com.dot.gallery.core.Settings
import com.dot.gallery.core.sandbox.IsolatedMetadataParser
import com.dot.gallery.core.util.ProgressThrottler
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
import com.dot.gallery.feature_node.domain.model.MediaMetadata
import com.dot.gallery.feature_node.domain.model.MediaVersion
import com.dot.gallery.feature_node.domain.model.bestEffortReverseGeocode
import com.dot.gallery.feature_node.domain.model.metadataParsingPolicy
import com.dot.gallery.feature_node.domain.model.retrieveExtraMediaMetadata
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.presentation.util.formattedAddress
import com.dot.gallery.feature_node.presentation.util.isMetadataUpToDate
import com.dot.gallery.feature_node.presentation.util.locationGroupName
import com.dot.gallery.feature_node.presentation.util.mediaStoreVersion
import com.dot.gallery.feature_node.presentation.util.printDebug
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val METADATA_GEOCODE_REPAIR_BATCH = 200

fun WorkManager.forceMetadataCollect() {
    val metadataWork = OneTimeWorkRequestBuilder<MetadataCollectionWorker>()
        .setInputData(workDataOf("forceReload" to true))
        .addTag("MetadataCollection_Force")
        .build()

    enqueueUniqueWork("MetadataCollection", ExistingWorkPolicy.APPEND_OR_REPLACE, metadataWork)
}

@HiltWorker
class MetadataCollectionWorker @AssistedInject constructor(
    private val database: InternalDatabase,
    private val repository: MediaRepository,
    private val geocoder: Geocoder?,
    private val isolatedParser: IsolatedMetadataParser,
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = runCatching {
        if (!BuildConfig.ENABLE_INDEXING) {
            collectCloudMediaMetadata()
            return Result.success()
        }
        val forceReload = inputData.getBoolean("forceReload", false)
        reverseGeocodePendingMetadata()
        if (database.isMetadataUpToDate(appContext) && !forceReload) {
            printDebug("Metadata is up to date")
            collectCloudMediaMetadata()
            return Result.success()
        }
        printDebug("Updating metadata...")
        setProgress(workDataOf("progress" to 0))
        if (forceReload) {
            printDebug("Force reloading metadata...")
        }
        val oldMedia = database.getMediaDao().getMedia()
        val media = repository.getCompleteMedia().map { it.data ?: emptyList() }.firstOrNull()
        printDebug("Retrieved ${media?.size ?: 0} media items from repository.")
        val differentMedia = if (!forceReload) {
            val oldMediaIds = oldMedia.mapTo(HashSet(oldMedia.size)) { it.id }
            media.orEmpty().filter { it.id !in oldMediaIds }
        } else media
        printDebug("Found ${differentMedia?.size ?: 0} new or updated media items.")
        media?.let {
            printDebug("Deleting forgotten metadata...")
            val localIds = it.fastMap { m -> m.id }
            val cloudIds = try {
                database.getCloudMediaDao().getAllAsync().map { c -> c.globalMediaId }
            } catch (_: Exception) { emptyList() }
            database.getMetadataDao().deleteForgottenMetadata(localIds + cloudIds)
        }
        differentMedia?.let { diffMedia ->
            if (diffMedia.isEmpty()) {
                printDebug("No new media to update metadata for.")
                collectCloudMediaMetadata()
                setProgress(workDataOf("progress" to 100))
                return Result.success()
            }
            val isolationMode = Settings.Security.getMetadataIsolationMode(appContext)
                .firstOrNull() ?: Settings.Security.METADATA_ISOLATION_SHARED
            val usePerFile = isolationMode == Settings.Security.METADATA_ISOLATION_PER_FILE
            printDebug("Updating metadata for ${diffMedia.size} items... (isolation=$isolationMode)")
            val throttler = ProgressThrottler()
            val total = diffMedia.size
            diffMedia.fastForEachIndexed { index, it ->
                if (!currentCoroutineContext().isActive || isStopped) return@fastForEachIndexed
                val pct =
                    if (total <= 1) 100 else (((index + 1).toFloat() / total.toFloat()) * 100f).roundToInt()
                throttler.emit(pct) { setProgress(workDataOf("progress" to it)) }
                appContext.retrieveExtraMediaMetadata(
                    isolatedParser = isolatedParser,
                    geocoder = geocoder,
                    media = it,
                    usePerFileIsolation = usePerFile,
                    policy = metadataParsingPolicy(bulk = true)
                )?.let { metadata ->
                    database.getMetadataDao().addMetadata(metadata)
                }
            }
        }
        printDebug("Metadata update complete")
        database.getMetadataDao().setMediaVersion(MediaVersion(appContext.mediaStoreVersion))

        // Also populate metadata for cached cloud media
        collectCloudMediaMetadata()

        setProgress(workDataOf("progress" to 100))
        return Result.success()
    }.getOrElse { exception ->
        if (exception is CancellationException) throw exception
        printDebug("MetadataCollectionWorker failed with exception: ${exception.message}")
        return Result.failure()
    }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocodePendingMetadata() {
        val activeGeocoder = geocoder ?: return
        val metadataDao = database.getMetadataDao()
        val pending = metadataDao.getPendingMetadataLocations(METADATA_GEOCODE_REPAIR_BATCH)
        if (pending.isEmpty()) return
        val addressByCoordinate = HashMap<Pair<Long, Long>, Address?>()
        var repaired = 0
        for (item in pending) {
            if (!currentCoroutineContext().isActive || isStopped) return
            val coordinateKey = item.gpsLatitude.toBits() to item.gpsLongitude.toBits()
            val address = if (addressByCoordinate.containsKey(coordinateKey)) {
                addressByCoordinate[coordinateKey]
            } else {
                bestEffortReverseGeocode(
                    enabled = true,
                    latitude = item.gpsLatitude,
                    longitude = item.gpsLongitude
                ) { latitude, longitude ->
                    withContext(Dispatchers.IO) {
                        activeGeocoder.getFromLocation(latitude, longitude, 1).orEmpty().firstOrNull()
                    }
                }.also { addressByCoordinate[coordinateKey] = it }
            } ?: continue
            currentCoroutineContext().ensureActive()
            val locationName = address.formattedAddress.takeIf(String::isNotBlank)
            val country = address.countryName?.takeIf(String::isNotBlank)
            val city = address.locationGroupName
            if (locationName == null && country == null && city == null) continue
            repaired += metadataDao.updateGeocodedLocation(
                mediaId = item.mediaId,
                latitude = item.gpsLatitude,
                longitude = item.gpsLongitude,
                locationName = locationName,
                country = country,
                city = city,
            )
        }
        printDebug("Reverse geocoded $repaired of ${pending.size} pending metadata locations")
    }

    private suspend fun collectCloudMediaMetadata() {
        try {
            val cloudEntities = database.getCloudMediaDao().getAllAsync()
            printDebug("collectCloudMediaMetadata: found ${cloudEntities.size} cloud entities")
            if (cloudEntities.isEmpty()) return
            val metadataDao = database.getMetadataDao()
            var count = 0
            var skipped = 0
            for (entity in cloudEntities) {
                val mediaId = entity.globalMediaId
                val existing = metadataDao.getCoreMetadata(mediaId)
                if (existing != null && (existing.imageWidth > 0 || existing.manufacturerName != null)) {
                    skipped++
                    continue
                }
                if (entity.width == 0 && entity.cameraMake == null) {
                    skipped++
                    continue
                }
                val locationName = listOfNotNull(entity.city, entity.state, entity.country)
                    .joinToString(", ").ifBlank { null }
                val isVideo = entity.mimeType.startsWith("video")
                val metadata = MediaMetadata(
                    mediaId = mediaId,
                    imageDescription = entity.imageDescription,
                    dateTimeOriginal = entity.dateTimeOriginal,
                    manufacturerName = entity.cameraMake,
                    modelName = entity.cameraModel,
                    lensModel = entity.lensModel,
                    aperture = entity.aperture,
                    exposureTime = entity.exposureTime,
                    iso = entity.iso?.toString(),
                    focalLength = entity.focalLength,
                    gpsLatitude = entity.latitude,
                    gpsLongitude = entity.longitude,
                    gpsLocationName = locationName,
                    gpsLocationNameCountry = entity.country,
                    gpsLocationNameCity = entity.city,
                    imageWidth = entity.width,
                    imageHeight = entity.height,
                    imageResolutionX = null,
                    imageResolutionY = null,
                    resolutionUnit = null,
                    durationMs = if (isVideo) entity.duration?.let { parseDurationToMs(it) } else null,
                    videoWidth = if (isVideo) entity.width else null,
                    videoHeight = if (isVideo) entity.height else null,
                    frameRate = null,
                    bitRate = null,
                    isNightMode = false,
                    isPanorama = false,
                    isPhotosphere = false,
                    isLongExposure = false,
                    isMotionPhoto = false
                )
                metadataDao.addMetadata(metadata)
                count++
            }
            printDebug("collectCloudMediaMetadata: populated=$count, skipped=$skipped (already existed)")
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            printDebug("collectCloudMediaMetadata: error: ${error.message}")
        }
    }

    private fun parseDurationToMs(duration: String): Long? {
        return try {
            val parts = duration.split(":")
            if (parts.size == 3) {
                val hours = parts[0].toLong()
                val minutes = parts[1].toLong()
                val seconds = parts[2].toDouble()
                ((hours * 3600 + minutes * 60) * 1000 + (seconds * 1000)).toLong()
            } else null
        } catch (_: Exception) { null }
    }
}