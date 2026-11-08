/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.docs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.SharedLinkInfo
import com.dot.gallery.cloud.offline.CacheAssetRef
import com.dot.gallery.cloud.sync.CloudIndexProgressManager
import com.dot.gallery.cloud.sync.CloudUploadWorker
import com.dot.gallery.cloud.ui.backup.AccountBackupStatus
import com.dot.gallery.cloud.ui.backup.BackupUiState
import com.dot.gallery.cloud.ui.backup.CloudBackupDashboardContent
import com.dot.gallery.cloud.ui.backup.UploadDetailsContent
import com.dot.gallery.cloud.ui.backup.UploadDetailsUiState
import com.dot.gallery.cloud.ui.backup.UploadGroup
import com.dot.gallery.cloud.ui.backup.UploadQueueItem
import com.dot.gallery.cloud.ui.destinations.CloudDestinationsContent
import com.dot.gallery.cloud.ui.destinations.DestinationAccount
import com.dot.gallery.cloud.ui.offline.AccountCacheSheetState
import com.dot.gallery.cloud.ui.offline.AlbumCacheEntry
import com.dot.gallery.cloud.ui.offline.OfflineAccount
import com.dot.gallery.cloud.ui.offline.OfflineAvailabilityStatus
import com.dot.gallery.cloud.ui.offline.OfflineModeContent
import com.dot.gallery.cloud.ui.offline.OfflineModeContentActions
import com.dot.gallery.cloud.ui.offline.OfflineModeDialog
import com.dot.gallery.cloud.ui.offline.OfflineModeUiState
import com.dot.gallery.cloud.ui.sharing.SharedLinksContent
import com.dot.gallery.cloud.ui.sharing.SharedLinksContentActions
import com.dot.gallery.cloud.ui.sharing.SharedLinksUiState
import com.dot.gallery.cloud.ui.space.FreeUpSpaceContent
import com.dot.gallery.cloud.ui.space.FreeUpSpaceUiState
import com.dot.gallery.cloud.ui.sync.SyncStatusUiState

/**
 * Cloud "use" surfaces — backup dashboard, upload queue, sync status,
 * destinations matrix, offline cache, free-up-space and shared links — hosting
 * the real Content composables over fictional fixtures. `photos.example.com`
 * and `nas.example.com` do not resolve; no sync worker, deletion, cache clear,
 * pin or share-link write ever runs. All values describe the same 12-item
 * sample library ([DocsGalleryData]) across the two sample accounts.
 */
val cloudUseScenes: List<DocsScene> = listOf(
    DocsScene(
        id = "cloud-backup-dashboard",
        title = "Cloud backup dashboard",
        pathTrace = listOf("Library", "Backup & Sync"),
        states = listOf("overview", "uploading", "indexing", "scrolled"),
        swipedStates = listOf("scrolled"),
    ) { state -> DocsBackupDashboardScene(state) },
    DocsScene(
        id = "cloud-upload-details",
        title = "Cloud upload details",
        pathTrace = listOf("Library", "Backup & Sync", "Upload details"),
        states = listOf("queued", "uploading"),
    ) { state -> DocsUploadDetailsScene(state) },
    DocsScene(
        id = "cloud-destinations",
        title = "Cloud destinations",
        pathTrace = listOf("Library", "Backup & Sync", "Destinations"),
        states = listOf("accounts", "single-account", "choose-clouds"),
        captureWindow = true,
    ) { state -> DocsDestinationsScene(state) },
    DocsScene(
        id = "cloud-offline-cache",
        title = "Offline cache",
        pathTrace = listOf("Settings", "Cloud accounts", "Offline & Cache"),
        states = listOf("ready", "offline", "budget", "account-cache"),
        captureWindow = true,
    ) { state -> DocsOfflineCacheScene(state) },
    DocsScene(
        id = "cloud-free-space",
        title = "Free up space",
        pathTrace = listOf("Settings", "Cloud accounts", "Home photos", "Free Up Space"),
        states = listOf("ready", "scanning", "automatic"),
    ) { state -> DocsFreeSpaceScene(state) },
    DocsScene(
        id = "cloud-shared-links",
        title = "Shared links",
        pathTrace = listOf("Library", "Shared Links"),
        states = listOf("list", "edit"),
        captureWindow = true,
    ) { state -> DocsSharedLinksScene(state) },
)

// ── Fixtures ──────────────────────────────────────────────────────────────

private const val DOCS_EPOCH_MS = DOCS_DEMO_EPOCH_SECONDS * 1_000L
private const val MB = 1_024L * 1_024L

private val dashboardAccount = AccountBackupStatus(
    configId = 11L,
    providerType = ProviderType.IMMICH,
    accountLabel = "Home photos",
    enabledAlbumCount = 3,
    totalAssets = 12,
    verifiedCount = 8,
    assumedCount = 1,
    syncEnabled = true,
)

private val dashboardState = BackupUiState(
    totalAssets = 12,
    verifiedCount = 8,
    assumedCount = 1,
    unknownCount = 3,
    backedUpCount = 8,
    remainderCount = 4,
    enabledAlbumCount = 3,
    accounts = listOf(dashboardAccount),
)

/** The last four docs photos queued for upload, grouped exactly like the VM (account × album). */
private val uploadGroups: List<UploadGroup> by lazy {
    DocsGalleryData.mediaItems().takeLast(4)
        .groupBy { it.albumID }
        .map { (albumId, items) ->
            UploadGroup(
                key = "11:$albumId",
                providerType = ProviderType.IMMICH,
                accountLabel = "Home photos",
                albumLabel = items.first().albumLabel,
                items = items.map {
                    UploadQueueItem(
                        mediaId = it.id,
                        uri = it.uri,
                        label = it.label,
                        sizeBytes = it.size,
                    )
                },
                totalBytes = items.sumOf { it.size },
            )
        }
}

private fun uploadState(running: Boolean) = UploadDetailsUiState(
    isWorkerRunning = running,
    phase = if (running) CloudUploadWorker.PHASE_UPLOADING else "",
    currentFileName = if (running) "Lake_wallpaper.jpg" else "",
    totalItems = if (running) 4 else 0,
    groups = uploadGroups,
    pendingCount = uploadGroups.sumOf { it.items.size },
    pendingBytes = uploadGroups.sumOf { it.totalBytes },
)

// ── Backup dashboard ──────────────────────────────────────────────────────

/**
 * The real [CloudBackupDashboardContent]: `overview` is the healthy sample
 * account, `uploading` shows the live upload card mid-run, `indexing` the
 * remote-indexing card. Navigation callbacks are no-ops — docs never leave
 * the scene.
 */
@Composable
fun DocsBackupDashboardScene(state: String?) {
    CloudBackupDashboardContent(
        state = if (state == "uploading") dashboardState.copy(isUploading = true) else dashboardState,
        runningAccounts = if (state == "uploading") setOf(11L) else emptySet(),
        indexState = if (state == "indexing") {
            CloudIndexProgressManager.IndexState(
                active = mapOf(
                    11L to CloudIndexProgressManager.AccountIndex(11L, "Home photos", 8),
                ),
            )
        } else {
            CloudIndexProgressManager.IndexState()
        },
        syncState = SyncStatusUiState(
            totalRemote = 12,
            totalCached = 12,
            synced = 8,
            remoteOnly = 4,
        ),
        uploadState = uploadState(running = state == "uploading"),
        onScanBackupStatus = {},
        onSetAutoSync = { _, _ -> },
        onBackup = { _ -> },
        onSyncNow = {},
        onNavigateToAlbumPicker = { _ -> },
        onNavigateToServiceSettings = { _ -> },
        onNavigateToBackupOptions = {},
        onNavigateToUploadDetails = {},
        onNavigateToAccounts = {},
    )
}


@Composable
fun DocsUploadDetailsScene(state: String?) {
    val context = LocalContext.current
    // The fixture rows carry fixture:// URIs the real AsyncImage cannot load —
    // point each queued item at its drawable's MediaStore-owned preview URI
    // (ids, counts, sizes and groups stay exactly as captured).
    val stateWithRealUris = remember(state) {
        uploadState(running = state == "uploading").let { s ->
            s.copy(
                groups = s.groups.map { group ->
                    group.copy(
                        items = group.items.map { item ->
                            val drawable = DocsGalleryData.drawableKeyByLabel[item.label]
                            if (drawable != null) {
                                item.copy(
                                    uri = DocsMediaStoreFixture.image(
                                        context = context,
                                        resourcePath = "drawable/docs_$drawable.jpg",
                                        displayName = "Docs_$drawable.jpg",
                                        ageDays = 0,
                                        favorite = false,
                                    ).uri
                                )
                            } else item
                        }
                    )
                }
            )
        }
    }
    UploadDetailsContent(state = stateWithRealUris)
}

// ── Destinations ──────────────────────────────────────────────────────────

/**
 * The real [CloudDestinationsContent]: Camera targets both sample clouds,
 * Screenshots none, Download only the NAS — matching the destinations prose.
 */
@Composable
fun DocsDestinationsScene(state: String?) {
    CloudDestinationsContent(
        accounts = listOf(
            DestinationAccount(11L, ProviderType.IMMICH, "Home photos"),
            DestinationAccount(12L, ProviderType.SMB, "My NAS"),
        ),
        albums = DocsGalleryData.albums(),
        enabledByAlbum = mapOf(
            DocsGalleryData.ALBUM_CAMERA to setOf(11L, 12L),
            DocsGalleryData.ALBUM_SCREENSHOTS to emptySet(),
            DocsGalleryData.ALBUM_DOWNLOAD to setOf(12L),
        ),
        deleteLocalByAlbum = mapOf(
            DocsGalleryData.ALBUM_CAMERA to false,
            DocsGalleryData.ALBUM_SCREENSHOTS to false,
            DocsGalleryData.ALBUM_DOWNLOAD to false,
        ),
        filterConfigId = if (state == "single-account") 11L else -1L,
        onAddAccount = {},
        onSetDeleteLocal = { _, _ -> },
        onSetDestination = { _, _, _ -> },
        onSetAlbumAllClouds = { _, _ -> },
        initialSheetAlbumId = if (state == "choose-clouds") DocsGalleryData.ALBUM_CAMERA else null,
    )
}

// ── Offline cache ─────────────────────────────────────────────────────────

/**
 * The real [OfflineModeContent] over one pinned sample account (24/24
 * variants = 12 photos × thumbnail+preview). `budget` opens the cache-limit
 * dialog, `account-cache` the per-account sheet. All actions are no-ops.
 */
@Composable
fun DocsOfflineCacheScene(state: String?) {
    val account = OfflineAccount(
        configId = 11L,
        label = "Home photos",
        providerType = ProviderType.IMMICH,
        pinned = true,
        availabilityStatus = OfflineAvailabilityStatus.COMPLETE,
        downloadedVariants = 24,
        totalVariants = 24,
        cacheBytes = 32L * MB,
    )
    OfflineModeContent(
        state = OfflineModeUiState(
            forceOffline = state == "offline",
            connected = true,
            budgetMb = 512,
            autoCacheBytes = 8L * MB,
            pinnedBytes = 24L * MB,
            accounts = listOf(account),
        ),
        accountSheet = if (state == "account-cache") {
            AccountCacheSheetState(
                configId = 11L,
                label = "Home photos",
                loading = false,
                totalBytes = 32L * MB,
                albums = listOf(
                    AlbumCacheEntry(
                        remoteId = "docs-album-camera",
                        name = "Camera",
                        cacheBytes = 20L * MB,
                        refs = listOf(CacheAssetRef(ProviderType.IMMICH, 11L, "docs-synced-1")),
                    ),
                    AlbumCacheEntry(
                        remoteId = "docs-album-screenshots",
                        name = "Screenshots",
                        cacheBytes = 4L * MB,
                        refs = listOf(CacheAssetRef(ProviderType.IMMICH, 11L, "docs-synced-2")),
                    ),
                    AlbumCacheEntry(
                        remoteId = "docs-album-download",
                        name = "Download",
                        cacheBytes = 8L * MB,
                        refs = listOf(CacheAssetRef(ProviderType.IMMICH, 11L, "docs-synced-3")),
                    ),
                ),
            )
        } else {
            null
        },
        actions = OfflineModeContentActions(),
        initialDialog = if (state == "budget") OfflineModeDialog.BUDGET else OfflineModeDialog.NONE,
    )
}

// ── Free up space ─────────────────────────────────────────────────────────

/**
 * The real [FreeUpSpaceContent]. The only eligible item is docs photo 12 —
 * 365 days old, not a favorite — so the "ready" pose reports exactly one
 * removable copy. No deletion path is ever exercised.
 */
@Composable
fun DocsFreeSpaceScene(state: String?) {
    FreeUpSpaceContent(
        state = FreeUpSpaceUiState(
            isScanning = state == "scanning",
            scannedCount = if (state == "scanning") 5 else 12,
            totalLocal = 12,
            backedUpItems = if (state == "scanning") {
                emptyList()
            } else {
                listOf(DocsGalleryData.mediaItems().first { it.id == 12L })
            },
            preferencesLoaded = true,
            keepFavorites = true,
            cutoffDays = 90,
            autoEnabled = state == "automatic",
            autoIntervalDays = 3,
            message = if (state == "ready" || state == "automatic") {
                "Verified 1 local copies on every configured destination."
            } else {
                ""
            },
        ),
        onKeepFavorites = {},
        onCutoffDays = {},
        onAutoEnabled = {},
        onAutoIntervalDays = {},
        onScan = {},
        onBeginDeletion = {},
        onCancelDeletion = {},
        // First fold ends above the Scan row; these poses exist to show the
        // scan result ("Remove N local copies") and the frequency options.
        initialScrollIndex = when (state) {
            "automatic" -> 10
            "scanning", "ready" -> 7
            else -> 0
        },
    )
}

// ── Shared links ──────────────────────────────────────────────────────────

private val sampleSharedLink = SharedLinkInfo(
    id = "docs-holiday",
    key = "example-holiday",
    type = "ALBUM",
    description = "Holiday photos",
    expiresAt = DOCS_EPOCH_MS + 7L * 86_400_000L,
    allowDownload = true,
    allowUpload = false,
    showMetadata = false,
    password = "example-only",
    assetCount = 3,
    providerType = ProviderType.IMMICH,
    serverConfigId = 11L,
    createdAt = DOCS_EPOCH_MS,
    albumName = "Holiday 2025",
)

/**
 * The real [SharedLinksContent] with one existing example album link; `edit`
 * opens the actual edit sheet. Copy/delete/update are no-ops — nothing here
 * creates a link.
 */
@Composable
fun DocsSharedLinksScene(state: String?) {
    SharedLinksContent(
        state = SharedLinksUiState(
            allLinks = listOf(sampleSharedLink),
            serverBaseUrls = mapOf(11L to "https://photos.example.com"),
            accountLabels = mapOf(11L to "Home photos"),
        ),
        actions = SharedLinksContentActions(),
        initialEditLinkId = if (state == "edit") sampleSharedLink.id else null,
    )
}
