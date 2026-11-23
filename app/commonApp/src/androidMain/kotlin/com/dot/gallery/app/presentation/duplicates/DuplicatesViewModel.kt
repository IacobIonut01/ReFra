/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.duplicates

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.core.activeDataStore
import com.dot.gallery.core.duplicates.DuplicateSet
import com.dot.gallery.core.duplicates.DuplicatesSource
import com.dot.gallery.core.work.workScheduler
import com.dot.gallery.core.workers.enqueueDuplicateHash
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.settings.DataStoreSettingsStore
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.domain.util.isVideo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DuplicateFilter { ALL, PHOTOS, VIDEOS }

/** What the last finished check found out about the library. */
data class DuplicateCheckInfo(
    val lastCheckedMillis: Long = 0,
    val checkedCount: Int = 0,
    val skippedLarge: Int = 0,
)

data class DuplicatesUiState(
    val loaded: Boolean = false,
    val sets: List<DuplicateSet<UriMedia>> = emptyList(),
    val filter: DuplicateFilter = DuplicateFilter.ALL,
    val choices: DuplicateChoices = DuplicateChoices(),
    val check: DuplicateCheckInfo = DuplicateCheckInfo(),
) {
    /** All sets, before the filter: the summary counts them all. */
    val visibleSets: List<DuplicateSet<UriMedia>>
        get() = when (filter) {
            DuplicateFilter.ALL -> sets
            DuplicateFilter.PHOTOS -> sets.filter { !it.keeper.isVideo }
            DuplicateFilter.VIDEOS -> sets.filter { it.keeper.isVideo }
        }
    val selected: List<UriMedia> get() = selectedCopies(sets, choices) { it.id }
    val selectedBytes: Long get() = selected.sumOf { it.size }
    val extraCopies: Int get() = sets.sumOf { it.items.size - 1 }
    val extraBytes: Long get() = sets.sumOf { it.extraBytes }
}

class DuplicatesViewModel @Inject constructor(
    private val context: Context,
    @me.tatarka.inject.annotations.Assisted private val savedStateHandle: SavedStateHandle,
    source: DuplicatesSource,
) : ViewModel() {

    private val settings = DataStoreSettingsStore(context.activeDataStore)
    private val filter = MutableStateFlow(DuplicateFilter.ALL)
    private val choices = MutableStateFlow(DuplicateChoices())

    private val checkInfo = combine(
        settings.observe(SettingsKeys.duplicateLastCheckedMillis),
        settings.observe(SettingsKeys.duplicateCheckedCount),
        settings.observe(SettingsKeys.duplicateSkippedLarge),
    ) { last, count, skipped -> DuplicateCheckInfo(last, count, skipped) }

    val uiState: StateFlow<DuplicatesUiState> = combine(
        source.sets,
        filter,
        choices,
        checkInfo,
    ) { sets, filter, choices, check ->
        DuplicatesUiState(
            loaded = true,
            sets = sets,
            filter = filter,
            choices = choices,
            check = check,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DuplicatesUiState())

    init {
        // Opening the screen refreshes the sets; a pass already queued or running is kept.
        viewModelScope.launch { workScheduler().enqueueDuplicateHash() }
    }

    fun setFilter(value: DuplicateFilter) = filter.update { value }

    fun toggle(set: DuplicateSet<UriMedia>, copyId: Long) =
        choices.update { it.toggle(set, copyId) { media -> media.id } }

    fun keep(set: DuplicateSet<UriMedia>, copyId: Long) =
        choices.update { it.keep(set, copyId) { media -> media.id } }

    /** Leaves [copyId] alone: it is not moved to the trash. */
    fun dontRemove(set: DuplicateSet<UriMedia>, copyId: Long) = choices.update { current ->
        if (set.keeperId(current) { it.id } == copyId || copyId in current.kept) current
        else current.copy(kept = current.kept + copyId)
    }

    fun checkAgain() = viewModelScope.launch { workScheduler().enqueueDuplicateHash(force = true) }

    /** Checks the large files the last pass skipped, this once. */
    fun checkLargeOnce() = viewModelScope.launch {
        workScheduler().enqueueDuplicateHash(force = true, includeLarge = true)
    }

    /** The copies just moved to the trash are gone; forget the choices made about them. */
    fun onRemoved(ids: Set<Long>) = choices.update { current ->
        current.copy(kept = current.kept - ids)
    }
}
