/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.data.data_source

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.dot.gallery.feature_node.domain.model.Category
import com.dot.gallery.feature_node.domain.model.MediaCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    // ============ Category CRUD ============

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<Category>)

    @Update
    suspend fun updateCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)

    @Query("DELETE FROM categories WHERE id = :categoryId")
    suspend fun deleteCategoryById(categoryId: Long)

    @Query("SELECT * FROM categories WHERE id = :categoryId")
    suspend fun getCategoryById(categoryId: Long): Category?

    @Query("SELECT * FROM categories WHERE id = :categoryId")
    fun getCategoryByIdFlow(categoryId: Long): Flow<Category?>

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun getCategoryByName(name: String): Category?

    @Query("SELECT * FROM categories ORDER BY isPinned DESC, name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY isPinned DESC, name ASC")
    suspend fun getAllCategoriesAsync(): List<Category>

    @Query("SELECT * FROM categories WHERE isUserCreated = 1 ORDER BY name ASC")
    fun getUserCreatedCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE isUserCreated = 0 ORDER BY name ASC")
    fun getSystemCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE isPinned = 1 ORDER BY name ASC")
    fun getPinnedCategories(): Flow<List<Category>>

    @Query("UPDATE categories SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :categoryId")
    suspend fun updateCategoryPinned(categoryId: Long, isPinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE categories SET name = :name, updatedAt = :updatedAt WHERE id = :categoryId")
    suspend fun updateCategoryName(categoryId: Long, name: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE categories SET threshold = :threshold, updatedAt = :updatedAt WHERE id = :categoryId")
    suspend fun updateCategoryThreshold(categoryId: Long, threshold: Float, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM categories")
    fun getCategoryCount(): Flow<Int>

    // ============ MediaCategory operations ============

    @Upsert
    suspend fun insertMediaCategory(mediaCategory: MediaCategory)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaCategories(mediaCategories: List<MediaCategory>)

    @Query("DELETE FROM media_category WHERE mediaId = :mediaId AND categoryId = :categoryId")
    suspend fun removeMediaFromCategory(mediaId: Long, categoryId: Long)

    @Query("DELETE FROM media_category WHERE categoryId = :categoryId")
    suspend fun removeAllMediaFromCategory(categoryId: Long)

    @Query("DELETE FROM media_category WHERE mediaId = :mediaId")
    suspend fun removeMediaFromAllCategories(mediaId: Long)

    @Query("DELETE FROM media_category WHERE mediaId NOT IN (:validMediaIds)")
    suspend fun cleanupOrphanedMediaCategories(validMediaIds: List<Long>)

    // Get all media IDs in a category, ordered by similarity score
    @Query("""
        SELECT mediaId FROM media_category 
        WHERE categoryId = :categoryId 
        ORDER BY similarityScore DESC
    """)
    fun getMediaIdsInCategory(categoryId: Long): Flow<List<Long>>

    @Query("""
        SELECT mediaId FROM media_category 
        WHERE categoryId = :categoryId 
        ORDER BY similarityScore DESC
    """)
    suspend fun getMediaIdsInCategoryAsync(categoryId: Long): List<Long>

    // Get media count for a category
    @Query("SELECT COUNT(*) FROM media_category WHERE categoryId = :categoryId")
    fun getMediaCountInCategory(categoryId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_category WHERE categoryId = :categoryId")
    suspend fun getMediaCountInCategoryAsync(categoryId: Long): Int

    // Get all categories for a media item
    @Query("""
        SELECT c.* FROM categories c
        INNER JOIN media_category mc ON c.id = mc.categoryId
        WHERE mc.mediaId = :mediaId
        ORDER BY mc.similarityScore DESC
    """)
    fun getCategoriesForMedia(mediaId: Long): Flow<List<Category>>

    @Query("""
        SELECT c.* FROM categories c
        INNER JOIN media_category mc ON c.id = mc.categoryId
        WHERE mc.mediaId = :mediaId
        ORDER BY mc.similarityScore DESC
    """)
    suspend fun getCategoriesForMediaAsync(mediaId: Long): List<Category>

    // Get thumbnail media ID for a category (highest similarity score)
    @Query("""
        SELECT mediaId FROM media_category 
        WHERE categoryId = :categoryId 
        ORDER BY similarityScore DESC 
        LIMIT 1
    """)
    fun getThumbnailMediaIdForCategory(categoryId: Long): Flow<Long?>

    @Query("""
        SELECT mediaId FROM media_category 
        WHERE categoryId = :categoryId 
        ORDER BY similarityScore DESC 
        LIMIT 1
    """)
    suspend fun getThumbnailMediaIdForCategoryAsync(categoryId: Long): Long?

    // Get similarity score for a specific media-category pair
    @Query("SELECT similarityScore FROM media_category WHERE mediaId = :mediaId AND categoryId = :categoryId")
    suspend fun getSimilarityScore(mediaId: Long, categoryId: Long): Float?

    // Check if a media item is in a category
    @Query("SELECT EXISTS(SELECT 1 FROM media_category WHERE mediaId = :mediaId AND categoryId = :categoryId)")
    suspend fun isMediaInCategory(mediaId: Long, categoryId: Long): Boolean

    // Get all media that are already classified (have embeddings processed)
    @Query("SELECT DISTINCT mediaId FROM media_category")
    suspend fun getAllClassifiedMediaIds(): List<Long>

    // Get categories with media count - for displaying in the grid
    @Query("""
        SELECT c.*, COUNT(mc.mediaId) as mediaCount, 
               (SELECT mc2.mediaId FROM media_category mc2 
                WHERE mc2.categoryId = c.id 
                ORDER BY mc2.similarityScore DESC LIMIT 1) as thumbnailMediaId
        FROM categories c
        LEFT JOIN media_category mc ON c.id = mc.categoryId
        GROUP BY c.id
        HAVING mediaCount > 0
        ORDER BY c.isPinned DESC, c.name ASC
    """)
    fun getCategoriesWithMediaCount(): Flow<List<CategoryWithMediaCount>>

    // Batch update for reclassification
    @Transaction
    suspend fun reclassifyMediaForCategory(categoryId: Long, mediaCategories: List<MediaCategory>) {
        removeAllMediaFromCategory(categoryId)
        insertMediaCategories(mediaCategories)
    }

    // Delete all data (for reset)
    @Query("DELETE FROM media_category")
    suspend fun deleteAllMediaCategories()

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    @Transaction
    suspend fun resetAllCategoryData() {
        deleteAllMediaCategories()
        deleteAllCategories()
    }

    // Get the top N categories by media count for carousel display
    @Query("""
        SELECT c.*, COUNT(mc.mediaId) as mediaCount,
               (SELECT mc2.mediaId FROM media_category mc2 
                WHERE mc2.categoryId = c.id 
                ORDER BY mc2.similarityScore DESC LIMIT 1) as thumbnailMediaId
        FROM categories c
        INNER JOIN media_category mc ON c.id = mc.categoryId
        GROUP BY c.id
        ORDER BY mediaCount DESC
        LIMIT :limit
    """)
    fun getTopCategoriesByMediaCount(limit: Int = 10): Flow<List<CategoryWithMediaCount>>
}

/**
 * Helper class for queries that return category with media count
 */
data class CategoryWithMediaCount(
    val id: Long,
    val name: String,
    val searchTerms: String,
    val embedding: FloatArray?,
    val threshold: Float,
    val isUserCreated: Boolean,
    val isPinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val mediaCount: Int,
    val thumbnailMediaId: Long?
) {
    fun toCategory() = Category(
        id = id,
        name = name,
        searchTerms = searchTerms,
        embedding = embedding,
        threshold = threshold,
        isUserCreated = isUserCreated,
        isPinned = isPinned,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CategoryWithMediaCount

        if (id != other.id) return false
        if (name != other.name) return false
        if (searchTerms != other.searchTerms) return false
        if (embedding != null) {
            if (other.embedding == null) return false
            if (!embedding.contentEquals(other.embedding)) return false
        } else if (other.embedding != null) return false
        if (threshold != other.threshold) return false
        if (isUserCreated != other.isUserCreated) return false
        if (isPinned != other.isPinned) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false
        if (mediaCount != other.mediaCount) return false
        if (thumbnailMediaId != other.thumbnailMediaId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + searchTerms.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        result = 31 * result + threshold.hashCode()
        result = 31 * result + isUserCreated.hashCode()
        result = 31 * result + isPinned.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + mediaCount
        result = 31 * result + (thumbnailMediaId?.hashCode() ?: 0)
        return result
    }
}
