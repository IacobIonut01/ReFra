/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.data.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Query
import androidx.room3.Upsert
import com.dot.gallery.domain.model.MergedSubfolderAlbum
import kotlinx.coroutines.flow.Flow


@Dao
interface MergedSubfolderDao {

    @Query("SELECT * FROM merged_subfolder_table")
    fun getMergedSubfolderAlbums(): Flow<List<MergedSubfolderAlbum>>

    @Query("SELECT * FROM merged_subfolder_table")
    suspend fun getAllAsync(): List<MergedSubfolderAlbum>

    @Upsert
    suspend fun insertMergedSubfolderAlbum(mergedSubfolderAlbum: MergedSubfolderAlbum)

    @Delete
    suspend fun removeMergedSubfolderAlbum(mergedSubfolderAlbum: MergedSubfolderAlbum)

    @Query("SELECT id FROM merged_subfolder_table")
    suspend fun getMergedSubfolderAlbumIds(): List<Long>

    @Query("UPDATE merged_subfolder_table SET displayMode = :displayMode WHERE id = :id")
    suspend fun updateDisplayMode(id: Long, displayMode: String)

}
