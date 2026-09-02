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
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import com.dot.gallery.core.Settings
import com.dot.gallery.core.decoder.format.ImageReencoder
import com.dot.gallery.core.metrics.StartupTracer
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.util.printWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

@RequiresApi(Build.VERSION_CODES.R)
/** DATE_MODIFIED of [uri] in seconds, or 0 when it cannot be read. */
fun ContentResolver.mediaDateModified(uri: Uri): Long {
    runCatching {
        query(uri, arrayOf(MediaStore.MediaColumns.DATE_MODIFIED), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) return it.getLong(0)
        }
    }.onFailure { printWarning("Failed to read the date of $uri: ${it.message}") }
    return 0L
}

fun ContentResolver.mediaSize(uri: Uri): Long {
    runCatching {
        query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) return it.getLong(0)
        }
    }.onFailure { printWarning("Failed to read the size of $uri: ${it.message}") }
    return -1L
}

internal fun isVerifiedMediaCopy(sourceSize: Long, copiedBytes: Long, destinationSize: Long): Boolean =
    copiedBytes > 0L &&
        (sourceSize < 0L || copiedBytes == sourceSize) &&
        (destinationSize < 0L || copiedBytes == destinationSize)

internal fun selectedModifiedTimestamp(
    updateModifiedDate: Boolean,
    sourceDateModified: Long,
    currentTimeSeconds: Long,
): Long? = if (updateModifiedDate) currentTimeSeconds else sourceDateModified.takeIf { it > 0L }

internal suspend fun InputStream.copyToCancellable(
    output: OutputStream,
    onBytesCopied: suspend (Int) -> Unit = {},
): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var copiedBytes = 0L
    while (true) {
        currentCoroutineContext().ensureActive()
        val read = read(buffer)
        if (read == -1) break
        currentCoroutineContext().ensureActive()
        output.write(buffer, 0, read)
        copiedBytes += read
        onBytesCopied(read)
    }
    currentCoroutineContext().ensureActive()
    return copiedBytes
}

/**
 * Gives a freshly written copy the timestamp of the media it was copied from. MediaProvider
 * ignores DATE_MODIFIED/DATE_TAKEN coming from an app and recomputes them from the file, so the
 * timestamp has to be set on the file itself and picked up by a rescan - otherwise every copy
 * jumps to the top of its album, ordered as if it had just been taken.
 */
suspend fun Context.restoreMediaTimestamp(
    uri: Uri,
    mimeType: String?,
    dateModifiedSeconds: Long
): Boolean {
    if (dateModifiedSeconds <= 0L) return false
    val path = runCatching {
        contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DATA),
            null,
            null,
            null
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull() ?: return false
    if (!File(path).setLastModified(dateModifiedSeconds * 1000)) return false
    return withTimeoutOrNull(SCAN_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            MediaScannerConnection.scanFile(
                this@restoreMediaTimestamp,
                arrayOf(path),
                arrayOf(mimeType)
            ) { _, _ ->
                if (continuation.isActive) continuation.resume(true) {  _, _, _ -> }
            }
        }
    } ?: false
}

private const val SCAN_TIMEOUT_MS = 10_000L

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
            val batchSpan = StartupTracer.begin("MediaStore.queryBatch(LIMIT=250)")
            val batchCursor = query(uri, projection, modifiedArgs, null)
            val batchCount = batchCursor?.count ?: 0
            StartupTracer.end(batchSpan)
            StartupTracer.begin("MediaStore.batchResult($batchCount rows)").also { s -> StartupTracer.end(s) }
            trySend(batchCursor)
            // Only run the full query if the batch was actually limited
            if (batchCount >= 250) {
                val fullSpan = StartupTracer.begin("MediaStore.queryFull(no limit)")
                val fullCursor = query(uri, projection, queryArgs, null)
                StartupTracer.end(fullSpan)
                StartupTracer.begin("MediaStore.fullResult(${fullCursor?.count ?: 0} rows)").also { s -> StartupTracer.end(s) }
                trySend(fullCursor)
            }
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
): Boolean = overrideImage(
    uri = uri,
    bitmap = bitmap,
    format = format,
    quality = 100,
)

/**
 * Format-aware save of an edited/rotated [bitmap] as a NEW file, re-encoded in [writeFormat] so the
 * source image format is preserved (JXL→JXL, AVIF→AVIF, HEIC→HEIC, …). Returns the new [Uri] or
 * `null` on failure. [mimeType] should match [writeFormat] (it drives the MediaStore MIME column).
 */
suspend fun ContentResolver.saveImageEncoded(
    bitmap: Bitmap,
    writeFormat: ImageReencoder.ImageWriteFormat,
    config: ImageReencoder.ReencodeConfig,
    mimeType: String,
    relativePath: String,
    displayName: String
): Uri? = performInsertWrite(
    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
    ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType.ifBlank { writeFormat.mimeType })
        put(
            MediaStore.MediaColumns.RELATIVE_PATH,
            if (relativePath.contains("DCIM") || relativePath.contains("Pictures"))
                relativePath
            else Environment.DIRECTORY_PICTURES + "/Edited"
        )
    }
) { out ->
    ImageReencoder.writeToStream(bitmap, writeFormat, config, out)
}

/**
 * Creates a new MediaStore image and lets [write] stream the encoded bytes straight into its file
 * descriptor (used by the editor's tiled bake + native scanline encoder so the full-resolution
 * output is never held in RAM). [write] must return true on success; on false/throw the pending
 * entry is deleted. Returns the new [Uri] or `null` on failure.
 */
suspend fun ContentResolver.saveImageStreaming(
    mimeType: String,
    relativePath: String,
    displayName: String,
    write: (fd: Int) -> Boolean,
): Uri? = withContext(Dispatchers.IO) {
    var tmp: Uri? = null
    runCatching {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                if (relativePath.contains("DCIM") || relativePath.contains("Pictures")) relativePath
                else Environment.DIRECTORY_PICTURES + "/Edited"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Insert returned null")
        tmp = uri
        val ok = openFileDescriptor(uri, "w")?.use { pfd -> write(pfd.fd) } ?: false
        if (!ok) throw IOException("Streaming encode failed")
        update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        uri
    }.getOrElse {
        tmp?.let { runCatching { delete(it, null, null) } }
        null
    }
}

/**
 * In-place overwrite of an existing image [uri] where [write] streams the encoded bytes into its
 * file descriptor. Preserves the original date columns. Returns true on success. Encoding always
 * finishes in [stagingFile] before the source is touched. Immediately before replacement, the
 * original bytes are copied to a rollback file and restored if the replacement write fails.
 */
suspend fun ContentResolver.overrideImageStreaming(
    uri: Uri,
    stagingFile: File,
    write: (fd: Int) -> Boolean,
): Boolean = withContext(Dispatchers.IO) {
    try {
        val originalDates = queryDateColumns(uri)
        val encoded = runCatching {
            ParcelFileDescriptor.open(
                stagingFile,
                ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_TRUNCATE or
                    ParcelFileDescriptor.MODE_READ_WRITE,
            ).use { output -> write(output.fd) }
        }.getOrDefault(false)
        if (!encoded || stagingFile.length() == 0L) return@withContext false

        replaceImageFromStaging(uri, stagingFile, originalDates)
    } finally {
        stagingFile.delete()
    }
}

/**
 * Format-aware in-place overwrite of an existing media row with [bitmap], re-encoded in
 * [writeFormat]. Preserves the original date columns so the item keeps its gallery position, and
 * for JPEG re-encodes carries over EXIF. Returns true on success.
 */
suspend fun ContentResolver.overrideImageEncoded(
    uri: Uri,
    bitmap: Bitmap,
    writeFormat: ImageReencoder.ImageWriteFormat,
    config: ImageReencoder.ReencodeConfig,
    keepExif: Boolean = true
): Boolean = withContext(Dispatchers.IO) {
    val stagingFile = File.createTempFile("encoded-override-", ".${writeFormat.fileExtension}")
    try {
        val originalDates = queryDateColumns(uri)
        val exifData = if (keepExif && writeFormat == ImageReencoder.ImageWriteFormat.JPEG) {
            runCatching {
                openInputStream(uri)?.use { input -> copyExifTags(ExifInterface(input)) }
            }.getOrNull()
        } else null

        val encoded = runCatching {
            FileOutputStream(stagingFile).use { output ->
                ImageReencoder.writeToStream(bitmap, writeFormat, config, output)
                output.flush()
                output.fd.sync()
            }
            if (exifData != null) runCatching { applyExifToStaging(stagingFile, exifData) }
            stagingFile.length() > 0L
        }.getOrDefault(false)
        if (!encoded) return@withContext false

        replaceImageFromStaging(uri, stagingFile, originalDates)
    } finally {
        stagingFile.delete()
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

/**
 * Saves raw image bytes without bitmap conversion.
 * Use this for formats like GIF, WebP that would lose animation/quality if converted.
 */
suspend fun ContentResolver.saveRawImage(
    data: ByteArray,
    mimeType: String,
    relativePath: String = Environment.DIRECTORY_PICTURES,
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
            else Environment.DIRECTORY_PICTURES + "/Restored"
        )
    }
) { out ->
    out.write(data)
}

suspend fun ContentResolver.saveRawStream(
    writeBlock: (OutputStream) -> Unit,
    mimeType: String,
    relativePath: String = Environment.DIRECTORY_PICTURES,
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
            else Environment.DIRECTORY_PICTURES + "/Restored"
        )
    }
) { out ->
    writeBlock(out)
}

suspend fun ContentResolver.saveVideoStream(
    writeBlock: (OutputStream) -> Unit,
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
    writeBlock(out)
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
            val uri = media.getUri()
            val sourceDateModified = contentResolver.mediaDateModified(uri)
            val updateModifiedDate = Settings.Album.updateModifiedDate(this@renameMedia)
                .firstOrNull() ?: false
            val renamed = contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newName) },
                null,
                null
            ) > 0
            if (renamed) {
                selectedModifiedTimestamp(
                    updateModifiedDate = updateModifiedDate,
                    sourceDateModified = sourceDateModified,
                    currentTimeSeconds = System.currentTimeMillis() / 1000L
                )?.let { timestamp ->
                    restoreMediaTimestamp(uri, media.mimeType, timestamp)
                }
            }
            renamed
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
        contentResolver.update(media.getUri(), contentValues, null, null) > 0
    }.onSuccess { updated ->
        if (updated) {
            // If RELATIVE_PATH changed (move), scan the new file location;
            // otherwise scan the current file path.
            val newRelPath = contentValues.getAsString(MediaStore.MediaColumns.RELATIVE_PATH)
            val scanPath = if (newRelPath != null) {
                // Derive volume prefix from the media's current path so this
                // works for both primary storage and SD cards.
                val volumePrefix = media.path.substringBeforeLast("/")
                    .removeSuffix(media.relativePath.removeSuffix("/"))
                    .trimEnd('/')
                volumePrefix + "/" + newRelPath.trimEnd('/') + "/" + media.label
            } else {
                media.path
            }
            MediaScannerConnection.scanFile(
                this@updateMedia, arrayOf(scanPath),
                arrayOf(media.mimeType), null
            )
        }
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
        val uri = media.getUri()
        val sourceDateModified = contentResolver.mediaDateModified(uri)
        val updateModifiedDate = Settings.Album.updateModifiedDate(this@updateMediaExif)
            .firstOrNull() ?: false
        contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
            ExifInterface(pfd.fileDescriptor).apply {
                action(media)
                saveAttributes()
            }
        } ?: throw IOException("PFD null")
        selectedModifiedTimestamp(
            updateModifiedDate = updateModifiedDate,
            sourceDateModified = sourceDateModified,
            currentTimeSeconds = System.currentTimeMillis() / 1000L
        )?.let { timestamp -> restoreMediaTimestamp(uri, media.mimeType, timestamp) }
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
    val stagingFile = File.createTempFile("bitmap-override-", ".tmp")
    try {
        val originalDates = queryDateColumns(uri)
        val resolvedMime = mimeType ?: getType(uri) ?: "image/jpeg"
        val compressFormat = format ?: inferCompressFormat(resolvedMime)
        val exifData = if (keepExif && resolvedMime.contains("jpeg", true)) {
            runCatching {
                openInputStream(uri)?.use { input -> copyExifTags(ExifInterface(input)) }
            }.getOrNull()
        } else null

        val encoded = runCatching {
            FileOutputStream(stagingFile).use { output ->
                if (!bitmap.compress(compressFormat, quality.coerceIn(0, 100), output)) {
                    throw IOException("Bitmap.compress returned false")
                }
                output.flush()
                output.fd.sync()
            }
            if (exifData != null && compressFormat == Bitmap.CompressFormat.JPEG) {
                runCatching { applyExifToStaging(stagingFile, exifData) }
            }
            stagingFile.length() > 0L
        }.getOrDefault(false)
        if (!encoded) return@withContext false

        sizeLimitBytes?.let { limit ->
            if (stagingFile.length() > limit) {
                onSizeLimitExceeded?.invoke(stagingFile.length())
                return@withContext false
            }
        }

        val replaced = replaceImageFromStaging(uri, stagingFile, originalDates)
        if (replaced && recycleSource) runCatching { bitmap.recycle() }
        replaced
    } finally {
        stagingFile.delete()
    }
}

private fun ContentResolver.replaceImageFromStaging(
    uri: Uri,
    stagingFile: File,
    originalDates: Pair<Long?, Long?>?,
): Boolean {
    val backupFile = File.createTempFile(
        "original-rollback-",
        ".bak",
        stagingFile.parentFile,
    )
    var keepBackup = false
    return try {
        copyUriToFile(uri, backupFile)

        val replaced = replaceWithRollback(stagingFile, backupFile) { source ->
            copyFileToUri(source, uri)
        }
        if (!replaced) return false

        originalDates?.let { (dateTaken, dateAdded) ->
            runCatching {
                update(uri, ContentValues().apply {
                    dateTaken?.let { put(MediaStore.Images.Media.DATE_TAKEN, it) }
                    dateAdded?.let { put(MediaStore.MediaColumns.DATE_ADDED, it) }
                }, null, null)
            }
        }
        true
    } catch (_: SourceRestoreException) {
        // Retain the rollback bytes for recovery rather than discarding the only complete source.
        // Normal replacement failures are restored before this point.
        keepBackup = true
        false
    } catch (_: Exception) {
        false
    } finally {
        if (!keepBackup) backupFile.delete()
    }
}

/**
 * Replaces a source from [replacementFile]. If that write throws after truncation, [backupFile] is
 * written back before this function reports failure. Kept internal so tests can inject a partial
 * first write without exposing failure hooks in production code.
 */
internal fun replaceWithRollback(
    replacementFile: File,
    backupFile: File,
    replaceSource: (File) -> Unit,
): Boolean = try {
    replaceSource(replacementFile)
    true
} catch (replacementError: Exception) {
    try {
        replaceSource(backupFile)
    } catch (restoreError: Exception) {
        throw SourceRestoreException(replacementError, restoreError)
    }
    false
}

private class SourceRestoreException(
    replacementError: Exception,
    restoreError: Exception,
) : IOException("Replacement and source restoration failed", restoreError) {
    init {
        addSuppressed(replacementError)
    }
}

private fun ContentResolver.copyUriToFile(uri: Uri, target: File) {
    val input = openInputStream(uri) ?: throw IOException("Failed to open source for backup")
    input.use {
        FileOutputStream(target).use { output ->
            it.copyTo(output)
            output.flush()
            output.fd.sync()
        }
    }
}

private fun ContentResolver.copyFileToUri(source: File, uri: Uri) {
    FileInputStream(source).use { input ->
        val output = openOutputStream(uri, "rwt")
            ?: throw IOException("Failed to open source for replacement")
        output.use {
            input.copyTo(it)
            it.flush()
            if (it is FileOutputStream) it.fd.sync()
        }
    }
}

private fun applyExifToStaging(
    stagingFile: File,
    exifData: Map<String, String>,
) {
    val exif = ExifInterface(stagingFile.absolutePath)
    exifData.forEach { (key, value) -> exif.setAttribute(key, value) }
    exif.setAttribute(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL.toString(),
    )
    exif.saveAttributes()
}

private fun ContentResolver.queryDateColumns(uri: Uri): Pair<Long?, Long?>? {
    return try {
        query(
            uri,
            arrayOf(MediaStore.Images.Media.DATE_TAKEN, MediaStore.MediaColumns.DATE_ADDED),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val dateTaken = if (!cursor.isNull(0)) cursor.getLong(0) else null
                val dateAdded = if (!cursor.isNull(1)) cursor.getLong(1) else null
                dateTaken to dateAdded
            } else null
        }
    } catch (_: Exception) {
        null
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
