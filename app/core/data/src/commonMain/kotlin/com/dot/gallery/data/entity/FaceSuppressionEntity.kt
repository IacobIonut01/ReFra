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


/**
 * "Never cluster this face into any person" — written when a person group is
 * deleted. Keyed by media + bounding box and matched to live
 * `detected_faces` rows by IoU, exactly like [FaceLinkEntity], so it survives
 * face re-detection and carries no person FK (the person is gone by design).
 */
@Entity(
    tableName = "face_suppressions",
    primaryKeys = ["mediaId", "left", "top", "right", "bottom"]
)
data class FaceSuppressionEntity(
    val mediaId: Long,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val createdAt: Long = 0L
)
