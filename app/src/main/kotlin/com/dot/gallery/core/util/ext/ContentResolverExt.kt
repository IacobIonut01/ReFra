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
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.FileUtils
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.dot.gallery.core.Constants
import com.dot.gallery.feature_node.domain.model.ExifAttributes
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isVideo
import com.dot.gallery.feature_node.presentation.util.printWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

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

suspend fun <T : Media> ContentResolver.copyMedia(
    from: T,
    path: String
) = withContext(Dispatchers.IO) {
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, from.label)
        put(MediaStore.MediaColumns.MIME_TYPE, from.mimeType)
        put(MediaStore.MediaColumns.RELATIVE_PATH, path)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val volumeUri =
        if (from.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    try {
        val outUri = insert(volumeUri, contentValues)
        if (outUri != null) {
            async {
                openFileDescriptor(outUri, "w", null).use { target ->
                    openFileDescriptor(from.getUri(), "r").use { from ->
                        if (target != null && from != null) {
                            try {
                                FileUtils.copy(from.fileDescriptor, target.fileDescriptor)
                            } catch (e: IOException) {
                                if (e.message.toString().contains("ENOSPC")) {
                                    Log.e(Constants.TAG, "No space left on device")
                                } else {
                                    Log.e(Constants.TAG, e.message.toString())
                                }
                                return@async
                            }
                        }
                    }
                }
            }.await()
            val updatedValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
                put(MediaStore.MediaColumns.DATE_MODIFIED, System.currentTimeMillis())
            }

            update(
                outUri,
                updatedValues,
                null
            ) > 0
        } else false
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun ContentResolver.overrideImage(
    uri: Uri,
    bitmap: Bitmap,
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG
): Boolean {
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DATE_MODIFIED, System.currentTimeMillis())
    }

    return runCatching {
        update(uri, values, null)
        openOutputStream(uri)?.use { stream ->
            if (!bitmap.compress(format, 100, stream))
                throw IOException("Failed to save bitmap.")
        } ?: throw IOException("Failed to open output stream.")
        update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null
        ) > 0
    }.getOrElse {
        throw it
    }
}

fun ContentResolver.restoreImage(
    byteArray: ByteArray,
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
    mimeType: String = "image/png",
    relativePath: String = Environment.DIRECTORY_PICTURES + "/Restored",
    displayName: String
): Uri? {
    val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    return saveImage(bitmap, format, mimeType, relativePath, displayName)
}

fun ContentResolver.saveImage(
    bitmap: Bitmap,
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
    mimeType: String = "image/png",
    relativePath: String = Environment.DIRECTORY_PICTURES,
    displayName: String
): Uri? {
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        put(
            MediaStore.MediaColumns.RELATIVE_PATH,
            if (relativePath.contains("DCIM") || relativePath.contains("Pictures")) relativePath
            else Environment.DIRECTORY_PICTURES + "/Edited"
        )
    }

    var uri: Uri? = null

    return runCatching {
        insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.also {
            uri = it // Keep uri reference so it can be removed on failure

            openOutputStream(it)?.use { stream ->
                if (!bitmap.compress(format, 95, stream))
                    throw IOException("Failed to save bitmap.")
            } ?: throw IOException("Failed to open output stream.")

        } ?: throw IOException("Failed to create new MediaStore record.")
    }.getOrElse {
        uri?.let { orphanUri ->
            // Don't leave an orphan entry in the MediaStore
            delete(orphanUri, null, null)
        }

        return null
    }
}

fun ContentResolver.saveVideo(
    bytes: ByteArray,
    mimeType: String,
    relativePath: String = Environment.DIRECTORY_MOVIES,
    displayName: String
): Uri? {
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        put(
            MediaStore.MediaColumns.RELATIVE_PATH,
            if (relativePath.contains("DCIM") || relativePath.contains("Movies")) relativePath
            else Environment.DIRECTORY_MOVIES + "/Edited"
        )
    }

    var uri: Uri? = null

    return runCatching {
        insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.also {
            uri = it // Keep uri reference so it can be removed on failure

            openOutputStream(it)?.use { stream ->
                stream.write(bytes)
            } ?: throw IOException("Failed to open output stream.")

        } ?: throw IOException("Failed to create new MediaStore record.")
    }.getOrElse {
        uri?.let { orphanUri ->
            // Don't leave an orphan entry in the MediaStore
            delete(orphanUri, null, null)
        }

        return null
    }
}

suspend fun <T: Media> Context.renameMedia(
    media: T,
    newName: String
): Boolean = withContext(Dispatchers.IO) {
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
    }
    with(contentResolver) {
        val uri = media.getUri()
        val newPath = media.path.removeSuffix(media.label).plus(newName)
        return@withContext runCatching {
            val updated = update(
                uri,
                contentValues,
                null
            ) > 0
            MediaScannerConnection.scanFile(
                this@renameMedia,
                arrayOf(media.path.removeSuffix(media.label)),
                arrayOf(media.mimeType), null
            )
            val newFile = File(newPath)
            if (newFile.exists()) {
                newFile.setLastModified(media.timestamp)
            }
            updated
        }.getOrElse {
            printWarning(it.message.toString())
            false
        }
    }
}

suspend fun <T : Media> Context.updateMedia(
    media: T,
    contentValues: ContentValues
): Boolean = withContext(Dispatchers.IO) {
    with(contentResolver) {
        val uri = media.getUri()
        return@withContext runCatching {
            val updated = update(
                uri,
                contentValues,
                null
            ) > 0
            MediaScannerConnection.scanFile(
                this@updateMedia,
                arrayOf(media.path.removeSuffix(media.label)),
                arrayOf(media.mimeType), null
            )
            updated
        }.getOrElse {
            printWarning(it.message.toString())
            false
        }
    }
}

suspend fun <T : Media> ContentResolver.updateMediaExif(
    media: T,
    exifAttributes: ExifAttributes
) = withContext(Dispatchers.IO) {
    return@withContext try {
        openFileDescriptor(media.getUri(), "rw").use { imagePfd ->
            if (imagePfd != null) {
                val exif = ExifInterface(imagePfd.fileDescriptor)
                exifAttributes.writeExif(exif)
                runCatching {
                    exif.saveAttributes()
                }.onFailure {
                    it.printStackTrace()
                }
            }
            true
        }
    } catch (e: java.lang.Exception) {
        e.printStackTrace()
        false
    }
}
