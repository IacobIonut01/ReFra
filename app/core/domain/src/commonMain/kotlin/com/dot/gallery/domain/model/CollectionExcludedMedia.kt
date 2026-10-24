/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.domain.model

import androidx.compose.runtime.Immutable
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import kotlinx.serialization.Serializable

/**
 * A photo the user took out of a collection. Albums contribute their current
 * contents, so this row is what keeps a removed photo from coming back.
 */
@Entity(
    tableName = "collection_excluded_media",
    primaryKeys = ["collectionId", "mediaId"],
    foreignKeys = [
        ForeignKey(
            entity = Collection::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["collectionId"])
    ]
)
@Immutable
@Serializable
data class CollectionExcludedMedia(
    val collectionId: Long,
    val mediaId: Long
)
