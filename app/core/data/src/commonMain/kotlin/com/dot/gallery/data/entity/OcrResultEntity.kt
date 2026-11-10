/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.data.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Recognized text of one library photo (local or an indexed cloud provider). Vault media is never
 * stored here. [sourceRevision] identifies the file version, [resultRevision] the packs and models.
 */
@Entity(
    tableName = "ocr_results",
    indices = [Index(value = ["sourceType"]), Index(value = ["providerType", "serverConfigId"])],
)
data class OcrResultEntity(
    @PrimaryKey
    val mediaId: Long,
    @ColumnInfo(defaultValue = "'local'")
    val sourceType: String = SOURCE_LOCAL,
    @ColumnInfo(defaultValue = "NULL")
    val providerType: String? = null,
    @ColumnInfo(defaultValue = "NULL")
    val serverConfigId: Long? = null,
    @ColumnInfo(defaultValue = "NULL")
    val remoteId: String? = null,
    @ColumnInfo(defaultValue = "''")
    val sourceRevision: String = "",
    @ColumnInfo(defaultValue = "''")
    val resultRevision: String = "",
    @ColumnInfo(defaultValue = "''")
    val packIds: String = "",
    @ColumnInfo(defaultValue = "''")
    val fullText: String = "",
    @ColumnInfo(defaultValue = "''")
    val normalizedText: String = "",
    @ColumnInfo(defaultValue = "'{}'")
    val documentJson: String = "{}",
    @ColumnInfo(defaultValue = "1")
    val documentVersion: Int = 1,
    @ColumnInfo(defaultValue = "0")
    val averageConfidence: Float = 0f,
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = 0L,
) {
    companion object {
        const val SOURCE_LOCAL = "local"
        const val SOURCE_CLOUD = "cloud"
    }
}
