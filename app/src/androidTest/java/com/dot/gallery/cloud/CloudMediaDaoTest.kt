/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.data.dao.CloudMediaDao
import com.dot.gallery.cloud.data.entity.CloudBackupRevisionEntity
import com.dot.gallery.cloud.data.entity.CloudMediaEntity
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
import com.dot.gallery.feature_node.domain.util.isVideo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the cloud_media query surface that backs the unified timeline and the
 * Favorites/Archive/Trash screens:
 *  - the timeline query excludes trashed AND archived items (the exact source the
 *    MediaDistributor merges into the main grid);
 *  - per-account/per-provider scoping is correct so removing one account can't wipe another;
 *  - toUriMedia() derives strictly-negative, globally-unique ids (timeline-key crash guard).
 */
@RunWith(AndroidJUnit4::class)
class CloudMediaDaoTest {

    private lateinit var db: InternalDatabase
    private lateinit var dao: CloudMediaDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.getCloudMediaDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun media(
        remoteId: String,
        provider: ProviderType,
        configId: Long,
        timestamp: Long = 1_000L,
        favorite: Boolean = false,
        trashed: Boolean = false,
        archived: Boolean = false,
        mimeType: String = "image/jpeg",
        duration: String? = null
    ) = CloudMediaEntity(
        remoteId = remoteId,
        providerType = provider,
        serverConfigId = configId,
        label = remoteId,
        timestamp = timestamp,
        favorite = favorite,
        trashed = trashed,
        archived = archived,
        mimeType = mimeType,
        duration = duration
    )

    private fun revision(
        localUri: String,
        remoteFingerprint: String = "sha1"
    ) = CloudBackupRevisionEntity(
        serverConfigId = 1L,
        providerType = ProviderType.IMMICH,
        localUri = localUri,
        localSize = 1234L,
        localTimestamp = 100L,
        remoteId = "asset",
        remoteFingerprint = remoteFingerprint,
        verifiedAt = System.currentTimeMillis()
    )

    @Test
    fun timelineExcludesTrashedAndArchived() = runBlocking {
        dao.insertAll(
            listOf(
                media("visible-1", ProviderType.IMMICH, 1L),
                media("trashed-1", ProviderType.IMMICH, 1L, trashed = true),
                media("archived-1", ProviderType.IMMICH, 1L, archived = true),
                media("visible-2", ProviderType.OWNCLOUD, 2L)
            )
        )

        val timeline = dao.getAllForTimeline().first()
        assertEquals(2, timeline.size)
        assertEquals(setOf("visible-1", "visible-2"), timeline.map { it.remoteId }.toSet())
    }

    @Test
    fun captureDateSurvivesBothRescanWritesButNotAChangedFile() = runBlocking {
        val original = media("photo.jpg", ProviderType.WEBDAV, 1L).copy(
            originalUrl = "https://example.test/photo.jpg", size = 100L
        )
        dao.insert(original.copy(takenTimestamp = 500L))
        dao.insertAll(listOf(original.copy(favorite = true)))
        assertEquals(500L, dao.getByRemoteId(original.remoteId, original.providerType, 1L)?.takenTimestamp)
        assertTrue(dao.getByRemoteId(original.remoteId, original.providerType, 1L)!!.favorite)
        dao.insert(original)
        assertEquals(500L, dao.getByRemoteId(original.remoteId, original.providerType, 1L)?.takenTimestamp)
        dao.insertAll(listOf(original.copy(timestamp = 2000L)))
        assertNull(dao.getByRemoteId(original.remoteId, original.providerType, 1L)?.takenTimestamp)
    }

    @Test
    fun captureDateUpdateIsScopedAndRejectsStaleResults() = runBlocking {
        val records = listOf(
            ProviderType.WEBDAV to 1L, ProviderType.WEBDAV to 2L, ProviderType.OWNCLOUD to 1L
        ).map { (provider, configId) ->
            media("photo.jpg", provider, configId).copy(originalUrl = "https://example.test/photo.jpg", size = 100L)
        }
        val first = records.first()
        dao.insertAll(records)

        assertEquals(0, dao.updateCaptureTime(first.remoteId, first.providerType, 1L, first.originalUrl, 99L, first.timestamp, 500L))
        assertEquals(0, dao.updateCaptureTime(first.remoteId, first.providerType, 1L, first.originalUrl, first.size, 999L, 500L))
        assertEquals(1, dao.updateCaptureTime(first.remoteId, first.providerType, 1L, first.originalUrl, first.size, first.timestamp, 500L))
        assertEquals(0, dao.updateCaptureTime(first.remoteId, first.providerType, 1L, first.originalUrl, first.size, first.timestamp, 600L))
        assertEquals(500L, dao.getByRemoteId(first.remoteId, first.providerType, 1L)?.takenTimestamp)
        assertEquals(first.timestamp, dao.getByRemoteId(first.remoteId, first.providerType, 1L)?.timestamp)
        assertNull(dao.getByRemoteId(first.remoteId, first.providerType, 2L)?.takenTimestamp)
        assertNull(dao.getByRemoteId(first.remoteId, ProviderType.OWNCLOUD, 1L)?.takenTimestamp)
    }

    @Test
    fun timelineOrderedByTimestampDescending() = runBlocking {
        dao.insertAll(
            listOf(
                media("old", ProviderType.IMMICH, 1L, timestamp = 100L),
                media("new", ProviderType.IMMICH, 1L, timestamp = 300L),
                media("mid", ProviderType.IMMICH, 1L, timestamp = 200L)
            )
        )
        val ordered = dao.getAllForTimeline().first().map { it.remoteId }
        assertEquals(listOf("new", "mid", "old"), ordered)
    }

    @Test
    fun deletingOneAccountLeavesOtherAccountsIntact() = runBlocking {
        dao.insertAll(
            listOf(
                media("a", ProviderType.IMMICH, 1L),
                media("b", ProviderType.IMMICH, 1L),
                media("c", ProviderType.OWNCLOUD, 2L)
            )
        )

        dao.deleteByServerConfig(1L)

        assertEquals(0, dao.countByConfig(1L))
        assertEquals(1, dao.countByConfig(2L))
        assertEquals(1, dao.getAllForTimeline().first().size)
    }

    @Test
    fun sameRemoteIdOnTwoAccountsCoexistViaCompositeKey() = runBlocking {
        // Composite PK is (remoteId, providerType, serverConfigId): two Immich servers can both
        // hold an asset with remoteId "1" without one REPLACE-ing the other.
        dao.insertAll(
            listOf(
                media("1", ProviderType.IMMICH, 1L),
                media("1", ProviderType.IMMICH, 2L)
            )
        )
        assertEquals(2, dao.getAllForTimeline().first().size)
    }

    @Test
    fun remoteIndexReconciliationRemovesOnlyMissingRowsFromItsAccount() = runBlocking {
        dao.insertAll(
            listOf(
                media("current", ProviderType.SMB, 1L),
                media("missing", ProviderType.SMB, 1L),
                media("missing", ProviderType.SMB, 2L),
                media("missing", ProviderType.NFS, 1L)
            )
        )
        dao.upsertBackupRevision(
            revision("content://media/missing").copy(
                providerType = ProviderType.SMB,
                remoteId = "missing"
            )
        )

        dao.deleteMissingRemoteMedia(1L, ProviderType.SMB, listOf("current"))

        assertEquals("current", dao.getByRemoteId("current", ProviderType.SMB, 1L)?.remoteId)
        assertNull(dao.getByRemoteId("missing", ProviderType.SMB, 1L))
        assertEquals("missing", dao.getByRemoteId("missing", ProviderType.SMB, 2L)?.remoteId)
        assertEquals("missing", dao.getByRemoteId("missing", ProviderType.NFS, 1L)?.remoteId)
        assertTrue(dao.getBackupRevisions(1L).isEmpty())
    }

    @Test
    fun rowMutationsAreScopedToOwningAccount() = runBlocking {
        val first = media("shared-id", ProviderType.IMMICH, 1L)
        val second = media("shared-id", ProviderType.IMMICH, 2L)
        dao.insertAll(listOf(first, second))

        val matched = dao.updateFavoriteAndCount("shared-id", ProviderType.IMMICH, 2L, true)
        val unmatched = dao.updateFavoriteAndCount("missing-id", ProviderType.IMMICH, 2L, true)

        assertEquals(1, matched)
        assertEquals(0, unmatched)
        assertEquals(false, dao.getByRemoteId("shared-id", ProviderType.IMMICH, 1L)?.favorite)
        assertEquals(true, dao.getByRemoteId("shared-id", ProviderType.IMMICH, 2L)?.favorite)
        assertEquals(first.globalMediaId, dao.getByGlobalMediaId(first.globalMediaId)?.globalMediaId)
    }

    @Test
    fun favoritesArchivedTrashedCountsAreScoped() = runBlocking {
        dao.insertAll(
            listOf(
                media("f", ProviderType.IMMICH, 1L, favorite = true),
                media("ar", ProviderType.IMMICH, 1L, archived = true),
                media("tr", ProviderType.IMMICH, 1L, trashed = true),
                media("plain", ProviderType.IMMICH, 1L)
            )
        )
        assertEquals(1, dao.countFavorites())
        assertEquals(1, dao.countArchived())
        assertEquals(1, dao.countTrashed())
        assertEquals(2, dao.countCached()) // favorite + plain (archived/trashed excluded)
    }

    @Test
    fun toUriMediaProducesNegativeUniqueIds() = runBlocking {
        dao.insertAll(
            listOf(
                media("1", ProviderType.IMMICH, 1L),
                media("1", ProviderType.IMMICH, 2L),
                media("1", ProviderType.OWNCLOUD, 3L)
            )
        )
        val ids = dao.getAllForTimeline().first().map { it.toUriMedia().id }
        assertTrue("all cloud ids must be negative", ids.all { it < 0L })
        assertEquals("ids must be unique across accounts/providers", ids.size, ids.toSet().size)
    }

    @Test
    fun backupRevisionCacheSupportsIdenticalLocalFilesWithoutChangingRemoteMetadata() = runBlocking {
        dao.insert(
            media("asset", ProviderType.IMMICH, 1L, timestamp = 2_000L).copy(
                contentHash = "sha1",
                size = 2345L,
                favorite = true
            )
        )
        dao.upsertBackupRevision(revision("content://media/42"))
        dao.upsertBackupRevision(revision("content://media/43"))

        val cached = dao.getByRemoteId("asset", ProviderType.IMMICH, 1L)!!
        assertEquals(2345L, cached.size)
        assertEquals(2_000L, cached.timestamp)
        assertTrue(cached.favorite)
        assertEquals(2, dao.getBackupRevisions(1L).size)
    }

    @Test
    fun remoteRefreshInvalidatesBackupProofWhenFingerprintChanges() = runBlocking {
        dao.insert(
            media("asset", ProviderType.IMMICH, 1L, timestamp = 2_000L).copy(
                contentHash = "old-hash"
            )
        )
        dao.upsertBackupRevision(revision("content://media/42", remoteFingerprint = "old-hash"))

        dao.insertAll(
            listOf(
                media("asset", ProviderType.IMMICH, 1L, timestamp = 3_000L).copy(
                    contentHash = "new-hash"
                )
            )
        )

        assertTrue(dao.getBackupRevisions(1L).isEmpty())
    }

    @Test
    fun remoteRefreshPreservesBackupProofWhenFingerprintIsUnchanged() = runBlocking {
        val hex = "000102030405060708090a0b0c0d0e0f10111213"
        val base64 = "AAECAwQFBgcICQoLDA0ODxAREhM="
        dao.insert(
            media("asset", ProviderType.IMMICH, 1L, timestamp = 2_000L).copy(
                contentHash = base64
            )
        )
        dao.upsertBackupRevision(revision("content://media/42", remoteFingerprint = hex))

        dao.insertAll(
            listOf(
                media("asset", ProviderType.IMMICH, 1L, timestamp = 3_000L).copy(
                    contentHash = base64
                )
            )
        )

        assertEquals(1, dao.getBackupRevisions(1L).size)
    }

    @Test
    fun videoWithoutDurationStillResolvesAsVideo() = runBlocking {
        // Path providers can't report duration; toUriMedia must default video duration to ""
        // (non-null) so Media.isVideo stays true and it renders in the player, not as a still.
        dao.insert(media("clip", ProviderType.SMB, 1L, mimeType = "video/mp4", duration = null))
        val uriMedia = dao.getAllForTimeline().first().single().toUriMedia()
        assertEquals("", uriMedia.duration)
        assertTrue(uriMedia.isVideo)
    }

    private fun installHashUpdateProbe() {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TABLE backup_hash_updates (remoteId TEXT NOT NULL)"
        )
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER track_backup_hash_updates AFTER UPDATE OF contentHash ON cloud_media BEGIN INSERT INTO backup_hash_updates(remoteId) VALUES (NEW.remoteId); END"
        )
    }

    private fun hashUpdateCount(): Int =
        db.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM backup_hash_updates")
            .use { it.moveToFirst(); it.getInt(0) }

    @Test
    fun unchangedContentHashSkipsTheSqlUpdate() = runTest {
        dao.insert(
            media("asset", ProviderType.WEBDAV, 1L).copy(
                contentHash = "same",
                favorite = true,
                localCopyPath = "/data/local/asset.jpg"
            )
        )
        installHashUpdateProbe()

        assertEquals(1, dao.updateContentHash("asset", ProviderType.WEBDAV, 1L, "same"))
        assertEquals(0, hashUpdateCount())
        val stored = dao.getByRemoteId("asset", ProviderType.WEBDAV, 1L)!!
        assertEquals("same", stored.contentHash)
        assertTrue(stored.favorite)
        assertEquals("/data/local/asset.jpg", stored.localCopyPath)

        assertEquals(0, dao.updateContentHash("missing", ProviderType.WEBDAV, 1L, "same"))
        assertEquals(0, hashUpdateCount())
    }

    @Test
    fun changedContentHashWritesOnceAndStaysAccountScoped() = runTest {
        dao.insert(media("asset", ProviderType.WEBDAV, 1L))
        dao.insert(
            media("asset", ProviderType.WEBDAV, 2L).copy(
                contentHash = "other-account",
                favorite = true,
                localCopyPath = "/data/local/other.jpg"
            )
        )
        installHashUpdateProbe()

        assertEquals(1, dao.updateContentHash("asset", ProviderType.WEBDAV, 1L, "first"))
        assertEquals(1, dao.updateContentHash("asset", ProviderType.WEBDAV, 1L, "changed"))
        assertEquals(2, hashUpdateCount())
        assertEquals(1, dao.updateContentHash("asset", ProviderType.WEBDAV, 1L, "changed"))
        assertEquals(2, hashUpdateCount())

        val second = dao.getByRemoteId("asset", ProviderType.WEBDAV, 2L)!!
        assertEquals("other-account", second.contentHash)
        assertTrue(second.favorite)
        assertEquals("/data/local/other.jpg", second.localCopyPath)
    }
}
