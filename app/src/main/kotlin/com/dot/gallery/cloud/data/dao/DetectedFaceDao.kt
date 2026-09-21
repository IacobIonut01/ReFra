/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.dot.gallery.cloud.data.entity.DetectedFaceEntity
import com.dot.gallery.cloud.data.entity.FaceClusterEntity
import com.dot.gallery.cloud.data.entity.FaceExclusionEntity
import com.dot.gallery.cloud.data.entity.FaceLinkEntity
import com.dot.gallery.cloud.data.entity.FaceLinkKind
import kotlinx.coroutines.flow.Flow

data class DetectedFaceHeader(
    val mediaId: Long,
    val timestamp: Long,
    val resultRevision: String
)

data class DetectedFacePersonCount(
    val personId: String,
    val faceCount: Int
)

data class DetectedFacePhotoCount(
    val personId: String,
    val photoCount: Int
)

@Dao
interface DetectedFaceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(face: DetectedFaceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(faces: List<DetectedFaceEntity>)

    @Query("SELECT * FROM detected_faces WHERE personId = :personId ORDER BY confidence DESC")
    fun getByPerson(personId: String): Flow<List<DetectedFaceEntity>>

    @Query("SELECT DISTINCT mediaId FROM detected_faces WHERE personId = :personId")
    suspend fun getMediaIdsForPerson(personId: String): List<Long>

    @Query("SELECT DISTINCT mediaId FROM detected_faces WHERE personId = :personId")
    fun observeMediaIdsForPerson(personId: String): Flow<List<Long>>

    @Query("SELECT DISTINCT personId FROM detected_faces WHERE mediaId = :mediaId AND personId IS NOT NULL")
    fun observePersonIdsForMedia(mediaId: Long): Flow<List<String>>

    @Query("SELECT * FROM detected_faces WHERE mediaId = :mediaId")
    suspend fun getByMedia(mediaId: Long): List<DetectedFaceEntity>

    @Query("SELECT * FROM detected_faces")
    suspend fun getAll(): List<DetectedFaceEntity>

    @Query("SELECT mediaId, timestamp, resultRevision FROM detected_faces")
    suspend fun getHeaders(): List<DetectedFaceHeader>

    @Query("SELECT DISTINCT mediaId FROM detected_faces")
    suspend fun getIndexedMediaIds(): List<Long>

    @Query("SELECT * FROM face_clusters")
    suspend fun getClusters(): List<FaceClusterEntity>

    @Query("SELECT * FROM face_clusters")
    fun observeClusters(): Flow<List<FaceClusterEntity>>

    @Query("SELECT personId, COUNT(*) AS faceCount FROM detected_faces WHERE personId IS NOT NULL GROUP BY personId")
    suspend fun getPersonCounts(): List<DetectedFacePersonCount>

    @Query("SELECT personId, COUNT(DISTINCT mediaId) AS photoCount FROM detected_faces WHERE personId IS NOT NULL GROUP BY personId")
    fun observePersonPhotoCounts(): Flow<List<DetectedFacePhotoCount>>

    @Query("SELECT COUNT(DISTINCT mediaId) FROM detected_faces WHERE personId IS NOT NULL")
    fun observeIndexedPhotoCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT mediaId) FROM detected_faces WHERE personId = :personId")
    suspend fun countMediaForPerson(personId: String): Int

    @Upsert
    suspend fun upsertClusters(clusters: List<FaceClusterEntity>)

    @Query("DELETE FROM face_clusters")
    suspend fun deleteClusters()

    @Query("DELETE FROM face_clusters WHERE personId IN (:personIds)")
    suspend fun deleteClusters(personIds: List<String>)

    @Transaction
    suspend fun replaceClusters(clusters: List<FaceClusterEntity>) {
        deleteClusters()
        if (clusters.isNotEmpty()) upsertClusters(clusters)
    }

    @Query("UPDATE detected_faces SET resultRevision = :revision WHERE mediaId = :mediaId")
    suspend fun updateResultRevision(mediaId: Long, revision: String): Int

    @Query("SELECT COUNT(*) FROM detected_faces WHERE personId = :personId")
    suspend fun countForPerson(personId: String): Int

    @Query("UPDATE detected_faces SET personId = :newPersonId WHERE personId = :oldPersonId")
    suspend fun reassignPerson(oldPersonId: String, newPersonId: String)

    @Query("UPDATE detected_faces SET personId = :personId WHERE id = :faceId")
    suspend fun assignFace(faceId: Long, personId: String?)

    @Query("UPDATE detected_faces SET personId = :personId WHERE id IN (:faceIds)")
    suspend fun assignFaces(faceIds: List<Long>, personId: String?)

    @Query("SELECT * FROM detected_faces WHERE personId = :personId")
    suspend fun getByPersonOnce(personId: String): List<DetectedFaceEntity>

    @Query("UPDATE detected_faces SET personId = NULL WHERE personId = :personId AND mediaId IN (:mediaIds)")
    suspend fun unassignPersonMedia(personId: String, mediaIds: List<Long>): Int

    @Query("DELETE FROM detected_faces WHERE mediaId = :mediaId")
    suspend fun deleteByMedia(mediaId: Long)

    @Query(
        """
        SELECT DISTINCT personId FROM detected_faces
        WHERE personId IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM media WHERE media.id = detected_faces.mediaId)
          AND NOT EXISTS (SELECT 1 FROM cloud_media WHERE cloud_media.globalMediaId = detected_faces.mediaId)
        """
    )
    suspend fun getOrphanPersonIds(): List<String>

    @Query(
        """
        DELETE FROM detected_faces
        WHERE NOT EXISTS (SELECT 1 FROM media WHERE media.id = detected_faces.mediaId)
          AND NOT EXISTS (SELECT 1 FROM cloud_media WHERE cloud_media.globalMediaId = detected_faces.mediaId)
        """
    )
    suspend fun deleteOrphans(): Int

    @Query("DELETE FROM detected_faces")
    suspend fun deleteAll()

    // ── Face exclusions ("this media does not contain this person") ──

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExclusions(exclusions: List<FaceExclusionEntity>)

    @Query("SELECT * FROM face_exclusions")
    suspend fun getExclusions(): List<FaceExclusionEntity>

    @Query("DELETE FROM face_exclusions WHERE mediaId = :mediaId AND personId = :personId")
    suspend fun deleteExclusion(mediaId: Long, personId: String)

    @Query(
        """
        DELETE FROM face_exclusions
        WHERE NOT EXISTS (SELECT 1 FROM media WHERE media.id = face_exclusions.mediaId)
          AND NOT EXISTS (SELECT 1 FROM cloud_media WHERE cloud_media.globalMediaId = face_exclusions.mediaId)
        """
    )
    suspend fun deleteOrphanExclusions(): Int

    // ── Face links (durable user assertions feeding batch clustering) ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLinks(links: List<FaceLinkEntity>)

    @Query("SELECT * FROM face_links")
    suspend fun getLinks(): List<FaceLinkEntity>

    @Query("SELECT * FROM face_links")
    fun observeLinks(): Flow<List<FaceLinkEntity>>

    @Query("DELETE FROM face_links WHERE id IN (:ids)")
    suspend fun deleteLinksByIds(ids: List<Long>)

    @Query("UPDATE OR REPLACE face_links SET personId = :targetId WHERE personId = :sourceId AND kind = :kind")
    suspend fun reassignLinks(sourceId: String, targetId: String, kind: FaceLinkKind)

    @Query("DELETE FROM face_links WHERE personId = :personId")
    suspend fun deleteLinksForPerson(personId: String)

    @Query(
        """
        DELETE FROM face_links
        WHERE NOT EXISTS (SELECT 1 FROM media WHERE media.id = face_links.mediaId)
          AND NOT EXISTS (SELECT 1 FROM cloud_media WHERE cloud_media.globalMediaId = face_links.mediaId)
        """
    )
    suspend fun deleteOrphanLinks(): Int
}
