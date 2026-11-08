/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.sandbox

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.dot.gallery.core.util.ext.saveRawStream
import com.dot.gallery.core.util.ext.saveVideoStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.dot.gallery.core.util.or
import com.dot.gallery.core.presentation.util.add
import com.dot.gallery.core.presentation.util.collectMedia
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.domain.uri.query
import com.dot.gallery.domain.util.isImage
import com.dot.gallery.domain.util.isVideo
import com.dot.gallery.core.logging.printDebug
import com.dot.gallery.core.logging.printWarning


/**
 * Queries media files from the user's private folder via SAF [DocumentFile] APIs.
 *
 * Unlike [MediaStore]-based queries, these files live outside the normal
 * media library and are only accessible through the persisted tree URI.
 */
const val PRIVATE_FOLDER_ALBUM_ID = -300L

class PrivateFolderRepository(private val context: Context) {

    /**
     * Monotonic counter bumped whenever the private folder's contents change
     * (add / delete / move-out). [listMediaProgressive] re-scans on every
     * change so observers (e.g. the private-folder grid) refresh immediately
     * after an item is removed, instead of showing a stale snapshot.
     */
    private val invalidations = MutableStateFlow(0)

    private fun invalidate() {
        invalidations.value += 1
    }

    /**
     * A lightweight representation of a file in the private folder.
     */
    data class PrivateMedia(
        val uri: Uri,
        val displayName: String,
        val mimeType: String,
        val size: Long,
        val lastModified: Long
    ) {
        val isImage: Boolean get() = mimeType.startsWith("image/")
        val isVideo: Boolean get() = mimeType.startsWith("video/")
    }

    /**
     * A progressive snapshot of the private folder scan. [media] is the
     * sorted list found so far; [isLoading] is true while the SAF tree is
     * still being traversed and false once the scan has fully completed.
     */
    data class ScanState(
        val media: List<PrivateMedia>,
        val isLoading: Boolean
    )

    /**
     * Lists all image and video files from the private folder, emitting
     * progressive snapshots as subfolders are traversed.
     *
     * The tree is walked breadth-first so shallow media appears first, and
     * intermediate results are emitted (time-throttled) so the UI fills in
     * incrementally instead of staying empty until the whole — potentially
     * huge — tree has been scanned. The terminal emission carries
     * [ScanState.isLoading] = false.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun listMediaProgressive(): Flow<ScanState> =
        invalidations.flatMapLatest { scanMediaProgressive() }

    private fun scanMediaProgressive(): Flow<ScanState> = flow {
        val uriString = PrivateFolderManager.getUri(context).firstOrNull()
        if (uriString.isNullOrEmpty()) {
            emit(ScanState(emptyList(), isLoading = false))
            return@flow
        }

        if (!PrivateFolderManager.hasValidPermission(context, uriString)) {
            printWarning("PrivateFolderRepository: lost permission for $uriString")
            emit(ScanState(emptyList(), isLoading = false))
            return@flow
        }

        // Signal that scanning has started so the UI can show a loading state
        // instead of an empty state while deep trees are still being walked.
        emit(ScanState(emptyList(), isLoading = true))

        val treeUri = uriString.toUri()
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)

        val media = mutableListOf<PrivateMedia>()
        collectMedia(treeUri, rootDocId, media)
        printDebug("PrivateFolderRepository: found ${media.size} media files")
        emit(ScanState(media.sortedByDescending { it.lastModified }, isLoading = false))
    }.flowOn(Dispatchers.IO)

    /**
     * Lists all image and video files from the private folder.
     * Emits an empty list if no folder is configured or permission is lost.
     */
    fun listMedia(): Flow<List<PrivateMedia>> =
        listMediaProgressive().map { it.media }

    /**
     * Breadth-first traversal of the SAF tree. Emits time-throttled,
     * accumulating sorted snapshots through [emit] so the UI can render
     * media as it is discovered rather than waiting for the full scan.
     */
    private suspend fun FlowCollector<ScanState>.collectMedia(
        treeUri: Uri,
        rootDocId: String,
        result: MutableList<PrivateMedia>
    ) {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        val queue = ArrayDeque<String>()
        queue.add(rootDocId)
        var lastEmit = System.currentTimeMillis()
        var pending = 0

        while (queue.isNotEmpty()) {
            val docId = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            var cursor: Cursor? = null
            try {
                cursor = context.contentResolver.query(childrenUri, projection, null, null, null)
                cursor?.use {
                    while (it.moveToNext()) {
                        val childDocId = it.getString(0) ?: continue
                        val name = it.getString(1) ?: "unknown"
                        val mime = it.getString(2) ?: continue
                        val size = it.getLong(3)
                        val modified = it.getLong(4)

                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            queue.add(childDocId)
                        } else if (mime.startsWith("image/") || mime.startsWith("video/")) {
                            val docUri =
                                DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                            result.add(
                                PrivateMedia(
                                    uri = docUri,
                                    displayName = name,
                                    mimeType = mime,
                                    size = size,
                                    lastModified = modified
                                )
                            )
                            pending++
                        }
                    }
                }
            } catch (e: Exception) {
                printWarning("PrivateFolderRepository: query failed for $childrenUri: ${e.message}")
            }

            // Emit a throttled intermediate snapshot so the grid fills in as
            // we go, without sorting the whole list on every single folder.
            val now = System.currentTimeMillis()
            if (pending > 0 && now - lastEmit >= INTERMEDIATE_EMIT_INTERVAL_MS) {
                emit(ScanState(result.sortedByDescending { it.lastModified }, isLoading = true))
                lastEmit = now
                pending = 0
            }
        }
    }

    /**
     * Copy an external media [sourceUri] (e.g. a MediaStore item) into the
     * configured private folder via SAF. The system document provider assigns
     * a unique display name if [displayName] already exists, so this never
     * overwrites. Returns true if the file was written successfully.
     *
     * Callers that want a "move" should delete the original only after this
     * returns true (e.g. via a MediaStore delete request).
     */
    suspend fun addMedia(
        sourceUri: Uri,
        displayName: String,
        mimeType: String
    ): Boolean = withContext(Dispatchers.IO) {
        val uriString = PrivateFolderManager.getUri(context).firstOrNull()
        if (uriString.isNullOrEmpty()) {
            printWarning("PrivateFolderRepository: no private folder configured")
            return@withContext false
        }
        if (!PrivateFolderManager.hasValidPermission(context, uriString)) {
            printWarning("PrivateFolderRepository: lost permission for $uriString")
            return@withContext false
        }

        val treeUri = uriString.toUri()
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
        val safeMime = mimeType.ifBlank { "application/octet-stream" }
        val safeName = displayName.ifBlank { "file_${System.currentTimeMillis()}" }

        var newDocUri: Uri? = null
        return@withContext try {
            newDocUri = DocumentsContract.createDocument(
                context.contentResolver, parentDocUri, safeMime, safeName
            ) ?: return@withContext false
            val copied = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                context.contentResolver.openOutputStream(newDocUri)?.use { output ->
                    input.copyTo(output)
                    true
                } ?: false
            } ?: false
            if (!copied) {
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, newDocUri) }
            }
            if (copied) invalidate()
            copied
        } catch (e: Exception) {
            printWarning("PrivateFolderRepository: addMedia failed for $sourceUri: ${e.message}")
            newDocUri?.let { runCatching { DocumentsContract.deleteDocument(context.contentResolver, it) } }
            false
        }
    }

    /**
     * Delete a file from the private folder.
     * Returns true if deletion succeeded.
     */
    fun deleteMedia(media: PrivateMedia): Boolean = deleteByUri(media.uri)

    /**
     * Delete a private-folder file by its SAF document [uri].
     *
     * Private-folder items are SAF documents, not MediaStore entries, so they
     * must be removed via [DocumentsContract.deleteDocument] rather than a
     * MediaStore delete request (which crashes on tree document URIs — ).
     * Returns true if deletion succeeded.
     */
    fun deleteByUri(uri: Uri): Boolean {
        return try {
            DocumentsContract.deleteDocument(context.contentResolver, uri).also { deleted ->
                if (deleted) invalidate()
            }
        } catch (e: Exception) {
            printWarning("PrivateFolderRepository: delete failed for $uri: ${e.message}")
            false
        }
    }

    /**
     * Copy a private-folder [sourceUri] back into the public MediaStore
     * (Pictures/Movies "Restored"), then delete the private-folder original so
     * the operation behaves as a true "move out". Returns true only if the
     * public copy was written AND the SAF original was removed.
     */
    suspend fun moveOut(
        sourceUri: Uri,
        displayName: String,
        mimeType: String
    ): Boolean = withContext(Dispatchers.IO) {
        val isVideo = mimeType.startsWith("video/")
        val safeName = displayName.ifBlank { "file_${System.currentTimeMillis()}" }
        val restored = try {
            if (isVideo) {
                context.contentResolver.saveVideoStream(
                    writeBlock = { out ->
                        context.contentResolver.openInputStream(sourceUri)?.use { it.copyTo(out) }
                            ?: throw java.io.IOException("Cannot open $sourceUri")
                    },
                    displayName = safeName,
                    mimeType = mimeType.ifBlank { "video/mp4" },
                    relativePath = Environment.DIRECTORY_MOVIES + "/Restored"
                )
            } else {
                context.contentResolver.saveRawStream(
                    writeBlock = { out ->
                        context.contentResolver.openInputStream(sourceUri)?.use { it.copyTo(out) }
                            ?: throw java.io.IOException("Cannot open $sourceUri")
                    },
                    displayName = safeName,
                    mimeType = mimeType.ifBlank { "image/*" },
                    relativePath = Environment.DIRECTORY_PICTURES + "/Restored"
                )
            }
        } catch (e: Exception) {
            printWarning("PrivateFolderRepository: moveOut copy failed for $sourceUri: ${e.message}")
            null
        }
        if (restored == null) return@withContext false
        deleteByUri(sourceUri)
    }

    private companion object {
        /** Minimum gap between intermediate progressive emissions while scanning. */
        const val INTERMEDIATE_EMIT_INTERVAL_MS = 300L
    }
}
