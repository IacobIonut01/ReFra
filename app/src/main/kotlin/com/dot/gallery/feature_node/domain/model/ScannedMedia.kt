/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.feature_node.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scanned_media")
data class ScannedMedia(
    @PrimaryKey(autoGenerate = false)
    val id: Long
)
