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
import androidx.room.Update
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.SyncState
import com.dot.gallery.cloud.data.entity.CloudBackupRevisionEntity
import com.dot.gallery.cloud.data.entity.CloudMediaEntity
import com.dot.gallery.cloud.data.entity.backupFingerprint
import com.dot.gallery.cloud.data.entity.canonicalBackupChecksum
import kotlinx.coroutines.flow.Flow

data class CloudMediaLocalState(
    val remoteId: String,
    val providerType: ProviderType,
    val serverConfigId: Long,
    val localCopyPath: String,
    val size: Long,
    val timestamp: Long,
    val syncState: SyncState,
    val appLocalCopy: Boolean = false
)

data class CloudMediaRemoteRevision(
    val fileId: String,
    val label: String,
    val mimeType: String,
    val size: Long,
    val lastSyncedAt: Long
)

data class CloudMediaSmartFeatureRevision(
    val remoteId: String,
    val path: String,
    val label: String,
    val mimeType: String,
    val timestamp: Long,
    val size: Long
)

/** Result of [CloudMediaDao.getSyncStateStats] — item count + total byte size. */
data class PendingDownloadInfo(
    val itemCount: Int,
    val totalBytes: Long
)

internal fun shouldInvalidateBackupRevision(
    incomingFingerprint: String,
    incomingHasContentHash: Boolean,
    storedFingerprint: String
): Boolean {
    val canonicalStored = canonicalBackupChecksum(storedFingerprint)
    val storedHasContentHash = canonicalStored.length == 40 && canonicalStored.all {
        it in '0'..'9' || it in 'a'..'f'
    }
    return (!storedHasContentHash || incomingHasContentHash) && incomingFingerprint != canonicalStored
}

@Dao
interface CloudMediaDao {

    @Query("SELECT * FROM cloud_media WHERE providerType = :providerType ORDER BY timestamp DESC")
    fun getByProvider(providerType: ProviderType): Flow<List<CloudMediaEntity>>

    @Query("SELECT * FROM cloud_media WHERE serverConfigId = :configId ORDER BY timestamp DESC")
    fun getByServerConfig(configId: Long): Flow<List<CloudMediaEntity>>

    @Query("SELECT * FROM cloud_backup_revision WHERE serverConfigId = :configId")
    suspend fun getBackupRevisions(configId: Long): List<CloudBackupRevisionEntity>

    @Query(
        """
        SELECT * FROM cloud_backup_revision
        WHERE serverConfigId = :configId AND verifiedAt >= :verifiedAfter
        """
    )
    suspend fun getValidBackupRevisions(
        configId: Long,
        verifiedAfter: Long
    ): List<CloudBackupRevisionEntity>

    @Query(
        """
        SELECT * FROM cloud_backup_revision
        WHERE serverConfigId = :configId AND remoteId IN (:remoteIds)
        """
    )
    suspend fun getBackupRevisions(configId: Long, remoteIds: List<String>): List<CloudBackupRevisionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBackupRevision(revision: CloudBackupRevisionEntity)

    @Query(
        """
        DELETE FROM cloud_backup_revision
        WHERE serverConfigId = :configId AND localUri = :localUri
        """
    )
    suspend fun deleteBackupRevision(configId: Long, localUri: String)

    @Query("DELETE FROM cloud_backup_revision WHERE serverConfigId = :configId")
    suspend fun deleteBackupRevisions(configId: Long)

    @Query("DELETE FROM cloud_backup_revision")
    suspend fun deleteAllBackupRevisions()

    @Query("DELETE FROM cloud_backup_revision WHERE providerType = :providerType")
    suspend fun deleteBackupRevisions(providerType: ProviderType)

    @Query(
        """
        DELETE FROM cloud_backup_revision
        WHERE serverConfigId = :configId AND remoteId = :remoteId
        """
    )
    suspend fun deleteBackupRevisions(configId: Long, remoteId: String)

    @Query(
        """
        SELECT fileId, label, mimeType, size, lastSyncedAt FROM cloud_media
        WHERE serverConfigId = :configId AND fileId != '' AND lastSyncedAt > 0
            AND trashed = 0
        """
    )
    suspend fun getRemoteRevisions(configId: Long): List<CloudMediaRemoteRevision>

    @Query(
        """
        SELECT remoteId, path, label, mimeType, timestamp, size FROM cloud_media
        WHERE serverConfigId = :configId AND trashed = 0 AND archived = 0
        ORDER BY remoteId
        """
    )
    suspend fun getSmartFeatureRevisions(configId: Long): List<CloudMediaSmartFeatureRevision>

    @Query(
        """
        SELECT remoteId, providerType, serverConfigId, localCopyPath, size, timestamp, syncState,
            appLocalCopy
        FROM cloud_media
        WHERE serverConfigId = :configId AND localCopyPath != '' AND remoteId IN (:remoteIds)
        """
    )
    suspend fun getLocalStates(configId: Long, remoteIds: List<String>): List<CloudMediaLocalState>

    @Query(
        """
        SELECT remoteId, providerType, serverConfigId, localCopyPath, size, timestamp, syncState,
            appLocalCopy
        FROM cloud_media
        WHERE providerType = :providerType AND serverConfigId = :serverConfigId
            AND appLocalCopy = 1 AND remoteId IN (:remoteIds)
        """
    )
    suspend fun getLocalCopyStates(
        providerType: ProviderType,
        serverConfigId: Long,
        remoteIds: List<String>
    ): List<CloudMediaLocalState>

    @Query(
        """
        SELECT remoteId, providerType, serverConfigId, localCopyPath, size, timestamp, syncState,
            appLocalCopy
        FROM cloud_media
        WHERE serverConfigId = :configId AND appLocalCopy = 1
        """
    )
    suspend fun getLocalCopyStatesForConfig(configId: Long): List<CloudMediaLocalState>

    @Query(
        """
        SELECT * FROM cloud_media
        WHERE serverConfigId = :configId AND syncState = :state AND trashed = 0
        ORDER BY timestamp DESC
        """
    )
    suspend fun getBySyncStateForConfig(configId: Long, state: SyncState): List<CloudMediaEntity>

    @Query("SELECT * FROM cloud_media WHERE favorite = 1 AND trashed = 0 AND archived = 0 ORDER BY timestamp DESC")
    fun getFavorites(): Flow<List<CloudMediaEntity>>

    @Query("SELECT * FROM cloud_media WHERE favorite = 1 AND providerType = :providerType ORDER BY timestamp DESC")
    fun getFavoritesByProvider(providerType: ProviderType): Flow<List<CloudMediaEntity>>

    @Query("SELECT * FROM cloud_media WHERE trashed = 1 ORDER BY timestamp DESC")
    fun getTrashed(): Flow<List<CloudMediaEntity>>

    @Query("SELECT * FROM cloud_media WHERE trashed = 1 AND providerType = :providerType ORDER BY timestamp DESC")
    fun getTrashedByProvider(providerType: ProviderType): Flow<List<CloudMediaEntity>>

    @Query("SELECT * FROM cloud_media WHERE globalMediaId = :globalMediaId LIMIT 1")
    suspend fun getByGlobalMediaId(globalMediaId: Long): CloudMediaEntity?

    @Query("SELECT * FROM cloud_media WHERE globalMediaId IN (:ids) AND trashed = 0 AND archived = 0")
    fun observeByGlobalMediaIds(ids: List<Long>): Flow<List<CloudMediaEntity>>

    @Query(
        """
        SELECT * FROM cloud_media
        WHERE remoteId = :remoteId AND providerType = :providerType AND serverConfigId = :serverConfigId
        LIMIT 1
        """
    )
    suspend fun getByRemoteId(
        remoteId: String,
        providerType: ProviderType,
        serverConfigId: Long
    ): CloudMediaEntity?

    @Query("SELECT * FROM cloud_media WHERE contentHash = :hash AND serverConfigId = :serverConfigId LIMIT 1")
    suspend fun getByContentHash(hash: String, serverConfigId: Long): CloudMediaEntity?

    @Query(
        """
        UPDATE cloud_media SET contentHash = :contentHash
        WHERE remoteId = :remoteId AND providerType = :providerType AND serverConfigId = :serverConfigId
        """
    )
    suspend fun updateContentHashValue(
        remoteId: String,
        providerType: ProviderType,
        serverConfigId: Long,
        contentHash: String
    ): Int

    @Query(
        """
        UPDATE cloud_media SET localCopyPath = ''
        WHERE remoteId = :remoteId AND providerType = :providerType AND serverConfigId = :serverConfigId
        """
    )
    suspend fun clearLocalCopyPath(
        remoteId: String,
        providerType: ProviderType,
        serverConfigId: Long
    ): Int

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM cloud_media
            WHERE remoteId = :remoteId AND providerType = :providerType
                AND serverConfigId = :serverConfigId AND contentHash = :contentHash
        )
    """)
    suspend fun hasContentHash(
        remoteId: String,
        providerType: ProviderType,
        serverConfigId: Long,
        contentHash: String
    ): Boolean

    @Transaction
    suspend fun updateContentHash(
        remoteId: String,
        providerType: ProviderType,
        serverConfigId: Long,
        contentHash: String
    ): Int = if (hasContentHash(remoteId, providerType, serverConfigId, contentHash)) 1
        else updateContentHashValue(remoteId, providerType, serverConfigId, contentHash)

    @Query(
        """
        SELECT * FROM cloud_media
        WHERE contentHash IN (:hashes) AND serverConfigId = :serverConfigId
        LIMIT 1
        """
    )
    suspend fun getByContentHashes(hashes: List<String>, serverConfigId: Long): CloudMediaEntity?

    @Query("SELECT * FROM cloud_media WHERE syncState = :state ORDER BY timestamp DESC")
    fun getBySyncState(state: SyncState): Flow<List<CloudMediaEntity>>

    @Query("SELECT * FROM cloud_media ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    fun getAll(limit: Int, offset: Int): Flow<List<CloudMediaEntity>>

    @Query("SELECT * FROM cloud_media ORDER BY timestamp DESC")
    fun getAll(): Flow<List<CloudMediaEntity>>

    @Query("SELECT * FROM cloud_media WHERE trashed = 0 AND archived = 0 ORDER BY timestamp DESC")
    fun getAllForTimeline(): Flow<List<CloudMediaEntity>>

    @Query("SELECT * FROM cloud_media WHERE trashed = 0 AND archived = 0 ORDER BY timestamp DESC")
    suspend fun getAllCachedAsync(): List<CloudMediaEntity>

    @Query("SELECT * FROM cloud_media ORDER BY timestamp DESC")
    suspend fun getAllAsync(): List<CloudMediaEntity>

    @Query("SELECT * FROM cloud_media WHERE archived = 1 AND trashed = 0 ORDER BY timestamp DESC")
    suspend fun getArchivedAsync(): List<CloudMediaEntity>

    @Query("SELECT * FROM cloud_media WHERE archived = 1 AND trashed = 0 AND providerType = :providerType ORDER BY timestamp DESC")
    suspend fun getArchivedByProviderAsync(providerType: ProviderType): List<CloudMediaEntity>

    @Query("SELECT * FROM cloud_media WHERE favorite = 1 AND trashed = 0 AND archived = 0 ORDER BY timestamp DESC")
    suspend fun getFavoritesAsync(): List<CloudMediaEntity>

    @Query("SELECT * FROM cloud_media WHERE trashed = 1 ORDER BY timestamp DESC")
    suspend fun getTrashedAsync(): List<CloudMediaEntity>

    @Query("SELECT COUNT(*) FROM cloud_media WHERE providerType = :providerType")
    suspend fun countByProvider(providerType: ProviderType): Int

    @Query("SELECT COUNT(*) FROM cloud_media WHERE serverConfigId = :configId")
    suspend fun countByConfig(configId: Long): Int

    @Query("SELECT COUNT(*) FROM cloud_media WHERE trashed = 0 AND archived = 0")
    suspend fun countCached(): Int

    @Query("SELECT COUNT(*) FROM cloud_media WHERE favorite = 1 AND trashed = 0 AND archived = 0")
    suspend fun countFavorites(): Int

    @Query("SELECT COUNT(*) FROM cloud_media WHERE archived = 1 AND trashed = 0")
    suspend fun countArchived(): Int

    @Query("SELECT COUNT(*) FROM cloud_media WHERE trashed = 1")
    suspend fun countTrashed(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllRaw(items: List<CloudMediaEntity>)

    @Transaction
    suspend fun insertAll(items: List<CloudMediaEntity>) {
        items.groupBy { it.serverConfigId }.forEach { (configId, configItems) ->
            configItems.chunked(900).forEach { chunk ->
                val remoteIds = chunk.map { it.remoteId }
                val localStates = getLocalStates(configId, remoteIds)
                    .associateBy { it.providerType to it.remoteId }
                val incomingByRemoteId = chunk.associateBy { it.remoteId }
                getBackupRevisions(configId, remoteIds).forEach { revision ->
                    val incoming = incomingByRemoteId[revision.remoteId]
                    if (incoming != null && shouldInvalidateBackupRevision(
                            incomingFingerprint = incoming.backupFingerprint(),
                            incomingHasContentHash = !incoming.contentHash.isNullOrBlank(),
                            storedFingerprint = revision.remoteFingerprint
                        )
                    ) {
                        deleteBackupRevision(configId, revision.localUri)
                    }
                }
                insertAllRaw(
                    chunk.map { item ->
                        val local = localStates[item.providerType to item.remoteId]
                        if (local == null) item else item.copy(
                            localCopyPath = local.localCopyPath,
                            size = local.size,
                            timestamp = local.timestamp,
                            syncState = local.syncState,
                            appLocalCopy = local.appLocalCopy
                        )
                    }
                )
            }
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: CloudMediaEntity)

    @Update
    suspend fun update(item: CloudMediaEntity)

    @Query(
        """
        UPDATE cloud_media SET syncState = :state
        WHERE remoteId = :remoteId AND providerType = :providerType AND serverConfigId = :serverConfigId
        """
    )
    suspend fun updateSyncState(
        remoteId: String,
        providerType: ProviderType,
        serverConfigId: Long,
        state: SyncState
    )

    @Query(
        """
        UPDATE cloud_media SET favorite = :favorite
        WHERE remoteId = :remoteId AND providerType = :providerType AND serverConfigId = :serverConfigId
        """
    )
    suspend fun updateFavorite(
        remoteId: String,
        providerType: ProviderType,
        serverConfigId: Long,
        favorite: Boolean
    )

    /** Same scoped mutation with Room's matched-row count for import result reporting. */
    @Query(
        """
        UPDATE cloud_media SET favorite = :favorite
        WHERE remoteId = :remoteId AND providerType = :providerType AND serverConfigId = :serverConfigId
        """
    )
    suspend fun updateFavoriteAndCount(
        remoteId: String,
        providerType: ProviderType,
        serverConfigId: Long,
        favorite: Boolean
    ): Int

    @Query(
        """
        UPDATE cloud_media SET trashed = :trashed
        WHERE remoteId = :remoteId AND providerType = :providerType AND serverConfigId = :serverConfigId
        """
    )
    suspend fun updateTrashed(
        remoteId: String,
        providerType: ProviderType,
        serverConfigId: Long,
        trashed: Boolean
    )

    @Query(
        """
        UPDATE cloud_media SET archived = :archived
        WHERE remoteId = :remoteId AND providerType = :providerType AND serverConfigId = :serverConfigId
        """
    )
    suspend fun updateArchived(
        remoteId: String,
        providerType: ProviderType,
        serverConfigId: Long,
        archived: Boolean
    )

    /**
     * Writes a local-file binding onto a cloud row. [appOwned] is intentionally
     * required — it must stay `true` ONLY for files the app itself created (download
     * worker); linking the user's own pre-existing file (the reconcile path in
     * `CloudLocalCopies.findVerifiedLocalCopy`) passes `false`, because every
     * destructive reader of `localCopyPath` filters on this flag.
     */
    @Query(
        """
        UPDATE cloud_media SET localCopyPath = :path, syncState = :state, appLocalCopy = :appOwned
        WHERE remoteId = :remoteId AND providerType = :providerType AND serverConfigId = :serverConfigId
        """
    )
    suspend fun updateLocalCopy(
        remoteId: String,
        providerType: ProviderType,
        serverConfigId: Long,
        path: String,
        state: SyncState,
        appOwned: Boolean
    )

    /**
     * Releases an app-downloaded copy binding after the file was removed: the row goes
     * back to `REMOTE_ONLY` so a future download can recreate it in the (by then fixed)
     * destination folder. Only rows actually owned by the app are touched — a user's
     * original file is never re-linked to REMOTE_ONLY by this.
     */
    @Query(
        """
        UPDATE cloud_media SET localCopyPath = '', appLocalCopy = 0, syncState = 'REMOTE_ONLY'
        WHERE serverConfigId = :configId AND remoteId IN (:remoteIds) AND appLocalCopy = 1
        """
    )
    suspend fun releaseAppLocalCopies(configId: Long, remoteIds: List<String>): Int

    /**
     * Aggregate over `REMOTE_ONLY` rows — count + byte size of what an enabled download
     * would pull — for the download-enable confirmation dialog and storage preflight.
     */
    @Query(
        """
        SELECT COUNT(*) AS itemCount, COALESCE(SUM(size), 0) AS totalBytes FROM cloud_media
        WHERE serverConfigId = :configId AND syncState = :state AND trashed = 0
            AND (:includeVideos = 1 OR mimeType NOT LIKE 'video/%')
        """
    )
    suspend fun getSyncStateStats(
        configId: Long,
        state: SyncState,
        includeVideos: Boolean
    ): PendingDownloadInfo

    @Query(
        """
        SELECT remoteId FROM cloud_media
        WHERE providerType = :providerType AND serverConfigId = :serverConfigId
        """
    )
    suspend fun getRemoteIds(providerType: ProviderType, serverConfigId: Long): List<String>

    @Query(
        """
        DELETE FROM cloud_backup_revision
        WHERE serverConfigId = :serverConfigId AND remoteId IN (:remoteIds)
        """
    )
    suspend fun deleteBackupRevisionsByRemoteIds(
        serverConfigId: Long,
        remoteIds: List<String>
    )

    @Query(
        """
        DELETE FROM cloud_media
        WHERE providerType = :providerType AND serverConfigId = :serverConfigId
            AND remoteId IN (:remoteIds)
        """
    )
    suspend fun deleteByRemoteIdsRaw(
        providerType: ProviderType,
        serverConfigId: Long,
        remoteIds: List<String>
    )

    /**
     * Prunes cached rows whose remote id is absent from [currentRemoteIds] (a complete
     * remote index — never call this with a partial listing). Returns the local-copy
     * states of the pruned rows so the caller can optionally remove the files the app
     * itself downloaded ("sync remote deletions").
     */
    @Transaction
    suspend fun deleteMissingRemoteMedia(
        serverConfigId: Long,
        providerType: ProviderType,
        currentRemoteIds: Collection<String>
    ): List<CloudMediaLocalState> {
        val current = currentRemoteIds.toHashSet()
        val stale = getRemoteIds(providerType, serverConfigId).filterNot(current::contains)
        return deleteStaleRemoteIds(providerType, serverConfigId, stale)
    }

    /**
     * Deletes the given remote ids (provider-reported deletions). Returns the local-copy
     * states of the removed rows, same contract as [deleteMissingRemoteMedia].
     */
    @Transaction
    suspend fun deleteByRemoteIds(
        providerType: ProviderType,
        serverConfigId: Long,
        remoteIds: List<String>
    ): List<CloudMediaLocalState> = deleteStaleRemoteIds(providerType, serverConfigId, remoteIds)

    private suspend fun deleteStaleRemoteIds(
        providerType: ProviderType,
        serverConfigId: Long,
        staleRemoteIds: List<String>
    ): List<CloudMediaLocalState> {
        if (staleRemoteIds.isEmpty()) return emptyList()
        val localStates = staleRemoteIds.chunked(900)
            .flatMap { getLocalCopyStates(providerType, serverConfigId, it) }
        staleRemoteIds.chunked(900).forEach { remoteIds ->
            deleteBackupRevisionsByRemoteIds(serverConfigId, remoteIds)
            deleteByRemoteIdsRaw(providerType, serverConfigId, remoteIds)
        }
        return localStates
    }

    @Query(
        """
        DELETE FROM cloud_media
        WHERE remoteId = :remoteId AND providerType = :providerType AND serverConfigId = :serverConfigId
        """
    )
    suspend fun deleteRaw(remoteId: String, providerType: ProviderType, serverConfigId: Long)

    @Transaction
    suspend fun delete(remoteId: String, providerType: ProviderType, serverConfigId: Long) {
        deleteBackupRevisions(serverConfigId, remoteId)
        deleteRaw(remoteId, providerType, serverConfigId)
    }

    @Query("DELETE FROM cloud_media WHERE serverConfigId = :configId")
    suspend fun deleteByServerConfigRaw(configId: Long)

    @Transaction
    suspend fun deleteByServerConfig(configId: Long) {
        deleteBackupRevisions(configId)
        deleteByServerConfigRaw(configId)
    }

    @Query("DELETE FROM cloud_media WHERE providerType = :providerType")
    suspend fun deleteByProviderRaw(providerType: ProviderType)

    @Transaction
    suspend fun deleteByProvider(providerType: ProviderType) {
        deleteBackupRevisions(providerType)
        deleteByProviderRaw(providerType)
    }

    @Query("DELETE FROM cloud_media")
    suspend fun deleteAllRaw()

    @Transaction
    suspend fun deleteAll() {
        deleteAllBackupRevisions()
        deleteAllRaw()
    }
}
