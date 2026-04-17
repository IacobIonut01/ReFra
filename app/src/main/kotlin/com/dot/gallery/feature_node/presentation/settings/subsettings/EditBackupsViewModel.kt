/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.settings.subsettings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dot.gallery.core.EditBackupManager
import com.dot.gallery.core.workers.EditBackupWorker
import com.dot.gallery.core.workers.autoCleanupEditBackups
import com.dot.gallery.core.workers.deleteAllEditBackups
import com.dot.gallery.core.workers.deleteSelectedEditBackups
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditBackupsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val editBackupManager: EditBackupManager,
    private val workManager: WorkManager,
    private val repository: MediaRepository
) : ViewModel() {

    data class State(
        val isLoading: Boolean = true,
        val totalSize: Long = 0L,
        val freeSpace: Long = 0L,
        val backups: List<EditBackupManager.BackupInfo> = emptyList(),
        val mediaList: List<Media.UriMedia> = emptyList()
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val info = editBackupManager.getAllBackupInfo()
                .sortedByDescending { it.editTimestamp }
            val size = editBackupManager.getTotalBackupSize()
            val free = context.filesDir.freeSpace

            val backupUris = info.map { it.originalUri }
            val mediaList = if (backupUris.isNotEmpty()) {
                val resource = repository.getMediaListByUris(
                    listOfUris = backupUris,
                    reviewMode = false,
                    onlyMatching = true
                ).first()
                val allMedia = resource.data ?: emptyList()
                val backupMediaIds = info.map { it.mediaId }.toSet()
                allMedia.filter { it.id in backupMediaIds }
            } else emptyList()

            _state.value = State(
                isLoading = false,
                totalSize = size,
                freeSpace = free,
                backups = info,
                mediaList = mediaList
            )
        }
    }

    fun autoCleanup(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val workId = workManager.autoCleanupEditBackups()
            workManager.getWorkInfoByIdFlow(workId).collect { info ->
                if (info == null) return@collect
                when (info.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val count = info.outputData.getInt(
                            EditBackupWorker.KEY_DELETED_COUNT, 0
                        )
                        refresh()
                        onResult(count)
                        return@collect
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        refresh()
                        onResult(0)
                        return@collect
                    }
                    else -> { /* waiting */ }
                }
            }
        }
    }

    fun deleteAll(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val workId = workManager.deleteAllEditBackups()
            workManager.getWorkInfoByIdFlow(workId).collect { info ->
                if (info == null) return@collect
                if (info.state.isFinished) {
                    refresh()
                    onDone()
                    return@collect
                }
            }
        }
    }

    fun deleteSelected(mediaIds: List<Long>, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val workId = workManager.deleteSelectedEditBackups(mediaIds.toLongArray())
            workManager.getWorkInfoByIdFlow(workId).collect { info ->
                if (info == null) return@collect
                if (info.state.isFinished) {
                    refresh()
                    onDone()
                    return@collect
                }
            }
        }
    }
}
