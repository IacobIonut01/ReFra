package com.dot.gallery.core.workers

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.annotation.IntDef
import androidx.core.net.toUri
import com.dot.gallery.core.work.WorkComponentHolder
import com.dot.gallery.core.work.GalleryWorker
import com.dot.gallery.core.work.GalleryWorkerAdapter
import com.dot.gallery.core.work.WorkInput
import com.dot.gallery.core.work.WorkSignals
import com.dot.gallery.core.work.workData
import com.dot.gallery.core.work.workDataJson
import dev.brewkits.kmpworkmanager.annotations.Worker
import dev.brewkits.kmpworkmanager.background.domain.BackgroundTaskScheduler
import dev.brewkits.kmpworkmanager.background.domain.TaskTrigger
import dev.brewkits.kmpworkmanager.background.domain.WorkerEnvironment
import dev.brewkits.kmpworkmanager.background.domain.WorkerResult
import com.awxkee.jxlcoder.JxlCoder
import com.dot.gallery.cloud.core.CloudUri
import com.dot.gallery.cloud.core.capabilities.SyncCapableProvider
import com.dot.gallery.cloud.util.CloudMediaDownloader
import com.dot.gallery.core.Settings
import com.dot.gallery.core.decoder.format.ImageReencoder
import com.dot.gallery.core.decoder.format.ImageReencoderCodecs
import com.dot.gallery.core.decoder.format.SourceQualityProbe
import com.dot.gallery.core.util.ext.overrideImageFromEditedCopy
import com.dot.gallery.core.util.ext.overrideImageStreaming
import androidx.exifinterface.media.ExifInterface
import com.dot.gallery.core.util.ext.restoreMediaTimestamp
import com.dot.gallery.core.util.ext.selectedModifiedTimestamp
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.core.logging.withLogScope
import com.github.panpf.sketch.util.rotate
import com.radzivon.bartoshyk.avif.coder.HeifCoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.model.Album
import com.dot.gallery.core.util.or
import com.dot.gallery.core.presentation.util.update
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.domain.uri.path
import com.dot.gallery.domain.uri.scheme
import com.dot.gallery.domain.util.isCloud
import com.dot.gallery.core.logging.printError
import com.dot.gallery.core.logging.printWarn
import kotlinx.serialization.json.JsonArray
import me.tatarka.inject.annotations.Inject





/**
 * Enqueues a rotation. When the source format has no Android encoder (RAW/TIFF/PSD/…), the caller
 * must set [forceCopy] = true (after user confirmation) so the worker writes a new PNG copy instead
 * of failing to overwrite in place.
 */
suspend fun BackgroundTaskScheduler.rotateImage(
    media: Media,
    degrees: Int,
    forceCopy: Boolean = false
): String {
    val id = "rotate_${UUID.randomUUID()}"
    enqueue(
        id = id,
        trigger = TaskTrigger.OneTime(),
        workerClassName = RotateMediaWorker.WORKER_NAME,
        inputJson = rotateSingleInputJson(
            runId = id,
            degrees = degrees,
            item = RotateItemInput(
                mediaUri = media.getUri().toString(),
                mimeType = media.mimeType,
                label = media.label,
                forceCopy = forceCopy,
                sourceDateModified = media.timestamp,
            ),
        ),
    )
    return id
}

/** One item of a batch rotation, as serialized into [RotateMediaWorker.KEY_ITEMS]. */
internal data class RotateItemInput(
    val mediaUri: String,
    val mimeType: String?,
    val label: String?,
    val forceCopy: Boolean,
    val sourceDateModified: Long,
) {
    fun toJson() = workData(
        RotateMediaWorker.KEY_MEDIA_URI to mediaUri,
        RotateMediaWorker.KEY_MIME_TYPE to mimeType,
        RotateMediaWorker.KEY_LABEL to label,
        RotateMediaWorker.KEY_FORCE_COPY to forceCopy,
        RotateMediaWorker.KEY_SOURCE_DATE_MODIFIED to sourceDateModified,
    )

    companion object {
        fun from(input: WorkInput): RotateItemInput? = input.getString(RotateMediaWorker.KEY_MEDIA_URI)?.let {
            RotateItemInput(
                mediaUri = it,
                mimeType = input.getString(RotateMediaWorker.KEY_MIME_TYPE),
                label = input.getString(RotateMediaWorker.KEY_LABEL),
                forceCopy = input.getBoolean(RotateMediaWorker.KEY_FORCE_COPY, false),
                sourceDateModified = input.getLong(RotateMediaWorker.KEY_SOURCE_DATE_MODIFIED, 0L),
            )
        }
    }
}

internal fun rotateBatchInputJson(runId: String, degrees: Int, items: List<RotateItemInput>): String =
    workDataJson(
        RotateMediaWorker.KEY_RUN_ID to runId,
        RotateMediaWorker.KEY_ROTATION_DEGREES to degrees,
        RotateMediaWorker.KEY_ITEMS to JsonArray(items.map { it.toJson() }),
    )

/**
 * Enqueues ONE worker that rotates [items] sequentially (one full-resolution bitmap alive at a
 * time) and reports [RotateBatchProgress] under [runId] after every item. Each pair carries the
 * item's forced-copy decision for formats that can't be overwritten in place.
 */
suspend fun BackgroundTaskScheduler.rotateImages(
    runId: String,
    items: List<Pair<Media, Boolean>>,
    degrees: Int,
): String {
    enqueue(
        id = runId,
        trigger = TaskTrigger.OneTime(),
        workerClassName = RotateMediaWorker.WORKER_NAME,
        inputJson = rotateBatchInputJson(
            runId = runId,
            degrees = degrees,
            items = items.map { (media, forceCopy) ->
                RotateItemInput(
                    mediaUri = media.getUri().toString(),
                    mimeType = media.mimeType,
                    label = media.label,
                    forceCopy = forceCopy,
                    sourceDateModified = media.timestamp,
                )
            },
        ),
    )
    return runId
}

/** Batch progress payload: [done] items finished, [current] index in flight (-1 when idle). */
data class RotateBatchProgress(
    val status: Int,
    val total: Int,
    val done: Int,
    val current: Int,
    val failed: Set<Int>,
) {
    val isTerminal: Boolean
        get() = status == RotateMediaWorker.Status.COMPLETED || status == RotateMediaWorker.Status.FAILED

    fun toJson() = workData(
        RotateMediaWorker.KEY_STATUS to status,
        RotateMediaWorker.KEY_TOTAL to total,
        RotateMediaWorker.KEY_DONE to done,
        RotateMediaWorker.KEY_CURRENT to current,
        RotateMediaWorker.KEY_FAILED to failed.sorted().toIntArray(),
    )

    companion object {
        fun from(progress: WorkInput): RotateBatchProgress? {
            val total = progress.getIntOrNull(RotateMediaWorker.KEY_TOTAL) ?: return null
            return RotateBatchProgress(
                status = progress.getInt(RotateMediaWorker.KEY_STATUS, RotateMediaWorker.Status.STARTED),
                total = total,
                done = progress.getInt(RotateMediaWorker.KEY_DONE, 0),
                current = progress.getInt(RotateMediaWorker.KEY_CURRENT, -1),
                failed = progress.getIntArray(RotateMediaWorker.KEY_FAILED)?.toSet().orEmpty(),
            )
        }
    }
}

/**
 * Per-run record of batch progress, persisted so a re-run after process death or a stopped job
 * neither turns finished photos a second time nor crash-loops on the item that killed the
 * process (e.g. a decode too large for the device): that item is reported failed and skipped.
 */
internal class RotateBatchCheckpoint(dir: File, runId: String) {
    private val file = File(dir, runId.filter { it.isLetterOrDigit() || it == '_' || it == '-' } + ".idx")

    data class State(val completed: Set<Int>, val interrupted: Set<Int>)

    fun read(): State = runCatching {
        if (!file.exists()) return@runCatching State(emptySet(), emptySet())
        val lines = file.readLines().map { it.trim() }
        val completed = lines.mapNotNullTo(mutableSetOf()) { it.toIntOrNull() }
        val attempted = lines.mapNotNullTo(mutableSetOf()) { line ->
            if (line.startsWith(ATTEMPT)) line.removePrefix(ATTEMPT).toIntOrNull() else null
        }
        State(completed, attempted - completed)
    }.getOrElse { error ->
        printWarn("worker.rotate", "Batch checkpoint unreadable", ctx = mapOf("error" to "${error.message}"))
        State(emptySet(), emptySet())
    }

    fun markAttempt(index: Int) = append("$ATTEMPT$index")

    fun markCompleted(index: Int) = append("$index")

    fun clear() {
        if (file.exists() && !file.delete()) {
            printWarn("worker.rotate", "Batch checkpoint not deleted")
        }
    }

    private fun append(line: String) {
        runCatching {
            file.parentFile?.mkdirs()
            file.appendText("$line\n")
        }.onFailure { error ->
            printWarn("worker.rotate", "Batch checkpoint not written", ctx = mapOf("error" to "${error.message}"))
        }
    }

    private companion object {
        const val ATTEMPT = "a"
    }
}

/** Single-item input; [runId] keys its progress so the viewer can follow this exact run. */
internal fun rotateSingleInputJson(runId: String, degrees: Int, item: RotateItemInput): String =
    workDataJson(
        RotateMediaWorker.KEY_RUN_ID to runId,
        RotateMediaWorker.KEY_ROTATION_DEGREES to degrees,
        RotateMediaWorker.KEY_MEDIA_URI to item.mediaUri,
        RotateMediaWorker.KEY_MIME_TYPE to item.mimeType,
        RotateMediaWorker.KEY_LABEL to item.label,
        RotateMediaWorker.KEY_FORCE_COPY to item.forceCopy,
        RotateMediaWorker.KEY_SOURCE_DATE_MODIFIED to item.sourceDateModified,
    )

internal fun isJpegMime(mime: String): Boolean =
    mime.equals("image/jpeg", ignoreCase = true) || mime.equals("image/jpg", ignoreCase = true) ||
        mime.equals("image/pjpeg", ignoreCase = true)

/**
 * Turns [file]'s EXIF orientation by [degrees] clockwise, composing with any orientation (or
 * flip) already set. Returns false when the file is not a JPEG ExifInterface can rewrite.
 */
internal fun rotateExifOrientation(file: File, degrees: Int): Boolean = runCatching {
    val exif = ExifInterface(file.absolutePath)
    // rotate() leaves an absent (UNDEFINED) orientation untouched, so start from upright.
    if (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED) ==
        ExifInterface.ORIENTATION_UNDEFINED
    ) {
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
    }
    exif.rotate(degrees)
    exif.saveAttributes()
    true
}.getOrElse { error ->
    printWarn("worker.rotate", "EXIF orientation not saved", ctx = mapOf("error" to "${error.message}"))
    false
}

internal data class CloudRotationTarget(
    val cloudUri: CloudUri,
    val provider: SyncCapableProvider,
)

/** Resolves the exact account encoded in a cloud URI. Rotation never uses the type fallback. */
internal fun resolveCloudRotationTarget(
    registry: ProviderRegistry,
    uriString: String,
): Result<CloudRotationTarget> = runCatching {
    val cloudUri = CloudUri.parse(uriString)
        ?: throw IllegalArgumentException("Invalid cloud media Uri")
    if (cloudUri.configId <= 0L) {
        throw IllegalArgumentException("Cloud media Uri is missing its account config")
    }
    val accountProvider = registry.getByConfigId(cloudUri.configId)
        ?: throw IllegalStateException("Cloud account ${cloudUri.configId} is unavailable")
    if (accountProvider.providerType != cloudUri.providerType) {
        throw IllegalStateException("Cloud account provider does not match the media Uri")
    }
    val syncProvider = accountProvider as? SyncCapableProvider
        ?: throw IllegalStateException("Cloud account does not support upload")
    CloudRotationTarget(cloudUri, syncProvider)
}

class RotateMediaWorker @Inject constructor(
    private val appContext: Context,
    private val registry: ProviderRegistry
) : GalleryWorker {

    private val cr: ContentResolver = appContext.contentResolver

    override suspend fun doWork(input: WorkInput, env: WorkerEnvironment): WorkerResult = withLogScope("worker.rotate") { withContext(Dispatchers.IO) {
        // Progress is keyed by the enqueue id so concurrent runs never overwrite each other.
        val runId = input.getString(KEY_RUN_ID) ?: PROGRESS_TAG
        val degrees = (input.getInt(KEY_ROTATION_DEGREES, 0) + 360) % 360
        if (degrees % 90 != 0) return@withContext failure(runId, "Rotation must be multiple of 90")

        val batch = input.getObjectArray(KEY_ITEMS)
        if (batch != null) {
            return@withContext rotateBatch(runId, batch.mapNotNull(RotateItemInput::from), degrees)
        }
        val item = RotateItemInput.from(input)
            ?: return@withContext failure(runId, "Missing media Uri")
        if (degrees == 0) return@withContext success("No rotation requested")
        val updateModifiedDate = Settings.Album.updateModifiedDate(appContext).firstOrNull() ?: false
        when (val outcome = rotateOne(item, degrees, updateModifiedDate) { status, msg -> update(runId, status, msg) }) {
            is ItemOutcome.Rotated -> {
                update(runId, Status.COMPLETED, "Done")
                success("Rotation applied", resultUri = outcome.resultUri)
            }
            is ItemOutcome.Failed -> failure(runId, outcome.message)
        }
    } }

    /**
     * Rotates [items] one after another so only one full-resolution bitmap is alive at a time.
     * A failing item is recorded and skipped; cancellation is honoured between items.
     */
    private suspend fun rotateBatch(runId: String, items: List<RotateItemInput>, degrees: Int): WorkerResult {
        val total = items.size
        val failed = linkedSetOf<Int>()
        val checkpoint = RotateBatchCheckpoint(File(appContext.noBackupFilesDir, "rotate_batch"), runId)
        val resumed = checkpoint.read()
        val updateModifiedDate = Settings.Album.updateModifiedDate(appContext).firstOrNull() ?: false
        items.forEachIndexed { index, item ->
            currentCoroutineContext().ensureActive()
            if (index in resumed.completed) return@forEachIndexed
            if (index in resumed.interrupted) {
                failed += index
                printWarn(
                    tag = "worker.rotate",
                    message = "Batch item skipped: the process died while rotating it",
                    ctx = mapOf("index" to "$index", "total" to "$total"),
                )
                return@forEachIndexed
            }
            reportBatch(runId, RotateBatchProgress(Status.ROTATING, total, index, index, failed.toSet()))
            checkpoint.markAttempt(index)
            val outcome = if (degrees == 0) ItemOutcome.Rotated(null)
            else rotateOne(item, degrees, updateModifiedDate) { _, _ -> }
            if (outcome is ItemOutcome.Rotated) checkpoint.markCompleted(index)
            if (outcome is ItemOutcome.Failed) {
                failed += index
                printWarn(
                    tag = "worker.rotate",
                    message = "Batch item rotation failed",
                    ctx = mapOf("index" to "$index", "total" to "$total", "reason" to outcome.message),
                )
            }
        }
        checkpoint.clear()
        val allFailed = total > 0 && failed.size == total
        reportBatch(
            runId,
            RotateBatchProgress(if (allFailed) Status.FAILED else Status.COMPLETED, total, total, -1, failed.toSet()),
        )
        val message = "Rotated ${total - failed.size}/$total"
        return if (allFailed) WorkerResult.Failure(message) else success(message)
    }

    private sealed interface ItemOutcome {
        data class Rotated(val resultUri: String?) : ItemOutcome
        data class Failed(val message: String) : ItemOutcome
    }

    private suspend fun rotateOne(
        item: RotateItemInput,
        degrees: Int,
        updateModifiedDate: Boolean,
        update: suspend (status: Int, message: String) -> Unit,
    ): ItemOutcome {
        val uriStr = item.mediaUri
        val sourceUri = uriStr.toUri()
        update(Status.STARTED, "Begin")
        val mime = item.mimeType
            ?: (cr.getType(sourceUri) ?: "image/jpeg")
        val modifiedTimestamp = selectedModifiedTimestamp(
            updateModifiedDate = updateModifiedDate,
            sourceDateModified = item.sourceDateModified,
            currentTimeSeconds = System.currentTimeMillis() / 1000L
        )

        val isCloud = sourceUri.scheme == CloudUri.SCHEME
        if (!isCloud && !item.forceCopy && isJpegMime(mime)) {
            // Lossless: turn the EXIF orientation instead of decoding and re-encoding. Instant at
            // any resolution, keeps every byte of image data and metadata (dates, GPS, motion
            // video), and composes with an orientation the camera already set.
            update(Status.SAVING, "Updating orientation")
            if (rotateJpegInPlace(sourceUri, degrees, modifiedTimestamp)) return ItemOutcome.Rotated(null)
            printWarn(
                tag = "worker.rotate",
                message = "Lossless JPEG rotation failed; re-encoding instead",
                ctx = mapOf("mime" to mime),
            )
        }
        val cloudTarget = if (isCloud) {
            resolveCloudRotationTarget(registry, uriStr).getOrElse { error ->
                return ItemOutcome.Failed(error.message ?: "Invalid cloud account")
            }
        } else null
        return try {
            update(Status.DECODING, "Decoding original")
            val original = if (isCloud) {
                decodeCloudFullResolution(sourceUri, mime)
            } else {
                decodeFullResolution(sourceUri, mime)
            } ?: return ItemOutcome.Failed("Decode failed")

            update(Status.ROTATING, "Applying rotation=$degrees")
            val rotated = original.rotate(degrees)
            if (rotated !== original) original.recycle()

            update(Status.SAVING, "Saving")
            val label = item.label ?: "rotated"
            val forceCopy = item.forceCopy
            // Preserve the source format (JXL→JXL, AVIF→AVIF, …). Non-encodable sources
            // (RAW/TIFF/PSD/…) get PNG, which only happens on the forced-copy path.
            val writeFormat = ImageReencoder.formatForMime(mime, label)
                ?: ImageReencoder.ImageWriteFormat.PNG
            val detectedQuality = if (writeFormat == ImageReencoder.ImageWriteFormat.JPEG && !isCloud) {
                runCatching { detectSourceQuality(sourceUri, mime) }.getOrNull()
            } else null
            val config = Settings.Misc.getReencodeConfig(appContext, detectedQuality)
            if (isCloud) {
                val localUri = saveRotatedAsNewLocalUri(rotated, writeFormat, config, label, null)
                rotated.recycle()
                if (localUri == null) return ItemOutcome.Failed("Save failed")

                // Upload back to the exact account carried by cfg. Resolution happened before
                // decode/save, so a missing or mismatched account cannot create an orphaned copy.
                update(Status.UPLOADING, "Uploading to cloud")
                val target = requireNotNull(cloudTarget)
                val tempMedia = UriMedia(
                    id = 0,
                    label = copyDisplayName(label, writeFormat),
                    uri = localUri,
                    path = localUri.toString(),
                    relativePath = "Pictures",
                    albumID = 0,
                    albumLabel = "",
                    timestamp = System.currentTimeMillis() / 1000,
                    expiryTimestamp = null,
                    takenTimestamp = null,
                    fullDate = "",
                    mimeType = writeFormat.mimeType,
                    favorite = 0,
                    trashed = 0,
                    size = 0,
                    duration = null,
                )
                // Keep the rotated copy beside the source file: on path-based stores the
                // remoteId IS the remote path, so its parent folder becomes the upload
                // target instead of the provider's default folder. Content-addressed
                // providers (Immich) have no '/' in their remoteId and ignore this.
                val remoteParent = target.cloudUri.remoteId
                    .takeIf { '/' in it }
                    ?.substringBeforeLast('/')
                val uploadResult = target.provider.uploadAsset(tempMedia, remoteParent)
                val uploaded = uploadResult.getOrElse { error ->
                    // Keep the generated local copy when upload is not confirmed.
                    return ItemOutcome.Failed("Upload failed: ${error.message}")
                }
                if (uploaded.providerType != target.cloudUri.providerType ||
                    uploaded.serverConfigId != target.cloudUri.configId
                ) {
                    // An ambiguous terminal result is not safe enough to delete the only local copy.
                    return ItemOutcome.Failed("Upload account confirmation mismatch")
                }
                runCatching { cr.delete(localUri, null, null) }
            } else if (forceCopy) {
                // Source can't be overwritten in place — write a new (PNG) copy instead.
                val newUri = saveRotatedAsNewLocalUri(
                    rotated,
                    writeFormat,
                    config,
                    label,
                    modifiedTimestamp
                )
                rotated.recycle()
                if (newUri == null) return ItemOutcome.Failed("Save failed")
                return ItemOutcome.Rotated(newUri.toString())
            } else {
                val saved = saveRotatedInPlace(
                    sourceUri = sourceUri,
                    rotated = rotated,
                    writeFormat = writeFormat,
                    config = config,
                    modifiedTimestamp = modifiedTimestamp
                )
                rotated.recycle()
                if (!saved) return ItemOutcome.Failed("Save failed")
            }

            ItemOutcome.Rotated(null)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (oom: OutOfMemoryError) {
            printError(
                tag = "RotateMediaWorker",
                message = "OOM while rotating",
                throwable = oom,
            )
            ItemOutcome.Failed("OOM while rotating")
        } catch (e: Exception) {
            printError(
                tag = "RotateMediaWorker",
                message = "Rotation failed",
                throwable = e,
            )
            ItemOutcome.Failed("Error: ${e.message}")
        }
    }

    private suspend fun rotateJpegInPlace(sourceUri: Uri, degrees: Int, modifiedTimestamp: Long?): Boolean {
        val stagingFile = File.createTempFile("rotate-exif-", ".jpg", appContext.cacheDir)
        val saved = cr.overrideImageFromEditedCopy(sourceUri, stagingFile) { staged ->
            rotateExifOrientation(staged, degrees)
        }
        if (saved && modifiedTimestamp != null) {
            appContext.restoreMediaTimestamp(sourceUri, "image/jpeg", modifiedTimestamp)
        }
        return saved
    }

    private suspend fun saveRotatedInPlace(
        sourceUri: Uri,
        rotated: Bitmap,
        writeFormat: ImageReencoder.ImageWriteFormat,
        config: ImageReencoder.ReencodeConfig,
        modifiedTimestamp: Long?
    ): Boolean = withContext(Dispatchers.IO) {
        val stagingFile = File.createTempFile("rotate-override-", ".tmp", appContext.cacheDir)
        val saved = cr.overrideImageStreaming(sourceUri, stagingFile) { fd ->
            ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.fromFd(fd)).use { output ->
                ImageReencoderCodecs.writeToStream(rotated, writeFormat, config, output)
                output.flush()
                true
            }
        }
        if (saved && modifiedTimestamp != null) {
            appContext.restoreMediaTimestamp(sourceUri, writeFormat.mimeType, modifiedTimestamp)
        }
        saved
    }

    /** Reads a header prefix and estimates the JPEG source quality (or null). */
    private fun detectSourceQuality(uri: Uri, mime: String): Int? {
        val prefix = cr.openInputStream(uri)?.use { input ->
            val buf = ByteArray(256 * 1024)
            val read = input.read(buf)
            if (read <= 0) null else buf.copyOf(read)
        } ?: return null
        return SourceQualityProbe.detect(prefix, mime)
    }

    /** Builds a display name for a rotated copy carrying the write format's extension. */
    private fun copyDisplayName(label: String, writeFormat: ImageReencoder.ImageWriteFormat): String {
        val base = label.substringBeforeLast('.', label)
        return "rotated_$base.${writeFormat.fileExtension}"
    }

    private fun decodeCloudFullResolution(cloudUri: Uri, mime: String): Bitmap? {
        val inputStream = openCloudInputStream(cloudUri) ?: return null
        return inputStream.use { input -> decodeFromStream(input, mime) }
    }

    private fun openCloudInputStream(cloudUri: Uri): InputStream? {
        return CloudMediaDownloader.downloadCloudMedia(cloudUri)
    }

    private suspend fun saveRotatedAsNewLocalUri(
        rotated: Bitmap,
        writeFormat: ImageReencoder.ImageWriteFormat,
        config: ImageReencoder.ReencodeConfig,
        label: String,
        modifiedTimestamp: Long?
    ): Uri? {
        var targetUri: Uri? = null
        return try {
            targetUri = cr.insert(
                MediaStore.Images.Media.getContentUri("external"),
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, copyDisplayName(label, writeFormat))
                    put(MediaStore.MediaColumns.MIME_TYPE, writeFormat.mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            ) ?: throw IllegalStateException("Failed to create rotated copy")

            cr.openOutputStream(targetUri)?.use { output ->
                ImageReencoderCodecs.writeToStream(rotated, writeFormat, config, output)
                output.flush()
            } ?: throw IllegalStateException("Failed to open rotated copy")

            cr.update(
                targetUri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
            modifiedTimestamp?.let {
                appContext.restoreMediaTimestamp(targetUri, writeFormat.mimeType, it)
            }
            targetUri
        } catch (e: Exception) {
            printError("worker.rotate", "failed to save rotated copy", e)
            targetUri?.let { runCatching { cr.delete(it, null, null) } }
            null
        }
    }

    private fun decodeFullResolution(uri: Uri, mime: String): Bitmap? {
        val lower = mime.lowercase()
        return cr.openInputStream(uri)?.use { input -> decodeFromStream(input, mime) }
    }

    private fun decodeFromStream(input: InputStream, mime: String): Bitmap? {
        val lower = mime.lowercase()
        return when {
            lower.contains("jxl") -> {
                val bytes = input.readBytes()
                val size = JxlCoder.getSize(bytes) ?: return null
                JxlCoder.decodeSampled(bytes, size.width, size.height)
            }

            lower.contains("heic") || lower.contains("heif") || lower.contains("avif") || lower.contains(
                "avis"
            ) -> {
                val bytes = input.readBytes()
                val coder = HeifCoder()
                val size = coder.getSize(bytes) ?: return null
                coder.decodeSampled(bytes, size.width, size.height)
            }

            else -> {
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inMutable = false
                }
                BitmapFactory.decodeStream(input, null, opts)
            }
        }
    }

    private fun update(runId: String, @Status status: Int, msg: String) {
        WorkSignals.reportProgress(runId, workData(KEY_STATUS to status, KEY_MESSAGE to msg))
    }

    private fun reportBatch(runId: String, progress: RotateBatchProgress) {
        WorkSignals.reportProgress(runId, progress.toJson())
    }

    private fun success(msg: String, resultUri: String? = null): WorkerResult =
        WorkerResult.Success(
            message = msg,
            data = workData(
                KEY_STATUS to Status.COMPLETED,
                KEY_MESSAGE to msg,
                KEY_RESULT_URI to resultUri,
            )
        )

    private fun failure(runId: String, msg: String): WorkerResult {
        // Failure can't carry data — publish it so observers reading
        // KEY_STATUS/KEY_MESSAGE still get the terminal payload.
        update(runId, Status.FAILED, msg)
        WorkSignals.recordOutput(
            WORKER_NAME,
            workData(KEY_STATUS to Status.FAILED, KEY_MESSAGE to msg)
        )
        return WorkerResult.Failure(msg)
    }

    companion object {
        const val WORKER_NAME = "RotateMediaWorker"
        const val PROGRESS_TAG = WORKER_NAME
        const val KEY_MEDIA_URI = "media_uri"
        const val KEY_ROTATION_DEGREES = "rotation_degrees"
        const val KEY_MIME_TYPE = "mime_type"
        const val KEY_LABEL = "label"
        const val KEY_FORCE_COPY = "force_copy"
        const val KEY_SOURCE_DATE_MODIFIED = "source_date_modified"
        const val KEY_RUN_ID = "run_id"
        const val KEY_ITEMS = "items"

        const val KEY_STATUS = "status"
        const val KEY_MESSAGE = "message"
        const val KEY_RESULT_URI = "result_uri"
        const val KEY_TOTAL = "total"
        const val KEY_DONE = "done"
        const val KEY_CURRENT = "current"
        const val KEY_FAILED = "failed"
    }

    @IntDef(
        Status.STARTED,
        Status.DECODING,
        Status.ROTATING,
        Status.SAVING,
        Status.UPLOADING,
        Status.COMPLETED,
        Status.FAILED
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class Status {
        companion object {
            const val STARTED = 0
            const val DECODING = 1
            const val ROTATING = 2
            const val SAVING = 3
            const val UPLOADING = 6
            const val COMPLETED = 4
            const val FAILED = 5
        }
    }
}

@Worker(name = RotateMediaWorker.WORKER_NAME)
class RotateMediaWorkerAdapter : GalleryWorkerAdapter(RotateMediaWorker.WORKER_NAME) {
    override suspend fun run(input: WorkInput, env: WorkerEnvironment): WorkerResult =
        WorkComponentHolder.component.rotateMediaWorker.doWork(input, env)
}
