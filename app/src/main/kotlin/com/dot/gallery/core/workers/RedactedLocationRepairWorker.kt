package com.dot.gallery.core.workers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dot.gallery.core.Settings
import com.dot.gallery.core.logging.withLogScope
import com.dot.gallery.core.sandbox.IsolatedMetadataParser
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
import com.dot.gallery.feature_node.data.data_source.MediaDao
import com.dot.gallery.feature_node.data.data_source.MetadataDao
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadata
import com.dot.gallery.feature_node.domain.model.metadataParsingPolicy
import com.dot.gallery.feature_node.domain.model.retrieveExtraMediaMetadata
import com.dot.gallery.feature_node.presentation.util.printDebug
import com.dot.gallery.feature_node.presentation.util.printWarning
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.firstOrNull

internal const val REDACTED_LOCATION_REPAIR_WORK = "RedactedLocationRepair"

fun WorkManager.enqueueRedactedLocationRepair(
    policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
) {
    enqueueUniqueWork(
        REDACTED_LOCATION_REPAIR_WORK,
        policy,
        OneTimeWorkRequestBuilder<RedactedLocationRepairWorker>()
            .addTag(REDACTED_LOCATION_REPAIR_WORK)
            .build()
    )
}

/**
 * Re-parses metadata rows that were captured while MediaProvider redacted GPS EXIF
 * (missing ACCESS_MEDIA_LOCATION). Those rows carry the literal 0,0 coordinate pair,
 * which nothing else produces — so once the permission is granted this worker can
 * repair exactly the poisoned rows and store either the real fix or no coordinate.
 */
@HiltWorker
class RedactedLocationRepairWorker @AssistedInject constructor(
    private val database: InternalDatabase,
    private val geocoder: Geocoder?,
    private val isolatedParser: IsolatedMetadataParser,
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withLogScope("worker.location-repair") {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return@withLogScope Result.success()
        }
        val isolationMode = Settings.Security.getMetadataIsolationMode(appContext)
            .firstOrNull() ?: Settings.Security.METADATA_ISOLATION_SHARED
        val usePerFile = isolationMode == Settings.Security.METADATA_ISOLATION_PER_FILE
        val repaired = try {
            repairRedactedMetadataLocations(
                mediaDao = database.getMediaDao(),
                metadataDao = database.getMetadataDao(),
            ) { media ->
                appContext.retrieveExtraMediaMetadata(
                    isolatedParser = isolatedParser,
                    geocoder = geocoder,
                    media = media,
                    usePerFileIsolation = usePerFile,
                    policy = metadataParsingPolicy(bulk = true)
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            printWarning("Redacted location repair failed: ${error.javaClass.simpleName}")
            return@withLogScope if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
        printDebug("Re-parsed $repaired metadata rows captured under GPS redaction")
        Result.success()
    }
}

/**
 * Re-parses every metadata row whose stored coordinates are the redaction signature
 * (literal 0,0) via [parse] and upserts the result. Rows whose media vanished are
 * skipped; a parse returning null or still-0,0 coordinates leaves the row queued for
 * a later attempt. Returns the number of rows successfully re-parsed.
 */
internal suspend fun repairRedactedMetadataLocations(
    mediaDao: MediaDao,
    metadataDao: MetadataDao,
    parse: suspend (Media) -> MediaMetadata?,
): Int {
    var repaired = 0
    for (mediaId in metadataDao.getZeroCoordinateMediaIds()) {
        currentCoroutineContext().ensureActive()
        val media = runCatching { mediaDao.getMediaById(mediaId) }.getOrNull() ?: continue
        val metadata = runCatching { parse(media) }.getOrNull() ?: continue
        metadataDao.addMetadata(metadata)
        repaired++
    }
    return repaired
}
