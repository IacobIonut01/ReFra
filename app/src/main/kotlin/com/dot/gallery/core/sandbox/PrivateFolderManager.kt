/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.sandbox

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import android.provider.DocumentsContract
import com.dot.gallery.core.activeDataStore
import com.dot.gallery.feature_node.presentation.util.printDebug
import com.dot.gallery.feature_node.presentation.util.printWarning
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

/**
 * Manages the user-selected "private folder" — a SAF-backed directory
 * outside the app's default media scope.
 *
 * The folder URI is persisted via [takePersistableUriPermission] and stored
 * in the active DataStore so it survives app restarts and device reboots.
 */
object PrivateFolderManager {

    private val PRIVATE_FOLDER_URI_KEY =
        androidx.datastore.preferences.core.stringPreferencesKey("private_folder_uri")

    /**
     * Launch the system folder picker. Call this from a Composable or Activity
     * that has registered an [ActivityResultLauncher] for `ACTION_OPEN_DOCUMENT_TREE`.
     */
    fun createPickerIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
        }
    }

    /**
     * Persist the SAF permission, create a `.nomedia` file to prevent
     * MediaStore scanning, and save the URI to DataStore.
     * Call this from the `ActivityResultLauncher` callback.
     */
    suspend fun onFolderPicked(context: Context, uri: Uri) {
        // Persist across reboots
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            printWarning("PrivateFolderManager: failed to persist permission: ${e.message}")
        }

        // Create .nomedia so MediaStore ignores the folder contents
        ensureNoMedia(context, uri)

        // Save URI to DataStore
        context.activeDataStore.edit { prefs ->
            prefs[PRIVATE_FOLDER_URI_KEY] = uri.toString()
        }
        printDebug("PrivateFolderManager: saved private folder URI: $uri")
    }

    /**
     * Clear the saved private folder. Does NOT revoke SAF permissions
     * (the user can do that from system settings).
     */
    suspend fun clearFolder(context: Context) {
        // Try to release persisted permission
        val currentUri = getUri(context).firstOrNull()
        if (!currentUri.isNullOrEmpty()) {
            try {
                context.contentResolver.releasePersistableUriPermission(
                    currentUri.toUri(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Permission may have been revoked already
            }
        }

        context.activeDataStore.edit { prefs ->
            prefs.remove(PRIVATE_FOLDER_URI_KEY)
        }
        printDebug("PrivateFolderManager: cleared private folder")
    }

    /**
     * Clear the folder AND delete the .nomedia file so MediaStore will
     * re-scan and make the contents visible to other apps.
     */
    suspend fun clearFolderAndReveal(context: Context) {
        val currentUri = getUri(context).firstOrNull()
        if (!currentUri.isNullOrEmpty()) {
            val uri = currentUri.toUri()
            removeNoMedia(context, uri)
            triggerMediaScan(context, uri)
        }
        clearFolder(context)
    }

    /**
     * Flow of the current private folder URI string (empty if not set).
     */
    fun getUri(context: Context): Flow<String> {
        return context.activeDataStore.data.map { prefs ->
            prefs[PRIVATE_FOLDER_URI_KEY] ?: ""
        }
    }

    /**
     * Check if the persisted SAF permission is still valid.
     */
    fun hasValidPermission(context: Context, uriString: String): Boolean {
        if (uriString.isEmpty()) return false
        val uri = uriString.toUri()
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
    }

    /**
     * Create a `.nomedia` file in the given folder if one doesn't already exist.
     * This tells Android's MediaStore scanner to skip the directory, preventing
     * private photos from appearing in other gallery apps or system search.
     */
    private fun ensureNoMedia(context: Context, folderUri: Uri) {
        try {
            val treeDocId = DocumentsContract.getTreeDocumentId(folderUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                folderUri, treeDocId
            )

            // Check if .nomedia already exists
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    if (name == ".nomedia") {
                        printDebug("PrivateFolderManager: .nomedia already exists")
                        return
                    }
                }
            }

            // Create an empty .nomedia file
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(
                folderUri, treeDocId
            )
            DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                "application/octet-stream",
                ".nomedia"
            )
            printDebug("PrivateFolderManager: created .nomedia in private folder")
        } catch (e: Exception) {
            printWarning("PrivateFolderManager: failed to create .nomedia: ${e.message}")
        }
    }

    /**
     * Delete the `.nomedia` file from the given folder so MediaStore will
     * pick up its contents again on the next scan.
     */
    private fun removeNoMedia(context: Context, folderUri: Uri) {
        try {
            val treeDocId = DocumentsContract.getTreeDocumentId(folderUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                folderUri, treeDocId
            )
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(0)
                    val name = cursor.getString(1)
                    if (name == ".nomedia") {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                        DocumentsContract.deleteDocument(context.contentResolver, docUri)
                        printDebug("PrivateFolderManager: deleted .nomedia from private folder")
                        return
                    }
                }
            }
            printDebug("PrivateFolderManager: no .nomedia found to delete")
        } catch (e: Exception) {
            printWarning("PrivateFolderManager: failed to delete .nomedia: ${e.message}")
        }
    }

    /**
     * Ask the system MediaScanner to re-scan the folder so its contents
     * appear in MediaStore (and therefore in other gallery apps).
     */
    private fun triggerMediaScan(context: Context, folderUri: Uri) {
        try {
            val treeDocId = DocumentsContract.getTreeDocumentId(folderUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                folderUri, treeDocId
            )
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            val urisToScan = mutableListOf<Uri>()
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(0)
                    val mime = cursor.getString(1) ?: continue
                    if (mime.startsWith("image/") || mime.startsWith("video/")) {
                        urisToScan.add(
                            DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                        )
                    }
                }
            }
            if (urisToScan.isNotEmpty()) {
                MediaScannerConnection.scanFile(
                    context,
                    urisToScan.map { it.toString() }.toTypedArray(),
                    null,
                    null
                )
                printDebug("PrivateFolderManager: triggered media scan for ${urisToScan.size} files")
            }
        } catch (e: Exception) {
            printWarning("PrivateFolderManager: failed to trigger media scan: ${e.message}")
        }
    }
}
