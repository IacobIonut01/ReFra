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
        update(uri, ContentValues().apply {
            put(MediaStore.MediaColumns.DATE_MODIFIED, System.currentTimeMillis())
        }, null)
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