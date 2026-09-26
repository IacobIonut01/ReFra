package com.dot.gallery.core.workers

import android.content.Context
import android.location.Address
import android.location.Geocoder
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dot.gallery.core.logging.withLogScope
import com.dot.gallery.feature_node.data.data_source.GeocodedMetadataLocation
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
import com.dot.gallery.feature_node.data.data_source.MetadataDao
import com.dot.gallery.feature_node.domain.model.bestEffortReverseGeocode
import com.dot.gallery.feature_node.domain.model.locationCoordinateGroupKey
import com.dot.gallery.feature_node.presentation.util.formattedAddress
import com.dot.gallery.feature_node.presentation.util.locationGroupName
import com.dot.gallery.feature_node.presentation.util.printDebug
import com.dot.gallery.feature_node.presentation.util.printWarning
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

internal const val METADATA_LOCATION_REPAIR_WORK = "MetadataLocationRepair"
private const val METADATA_GEOCODE_REPAIR_BATCH = 32

fun WorkManager.enqueueMetadataLocationRepair(
    policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
) {
    enqueueUniqueWork(METADATA_LOCATION_REPAIR_WORK, policy, metadataLocationRepairRequest())
}

internal fun metadataLocationRepairRequest(): OneTimeWorkRequest =
    OneTimeWorkRequestBuilder<MetadataLocationRepairWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
        .addTag(METADATA_LOCATION_REPAIR_WORK)
        .build()

internal fun metadataLocationRepairResult(needsRetry: Boolean, runAttemptCount: Int): ListenableWorker.Result =
    when {
        !needsRetry -> ListenableWorker.Result.success()
        runAttemptCount < 3 -> ListenableWorker.Result.retry()
        else -> ListenableWorker.Result.failure()
    }

@HiltWorker
class MetadataLocationRepairWorker @AssistedInject constructor(
    private val database: InternalDatabase,
    private val geocoder: Geocoder?,
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    @Suppress("DEPRECATION")
    override suspend fun doWork(): Result = withLogScope("worker.metadata-repair") {
        val activeGeocoder = geocoder ?: run {
            printWarning("Location name repair unavailable: no geocoder")
            return@withLogScope Result.success()
        }
        val needsRetry = try {
            repairPendingMetadataLocations(database.getMetadataDao()) { latitude, longitude ->
                withContext(Dispatchers.IO) {
                    activeGeocoder.getFromLocation(latitude, longitude, 1).orEmpty().firstOrNull()
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            printWarning("Location name repair failed: ${error.javaClass.simpleName}")
            true
        }
        if (needsRetry) printWarning("Location name repair incomplete (attempt ${runAttemptCount + 1})")
        metadataLocationRepairResult(needsRetry, runAttemptCount)
    }
}

internal suspend fun repairPendingMetadataLocations(
    metadataDao: MetadataDao,
    lookup: suspend (Double, Double) -> Address?,
): Boolean {
    val addressByCoordinate = object : LinkedHashMap<String, Address?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Address?>): Boolean =
            size > 256
    }
    var afterMediaId = Long.MIN_VALUE
    var examined = 0
    var repaired = 0
    var needsRetry = false
    while (true) {
        currentCoroutineContext().ensureActive()
        val pending = metadataDao.getPendingMetadataLocations(afterMediaId, METADATA_GEOCODE_REPAIR_BATCH)
        if (pending.isEmpty()) break
        afterMediaId = pending.last().mediaId
        examined += pending.size
        val updates = ArrayList<GeocodedMetadataLocation>(pending.size)
        for (item in pending) {
            currentCoroutineContext().ensureActive()
            if (item.gpsLatitude == 0.0 && item.gpsLongitude == 0.0) continue
            val coordinateKey = locationCoordinateGroupKey(item.gpsLatitude, item.gpsLongitude) ?: continue
            val address = if (addressByCoordinate.containsKey(coordinateKey)) {
                addressByCoordinate[coordinateKey]
            } else {
                bestEffortReverseGeocode(true, item.gpsLatitude, item.gpsLongitude, lookup)
                    .also { addressByCoordinate[coordinateKey] = it }
            }
            if (address == null) {
                needsRetry = true
                continue
            }
            currentCoroutineContext().ensureActive()
            val locationName = address.formattedAddress.takeIf(String::isNotBlank)
            val country = address.countryName?.takeIf(String::isNotBlank)
            val city = address.locationGroupName
            if (country == null && city == null) {
                needsRetry = true
                continue
            }
            updates += GeocodedMetadataLocation(
                mediaId = item.mediaId,
                latitude = item.gpsLatitude,
                longitude = item.gpsLongitude,
                locationName = locationName,
                country = country,
                city = city,
            )
        }
        currentCoroutineContext().ensureActive()
        if (updates.isNotEmpty()) repaired += metadataDao.updateGeocodedLocations(updates)
    }
    printDebug("Reverse geocoded $repaired of $examined pending metadata locations")
    return needsRetry
}
