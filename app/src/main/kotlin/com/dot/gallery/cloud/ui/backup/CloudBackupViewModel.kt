/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.backup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dot.gallery.cloud.core.ConnectionState
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.UploadTargetResolver
import com.dot.gallery.cloud.core.capabilities.SyncCapableProvider
import com.dot.gallery.cloud.data.dao.CloudMediaDao
import com.dot.gallery.cloud.data.dao.CloudServerConfigDao
import com.dot.gallery.cloud.data.dao.CloudUploadPrefDao
import com.dot.gallery.cloud.data.entity.CloudUploadPrefEntity
import com.dot.gallery.cloud.di.CloudProviderInitializer
import com.dot.gallery.cloud.sync.CloudIndexProgressManager
import com.dot.gallery.cloud.sync.CloudSyncScheduler
import com.dot.gallery.cloud.sync.CloudUploadWorker
import com.dot.gallery.cloud.sync.backupLocalRevisionKey
import com.dot.gallery.cloud.sync.backupRemoteRevisionKey
import com.dot.gallery.cloud.sync.backupRevisionLocalUri
import com.dot.gallery.cloud.sync.backupVerificationCutoff
import com.dot.gallery.cloud.sync.cacheVerifiedBackupRevision
import com.dot.gallery.cloud.sync.isActiveBackupWork
import com.dot.gallery.cloud.sync.isBackupRevisionCached
import com.dot.gallery.cloud.ui.verifiedItemsByIndex
import com.dot.gallery.core.activeDataStore
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.domain.util.MediaOrder
import com.dot.gallery.feature_node.domain.util.OrderType
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.util.printWarn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import javax.inject.Inject

/** Backup status for a single cloud account. */
data class AccountBackupStatus(
    val configId: Long,
    val providerType: ProviderType,
    val accountLabel: String,
    val enabledAlbumCount: Int,
    val totalAssets: Int,
    /** Provider-confirmed matches plus unchanged local revisions from successful syncs. */
    val verifiedCount: Int,
    /** Filename-only matches from the local cloud index; useful evidence, not proof. */
    val assumedCount: Int,
    val syncEnabled: Boolean = false,
    /**
     * Live connection state of this account's provider instance. An active, sync-capable
     * account whose provider failed to authenticate / never registered reports [ConnectionState.ERROR]
     * so the dashboard can surface it with an error indicator instead of hiding the whole
     * service (which previously made the screen fall back to the empty "Add cloud provider" state).
     */
    val connectionState: ConnectionState = ConnectionState.CONNECTED
) {
    val hasError: Boolean get() = connectionState == ConnectionState.ERROR
    /** Legacy consumers use this as a confirmed count; assumptions must never be called backed up. */
    val backedUpCount: Int get() = verifiedCount
    val unknownCount: Int get() = (totalAssets - verifiedCount - assumedCount).coerceAtLeast(0)
    val remainderCount: Int get() = (totalAssets - verifiedCount).coerceAtLeast(0)
    // The health bar only treats provider-confirmed content matches as verified. Filename-only
    // assumptions remain visually distinct and can never produce a misleading full/safe bar.
    val progress: Float get() = if (totalAssets > 0) verifiedCount.toFloat() / totalAssets else 0f
}

data class BackupUiState(
    val totalAssets: Int = 0,
    val verifiedCount: Int = 0,
    val assumedCount: Int = 0,
    val unknownCount: Int = 0,
    val backedUpCount: Int = 0,
    val remainderCount: Int = 0,
    val enabledAlbumCount: Int = 0,
    /** Per-account breakdown so each cloud's backup progress is unambiguous. */
    val accounts: List<AccountBackupStatus> = emptyList(),
    val isScanning: Boolean = false,
    val isUploading: Boolean = false,
    val scanProgress: String = "",
    val error: String? = null
)

@HiltViewModel
class CloudBackupViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: MediaRepository,
    private val uploadPrefDao: CloudUploadPrefDao,
    private val configDao: CloudServerConfigDao,
    private val cloudMediaDao: CloudMediaDao,
    private val registry: ProviderRegistry,
    private val providerInitializer: CloudProviderInitializer,
    private val workManager: WorkManager,
    private val syncScheduler: CloudSyncScheduler,
    indexProgressManager: CloudIndexProgressManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()
    private var scanJob: Job? = null

    /**
     * Whether a provider TYPE is sync-capable, resolved from a registered instance if present
     * or a transient (unauthenticated) instance otherwise. Capabilities are static per provider,
     * so this is safe to determine without a live/authenticated connection — which is exactly
     * what lets an errored-out account still be listed. Cached to avoid re-minting instances.
     */
    private val syncCapableCache = mutableMapOf<ProviderType, Boolean>()

    private fun isSyncCapable(type: ProviderType): Boolean =
        syncCapableCache.getOrPut(type) {
            (registry.getAllForType(type).firstOrNull()
                ?: providerInitializer.createTransientProvider(type)) is SyncCapableProvider
        }

    /**
     * Live connection state for an account. A missing (never-registered) provider means auth
     * failed at init, so it is reported as [ConnectionState.ERROR] rather than silently omitted.
     */
    private fun connectionStateOf(configId: Long): ConnectionState =
        registry.connectionStates.value[configId] ?: ConnectionState.ERROR

    /** Live progress of caching ("indexing") each account's remote media into the local DB. */
    val indexState: StateFlow<CloudIndexProgressManager.IndexState> = indexProgressManager.state

    val uploadPreferences = uploadPrefDao.getAll()
        .map { prefs -> prefs.associate { it.albumId to it.uploadEnabled } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val uploadWorkRunning: StateFlow<Boolean> = MutableStateFlow(false).also { flow ->
        viewModelScope.launch {
            // Track ANY backup run (periodic, "back up all", or per-account) via the shared tag.
            var wasRunning = false
            workManager.getWorkInfosByTagFlow(CloudUploadWorker.TAG_BACKUP)
                .collect { workInfos ->
                    val running = workInfos.any { isActiveBackupWork(it.state, it.tags) }
                    flow.value = running
                    _uiState.value = _uiState.value.copy(isUploading = running)
                    if (wasRunning && !running) scanBackupStatus()
                    wasRunning = running
                }
        }
    }

    init {
        viewModelScope.launch {
            var previousStates = emptyMap<Long, ConnectionState>()
            registry.connectionStates.collect { states ->
                _uiState.value = _uiState.value.copy(
                    accounts = _uiState.value.accounts.map { account ->
                        account.copy(connectionState = states[account.configId] ?: ConnectionState.ERROR)
                    }
                )
                val connected = newlyConnectedConfigIds(previousStates, states)
                if (connected.isNotEmpty() && uploadPrefDao.getEnabledList().any { it.serverConfigId in connected }) {
                    scanBackupStatus()
                }
                previousStates = states
            }
        }

        // Show the full service list and last-known counts INSTANTLY from the
        // persisted snapshot — opening the dashboard must not trigger the
        // expensive scan every time. A scan only runs on first-ever open (no
        // snapshot yet), on manual refresh, or when the album selection changes.
        viewModelScope.launch { loadInitialState() }

        // Reactively track enabled album IDs so the UI refreshes immediately when
        // albums are toggled in the picker. drop(1) skips the initial emission
        // (loadInitialState already primed the UI); later changes trigger a rescan.
        viewModelScope.launch {
            uploadPrefDao.getEnabled()
                .map(::backupSelectionKeys)
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    scanBackupStatus()
                }
        }
    }

    /**
     * Populates the UI from the persisted snapshot without scanning. Every active
     * sync-capable account is always listed (so the service cards render even
     * before any scan), with live album counts and last-known asset counts.
     */
    private suspend fun loadInitialState() {
        val persisted = readPersistedAccounts()
        val activeConfigs = configDao.getAll().first().filter { it.isActive }
        val accounts = activeConfigs.mapNotNull { cfg ->
            // List EVERY active sync-capable account, keyed off the persisted config rather
            // than a live provider instance. An account whose provider failed to authenticate
            // is never registered, but it must still appear here (with an error indicator) —
            // otherwise the last remaining errored account makes the whole screen collapse to
            // the empty "Add cloud provider" state.
            if (!isSyncCapable(cfg.providerType)) return@mapNotNull null
            val snapshot = persisted[cfg.id]
            AccountBackupStatus(
                configId = cfg.id,
                providerType = cfg.providerType,
                accountLabel = cfg.displayName.ifBlank { cfg.providerType.displayName },
                // Album count is a cheap DB read, so keep it live even without a scan.
                enabledAlbumCount = uploadPrefDao.getEnabledByConfigList(cfg.id).size,
                totalAssets = snapshot?.totalAssets ?: 0,
                verifiedCount = snapshot?.verifiedCount ?: 0,
                // Snapshots written before verification provenance was tracked are assumptions.
                assumedCount = snapshot?.assumedCount
                    ?: snapshot?.backedUpCount
                    ?: 0,
                syncEnabled = cfg.syncEnabled,
                connectionState = connectionStateOf(cfg.id)
            )
        }
        publishAccounts(accounts, isScanning = false)
        // Nothing cached yet — compute once so the numbers aren't all zero.
        if (backupScanRequired(accounts.mapTo(mutableSetOf()) { it.configId }, persisted.keys)) {
            scanBackupStatus()
        }
    }

    fun scanBackupStatus() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true, scanProgress = "Loading albums…")

            // Resolve every active account backed by a sync-capable provider. We list
            // ALL such accounts (even those with no albums selected yet) so the
            // dashboard surfaces every configured cloud and the user can configure
            // backup for each one. Each account is scanned independently.
            val activeConfigs = configDao.getAll().first().filter { it.isActive }
            val perConfig = activeConfigs.mapNotNull { cfg ->
                // Keep listing sync-capable accounts even when their provider is not
                // currently registered (e.g. it failed to authenticate). The live
                // provider — used only for the authoritative bulkUploadCheck — may be
                // null; in that case the account still renders with an error indicator
                // and its counts fall back to the cheap filename-cache match.
                if (!isSyncCapable(cfg.providerType)) return@mapNotNull null
                val provider = registry.getByConfigId(cfg.id) as? SyncCapableProvider
                val prefs = uploadPrefDao.getEnabledByConfigList(cfg.id)
                Triple(cfg, provider, prefs)
            }

            if (perConfig.isEmpty()) {
                publishAccounts(emptyList(), isScanning = false)
                persistAccounts(emptyList())
                return@launch
            }

            withContext(Dispatchers.IO) {
                try {
                    val hashByMediaId = HashMap<Long, String>()
                    suspend fun hashOf(media: com.dot.gallery.feature_node.domain.model.Media): String? =
                        hashByMediaId[media.id]
                            ?: computeSha1(media)?.also { hashByMediaId[media.id] = it }

                    // Phase A — cheap, hash-free assumptions (filename match against the durable
                    // cache) so the service cards + hero render immediately. Phase B hashes every
                    // candidate and promotes only provider-confirmed matches to verified. Gather
                    // each account's candidate media the SAME way the
                    // upload worker does — per enabled album via getMediaByAlbumId.
                    val scanned = perConfig.map { (cfg, provider, prefs) ->
                        val revisionProvider = provider
                            ?: providerInitializer.createTransientProvider(cfg.providerType) as? SyncCapableProvider
                        val mediaWithTargets = prefs.flatMap { pref ->
                            (repository.getMediaByAlbumId(pref.albumId, skipBatching = true).first().data ?: emptyList())
                                .map { it to UploadTargetResolver.resolve(cfg, pref, it) }
                        }.distinctBy { it.first.id to it.second }.filter { it.first.uri.scheme != "cloud" }
                        val media = mediaWithTargets.distinctBy { it.first.id }.map { it.first }
                        val targetPathsByMediaId = mediaWithTargets.groupBy(
                            keySelector = { it.first.id },
                            valueTransform = { it.second }
                        ).mapValues { (_, targets) -> targets.distinct() }
                        // Immich stores the original filename in `label` (remoteId is an
                        // opaque UUID); path-based stores key by remote path — cover both.
                        val cached = cloudMediaDao.getByServerConfig(cfg.id).first()
                        val cachedNames = cached
                            .mapNotNull { it.label.ifBlank { it.remoteId.substringAfterLast('/') }.ifBlank { null } }
                            .toSet()
                        val localRevisions = cloudMediaDao.getValidBackupRevisions(
                            cfg.id,
                            backupVerificationCutoff()
                        ).map { backupLocalRevisionKey(it.localUri, it.localSize, it.localTimestamp) }
                            .toSet()
                        val remoteRevisions = if (provider?.requiresUploadChecksum == true) {
                            cloudMediaDao.getRemoteRevisions(cfg.id)
                                .map {
                                    backupRemoteRevisionKey(
                                        it.fileId,
                                        it.label,
                                        it.mimeType,
                                        it.size,
                                        it.lastSyncedAt / 1000L
                                    )
                                }
                                .toSet()
                        } else {
                            emptySet()
                        }
                        val evidenceByMediaId = media.associate { item ->
                            val revisionUris = targetPathsByMediaId[item.id].orEmpty()
                                .map {
                                    backupRevisionLocalUri(
                                        item.getUri().toString(),
                                        revisionProvider?.deterministicRemoteId(item, it)
                                    )
                                }
                                .ifEmpty { listOf(item.getUri().toString()) }
                                .distinct()
                            val allTargetsVerified = revisionUris.all { revisionUri ->
                                backupLocalRevisionKey(revisionUri, item.size, item.timestamp) in localRevisions
                            }
                            item.id to if (allTargetsVerified) {
                                BackupMatchEvidence.VERIFIED_REVISION
                            } else {
                                backupMatchEvidence(
                                    uri = revisionUris.first(),
                                    mediaId = item.id,
                                    label = item.label,
                                    mimeType = item.mimeType,
                                    size = item.size,
                                    timestamp = item.timestamp,
                                    cachedNames = cachedNames,
                                    localRevisions = emptySet(),
                                    remoteRevisions = remoteRevisions
                                )
                            }
                        }
                        ScannedAccount(
                            config = cfg,
                            provider = provider,
                            connectionState = connectionStateOf(cfg.id),
                            enabledAlbumCount = prefs.size,
                            media = media,
                            targetPathsByMediaId = targetPathsByMediaId,
                            evidenceByMediaId = evidenceByMediaId
                        )
                    }

                    // Phase A publish: services + provisional counts, still scanning.
                    val refined = scanned.map {
                        it.toStatus(it.verifiedIds.size, it.assumedCount(it.verifiedIds))
                    }.toMutableList()
                    publishAccounts(refined, isScanning = true)
                    var verificationFailed = false

                    // Phase B: hash every readable, not-yet-verified candidate and ask the provider
                    // for a content match. Filename matches stay assumed unless this check confirms them.
                    scanned.forEachIndexed { accountIndex, s ->
                        val verifiedIds = s.verifiedIds.toMutableSet()
                        // No live provider (errored/not authenticated) — retain filename matches
                        // as assumptions and leave everything else unknown. With a provider, hash
                        // every remaining candidate so a filename match can be promoted to verified proof.
                        val provider = s.provider
                        val candidates = s.media.filterNot { it.id in verifiedIds }
                        if (provider != null) {
                            candidates.chunked(VERIFICATION_BATCH_SIZE).forEachIndexed { chunkIndex, chunk ->
                                val checked = (chunkIndex * VERIFICATION_BATCH_SIZE).coerceAtMost(candidates.size)
                                _uiState.value = _uiState.value.copy(
                                    scanProgress = "Verifying ${checked + 1}–${(checked + chunk.size).coerceAtMost(candidates.size)} of ${candidates.size} items…"
                                )
                                val hashed = chunk.mapNotNull { media ->
                                    hashOf(media)?.let { hash -> media to hash }
                                }
                                if (hashed.isNotEmpty()) {
                                    val verified = if (provider.requiresUploadChecksum) {
                                        val present = try {
                                            provider.bulkUploadCheck(hashed.map { it.second })
                                                .onFailure {
                                                    verificationFailed = true
                                                    printWarn(
                                                        "cloud.backup",
                                                        "remote verification failed: $it"
                                                    )
                                                }
                                                .getOrDefault(emptyMap())
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            verificationFailed = true
                                            printWarn(
                                                "cloud.backup",
                                                "remote verification failed: $e"
                                            )
                                            emptyMap()
                                        }
                                        verifiedItemsByIndex(hashed, present)
                                    } else {
                                        hashed.mapNotNull { (media, hash) ->
                                            val targets = s.targetPathsByMediaId[media.id].orEmpty()
                                                .ifEmpty { listOf(null) }
                                            val matches = targets.all { targetPath ->
                                                val targetMatches = try {
                                                    provider.verifyRemoteContent(media, targetPath, hash)
                                                        .onFailure {
                                                            verificationFailed = true
                                                            printWarn(
                                                                "cloud.backup",
                                                                "remote verification failed: $it"
                                                            )
                                                        }
                                                        .getOrDefault(false)
                                                } catch (e: CancellationException) {
                                                    throw e
                                                } catch (e: Exception) {
                                                    verificationFailed = true
                                                    printWarn(
                                                        "cloud.backup",
                                                        "remote verification failed: $e"
                                                    )
                                                    false
                                                }
                                                if (targetMatches) {
                                                    cacheVerifiedBackupRevision(
                                                        cloudMediaDao,
                                                        s.config.id,
                                                        media,
                                                        hash,
                                                        provider,
                                                        targetPath
                                                    )
                                                }
                                                targetMatches
                                            }
                                            (media to hash).takeIf { matches }
                                        }
                                    }
                                    verified.forEach { (media, hash) ->
                                        verifiedIds += media.id
                                        if (provider.requiresUploadChecksum) {
                                            cacheVerifiedBackupRevision(
                                                cloudMediaDao,
                                                s.config.id,
                                                media,
                                                hash,
                                                provider
                                            )
                                        }
                                    }
                                }
                                refined[accountIndex] = s.toStatus(
                                    verifiedIds.size,
                                    s.assumedCount(verifiedIds)
                                )
                                publishAccounts(refined, isScanning = true)
                            }
                        }
                        refined[accountIndex] = s.toStatus(
                            verifiedIds.size,
                            s.assumedCount(verifiedIds)
                        )
                    }

                    publishAccounts(refined, isScanning = false)
                    if (verificationFailed) {
                        _uiState.value = _uiState.value.copy(error = "Some backup items could not be verified")
                    } else {
                        persistAccounts(refined)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isScanning = false,
                        error = e.message
                    )
                }
            }
        }
    }

    /** Per-account working set carried between the cheap and authoritative scan phases. */
    private data class ScannedAccount(
        val config: com.dot.gallery.cloud.data.entity.CloudServerConfigEntity,
        val provider: SyncCapableProvider?,
        val connectionState: ConnectionState,
        val enabledAlbumCount: Int,
        val media: List<com.dot.gallery.feature_node.domain.model.Media.UriMedia>,
        val targetPathsByMediaId: Map<Long, List<String?>>,
        val evidenceByMediaId: Map<Long, BackupMatchEvidence>
    ) {
        val verifiedIds: Set<Long> = evidenceByMediaId
            .filterValues { it == BackupMatchEvidence.VERIFIED_REVISION }
            .keys

        fun assumedCount(verifiedIds: Set<Long>): Int = evidenceByMediaId.count { (mediaId, evidence) ->
            mediaId !in verifiedIds && evidence.isAssumed
        }

        fun toStatus(verified: Int, assumed: Int) = AccountBackupStatus(
            configId = config.id,
            providerType = config.providerType,
            accountLabel = config.displayName.ifBlank { config.providerType.displayName },
            enabledAlbumCount = enabledAlbumCount,
            totalAssets = media.size,
            verifiedCount = verified,
            assumedCount = assumed,
            syncEnabled = config.syncEnabled,
            connectionState = connectionState
        )
    }

    /** Pushes an account list to the UI, recomputing the aggregate totals. */
    private fun publishAccounts(accounts: List<AccountBackupStatus>, isScanning: Boolean) {
        val total = accounts.sumOf { it.totalAssets }
        val verified = accounts.sumOf { it.verifiedCount }
        val assumed = accounts.sumOf { it.assumedCount }
        val unknown = (total - verified - assumed).coerceAtLeast(0)
        _uiState.value = _uiState.value.copy(
            accounts = accounts,
            enabledAlbumCount = accounts.sumOf { it.enabledAlbumCount },
            totalAssets = total,
            verifiedCount = verified,
            assumedCount = assumed,
            unknownCount = unknown,
            backedUpCount = verified,
            remainderCount = (total - verified).coerceAtLeast(0),
            isScanning = isScanning,
            scanProgress = if (isScanning) _uiState.value.scanProgress else ""
        )
    }

    private suspend fun readPersistedAccounts(): Map<Long, PersistedAccountStatus> {
        val raw = context.activeDataStore.data.first()[STATUS_KEY] ?: return emptyMap()
        return runCatching { statusJson.decodeFromString<List<PersistedAccountStatus>>(raw) }
            .getOrDefault(emptyList())
            .associateBy { it.configId }
    }

    private suspend fun persistAccounts(accounts: List<AccountBackupStatus>) {
        val dto = accounts.map {
            PersistedAccountStatus(
                configId = it.configId,
                totalAssets = it.totalAssets,
                backedUpCount = it.backedUpCount,
                verifiedCount = it.verifiedCount,
                assumedCount = it.assumedCount
            )
        }
        runCatching {
            context.activeDataStore.edit { prefs -> prefs[STATUS_KEY] = statusJson.encodeToString(dto) }
        }
    }

    /** Config IDs whose per-account manual backup is currently running/enqueued. */
    val runningAccounts: StateFlow<Set<Long>> = MutableStateFlow<Set<Long>>(emptySet()).also { flow ->
        viewModelScope.launch {
            workManager.getWorkInfosByTagFlow(CloudUploadWorker.TAG_ACCOUNT_BACKUP)
                .collect { infos ->
                    flow.value = infos
                        .filter { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
                        .flatMap { it.tags }
                        .mapNotNull { tag ->
                            tag.substringAfter("${CloudUploadWorker.TAG_ACCOUNT_BACKUP}:", "")
                                .toLongOrNull()
                        }
                        .toSet()
                }
        }
    }

    /** Triggers a manual backup. [configId] <= 0 backs up every active account. */
    fun triggerBackup(configId: Long = -1L) {
        CloudUploadWorker.triggerNow(workManager, configId)
    }

    /** Enables/disables periodic auto-sync for a single account. */
    fun setAutoSync(configId: Long, enabled: Boolean) {
        viewModelScope.launch {
            val config = configDao.getById(configId) ?: return@launch
            configDao.update(config.copy(syncEnabled = enabled))
            syncScheduler.reconcile()
            scanBackupStatus()
        }
    }

    private suspend fun computeSha1(media: com.dot.gallery.feature_node.domain.model.Media): String? {
        return try {
            context.contentResolver.openInputStream(media.getUri())?.use { input ->
                val digest = MessageDigest.getInstance("SHA-1")
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** Persisted per-account backup counts so the dashboard opens instantly without a rescan. */
    @Serializable
    private data class PersistedAccountStatus(
        val configId: Long,
        val totalAssets: Int,
        /** Legacy aggregate retained so older snapshots decode and are treated as assumptions. */
        val backedUpCount: Int? = null,
        val verifiedCount: Int? = null,
        val assumedCount: Int? = null
    )

    private companion object {
        private const val VERIFICATION_BATCH_SIZE = 50
        private val STATUS_KEY = stringPreferencesKey("cloud_backup_status_v1")
        private val statusJson = Json { ignoreUnknownKeys = true }
    }
}

internal enum class BackupMatchEvidence {
    VERIFIED_REVISION,
    ASSUMED_REVISION,
    ASSUMED_FILENAME,
    UNKNOWN;

    val isAssumed: Boolean
        get() = this == ASSUMED_REVISION || this == ASSUMED_FILENAME
}

internal fun backupMatchEvidence(
    uri: String,
    mediaId: Long,
    label: String,
    mimeType: String,
    size: Long,
    timestamp: Long,
    cachedNames: Set<String>,
    localRevisions: Set<String>,
    remoteRevisions: Set<String>
): BackupMatchEvidence = when {
    backupLocalRevisionKey(uri, size, timestamp) in localRevisions ->
        BackupMatchEvidence.VERIFIED_REVISION
    isBackupRevisionCached(
        uri = uri,
        mediaId = mediaId,
        label = label,
        mimeType = mimeType,
        size = size,
        timestamp = timestamp,
        localRevisions = emptySet(),
        remoteRevisions = remoteRevisions
    ) -> BackupMatchEvidence.ASSUMED_REVISION
    label in cachedNames -> BackupMatchEvidence.ASSUMED_FILENAME
    else -> BackupMatchEvidence.UNKNOWN
}

internal fun backupSelectionKeys(
    preferences: List<CloudUploadPrefEntity>
): Set<Pair<Long, Long>> = preferences.mapTo(mutableSetOf()) {
    it.serverConfigId to it.albumId
}

internal fun backupScanRequired(
    activeConfigIds: Set<Long>,
    persistedConfigIds: Set<Long>
): Boolean = activeConfigIds.any { it !in persistedConfigIds }

internal fun newlyConnectedConfigIds(
    previous: Map<Long, ConnectionState>,
    current: Map<Long, ConnectionState>
): Set<Long> = current.mapNotNullTo(mutableSetOf()) { (configId, state) ->
    configId.takeIf { state == ConnectionState.CONNECTED && previous[configId] != ConnectionState.CONNECTED }
}
