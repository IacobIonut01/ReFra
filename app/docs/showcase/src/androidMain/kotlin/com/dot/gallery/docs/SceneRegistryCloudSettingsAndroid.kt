/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.docs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dot.gallery.cloud.core.ConnectionState
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.ui.CloudAccountsViewModel
import com.dot.gallery.cloud.ui.backup.BackupOptionsContent
import com.dot.gallery.cloud.ui.settings.CloudAdvancedSettingsContent
import com.dot.gallery.cloud.ui.settings.CloudNetworkingContent
import com.dot.gallery.cloud.ui.settings.CloudNotificationSettingsContent
import com.dot.gallery.cloud.ui.settings.CloudProviderSettingsContent
import com.dot.gallery.cloud.ui.settings.CloudProviderSettingsContentActions
import com.dot.gallery.cloud.ui.settings.CloudProviderSettingsContentState
import com.dot.gallery.cloud.ui.settings.CloudProviderSettingsDialog
import com.dot.gallery.cloud.ui.settings.CloudViewerSettingsContent
import com.dot.gallery.data.dao.PendingDownloadInfo
import com.dot.gallery.data.entity.CloudServerConfigEntity

/**
 * Per-account cloud settings scenes. They host the real Content composables
 * the production settings screens delegate to, over a fictional sample account
 * — `photos.example.com`/`nas.example.com` do not resolve, every switch and
 * dialog mutates only the remembered sample config, and no sync, download,
 * cache clear or deletion ever runs.
 */
val cloudSettingsScenes: List<DocsScene> = listOf(
    DocsScene(
        id = "cloud-account-settings",
        title = "Cloud account settings",
        pathTrace = listOf("Settings", "Cloud accounts", "Home photos"),
        states = listOf("overview", "syncing", "downloads", "scrolled"),
        swipedStates = listOf("scrolled"),
    ) { state -> DocsCloudAccountSettingsScene(state) },
    DocsScene(
        id = "cloud-account-dialog",
        title = "Account settings dialogs",
        pathTrace = listOf("Settings", "Cloud accounts", "Home photos"),
        states = listOf("interval", "download", "remove-copies", "delete-account"),
        captureWindow = true,
    ) { state -> DocsCloudAccountSettingsScene(state, dialogFor(state)) },
    DocsScene(
        id = "cloud-notification-options",
        title = "Cloud notification options",
        pathTrace = listOf("Settings", "Cloud accounts", "Home photos", "Notifications"),
        states = listOf("default", "scrolled"),
        swipedStates = listOf("scrolled"),
        presentation = "component",
        widthDp = 412,
        heightDp = 540,
    ) { DocsNotificationOptionsScene() },
    DocsScene(
        id = "cloud-viewer-options",
        title = "Cloud viewer options",
        pathTrace = listOf("Settings", "Cloud accounts", "Home photos", "Viewer"),
        states = listOf("default", "scrolled"),
        swipedStates = listOf("scrolled"),
        presentation = "component",
        widthDp = 412,
        heightDp = 320,
    ) { DocsViewerOptionsScene() },
    DocsScene(
        id = "cloud-advanced-options",
        title = "Cloud advanced options",
        pathTrace = listOf("Settings", "Cloud accounts", "Home photos", "Advanced"),
        states = listOf("normal", "read-only"),
    ) { state -> DocsAdvancedOptionsScene(state) },
    DocsScene(
        id = "cloud-backup-options",
        title = "Cloud backup options",
        pathTrace = listOf("Settings", "Cloud accounts", "Backup options"),
        states = listOf("immich", "folders"),
    ) { state -> DocsBackupOptionsScene(state) },
    DocsScene(
        id = "cloud-network-options",
        title = "Cloud networking",
        pathTrace = listOf("Settings", "Cloud accounts", "Home photos", "Networking"),
        states = listOf("local", "external", "scrolled"),
        swipedStates = listOf("scrolled"),
    ) { state -> DocsNetworkOptionsScene(state) },
)

// ── Fixtures ──────────────────────────────────────────────────────────────

/** Fictional sample Immich account — the host does not resolve, nothing syncs. */
private fun sampleAccountConfig() = CloudServerConfigEntity(
    id = 11L,
    providerType = ProviderType.IMMICH,
    serverUrl = "https://photos.example.com",
    displayName = "Home photos",
    syncEnabled = true,
    wifiOnly = true,
    syncIntervalMinutes = 60,
    downloadRemoteEnabled = false,
    downloadVideos = true,
)

/** Fictional path-based sample — SMB gets upload folders, Immich does not. */
private fun sampleSmbConfig() = CloudServerConfigEntity(
    id = 12L,
    providerType = ProviderType.SMB,
    serverUrl = "smb://nas.example.com/Photos",
    displayName = "My NAS",
    username = "alex",
    uploadBasePath = "ReFra",
    uploadVideosPath = "ReFra/Videos",
)

private val sampleCopiesInfo = PendingDownloadInfo(itemCount = 3, totalBytes = 6L * 1024 * 1024)

private fun dialogFor(state: String?): CloudProviderSettingsDialog = when (state) {
    "interval" -> CloudProviderSettingsDialog.SYNC_INTERVAL
    "download" -> CloudProviderSettingsDialog.DOWNLOAD
    "remove-copies" -> CloudProviderSettingsDialog.REMOVE_DOWNLOADS
    "delete-account" -> CloudProviderSettingsDialog.DELETE_ACCOUNT
    else -> CloudProviderSettingsDialog.NONE
}

// ── Account settings ──────────────────────────────────────────────────────

/**
 * The real [CloudProviderSettingsContent] over the sample Immich account.
 * `syncing` poses the SyncProgress row mid-run; `downloads` enables
 * remote-to-local downloads and reports three already-downloaded copies so the
 * videos switch and remove-copies row show. Dialog states reuse the same
 * screen with the matching dialog open.
 */
@Composable
fun DocsCloudAccountSettingsScene(
    state: String?,
    initialDialog: CloudProviderSettingsDialog = CloudProviderSettingsDialog.NONE,
) {
    var config by remember(state) {
        mutableStateOf(
            if (state == "downloads") {
                sampleAccountConfig().copy(downloadRemoteEnabled = true)
            } else {
                sampleAccountConfig()
            }
        )
    }
    var progress by remember(state) {
        mutableStateOf<CloudAccountsViewModel.SyncProgress?>(
            if (state == "syncing") {
                CloudAccountsViewModel.SyncProgress(
                    isSyncing = true,
                    message = "Checking example library…",
                )
            } else {
                null
            }
        )
    }
    CloudProviderSettingsContent(
        state = CloudProviderSettingsContentState(
            config = config,
            connectionState = ConnectionState.CONNECTED,
            syncProgress = progress,
            copiesInfo = if (state == "downloads" || state == "remove-copies") sampleCopiesInfo else null,
        ),
        actions = CloudProviderSettingsContentActions(
            updateConfig = { transform -> config = config.transform() },
            syncNow = {
                progress = CloudAccountsViewModel.SyncProgress(
                    isSyncing = true,
                    message = "Checking example library…",
                )
            },
            pendingDownloadStats = { sampleCopiesInfo },
        ),
        initialDialog = initialDialog,
    )
}

// ── Sub-page options ──────────────────────────────────────────────────────

@Composable
fun DocsNotificationOptionsScene() {
    var config by remember { mutableStateOf(sampleAccountConfig()) }
    CloudNotificationSettingsContent(
        config = config,
        onUpdateConfig = { transform -> config = config.transform() },
    )
}

@Composable
fun DocsViewerOptionsScene() {
    var config by remember { mutableStateOf(sampleAccountConfig().copy(loopVideos = true)) }
    CloudViewerSettingsContent(
        config = config,
        onUpdateConfig = { transform -> config = config.transform() },
    )
}

@Composable
fun DocsAdvancedOptionsScene(state: String?) {
    var config by remember(state) {
        mutableStateOf(sampleAccountConfig().copy(readOnlyMode = state == "read-only"))
    }
    var cacheClearing by remember { mutableStateOf(false) }
    CloudAdvancedSettingsContent(
        config = config,
        cacheClearing = cacheClearing,
        onUpdateConfig = { transform -> config = config.transform() },
        // The production callback clears the app-wide image cache; here it only
        // flips the sample busy label — nothing is evicted.
        onClearImageCache = { cacheClearing = true },
    )
}

@Composable
fun DocsBackupOptionsScene(state: String?) {
    var config by remember(state) {
        mutableStateOf(
            when (state) {
                "folders" -> sampleSmbConfig()
                else -> sampleAccountConfig().copy(
                    requireCharging = true,
                    cellularPhotos = false,
                    cellularVideos = false,
                    syncAlbums = true,
                )
            }
        )
    }
    BackupOptionsContent(
        config = config,
        global = false,
        onUpdateConfig = { transform -> config = config.transform() },
    )
}

@Composable
fun DocsNetworkOptionsScene(state: String?) {
    var config by remember(state) {
        mutableStateOf(
            sampleAccountConfig().copy(
                autoUrlSwitch = true,
                localWifiSsid = "Home Wi-Fi",
                localServerUrl = "https://photos.home.example.com",
                externalUrls = "[\"https://photos.example.com\"]",
            )
        )
    }
    // Resolved exactly like the wrapper does — example values, no probe.
    val localActive = state != "external"
    CloudNetworkingContent(
        config = config,
        effectiveUrl = if (localActive) {
            config.localServerUrl
        } else {
            "https://photos.example.com"
        },
        localActive = localActive,
        onUpdateConfig = { transform -> config = config.transform() },
    )
}
