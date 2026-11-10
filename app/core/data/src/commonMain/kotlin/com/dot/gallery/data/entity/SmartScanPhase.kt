/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.data.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

enum class SmartScanPhase(val storedValue: String) {
    SOURCE_SYNC("source_sync"),
    METADATA("metadata"),
    SEARCH_INDEX("search_index"),
    CATEGORY_CLASSIFICATION("category_classification"),
    FACE_INDEX("face_index"),
    FACE_CLUSTER("face_cluster"),
    OCR_INDEX("ocr_index")
}
