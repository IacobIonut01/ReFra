package com.dot.gallery.core.workers

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
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
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.getUri
import com.github.panpf.sketch.util.rotate
import com.radzivon.bartoshyk.avif.coder.HeifCoder
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

fun WorkManager.rotateImage(
    media: Media,
    degrees: Int
): UUID {
    val work = OneTimeWorkRequestBuilder<RotateMediaWorker>()
        .setInputData(
            workDataOf(
                RotateMediaWorker.KEY_MEDIA_URI to media.getUri().toString(),
                RotateMediaWorker.KEY_ROTATION_DEGREES to degrees,
                RotateMediaWorker.KEY_MIME_TYPE to media.mimeType,
            )
        )
        .build()
    enqueue(work)
    return work.id
}

@HiltWorker
class RotateMediaWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
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

        try {
            update(Status.DECODING, "Decoding original")
            val original = decodeFullResolution(sourceUri, mime)
                ?: return@withContext failure("Decode failed")

            update(Status.ROTATING, "Applying rotation=$degrees")
            val rotated = original.rotate(degrees)
            if (rotated !== original) original.recycle()

            update(Status.SAVING, "Saving")
            val format = chooseCompressFormat(mime)
            val saved = saveRotatedInPlace(
                sourceUri = sourceUri,
                rotated = rotated,
                format = format,
                quality = 95
            )
            rotated.recycle()
            if (!saved) return@withContext failure("Save failed")

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
        format: Bitmap.CompressFormat,
        quality: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Overwrite the original file via its Uri
            cr.openOutputStream(sourceUri, "wt")?.use { out ->
                if (!rotated.compress(format, quality, out)) {
                    throw RuntimeException("Compression failed")
                }
            } ?: throw RuntimeException("Stream failed")

            // Touch date_modified so MediaStore picks up the change
            val values = ContentValues().apply {
                put(
                    MediaStore.MediaColumns.DATE_MODIFIED,
                    System.currentTimeMillis() / 1000
                )
            }
            cr.update(sourceUri, values, null, null)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun decodeFullResolution(uri: Uri, mime: String): Bitmap? {
        val lower = mime.lowercase()
        return cr.openInputStream(uri)?.use { input ->
            when {
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
    }

    private fun chooseCompressFormat(mime: String): Bitmap.CompressFormat {
        val m = mime.lowercase()
        return when {
            m.contains("jpeg") || m.contains("jpg") -> Bitmap.CompressFormat.JPEG
            m.contains("png") -> Bitmap.CompressFormat.PNG
            m.contains("webp") -> {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }

            else -> Bitmap.CompressFormat.PNG
        }
    }

    private suspend fun update(@Status status: Int, msg: String) {
        setProgress(workDataOf(KEY_STATUS to status, KEY_MESSAGE to msg))
    }

    private fun success(msg: String): Result =
        Result.success(
            Data.Builder()
                .putInt(KEY_STATUS, Status.COMPLETED)
                .putString(KEY_MESSAGE, msg)
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

        const val KEY_STATUS = "status"
        const val KEY_MESSAGE = "message"
    }

    @IntDef(
        Status.STARTED,
        Status.DECODING,
        Status.ROTATING,
        Status.SAVING,
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
            const val COMPLETED = 4
            const val FAILED = 5
        }
    }
}
