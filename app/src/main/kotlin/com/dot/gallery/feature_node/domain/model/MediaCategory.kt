/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlinx.serialization.Serializable

/**
 * Junction table linking media items to categories with similarity scores.
 * This allows a media item to belong to multiple categories with different confidence levels.
 *
 * @param mediaId The ID of the media item
 * @param categoryId The ID of the category
 * @param similarityScore The cosine similarity score between the media embedding and category embedding
 * @param addedAt Timestamp when the media was added to this category
 * @param isManuallyAdded Whether the user manually added this media to the category
 */
@Entity(
    tableName = "media_category",
    primaryKeys = ["mediaId", "categoryId"],
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["mediaId"]),
        Index(value = ["similarityScore"])
    ]
)
@Serializable
data class MediaCategory(
    val mediaId: Long,
    val categoryId: Long,
    val similarityScore: Float,
    val addedAt: Long = System.currentTimeMillis(),
    val isManuallyAdded: Boolean = false
)

/**
 * Represents a category with its associated media count and thumbnail.
 * Used for displaying categories in the UI.
 */
data class CategoryWithCount(
    val category: Category,
    val mediaCount: Int,
    val thumbnailMediaId: Long?
)

/**
 * Represents the state of category classification.
 */
data class CategoryClassificationState(
    val isClassifying: Boolean = false,
    val progress: Float = 0f,
    val currentCategory: String? = null,
    val totalCategories: Int = 0,
    val processedCategories: Int = 0,
    val totalMedia: Int = 0,
    val processedMedia: Int = 0
)
