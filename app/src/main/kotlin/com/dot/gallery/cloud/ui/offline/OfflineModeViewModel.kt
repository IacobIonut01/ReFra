/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.capabilities.RemoteMediaProvider
import com.dot.gallery.cloud.data.dao.CloudMediaDao
import com.dot.gallery.cloud.data.dao.CloudOfflinePinDao
import com.dot.gallery.cloud.data.dao.CloudServerConfigDao
import com.dot.gallery.cloud.data.entity.CloudMediaEntity
import com.dot.gallery.cloud.data.entity.CloudOfflinePinEntity
import com.dot.gallery.cloud.offline.CacheAssetRef
import com.dot.gallery.cloud.offline.CloudMediaCache
import com.dot.gallery.cloud.offline.OfflineModeManager
import com.dot.gallery.cloud.sync.CloudOfflineDownloadWorker
import com.dot.gallery.feature_node.presentation.util.printWarn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class OfflineAvailabilityStatus {
    NOT_PINNED,
    PINNED,
    QUEUED,
    DOWNLOADING,
    PARTIAL,
    COMPLETE,
    FAILED
}

enum class OfflineDownloadWorkState { IDLE, QUEUED, RUNNING, SUCCEEDED, FAILED }

data class OfflineCoverage(val downloadedVariants: Int = 0, val totalVariants: Int = 0)

data class OfflineAccount(
    val configId: Long,
    val label: String,
    val providerType: ProviderType,
    val pinned: Boolean,
    val availabilityStatus: OfflineAvailabilityStatus = OfflineAvailabilityStatus.NOT_PINNED,
    val downloadedVariants: Int = 0,
    val totalVariants: Int = 0,
    val cacheBytes: Long = 0L
)

internal fun offlineAvailabilityStatus(
    pinned: Boolean,
    coverage: OfflineCoverage,
    workState: OfflineDownloadWorkState
): OfflineAvailabilityStatus = when {
    !pinned -> OfflineAvailabilityStatus.NOT_PINNED
    coverage.totalVariants == 0 -> OfflineAvailabilityStatus.PINNED
    coverage.downloadedVariants >= coverage.totalVariants -> OfflineAvailabilityStatus.COMPLETE
    workState == OfflineDownloadWorkState.FAILED -> OfflineAvailabilityStatus.FAILED
    workState == OfflineDownloadWorkState.QUEUED && coverage.downloadedVariants == 0 ->
        OfflineAvailabilityStatus.QUEUED
    workState == OfflineDownloadWorkState.RUNNING && coverage.downloadedVariants == 0 ->
        OfflineAvailabilityStatus.DOWNLOADING
    coverage.downloadedVariants > 0 -> OfflineAvailabilityStatus.PARTIAL
    else -> OfflineAvailabilityStatus.PINNED
}

/** On-disk cache usage for one album of an account (populated from a network album fetch). */
data class AlbumCacheEntry(
    val remoteId: String,
    val name: String,
    val cacheBytes: Long,
    val refs: List<CacheAssetRef>,
    val clearing: Boolean = false
)

/** State of the per-account cache-management bottom sheet. */
data class AccountCacheSheetState(
    val configId: Long,
    val label: String,
    val loading: Boolean = true,
    val error: String? = null,
    val totalBytes: Long = 0L,
    val albums: List<AlbumCacheEntry> = emptyList()
)

data class OfflineModeUiState(
    val forceOffline: Boolean = false,
    val connected: Boolean = true,
    val cacheOnView: Boolean = true,
    val cacheWifiOnly: Boolean = false,
    val budgetMb: Int = OfflineModeManager.DEFAULT_BUDGET_MB,
    val autoCacheBytes: Long = 0L,
    val pinnedBytes: Long = 0L,
    val accounts: List<OfflineAccount> = emptyList(),
    val downloading: Boolean = false,
    val downloadDone: Int = 0,
    val downloadTotal: Int = 0
)

@HiltViewModel
class OfflineModeViewModel @Inject constructor(
    private val manager: OfflineModeManager,
    private val cache: CloudMediaCache,
    private val pinDao: CloudOfflinePinDao,
    private val configDao: CloudServerConfigDao,
    private val cloudMediaDao: CloudMediaDao,
    private val registry: ProviderRegistry,
    private val workManager: WorkManager
) : ViewModel() {

    /** auto bytes, pinned bytes, per-account cache bytes and pinned coverage keyed by configId. */
    private val _sizes = MutableStateFlow(CacheSizes())
    private val _downloadInfo = MutableStateFlow(DownloadInfo())

    private val _accountSheet = MutableStateFlow<AccountCacheSheetState?>(null)
    val accountSheet: StateFlow<AccountCacheSheetState?> = _accountSheet.asStateFlow()
    private var sheetJob: Job? = null

    val uiState: StateFlow<OfflineModeUiState> = combine(
        combine(
            manager.forceOffline,
            manager.connected,
            manager.cacheOnView,
            manager.cacheWifiOnly,
            manager.budgetMbFlow
        ) { force, connected, onView, wifiOnly, budget ->
            OfflineModeUiState(
                forceOffline = force,
                connected = connected,
                cacheOnView = onView,
                cacheWifiOnly = wifiOnly,
                budgetMb = budget
            )
        },
        configDao.getAll(),
        pinDao.getAll(),
        _sizes,
        _downloadInfo
    ) { base, configs, pins, sizes, dl ->
        val pinnedIds = pins.map { it.serverConfigId }.toSet()
        base.copy(
            autoCacheBytes = sizes.auto,
            pinnedBytes = sizes.pinned,
            accounts = configs.filter { it.isActive }.map { c ->
                val pinned = c.id in pinnedIds
                val coverage = sizes.coverage[c.id] ?: OfflineCoverage()
                OfflineAccount(
                    configId = c.id,
                    label = c.displayName.ifBlank { c.providerType.displayName },
                    providerType = c.providerType,
                    pinned = pinned,
                    availabilityStatus = offlineAvailabilityStatus(pinned, coverage, dl.state),
                    downloadedVariants = coverage.downloadedVariants,
                    totalVariants = coverage.totalVariants,
                    cacheBytes = sizes.perAccount[c.id] ?: 0L
                )
            },
            downloading = dl.state == OfflineDownloadWorkState.QUEUED ||
                dl.state == OfflineDownloadWorkState.RUNNING,
            downloadDone = dl.done,
            downloadTotal = dl.total
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OfflineModeUiState())

    init {
        refreshSizes()
        observeDownload()
    }

    fun refreshSizes() {
        viewModelScope.launch {
            _sizes.value = withContext(Dispatchers.IO) {
                val mediaByAccount = cloudMediaDao.getAllAsync().groupBy { it.serverConfigId }
                val perAccount = mediaByAccount.mapValues { (_, list) ->
                    cache.sizeForAssets(list.map { it.toRef() })
                }
                val coverage = mediaByAccount.mapValues { (_, list) ->
                    val refs = list.map { it.toRef() }
                    OfflineCoverage(
                        downloadedVariants = cache.pinnedVariantCount(refs, OFFLINE_SIZE_LABELS),
                        totalVariants = refs.size * OFFLINE_SIZE_LABELS.size
                    )
                }
                CacheSizes(cache.autoSizeBytes(), cache.pinnedSizeBytes(), perAccount, coverage)
            }
        }
    }

    private fun observeDownload() {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(CloudOfflineDownloadWorker.WORK_NAME).collect { infos ->
                val info = infos.firstOrNull {
                    it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING ||
                        it.state == WorkInfo.State.BLOCKED
                } ?: infos.lastOrNull()
                val data = if (info?.state?.isFinished == true) info.outputData else info?.progress
                _downloadInfo.value = DownloadInfo(
                    state = when (info?.state) {
                        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> OfflineDownloadWorkState.QUEUED
                        WorkInfo.State.RUNNING -> OfflineDownloadWorkState.RUNNING
                        WorkInfo.State.SUCCEEDED -> OfflineDownloadWorkState.SUCCEEDED
                        WorkInfo.State.FAILED -> OfflineDownloadWorkState.FAILED
                        else -> OfflineDownloadWorkState.IDLE
                    },
                    done = data?.getInt(CloudOfflineDownloadWorker.KEY_DONE, 0) ?: 0,
                    total = data?.getInt(CloudOfflineDownloadWorker.KEY_TOTAL, 0) ?: 0,
                    failed = data?.getInt(CloudOfflineDownloadWorker.KEY_FAILED, 0) ?: 0,
                    error = data?.getString(CloudOfflineDownloadWorker.KEY_ERROR)?.ifBlank { null }
                )
                refreshSizes()
            }
        }
    }

    fun setForceOffline(enabled: Boolean) = viewModelScope.launch { manager.setForceOffline(enabled) }
    fun setCacheOnView(enabled: Boolean) = viewModelScope.launch { manager.setCacheOnView(enabled) }
    fun setCacheWifiOnly(enabled: Boolean) = viewModelScope.launch { manager.setCacheWifiOnly(enabled) }
    fun setBudgetMb(mb: Int) = viewModelScope.launch {
        manager.setBudgetMb(mb)
        withContext(Dispatchers.IO) { cache.trimAuto(mb.toLong() * 1024L * 1024L) }
        refreshSizes()
    }

    fun pinAccount(configId: Long, label: String) {
        viewModelScope.launch {
            val config = configDao.getById(configId) ?: return@launch
            pinDao.upsert(
                CloudOfflinePinEntity(
                    serverConfigId = configId,
                    providerType = config.providerType,
                    label = label
                )
            )
            CloudOfflineDownloadWorker.triggerNow(workManager, manager.cacheWifiOnlyNow)
        }
    }

    fun unpinAccount(configId: Long, removeDownloadedData: Boolean) {
        viewModelScope.launch {
            pinDao.deleteByConfig(configId)
            withContext(Dispatchers.IO) {
                workManager.cancelUniqueWork(CloudOfflineDownloadWorker.WORK_NAME).result.get()
            }
            if (removeDownloadedData) {
                val refs = withContext(Dispatchers.IO) {
                    cloudMediaDao.getByServerConfig(configId).first().map { it.toRef() }
                }
                withContext(Dispatchers.IO) { cache.clearPinnedForAssets(refs) }
            }
            // Continue any remaining accounts after cancelling the worker that may still have held
            // this account in its initial target snapshot.
            if (pinDao.getAllAsync().isNotEmpty()) {
                CloudOfflineDownloadWorker.triggerNow(workManager, manager.cacheWifiOnlyNow)
            }
            refreshSizes()
        }
    }

    fun downloadNow() = CloudOfflineDownloadWorker.triggerNow(workManager, manager.cacheWifiOnlyNow)

    fun clearAutoCache() = viewModelScope.launch {
        withContext(Dispatchers.IO) { cache.clearAuto() }
        refreshSizes()
    }

    fun clearAllCache() = viewModelScope.launch {
        pinDao.getAllAsync().forEach { pinDao.deleteByConfig(it.serverConfigId) }
        withContext(Dispatchers.IO) {
            runCatching {
                workManager.cancelUniqueWork(CloudOfflineDownloadWorker.WORK_NAME).result.get()
            }
            cache.clearAll()
        }
        refreshSizes()
    }

    // === Per-account / per-album cache management ===

    /**
     * Open the cache sheet for [configId]. Computes the account's total cached bytes from the
     * local DB, then fetches the account's albums from the server and computes each album's
     * cached size (album membership is a remote-only concept, so this requires the network).
     */
    fun openAccountCache(configId: Long, label: String) {
        sheetJob?.cancel()
        _accountSheet.value = AccountCacheSheetState(configId = configId, label = label, loading = true)
        sheetJob = viewModelScope.launch {
            val provider = registry.getByConfigId(configId) as? RemoteMediaProvider
            val assets = withContext(Dispatchers.IO) { cloudMediaDao.getByServerConfig(configId).first() }
            val totalBytes = withContext(Dispatchers.IO) { cache.sizeForAssets(assets.map { it.toRef() }) }
            _accountSheet.updateIf(configId) { it.copy(totalBytes = totalBytes) }

            if (provider == null) {
                _accountSheet.updateIf(configId) {
                    it.copy(loading = false, error = "Account is offline — connect to manage albums")
                }
                return@launch
            }

            val albums = runCatching { provider.getRemoteAlbums().first() }
                .onFailure { printWarn("cloud.offline", "remote album fetch failed: $it") }
                .getOrNull()?.data ?: emptyList()
            if (albums.isEmpty()) {
                _accountSheet.updateIf(configId) { it.copy(loading = false) }
                return@launch
            }
            val entries = mutableListOf<AlbumCacheEntry>()
            for (album in albums) {
                val media = runCatching { provider.getRemoteAlbumMedia(album.remoteId).first() }
                    .onFailure { printWarn("cloud.offline", "remote album media fetch failed: $it") }
                    .getOrNull()?.data ?: emptyList()
                val refs = media.map { it.toRef() }
                val bytes = withContext(Dispatchers.IO) { cache.sizeForAssets(refs) }
                entries += AlbumCacheEntry(album.remoteId, album.name, bytes, refs)
                _accountSheet.updateIf(configId) { it.copy(albums = entries.toList()) }
            }
            _accountSheet.updateIf(configId) {
                it.copy(loading = false, albums = entries.sortedByDescending { e -> e.cacheBytes })
            }
        }
    }

    fun closeAccountCache() {
        sheetJob?.cancel()
        _accountSheet.value = null
    }

    /** Clear all cached data for one account (offline; enumerates the account's local rows). */
    fun clearAccountCache(configId: Long) = viewModelScope.launch {
        val refs = withContext(Dispatchers.IO) {
            cloudMediaDao.getByServerConfig(configId).first().map { it.toRef() }
        }
        withContext(Dispatchers.IO) { cache.clearForAssets(refs) }
        refreshSizes()
        _accountSheet.updateIf(configId) {
            it.copy(totalBytes = 0L, albums = it.albums.map { a -> a.copy(cacheBytes = 0L) })
        }
    }

    /** Clear cached data for a single album (uses the refs captured when the sheet loaded). */
    fun clearAlbumCache(configId: Long, albumRemoteId: String) = viewModelScope.launch {
        val entry = _accountSheet.value
            ?.takeIf { it.configId == configId }
            ?.albums?.find { it.remoteId == albumRemoteId } ?: return@launch
        _accountSheet.updateIf(configId) {
            it.copy(albums = it.albums.map { a -> if (a.remoteId == albumRemoteId) a.copy(clearing = true) else a })
        }
        withContext(Dispatchers.IO) { cache.clearForAssets(entry.refs) }
        val newTotal = withContext(Dispatchers.IO) {
            cache.sizeForAssets(cloudMediaDao.getByServerConfig(configId).first().map { it.toRef() })
        }
        _accountSheet.updateIf(configId) {
            it.copy(
                totalBytes = newTotal,
                albums = it.albums.map { a ->
                    if (a.remoteId == albumRemoteId) a.copy(cacheBytes = 0L, clearing = false) else a
                }
            )
        }
        refreshSizes()
    }

    private fun CloudMediaEntity.toRef() = CacheAssetRef(providerType, serverConfigId, remoteId)

    /** Apply [block] to the sheet state only if it is still showing [configId] (guards races). */
    private inline fun MutableStateFlow<AccountCacheSheetState?>.updateIf(
        configId: Long,
        block: (AccountCacheSheetState) -> AccountCacheSheetState
    ) {
        val current = value
        if (current != null && current.configId == configId) value = block(current)
    }

    data class CacheSizes(
        val auto: Long = 0L,
        val pinned: Long = 0L,
        val perAccount: Map<Long, Long> = emptyMap(),
        val coverage: Map<Long, OfflineCoverage> = emptyMap()
    )

    data class DownloadInfo(
        val state: OfflineDownloadWorkState = OfflineDownloadWorkState.IDLE,
        val done: Int = 0,
        val total: Int = 0,
        val failed: Int = 0,
        val error: String? = null
    )

    private companion object {
        val OFFLINE_SIZE_LABELS = listOf("thumbnail", "preview")
    }
}
