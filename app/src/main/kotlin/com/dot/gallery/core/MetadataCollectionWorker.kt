package com.dot.gallery.core

import android.content.Context
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastMap
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
import com.dot.gallery.feature_node.domain.model.retrieveExtraMediaMetadata
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.presentation.util.printDebug
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

fun WorkManager.forceMetadataCollect() {
    val metadataWork = OneTimeWorkRequestBuilder<MetadataCollectionWorker>()
        .setInputData(workDataOf("forceReload" to true))
        .addTag("MetadataCollection_Force")
        .build()

    enqueueUniqueWork("MetadataCollection", ExistingWorkPolicy.APPEND, metadataWork)
}

@HiltWorker
class MetadataCollectionWorker @AssistedInject constructor(
    private val database: InternalDatabase,
    private val repository: MediaRepository,
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        printDebug("Updating metadata...")
        setProgress(workDataOf("progress" to 1))
        val forceReload = inputData.getBoolean("forceReload", false)
        if (forceReload) {
            printDebug("Force reloading metadata...")
        }
        val oldMedia = database.getMediaDao().getMedia()
        val media = repository.getMedia().map { it.data ?: emptyList() }.firstOrNull()
        val differentMedia = if (!forceReload) {
            media?.fastFilter { newMedia ->
                oldMedia.none { oldMediaItem ->
                    newMedia == oldMediaItem
                }
            }
        } else media
        differentMedia?.let { diffMedia ->
            printDebug("Updating metadata for ${diffMedia.size} items...")
            diffMedia.fastForEachIndexed { index, it ->
                setProgress(workDataOf("progress" to ((index + 1) * 100 / diffMedia.size.toFloat()).roundToInt()))
                appContext.retrieveExtraMediaMetadata(it)?.let { metadata ->
                    database.getMetadataDao().addMetadata(metadata)
                }
            }
        }
        media?.let {
            database.getMetadataDao().deleteForgottenMetadata(it.fastMap { m -> m.id })
        }
        printDebug("Metadata update complete")
        setProgress(workDataOf("progress" to 100))
        return Result.success()
    }
}