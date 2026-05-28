/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.data.data_source

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.dot.gallery.feature_node.domain.model.ScannedMedia

@Dao
interface ScannedMediaDao {

    @Query("SELECT id FROM scanned_media")
    suspend fun getScannedIds(): List<Long>

    @Upsert
    suspend fun insertAll(items: List<ScannedMedia>)

    @Query("DELETE FROM scanned_media WHERE id NOT IN (SELECT id FROM media)")
    suspend fun removeStaleEntries()

}
