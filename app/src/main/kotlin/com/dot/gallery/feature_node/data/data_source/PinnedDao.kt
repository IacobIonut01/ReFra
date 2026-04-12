/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.data.data_source

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.dot.gallery.feature_node.domain.model.PinnedAlbum
import kotlinx.coroutines.flow.Flow

@Dao
interface PinnedDao {

    @Query("SELECT * FROM pinned_table")
    fun getPinnedAlbums(): Flow<List<PinnedAlbum>>

    @Upsert
    suspend fun insertPinnedAlbum(pinnedAlbum: PinnedAlbum)

    @Delete
    suspend fun removePinnedAlbum(pinnedAlbum: PinnedAlbum)

    @Query("SELECT EXISTS(SELECT * FROM pinned_table WHERE id = :albumId)")
    fun albumIsPinned(albumId: Long): Boolean

    @Query("SELECT id FROM pinned_table")
    suspend fun getPinnedAlbumIds(): List<Long>

}