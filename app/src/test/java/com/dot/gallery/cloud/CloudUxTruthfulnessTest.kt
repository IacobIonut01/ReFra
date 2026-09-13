package com.dot.gallery.cloud

import com.dot.gallery.cloud.core.ConnectionState
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.ui.CloudAccountDeletion
import com.dot.gallery.cloud.ui.CloudAccountDeletionState
import com.dot.gallery.cloud.ui.backup.AccountBackupStatus
import com.dot.gallery.cloud.ui.offline.OfflineAvailabilityStatus
import com.dot.gallery.cloud.ui.offline.OfflineCoverage
import com.dot.gallery.cloud.ui.offline.OfflineDownloadWorkState
import com.dot.gallery.cloud.sync.isRetryableOfflineHttpStatus
import com.dot.gallery.cloud.ui.offline.offlineAvailabilityStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CloudUxTruthfulnessTest {

    @Test
    fun removingAccountFinishesBeforeNavigationClearsItsScope() = runTest {
        val owner = CoroutineScope(coroutineContext + Job())
        val events = mutableListOf<String>()
        val deletion = CloudAccountDeletion(owner) { configId ->
            events += "start:$configId"
            delay(1_000)
            events += "cache cleared"
            delay(1_000)
            events += "account removed"
        }
        backgroundScope.launch {
            deletion.state.first { it == CloudAccountDeletionState.Deleted(42L) }
            events += "navigate"
            owner.cancel()
        }

        deletion.delete(42L)
        runCurrent()
        assertEquals(CloudAccountDeletionState.Deleting(42L), deletion.state.value)
        assertEquals(listOf("start:42"), events)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(CloudAccountDeletionState.Deleting(42L), deletion.state.value)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(CloudAccountDeletionState.Deleted(42L), deletion.state.value)
        assertEquals(listOf("start:42", "cache cleared", "account removed", "navigate"), events)
        owner.cancel()
    }

    @Test
    fun repeatedRemovalClicksDoNotStartConcurrentCleanup() = runTest {
        val removedIds = mutableListOf<Long>()
        val deletion = CloudAccountDeletion(backgroundScope) {
            removedIds += it
            delay(1_000)
        }

        deletion.delete(41L)
        deletion.delete(41L)
        deletion.delete(42L)
        runCurrent()

        assertEquals(listOf(41L), removedIds)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(CloudAccountDeletionState.Deleted(41L), deletion.state.value)
    }

    @Test
    fun failedAccountRemovalCanBeRetriedWithoutReportingSuccess() = runTest {
        var attempts = 0
        val deletion = CloudAccountDeletion(backgroundScope) {
            attempts++
            if (attempts == 1) throw IllegalStateException("Cleanup failed")
        }

        deletion.delete(42L)
        runCurrent()
        assertEquals(CloudAccountDeletionState.Failed(42L), deletion.state.value)

        deletion.delete(42L)
        assertEquals(CloudAccountDeletionState.Deleting(42L), deletion.state.value)
        runCurrent()
        assertEquals(CloudAccountDeletionState.Deleted(42L), deletion.state.value)
        assertEquals(2, attempts)
    }

    @Test
    fun cancelledRemovalDoesNotReportFailureOrSuccess() = runTest {
        val owner = CoroutineScope(coroutineContext + Job())
        var removed = false
        val deletion = CloudAccountDeletion(owner) {
            delay(1_000)
            removed = true
        }

        deletion.delete(42L)
        runCurrent()
        owner.cancel()
        runCurrent()

        assertEquals(false, removed)
        assertEquals(CloudAccountDeletionState.Idle, deletion.state.value)
    }

    @Test
    fun pinIntentIsNotReportedAsCompleteBeforeVariantsExist() {
        assertEquals(
            OfflineAvailabilityStatus.PINNED,
            offlineAvailabilityStatus(
                pinned = true,
                coverage = OfflineCoverage(downloadedVariants = 0, totalVariants = 4),
                workState = OfflineDownloadWorkState.IDLE
            )
        )
        assertEquals(
            OfflineAvailabilityStatus.QUEUED,
            offlineAvailabilityStatus(
                pinned = true,
                coverage = OfflineCoverage(downloadedVariants = 0, totalVariants = 4),
                workState = OfflineDownloadWorkState.QUEUED
            )
        )
    }

    @Test
    fun offlineCoverageDistinguishesPartialCompleteAndFailed() {
        assertEquals(
            OfflineAvailabilityStatus.PARTIAL,
            offlineAvailabilityStatus(
                pinned = true,
                coverage = OfflineCoverage(downloadedVariants = 2, totalVariants = 4),
                workState = OfflineDownloadWorkState.RUNNING
            )
        )
        assertEquals(
            OfflineAvailabilityStatus.COMPLETE,
            offlineAvailabilityStatus(
                pinned = true,
                coverage = OfflineCoverage(downloadedVariants = 4, totalVariants = 4),
                workState = OfflineDownloadWorkState.SUCCEEDED
            )
        )
        assertEquals(
            OfflineAvailabilityStatus.FAILED,
            offlineAvailabilityStatus(
                pinned = true,
                coverage = OfflineCoverage(downloadedVariants = 1, totalVariants = 4),
                workState = OfflineDownloadWorkState.FAILED
            )
        )
    }

    @Test
    fun offlineWorkerRetriesTransientHttpFailuresOnly() {
        assertEquals(true, isRetryableOfflineHttpStatus(408))
        assertEquals(true, isRetryableOfflineHttpStatus(429))
        assertEquals(true, isRetryableOfflineHttpStatus(503))
        assertEquals(false, isRetryableOfflineHttpStatus(401))
        assertEquals(false, isRetryableOfflineHttpStatus(404))
    }

    @Test
    fun filenameMatchesRemainAssumedRatherThanVerifiedBackups() {
        val status = AccountBackupStatus(
            configId = 7L,
            providerType = ProviderType.IMMICH,
            accountLabel = "Photos",
            enabledAlbumCount = 1,
            totalAssets = 10,
            verifiedCount = 3,
            assumedCount = 4,
            connectionState = ConnectionState.CONNECTED
        )

        assertEquals(3, status.backedUpCount)
        assertEquals(4, status.assumedCount)
        assertEquals(3, status.unknownCount)
        assertEquals(7, status.remainderCount)
        assertEquals(0.3f, status.progress)
    }
}
