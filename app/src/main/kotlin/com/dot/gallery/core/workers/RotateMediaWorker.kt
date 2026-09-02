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
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.awxkee.jxlcoder.JxlCoder
import com.dot.gallery.cloud.core.CloudUri
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.cloud.core.capabilities.SyncCapableProvider
import com.dot.gallery.cloud.util.CloudMediaDownloader
import com.dot.gallery.core.Settings
import com.dot.gallery.core.decoder.format.ImageReencoder
import com.dot.gallery.core.decoder.format.SourceQualityProbe
import com.dot.gallery.core.util.ext.overrideImageStreaming
import com.dot.gallery.core.util.ext.restoreMediaTimestamp
import com.dot.gallery.core.util.ext.selectedModifiedTimestamp
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.getUri
import com.github.panpf.sketch.util.rotate
import com.radzivon.bartoshyk.avif.coder.HeifCoder
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID

/**
 * Enqueues a rotation. When the source format has no Android encoder (RAW/TIFF/PSD/…), the caller
 * must set [forceCopy] = true (after user confirmation) so the worker writes a new PNG copy instead
 * of failing to overwrite in place.
 */
fun WorkManager.rotateImage(
    media: Media,
    degrees: Int,
    forceCopy: Boolean = false
): UUID {
    val work = OneTimeWorkRequestBuilder<RotateMediaWorker>()
        .setInputData(
            workDataOf(
                RotateMediaWorker.KEY_MEDIA_URI to media.getUri().toString(),
                RotateMediaWorker.KEY_ROTATION_DEGREES to degrees,
                RotateMediaWorker.KEY_MIME_TYPE to media.mimeType,
                RotateMediaWorker.KEY_LABEL to media.label,
                RotateMediaWorker.KEY_FORCE_COPY to forceCopy,
                RotateMediaWorker.KEY_SOURCE_DATE_MODIFIED to media.timestamp,
            )
        )
        .build()
    enqueue(work)
    return work.id
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

@HiltWorker
class RotateMediaWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val registry: ProviderRegistry
) : CoroutineWorker(appContext, params) {

    private val cr: ContentResolver = appContext.contentResolver

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uriStr = inputData.getString(KEY_MEDIA_URI)
            ?: return@withContext failure("Missing media Uri")
        val sourceUri = uriStr.toUri()
        val degrees = (inputData.getInt(KEY_ROTATION_DEGREES, 0) + 360) % 360
        if (degrees % 90 != 0) return@withContext failure("Rotation must be multiple of 90")
        if (degrees == 0) return@withContext success("No rotation requested")

        update(Status.STARTED, "Begin")
        val mime = inputData.getString(KEY_MIME_TYPE)
            ?: (cr.getType(sourceUri) ?: "image/jpeg")
        val updateModifiedDate = Settings.Album.updateModifiedDate(appContext).firstOrNull() ?: false
        val modifiedTimestamp = selectedModifiedTimestamp(
            updateModifiedDate = updateModifiedDate,
            sourceDateModified = inputData.getLong(KEY_SOURCE_DATE_MODIFIED, 0L),
            currentTimeSeconds = System.currentTimeMillis() / 1000L
        )

        val isCloud = sourceUri.scheme == CloudUri.SCHEME
        val cloudTarget = if (isCloud) {
            resolveCloudRotationTarget(registry, uriStr).getOrElse { error ->
                return@withContext failure(error.message ?: "Invalid cloud account")
            }
        } else null
        try {
            update(Status.DECODING, "Decoding original")
            val original = if (isCloud) {
                decodeCloudFullResolution(sourceUri, mime)
            } else {
                decodeFullResolution(sourceUri, mime)
            } ?: return@withContext failure("Decode failed")

            update(Status.ROTATING, "Applying rotation=$degrees")
            val rotated = original.rotate(degrees)
            if (rotated !== original) original.recycle()

            update(Status.SAVING, "Saving")
            val label = inputData.getString(KEY_LABEL) ?: "rotated"
            val forceCopy = inputData.getBoolean(KEY_FORCE_COPY, false)
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
                if (localUri == null) return@withContext failure("Save failed")

                // Upload back to the exact account carried by cfg. Resolution happened before
                // decode/save, so a missing or mismatched account cannot create an orphaned copy.
                update(Status.UPLOADING, "Uploading to cloud")
                val target = requireNotNull(cloudTarget)
                val tempMedia = Media.UriMedia(
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
                val uploadResult = target.provider.uploadAsset(tempMedia)
                val uploaded = uploadResult.getOrElse { error ->
                    // Keep the generated local copy when upload is not confirmed.
                    return@withContext failure("Upload failed: ${error.message}")
                }
                if (uploaded.providerType != target.cloudUri.providerType ||
                    uploaded.serverConfigId != target.cloudUri.configId
                ) {
                    // An ambiguous terminal result is not safe enough to delete the only local copy.
                    return@withContext failure("Upload account confirmation mismatch")
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
                if (newUri == null) return@withContext failure("Save failed")
                update(Status.COMPLETED, "Done")
                return@withContext success("Rotation applied", resultUri = newUri.toString())
            } else {
                val saved = saveRotatedInPlace(
                    sourceUri = sourceUri,
                    rotated = rotated,
                    writeFormat = writeFormat,
                    config = config,
                    modifiedTimestamp = modifiedTimestamp
                )
                rotated.recycle()
                if (!saved) return@withContext failure("Save failed")
            }

            update(Status.COMPLETED, "Done")
            success("Rotation applied")
        } catch (oom: OutOfMemoryError) {
            oom.printStackTrace()
            failure("OOM while rotating")
        } catch (e: Exception) {
            failure("Error: ${e.message}")
        }
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
                ImageReencoder.writeToStream(rotated, writeFormat, config, output)
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
                ImageReencoder.writeToStream(rotated, writeFormat, config, output)
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
            e.printStackTrace()
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

    private suspend fun update(@Status status: Int, msg: String) {
        setProgress(workDataOf(KEY_STATUS to status, KEY_MESSAGE to msg))
    }

    private fun success(msg: String, resultUri: String? = null): Result =
        Result.success(
            Data.Builder()
                .putInt(KEY_STATUS, Status.COMPLETED)
                .putString(KEY_MESSAGE, msg)
                .apply { if (resultUri != null) putString(KEY_RESULT_URI, resultUri) }
                .build()
        )

    private fun failure(msg: String): Result =
        Result.failure(
            Data.Builder()
                .putInt(KEY_STATUS, Status.FAILED)
                .putString(KEY_MESSAGE, msg)
                .build()
        )

    companion object {
        const val KEY_MEDIA_URI = "media_uri"
        const val KEY_ROTATION_DEGREES = "rotation_degrees"
        const val KEY_MIME_TYPE = "mime_type"
        const val KEY_LABEL = "label"
        const val KEY_FORCE_COPY = "force_copy"
        const val KEY_SOURCE_DATE_MODIFIED = "source_date_modified"

        const val KEY_STATUS = "status"
        const val KEY_MESSAGE = "message"
        const val KEY_RESULT_URI = "result_uri"
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
