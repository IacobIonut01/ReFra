package com.dot.gallery.core.workers

import android.content.Context
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
import com.dot.gallery.core.util.ProgressThrottler
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
import com.dot.gallery.feature_node.domain.model.MediaVersion
import com.dot.gallery.feature_node.domain.model.retrieveExtraMediaMetadata
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.presentation.util.isMetadataUpToDate
import com.dot.gallery.feature_node.presentation.util.mediaStoreVersion
import com.dot.gallery.feature_node.presentation.util.printDebug
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

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
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = runCatching {
        if (!BuildConfig.ENABLE_INDEXING) return Result.success()
        val forceReload = inputData.getBoolean("forceReload", false)
        if (database.isMetadataUpToDate(appContext) && !forceReload) {
            printDebug("Metadata is up to date")
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
            media.orEmpty().filter { mediaItem -> oldMedia.none { it.id == mediaItem.id } }
        } else media
        printDebug("Found ${differentMedia?.size ?: 0} new or updated media items.")
        media?.let {
            printDebug("Deleting forgotten metadata...")
            database.getMetadataDao().deleteForgottenMetadata(it.fastMap { m -> m.id })
        }
        differentMedia?.let { diffMedia ->
            if (diffMedia.isEmpty()) {
                printDebug("No new media to update metadata for.")
                setProgress(workDataOf("progress" to 100))
                return Result.success()
            }
            printDebug("Updating metadata for ${diffMedia.size} items...")
            val throttler = ProgressThrottler()
            val total = diffMedia.size
            diffMedia.fastForEachIndexed { index, it ->
                if (!currentCoroutineContext().isActive || isStopped) return@fastForEachIndexed
                val pct =
                    if (total <= 1) 100 else (((index + 1).toFloat() / total.toFloat()) * 100f).roundToInt()
                throttler.emit(pct) { setProgress(workDataOf("progress" to it)) }
                appContext.retrieveExtraMediaMetadata(geocoder, it)?.let { metadata ->
                    database.getMetadataDao().addMetadata(metadata)
                }
            }
        }
        printDebug("Metadata update complete")
        database.getMetadataDao().setMediaVersion(MediaVersion(appContext.mediaStoreVersion))
        setProgress(workDataOf("progress" to 100))
        return Result.success()
    }.getOrElse { exception ->
        printDebug("MetadataCollectionWorker failed with exception: ${exception.message}")
        return Result.failure()
    }
}