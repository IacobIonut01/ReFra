package com.dot.gallery.core.workers

import android.content.Context
import android.location.Geocoder
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastMap
import com.dot.gallery.core.work.WorkComponentHolder
import com.dot.gallery.core.work.GalleryWorker
import com.dot.gallery.core.work.GalleryWorkerAdapter
import com.dot.gallery.core.work.WorkInput
import com.dot.gallery.core.work.WorkSignals
import com.dot.gallery.core.work.workData
import com.dot.gallery.core.work.workDataJson
import dev.brewkits.kmpworkmanager.annotations.Worker
import dev.brewkits.kmpworkmanager.background.domain.BackgroundTaskScheduler
import dev.brewkits.kmpworkmanager.background.domain.Constraints
import dev.brewkits.kmpworkmanager.background.domain.ExistingPolicy
import dev.brewkits.kmpworkmanager.background.domain.TaskTrigger
import dev.brewkits.kmpworkmanager.background.domain.WorkerEnvironment
import dev.brewkits.kmpworkmanager.background.domain.WorkerResult
import com.dot.gallery.core.Settings
import com.dot.gallery.core.sandbox.IsolatedMetadataParser
import com.dot.gallery.core.util.ProgressThrottler
import com.dot.gallery.data.InternalDatabase
import com.dot.gallery.data.entity.MediaMetadata
import com.dot.gallery.domain.model.MediaVersion
import com.dot.gallery.domain.model.retrieveExtraMediaMetadata
import com.dot.gallery.data.repository.MediaRepository
import com.dot.gallery.app.presentation.util.isMetadataUpToDate
import com.dot.gallery.core.logging.withLogScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import com.dot.gallery.core.util.or
import com.dot.gallery.core.presentation.util.update
import com.dot.gallery.domain.util.isVideo
import com.dot.gallery.core.presentation.util.mediaStoreVersion
import com.dot.gallery.core.logging.printDebug
import com.dot.gallery.data.entity.metadataParsingPolicy
import com.dot.gallery.core.logging.printError
import com.dot.gallery.BuildConfig
import me.tatarka.inject.annotations.Inject




suspend fun BackgroundTaskScheduler.forceMetadataCollect() {
    enqueue(
        id = "MetadataCollection",
        trigger = TaskTrigger.OneTime(),
        workerClassName = MetadataCollectionWorker.WORKER_NAME,
        inputJson = workDataJson("forceReload" to true),
        // was APPEND_OR_REPLACE: forcing a reload while one runs restarts it with force=true
        policy = ExistingPolicy.REPLACE,
        tags = setOf("MetadataCollection_Force"),
    )
}

class MetadataCollectionWorker @Inject constructor(
    private val database: InternalDatabase,
    private val repository: MediaRepository,
    private val geocoder: Geocoder?,
    private val isolatedParser: IsolatedMetadataParser,
    private val appContext: Context,
) : GalleryWorker {

    override suspend fun doWork(input: WorkInput, env: WorkerEnvironment): WorkerResult = withLogScope("worker.metadata-collection") { runCatching {
        if (!BuildConfig.ENABLE_INDEXING) {
            collectCloudMediaMetadata()
            return@withLogScope WorkerResult.Success()
        }
        val forceReload = input.getBoolean("forceReload", false)
        reverseGeocodePendingMetadata()
        if (database.isMetadataUpToDate(appContext) && !forceReload) {
            printDebug("Metadata is up to date")
            collectCloudMediaMetadata()
            return@withLogScope WorkerResult.Success()
        }
        printDebug("Updating metadata...")
        WorkSignals.reportProgress("MetadataCollection", workData("progress" to 0))
        if (forceReload) {
            printDebug("Force reloading metadata...")
        }
        val oldMedia = database.getMediaDao().getMedia()
        val media = repository.getCompleteMedia().map { it.data ?: emptyList() }.firstOrNull()
        printDebug("Retrieved ${media?.size ?: 0} media items from repository.")
        val differentMedia = if (!forceReload) {
            val oldMediaIds = oldMedia.mapTo(HashSet(oldMedia.size)) { it.id }
            // Also re-collect videos that lack complete metadata rows — an earlier
            // parse may have produced nothing (e.g. isolated-service video parse
            // failing on Android 12) and this is their only bulk retry.
            val completeIds = database.getMetadataDao().getCompleteMetadataIds().toHashSet()
            media.orEmpty().filter { it.id !in oldMediaIds || it.isVideo && it.id !in completeIds }
        } else media
        printDebug("Found ${differentMedia?.size ?: 0} new or updated media items.")
        media?.let {
            printDebug("Deleting forgotten metadata...")
            val localIds = it.fastMap { m -> m.id }
            val cloudIds = try {
                database.getCloudMediaDao().getAllAsync().map { c -> c.globalMediaId }
            } catch (e: Exception) {
                printError(
                    "worker.metadata",
                    "failed to load cloud media ids; skipping forgotten-metadata cleanup",
                    e
                )
                null
            }
            if (cloudIds != null) {
                database.getMetadataDao().deleteForgottenMetadata(localIds + cloudIds)
            }
        }
        differentMedia?.let { diffMedia ->
            if (diffMedia.isEmpty()) {
                printDebug("No new media to update metadata for.")
                collectCloudMediaMetadata()
                WorkSignals.reportProgress("MetadataCollection", workData("progress" to 100))
                return@withLogScope WorkerResult.Success()
            }
            val isolationMode = Settings.Security.getMetadataIsolationMode(appContext)
                .firstOrNull() ?: Settings.Security.METADATA_ISOLATION_SHARED
            val usePerFile = isolationMode == Settings.Security.METADATA_ISOLATION_PER_FILE
            printDebug("Updating metadata for ${diffMedia.size} items... (isolation=$isolationMode)")
            val throttler = ProgressThrottler()
            val total = diffMedia.size
            diffMedia.fastForEachIndexed { index, it ->
                if (!currentCoroutineContext().isActive || env.isCancelled()) return@fastForEachIndexed
                val pct =
                    if (total <= 1) 100 else (((index + 1).toFloat() / total.toFloat()) * 100f).roundToInt()
                throttler.emit(pct) { WorkSignals.reportProgress("MetadataCollection", workData("progress" to it)) }
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

        WorkSignals.reportProgress("MetadataCollection", workData("progress" to 100))
        return@withLogScope WorkerResult.Success()
    }.getOrElse { exception ->
        if (exception is CancellationException) throw exception
        printDebug("MetadataCollectionWorker failed with exception: ${exception.message}")
        return@withLogScope WorkerResult.Failure("metadata collection failed")
    } }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocodePendingMetadata() {
        val activeGeocoder = geocoder ?: return
        repairPendingMetadataLocations(database.getMetadataDao()) { latitude, longitude ->
            withContext(Dispatchers.IO) {
                activeGeocoder.getFromLocation(latitude, longitude, 1).orEmpty().firstOrNull()
            }
        }
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

    companion object {
        const val WORKER_NAME = "MetadataCollectionWorker"
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
@Worker(name = MetadataCollectionWorker.WORKER_NAME)
class MetadataCollectionWorkerAdapter :
    GalleryWorkerAdapter("MetadataCollection") {
    override suspend fun run(input: WorkInput, env: WorkerEnvironment): WorkerResult =
        WorkComponentHolder.component.metadataCollectionWorker.doWork(input, env)
}
