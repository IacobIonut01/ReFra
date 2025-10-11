/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.util.ext

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.util.printWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

fun ContentResolver.querySteppedFlow(
    uri: Uri,
    projection: Array<String>? = null,
    queryArgs: Bundle? = Bundle(),
) = callbackFlow {
    // Each query will have its own cancellationSignal.
    // Before running any new query the old cancellationSignal must be cancelled
    // to ensure the currently running query gets interrupted so that we don't
    // send data across the channel if we know we received a newer set of data.
    var cancellationSignal = CancellationSignal()
    // ContentObserver.onChange can be called concurrently so make sure
    // access to the cancellationSignal is synchronized.
    val mutex = Mutex()
    val modifiedArgs = queryArgs?.deepCopy()?.apply {
        putString(ContentResolver.QUERY_ARG_SQL_LIMIT, "250")
    }

    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            launch(Dispatchers.IO) {
                mutex.withLock {
                    cancellationSignal.cancel()
                    cancellationSignal = CancellationSignal()
                }
                runCatching {
                    trySend(query(uri, projection, queryArgs, cancellationSignal))
                }
            }
        }
    }

    registerContentObserver(uri, true, observer)

    // The first set of values must always be generated and cannot (shouldn't) be cancelled.
    launch(Dispatchers.IO) {
        runCatching {
            trySend(query(uri, projection, modifiedArgs, null))
            trySend(query(uri, projection, queryArgs, null))
        }
    }

    awaitClose {
        // Stop receiving content changes.
        unregisterContentObserver(observer)
        // Cancel any possibly running query.
        cancellationSignal.cancel()
    }
}.conflate()

fun ContentResolver.queryFlow(
    uri: Uri,
    projection: Array<String>? = null,
    queryArgs: Bundle? = Bundle(),
) = callbackFlow {
    // Each query will have its own cancellationSignal.
    // Before running any new query the old cancellationSignal must be cancelled
    // to ensure the currently running query gets interrupted so that we don't
    // send data across the channel if we know we received a newer set of data.
    var cancellationSignal = CancellationSignal()
    // ContentObserver.onChange can be called concurrently so make sure
    // access to the cancellationSignal is synchronized.
    val mutex = Mutex()

    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            launch(Dispatchers.IO) {
                mutex.withLock {
                    cancellationSignal.cancel()
                    cancellationSignal = CancellationSignal()
                }
                runCatching {
                    trySend(query(uri, projection, queryArgs, cancellationSignal))
                }
            }
        }
    }

    registerContentObserver(uri, true, observer)

    // The first set of values must always be generated and cannot (shouldn't) be cancelled.
    launch(Dispatchers.IO) {
        runCatching {
            trySend(
                query(uri, projection, queryArgs, null)
            )
        }
    }

    awaitClose {
        // Stop receiving content changes.
        unregisterContentObserver(observer)
        // Cancel any possibly running query.
        cancellationSignal.cancel()
    }
}.conflate()

suspend fun ContentResolver.overrideImage(
    uri: Uri,
    bitmap: Bitmap,
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        update(uri, ContentValues(), null)
        openOutputStream(uri)?.use { out ->
            if (!bitmap.compress(format, 100, out)) throw IOException("Compression failed")
        } ?: throw IOException("Stream open failed")
        update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null
        ) > 0
    }.getOrElse {
        throw it
    }
}

suspend fun ContentResolver.restoreImage(
    data: ByteArray,
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
    mimeType: String = "image/png",
    relativePath: String = "${Environment.DIRECTORY_PICTURES}/Restored",
    displayName: String
): Uri? = saveBitmap(
    BitmapFactory.decodeByteArray(data, 0, data.size),
    format,
    mimeType,
    relativePath,
    displayName
)

suspend fun ContentResolver.saveImage(
    bitmap: Bitmap,
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
    mimeType: String = "image/png",
    relativePath: String = Environment.DIRECTORY_PICTURES,
    displayName: String
): Uri? = saveBitmap(
    bitmap,
    format,
    mimeType,
    relativePath,
    displayName
)

private suspend fun ContentResolver.saveBitmap(
    bitmap: Bitmap,
    format: Bitmap.CompressFormat,
    mimeType: String,
    relativePath: String,
    displayName: String
): Uri? = performInsertWrite(
    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
    ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        put(
            MediaStore.MediaColumns.RELATIVE_PATH,
            if (relativePath.contains("DCIM") || relativePath.contains("Pictures"))
                relativePath
            else Environment.DIRECTORY_PICTURES + "/Edited"
        )
    }
) { out ->
    if (!bitmap.compress(format, 95, out)) throw IOException("Compression failed")
}

suspend fun ContentResolver.saveVideo(
    data: ByteArray,
    mimeType: String,
    relativePath: String = Environment.DIRECTORY_MOVIES,
    displayName: String
): Uri? = performInsertWrite(
    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
    ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        put(
            MediaStore.MediaColumns.RELATIVE_PATH,
            if (relativePath.contains("DCIM") || relativePath.contains("Movies"))
                relativePath
            else Environment.DIRECTORY_MOVIES + "/Edited"
        )
    }
) { out ->
    out.write(data)
}

private suspend fun ContentResolver.performInsertWrite(
    baseUri: Uri,
    values: ContentValues,
    writeBlock: (OutputStream) -> Unit
): Uri? = withContext(Dispatchers.IO) {
    var tmp: Uri? = null
    runCatching {
        insert(baseUri, values)?.also { uri ->
            tmp = uri
            openOutputStream(uri)?.use(writeBlock)
                ?: throw IOException("Stream open failed")
        } ?: throw IOException("Insert returned null")
    }.getOrElse {
        tmp?.let { delete(it, null, null) }
        null
    }
}

suspend fun <T : Media> Context.renameMedia(media: T, newName: String): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            contentResolver.update(
                media.getUri(),
                ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newName) },
                null
            ) > 0
        }.onSuccess {
            MediaScannerConnection.scanFile(
                this@renameMedia, arrayOf(media.path.removeSuffix(media.label)),
                arrayOf(media.mimeType), null
            )
        }.getOrElse {
            printWarning(it.message.toString())
            false
        }
    }

suspend fun <T : Media> Context.updateMedia(
    media: T,
    contentValues: ContentValues
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        contentResolver.update(media.getUri(), contentValues, null) > 0
    }.onSuccess {
        MediaScannerConnection.scanFile(
            this@updateMedia, arrayOf(media.path.removeSuffix(media.label)),
            arrayOf(media.mimeType), null
        )
    }.getOrElse {
        printWarning(it.message.toString())
        false
    }
}

suspend fun <T : Media> Context.updateMediaExif(
    media: T,
    action: suspend ExifInterface.(T) -> Unit,
    postAction: suspend (T) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        contentResolver.openFileDescriptor(media.getUri(), "rw")?.use { pfd ->
            ExifInterface(pfd.fileDescriptor).apply {
                action(media)
                saveAttributes()
            }
        } ?: throw IOException("PFD null")
        updateMedia(media, ContentValues().apply {
            put(MediaStore.MediaColumns.DATE_MODIFIED, System.currentTimeMillis())
        })
        postAction(media)
        true
    }.getOrElse {
        printWarning(it.message.toString())
        false
    }
}


/**
 * Overwrite an existing media row (image) with a new bitmap.
 * Returns true on success, false on failure (never throws unless coroutine cancelled).
 */
suspend fun ContentResolver.overrideImage(
    uri: Uri,
    bitmap: Bitmap,
    mimeType: String? = null,
    format: Bitmap.CompressFormat? = null,
    quality: Int = 95,
    keepExif: Boolean = true,
    recycleSource: Boolean = false,
    sizeLimitBytes: Long? = null,
    onSizeLimitExceeded: ((Long) -> Unit)? = null
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        // 1. Resolve mime + format
        val resolvedMime = mimeType
            ?: getType(uri)
            ?: "image/jpeg"

        val compressFormat = format ?: inferCompressFormat(resolvedMime)

        // 2. Capture EXIF (only if JPEG + keepExif)
        val exifData: MutableMap<String, String>? =
            if (keepExif && resolvedMime.contains("jpeg", true)) {
                try {
                    openInputStream(uri)?.use { input ->
                        val exif = ExifInterface(input)
                        copyExifTags(exif)
                    }
                } catch (_: Exception) { null }
            } else null

        // 3. Mark pending (scoped storage) to reduce race (best effort)
        val canPending = Build.VERSION.SDK_INT >= 29
        if (canPending) {
            runCatching {
                update(uri, ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }, null, null)
            }
        }

        // 4. Encode into memory first (atomic style) so we fail early
        val encoded = ByteArrayOutputStream().use { bos ->
            if (!bitmap.compress(compressFormat, quality.coerceIn(0,100), bos))
                error("Bitmap.compress returned false")
            bos.toByteArray()
        }

        // 5. Size limit check
        sizeLimitBytes?.let { limit ->
            if (encoded.size.toLong() > limit) {
                onSizeLimitExceeded?.invoke(encoded.size.toLong())
                if (canPending) clearPendingQuiet(uri)
                return@runCatching false
            }
        }

        // 6. Write (truncate + replace)
        (openOutputStream(uri, "rwt") // "rwt" truncates if supported
            ?: openOutputStream(uri) // fallback
            ?: error("Failed to open output stream"))
                .use { out ->
                    out.write(encoded)
                    out.flush()
                    if (out is FileOutputStream) {
                        try { out.fd.sync() } catch (_: Exception) {}
                    }
                }

        // 7. Restore EXIF (requires rewrite for JPEG)
        if (exifData != null && compressFormat == Bitmap.CompressFormat.JPEG) {
            try {
                // Re-open and rewrite selected tags
                openFileDescriptor(uri, "rw")?.use { pfd ->
                    FileInputStream(pfd.fileDescriptor).use { fis ->
                        // Need temp file because ExifInterface rewrite requires random access
                        val tmp = File.createTempFile("exif_tmp", ".jpg")
                        tmp.outputStream().use { it.write(encoded) }
                        val exif = ExifInterface(tmp.absolutePath)
                        exifData.forEach { (k, v) -> exif.setAttribute(k, v) }
                        // After physical rotation, orientation must be normal
                        exif.setAttribute(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL.toString()
                        )
                        exif.saveAttributes()
                        // Write back
                        FileInputStream(tmp).use { updated ->
                            openOutputStream(uri, "rwt")?.use { finalOut ->
                                updated.copyTo(finalOut)
                                finalOut.flush()
                            }
                        }
                        tmp.delete()
                    }
                }
            } catch (_: Exception) {
                // Silent; keep at least the pixels
            }
        }

        if (recycleSource) runCatching { bitmap.recycle() }

        // 8. Clear pending
        if (canPending) clearPendingQuiet(uri)

        true
    }.getOrElse {
        clearPendingQuiet(uri)
        false
    }
}

private fun ContentResolver.clearPendingQuiet(uri: Uri) {
    runCatching {
        update(uri, ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }, null, null)
    }
}

private fun inferCompressFormat(mime: String): Bitmap.CompressFormat =
    when {
        mime.contains("png", true) -> Bitmap.CompressFormat.PNG
        mime.contains("webp", true) -> {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        mime.contains("jpeg", true) || mime.contains("jpg", true) -> Bitmap.CompressFormat.JPEG
        else -> Bitmap.CompressFormat.PNG
    }

private val EXIF_PASSTHROUGH_TAGS = arrayOf(
    ExifInterface.TAG_DATETIME_ORIGINAL,
    ExifInterface.TAG_DATETIME,
    ExifInterface.TAG_MAKE,
    ExifInterface.TAG_MODEL,
    ExifInterface.TAG_F_NUMBER,
    ExifInterface.TAG_FOCAL_LENGTH,
    ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
    ExifInterface.TAG_EXPOSURE_TIME,
    ExifInterface.TAG_WHITE_BALANCE,
    ExifInterface.TAG_GPS_LATITUDE,
    ExifInterface.TAG_GPS_LONGITUDE,
    ExifInterface.TAG_GPS_LATITUDE_REF,
    ExifInterface.TAG_GPS_LONGITUDE_REF,
    ExifInterface.TAG_GPS_ALTITUDE,
    ExifInterface.TAG_GPS_ALTITUDE_REF,
    ExifInterface.TAG_GPS_TIMESTAMP,
    ExifInterface.TAG_GPS_DATESTAMP
)

private fun copyExifTags(exif: ExifInterface): MutableMap<String, String> =
    buildMap {
        for (tag in EXIF_PASSTHROUGH_TAGS) {
            exif.getAttribute(tag)?.let { put(tag, it) }
        }
    }.toMutableMap()
