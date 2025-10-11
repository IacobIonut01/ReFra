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
        .map { list -> list.any { it.state == WorkInfo.State.RUNNING } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val progress: StateFlow<Float> = workInfosFlow
        .map { list ->
            val relevant = list.filter { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED || it.state.isFinished }
            if (relevant.isEmpty()) return@map 0f
            val progressValues = relevant.map { it.progress.getInt("progress", 0) }
            val avg = if (progressValues.isNotEmpty()) progressValues.sum() / progressValues.size else 0
            val pct = avg.coerceIn(0, 100)
            // If all finished and at least one had progress, force 100%
            val allFinished = relevant.all { it.state.isFinished }
            if (allFinished) 1f else pct / 100f
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    fun <T: Media> enqueueCopy(vararg sets: Pair<T, String>, onStarted: () -> Unit = {}) {
        if (sets.isEmpty()) return
        workManager.copyMedia(*sets)
        onStarted()
    }
}
