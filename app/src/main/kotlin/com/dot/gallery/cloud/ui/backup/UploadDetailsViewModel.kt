/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.UploadTargetResolver
import com.dot.gallery.cloud.core.capabilities.SyncCapableProvider
import com.dot.gallery.cloud.data.dao.CloudMediaDao
import com.dot.gallery.cloud.data.dao.CloudServerConfigDao
import com.dot.gallery.cloud.data.dao.CloudUploadPrefDao
import com.dot.gallery.cloud.sync.CloudUploadWorker
import com.dot.gallery.cloud.sync.backupLocalRevisionKey
import com.dot.gallery.cloud.sync.backupRevisionLocalUri
import com.dot.gallery.cloud.sync.backupVerificationCutoff
import com.dot.gallery.cloud.sync.cacheVerifiedBackupRevision
import com.dot.gallery.cloud.sync.isActiveBackupWork
import com.dot.gallery.cloud.ui.verifiedItemsByIndex
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.repository.MediaRepository
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject

/** A single asset queued for upload, with its thumbnail URI and byte size. */
data class UploadQueueItem(
    val mediaId: Long,
    val uri: Uri,
    val label: String,
    val sizeBytes: Long
)

/** Pending uploads for one (account × album), so the UI can group thumbnails. */
data class UploadGroup(
    val key: String,
    val providerType: ProviderType,
    val accountLabel: String,
    val albumLabel: String,
    val items: List<UploadQueueItem>,
    val totalBytes: Long
)

data class UploadDetailsUiState(
    val isWorkerRunning: Boolean = false,
    val phase: String = "",
    val currentFileName: String = "",
    val totalItems: Int = 0,
    val checkedItems: Int = 0,
    val completedItems: Int = 0,
    val failedItems: Int = 0,
    // Pending-queue preview (grouped by provider + album), with size estimate.
    val isScanning: Boolean = false,
    val groups: List<UploadGroup> = emptyList(),
    val pendingCount: Int = 0,
    val pendingBytes: Long = 0L
)

@HiltViewModel
class UploadDetailsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    private val configDao: CloudServerConfigDao,
    private val uploadPrefDao: CloudUploadPrefDao,
    private val cloudMediaDao: CloudMediaDao,
    private val registry: ProviderRegistry,
    private val repository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UploadDetailsUiState())
    val uiState: StateFlow<UploadDetailsUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        observeWorkProgress()
        refreshQueue()
    }

    private fun observeWorkProgress() {
        viewModelScope.launch {
            // Observe by the shared backup tag rather than a single unique work name so we
            // also surface progress for per-account "Back up now" runs (each of which is
            // enqueued under its own unique name "cloud_upload_now_<configId>").
            var wasRunning = false
            workManager.getWorkInfosByTagFlow(CloudUploadWorker.TAG_BACKUP)
                .collect { workInfos ->
                    val active = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING }
                        ?: workInfos.firstOrNull { isActiveBackupWork(it.state, it.tags) }
                    if (active == null) {
                        _uiState.value = _uiState.value.copy(isWorkerRunning = false)
                        // A run just finished — the pending set changed, so recompute it.
                        if (wasRunning) {
                            wasRunning = false
                            refreshQueue()
                        }
                        return@collect
                    }
                    wasRunning = true
                    val progress = active.progress
                    _uiState.value = _uiState.value.copy(
                        isWorkerRunning = true,
                        phase = progress.getString(CloudUploadWorker.KEY_PHASE) ?: "",
                        currentFileName = progress.getString(CloudUploadWorker.KEY_CURRENT_FILE) ?: "",
                        totalItems = progress.getInt(CloudUploadWorker.KEY_TOTAL_ITEMS, 0),
                        checkedItems = progress.getInt(CloudUploadWorker.KEY_CHECKED_ITEMS, 0),
                        completedItems = progress.getInt(CloudUploadWorker.KEY_COMPLETED_ITEMS, 0),
                        failedItems = progress.getInt(CloudUploadWorker.KEY_FAILED_ITEMS, 0)
                    )
                }
        }
    }

    /**
     * Builds the pending-upload preview the SAME way [CloudUploadWorker] builds its
     * queue: per active account, per enabled album, keep only assets not yet present
     * on that cloud. Grouped by (account × album) with a total-size estimate.
     */
    fun refreshQueue() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true)
            val activeConfigs = configDao.getAll().first().filter { it.isActive }
            val perConfig = activeConfigs.mapNotNull { cfg ->
                val provider = registry.getByConfigId(cfg.id) as? SyncCapableProvider ?: return@mapNotNull null
                Triple(cfg, provider, uploadPrefDao.getEnabledByConfigList(cfg.id))
            }
            withContext(Dispatchers.IO) {
                val hashByMediaId = HashMap<Long, String>()
                suspend fun hashOf(media: Media): String? =
                    hashByMediaId[media.id] ?: computeSha1(media)?.also { hashByMediaId[media.id] = it }

                val groups = mutableListOf<UploadGroup>()
                for ((cfg, provider, prefs) in perConfig) {
                    val accountLabel = cfg.displayName.ifBlank { cfg.providerType.displayName }
                    val localRevisions = cloudMediaDao.getValidBackupRevisions(
                        cfg.id,
                        backupVerificationCutoff()
                    ).map { backupLocalRevisionKey(it.localUri, it.localSize, it.localTimestamp) }
                        .toSet()
                    for (pref in prefs) {
                        fun targetPath(item: Media) = UploadTargetResolver.resolve(cfg, pref, item)
                        val media = (repository.getMediaByAlbumId(pref.albumId, skipBatching = true).first().data ?: emptyList())
                            .filter { it.uri.scheme != "cloud" }
                        if (media.isEmpty()) continue
                        val verifiedIds = media.filterTo(mutableListOf()) { item ->
                            backupLocalRevisionKey(
                                backupRevisionLocalUri(
                                    item.getUri().toString(),
                                    provider.deterministicRemoteId(item, targetPath(item))
                                ),
                                item.size,
                                item.timestamp
                            ) in localRevisions
                        }.mapTo(mutableSetOf()) { it.id }
                        val unchecked = media.filterNot { it.id in verifiedIds }
                        if (provider.requiresUploadChecksum) {
                            // Hash every remaining readable item so filename or remote metadata matches
                            // never suppress a pending upload without provider-confirmed content evidence.
                            val hashed = unchecked.mapNotNull { item ->
                                hashOf(item)?.let { hash -> item to hash }
                            }
                            // Unreadable items remain pending rather than sending an invalid empty hash.
                            val present = if (hashed.isEmpty()) emptyMap() else try {
                                provider.bulkUploadCheck(hashed.map { it.second })
                                    .onFailure {
                                        printWarn("cloud.backup", "remote verification failed: $it")
                                    }
                                    .getOrDefault(emptyMap())
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                printWarn("cloud.backup", "remote verification failed: $e")
                                emptyMap()
                            }
                            verifiedItemsByIndex(hashed, present).forEach { (item, hash) ->
                                verifiedIds += item.id
                                cacheVerifiedBackupRevision(
                                    cloudMediaDao,
                                    cfg.id,
                                    item,
                                    hash,
                                    provider,
                                    targetPath(item)
                                )
                            }
                        } else {
                            unchecked.forEach { item ->
                                val hash = hashOf(item) ?: return@forEach
                                val verified = try {
                                    provider.verifyRemoteContent(item, targetPath(item), hash)
                                        .onFailure {
                                            printWarn("cloud.backup", "remote verification failed: $it")
                                        }
                                        .getOrDefault(false)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    printWarn("cloud.backup", "remote verification failed: $e")
                                    false
                                }
                                if (verified) {
                                    verifiedIds += item.id
                                    cacheVerifiedBackupRevision(
                                        cloudMediaDao,
                                        cfg.id,
                                        item,
                                        hash,
                                        provider,
                                        targetPath(item)
                                    )
                                }
                            }
                        }
                        val pending = media.filterNot { it.id in verifiedIds }
                        if (pending.isEmpty()) continue
                        val items = pending.map {
                            UploadQueueItem(it.id, it.getUri(), it.label, it.size)
                        }
                        groups += UploadGroup(
                            key = "${cfg.id}:${pref.albumId}",
                            providerType = cfg.providerType,
                            accountLabel = accountLabel,
                            albumLabel = pref.albumLabel.ifBlank { media.first().albumLabel },
                            items = items,
                            totalBytes = items.sumOf { it.sizeBytes }
                        )
                    }
                }
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    groups = groups,
                    pendingCount = groups.sumOf { it.items.size },
                    pendingBytes = groups.sumOf { it.totalBytes }
                )
            }
        }
    }

    private suspend fun computeSha1(media: Media): String? {
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
}

internal fun visibleBackupProgressItems(
    phase: String,
    checkedItems: Int,
    completedItems: Int,
    failedItems: Int
): Int = if (phase == CloudUploadWorker.PHASE_VERIFYING) {
    checkedItems
} else {
    completedItems + failedItems
}
