/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.data.entity.PersonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {

    @Query("SELECT * FROM people ORDER BY faceCount DESC")
    fun getAll(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people WHERE providerType = :type ORDER BY faceCount DESC")
    fun getByProvider(type: ProviderType): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people WHERE providerType = :type AND hidden = 0 ORDER BY faceCount DESC")
    fun getVisibleByProvider(type: ProviderType): Flow<List<PersonEntity>>

    /**
     * Live count of hidden persons for a provider. The Library People section stays
     * reachable while this is non-zero even when every visible person is gone (#1262).
     */
    @Query("SELECT COUNT(*) FROM people WHERE providerType = :type AND hidden = 1")
    fun observeHiddenCount(type: ProviderType = ProviderType.LOCAL_PEOPLE): Flow<Int>

    @Query("SELECT * FROM people WHERE providerType = :type ORDER BY faceCount DESC")
    suspend fun getByProviderOnce(type: ProviderType): List<PersonEntity>

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun getById(id: String): PersonEntity?

    /** Reactive single-person lookup — includes hidden rows (unlike [getVisibleByProvider]). */
    @Query("SELECT * FROM people WHERE id = :id")
    fun observeById(id: String): Flow<PersonEntity?>

    @Upsert
    suspend fun insertAll(people: List<PersonEntity>)

    @Upsert
    suspend fun insert(person: PersonEntity)

    @Query("UPDATE people SET name = :name WHERE id = :id")
    suspend fun updateName(id: String, name: String)

    @Query("UPDATE people SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean)

    @Query("UPDATE people SET thumbnailMediaId = :mediaId, thumbnailUrl = :thumbnailUrl WHERE id = :id")
    suspend fun updateThumbnail(id: String, mediaId: Long?, thumbnailUrl: String?)

    @Query("UPDATE people SET faceCount = :faceCount, lastUpdated = :lastUpdated WHERE id = :id")
    suspend fun updateFaceCount(id: String, faceCount: Int, lastUpdated: Long)

    @Query("DELETE FROM people WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM people WHERE providerType = :type")
    suspend fun deleteByProvider(type: ProviderType)

    @Query("DELETE FROM people")
    suspend fun deleteAll()
}
