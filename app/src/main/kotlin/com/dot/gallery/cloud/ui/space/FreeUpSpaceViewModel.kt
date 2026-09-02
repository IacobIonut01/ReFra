/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.space

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.R
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.cloud.core.capabilities.SyncCapableProvider
import com.dot.gallery.cloud.data.dao.CloudUploadPrefDao
import com.dot.gallery.core.Resource
import com.dot.gallery.core.activeDataStore
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isFavorite
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import javax.inject.Inject

internal const val FREE_UP_SPACE_DELETE_BATCH_SIZE = 2_000

internal fun <T> freeUpSpaceDeletionBatch(items: List<T>): List<T> =
    items.take(FREE_UP_SPACE_DELETE_BATCH_SIZE)

internal fun verifiedLocalRevisionMatches(
    mediaId: Long,
    currentHash: String?,
    verifiedHashes: Map<Long, String>
): Boolean = currentHash != null && verifiedHashes[mediaId] == currentHash

data class FreeUpSpaceUiState(
    val isScanning: Boolean = false,
    val isDeleting: Boolean = false,
    val isPreparingDeletionBatch: Boolean = false,
    val isDeletionRequestPending: Boolean = false,
    val preferencesLoaded: Boolean = false,
    val scannedCount: Int = 0,
    val totalLocal: Int = 0,
    val backedUpItems: List<Media.UriMedia> = emptyList(),
    val verifiedHashes: Map<Long, String> = emptyMap(),
    val deletionCandidates: List<Media.UriMedia> = emptyList(),
    val pendingDeletionItems: List<Media.UriMedia> = emptyList(),
    val deletedCount: Int = 0,
    val keepFavorites: Boolean = true,
    // -1 = "Never": automatic/age-based removal is disabled. This is the default so
    // nothing is ever removed unless the user explicitly picks a time range.
    val cutoffDays: Int = FreeUpSpaceViewModel.NEVER_CUTOFF,
    val message: String = "",
    val error: String? = null
)

@HiltViewModel
class FreeUpSpaceViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: MediaRepository,
    private val registry: ProviderRegistry,
    private val uploadPrefDao: CloudUploadPrefDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(FreeUpSpaceUiState())
    val uiState: StateFlow<FreeUpSpaceUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val preferences = runCatching { context.activeDataStore.data.first() }.getOrNull()
            _uiState.update {
                it.copy(
                    preferencesLoaded = true,
                    keepFavorites = preferences?.get(KEEP_FAVORITES_KEY) ?: true,
                    cutoffDays = preferences?.get(CUTOFF_DAYS_KEY) ?: NEVER_CUTOFF
                )
            }
        }
    }

    companion object {
        /** Sentinel cutoff meaning "never remove based on age". */
        const val NEVER_CUTOFF = -1
        private const val MEDIA_QUERY_TIMEOUT_MS = 10_000L
        private val KEEP_FAVORITES_KEY = booleanPreferencesKey("cloud_free_space_keep_favorites")
        private val CUTOFF_DAYS_KEY = intPreferencesKey("cloud_free_space_cutoff_days")
    }

    fun setKeepFavorites(keep: Boolean) {
        _uiState.update {
            it.copy(
                keepFavorites = keep,
                backedUpItems = emptyList(),
                verifiedHashes = emptyMap(),
                deletionCandidates = emptyList(),
                pendingDeletionItems = emptyList(),
                message = "",
                error = null
            )
        }
        viewModelScope.launch {
            context.activeDataStore.edit { it[KEEP_FAVORITES_KEY] = keep }
        }
    }

    fun setCutoffDays(days: Int) {
        _uiState.update {
            it.copy(
                cutoffDays = days,
                backedUpItems = emptyList(),
                verifiedHashes = emptyMap(),
                deletionCandidates = emptyList(),
                pendingDeletionItems = emptyList(),
                message = "",
                error = null
            )
        }
        viewModelScope.launch {
            context.activeDataStore.edit { it[CUTOFF_DAYS_KEY] = days }
        }
    }

    fun scan() {
        val options = _uiState.value
        if (!options.preferencesLoaded) return
        // "Never" disables removal entirely — surface a clear message and do nothing.
        if (options.cutoffDays == NEVER_CUTOFF) {
            _uiState.update {
                it.copy(
                    isDeleting = false,
                    isPreparingDeletionBatch = false,
                    isDeletionRequestPending = false,
                    backedUpItems = emptyList(),
                    verifiedHashes = emptyMap(),
                    deletionCandidates = emptyList(),
                    pendingDeletionItems = emptyList(),
                    message = context.getString(R.string.cloud_free_space_never_summary)
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isScanning = true,
                isDeleting = false,
                isPreparingDeletionBatch = false,
                isDeletionRequestPending = false,
                scannedCount = 0,
                deletedCount = 0,
                message = context.getString(R.string.cloud_free_space_loading),
                backedUpItems = emptyList(),
                verifiedHashes = emptyMap(),
                deletionCandidates = emptyList(),
                pendingDeletionItems = emptyList(),
                error = null
            )
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val allMedia = loadCompleteMedia()
                        ?: throw IllegalStateException(context.getString(R.string.error_title))
                    val cutoffMs = System.currentTimeMillis() -
                            (options.cutoffDays.toLong() * 86_400_000L)
                    val candidates = allMedia
                        .filter { it.uri.scheme != "cloud" && it.definedTimestamp * 1000L < cutoffMs }
                        .let { items ->
                            if (options.keepFavorites) items.filterNot { it.isFavorite }
                            else items
                        }
                    val preferencesByAlbum = uploadPrefDao.getEnabledList().groupBy { it.albumId }
                    val hashCache = mutableMapOf<Long, String?>()
                    val verifiedHashes = mutableMapOf<Long, String>()
                    val verified = candidates.filterIndexed { index, media ->
                        val checksum = hashCache.getOrPut(media.id) { computeSha1(media) }
                        val destinations = preferencesByAlbum[media.albumID].orEmpty()
                        val presentEverywhere = checksum != null && destinations.isNotEmpty() &&
                                destinations.all { preference ->
                                    val provider = registry.getByConfigId(preference.serverConfigId)
                                            as? SyncCapableProvider ?: return@all false
                                    verifyRemoteContent(
                                        provider,
                                        media,
                                        preference.albumLabel.trim().ifBlank { null },
                                        checksum
                                    )
                                }
                        if (presentEverywhere) verifiedHashes[media.id] = checksum
                        _uiState.update { it.copy(scannedCount = index + 1) }
                        presentEverywhere
                    }
                    _uiState.value = _uiState.value.copy(
                        isScanning = false,
                        totalLocal = candidates.size,
                        backedUpItems = verified,
                        verifiedHashes = verifiedHashes,
                        message = if (verified.isEmpty()) {
                            context.getString(R.string.cloud_free_space_none_verified)
                        } else {
                            context.getString(R.string.cloud_free_space_verified_count, verified.size)
                        }
                    )
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isScanning = false,
                        error = e.message ?: context.getString(R.string.error_title)
                    )
                }
            }
        }
    }

    fun beginLocalDeletion() {
        val items = _uiState.value.backedUpItems
        if (items.isEmpty()) return
        _uiState.update {
            it.copy(
                isDeleting = true,
                isPreparingDeletionBatch = false,
                isDeletionRequestPending = false,
                deletionCandidates = items,
                pendingDeletionItems = emptyList(),
                deletedCount = 0,
                message = context.getString(R.string.cloud_free_space_removing_count, items.size),
                error = null
            )
        }
    }

    fun prepareNextDeletionBatch() {
        val state = _uiState.value
        if (!state.isDeleting || state.isPreparingDeletionBatch ||
            state.isDeletionRequestPending || state.pendingDeletionItems.isNotEmpty()
        ) return
        val candidates = freeUpSpaceDeletionBatch(state.deletionCandidates)
        if (candidates.isEmpty()) return
        _uiState.update { it.copy(isPreparingDeletionBatch = true) }
        viewModelScope.launch {
            val verified = withContext(Dispatchers.IO) {
                val currentById = loadCompleteMedia()?.associateBy { media -> media.id }
                    ?: return@withContext null
                val preferencesByAlbum = uploadPrefDao.getEnabledList().groupBy { it.albumId }
                val cutoffMs = System.currentTimeMillis() -
                        (state.cutoffDays.toLong() * 86_400_000L)
                candidates.mapNotNull { candidate ->
                    val media = currentById[candidate.id] ?: return@mapNotNull null
                    if (media.uri.scheme == "cloud" || media.definedTimestamp * 1000L >= cutoffMs) {
                        return@mapNotNull null
                    }
                    if (state.keepFavorites && media.isFavorite) return@mapNotNull null
                    val checksum = state.verifiedHashes[media.id] ?: return@mapNotNull null
                    if (!verifiedLocalRevisionMatches(media.id, computeSha1(media), state.verifiedHashes)) {
                        return@mapNotNull null
                    }
                    val destinations = preferencesByAlbum[media.albumID].orEmpty()
                    media.takeIf {
                        destinations.isNotEmpty() && destinations.all { preference ->
                            val provider = registry.getByConfigId(preference.serverConfigId)
                                    as? SyncCapableProvider ?: return@all false
                            verifyRemoteContent(
                                provider,
                                media,
                                preference.albumLabel.trim().ifBlank { null },
                                checksum
                            )
                        }
                    }
                }
            }
            if (verified == null) {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        isPreparingDeletionBatch = false,
                        deletionCandidates = emptyList(),
                        error = context.getString(R.string.cloud_free_space_remove_failed)
                    )
                }
                return@launch
            }
            val candidateIds = candidates.mapTo(mutableSetOf()) { media -> media.id }
            val verifiedIds = verified.mapTo(mutableSetOf()) { media -> media.id }
            val rejectedIds = candidateIds - verifiedIds
            val remainingCandidates = state.deletionCandidates.drop(candidates.size)
            _uiState.update {
                val backedUpItems = it.backedUpItems.filterNot { media -> media.id in rejectedIds }
                val finished = verified.isEmpty() && remainingCandidates.isEmpty()
                it.copy(
                    isDeleting = !finished,
                    isPreparingDeletionBatch = false,
                    deletionCandidates = remainingCandidates,
                    pendingDeletionItems = verified,
                    backedUpItems = backedUpItems,
                    verifiedHashes = it.verifiedHashes.filterKeys { id -> id !in rejectedIds },
                    message = if (finished) {
                        if (it.deletedCount > 0) {
                            context.getString(R.string.cloud_free_space_removed_count, it.deletedCount)
                        } else {
                            context.getString(R.string.cloud_free_space_none_verified)
                        }
                    } else {
                        context.getString(R.string.cloud_free_space_removing_count, backedUpItems.size)
                    }
                )
            }
        }
    }

    fun markDeletionRequestPending() {
        _uiState.update { it.copy(isDeletionRequestPending = true) }
    }

    fun completeLocalDeletionBatch() {
        _uiState.update {
            val deletedIds = it.pendingDeletionItems.mapTo(mutableSetOf()) { media -> media.id }
            if (deletedIds.isEmpty()) return@update it
            val backedUpItems = it.backedUpItems.filterNot { media -> media.id in deletedIds }
            val deletedCount = it.deletedCount + deletedIds.size
            val hasMore = it.deletionCandidates.isNotEmpty()
            it.copy(
                isDeleting = hasMore,
                isPreparingDeletionBatch = false,
                isDeletionRequestPending = false,
                pendingDeletionItems = emptyList(),
                backedUpItems = backedUpItems,
                verifiedHashes = it.verifiedHashes.filterKeys { id -> id !in deletedIds },
                deletedCount = deletedCount,
                message = if (hasMore) {
                    context.getString(R.string.cloud_free_space_removing_count, backedUpItems.size)
                } else {
                    context.getString(R.string.cloud_free_space_removed_count, deletedCount)
                },
                error = null
            )
        }
    }

    fun cancelLocalDeletion() {
        _uiState.update {
            it.copy(
                isDeleting = false,
                isPreparingDeletionBatch = false,
                isDeletionRequestPending = false,
                deletionCandidates = emptyList(),
                pendingDeletionItems = emptyList(),
                message = context.getString(R.string.cloud_free_space_verified_count, it.backedUpItems.size)
            )
        }
    }

    fun failLocalDeletion() {
        val state = _uiState.value
        _uiState.update {
            it.copy(
                isDeleting = true,
                isPreparingDeletionBatch = true,
                isDeletionRequestPending = false,
                pendingDeletionItems = emptyList(),
                error = context.getString(R.string.cloud_free_space_remove_failed)
            )
        }
        viewModelScope.launch {
            val localIds = withContext(Dispatchers.IO) {
                loadCompleteMedia()?.mapTo(mutableSetOf()) { media -> media.id }
            }
            _uiState.update {
                if (localIds == null) {
                    it.copy(
                        isDeleting = false,
                        isPreparingDeletionBatch = false,
                        deletionCandidates = emptyList(),
                        message = context.getString(
                            R.string.cloud_free_space_verified_count,
                            it.backedUpItems.size
                        )
                    )
                } else {
                    val remaining = state.backedUpItems.filter { media -> media.id in localIds }
                    val removedCount = state.deletedCount + state.backedUpItems.size - remaining.size
                    it.copy(
                        isDeleting = false,
                        isPreparingDeletionBatch = false,
                        deletionCandidates = emptyList(),
                        backedUpItems = remaining,
                        verifiedHashes = state.verifiedHashes.filterKeys(localIds::contains),
                        deletedCount = removedCount,
                        message = if (remaining.isEmpty() && removedCount > 0) {
                            context.getString(R.string.cloud_free_space_removed_count, removedCount)
                        } else {
                            context.getString(R.string.cloud_free_space_verified_count, remaining.size)
                        },
                        error = if (remaining.isEmpty()) null else it.error
                    )
                }
            }
        }
    }

    private suspend fun loadCompleteMedia(): List<Media.UriMedia>? = try {
        withTimeoutOrNull(MEDIA_QUERY_TIMEOUT_MS) {
            (repository.getCompleteMedia().first() as? Resource.Success)?.data
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend fun verifyRemoteContent(
        provider: SyncCapableProvider,
        media: Media,
        targetPath: String?,
        checksum: String
    ): Boolean = try {
        provider.verifyRemoteContent(media, targetPath, checksum).getOrDefault(false)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    private fun computeSha1(media: Media): String? {
        return try {
            context.contentResolver.openInputStream(media.getUri())?.use { input ->
                val digest = MessageDigest.getInstance("SHA-1")
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (_: Exception) { null }
    }
}
