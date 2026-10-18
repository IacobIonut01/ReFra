/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.workers

import android.content.Context
import android.os.StatFs
import com.dot.gallery.core.work.WorkComponentHolder
import com.dot.gallery.core.work.GalleryWorker
import com.dot.gallery.core.work.GalleryWorkerAdapter
import com.dot.gallery.core.work.WorkInput
import com.dot.gallery.core.work.WorkSignals
import com.dot.gallery.core.work.workData
import com.dot.gallery.core.work.workDataJson
import com.dot.gallery.core.ml.DownloadInfo
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.data.entity.SmartScanFeature
import com.dot.gallery.core.logging.withLogScope
import dev.brewkits.kmpworkmanager.annotations.Worker
import dev.brewkits.kmpworkmanager.background.domain.BackgroundTaskScheduler
import dev.brewkits.kmpworkmanager.background.domain.BackoffPolicy
import dev.brewkits.kmpworkmanager.background.domain.Constraints
import dev.brewkits.kmpworkmanager.background.domain.ExistingPolicy
import dev.brewkits.kmpworkmanager.background.domain.TaskPriority
import dev.brewkits.kmpworkmanager.background.domain.TaskRequest
import dev.brewkits.kmpworkmanager.background.domain.TaskTrigger
import dev.brewkits.kmpworkmanager.background.domain.WorkerEnvironment
import dev.brewkits.kmpworkmanager.background.domain.WorkerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import com.dot.gallery.core.util.Query
import com.dot.gallery.core.util.or
import com.dot.gallery.core.ml.ModelManager
import com.dot.gallery.core.smart.SmartScanScheduler
import com.dot.gallery.domain.uri.path
import com.dot.gallery.core.logging.printInfo
import com.dot.gallery.core.logging.printWarning
import me.tatarka.inject.annotations.Inject


class ModelDownloadWorker @Inject constructor(
    private val modelManager: ModelManager,
    private val smartScanScheduler: SmartScanScheduler,
    private val appContext: Context,
) : GalleryWorker {

    override suspend fun doWork(input: WorkInput, env: WorkerEnvironment): WorkerResult =
        withLogScope("worker.model-download") {
            withContext(Dispatchers.IO) {
                // Which feature's models this worker instance is responsible for.
                val group = input.getString(KEY_GROUP)
                    ?.let { runCatching { ModelGroup.valueOf(it) }.getOrNull() }
                    ?: ModelGroup.SEARCH_CLIP
                try {
                    reportProgress(0f, "Starting download...")
                    modelManager.updateDownloadProgress(group, 0f)

                    // Check available storage
                    val statFs = StatFs(appContext.filesDir.path)
                    val availableBytes = statFs.availableBytes
                    val requiredBytes = requiredBytesFor(group)
                    if (availableBytes < requiredBytes) {
                        val availableMb = availableBytes / (1024 * 1024)
                        val requiredMb = requiredBytes / (1024 * 1024)
                        val error =
                            "Not enough storage (need ${requiredMb} MB, ${availableMb} MB available)"
                        modelManager.onDownloadFailed(group, error)
                        reportProgress(0f, error)
                        return@withContext failure(group, error)
                    }

                    val modelsDir = modelManager.modelsDir
                    modelsDir.mkdirs()

                    val filesToDownload = group.files
                    val totalFiles = filesToDownload.size
                    var completedFiles = 0

                    // Query total download size for all files
                    var totalAllBytes = 0L
                    val fileSizes = mutableMapOf<String, Long>()
                    filesToDownload.forEach { fileName ->
                        val destFile = modelManager.getDestinationFile(fileName)
                        if (destFile.exists() && destFile.length() > 0) {
                            fileSizes[fileName] = destFile.length()
                        } else {
                            try {
                                val fileUrl = modelUrlFor(fileName)
                                val conn = URL(fileUrl).openConnection() as HttpURLConnection
                                conn.requestMethod = "HEAD"
                                conn.connectTimeout = 15_000
                                conn.connect()
                                val len = conn.contentLengthLong
                                fileSizes[fileName] = if (len > 0) len else 0L
                                conn.disconnect()
                            } catch (_: Exception) {
                                fileSizes[fileName] = 0L
                            }
                        }
                    }
                    totalAllBytes = fileSizes.values.sum()
                    var downloadedBytes = 0L

                    filesToDownload.forEach { fileName ->
                        if (!currentCoroutineContext().isActive || env.isCancelled()) {
                            cleanupPartialFiles(filesToDownload)
                            return@withContext WorkerResult.Failure("cancelled")
                        }

                        val destFile = modelManager.getDestinationFile(fileName)
                        destFile.parentFile?.mkdirs()

                        // Skip files that already exist and match the expected checksum
                        if (modelManager.isModelFileValid(fileName)) {
                            downloadedBytes += destFile.length()
                            completedFiles++
                            val overallProgress = (completedFiles.toFloat() / totalFiles) * 100f
                            modelManager.updateDownloadProgress(group, overallProgress)
                            reportProgress(overallProgress, "Skipping $fileName (already exists)")
                            printInfo("ModelDownloadWorker: Skipping $fileName (already exists)")
                            return@forEach
                        }

                        val url = modelUrlFor(fileName)
                        val tempFile = modelManager.getTempFile(fileName)
                        tempFile.parentFile?.mkdirs()

                        try {
                            downloadFile(
                                group,
                                url,
                                tempFile,
                                fileName,
                                completedFiles,
                                totalFiles,
                                downloadedBytes,
                                totalAllBytes
                            )

                            // Atomic rename
                            if (!tempFile.renameTo(destFile)) {
                                tempFile.inputStream().use { input ->
                                    destFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                tempFile.delete()
                            }

                            downloadedBytes += destFile.length()
                            completedFiles++
                            val overallProgress = (completedFiles.toFloat() / totalFiles) * 100f
                            modelManager.updateDownloadProgress(group, overallProgress)
                            reportProgress(overallProgress, "Downloaded $fileName")
                            printInfo("ModelDownloadWorker: Downloaded $fileName")

                        } catch (e: Exception) {
                            tempFile.delete()
                            throw e
                        }
                    }

                    // Validate all files are present
                    modelManager.onDownloadComplete(group)
                    if (!modelManager.isReady(group)) {
                        return@withContext failure(group, "Downloaded model checksum validation failed")
                    }
                    when (group) {
                        // Only a completed download of the *active* engine can feed a
                        // scan — the inactive engine just sits on disk.
                        ModelGroup.SEARCH_CLIP, ModelGroup.SEARCH_EG2 ->
                            if (group == modelManager.activeSearchModel.group) {
                                smartScanScheduler.automatic(
                                    SmartScanFeature.EMBEDDINGS.bit or SmartScanFeature.CATEGORIES.bit
                                )
                            }

                        ModelGroup.FACE_DETECT, ModelGroup.FACE_RECOGNITION ->
                            smartScanScheduler.automatic(SmartScanFeature.PERSONS.bit)

                        ModelGroup.CUTOUT -> Unit
                    }
                    reportProgress(100f, "Complete")
                    printInfo("ModelDownloadWorker: All models downloaded successfully")
                    return@withContext WorkerResult.Success()

                } catch (e: IOException) {
                    // Transient network error — retry with backoff
                    val error = "Download failed (will retry): ${e.message}"
                    modelManager.onDownloadFailed(group, error)
                    reportProgress(0f, error)
                    printWarning("ModelDownloadWorker: $error")
                    return@withContext WorkerResult.Retry(reason = error)
                } catch (e: Exception) {
                    // Non-transient error — permanent failure
                    val error = "Download failed: ${e.message}"
                    modelManager.onDownloadFailed(group, error)
                    reportProgress(0f, error)
                    printWarning("ModelDownloadWorker: $error")
                    return@withContext failure(group, error)
                }
            }
        }

    private fun failure(group: ModelGroup, error: String): WorkerResult {
        // Failure carries no data in kmpworkmanager — publish the KEY_ERROR payload so
        // WorkSignals.observe readers still see the terminal error detail.
        WorkSignals.recordOutput(workName(group), workData(KEY_ERROR to error))
        return WorkerResult.Failure(error)
    }

    private fun reportProgress(progress: Float, status: String) {
        WorkSignals.reportProgress(
            PROGRESS_WORK_NAME,
            workData(KEY_PROGRESS to progress, KEY_STATUS to status)
        )
    }

    private fun downloadFile(
        group: ModelGroup,
        url: String,
        destFile: File,
        fileName: String,
        completedFiles: Int,
        totalFiles: Int,
        previousFilesBytes: Long,
        totalAllBytes: Long
    ) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 30_000
        connection.requestMethod = "GET"

        try {
            connection.connect()
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw RuntimeException("HTTP $responseCode for $url")
            }

            val contentLength = connection.contentLengthLong
            var bytesRead = 0L
            val startTime = System.currentTimeMillis()

            connection.inputStream.use { input ->
                destFile.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read

                        if (contentLength > 0) {
                            val fileProgress = bytesRead.toFloat() / contentLength.toFloat()
                            val overallProgress =
                                ((completedFiles + fileProgress) / totalFiles) * 100f
                            modelManager.updateDownloadProgress(group, overallProgress)

                            // Calculate speed
                            val elapsed = System.currentTimeMillis() - startTime
                            val speed = if (elapsed > 0) (bytesRead * 1000L) / elapsed else 0L
                            val totalDownloaded = previousFilesBytes + bytesRead
                            modelManager.updateDownloadInfo(
                                group,
                                DownloadInfo(
                                    speed = speed,
                                    downloadedBytes = totalDownloaded,
                                    totalBytes = totalAllBytes,
                                    currentFile = fileName
                                )
                            )

                            // Update progress periodically (~every 1MB)
                            if (bytesRead % (1024 * 1024) < 65536) {
                                reportProgress(
                                    overallProgress,
                                    "Downloading $fileName... ${(fileProgress * 100).toInt()}%"
                                )
                            }
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Resolve the canonical download URL for a model [fileName]. */
    private fun modelUrlFor(fileName: String): String = when {
        // EmbeddingGemma 2 backbone + encoders live under onnx/, tokenizer at repo root.
        fileName == "model_q4.onnx" || fileName == "model_q4.onnx_data" ||
            fileName == "vision_encoder_q4.onnx" || fileName == "vision_encoder_q4.onnx_data" ->
            "$EMBEDDING_GEMMA_BASE/onnx/$fileName"
        fileName == "tokenizer.json" || fileName == "tokenizer_config.json" ->
            "$EMBEDDING_GEMMA_BASE/$fileName"
        else -> legacyModelUrlFor(fileName)
    }

    private fun legacyModelUrlFor(fileName: String): String = when (fileName) {
        "mobile_sam_image_encoder.onnx" -> "https://huggingface.co/Acly/MobileSAM/resolve/main/mobile_sam_image_encoder.onnx"
        "sam_mask_decoder_single.onnx" -> "https://huggingface.co/Acly/MobileSAM/resolve/main/sam_mask_decoder_single.onnx"
        // UltraFace RFB-320 lightweight face detector (ONNX Model Zoo, ~1.2 MB).
        "version-RFB-320.onnx" -> "https://github.com/onnx/models/raw/main/validated/vision/body_analysis/ultraface/models/version-RFB-320.onnx"
        // ArcFace 112x112 -> 512-d embedding face-recognition model.
        "arcface.onnx" -> "https://huggingface.co/garavv/arcface-onnx/resolve/main/arc.onnx"
        else -> "${ModelManager.BASE_DOWNLOAD_URL}$fileName"
    }

    private fun cleanupPartialFiles(files: List<String>) {
        files.forEach { fileName ->
            modelManager.getTempFile(fileName).delete()
        }
    }

    companion object {
        const val KEY_PROGRESS = "progress"
        const val KEY_STATUS = "status"
        const val KEY_ERROR = "error"
        const val KEY_GROUP = "group"
        const val WORKER_NAME = "ModelDownloadWorker"
        const val WORK_NAME = WORKER_NAME
        const val PROGRESS_WORK_NAME = WORKER_NAME
        const val TAG = "ModelDownload"

        /** Unique work name per group so each feature's download runs and is tracked independently. */
        fun workName(group: ModelGroup) = "${WORK_NAME}_${group.name}"

        const val EMBEDDING_GEMMA_BASE =
            "https://huggingface.co/onnx-community/embeddinggemma-2-ONNX/resolve/main"

        /** ~150 MB for the small groups (models are ≤ ~140 MB but we need temp space). */
        const val MINIMUM_REQUIRED_BYTES = 150L * 1024 * 1024

        /**
         * EmbeddingGemma 2 is ~320 MB on disk — require headroom for the download plus
         * the in-flight temp file.
         */
        const val SEARCH_EG2_REQUIRED_BYTES = 700L * 1024 * 1024

        /** CLIP is ~155 MB on disk — temp-file headroom included. */
        const val SEARCH_CLIP_REQUIRED_BYTES = 350L * 1024 * 1024

        fun requiredBytesFor(group: ModelGroup): Long = when (group) {
            ModelGroup.SEARCH_EG2 -> SEARCH_EG2_REQUIRED_BYTES
            ModelGroup.SEARCH_CLIP -> SEARCH_CLIP_REQUIRED_BYTES
            else -> MINIMUM_REQUIRED_BYTES
        }
    }
}

@Worker(name = ModelDownloadWorker.WORKER_NAME)
class ModelDownloadWorkerAdapter : GalleryWorkerAdapter(ModelDownloadWorker.WORKER_NAME) {
    override suspend fun run(input: WorkInput, env: WorkerEnvironment): WorkerResult =
        WorkComponentHolder.component.modelDownloadWorker.doWork(input, env)
}

/**
 * Extension function to start a model download for a single feature [group].
 */
suspend fun BackgroundTaskScheduler.downloadModels(group: ModelGroup) {
    // REPLACE (not KEEP): a finished task with the same name lingers after a completed
    // download or a cancel. With KEEP that stale record blocks re-enqueue, so tapping
    // Download again after deleting the models did nothing (issue #1044). REPLACE cancels
    // any existing (finished or in-flight) task and always starts a fresh download.
    //
    // Single-step chain so the task can carry HIGH priority (expedited on Android) — a
    // standalone enqueue() has no priority knob. isHeavyTask runs it in the foreground
    // service so a long download survives the app being backgrounded.
    beginWith(
        TaskRequest(
            workerClassName = ModelDownloadWorker.WORKER_NAME,
            inputJson = workDataJson(ModelDownloadWorker.KEY_GROUP to group.name),
            constraints = Constraints(
                requiresNetwork = true,
                isHeavyTask = true,
                backoffPolicy = BackoffPolicy.EXPONENTIAL,
                backoffDelayMs = 30_000,
            ),
            priority = TaskPriority.HIGH,
            tags = setOf(ModelDownloadWorker.TAG),
        )
    ).enqueue(
        id = ModelDownloadWorker.workName(group),
        policy = ExistingPolicy.REPLACE,
    )
}

/**
 * Extension function to cancel a feature [group]'s model download.
 */
fun BackgroundTaskScheduler.cancelModelDownload(group: ModelGroup) {
    cancel(ModelDownloadWorker.workName(group))
}
