package com.dot.gallery.feature_node.presentation.classifier

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo.State
import androidx.work.WorkManager
import com.dot.gallery.core.workers.startClassification
import com.dot.gallery.core.workers.stopClassification
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.presentation.util.update
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val workManager: WorkManager
) : ViewModel() {

    val classifiedCategories = repository.getClassifiedCategories()
        .map { if (it.isNotEmpty()) it.distinct() else it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val mostPopularCategory = repository.getClassifiedMediaByMostPopularCategory()
        .map { it.groupBy { it.category!! }.toSortedMap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyMap())

    val categoriesWithMedia = repository.getCategoriesWithMedia()
        .map { it.sortedBy { it.category!! } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val classifiedMediaCount = repository.getClassifiedMediaCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0)

    val isRunning = workManager.getWorkInfosByTagFlow("ImageClassifier")
        .map { it.lastOrNull()?.state == State.RUNNING }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    val progress = workManager.getWorkInfosByTagFlow("ImageClassifier")
        .map {
            it.lastOrNull()?.progress?.getFloat("progress", 0f) ?: 0f
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0f)

    val selectionState = mutableStateOf(false)
    val selectedMedia = mutableStateListOf<Media.ClassifiedMedia>()

    fun toggleSelection(mediaState: MediaState<Media.ClassifiedMedia>, index: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = mediaState.media[index]
            val selectedPhoto = selectedMedia.find { it.id == item.id }
            if (selectedPhoto != null) {
                selectedMedia.remove(selectedPhoto)
            } else {
                selectedMedia.add(item)
            }
            selectionState.update(selectedMedia.isNotEmpty())
        }
    }

    fun <T: Media> addMedia(vault: Vault, media: T) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addMedia(vault, media)
        }
    }

    fun startClassification() {
        workManager.startClassification()
    }

    fun deleteClassifications() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteClassifications()
        }
    }

    fun stopClassification() {
        workManager.stopClassification()
    }

}