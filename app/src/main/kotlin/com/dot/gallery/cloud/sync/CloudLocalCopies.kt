/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.sync

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import com.dot.gallery.cloud.data.dao.CloudMediaLocalState
import com.dot.gallery.cloud.data.entity.CloudMediaEntity
import com.dot.gallery.cloud.data.entity.canonicalBackupChecksum
import com.dot.gallery.feature_node.presentation.util.printDebug
import com.dot.gallery.feature_node.presentation.util.printWarn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Extracts the MediaStore row id from a stored `localCopyPath`. The download pipeline
 * stores canonical `content://media/<volume>/file/<id>` URIs, so the id is always the
 * last path segment. Returns null for anything else (legacy file:// paths, blanks).
 * Kept free of android.net.Uri so it stays unit-testable on the JVM.
 */
internal fun localCopyMediaStoreId(localCopyPath: String): Long? =
    localCopyPath.substringAfterLast('/').toLongOrNull()

private const val MAX_LOCAL_COPY_CANDIDATES = 32

/**
 * Cheap pre-filter for [findVerifiedLocalCopy], kept pure so JVM tests cover it: the
 * local file is only worth hashing when its name matches the remote label and its size
 * matches the remote size (unknown remote size `<= 0` skips the size check). The hash
 * verify afterwards is what actually authorizes the link.
 */
internal fun isLocalCopyCandidate(
    remoteLabel: String,
    remoteSize: Long,
    localName: String?,
    localSize: Long
): Boolean = remoteLabel.isNotBlank() && remoteLabel == localName &&
    (remoteSize <= 0L || remoteSize == localSize)

/**
 * Reconciles a `REMOTE_ONLY` row against existing local files: find a MediaStore file
 * with the same name (+ size when known) and SHA-1 matching [entity]'s remote
 * `contentHash`, so content the server already holds — e.g. uploaded by another app
 * before ReFra saw it — is linked instead of re-downloaded. Returns the verified
 * local `content://media` URI, or null when no identical local file exists (or the
 * remote has no checksum to compare against). Never returns an URI outside MediaStore.
 */
internal suspend fun findVerifiedLocalCopy(
    context: Context,
    entity: CloudMediaEntity
): Uri? = withContext(Dispatchers.IO) {
    val expectedHash = entity.contentHash
        ?.takeIf { it.isNotBlank() }
        ?.let(::canonicalBackupChecksum) ?: return@withContext null
    if (entity.label.isBlank()) return@withContext null
    val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    val selection = buildString {
        append(MediaStore.MediaColumns.DISPLAY_NAME).append(" = ?")
        append(" AND ").append(MediaStore.MediaColumns.IS_PENDING).append(" = 0")
        append(" AND ").append(MediaStore.MediaColumns.IS_TRASHED).append(" = 0")
        if (entity.size > 0) append(" AND ").append(MediaStore.MediaColumns.SIZE).append(" = ?")
    }
    val args = buildList {
        add(entity.label)
        if (entity.size > 0) add(entity.size.toString())
    }
    val candidates = mutableListOf<Uri>()
    runCatching {
        context.contentResolver.query(
            collection,
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE
            ),
            selection,
            args.toTypedArray(),
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            while (cursor.moveToNext() && candidates.size < MAX_LOCAL_COPY_CANDIDATES) {
                if (isLocalCopyCandidate(
                        entity.label, entity.size,
                        cursor.getString(nameCol), cursor.getLong(sizeCol)
                    )
                ) {
                    candidates += ContentUris.withAppendedId(collection, cursor.getLong(idCol))
                }
            }
        }
    }
    candidates.firstOrNull { uri ->
        sha1Hex(context, uri)?.equals(expectedHash, ignoreCase = true) == true
    }
}

private fun sha1Hex(context: Context, uri: Uri): String? = runCatching {
    val digest = MessageDigest.getInstance("SHA-1")
    context.contentResolver.openInputStream(uri)?.use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    } ?: return null
    digest.digest().joinToString("") { "%02x".format(it) }
}.getOrNull()

/**
 * Ownership check for a `localCopyPath` URI. MediaStore stamps rows the app created
 * with `OWNER_PACKAGE_NAME` = our package (API 29+, always true here), so only URIs
 * carrying our owner tag — or file paths under our private directories — may be
 * deleted by the app. Anything else (another app's file, a null owner) is refused.
 */
private fun isAppOwnedCopy(context: Context, uri: Uri): Boolean =
    when (uri.scheme) {
        ContentResolver.SCHEME_FILE -> {
            val path = uri.path ?: return false
            runCatching {
                val canonical = File(path).canonicalPath
                listOfNotNull(context.cacheDir, context.filesDir, context.getExternalFilesDir(null))
                    .map { it.canonicalPath }
                    .any { canonical == it || canonical.startsWith("$it/") }
            }.getOrDefault(false)
        }
        ContentResolver.SCHEME_CONTENT -> runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.OWNER_PACKAGE_NAME),
                null, null, null
            )?.use { cursor ->
                // Row absent -> the delete below is a no-op anyway; a present row must
                // carry our owner tag, otherwise the copy belongs to someone else.
                !cursor.moveToFirst() || cursor.getString(0) == context.packageName
            } ?: false
        }.getOrDefault(false)
        else -> false
    }

/**
 * Deletes the MediaStore/cache copies referenced by [states] and returns the states
 * whose copy is now gone (deleted or already absent). Callers must only pass rows
 * selected with `appLocalCopy = 1` — upload paths also populate `localCopyPath` with
 * the user's ORIGINAL file, so filtering on the app-owned flag is what makes deletion
 * safe; [isAppOwnedCopy] is the second belt: a URI that isn't provably ours (owner
 * package mismatch, foreign path) is skipped loudly rather than deleted. Failures are
 * logged per item and never abort the batch.
 */
internal suspend fun deleteAppLocalCopies(
    context: Context,
    states: List<CloudMediaLocalState>
): List<CloudMediaLocalState> = withContext(Dispatchers.IO) {
    val removed = mutableListOf<CloudMediaLocalState>()
    for (state in states) {
        val uriString = state.localCopyPath
        if (uriString.isBlank()) continue
        val uri = uriString.toUri()
        try {
            if (!isAppOwnedCopy(context, uri)) {
                printWarn(
                    "cloud.sync",
                    "refusing to delete non-app-owned local copy",
                    ctx = mapOf("remoteId" to state.remoteId, "uri" to uriString)
                )
                continue
            }
            val deleted = if (uri.scheme == ContentResolver.SCHEME_FILE) {
                File(uri.path ?: "").delete()
            } else {
                context.contentResolver.delete(uri, null, null) > 0
            }
            removed += state
            if (deleted) {
                printDebug("CloudLocalCopies: deleted local copy for ${state.remoteId}")
            } else {
                printDebug("CloudLocalCopies: local copy already gone for ${state.remoteId}")
            }
        } catch (e: Exception) {
            printDebug("CloudLocalCopies: failed to delete local copy for ${state.remoteId}: ${e.message}")
        }
    }
    removed
}
