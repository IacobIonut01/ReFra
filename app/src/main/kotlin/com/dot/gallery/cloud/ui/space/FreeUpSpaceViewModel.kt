/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.space

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.R
import com.dot.gallery.cloud.sync.AUTO_ENABLED_KEY
import com.dot.gallery.cloud.sync.AUTO_INTERVAL_DAYS_KEY
import com.dot.gallery.cloud.sync.CUTOFF_DAYS_KEY
import com.dot.gallery.cloud.sync.FREE_UP_SPACE_DEFAULT_INTERVAL_DAYS
import com.dot.gallery.cloud.sync.FREE_UP_SPACE_NEVER_CUTOFF
import com.dot.gallery.cloud.sync.FreeUpSpaceAutoScheduler
import com.dot.gallery.cloud.sync.FreeUpSpaceEngine
import com.dot.gallery.cloud.sync.KEEP_FAVORITES_KEY
import com.dot.gallery.cloud.sync.freeUpSpaceDeletionBatch
import com.dot.gallery.core.activeDataStore
import com.dot.gallery.feature_node.domain.model.Media
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

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
    val autoEnabled: Boolean = false,
    val autoIntervalDays: Int = FREE_UP_SPACE_DEFAULT_INTERVAL_DAYS,
    val message: String = "",
    val error: String? = null
)

@HiltViewModel
class FreeUpSpaceViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val engine: FreeUpSpaceEngine,
    private val autoScheduler: FreeUpSpaceAutoScheduler
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
                    cutoffDays = preferences?.get(CUTOFF_DAYS_KEY) ?: NEVER_CUTOFF,
                    autoEnabled = preferences?.get(AUTO_ENABLED_KEY) ?: false,
                    autoIntervalDays = preferences?.get(AUTO_INTERVAL_DAYS_KEY)
                        ?: FREE_UP_SPACE_DEFAULT_INTERVAL_DAYS
                )
            }
        }
    }

    companion object {
        /** Sentinel cutoff meaning "never remove based on age". */
        const val NEVER_CUTOFF = FREE_UP_SPACE_NEVER_CUTOFF
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

    fun setAutoEnabled(enabled: Boolean) {
        _uiState.update { it.copy(autoEnabled = enabled) }
        viewModelScope.launch {
            context.activeDataStore.edit { it[AUTO_ENABLED_KEY] = enabled }
            autoScheduler.sync(enabled, _uiState.value.autoIntervalDays)
        }
    }

    fun setAutoIntervalDays(days: Int) {
        _uiState.update { it.copy(autoIntervalDays = days) }
        viewModelScope.launch {
            context.activeDataStore.edit { it[AUTO_INTERVAL_DAYS_KEY] = days }
            val state = _uiState.value
            if (state.autoEnabled) autoScheduler.sync(true, days)
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
            try {
                val result = engine.scan(options.cutoffDays, options.keepFavorites) { scanned ->
                    _uiState.update { it.copy(scannedCount = scanned) }
                } ?: throw IllegalStateException(context.getString(R.string.error_title))
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    totalLocal = result.totalLocal,
                    backedUpItems = result.verified,
                    verifiedHashes = result.verifiedHashes,
                    message = if (result.verified.isEmpty()) {
                        context.getString(R.string.cloud_free_space_none_verified)
                    } else {
                        context.getString(R.string.cloud_free_space_verified_count, result.verified.size)
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
            val verified = engine.reverifyForDeletion(
                candidates,
                state.cutoffDays,
                state.keepFavorites,
                state.verifiedHashes
            )
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
                engine.loadCompleteMedia()?.mapTo(mutableSetOf()) { media -> media.id }
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
}
