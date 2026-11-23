/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.data.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.dot.gallery.data.entity.MediaContentHashEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaContentHashDao {
    @Query("SELECT * FROM media_content_hash")
    suspend fun getAll(): List<MediaContentHashEntity>

    /** Rows whose full hash another row shares: the members of exact-duplicate sets. */
    @Query(
        """
        SELECT * FROM media_content_hash WHERE sha1 IN (
            SELECT sha1 FROM media_content_hash WHERE sha1 IS NOT NULL
            GROUP BY sha1 HAVING COUNT(*) > 1
        )
        """
    )
    fun observeDuplicates(): Flow<List<MediaContentHashEntity>>

    @Upsert
    suspend fun upsertAll(entries: List<MediaContentHashEntity>)

    @Query("DELETE FROM media_content_hash WHERE mediaId IN (:mediaIds)")
    suspend fun deleteByIds(mediaIds: List<Long>)
}
