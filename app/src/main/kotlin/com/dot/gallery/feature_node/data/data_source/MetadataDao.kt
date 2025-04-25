package com.dot.gallery.feature_node.data.data_source

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.dot.gallery.feature_node.domain.model.FullMediaMetadata
import com.dot.gallery.feature_node.domain.model.MediaMetadata
import com.dot.gallery.feature_node.domain.model.MediaMetadataCore
import com.dot.gallery.feature_node.domain.model.MediaMetadataFlags
import com.dot.gallery.feature_node.domain.model.MediaMetadataVideo
import com.dot.gallery.feature_node.domain.model.toCore
import com.dot.gallery.feature_node.domain.model.toFlags
import com.dot.gallery.feature_node.domain.model.toVideo
import kotlinx.coroutines.flow.Flow

@Dao
interface MetadataDao {

    @Transaction
    fun addMetadata(mediaMetadata: MediaMetadata) {
        upsertCore(mediaMetadata.toCore())
        upsertVideo(mediaMetadata.toVideo())
        upsertFlags(mediaMetadata.toFlags())
    }

    @Upsert fun upsertCore(core: MediaMetadataCore)
    @Upsert fun upsertVideo(video: MediaMetadataVideo)
    @Upsert fun upsertFlags(flags: MediaMetadataFlags)

    @Transaction
    suspend fun deleteForgottenMetadata(ids: List<Long>) {
        deleteOrphansCore(ids)
        deleteOrphansVideo(ids)
        deleteOrphansFlags(ids)
    }

    @Query("DELETE FROM media_metadata_core WHERE mediaId NOT IN (:ids)")
    suspend fun deleteOrphansCore(ids: List<Long>)

    @Query("DELETE FROM media_metadata_video WHERE mediaId NOT IN (:ids)")
    suspend fun deleteOrphansVideo(ids: List<Long>)

    @Query("DELETE FROM media_metadata_flags WHERE mediaId NOT IN (:ids)")
    suspend fun deleteOrphansFlags(ids: List<Long>)

    @Transaction
    @Query("SELECT * FROM media_metadata_core")
    fun getFullMetadata(): Flow<List<FullMediaMetadata>>

    @Transaction
    @Query("SELECT * FROM media_metadata_core WHERE mediaId = :id")
    fun getFullMetadata(id: Long): Flow<FullMediaMetadata>
}
