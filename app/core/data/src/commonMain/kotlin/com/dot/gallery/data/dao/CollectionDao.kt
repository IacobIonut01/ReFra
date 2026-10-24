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
import com.dot.gallery.domain.model.Collection
import com.dot.gallery.domain.model.CollectionAlbum
import com.dot.gallery.domain.model.CollectionExcludedMedia
import com.dot.gallery.domain.model.CollectionMedia
import kotlinx.coroutines.flow.Flow


@Dao
interface CollectionDao {

    // ============ Collection CRUD ============

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: Collection): Long

    @Update
    suspend fun updateCollection(collection: Collection)

    @Query("DELETE FROM collections WHERE id = :collectionId")
    suspend fun deleteCollection(collectionId: Long)

    @Query("SELECT * FROM collections WHERE id = :collectionId")
    fun getCollectionFlow(collectionId: Long): Flow<Collection?>

    @Query("SELECT * FROM collections WHERE id = :collectionId")
    suspend fun getCollectionAsync(collectionId: Long): Collection?

    @Query("SELECT * FROM collections ORDER BY isPinned DESC, sortOrder ASC, updatedAt DESC")
    fun getAllCollections(): Flow<List<Collection>>

    @Query("SELECT * FROM collections ORDER BY isPinned DESC, sortOrder ASC, updatedAt DESC")
    suspend fun getAllCollectionsAsync(): List<Collection>

    @Query("UPDATE collections SET label = :label, updatedAt = :updatedAt WHERE id = :collectionId")
    suspend fun updateCollectionLabel(collectionId: Long, label: String, updatedAt: Long = kotlin.time.Clock.System.now().toEpochMilliseconds())

    @Query("UPDATE collections SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :collectionId")
    suspend fun updateCollectionPinned(collectionId: Long, isPinned: Boolean, updatedAt: Long = kotlin.time.Clock.System.now().toEpochMilliseconds())

    @Query("UPDATE collections SET coverMediaId = :mediaId, updatedAt = :updatedAt WHERE id = :collectionId")
    suspend fun updateCollectionCover(collectionId: Long, mediaId: Long?, updatedAt: Long = kotlin.time.Clock.System.now().toEpochMilliseconds())

    @Query("SELECT COUNT(*) FROM collections")
    fun getCollectionCount(): Flow<Int>

    // ============ CollectionMedia operations ============

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMediaToCollection(collectionMedia: CollectionMedia)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMediaListToCollection(collectionMedia: List<CollectionMedia>)

    @Query("DELETE FROM collection_media WHERE collectionId = :collectionId AND mediaId = :mediaId")
    suspend fun removeMediaFromCollection(collectionId: Long, mediaId: Long)

    @Query("DELETE FROM collection_media WHERE collectionId = :collectionId")
    suspend fun removeAllMediaFromCollection(collectionId: Long)

    @Query("DELETE FROM collection_media WHERE mediaId NOT IN (:validMediaIds)")
    suspend fun cleanupOrphanedCollectionMedia(validMediaIds: List<Long>)

    @Query("SELECT mediaId FROM collection_media WHERE collectionId = :collectionId ORDER BY addedAt DESC")
    fun getMediaIdsInCollection(collectionId: Long): Flow<List<Long>>

    @Query("SELECT mediaId FROM collection_media WHERE collectionId = :collectionId ORDER BY addedAt DESC")
    suspend fun getMediaIdsInCollectionAsync(collectionId: Long): List<Long>

    @Query(COLLECTION_MEMBER_COUNT)
    fun getMediaCountInCollection(collectionId: Long): Flow<Int>

    @Query(COLLECTION_MEMBER_COUNT)
    suspend fun getMediaCountInCollectionAsync(collectionId: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM collection_media WHERE collectionId = :collectionId AND mediaId = :mediaId)")
    suspend fun isMediaInCollection(collectionId: Long, mediaId: Long): Boolean

    @Query("SELECT collectionId FROM collection_media WHERE mediaId = :mediaId")
    fun getCollectionIdsForMedia(mediaId: Long): Flow<List<Long>>

    // Get the thumbnail media id: use coverMediaId if set, otherwise most recently added media
    @Query("""
        SELECT COALESCE(
            c.coverMediaId,
            (SELECT cm.mediaId FROM collection_media cm WHERE cm.collectionId = c.id ORDER BY cm.addedAt DESC LIMIT 1)
        )
        FROM collections c WHERE c.id = :collectionId
    """)
    fun getThumbnailMediaId(collectionId: Long): Flow<Long?>

    // ============ CollectionAlbum operations ============

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addAlbumToCollection(collectionAlbum: CollectionAlbum)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addAlbumsToCollection(collectionAlbums: List<CollectionAlbum>)

    @Query("DELETE FROM collection_albums WHERE collectionId = :collectionId AND albumId = :albumId")
    suspend fun removeAlbumFromCollection(collectionId: Long, albumId: Long)

    @Query("DELETE FROM collection_albums WHERE collectionId = :collectionId")
    suspend fun removeAllAlbumsFromCollection(collectionId: Long)

    @Query("SELECT albumId FROM collection_albums WHERE collectionId = :collectionId")
    suspend fun getAlbumIdsInCollectionAsync(collectionId: Long): List<Long>

    @Query("DELETE FROM collection_media WHERE collectionId = :collectionId AND mediaId IN (SELECT id FROM media WHERE albumID IN (:albumIds))")
    suspend fun removeAlbumMediaFromCollection(collectionId: Long, albumIds: List<Long>)

    @Transaction
    suspend fun replaceAlbumsInCollection(collectionId: Long, albumIds: List<Long>) {
        val removedAlbumIds = getAlbumIdsInCollectionAsync(collectionId).filterNot(albumIds::contains)
        if (removedAlbumIds.isNotEmpty()) {
            removeAlbumMediaFromCollection(collectionId, removedAlbumIds)
        }
        removeAllAlbumsFromCollection(collectionId)
        if (albumIds.isNotEmpty()) {
            addAlbumsToCollection(albumIds.map { CollectionAlbum(collectionId, it) })
        }
    }

    @Query("SELECT DISTINCT albumId FROM collection_albums")
    fun getAllAlbumIdsInCollections(): Flow<List<Long>>

    @Query("SELECT albumId FROM collection_albums WHERE collectionId = :collectionId")
    fun getAlbumIdsInCollection(collectionId: Long): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun excludeMedia(excluded: CollectionExcludedMedia)

    @Query(
        "DELETE FROM collection_excluded_media " +
            "WHERE collectionId = :collectionId AND mediaId IN (:mediaIds)"
    )
    suspend fun clearExcludedMedia(collectionId: Long, mediaIds: List<Long>)

    @Query(
        "SELECT mediaId FROM collection_excluded_media WHERE collectionId = :collectionId"
    )
    fun getExcludedMediaIds(collectionId: Long): Flow<List<Long>>

    // Collections with count for UI display
    @Query(COLLECTIONS_WITH_COUNT)
    fun getCollectionsWithCount(): Flow<List<CollectionWithMediaCount>>
}

private const val COLLECTION_MEMBER_IDS = """
    SELECT members.id FROM (
        SELECT cm.mediaId AS id FROM collection_media cm WHERE cm.collectionId = :collectionId
        UNION
        SELECT m.id AS id FROM media m
        INNER JOIN collection_albums ca
            ON ca.collectionId = :collectionId AND m.albumID = ca.albumId
    ) AS members
    WHERE members.id NOT IN (
        SELECT e.mediaId FROM collection_excluded_media e WHERE e.collectionId = :collectionId
    )
"""

private const val COLLECTION_MEMBER_COUNT = """
    SELECT COUNT(*) FROM (
        $COLLECTION_MEMBER_IDS
    )
"""

private const val COLLECTIONS_WITH_COUNT = """
    SELECT c.*,
           (
               SELECT COUNT(*) FROM (
                   SELECT members.id FROM (
                       SELECT cm.mediaId AS id FROM collection_media cm WHERE cm.collectionId = c.id
                       UNION
                       SELECT m.id AS id FROM media m
                       INNER JOIN collection_albums ca
                           ON ca.collectionId = c.id AND m.albumID = ca.albumId
                   ) AS members
                   WHERE members.id NOT IN (
                       SELECT e.mediaId FROM collection_excluded_media e
                       WHERE e.collectionId = c.id
                   )
               )
           ) AS mediaCount,
           COALESCE(
               c.coverMediaId,
               (
                   SELECT cm2.mediaId FROM collection_media cm2
                   WHERE cm2.collectionId = c.id ORDER BY cm2.addedAt DESC LIMIT 1
               )
           ) AS thumbnailMediaId,
           COALESCE(
               (
                   SELECT SUM(sized.size) FROM media sized
                   WHERE sized.id IN (
                       SELECT members.id FROM (
                           SELECT cm3.mediaId AS id FROM collection_media cm3
                           WHERE cm3.collectionId = c.id
                           UNION
                           SELECT m2.id AS id FROM media m2
                           INNER JOIN collection_albums ca2
                               ON ca2.collectionId = c.id AND m2.albumID = ca2.albumId
                       ) AS members
                       WHERE members.id NOT IN (
                           SELECT e2.mediaId FROM collection_excluded_media e2
                           WHERE e2.collectionId = c.id
                       )
                   )
               ),
               0
           ) AS totalSize
    FROM collections c
    ORDER BY c.isPinned DESC, c.sortOrder ASC, c.updatedAt DESC
"""

/**
 * Helper class for queries that return collection with media count
 */
