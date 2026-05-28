/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.sandbox

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.dot.gallery.feature_node.presentation.util.printDebug
import com.dot.gallery.feature_node.presentation.util.printWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Queries media files from the user's private folder via SAF [DocumentFile] APIs.
 *
 * Unlike [MediaStore]-based queries, these files live outside the normal
 * media library and are only accessible through the persisted tree URI.
 */
class PrivateFolderRepository(private val context: Context) {

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
     * Lists all image and video files from the private folder.
     * Emits an empty list if no folder is configured or permission is lost.
     */
    fun listMedia(): Flow<List<PrivateMedia>> = flow {
        val uriString = PrivateFolderManager.getUri(context).firstOrNull()
        if (uriString.isNullOrEmpty()) {
            emit(emptyList())
            return@flow
        }

        if (!PrivateFolderManager.hasValidPermission(context, uriString)) {
            printWarning("PrivateFolderRepository: lost permission for $uriString")
            emit(emptyList())
            return@flow
        }

        val treeUri = uriString.toUri()
        val rootDocUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri, DocumentsContract.getTreeDocumentId(treeUri)
        )

        val media = mutableListOf<PrivateMedia>()
        collectMedia(treeUri, rootDocUri, media)
        printDebug("PrivateFolderRepository: found ${media.size} media files")
        emit(media.sortedByDescending { it.lastModified })
    }.flowOn(Dispatchers.IO)

    private fun collectMedia(
        treeUri: Uri,
        childrenUri: Uri,
        result: MutableList<PrivateMedia>
    ) {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(childrenUri, projection, null, null, null)
            cursor?.use {
                while (it.moveToNext()) {
                    val docId = it.getString(0) ?: continue
                    val name = it.getString(1) ?: "unknown"
                    val mime = it.getString(2) ?: continue
                    val size = it.getLong(3)
                    val modified = it.getLong(4)

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val subChildrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                            treeUri, docId
                        )
                        collectMedia(treeUri, subChildrenUri, result)
                    } else if (mime.startsWith("image/") || mime.startsWith("video/")) {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        result.add(
                            PrivateMedia(
                                uri = docUri,
                                displayName = name,
                                mimeType = mime,
                                size = size,
                                lastModified = modified
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            printWarning("PrivateFolderRepository: query failed for $childrenUri: ${e.message}")
        }
    }

    /**
     * Delete a file from the private folder.
     * Returns true if deletion succeeded.
     */
    fun deleteMedia(media: PrivateMedia): Boolean {
        return try {
            DocumentsContract.deleteDocument(context.contentResolver, media.uri)
        } catch (e: Exception) {
            printWarning("PrivateFolderRepository: delete failed for ${media.uri}: ${e.message}")
            false
        }
    }
}
