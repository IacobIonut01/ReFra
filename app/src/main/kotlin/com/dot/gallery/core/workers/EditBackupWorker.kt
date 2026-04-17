/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dot.gallery.core.EditBackupManager
import com.dot.gallery.feature_node.presentation.util.printDebug
import com.dot.gallery.feature_node.presentation.util.printError
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * WorkManager-backed worker for heavy edit-backup I/O operations:
 *  - REVERT: stream-copy the original back to the media URI
 *  - AUTO_CLEANUP: delete backups older than 90 days
 *  - DELETE_SELECTED: delete specific backups by media IDs
 *  - DELETE_ALL: delete every backup
 */
@HiltWorker
class EditBackupWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val editBackupManager: EditBackupManager
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val op = inputData.getString(KEY_OPERATION)
            ?: return@withContext failure("Missing operation")

        try {
            when (op) {
                OP_REVERT -> {
                    val mediaId = inputData.getLong(KEY_MEDIA_ID, -1L)
                    if (mediaId == -1L) return@withContext failure("Missing media ID")
                    printDebug("EditBackupWorker: reverting mediaId=$mediaId")
                    val success = editBackupManager.revertToOriginal(mediaId)
                    if (success) {
                        success("Reverted mediaId=$mediaId")
                    } else {
                        failure("Revert failed for mediaId=$mediaId")
                    }
                }

                OP_AUTO_CLEANUP -> {
                    val maxAgeMs = inputData.getLong(
                        KEY_MAX_AGE_MS, EditBackupManager.AUTO_CLEANUP_AGE_MS
                    )
                    printDebug("EditBackupWorker: auto-cleanup older than ${maxAgeMs}ms")
                    val count = editBackupManager.deleteBackupsOlderThan(maxAgeMs)
                    success(
                        "Cleaned up $count backup(s)",
                        extra = workDataOf(KEY_DELETED_COUNT to count)
                    )
                }

                OP_DELETE_SELECTED -> {
                    val ids = inputData.getLongArray(KEY_MEDIA_IDS)
                        ?: return@withContext failure("Missing media IDs")
                    printDebug("EditBackupWorker: deleting ${ids.size} selected backup(s)")
                    editBackupManager.deleteBackups(ids.toList())
                    success("Deleted ${ids.size} backup(s)")
                }

                OP_DELETE_ALL -> {
                    printDebug("EditBackupWorker: deleting all backups")
                    editBackupManager.deleteAllBackups()
                    success("All backups deleted")
                }

                else -> failure("Unknown operation: $op")
            }
        } catch (e: Exception) {
            printError("EditBackupWorker failed ($op): ${e.message}")
            failure("Error: ${e.message}")
        }
    }

    private fun success(msg: String, extra: Data? = null): Result =
        Result.success(
            Data.Builder()
                .putBoolean(KEY_SUCCESS, true)
                .putString(KEY_MESSAGE, msg)
                .apply {
                    extra?.keyValueMap?.forEach { (k, v) ->
                        when (v) {
                            is String -> putString(k, v)
                            is Int -> putInt(k, v)
                            is Long -> putLong(k, v)
                            is Boolean -> putBoolean(k, v)
                        }
                    }
                }
                .build()
        )

    private fun failure(msg: String): Result =
        Result.failure(
            Data.Builder()
                .putBoolean(KEY_SUCCESS, false)
                .putString(KEY_MESSAGE, msg)
                .build()
        )

    companion object {
        const val TAG = "EditBackupWorker"

        const val KEY_OPERATION = "operation"
        const val KEY_MEDIA_ID = "media_id"
        const val KEY_MEDIA_IDS = "media_ids"
        const val KEY_MAX_AGE_MS = "max_age_ms"
        const val KEY_SUCCESS = "success"
        const val KEY_MESSAGE = "message"
        const val KEY_DELETED_COUNT = "deleted_count"

        const val OP_REVERT = "revert"
        const val OP_AUTO_CLEANUP = "auto_cleanup"
        const val OP_DELETE_SELECTED = "delete_selected"
        const val OP_DELETE_ALL = "delete_all"
    }
}

// ─── WorkManager extension helpers ───

fun WorkManager.revertEditBackup(mediaId: Long): UUID {
    val work = OneTimeWorkRequestBuilder<EditBackupWorker>()
        .addTag(EditBackupWorker.TAG)
        .setInputData(
            workDataOf(
                EditBackupWorker.KEY_OPERATION to EditBackupWorker.OP_REVERT,
                EditBackupWorker.KEY_MEDIA_ID to mediaId
            )
        )
        .build()
    enqueue(work)
    return work.id
}

fun WorkManager.autoCleanupEditBackups(
    maxAgeMs: Long = EditBackupManager.AUTO_CLEANUP_AGE_MS
): UUID {
    val work = OneTimeWorkRequestBuilder<EditBackupWorker>()
        .addTag(EditBackupWorker.TAG)
        .setInputData(
            workDataOf(
                EditBackupWorker.KEY_OPERATION to EditBackupWorker.OP_AUTO_CLEANUP,
                EditBackupWorker.KEY_MAX_AGE_MS to maxAgeMs
            )
        )
        .build()
    enqueue(work)
    return work.id
}

fun WorkManager.deleteSelectedEditBackups(mediaIds: LongArray): UUID {
    val work = OneTimeWorkRequestBuilder<EditBackupWorker>()
        .addTag(EditBackupWorker.TAG)
        .setInputData(
            workDataOf(
                EditBackupWorker.KEY_OPERATION to EditBackupWorker.OP_DELETE_SELECTED,
                EditBackupWorker.KEY_MEDIA_IDS to mediaIds
            )
        )
        .build()
    enqueue(work)
    return work.id
}

fun WorkManager.deleteAllEditBackups(): UUID {
    val work = OneTimeWorkRequestBuilder<EditBackupWorker>()
        .addTag(EditBackupWorker.TAG)
        .setInputData(
            workDataOf(
                EditBackupWorker.KEY_OPERATION to EditBackupWorker.OP_DELETE_ALL
            )
        )
        .build()
    enqueue(work)
    return work.id
}
