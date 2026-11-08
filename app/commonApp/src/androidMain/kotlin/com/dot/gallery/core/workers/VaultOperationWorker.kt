package com.dot.gallery.core.workers

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.dot.gallery.core.work.WorkComponentHolder
import com.dot.gallery.core.work.GalleryWorker
import com.dot.gallery.core.work.GalleryWorkerAdapter
import com.dot.gallery.core.work.WorkInput
import com.dot.gallery.core.work.workData
import com.dot.gallery.core.work.workDataJson
import com.dot.gallery.core.work.WorkSignals
import com.dot.gallery.core.util.ProgressThrottler
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.data.repository.MediaRepository
import com.dot.gallery.domain.util.getUri
import dev.brewkits.kmpworkmanager.annotations.Worker
import dev.brewkits.kmpworkmanager.background.domain.BackgroundTaskScheduler
import dev.brewkits.kmpworkmanager.background.domain.Constraints
import dev.brewkits.kmpworkmanager.background.domain.ExistingPolicy
import dev.brewkits.kmpworkmanager.background.domain.TaskTrigger
import dev.brewkits.kmpworkmanager.background.domain.WorkerEnvironment
import dev.brewkits.kmpworkmanager.background.domain.WorkerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID
import com.dot.gallery.domain.util.migrate
import com.dot.gallery.core.util.or
import com.dot.gallery.core.metrics.MetricsCollector
import com.dot.gallery.core.presentation.util.add
import com.dot.gallery.core.logging.withLogScope
import me.tatarka.inject.annotations.Inject




/**
 * Unified worker for vault related operations (encrypt/add to vault, restore/decrypt, hide/delete encrypted).
 * Also re-used for simple hide from main timeline by moving into a vault or performing repository mutation.
 *
 * Runs as a kmpworkmanager heavy task (foreground service with the library's generic
 * notification) — scheduled with `isHeavyTask = true`.
 */
class VaultOperationWorker @Inject constructor(
    private val appContext: Context,
    private val repository: MediaRepository,
    private val metricsCollector: MetricsCollector,
) : GalleryWorker {

    override suspend fun doWork(input: WorkInput, env: WorkerEnvironment): WorkerResult =
        withLogScope("worker.vault-op") {
            withContext(Dispatchers.IO) {
                val op = input.getString(KEY_OPERATION) ?: return@withContext WorkerResult.Failure("missing operation")
                val mediaJson = input.getString(KEY_MEDIA_URIS) ?: return@withContext WorkerResult.Failure("missing media uris")
                val inputUris = Json.decodeFromString<List<String>>(mediaJson).map { it.toUri() }
                val requestDelete = input.getBoolean(KEY_DELETE_ORIGINALS, false)
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
                    runCatching {
                        metricsCollector.incSidecarRead() // reuse counter slot; ideally add dedicated skip metric later
                    }
                    com.dot.gallery.core.logging.printDebug("VaultOperationWorker skipped $skipped missing URIs")
                }
                if (mediaUris.isEmpty()) {
                    // Nothing to do; treat as success to avoid retry loops due to deleted items.
                    return@withContext WorkerResult.Success()
                }

                updateProgress(completed = 0, total = mediaUris.size, operation = op)

                val processed = mutableListOf<Uri>()
                var failedCount = 0
                var opFailed = false
                var hardFailure: String? = null
                when (op) {
                    OP_ENCRYPT, OP_HIDE -> {
                        val vaultJson =
                            input.getString(KEY_VAULT) ?: return@withContext WorkerResult.Failure("missing vault")
                        val vault = Json.decodeFromString<Vault>(vaultJson)
                        val mediaList =
                            repository.getMediaListByUris(mediaUris, reviewMode = false, onlyMatching = true).firstOrNull()?.data
                                ?: return@withContext WorkerResult.Failure("no media")
                        val total = mediaList.size.coerceAtLeast(1)
                        if (mediaList.isEmpty()) return@withContext WorkerResult.Success()
                        mediaList.forEachIndexed { index, media ->
                            if (!currentCoroutineContext().isActive || env.isCancelled()) return@withContext WorkerResult.Success()
                            // honor the per-item result — previously a false return still counted
                            // as encrypted, and with deleteOriginals the original could be removed while
                            // the vault copy never existed.
                            val ok = try {
                                repository.addMedia(vault, media)
                            } catch (e: Throwable) {
                                com.dot.gallery.core.logging.printError(
                                    "worker.vault-op", "encrypt failed for media ${media.id}", e
                                )
                                false
                            }
                            if (ok) processed += media.getUri() else failedCount++
                            updateProgress(completed = index + 1, total = total, operation = op)
                        }
                        opFailed = processed.isEmpty() && failedCount > 0
                    }

                    OP_DECRYPT -> {
                        val vaultJson =
                            input.getString(KEY_VAULT) ?: return@withContext WorkerResult.Failure("missing vault")
                        val vault = Json.decodeFromString<Vault>(vaultJson)

                        val allVaultMedia = repository.getEncryptedMedia(vault).firstOrNull()?.data
                            ?: return@withContext WorkerResult.Failure("no vault media")

                        val mediaList = allVaultMedia.filter { media ->
                            mediaUris.contains(media.uri)
                        }

                        val total = mediaList.size.coerceAtLeast(1)
                        if (mediaList.isEmpty()) return@withContext WorkerResult.Success()
                        mediaList.forEachIndexed { index, media ->
                            if (!currentCoroutineContext().isActive || env.isCancelled()) return@withContext WorkerResult.Success()
                            val ok = try {
                                repository.restoreMedia(vault, media)
                            } catch (e: Throwable) {
                                com.dot.gallery.core.logging.printError(
                                    "worker.vault-op", "restore failed for media ${media.id}", e
                                )
                                false
                            }
                            if (ok) processed += media.getUri() else failedCount++
                            updateProgress(completed = index + 1, total = total, operation = op)
                        }
                        opFailed = processed.isEmpty() && failedCount > 0
                    }

                    OP_MIGRATE -> {
                        repository.migrateVault()
                    }

                    else -> hardFailure = "unknown op: $op"
                }

                if (hardFailure != null) return@withContext WorkerResult.Failure(hardFailure!!)
                if (opFailed) return@withContext WorkerResult.Failure("all items failed")

                if (requestDelete && processed.isNotEmpty()) {
                    // We cannot silently delete without user consent; expose URIs back so UI can launch a delete request.
                    // Provide processed URI list as JSON in output so caller can trigger permission flow exactly once.
                    return@withContext WorkerResult.Success(
                        data = workData(
                            KEY_LEFTOVER_URIS to Json.encodeToString(processed.map { it.toString() }),
                            KEY_SUCCEEDED_COUNT to processed.size,
                            KEY_FAILED_COUNT to failedCount
                        )
                    )
                }
                WorkerResult.Success(
                    data = workData(
                        KEY_SUCCEEDED_COUNT to processed.size,
                        KEY_FAILED_COUNT to failedCount
                    )
                )
            }
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
            WorkSignals.reportProgress(PROGRESS_WORK_NAME, workData(KEY_PROGRESS to it))
        }
    }

    companion object {
        private const val PROGRESS_MAX = 100f
        const val WORKER_NAME = "VaultOperationWorker"
        const val PROGRESS_WORK_NAME = WORKER_NAME
        const val KEY_OPERATION = "operation"
        const val KEY_VAULT = "vault"
        const val KEY_MEDIA_URIS = "mediaUris"
        const val KEY_PROGRESS = "progress"
        const val KEY_DELETE_ORIGINALS = "deleteOriginals"
        const val KEY_LEFTOVER_URIS = "leftoverUris"
        const val KEY_SUCCEEDED_COUNT = "succeededCount"
        const val KEY_FAILED_COUNT = "failedCount"
        const val OP_ENCRYPT = "encrypt"
        const val OP_DECRYPT = "decrypt"
        const val OP_HIDE = "hide"
        const val OP_MIGRATE = "migrate"
    }
}

@Worker(name = VaultOperationWorker.WORKER_NAME)
class VaultOperationWorkerAdapter : GalleryWorkerAdapter(VaultOperationWorker.WORKER_NAME) {
    override suspend fun run(input: WorkInput, env: WorkerEnvironment): WorkerResult =
        WorkComponentHolder.component.vaultOperationWorker.doWork(input, env)
}

// Enqueue helpers

/**
 * Splits [items] into chunks whose joined string form stays under [maxChars] — the
 * worker input payload shares WorkManager's ~10 KB serialization cap, so a bulk
 * vault op over a few hundred URIs must arrive as several tasks or the enqueue
 * itself throws and crashes the caller before a single item is processed.
 */
internal fun chunkStringsForWorkInput(items: List<String>, maxChars: Int = 8 * 1024): List<List<String>> {
    val chunks = mutableListOf<List<String>>()
    var current = mutableListOf<String>()
    var currentSize = 2 // surrounding "[]"
    for (item in items) {
        val itemSize = item.length + 3 // quotes + separator
        if (current.isNotEmpty() && currentSize + itemSize > maxChars) {
            chunks += current
            current = mutableListOf()
            currentSize = 2
        }
        current += item
        currentSize += itemSize
    }
    if (current.isNotEmpty()) chunks += current
    return chunks
}

private fun vaultOpPairs(
    operation: String,
    mediaChunk: List<Uri>,
    vault: Vault?,
    deleteOriginals: Boolean
): Array<Pair<String, Any>> {
    val pairs = mutableListOf<Pair<String, Any>>(
        VaultOperationWorker.KEY_OPERATION to operation,
        VaultOperationWorker.KEY_MEDIA_URIS to Json.encodeToString(mediaChunk.map { it.toString() })
    )
    if (vault != null) {
        pairs += VaultOperationWorker.KEY_VAULT to Json.encodeToString(vault)
    }
    if (deleteOriginals) {
        pairs += VaultOperationWorker.KEY_DELETE_ORIGINALS to true
    }
    return pairs.toTypedArray()
}

suspend fun BackgroundTaskScheduler.enqueueVaultOperation(
    operation: String,
    media: List<Uri>,
    vault: Vault?,
    uniqueKey: String = UUID.randomUUID().toString(),
    deleteOriginals: Boolean = false
) {
    val chunks = chunkStringsForWorkInput(media.map { it.toString() })
    chunks.forEachIndexed { index, chunk ->
        enqueue(
            id = "VaultOp_${uniqueKey}_$index",
            trigger = TaskTrigger.OneTime(),
            workerClassName = VaultOperationWorker.WORKER_NAME,
            constraints = Constraints(isHeavyTask = true),
            inputJson = workDataJson(*vaultOpPairs(operation, chunk.map { it.toUri() }, vault, deleteOriginals)),
            policy = ExistingPolicy.KEEP,
            tags = setOf("VaultOp"),
        )
    }
}

// Overload returning the task ids so callers can observe completion before destructive actions (e.g., deletion).
suspend fun BackgroundTaskScheduler.enqueueVaultOperationWithId(
    operation: String,
    media: List<Uri>,
    vault: Vault?,
    uniqueKey: String = UUID.randomUUID().toString(),
    deleteOriginals: Boolean = false
): List<String> {
    val chunks = chunkStringsForWorkInput(media.map { it.toString() })
    return chunks.mapIndexed { index, chunk ->
        val id = "VaultOp_${uniqueKey}_$index"
        enqueue(
            id = id,
            trigger = TaskTrigger.OneTime(),
            workerClassName = VaultOperationWorker.WORKER_NAME,
            constraints = Constraints(isHeavyTask = true),
            inputJson = workDataJson(*vaultOpPairs(operation, chunk.map { it.toUri() }, vault, deleteOriginals)),
            policy = ExistingPolicy.REPLACE,
            tags = setOf("VaultOp"),
        )
        id
    }
}
