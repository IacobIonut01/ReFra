/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.data.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import androidx.room3.Upsert
import com.dot.gallery.domain.model.Category
import kotlinx.coroutines.flow.Flow

data class CategoryWithMediaCount(
    val id: Long,
    val name: String,
    val searchTerms: String,
    val embedding: FloatArray?,
    val referenceImageIds: List<Long>,
    val threshold: Float,
    val isUserCreated: Boolean,
    val isPinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val mediaCount: Int,
    /** User-picked cover when it is still a displayable member, else the best-scored member. */
    val thumbnailMediaId: Long?,
    /** Raw user-picked cover (may point at a removed photo; use [thumbnailMediaId] to display). */
    val coverMediaId: Long? = null,
) {
    fun toCategory() = Category(
        id = id,
        name = name,
        searchTerms = searchTerms,
        embedding = embedding,
        referenceImageIds = referenceImageIds,
        threshold = threshold,
        isUserCreated = isUserCreated,
        isPinned = isPinned,
        createdAt = createdAt,
        updatedAt = updatedAt,
        coverMediaId = coverMediaId,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CategoryWithMediaCount) return false

        if (id != other.id) return false
        if (name != other.name) return false
        if (searchTerms != other.searchTerms) return false
        if (embedding != null) {
            if (other.embedding == null) return false
            if (!embedding.contentEquals(other.embedding)) return false
        } else if (other.embedding != null) return false
        if (referenceImageIds != other.referenceImageIds) return false
        if (threshold != other.threshold) return false
        if (isUserCreated != other.isUserCreated) return false
        if (isPinned != other.isPinned) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false
        if (mediaCount != other.mediaCount) return false
        if (thumbnailMediaId != other.thumbnailMediaId) return false
        if (coverMediaId != other.coverMediaId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + searchTerms.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        result = 31 * result + referenceImageIds.hashCode()
        result = 31 * result + threshold.hashCode()
        result = 31 * result + isUserCreated.hashCode()
        result = 31 * result + isPinned.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + mediaCount
        result = 31 * result + (thumbnailMediaId?.hashCode() ?: 0)
        result = 31 * result + (coverMediaId?.hashCode() ?: 0)
        return result
    }
}
