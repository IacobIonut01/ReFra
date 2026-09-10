/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud

import com.dot.gallery.cloud.core.ConnectionState
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.auth.CloudConnectionErrorKind
import com.dot.gallery.cloud.core.auth.CloudConnectionException
import com.dot.gallery.cloud.data.entity.CloudServerConfigEntity
import com.dot.gallery.cloud.data.entity.CloudUploadPrefEntity
import com.dot.gallery.cloud.di.isNetworkSensitiveProviderType
import com.dot.gallery.cloud.di.retryCloudAuthentication
import com.dot.gallery.cloud.di.shouldReconfigureProvider
import com.dot.gallery.cloud.sync.CloudSyncSchedulePlan
import com.dot.gallery.cloud.sync.cloudSyncScheduleChanged
import com.dot.gallery.cloud.sync.cloudSyncSchedulePlan
import com.dot.gallery.cloud.sync.fetchAllCloudIndexPages
import com.dot.gallery.cloud.sync.fetchCloudIndexPage
import com.dot.gallery.cloud.sync.isCloudSyncDue
import com.dot.gallery.cloud.sync.shouldStartCloudIndex
import com.dot.gallery.cloud.sync.syncIncrementally
import com.dot.gallery.cloud.ui.AddServerUiState
import com.dot.gallery.cloud.ui.backup.backupScanRequired
import com.dot.gallery.cloud.ui.backup.backupSelectionKeys
import com.dot.gallery.cloud.ui.backup.newlyConnectedConfigIds
import com.dot.gallery.cloud.ui.mergeCloudServerConfig
import com.dot.gallery.core.Resource
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class CloudSyncRegressionTest {

    @Test
    fun editingAccountPreservesFieldsOutsideAccountWizard() {
        val oldEntity = config(
            id = 42L,
            intervalMinutes = 720,
            wifiOnly = false
        ).copy(
            lastConnected = 1234L,
            syncFolders = "Camera,Pictures",
            cellularPhotos = true,
            cellularVideos = true,
            requireCharging = true,
            syncAlbums = true,
            showBackupTotalProgress = false,
            showBackupDetailProgress = true,
            notifyBackupFailures = false,
            externalUrls = "[\"https://backup.example\"]",
            loadPreviewImage = false,
            loadOriginalImage = true,
            autoPlayVideos = false,
            loopVideos = true,
            forceOriginalVideo = true,
            verboseLogging = true,
            syncRemoteDeletions = true,
            preferRemoteImages = true,
            readOnlyMode = true
        )
        val state = AddServerUiState(
            providerType = ProviderType.IMMICH,
            serverUrl = "https://new.example/",
            apiKey = "new-key",
            username = "new-user",
            password = "new-password",
            displayName = "New name",
            syncEnabled = true,
            wifiOnly = true,
            autoUrlSwitch = true,
            localWifiSsid = " Home ",
            localServerUrl = "https://local.example/",
            savedConfigId = oldEntity.id
        )

        val merged = mergeCloudServerConfig(oldEntity, state) { "encrypted:$it" }

        assertEquals(
            oldEntity.copy(
                providerType = ProviderType.IMMICH,
                serverUrl = "https://new.example",
                apiKey = "encrypted:new-key",
                username = "new-user",
                encryptedPassword = "encrypted:new-password",
                displayName = "New name",
                isActive = true,
                syncEnabled = true,
                wifiOnly = true,
                autoUrlSwitch = true,
                localWifiSsid = "Home",
                localServerUrl = "https://local.example"
            ),
            merged
        )
    }

    @Test
    fun backupSelectionChangesRemainScopedToEachAccount() {
        val keys = backupSelectionKeys(
            listOf(
                CloudUploadPrefEntity(1L, 10L, ProviderType.IMMICH, uploadEnabled = true),
                CloudUploadPrefEntity(2L, 10L, ProviderType.IMMICH, uploadEnabled = true)
            )
        )

        assertEquals(setOf(1L to 10L, 2L to 10L), keys)
    }

    @Test
    fun newlyAddedAccountIsScannedWhenOtherAccountsHaveSnapshots() {
        assertTrue(backupScanRequired(setOf(1L, 2L), setOf(1L)))
        assertFalse(backupScanRequired(setOf(1L), setOf(1L, 2L)))
    }

    @Test
    fun reconnectingProviderRequestsFreshBackupVerification() {
        assertEquals(
            setOf(1L),
            newlyConnectedConfigIds(
                previous = emptyMap(),
                current = mapOf(1L to ConnectionState.CONNECTED)
            )
        )
        assertEquals(
            setOf(2L),
            newlyConnectedConfigIds(
                previous = mapOf(1L to ConnectionState.CONNECTED, 2L to ConnectionState.ERROR),
                current = mapOf(1L to ConnectionState.CONNECTED, 2L to ConnectionState.CONNECTED)
            )
        )
    }

    @Test
    fun scheduleUsesFastestEnabledIntervalAndAggregateNetworkPolicies() {
        val configs = listOf(
            config(id = 1L, intervalMinutes = 360, wifiOnly = true),
            config(id = 2L, intervalMinutes = 30, wifiOnly = false),
            config(id = 3L, intervalMinutes = 15, wifiOnly = true).copy(syncEnabled = false),
            config(id = 4L, intervalMinutes = 15, wifiOnly = true).copy(isActive = false)
        )

        assertEquals(
            CloudSyncSchedulePlan(
                intervalMinutes = 30L,
                syncWifiOnly = false,
                uploadWifiOnly = false
            ),
            cloudSyncSchedulePlan(configs)
        )
    }

    @Test
    fun scheduleIsRemovedWhenNoActiveAccountHasSyncEnabled() {
        val configs = listOf(
            config(id = 1L, intervalMinutes = 60, wifiOnly = true).copy(syncEnabled = false),
            config(id = 2L, intervalMinutes = 60, wifiOnly = true).copy(isActive = false)
        )

        assertEquals(null, cloudSyncSchedulePlan(configs))
    }

    @Test
    fun uploadCanUseMeteredNetworkWhenAnAccountAllowsCellularMedia() {
        val plan = cloudSyncSchedulePlan(
            listOf(config(id = 1L, intervalMinutes = 60, wifiOnly = true).copy(cellularPhotos = true))
        )

        assertEquals(true, plan?.syncWifiOnly)
        assertEquals(false, plan?.uploadWifiOnly)
    }

    @Test
    fun onlySchedulingPolicyChangesRequireReconciliation() {
        val oldConfig = config(id = 1L, intervalMinutes = 60, wifiOnly = true)

        assertFalse(cloudSyncScheduleChanged(oldConfig, oldConfig.copy(displayName = "Renamed")))
        assertTrue(cloudSyncScheduleChanged(oldConfig, oldConfig.copy(syncIntervalMinutes = 30)))
        assertTrue(cloudSyncScheduleChanged(oldConfig, oldConfig.copy(requireCharging = true)))
        assertTrue(cloudSyncScheduleChanged(oldConfig, oldConfig.copy(syncAlbums = true)))
    }

    @Test
    fun accountIntervalIsEnforcedInsideSharedWorker() {
        val hour = 60L * 60_000L

        assertFalse(isCloudSyncDue(lastSyncTimestamp = hour, intervalMinutes = 60, now = hour * 2 - 1))
        assertTrue(isCloudSyncDue(lastSyncTimestamp = hour, intervalMinutes = 60, now = hour * 2))
        assertTrue(isCloudSyncDue(lastSyncTimestamp = 0L, intervalMinutes = 1440, now = 1L))
    }

    @Test
    fun cloudIndexDoesNotStartWhileOffline() {
        assertFalse(shouldStartCloudIndex(effectiveOffline = true))
        assertTrue(shouldStartCloudIndex(effectiveOffline = false))
    }

    @Test
    fun networkRouteChangesForceNasProvidersToReconnect() {
        assertTrue(isNetworkSensitiveProviderType(ProviderType.SMB))
        assertTrue(isNetworkSensitiveProviderType(ProviderType.NFS))
        assertFalse(isNetworkSensitiveProviderType(ProviderType.IMMICH))
        assertTrue(
            shouldReconfigureProvider(
                lastResolvedUrl = "smb://nas/photos",
                resolvedUrl = "smb://nas/photos",
                connectionState = ConnectionState.CONNECTED,
                force = true
            )
        )
        assertFalse(
            shouldReconfigureProvider(
                lastResolvedUrl = "https://photos.example",
                resolvedUrl = "https://photos.example",
                connectionState = ConnectionState.CONNECTED,
                force = false
            )
        )
    }

    @Test
    fun persistedAccountAuthenticationRetriesTransientInstallFailure() = runTest {
        var attempts = 0
        val retryDelays = mutableListOf<Long>()

        retryCloudAuthentication(
            delayBeforeRetry = { retryDelays += it }
        ) {
            attempts++
            if (attempts < 3) throw IOException("Network is not ready")
        }

        assertEquals(3, attempts)
        assertEquals(listOf(1_000L, 2_000L), retryDelays)
    }

    @Test
    fun persistedAccountAuthenticationRetriesClassifiedServerFailure() = runTest {
        var attempts = 0

        retryCloudAuthentication(delayBeforeRetry = {}) {
            attempts++
            if (attempts == 1) {
                throw CloudConnectionException(
                    CloudConnectionErrorKind.SERVER,
                    "Server is starting"
                )
            }
        }

        assertEquals(2, attempts)
    }

    @Test
    fun persistedAccountAuthenticationDoesNotRetryCredentialFailure() = runTest {
        var attempts = 0

        val result = runCatching {
            retryCloudAuthentication(delayBeforeRetry = {}) {
                attempts++
                throw CloudConnectionException(
                    CloudConnectionErrorKind.AUTHENTICATION,
                    "Invalid API key",
                    IOException("Rejected request")
                )
            }
        }

        assertTrue(result.isFailure)
        assertEquals(1, attempts)
    }

    @Test
    fun stalledCloudIndexPageTimesOut() = runTest {
        var timedOut = false

        try {
            fetchCloudIndexPage(timeoutMillis = 1_000L) { awaitCancellation() }
        } catch (_: TimeoutCancellationException) {
            timedOut = true
        }

        assertTrue(timedOut)
    }

    @Test
    fun cloudIndexFetchesEveryPageUntilTheFirstShortPage() = runTest {
        val requestedPages = mutableListOf<Int>()
        val reportedTotals = mutableListOf<Int>()

        val result = fetchAllCloudIndexPages(
            pageSize = 2,
            maxPages = 10,
            fetchPage = { page, _ ->
                requestedPages += page
                Resource.Success(
                    when (page) {
                        0 -> listOf("a", "b")
                        1 -> listOf("c", "d")
                        else -> listOf("e")
                    }
                )
            },
            onPage = { _, total -> reportedTotals += total }
        )

        assertEquals(Result.success(5), result)
        assertEquals(listOf(0, 1, 2), requestedPages)
        assertEquals(listOf(2, 4, 5), reportedTotals)
    }

    @Test
    fun cloudIndexStopsAndFailsWhenAnyPageFails() = runTest {
        val requestedPages = mutableListOf<Int>()

        val result = fetchAllCloudIndexPages<String>(
            pageSize = 2,
            maxPages = 10,
            fetchPage = { page, _ ->
                requestedPages += page
                if (page == 0) Resource.Success(listOf("a", "b")) else Resource.Error("server unavailable")
            }
        )

        assertTrue(result.isFailure)
        assertEquals("server unavailable", result.exceptionOrNull()?.message)
        assertEquals(listOf(0, 1), requestedPages)
    }

    @Test
    fun failedFetchDoesNotAdvanceWatermark() = runBlocking {
        var watermark = 100L

        val result = syncIncrementally<String>(
            lastWatermark = watermark,
            nextWatermark = 200L,
            fetch = { Result.failure(IllegalStateException("offline")) },
            persist = {},
            advanceWatermark = { watermark = it }
        )

        assertTrue(result.isFailure)
        assertEquals(100L, watermark)
    }

    @Test
    fun failedPersistenceDoesNotAdvanceWatermark() = runBlocking {
        var watermark = 100L

        val result = syncIncrementally(
            lastWatermark = watermark,
            nextWatermark = 200L,
            fetch = { Result.success(listOf("change")) },
            persist = { throw IllegalStateException("database unavailable") },
            advanceWatermark = { watermark = it }
        )

        assertTrue(result.isFailure)
        assertEquals(100L, watermark)
    }

    @Test
    fun successfulPersistenceAdvancesWatermarkAfterWritingChanges() = runBlocking {
        val events = mutableListOf<String>()

        val result = syncIncrementally(
            lastWatermark = 100L,
            nextWatermark = 200L,
            fetch = {
                assertEquals(100L, it)
                Result.success(listOf("change"))
            },
            persist = { events += "persist:${it.single()}" },
            advanceWatermark = { events += "watermark:$it" }
        )

        assertEquals(Result.success(1), result)
        assertEquals(listOf("persist:change", "watermark:200"), events)
    }

    private fun config(
        id: Long,
        intervalMinutes: Int,
        wifiOnly: Boolean
    ) = CloudServerConfigEntity(
        id = id,
        providerType = ProviderType.IMMICH,
        serverUrl = "https://example.com/$id",
        syncEnabled = true,
        wifiOnly = wifiOnly,
        syncIntervalMinutes = intervalMinutes
    )
}
