/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.domain.model

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dot.gallery.feature_node.domain.util.UriSerializer
import kotlinx.serialization.Serializable

@Entity(tableName = "edited_media")
data class EditedMedia(
    @PrimaryKey
    val mediaId: Long,
    @Serializable(with = UriSerializer::class)
    val originalUri: Uri,
    val backupPath: String,
    val originalMimeType: String,
    val editTimestamp: Long = System.currentTimeMillis()
)
