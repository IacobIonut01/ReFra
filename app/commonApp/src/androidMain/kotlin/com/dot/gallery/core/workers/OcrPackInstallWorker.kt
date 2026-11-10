/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.workers

import android.content.Context
import com.dot.gallery.core.activeDataStore
import com.dot.gallery.core.logging.printError
import com.dot.gallery.core.logging.printInfo
import com.dot.gallery.core.logging.printWarn
import com.dot.gallery.core.smart.SmartScanScheduler
import com.dot.gallery.data.entity.SmartScanFeature
import com.dot.gallery.domain.settings.DataStoreSettingsStore
import com.dot.gallery.domain.settings.SettingsKeys
import kotlinx.coroutines.flow.first
import com.dot.gallery.core.logging.withLogScope
import com.dot.gallery.core.ocr.catalog.OcrCatalogModel
import com.dot.gallery.core.ocr.model.OcrFileMatcher
import com.dot.gallery.core.ocr.model.OcrInstallPolicy
import com.dot.gallery.core.ocr.model.OcrPackState
import com.dot.gallery.core.ocr.model.OcrPackStore
import com.dot.gallery.core.work.GalleryWorker
import com.dot.gallery.core.work.GalleryWorkerAdapter
import com.dot.gallery.core.work.WorkComponentHolder
import com.dot.gallery.core.work.WorkInput
import com.dot.gallery.core.work.workDataJson
import dev.brewkits.kmpworkmanager.annotations.Worker
import dev.brewkits.kmpworkmanager.background.domain.BackgroundTaskScheduler
import dev.brewkits.kmpworkmanager.background.domain.BackoffPolicy
import dev.brewkits.kmpworkmanager.background.domain.Constraints
import dev.brewkits.kmpworkmanager.background.domain.ExistingPolicy
import dev.brewkits.kmpworkmanager.background.domain.TaskPriority
import dev.brewkits.kmpworkmanager.background.domain.TaskRequest
import dev.brewkits.kmpworkmanager.background.domain.WorkerEnvironment
import dev.brewkits.kmpworkmanager.background.domain.WorkerResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.coroutines.coroutineContext

/**
 * Downloads the models a pack is missing (the shared detector the first time, then its recognizer)
 * from their pinned URLs. Each file is written to `.part` with HTTP Range resume, accepted only when
 * its size and SHA-256 match the catalog, and then moved into place atomically.
 */
class OcrPackInstallWorker @Inject constructor(
    private val store: OcrPackStore,
    private val appContext: Context,
    private val smartScanScheduler: SmartScanScheduler,
) : GalleryWorker {
    private val settings by lazy { DataStoreSettingsStore(appContext.activeDataStore) }

    override suspend fun doWork(input: WorkInput, env: WorkerEnvironment): WorkerResult = withLogScope(TAG) {
        val packId = input.getString(KEY_PACK) ?: return@withLogScope WorkerResult.Failure("missing pack")
        withContext(Dispatchers.IO) {
            try {
                val models = store.missingModels(packId)
                val total = models.sumOf { it.onnx.sizeBytes }
                var done = 0L
                for (model in models) {
                    download(model) { written -> store.setProgress(packId, OcrPackState.Downloading(done + written, total)) }
                    done += model.onnx.sizeBytes
                }
                store.setProgress(packId, null)
                // The pack is used from the moment it is on the device, not when it was requested.
                settings.put(SettingsKeys.ocrPacks, settings.observe(SettingsKeys.ocrPacks).first() + packId)
                printInfo(TAG, "Installed OCR pack $packId")
                if (settings.observe(SettingsKeys.ocrEnabled).first()) {
                    runCatching { smartScanScheduler.automatic(SmartScanFeature.TEXT.bit) }
                        .onFailure { printWarn(TAG, "Scheduling text indexing failed", mapOf("error" to it.toString())) }
                }
                WorkerResult.Success()
            } catch (e: CancellationException) {
                store.setProgress(packId, null)
                throw e
            } catch (e: Exception) {
                printError(TAG, "OCR pack $packId failed to install", e, ctx = mapOf("pack" to packId))
                store.setProgress(packId, OcrPackState.Failed(e.message ?: e.toString()))
                WorkerResult.Failure(e.message ?: "download failed")
            }
        }
    }

    private suspend fun download(model: OcrCatalogModel, onProgress: (Long) -> Unit) {
        val artifact = model.onnx
        val part = store.tempFile(model).apply { parentFile?.mkdirs() }
        val existing = part.length().takeIf { part.isFile && it < artifact.sizeBytes } ?: 0L
        val connection = (URL(artifact.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            val resumed = connection.responseCode == HttpURLConnection.HTTP_PARTIAL
            if (!resumed && connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode} for ${model.id}")
            }
            var written = if (resumed) existing else 0L
            connection.inputStream.use { input ->
                FileOutputStream(part, resumed).use { output ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        written += read
                        if (written > artifact.sizeBytes) throw IOException("${model.id} is larger than pinned")
                        output.write(buffer, 0, read)
                        onProgress(written)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        val sha = part.inputStream().use(OcrFileMatcher::sha256)
        if (part.length() != artifact.sizeBytes || sha != artifact.sha256) {
            part.delete()
            throw IOException("${model.id} did not verify")
        }
        Files.move(part.toPath(), store.destination(model).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    companion object {
        const val TAG = "ml.ocr"
        const val KEY_PACK = "pack"
        const val WORKER_NAME = "OcrPackInstallWorker"
        fun workName(packId: String) = "${WORKER_NAME}_$packId"
    }
}

@Worker(name = OcrPackInstallWorker.WORKER_NAME)
class OcrPackInstallWorkerAdapter : GalleryWorkerAdapter(OcrPackInstallWorker.WORKER_NAME) {
    override suspend fun run(input: WorkInput, env: WorkerEnvironment): WorkerResult =
        WorkComponentHolder.component.ocrPackInstallWorker.doWork(input, env)
}

/**
 * Schedules a pack download. Returns false, and schedules nothing, when [policy] forbids downloads
 * (offline builds): those only accept picked files.
 */
suspend fun BackgroundTaskScheduler.installOcrPack(packId: String, policy: OcrInstallPolicy): Boolean {
    if (!policy.downloadsAllowed) return false
    beginWith(
        TaskRequest(
            workerClassName = OcrPackInstallWorker.WORKER_NAME,
            inputJson = workDataJson(OcrPackInstallWorker.KEY_PACK to packId),
            constraints = Constraints(
                requiresNetwork = true,
                isHeavyTask = true,
                backoffPolicy = BackoffPolicy.EXPONENTIAL,
                backoffDelayMs = 30_000,
            ),
            priority = TaskPriority.HIGH,
            tags = setOf(OcrPackInstallWorker.TAG),
        )
    ).enqueue(id = OcrPackInstallWorker.workName(packId), policy = ExistingPolicy.REPLACE)
    return true
}

fun BackgroundTaskScheduler.cancelOcrPackInstall(packId: String) = cancel(OcrPackInstallWorker.workName(packId))
