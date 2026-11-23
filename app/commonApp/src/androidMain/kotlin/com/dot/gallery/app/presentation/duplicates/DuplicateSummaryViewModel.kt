/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.duplicates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.core.duplicates.DuplicateSummary
import com.dot.gallery.core.duplicates.DuplicatesSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The Library's Duplicates tile: how many sets exist and the space their extra copies take. */
class DuplicateSummaryViewModel @Inject constructor(
    @me.tatarka.inject.annotations.Assisted private val savedStateHandle: SavedStateHandle,
    source: DuplicatesSource,
) : ViewModel() {
    val summary: StateFlow<DuplicateSummary?> = source.summary
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
