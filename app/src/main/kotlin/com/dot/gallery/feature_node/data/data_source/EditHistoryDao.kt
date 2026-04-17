/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.data.data_source

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.dot.gallery.feature_node.domain.model.EditedMedia
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
}
