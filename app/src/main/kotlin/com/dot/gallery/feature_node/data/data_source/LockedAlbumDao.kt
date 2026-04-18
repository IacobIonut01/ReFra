/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.data.data_source

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.dot.gallery.feature_node.domain.model.LockedAlbum
import kotlinx.coroutines.flow.Flow

@Dao
interface LockedAlbumDao {

    @Query("SELECT * FROM locked_table")
    fun getLockedAlbums(): Flow<List<LockedAlbum>>

    @Upsert
    suspend fun insertLockedAlbum(lockedAlbum: LockedAlbum)

    @Delete
    suspend fun removeLockedAlbum(lockedAlbum: LockedAlbum)

    @Query("SELECT EXISTS(SELECT * FROM locked_table WHERE id = :albumId)")
    fun albumIsLocked(albumId: Long): Boolean

    @Query("SELECT id FROM locked_table")
    suspend fun getLockedAlbumIds(): List<Long>

}
