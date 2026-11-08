/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.data.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.dot.gallery.data.entity.CloudUploadPrefEntity
import kotlinx.coroutines.flow.Flow


@Dao
interface CloudUploadPrefDao {

    @Query("SELECT * FROM cloud_upload_pref")
    fun getAll(): Flow<List<CloudUploadPrefEntity>>

    @Query("SELECT * FROM cloud_upload_pref WHERE uploadEnabled = 1")
    fun getEnabled(): Flow<List<CloudUploadPrefEntity>>

    @Query("SELECT * FROM cloud_upload_pref WHERE uploadEnabled = 1")
    suspend fun getEnabledList(): List<CloudUploadPrefEntity>

    @Query("SELECT * FROM cloud_upload_pref WHERE serverConfigId = :configId")
    fun getByConfig(configId: Long): Flow<List<CloudUploadPrefEntity>>

    @Query("SELECT * FROM cloud_upload_pref WHERE serverConfigId = :configId AND uploadEnabled = 1")
    fun getEnabledByConfig(configId: Long): Flow<List<CloudUploadPrefEntity>>

    @Query("SELECT * FROM cloud_upload_pref WHERE serverConfigId = :configId AND uploadEnabled = 1")
    suspend fun getEnabledByConfigList(configId: Long): List<CloudUploadPrefEntity>

    @Query("SELECT * FROM cloud_upload_pref WHERE serverConfigId = :configId AND albumId = :albumId")
    suspend fun get(configId: Long, albumId: Long): CloudUploadPrefEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CloudUploadPrefEntity)

    @Query("DELETE FROM cloud_upload_pref WHERE serverConfigId = :configId AND albumId = :albumId")
    suspend fun delete(configId: Long, albumId: Long)

    @Query("DELETE FROM cloud_upload_pref WHERE serverConfigId = :configId")
    suspend fun deleteByConfig(configId: Long)
}
