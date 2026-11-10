/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.data.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.dot.gallery.data.entity.OcrResultEntity
import com.dot.gallery.data.entity.OcrSearchEntity
import kotlinx.coroutines.flow.Flow

/** Stored text of library photos and its full-text index. Vault media never reaches these tables. */
@Dao
interface OcrResultDao {
    @Upsert
    suspend fun upsertResult(result: OcrResultEntity)

    @Upsert
    suspend fun upsertSearch(search: OcrSearchEntity)

    @Query("SELECT * FROM ocr_results WHERE mediaId = :mediaId LIMIT 1")
    suspend fun get(mediaId: Long): OcrResultEntity?

    @Query("SELECT mediaId FROM ocr_results WHERE resultRevision = :resultRevision")
    suspend fun idsWithRevision(resultRevision: String): List<Long>

    @Query(
        "SELECT ocr_search.rowid FROM ocr_search WHERE ocr_search MATCH :match LIMIT :limit"
    )
    suspend fun matchIds(match: String, limit: Int): List<Long>

    @Query(
        "SELECT mediaId FROM ocr_results WHERE instr(normalizedText, :needle) > 0 ORDER BY updatedAt DESC LIMIT :limit"
    )
    suspend fun containingIds(needle: String, limit: Int): List<Long>

    @Query("SELECT * FROM ocr_results WHERE mediaId IN (:ids)")
    suspend fun results(ids: List<Long>): List<OcrResultEntity>

    @Query("DELETE FROM ocr_results WHERE mediaId = :mediaId")
    suspend fun deleteResult(mediaId: Long)

    @Query("DELETE FROM ocr_search WHERE rowid = :mediaId")
    suspend fun deleteSearch(mediaId: Long)

    @Query("DELETE FROM ocr_results")
    suspend fun deleteAllResults()

    @Query("DELETE FROM ocr_search")
    suspend fun deleteAllSearch()

    @Query("SELECT COUNT(*) FROM ocr_results")
    fun observeCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(length(CAST(fullText AS BLOB)) + length(CAST(documentJson AS BLOB)) + length(CAST(normalizedText AS BLOB))), 0) FROM ocr_results")
    fun observeBytes(): Flow<Long>
}
