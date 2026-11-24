/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.data.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Fts4
import androidx.room3.FtsOptions
import androidx.room3.PrimaryKey

/**
 * Full-text index over [OcrResultEntity.normalizedText]. `rowid` is the media id. [searchTokens]
 * holds overlapping bigrams for scripts written without spaces (CJK, Thai), which unicode61 cannot split.
 */
// Explicit Any::class: wasmJs KSP cannot read Fts4's default contentEntity value
// ("Cannot find external content entity class"); spelling it out keeps standalone FTS.
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61, contentEntity = Any::class)
@Entity(tableName = "ocr_search")
data class OcrSearchEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val mediaId: Long,
    val normalizedText: String,
    val searchTokens: String,
)
