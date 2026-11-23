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
import com.dot.gallery.core.work.workScheduler
import com.dot.gallery.core.workers.enqueueDuplicateHash
import com.dot.gallery.domain.settings.DataStoreSettingsStore
import com.dot.gallery.domain.settings.PrefKey
import com.dot.gallery.domain.settings.SettingsKeys
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DuplicateSettingsState(
    val loaded: Boolean = false,
    val keepFolders: List<String> = emptyList(),
    val removeFolders: List<String> = emptyList(),
    val checkLargeFiles: Boolean = false,
)

/** The Duplicate settings screen: which copy is kept, and whether large files are checked. */
class DuplicateSettingsViewModel @Inject constructor(
    context: Context,
    @me.tatarka.inject.annotations.Assisted private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val settings = DataStoreSettingsStore(context.activeDataStore)

    val state: StateFlow<DuplicateSettingsState> = combine(
        settings.observe(SettingsKeys.duplicateKeepFolders),
        settings.observe(SettingsKeys.duplicateRemoveFolders),
        settings.observe(SettingsKeys.duplicateCheckLargeFiles),
    ) { keep, remove, large ->
        DuplicateSettingsState(
            loaded = true,
            keepFolders = keep.toList(),
            removeFolders = remove.toList(),
            checkLargeFiles = large,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DuplicateSettingsState())

    fun setCheckLargeFiles(enabled: Boolean) = viewModelScope.launch {
        settings.put(SettingsKeys.duplicateCheckLargeFiles, enabled)
        if (enabled) workScheduler().enqueueDuplicateHash(force = true)
    }

    fun addKeepFolder(folder: String) = editFolders(SettingsKeys.duplicateKeepFolders) { it + folder }
    fun removeKeepFolder(folder: String) = editFolders(SettingsKeys.duplicateKeepFolders) { it - folder }
    fun addRemoveFolder(folder: String) = editFolders(SettingsKeys.duplicateRemoveFolders) { it + folder }
    fun removeRemoveFolder(folder: String) = editFolders(SettingsKeys.duplicateRemoveFolders) { it - folder }

    fun reset() = viewModelScope.launch {
        settings.put(SettingsKeys.duplicateKeepFolders, SettingsKeys.duplicateKeepFolders.default)
        settings.put(SettingsKeys.duplicateRemoveFolders, SettingsKeys.duplicateRemoveFolders.default)
        settings.put(SettingsKeys.duplicateCheckLargeFiles, SettingsKeys.duplicateCheckLargeFiles.default)
    }

    private fun editFolders(key: PrefKey<Set<String>>, change: (Set<String>) -> Set<String>) =
        viewModelScope.launch {
            val current = settings.observe(key).first()
            settings.put(key, change(current).map(String::trim).filter(String::isNotEmpty).toSet())
        }
}
