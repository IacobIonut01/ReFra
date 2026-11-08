/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.data.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.dot.gallery.domain.model.ImageEmbedding
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageEmbeddingDao {
    @Upsert
    suspend fun addImageEmbedding(imageEmbedding: ImageEmbedding)

    @Query("SELECT * FROM image_embeddings WHERE id = :id LIMIT 1")
    suspend fun getRecord(id: Long): ImageEmbedding?

    @Query("SELECT * FROM image_embeddings")
    fun getRecords(): Flow<List<ImageEmbedding>>

    @Query("SELECT * FROM image_embeddings WHERE id IN (:ids)")
    suspend fun getRecords(ids: List<Long>): List<ImageEmbedding>

    @Query("SELECT id, date, resultRevision, length(embedding) AS embeddingBytes FROM image_embeddings")
    suspend fun getHeaders(): List<ImageEmbeddingHeader>

    /**
     * Change feed without blob payloads — `length(embedding)` never decodes the
     * vector, so each emission stays cheap even at index scale.
     */
    @Query("SELECT id, date, resultRevision, length(embedding) AS embeddingBytes FROM image_embeddings")
    fun getHeaderFlow(): Flow<List<ImageEmbeddingHeader>>

    @Query("SELECT id FROM image_embeddings")
    suspend fun getIds(): List<Long>

    @Query("SELECT COUNT(*) FROM image_embeddings")
    suspend fun count(): Int

    /** Live number of indexed photos, for the Categories "not matched yet" stat. */
    @Query("SELECT COUNT(*) FROM image_embeddings")
    fun observeCount(): Flow<Int>

    @Query("UPDATE image_embeddings SET resultRevision = :revision WHERE id = :id")
    suspend fun updateResultRevision(id: Long, revision: String): Int

    @Query("UPDATE image_embeddings SET resultRevision = :revision WHERE id IN (:ids)")
    suspend fun updateResultRevisions(ids: List<Long>, revision: String): Int

    @Query(
        """
        DELETE FROM image_embeddings
        WHERE NOT EXISTS (SELECT 1 FROM media WHERE media.id = image_embeddings.id)
          AND NOT EXISTS (SELECT 1 FROM cloud_media WHERE cloud_media.globalMediaId = image_embeddings.id)
        """
    )
    suspend fun deleteOrphans(): Int
}
