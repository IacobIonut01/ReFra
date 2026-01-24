package com.dot.gallery.feature_node.presentation.exif

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dot.gallery.core.workers.copyMedia
import com.dot.gallery.feature_node.domain.model.Media
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class CopyMediaViewModel @Inject constructor(
    private val workManager: WorkManager
) : ViewModel() {

    private val workInfosFlow = workManager.getWorkInfosByTagFlow("MediaCopyWorker")

    val isActive: StateFlow<Boolean> = workInfosFlow
        .map { list -> list.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val progress: StateFlow<Float> = workInfosFlow
        .map { list ->
            // Only consider work that is currently running or enqueued
            val activeWork = list.filter { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
            
            // If no active work, return 0 to show the album selection UI
            if (activeWork.isEmpty()) return@map 0f
            
            val progressValues = activeWork.map { it.progress.getInt("progress", 0) }
            val avg = if (progressValues.isNotEmpty()) progressValues.sum() / progressValues.size else 0
            avg.coerceIn(0, 100) / 100f
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    fun <T: Media> enqueueCopy(vararg sets: Pair<T, String>, onStarted: () -> Unit = {}) {
        if (sets.isEmpty()) return
        // Prune old finished work before starting new work
        workManager.pruneWork()
        workManager.copyMedia(*sets)
        onStarted()
    }
}
