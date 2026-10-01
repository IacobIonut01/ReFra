/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.sync

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.dot.gallery.feature_node.presentation.util.printWarn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ASSET_STORE_UUID =
    Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
private val ASSET_STORE_SHARD = Regex("^[0-9a-fA-F]{2}$")

/**
 * Sanitizes a download sub-path (relative to `Pictures/`/`Movies/`): drops empty and
 * `.`/`..` segments, and — when the path embeds a remote asset-store layout such as
 * Immich's `upload/<userId>/<xx>` shard tree — substitutes [fallback] instead, so an
 * internal storage layout can never leak into user-visible folders. Pure function,
 * kept free of Android APIs so it stays unit-testable on the JVM.
 */
internal fun sanitizeDownloadSubPath(subPath: String, fallback: String): String {
    val cleanFallback = fallback.trim('/').ifBlank { "Cloud" }
    val segments = subPath.split('/')
        .map { it.trim() }
        .filter { it.isNotEmpty() && it != "." && it != ".." }
    if (segments.isEmpty()) return cleanFallback
    for (i in 1 until segments.size) {
        if (ASSET_STORE_UUID.matches(segments[i - 1]) && ASSET_STORE_SHARD.matches(segments[i])) {
            return cleanFallback
        }
    }
    return segments.joinToString("/")
}

/**
 * Makes a remote album name safe to use as a single MediaStore folder segment.
 * Pure function — JVM unit tests cover it.
 */
internal fun sanitizeDownloadPathSegment(name: String): String =
    name.replace(Regex("[/\\\\]"), "-")
        .replace(Regex("[\\u0000-\\u001f]"), "")
        .trim()
        .trimStart('.')
        .ifBlank { "Album" }

/**
 * Shared MediaStore writer for cloud downloads. Used by the manual "Download" action
 * (`MediaHandlerImpl.downloadCloudMedia`) and by `CloudDownloadWorker` for automatic
 * remote -> local sync, so both paths produce identical MediaStore rows.
 */
object CloudMediaStoreWriter {

    /**
     * @param displayName file name shown in MediaStore (remote label).
     * @param mimeType remote MIME type — decides the Images vs Video collection.
     * @param relativeSubPath sub-path appended to `Pictures/` or `Movies/`
     *   (e.g. `Cloud` for manual downloads, `Album/Sub` when mirroring remote folders,
     *   or the provider display name as a fallback root).
     * @param fallbackSubPath used when [relativeSubPath] sanitizes to nothing or is
     *   rejected as an internal asset-store layout.
     * @param takenTimestamp remote capture time in epoch millis; written to DATE_TAKEN so
     *   the downloaded file sorts where it was shot, not when it was fetched.
     */
    data class Request(
        val displayName: String,
        val mimeType: String,
        val relativeSubPath: String = "Cloud",
        val fallbackSubPath: String = "Cloud",
        val takenTimestamp: Long? = null
    )

    /**
     * Copies [source] (typically a `file://` cache URI from `downloadAsset`) into a new
     * MediaStore row and returns its content URI, or null on failure. A failed write
     * cleans up the pending row so no phantom entries are left behind.
     */
    suspend fun write(context: Context, source: Uri, request: Request): Uri? =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val isVideo = request.mimeType.startsWith("video/")
            val collection = if (isVideo) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val subPath = sanitizeDownloadSubPath(
                request.relativeSubPath,
                request.fallbackSubPath
            )
            val relativePath = if (isVideo)
                Environment.DIRECTORY_MOVIES + "/" + subPath
            else
                Environment.DIRECTORY_PICTURES + "/" + subPath

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, request.displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, request.mimeType)
                request.takenTimestamp?.let { put(MediaStore.MediaColumns.DATE_TAKEN, it) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val insertUri = resolver.insert(collection, values) ?: return@withContext null
            try {
                resolver.openOutputStream(insertUri)?.use { output ->
                    resolver.openInputStream(source)?.use { input ->
                        input.copyTo(output)
                    }
                } ?: throw java.io.IOException("Could not open download streams")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(insertUri, values, null, null)
                }
                insertUri
            } catch (e: Exception) {
                runCatching { resolver.delete(insertUri, null, null) }
                null
            }
        }

    /**
     * Best-effort delete of the download source. Providers return `file://` cache URIs,
     * so only files under the app cache directory are deleted. A `content://media` URI
     * here would mean a provider handed back an existing user file — that is a copy
     * source, never something this pipeline may delete, so it is refused loudly.
     */
    fun deleteSource(context: Context, source: Uri) {
        when {
            source.authority == MediaStore.AUTHORITY -> {
                printWarn(
                    "cloud.download",
                    "deleteSource refused MediaStore uri",
                    ctx = mapOf("uri" to source.toString())
                )
            }
            source.scheme == ContentResolver.SCHEME_FILE -> {
                val path = source.path ?: return
                runCatching {
                    val file = java.io.File(path)
                    val cachePath = context.cacheDir.canonicalPath
                    if (file.canonicalPath == cachePath ||
                        file.canonicalPath.startsWith("$cachePath/")
                    ) {
                        file.delete()
                    }
                }
            }
            else -> runCatching { context.contentResolver.delete(source, null, null) }
        }
    }
}
