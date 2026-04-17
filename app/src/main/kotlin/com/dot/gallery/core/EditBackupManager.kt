/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import com.dot.gallery.feature_node.data.data_source.EditHistoryDao
import com.dot.gallery.feature_node.domain.model.EditedMedia
import com.dot.gallery.feature_node.presentation.util.printDebug
import com.dot.gallery.feature_node.presentation.util.printError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class EditBackupManager(
    private val context: Context,
    private val editHistoryDao: EditHistoryDao
) {

    private val backupDir: File
        get() = File(context.filesDir, BACKUP_DIR_NAME).also { if (!it.exists()) it.mkdirs() }

    /**
     * Back up the original image bytes before an override edit.
     * If a backup already exists for this media, it is preserved (first edit's original).
     */
    suspend fun backupOriginal(mediaId: Long, uri: Uri, mimeType: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // If already backed up, keep the first original
                if (editHistoryDao.hasOriginalBackup(mediaId)) {
                    printDebug("Backup already exists for mediaId=$mediaId, keeping original")
                    // Update the edit timestamp
                    editHistoryDao.getEditedMedia(mediaId)?.let { existing ->
                        editHistoryDao.upsertEditedMedia(
                            existing.copy(editTimestamp = System.currentTimeMillis())
                        )
                    }
                    return@withContext true
                }

                val backupFile = File(backupDir, "$mediaId.bak")
                val bytesCopied = streamCopyFromUri(uri, backupFile)
                if (bytesCopied < 0) return@withContext false

                editHistoryDao.upsertEditedMedia(
                    EditedMedia(
                        mediaId = mediaId,
                        originalUri = uri,
                        backupPath = backupFile.absolutePath,
                        originalMimeType = mimeType,
                        editTimestamp = System.currentTimeMillis()
                    )
                )
                printDebug("Backed up original for mediaId=$mediaId ($bytesCopied bytes)")
                true
            } catch (e: Exception) {
                printError("Failed to backup original for mediaId=$mediaId: ${e.message}")
                false
            }
        }

    /**
     * Restore the original image from backup, writing it back to the media URI.
     * Returns the decoded original Bitmap on success, or null on failure.
     */
    suspend fun restoreOriginal(mediaId: Long): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val editedMedia = editHistoryDao.getEditedMedia(mediaId)
                ?: return@withContext null

            val backupFile = File(editedMedia.backupPath)
            if (!backupFile.exists()) {
                printError("Backup file not found: ${editedMedia.backupPath}")
                editHistoryDao.deleteEditedMedia(mediaId)
                return@withContext null
            }

            val bitmap = FileInputStream(backupFile).use { fis ->
                BitmapFactory.decodeStream(fis)
            } ?: return@withContext null

            bitmap
        } catch (e: Exception) {
            printError("Failed to restore original for mediaId=$mediaId: ${e.message}")
            null
        }
    }

    /**
     * Get an InputStream for the original backup file.
     * Caller is responsible for closing the stream.
     */
    suspend fun getOriginalStream(mediaId: Long): InputStream? = withContext(Dispatchers.IO) {
        val editedMedia = editHistoryDao.getEditedMedia(mediaId) ?: return@withContext null
        val backupFile = File(editedMedia.backupPath)
        if (!backupFile.exists()) return@withContext null
        FileInputStream(backupFile)
    }

    /**
     * Write original bytes back to a media URI and clean up the backup.
     */
    suspend fun revertToOriginal(mediaId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val editedMedia = editHistoryDao.getEditedMedia(mediaId)
                ?: return@withContext false

            val backupFile = File(editedMedia.backupPath)
            if (!backupFile.exists()) {
                editHistoryDao.deleteEditedMedia(mediaId)
                return@withContext false
            }

            // Stream-copy backup back to the media URI (constant memory)
            val outStream = context.contentResolver.openOutputStream(editedMedia.originalUri)
                ?: throw IOException("Failed to open output stream for ${editedMedia.originalUri}")
            FileInputStream(backupFile).use { fis ->
                outStream.use { out ->
                    fis.copyTo(out, bufferSize = STREAM_BUFFER_SIZE)
                }
            }

            // Update IS_PENDING to 0
            context.contentResolver.update(
                editedMedia.originalUri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null
            )

            // Clean up
            backupFile.delete()
            editHistoryDao.deleteEditedMedia(mediaId)
            printDebug("Reverted mediaId=$mediaId to original")
            true
        } catch (e: Exception) {
            printError("Failed to revert mediaId=$mediaId: ${e.message}")
            false
        }
    }

    /**
     * Delete a backup (e.g., when the media itself is deleted).
     */
    suspend fun deleteBackup(mediaId: Long) = withContext(Dispatchers.IO) {
        val editedMedia = editHistoryDao.getEditedMedia(mediaId) ?: return@withContext
        File(editedMedia.backupPath).delete()
        editHistoryDao.deleteEditedMedia(mediaId)
    }

    /**
     * Check if a media item has a recoverable original.
     */
    suspend fun hasOriginalBackup(mediaId: Long): Boolean =
        editHistoryDao.hasOriginalBackup(mediaId)

    /**
     * Flow-based check for UI reactivity.
     */
    fun hasOriginalBackupFlow(mediaId: Long): Flow<Boolean> =
        editHistoryDao.hasOriginalBackupFlow(mediaId)

    /**
     * Clean up orphaned backups (where the media no longer exists).
     */
    suspend fun cleanupOrphans(existingMediaIds: List<Long>) = withContext(Dispatchers.IO) {
        val allEdited = editHistoryDao.getAllEditedMedia()
        val orphans = allEdited.filter { it.mediaId !in existingMediaIds }
        orphans.forEach { File(it.backupPath).delete() }
        editHistoryDao.deleteOrphans(existingMediaIds)
    }

    /**
     * Data class describing a single backup entry for the UI.
     */
    data class BackupInfo(
        val mediaId: Long,
        val originalUri: Uri,
        val originalMimeType: String,
        val backupPath: String,
        val sizeBytes: Long,
        val editTimestamp: Long
    )

    /**
     * Returns total disk space used by all edit backups, in bytes.
     */
    suspend fun getTotalBackupSize(): Long = withContext(Dispatchers.IO) {
        val dir = backupDir
        if (!dir.exists()) return@withContext 0L
        dir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /**
     * Returns a list of all backup entries with file sizes.
     */
    suspend fun getAllBackupInfo(): List<BackupInfo> = withContext(Dispatchers.IO) {
        editHistoryDao.getAllEditedMedia().mapNotNull { edited ->
            val file = File(edited.backupPath)
            if (file.exists()) {
                BackupInfo(
                    mediaId = edited.mediaId,
                    originalUri = edited.originalUri,
                    originalMimeType = edited.originalMimeType,
                    backupPath = edited.backupPath,
                    sizeBytes = file.length(),
                    editTimestamp = edited.editTimestamp
                )
            } else null
        }
    }

    /**
     * Delete backups older than the given age in milliseconds.
     * Returns the number of backups deleted.
     */
    suspend fun deleteBackupsOlderThan(maxAgeMs: Long): Int = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        val allEdited = editHistoryDao.getAllEditedMedia()
        var count = 0
        allEdited.filter { it.editTimestamp < cutoff }.forEach { edited ->
            File(edited.backupPath).delete()
            editHistoryDao.deleteEditedMedia(edited.mediaId)
            count++
        }
        count
    }

    /**
     * Delete specific backups by media ID list.
     */
    suspend fun deleteBackups(mediaIds: List<Long>) = withContext(Dispatchers.IO) {
        mediaIds.forEach { id ->
            val edited = editHistoryDao.getEditedMedia(id) ?: return@forEach
            File(edited.backupPath).delete()
            editHistoryDao.deleteEditedMedia(id)
        }
    }

    /**
     * Delete all backups.
     */
    suspend fun deleteAllBackups() = withContext(Dispatchers.IO) {
        val allEdited = editHistoryDao.getAllEditedMedia()
        allEdited.forEach { File(it.backupPath).delete() }
        allEdited.forEach { editHistoryDao.deleteEditedMedia(it.mediaId) }
    }

    /**
     * Stream-copy content from a Uri to a File using a fixed-size buffer.
     * Returns the number of bytes copied, or -1 on failure.
     */
    private fun streamCopyFromUri(uri: Uri, destFile: File): Long {
        val inputStream: InputStream = context.contentResolver.openInputStream(uri)
            ?: return -1
        return inputStream.use { src ->
            destFile.outputStream().use { dst ->
                src.copyTo(dst, bufferSize = STREAM_BUFFER_SIZE)
            }
        }
    }

    companion object {
        private const val BACKUP_DIR_NAME = "edit_backups"
        private const val STREAM_BUFFER_SIZE = 8192
        const val AUTO_CLEANUP_AGE_MS = 90L * 24 * 60 * 60 * 1000 // 90 days
    }
}
