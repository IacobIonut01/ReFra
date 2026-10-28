package com.dot.gallery.data.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.model.MediaVersion
import com.dot.gallery.domain.model.TimelineSettings
import com.dot.gallery.domain.model.AllowedMedia
import kotlinx.coroutines.flow.Flow



@Dao
interface MediaDao {

    /** UriMedia */
    @Query("SELECT * FROM media ORDER BY timestamp DESC")
    suspend fun getMedia(): List<UriMedia>

    @Query("SELECT * FROM media WHERE mimeType LIKE :allowedMedia ORDER BY timestamp DESC")
    suspend fun getMediaByType(allowedMedia: AllowedMedia): List<UriMedia>

    @Query("SELECT * FROM media WHERE favorite = 1 ORDER BY timestamp DESC")
    suspend fun getFavorites(): List<UriMedia>

    @Query("SELECT * FROM media WHERE id = :id LIMIT 1")
    suspend fun getMediaById(id: Long): UriMedia

    @Query("SELECT * FROM media WHERE albumID = :albumId ORDER BY timestamp DESC")
    suspend fun getMediaByAlbumId(albumId: Long): List<UriMedia>

    @Query("SELECT * FROM media WHERE albumID = :albumId AND mimeType LIKE :allowedMedia ORDER BY timestamp DESC")
    suspend fun getMediaByAlbumIdAndType(albumId: Long, allowedMedia: AllowedMedia): List<UriMedia>

    @Upsert(entity = UriMedia::class)
    suspend fun addMediaList(mediaList: List<UriMedia>)

    @Transaction
    suspend fun updateMedia(mediaList: List<UriMedia>) {
        if (mediaList.isEmpty()) {
            deleteAllMedia()
            return
        }

        // Upsert the items in mediaList
        addMediaList(mediaList)

        // Get the IDs of the media items in mediaList
        val mediaIds = mediaList.map { it.id }

        // Delete items from the database that are not in mediaList
        deleteMediaNotInList(mediaIds)
    }

    @Query("DELETE FROM media")
    suspend fun deleteAllMedia()

    @Query("DELETE FROM media WHERE id NOT IN (:mediaIds)")
    suspend fun deleteMediaNotInList(mediaIds: List<Long>)

    @Query("DELETE FROM media WHERE id IN (:mediaIds)")
    suspend fun deleteMediaByIds(mediaIds: List<Long>)

    /** MediaVersion */
    @Upsert(entity = MediaVersion::class)
    suspend fun setMediaVersion(version: MediaVersion)

    @Query("SELECT EXISTS(SELECT * FROM media_version WHERE version = :version) LIMIT 1")
    suspend fun isMediaVersionUpToDate(version: String): Boolean

    @Query("DELETE FROM media_version")
    suspend fun invalidateMediaVersion()

    /** Timeline Settings */
    @Query("SELECT * FROM timeline_settings LIMIT 1")
    fun getTimelineSettings(): Flow<TimelineSettings?>

    @Query("SELECT * FROM timeline_settings LIMIT 1")
    suspend fun getTimelineSettingsOnce(): TimelineSettings?

    @Upsert(entity = TimelineSettings::class)
    suspend fun setTimelineSettings(settings: TimelineSettings)

}