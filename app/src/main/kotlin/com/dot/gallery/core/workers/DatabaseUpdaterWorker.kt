package com.dot.gallery.core.workers

import android.content.Context
import androidx.compose.ui.util.fastMap
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
import com.dot.gallery.feature_node.domain.model.MediaVersion
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.presentation.util.isMediaUpToDate
import com.dot.gallery.feature_node.presentation.util.mediaStoreVersion
import com.dot.gallery.feature_node.presentation.util.printDebug
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

fun WorkManager.updateDatabase() {
    val workPolicy = ExistingWorkPolicy.KEEP
    val constraints = Constraints.Builder()
        .setRequiresStorageNotLow(true)
        .build()

    val searchIndexerWork = OneTimeWorkRequestBuilder<SearchIndexerUpdaterWorker>()
        .setConstraints(constraints)
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .addTag("SearchIndexerUpdater")
        .build()

    val databaseUpdaterWork = OneTimeWorkRequestBuilder<DatabaseUpdaterWorker>()
        .setConstraints(constraints)
        .build()

    val metadataWork = OneTimeWorkRequestBuilder<MetadataCollectionWorker>()
        .setConstraints(constraints)
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .addTag("MetadataCollection")
        .build()

    enqueueUniqueWork("SearchIndexerUpdater", workPolicy, searchIndexerWork)
    enqueueUniqueWork("DatabaseUpdaterWorker", workPolicy, databaseUpdaterWork)
    enqueueUniqueWork("MetadataCollection", workPolicy, metadataWork)
}

@HiltWorker
class DatabaseUpdaterWorker @AssistedInject constructor(
    private val database: InternalDatabase,
    private val repository: MediaRepository,
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = runCatching {
        delay(5000)
    if (!currentCoroutineContext().isActive || isStopped) return Result.success()
        if (database.isMediaUpToDate(appContext)) {
            printDebug("Database is up to date")
            return Result.success()
        }
        withContext(Dispatchers.IO) {
            val mediaVersion = appContext.mediaStoreVersion
            val media =
                repository.getCompleteMedia().map { it.data ?: emptyList() }.firstOrNull()
            media?.let {
                printDebug("Database is not up to date. Updating to version $mediaVersion")
                database.getMediaDao().setMediaVersion(MediaVersion(mediaVersion))
                database.getMediaDao().updateMedia(it)
                database.getClassifierDao().deleteDeclassifiedImages(it.fastMap { m -> m.id })
            }
        }

        return@runCatching Result.success()
    }.getOrElse { exception ->
        printDebug("Error updating database: ${exception.message}")
        Result.failure()
    }
}

