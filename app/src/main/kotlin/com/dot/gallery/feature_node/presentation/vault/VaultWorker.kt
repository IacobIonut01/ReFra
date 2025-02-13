package com.dot.gallery.feature_node.presentation.vault

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

fun WorkManager.scheduleMediaMigrationCheck() {
    val uniqueWork = OneTimeWorkRequestBuilder<VaultWorker>()
        .setConstraints(
            Constraints.Builder()
                .setRequiresStorageNotLow(true)
                .build()
        )
        .setInputData(
            workDataOf(
                "work" to "migrate",
            )
        )
        .addTag("VaultWorker_Migration")
        .build()

    enqueueUniqueWork("VaultWorker_Migration_${UUID.randomUUID()}", ExistingWorkPolicy.KEEP, uniqueWork)
}

fun WorkManager.scheduleEncryptingMedia(
    vault: Vault,
    media: List<Uri>,
) {
    val uniqueWork = OneTimeWorkRequestBuilder<VaultWorker>()
        .setConstraints(
            Constraints.Builder()
                .setRequiresStorageNotLow(true)
                .build()
        )
        .setInputData(
            workDataOf(
                "work" to "encrypt",
                "targetMedia" to Json.encodeToString(media.map { it.toString() }),
                "targetVault" to Json.encodeToString(vault)
            )
        )
        .addTag("VaultWorker")
        .build()

    enqueueUniqueWork("VaultWorker_${vault.uuid}", ExistingWorkPolicy.KEEP, uniqueWork)
}

@HiltWorker
class VaultWorker @AssistedInject constructor(
    private val repository: MediaRepository,
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        withContext(Dispatchers.IO) {
            val workType = inputData.getString("work") ?: return@withContext Result.failure()
            if (workType == "encrypt") {
                val targetMedia = inputData.getString("targetMedia")
                val targetVault =
                    inputData.getString("targetVault") ?: return@withContext Result.failure()
                if (targetMedia.isNullOrEmpty()) {
                    return@withContext Result.failure()
                } else {
                    val decodedTargetMedia =
                        Json.decodeFromString<List<String>>(targetMedia).map { it.toUri() }
                    val decodedTargetVault = Json.decodeFromString<Vault>(targetVault)
                    val mediaList =
                        repository.getMediaListByUris(decodedTargetMedia, false).firstOrNull()?.data
                    if (mediaList.isNullOrEmpty()) {
                        return@withContext Result.failure()
                    }
                    mediaList.forEachIndexed { index, media ->
                        repository.addMedia(vault = decodedTargetVault, media = media)
                        setProgress(workDataOf("progress" to (index / (mediaList.size - 1).toFloat()) * 100f))
                    }
                    setProgress(workDataOf("progress" to 100f))
                    return@withContext Result.success()
                }
            } else if (workType == "migrate") {
                repository.migrateVault()
                return@withContext Result.success()
            } else {
                return@withContext Result.failure()
            }
        }

        return Result.success()
    }
}

