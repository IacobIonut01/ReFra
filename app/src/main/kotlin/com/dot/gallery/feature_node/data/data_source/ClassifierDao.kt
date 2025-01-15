package com.dot.gallery.feature_node.data.data_source

import androidx.compose.ui.util.fastMap
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Media.ClassifiedMedia
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Dao
interface ClassifierDao {

    @Query("SELECT category FROM classified_media WHERE category IS NOT NULL AND category != 'null' ORDER BY timestamp DESC")
    suspend fun getCategories(): List<String>

    @Query("SELECT category FROM classified_media WHERE category IS NOT NULL AND category != 'null' ORDER BY timestamp DESC")
    fun getCategoriesFlow(): Flow<List<String>>

    @Query("SELECT * FROM classified_media WHERE category IS NOT NULL AND category != 'null'")
    suspend fun getClassifiedMedia(): List<ClassifiedMedia>

    @Query("SELECT * FROM classified_media")
    suspend fun getCheckedMedia(): List<ClassifiedMedia>

    @Query("SELECT COUNT(*) FROM classified_media WHERE category IS NOT NULL AND category != 'null'")
    fun getClassifiedMediaCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM classified_media WHERE category = :category AND category IS NOT NULL AND category != 'null'")
    fun getClassifiedMediaCountAtCategory(category: String): Flow<Int>

    @Query("SELECT * FROM classified_media WHERE category = :category AND category IS NOT NULL AND category != 'null' ORDER BY timestamp DESC LIMIT 1")
    fun getClassifiedMediaThumbnailByCategory(category: String): Flow<ClassifiedMedia?>

    @Query("SELECT * FROM classified_media WHERE category = :category AND category IS NOT NULL AND category != 'null' ORDER BY timestamp DESC")
    suspend fun getClassifiedMediaByCategory(category: String): List<ClassifiedMedia>

    @Query("SELECT * FROM classified_media WHERE category = :category AND category IS NOT NULL AND category != 'null' ORDER BY timestamp DESC")
    fun getClassifiedMediaByCategoryFlow(category: String): Flow<List<ClassifiedMedia>>

    @Query("SELECT category FROM classified_media WHERE category IS NOT NULL AND category != 'null' GROUP BY category ORDER BY COUNT(*) DESC LIMIT 1")
    suspend fun getCategoryWithMostClassifiedMedia(): String?

    @Query("SELECT * FROM classified_media WHERE category IN (SELECT category FROM classified_media WHERE category IS NOT NULL AND category != 'null' GROUP BY category ORDER BY COUNT(*) DESC LIMIT 3)")
    fun getClassifiedMediaByMostPopularCategoryFlow(): Flow<List<ClassifiedMedia>>

    @Query("SELECT * FROM classified_media WHERE id IN (SELECT MAX(id) FROM classified_media WHERE category IS NOT NULL AND category != 'null' GROUP BY category)")
    fun getCategoriesWithMedia(): Flow<List<ClassifiedMedia>>

    @Query("SELECT category FROM classified_media WHERE id = :mediaId AND category IS NOT NULL AND category != 'null'")
    suspend fun getCategoryForMediaId(mediaId: Long): String?

    @Upsert
    suspend fun insertClassifiedMedia(classifiedMedia: ClassifiedMedia)

    @Upsert
    suspend fun insertClassifiedMediaList(classifiedMediaList: List<ClassifiedMedia>)

    @Query("DELETE FROM classified_media WHERE category = :category")
    suspend fun deleteAllClassifiedMediaByCategory(category: String)

    @Query("DELETE FROM classified_media")
    suspend fun deleteAllClassifiedMedia()

    @Transaction
    suspend fun updateCategory(category: String, classifiedMediaList: List<ClassifiedMedia>) {
        // Upsert the items in mediaList
        insertClassifiedMediaList(classifiedMediaList)

        // Delete items from the database that are no longer classified
        deleteDeclassifiedImages(classifiedMediaList.map { it.id })
    }

    @Query("DELETE FROM classified_media WHERE id NOT IN (:mediaIds)")
    suspend fun deleteDeclassifiedImages(mediaIds: List<Long>)

    @Query("UPDATE classified_media SET category = :newCategory WHERE id = :mediaId")
    suspend fun changeCategory(mediaId: Long, newCategory: String)

    @Transaction
    suspend fun exportData(): String {
        val classifiedMedia = getClassifiedMedia()
        return Json.encodeToString(classifiedMedia)
    }

    @Transaction
    suspend fun importData(data: String, fullMediaList: List<Media.UriMedia>) {
        val classifiedMedia = Json.decodeFromString<List<ClassifiedMedia>>(data)
        insertClassifiedMediaList(classifiedMedia)

        // Delete items from the database that are no longer classified or no longer exist
        deleteDeclassifiedImages(fullMediaList.fastMap { it.id })
    }

}