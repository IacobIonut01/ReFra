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

enum class SmartScanFeature(val bit: Int) {
    METADATA(1),
    EMBEDDINGS(1 shl 1),
    CATEGORIES(1 shl 2),
    PERSONS(1 shl 3),
    TEXT(1 shl 4);

    companion object {
        val ALL_MASK = entries.fold(0) { mask, feature -> mask or feature.bit }

        fun expandDependencies(mask: Int): Int =
            if (mask and CATEGORIES.bit != 0) mask or EMBEDDINGS.bit else mask
    }
}
