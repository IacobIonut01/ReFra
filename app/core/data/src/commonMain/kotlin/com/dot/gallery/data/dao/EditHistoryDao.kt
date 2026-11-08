/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.data.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.dot.gallery.domain.model.EditedMedia
import kotlinx.coroutines.flow.Flow


@Dao
interface EditHistoryDao {

    @Upsert
    suspend fun upsertEditedMedia(editedMedia: EditedMedia)

    @Query("SELECT * FROM edited_media WHERE mediaId = :mediaId LIMIT 1")
    suspend fun getEditedMedia(mediaId: Long): EditedMedia?

    @Query("SELECT * FROM edited_media WHERE mediaId = :mediaId LIMIT 1")
    fun getEditedMediaFlow(mediaId: Long): Flow<EditedMedia?>

    @Query("SELECT EXISTS(SELECT 1 FROM edited_media WHERE mediaId = :mediaId)")
    suspend fun hasOriginalBackup(mediaId: Long): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM edited_media WHERE mediaId = :mediaId)")
    fun hasOriginalBackupFlow(mediaId: Long): Flow<Boolean>

    @Query("DELETE FROM edited_media WHERE mediaId = :mediaId")
    suspend fun deleteEditedMedia(mediaId: Long)

    @Query("SELECT * FROM edited_media")
    suspend fun getAllEditedMedia(): List<EditedMedia>

    @Query("DELETE FROM edited_media WHERE mediaId NOT IN (:existingMediaIds)")
    suspend fun deleteOrphans(existingMediaIds: List<Long>)

    @Query(
        """
        SELECT * FROM edited_media
        WHERE NOT EXISTS (SELECT 1 FROM media WHERE media.id = edited_media.mediaId)
        """
    )
    suspend fun getOrphans(): List<EditedMedia>

    @Query("DELETE FROM edited_media WHERE mediaId = :mediaId")
    suspend fun deleteOrphan(mediaId: Long)
}
