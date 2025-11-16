package com.dot.gallery.core.workers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dot.gallery.R
import com.dot.gallery.core.util.ProgressThrottler
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.domain.util.getUri
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Unified worker for vault related operations (encrypt/add to vault, restore/decrypt, hide/delete encrypted).
 * Also re-used for simple hide from main timeline by moving into a vault or performing repository mutation.
 */
@HiltWorker
class VaultOperationWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: MediaRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val op = inputData.getString(KEY_OPERATION) ?: return@withContext Result.failure()
        val mediaJson = inputData.getString(KEY_MEDIA_URIS) ?: return@withContext Result.failure()
        val inputUris = Json.decodeFromString<List<String>>(mediaJson).map { it.toUri() }
    val requestDelete = inputData.getBoolean(KEY_DELETE_ORIGINALS, false)
        val resolver = appContext.contentResolver
        var skipped = 0
        val mediaUris = inputUris.filter { uri ->
            try {
                // Lightweight existence probe: openAssetFileDescriptor with mode 'r'
                resolver.openAssetFileDescriptor(uri, "r")?.use { }
                true
            } catch (_: Exception) {
                skipped++
                false
            }
        }
        if (skipped > 0) {
            try {
                val ep = EntryPointAccessors.fromApplication(appContext, com.dot.gallery.core.metrics.MetricsCollectorEntryPoint::class.java)
                ep.metrics().incSidecarRead() // reuse counter slot; ideally add dedicated skip metric later
            } catch (_: Throwable) {}
            com.dot.gallery.feature_node.presentation.util.printDebug("VaultOperationWorker skipped $skipped missing URIs")
        }
        if (mediaUris.isEmpty()) {
            // Nothing to do; treat as success to avoid retry loops due to deleted items.
            return@withContext Result.success()
        }

        // Set initial foreground notification
        setForeground(createForegroundInfo(progress = 0f, operation = op))

        val processed = mutableListOf<Uri>()
        val result = when (op) {
            OP_ENCRYPT, OP_HIDE -> {
                val vaultJson =
                    inputData.getString(KEY_VAULT) ?: return@withContext Result.failure()
                val vault = Json.decodeFromString<Vault>(vaultJson)
                val mediaList =
                    repository.getMediaListByUris(mediaUris, reviewMode = false, onlyMatching = true).firstOrNull()?.data
                        ?: return@withContext Result.failure()
                val total = mediaList.size.coerceAtLeast(1)
                if (mediaList.isEmpty()) return@withContext Result.success()
                mediaList.forEachIndexed { index, media ->
                    if (!currentCoroutineContext().isActive || isStopped) return@withContext Result.success()
                    repository.addMedia(vault, media)
                    processed += media.getUri()
                    updateProgress(completed = index + 1, total = total, operation = op)
                }
                Result.success()
            }

            OP_DECRYPT -> {
                val vaultJson =
                    inputData.getString(KEY_VAULT) ?: return@withContext Result.failure()
                val vault = Json.decodeFromString<Vault>(vaultJson)
                
                val allVaultMedia = repository.getEncryptedMedia(vault).firstOrNull()?.data 
                    ?: return@withContext Result.failure()
                
                val mediaList = allVaultMedia.filter { media -> 
                    mediaUris.contains(media.uri)
                }
                
                val total = mediaList.size.coerceAtLeast(1)
                if (mediaList.isEmpty()) return@withContext Result.success()
                mediaList.forEachIndexed { index, media ->
                    if (!currentCoroutineContext().isActive || isStopped) return@withContext Result.success()
                    repository.restoreMedia(vault, media)
                    updateProgress(completed = index + 1, total = total, operation = op)
                }
                Result.success()
            }

            OP_MIGRATE -> {
                repository.migrateVault()
                Result.success()
            }

            else -> Result.failure()
        }

        if (result == Result.success() && requestDelete && processed.isNotEmpty()) {
            // We cannot silently delete without user consent; expose URIs back so UI can launch a delete request.
            // Provide processed URI list as JSON in output so caller can trigger permission flow exactly once.
            return@withContext Result.success(
                workDataOf(
                    KEY_LEFTOVER_URIS to Json.encodeToString(processed.map { it.toString() })
                )
            )
        }
        result
    }

    /**
     * Update progress as a bounded percentage (0f..100f). `completed` is the count of items
     * already processed (1-based when called after processing an item). This prevents values
     * exceeding 100 when the underlying list size differs from the original URI list length.
     */
    private val throttler = ProgressThrottler()
    private suspend fun updateProgress(completed: Int, total: Int, operation: String) {
        val safeTotal = total.coerceAtLeast(1)
        val raw = (completed.toFloat() / safeTotal.toFloat()) * PROGRESS_MAX
        val pct = raw.coerceIn(0f, PROGRESS_MAX)
        val pctInt = pct.toInt()
        throttler.emit(pctInt) {
            setProgress(workDataOf(KEY_PROGRESS to it))
            setForeground(createForegroundInfo(pct, operation))
        }
    }

    private fun createForegroundInfo(progress: Float, operation: String): ForegroundInfo {
        val channelId = ensureChannel()
        val title = when (operation) {
            OP_ENCRYPT, OP_HIDE -> appContext.getString(R.string.vault_encrypt_notification_title)
            OP_DECRYPT -> appContext.getString(R.string.vault_decrypt_notification_title)
            else -> appContext.getString(R.string.vault_encrypt_notification_title)
        }
        val text = appContext.getString(R.string.vault_operation_in_progress, progress.toInt())
        val notif: Notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_vault)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.toInt(), false)
            .setContentTitle(title)
            .setContentText(text)
            .build()

        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        return ForegroundInfo(NOTIFICATION_ID, notif, fgsType)
    }

    private fun ensureChannel(): String {
        val channelId = CHANNEL_ID
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    appContext.getString(R.string.vault_notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description =
                        appContext.getString(R.string.vault_notification_channel_description)
                }
            )
        }
        return channelId
    }

    companion object {
        private const val CHANNEL_ID = "vault_operations"
        private const val NOTIFICATION_ID = 77
        private const val PROGRESS_MAX = 100f
        const val KEY_OPERATION = "operation"
        const val KEY_VAULT = "vault"
        const val KEY_MEDIA_URIS = "mediaUris"
        const val KEY_PROGRESS = "progress"
        const val KEY_DELETE_ORIGINALS = "deleteOriginals"
        const val KEY_LEFTOVER_URIS = "leftoverUris"
        const val OP_ENCRYPT = "encrypt"
        const val OP_DECRYPT = "decrypt"
        const val OP_HIDE = "hide"
        const val OP_MIGRATE = "migrate"
    }
}

// Enqueue helpers
fun WorkManager.enqueueVaultOperation(
    operation: String,
    media: List<Uri>,
    vault: Vault?,
    uniqueKey: String = UUID.randomUUID().toString(),
    deleteOriginals: Boolean = false
) {
    val input = mutableMapOf(
        VaultOperationWorker.KEY_OPERATION to operation,
        VaultOperationWorker.KEY_MEDIA_URIS to Json.encodeToString(media.map { it.toString() })
    )
    if (vault != null) {
        input[VaultOperationWorker.KEY_VAULT] = Json.encodeToString(vault)
    }
    if (deleteOriginals) {
        input[VaultOperationWorker.KEY_DELETE_ORIGINALS] = true.toString()
    }
    val request = OneTimeWorkRequestBuilder<VaultOperationWorker>()
        .setInputData(workDataOf(*input.toList().toTypedArray()))
        .addTag("VaultOp")
        .build()
    enqueueUniqueWork("VaultOp_$uniqueKey", ExistingWorkPolicy.KEEP, request)
}

// Overload returning WorkRequest ID so callers can observe completion before destructive actions (e.g., deletion).
fun WorkManager.enqueueVaultOperationWithId(
    operation: String,
    media: List<Uri>,
    vault: Vault?,
    uniqueKey: String = UUID.randomUUID().toString(),
    deleteOriginals: Boolean = false
): UUID {
    val input = mutableMapOf(
        VaultOperationWorker.KEY_OPERATION to operation,
        VaultOperationWorker.KEY_MEDIA_URIS to Json.encodeToString(media.map { it.toString() })
    )
    if (vault != null) {
        input[VaultOperationWorker.KEY_VAULT] = Json.encodeToString(vault)
    }
    if (deleteOriginals) {
        input[VaultOperationWorker.KEY_DELETE_ORIGINALS] = true.toString()
    }
    val request = OneTimeWorkRequestBuilder<VaultOperationWorker>()
        .setInputData(workDataOf(*input.toList().toTypedArray()))
        .addTag("VaultOp")
        .build()
    enqueueUniqueWork("VaultOp_$uniqueKey", ExistingWorkPolicy.REPLACE, request)
    return request.id
}
