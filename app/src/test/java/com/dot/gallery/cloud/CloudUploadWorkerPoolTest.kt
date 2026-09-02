/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud

import androidx.work.WorkInfo
import com.dot.gallery.cloud.data.dao.shouldInvalidateBackupRevision
import com.dot.gallery.cloud.sync.BACKUP_VERIFICATION_MAX_AGE_MS
import com.dot.gallery.cloud.sync.CloudUploadWorker
import com.dot.gallery.cloud.sync.backupChecksumVariants
import com.dot.gallery.cloud.sync.backupDestinationConfigIds
import com.dot.gallery.cloud.sync.backupRevisionLocalUri
import com.dot.gallery.cloud.sync.backupVerificationCutoff
import com.dot.gallery.cloud.sync.isActiveBackupWork
import com.dot.gallery.cloud.sync.isBackupRevisionCached
import com.dot.gallery.cloud.sync.mapWorkerPool
import com.dot.gallery.cloud.ui.backup.BackupMatchEvidence
import com.dot.gallery.cloud.ui.backup.backupMatchEvidence
import com.dot.gallery.cloud.ui.backup.visibleBackupProgressItems
import com.dot.gallery.cloud.sync.runWorkerPool
import com.dot.gallery.cloud.sync.shouldDeferChecksumCheck
import com.dot.gallery.cloud.immich.data.dto.ImmichAssetDto
import com.dot.gallery.cloud.immich.data.dto.ImmichBulkCheckResultItemDto
import com.dot.gallery.cloud.immich.immichChecksum
import com.dot.gallery.cloud.immich.verifiedImmichAssetHashes
import com.dot.gallery.cloud.netfs.contentSha1
import com.dot.gallery.cloud.ui.deleteLocalEnabledForSelection
import com.dot.gallery.cloud.ui.space.freeUpSpaceDeletionBatch
import com.dot.gallery.cloud.ui.space.verifiedLocalRevisionMatches
import com.dot.gallery.cloud.webdav.canCacheWebDavChecksum
import com.dot.gallery.cloud.webdav.data.api.WebDavClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class CloudUploadWorkerPoolTest {

    @Test
    fun dormantPeriodicWorkIsNotReportedAsAnActiveUpload() {
        assertTrue(
            !isActiveBackupWork(
                WorkInfo.State.ENQUEUED,
                setOf(CloudUploadWorker.TAG_BACKUP, CloudUploadWorker.TAG_PERIODIC_BACKUP)
            )
        )
        assertTrue(
            isActiveBackupWork(
                WorkInfo.State.ENQUEUED,
                setOf(CloudUploadWorker.TAG_BACKUP, CloudUploadWorker.TAG_MANUAL_BACKUP)
            )
        )
        assertTrue(
            isActiveBackupWork(
                WorkInfo.State.RUNNING,
                setOf(CloudUploadWorker.TAG_BACKUP, CloudUploadWorker.TAG_PERIODIC_BACKUP)
            )
        )
    }

    @Test
    fun checksumVerificationUsesCheckedItemsInsteadOfStayingAtZero() {
        assertEquals(
            40,
            visibleBackupProgressItems(
                phase = CloudUploadWorker.PHASE_VERIFYING,
                checkedItems = 40,
                completedItems = 0,
                failedItems = 0
            )
        )
        assertEquals(
            7,
            visibleBackupProgressItems(
                phase = CloudUploadWorker.PHASE_UPLOADING,
                checkedItems = 40,
                completedItems = 5,
                failedItems = 2
            )
        )
    }

    @Test
    fun processesEveryItemWithBoundedConcurrency() = runBlocking {
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val processed = ConcurrentHashMap.newKeySet<Int>()

        runWorkerPool((0 until 20).toList(), maxConcurrency = 3) { item ->
            val current = active.incrementAndGet()
            peak.updateAndGet { previous -> maxOf(previous, current) }
            delay(10)
            processed += item
            active.decrementAndGet()
        }

        assertEquals((0 until 20).toSet(), processed)
        assertEquals(3, peak.get())
    }

    @Test
    fun doesNotCreateMoreWorkersThanItems() = runBlocking {
        val active = AtomicInteger()
        val peak = AtomicInteger()

        runWorkerPool(listOf("one", "two"), maxConcurrency = 3) {
            val current = active.incrementAndGet()
            peak.updateAndGet { previous -> maxOf(previous, current) }
            delay(10)
            active.decrementAndGet()
        }

        assertTrue(peak.get() <= 2)
    }

    @Test
    fun deleteLocalCoverageIncludesEveryConfiguredDestination() {
        val destinations = backupDestinationConfigIds(
            albumId = 10L,
            albumIdsByConfig = mapOf(
                1L to setOf(10L, 20L),
                2L to setOf(10L),
                3L to setOf(30L)
            )
        )

        assertEquals(setOf(1L, 2L), destinations)
    }

    @Test
    fun immichAssetRevisionUsesModifiedTimeAndDeviceAssetId() {
        val entity = ImmichAssetDto(
            id = "remote-id",
            deviceAssetId = "42",
            originalFileName = "photo.jpg",
            originalMimeType = "image/jpeg",
            fileCreatedAt = "2026-01-01T00:00:00Z",
            fileModifiedAt = "2026-01-02T03:04:05Z"
        ).toCloudMediaEntity(serverConfigId = 7L, baseUrl = "https://immich.example")

        assertEquals("42", entity.fileId)
        assertEquals(1767323045000L, entity.lastSyncedAt)
    }

    @Test
    fun pathBasedVerificationIsScopedToItsRemoteTarget() {
        val uri = "content://media/42"

        assertTrue(
            backupRevisionLocalUri(uri, "Trips/photo.jpg") !=
                backupRevisionLocalUri(uri, "Family/photo.jpg")
        )
        assertEquals(uri, backupRevisionLocalUri(uri, null))
    }

    @Test
    fun cachedVerificationExpiresAfterBoundedAge() {
        val now = 1_000_000_000L

        assertEquals(now - BACKUP_VERIFICATION_MAX_AGE_MS, backupVerificationCutoff(now))
    }

    @Test
    fun checksumLookupSupportsImmichBase64AndLocalHexRepresentations() {
        val hex = "000102030405060708090a0b0c0d0e0f10111213"

        assertEquals(
            listOf(hex, "AAECAwQFBgcICQoLDA0ODxAREhM="),
            backupChecksumVariants(hex)
        )
    }

    @Test
    fun immichRequestsEncodeHexSha1AsBase64() {
        assertEquals(
            "AAECAwQFBgcICQoLDA0ODxAREhM=",
            immichChecksum("000102030405060708090a0b0c0d0e0f10111213")
        )
        assertEquals("already-encoded", immichChecksum("already-encoded"))
    }

    @Test
    fun deleteLocalSwitchOnlyReflectsSelectedAlbums() {
        assertTrue(
            !deleteLocalEnabledForSelection(
                enabledAlbums = setOf(10L),
                deleteLocalPreferences = mapOf(20L to true)
            )
        )
        assertTrue(
            deleteLocalEnabledForSelection(
                enabledAlbums = setOf(10L, 20L),
                deleteLocalPreferences = mapOf(10L to true, 20L to true)
            )
        )
    }

    @Test
    fun freeUpSpaceDeletionBatchesStayWithinMediaStoreLimit() {
        assertEquals(2_000, freeUpSpaceDeletionBatch((0 until 2_000).toList()).size)
        assertEquals(2_000, freeUpSpaceDeletionBatch((0 until 2_001).toList()).size)
    }

    @Test
    fun freeUpSpaceRejectsLocalContentChangedAfterVerification() {
        val verifiedHashes = mapOf(42L to "verified")

        assertTrue(verifiedLocalRevisionMatches(42L, "verified", verifiedHashes))
        assertTrue(!verifiedLocalRevisionMatches(42L, "changed", verifiedHashes))
        assertTrue(!verifiedLocalRevisionMatches(42L, null, verifiedHashes))
    }

    @Test
    fun immichPresenceRequiresNonTrashedDuplicate() {
        val duplicate = ImmichBulkCheckResultItemDto(
            action = "reject",
            assetId = "asset-id",
            reason = "duplicate",
            isTrashed = false
        )

        assertTrue(duplicate.isSafeDuplicate())
        assertTrue(!duplicate.copy(isTrashed = true).isSafeDuplicate())
        assertTrue(!duplicate.copy(reason = "unsupported-format").isSafeDuplicate())
        assertTrue(!duplicate.copy(assetId = null).isSafeDuplicate())
    }

    @Test
    fun immichVerificationRetainsDuplicateAssetIdentity() {
        val duplicate = ImmichBulkCheckResultItemDto(
            id = "1",
            action = "reject",
            assetId = "asset-id",
            reason = "duplicate",
            isTrashed = false
        )

        assertEquals(
            mapOf("asset-id" to "hash-1"),
            verifiedImmichAssetHashes(listOf("hash-0", "hash-1"), listOf(duplicate))
        )
        assertEquals(emptyMap<String, String>(), verifiedImmichAssetHashes(listOf("hash-0"), listOf(duplicate)))
    }

    @Test
    fun remoteRevisionCanBeDetectedButMustNotBeUsedForImmichChecksumBypass() {
        val remoteRevisions = setOf("42|photo.jpg|image/jpeg|1234|100")

        assertTrue(
            isBackupRevisionCached(
                uri = "content://media/42",
                mediaId = 42L,
                label = "photo.jpg",
                mimeType = "image/jpeg",
                size = 1234L,
                timestamp = 100L,
                localRevisions = emptySet(),
                remoteRevisions = remoteRevisions
            )
        )
        assertTrue(
            !isBackupRevisionCached(
                uri = "content://media/42",
                mediaId = 42L,
                label = "photo.jpg",
                mimeType = "image/jpeg",
                size = 1234L,
                timestamp = 101L,
                localRevisions = emptySet(),
                remoteRevisions = remoteRevisions
            )
        )
    }

    @Test
    fun unchangedSuccessfulSyncRevisionDoesNotRequireAnotherContentCheck() {
        assertEquals(
            BackupMatchEvidence.VERIFIED_REVISION,
            backupMatchEvidence(
                uri = "content://media/42",
                mediaId = 42L,
                label = "photo.jpg",
                mimeType = "image/jpeg",
                size = 1234L,
                timestamp = 100L,
                cachedNames = setOf("photo.jpg"),
                localRevisions = setOf("content://media/42|1234|100"),
                remoteRevisions = emptySet()
            )
        )
        assertEquals(
            BackupMatchEvidence.ASSUMED_FILENAME,
            backupMatchEvidence(
                uri = "content://media/42",
                mediaId = 42L,
                label = "photo.jpg",
                mimeType = "image/jpeg",
                size = 1234L,
                timestamp = 101L,
                cachedNames = setOf("photo.jpg"),
                localRevisions = setOf("content://media/42|1234|100"),
                remoteRevisions = emptySet()
            )
        )
    }

    @Test
    fun sameAppRemoteRevisionRemainsAnAssumptionUntilContentIsChecked() {
        assertEquals(
            BackupMatchEvidence.ASSUMED_REVISION,
            backupMatchEvidence(
                uri = "content://media/42",
                mediaId = 42L,
                label = "photo.jpg",
                mimeType = "image/jpeg",
                size = 1234L,
                timestamp = 100L,
                cachedNames = setOf("photo.jpg"),
                localRevisions = emptySet(),
                remoteRevisions = setOf("42|photo.jpg|image/jpeg|1234|100")
            )
        )
        assertEquals(
            BackupMatchEvidence.ASSUMED_FILENAME,
            backupMatchEvidence(
                uri = "content://media/99",
                mediaId = 99L,
                label = "photo.jpg",
                mimeType = "image/jpeg",
                size = 1234L,
                timestamp = 100L,
                cachedNames = setOf("photo.jpg"),
                localRevisions = emptySet(),
                remoteRevisions = setOf("42|photo.jpg|image/jpeg|1234|100")
            )
        )
    }

    @Test
    fun smallImmichQueueUsesFastStartChecksumPath() {
        assertTrue(shouldDeferChecksumCheck(itemCount = 6, maxConcurrentUploads = 3))
        assertTrue(!shouldDeferChecksumCheck(itemCount = 7, maxConcurrentUploads = 3))
    }

    @Test
    fun hashVerifiedRevisionSurvivesMetadataOnlyRefresh() {
        val verifiedHash = "000102030405060708090a0b0c0d0e0f10111213"

        assertTrue(!shouldInvalidateBackupRevision("1234|5678", false, verifiedHash))
        assertTrue(shouldInvalidateBackupRevision("different-hash", true, verifiedHash))
        assertTrue(shouldInvalidateBackupRevision("1234|5679", false, "1234|5678"))
    }

    @Test
    fun webDavChecksumCacheRequiresServerValidator() {
        assertTrue(!canCacheWebDavChecksum(""))
        assertTrue(canCacheWebDavChecksum("etag"))
    }

    @Test
    fun webDavChecksumStreamsRemoteContent() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("verified"))
            val client = WebDavClient(
                OkHttpClient(),
                server.url("/dav").toString(),
                "user",
                "password",
                ""
            )

            assertEquals("ec734b651574683f36974c7f12847fbbe084dbe2", client.sha1("album/photo.jpg"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun networkFileSystemChecksumStreamsRemoteContent() {
        assertEquals(
            "ec734b651574683f36974c7f12847fbbe084dbe2",
            contentSha1(ByteArrayInputStream("verified".toByteArray()))
        )
    }

    @Test
    fun mapsConcurrentlyWhilePreservingInputOrder() = runBlocking {
        val active = AtomicInteger()
        val peak = AtomicInteger()

        val mapped = mapWorkerPool((0 until 6).toList(), maxConcurrency = 3) { item ->
            val current = active.incrementAndGet()
            peak.updateAndGet { previous -> maxOf(previous, current) }
            delay((6 - item).toLong())
            active.decrementAndGet()
            item * 2
        }

        assertEquals(listOf(0, 2, 4, 6, 8, 10), mapped)
        assertEquals(3, peak.get())
    }
}
