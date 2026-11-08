/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.data.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.data.entity.CloudAlbumSyncEntity
import kotlinx.coroutines.flow.Flow


@Dao
interface CloudAlbumSyncDao {

    @Query("SELECT * FROM cloud_album_sync")
    fun getAll(): Flow<List<CloudAlbumSyncEntity>>

    @Query("SELECT * FROM cloud_album_sync")
    suspend fun getAllAsync(): List<CloudAlbumSyncEntity>

    @Query("SELECT * FROM cloud_album_sync WHERE serverConfigId = :configId")
    fun getByServer(configId: Long): Flow<List<CloudAlbumSyncEntity>>

    @Query(
        "SELECT syncEnabled FROM cloud_album_sync " +
            "WHERE albumRemoteId = :albumId AND providerType = :providerType " +
            "AND serverConfigId = :configId"
    )
    suspend fun isSyncEnabled(
        albumId: String,
        providerType: ProviderType,
        configId: Long
    ): Boolean?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CloudAlbumSyncEntity)

    @Query(
        "UPDATE cloud_album_sync SET syncEnabled = :enabled " +
            "WHERE albumRemoteId = :albumId AND providerType = :providerType " +
            "AND serverConfigId = :configId"
    )
    suspend fun setSyncEnabled(
        albumId: String,
        providerType: ProviderType,
        configId: Long,
        enabled: Boolean
    )

    @Query("DELETE FROM cloud_album_sync WHERE serverConfigId = :configId")
    suspend fun deleteByServer(configId: Long)
}
