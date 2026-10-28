package com.dot.gallery.core.workers

import android.content.Context
import android.os.Build
import androidx.compose.ui.util.fastMap
import androidx.room3.withWriteTransaction
import com.dot.gallery.core.work.WorkComponentHolder
import com.dot.gallery.core.work.GalleryWorker
import com.dot.gallery.core.work.GalleryWorkerAdapter
import com.dot.gallery.core.work.WorkInput
import com.dot.gallery.data.InternalDatabase
import com.dot.gallery.domain.model.MediaVersion
import com.dot.gallery.data.repository.MediaRepository
import com.dot.gallery.app.presentation.util.isMediaUpToDate
import com.dot.gallery.core.logging.withLogScope
import dev.brewkits.kmpworkmanager.annotations.Worker
import dev.brewkits.kmpworkmanager.background.domain.BackgroundTaskScheduler
import dev.brewkits.kmpworkmanager.background.domain.Constraints
import dev.brewkits.kmpworkmanager.background.domain.ExistingPolicy
import dev.brewkits.kmpworkmanager.background.domain.TaskPriority
import dev.brewkits.kmpworkmanager.background.domain.TaskRequest
import dev.brewkits.kmpworkmanager.background.domain.TaskTrigger
import dev.brewkits.kmpworkmanager.background.domain.WorkerEnvironment
import dev.brewkits.kmpworkmanager.background.domain.WorkerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import com.dot.gallery.core.util.or
import com.dot.gallery.core.util.ext.updateMedia
import com.dot.gallery.core.presentation.util.mediaStoreVersion
import com.dot.gallery.core.logging.printDebug
import me.tatarka.inject.annotations.Inject



/**
 * Fire-and-forget database hygiene: re-indexes search metadata and reconciles the media
 * table with MediaStore. The indexer/metadata passes are enqueued as expedited chains on
 * API 31+ (chain steps carry TaskPriority; standalone enqueues never expedite under
 * kmpworkmanager) and fall back to plain enqueues on older versions.
 */
suspend fun BackgroundTaskScheduler.updateDatabase() {
    val policy = ExistingPolicy.KEEP

    // androidx requiresStorageNotLow=true ↔ simply not opting into ALLOW_LOW_STORAGE
    val storageConstraints = Constraints()

    suspend fun enqueueExpedited(worker: String, tag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            beginWith(
                TaskRequest(
                    workerClassName = worker,
                    constraints = storageConstraints,
                    priority = TaskPriority.HIGH,
                    tags = setOf(tag),
                )
            ).withId(tag, policy).enqueue()
        } else {
            enqueue(
                id = tag,
                trigger = TaskTrigger.OneTime(),
                workerClassName = worker,
                constraints = storageConstraints,
                policy = policy,
                tags = setOf(tag),
            )
        }
    }

    enqueueExpedited(SearchIndexerUpdaterWorker.WORKER_NAME, "SearchIndexerUpdater")
    enqueue(
        id = "DatabaseUpdaterWorker",
        trigger = TaskTrigger.OneTime(),
        workerClassName = DatabaseUpdaterWorker.WORKER_NAME,
        constraints = storageConstraints,
        policy = policy,
    )
    enqueueExpedited(MetadataCollectionWorker.WORKER_NAME, "MetadataCollection")
}

class DatabaseUpdaterWorker @Inject constructor(
    private val database: InternalDatabase,
    private val repository: MediaRepository,
    private val appContext: Context,
) : GalleryWorker {

    override suspend fun doWork(input: WorkInput, env: WorkerEnvironment): WorkerResult =
        withLogScope("worker.db-updater") {
            runCatching {
                delay(5000)
                if (!currentCoroutineContext().isActive || env.isCancelled()) return@withLogScope WorkerResult.Success()
                if (database.isMediaUpToDate(appContext)) {
                    printDebug("Database is up to date")
                    return@withLogScope WorkerResult.Success()
                }
                withContext(Dispatchers.IO) {
                    val mediaVersion = appContext.mediaStoreVersion
                    val media =
                        repository.getCompleteMedia().map { it.data ?: emptyList() }.firstOrNull()
                    media?.let {
                        printDebug("Database is not up to date. Updating to version $mediaVersion")
                        database.withWriteTransaction {
                            database.getMediaDao().updateMedia(it)
                            database.getClassifierDao().deleteDeclassifiedImages(it.fastMap { m -> m.id })
                            database.getMediaDao().setMediaVersion(MediaVersion(mediaVersion))
                        }
                        // Keep category memberships so trashed or temporarily unavailable media regain
                        // their assignments when restored; category queries filter against this mirror.
                    }
                }
                runCatching { com.dot.gallery.core.work.workScheduler().runDataHealthAfterSync() }
                    .onFailure { printDebug("Could not schedule data health: ${it.message}") }

                return@runCatching WorkerResult.Success()
            }.getOrElse { exception ->
                printDebug("Error updating database: ${exception.message}")
                WorkerResult.Failure("db update failed: ${exception.message}")
            }
        }

    companion object {
        const val WORKER_NAME = "DatabaseUpdaterWorker"
    }
}

@Worker(name = DatabaseUpdaterWorker.WORKER_NAME)
class DatabaseUpdaterWorkerAdapter :
    GalleryWorkerAdapter(DatabaseUpdaterWorker.WORKER_NAME) {
    override suspend fun run(input: WorkInput, env: WorkerEnvironment): WorkerResult =
        WorkComponentHolder.component.databaseUpdaterWorker.doWork(input, env)
}
