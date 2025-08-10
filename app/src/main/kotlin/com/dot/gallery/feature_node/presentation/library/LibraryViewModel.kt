package com.dot.gallery.feature_node.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.dot.gallery.feature_node.domain.model.LibraryIndicatorState
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.domain.util.MediaOrder
import com.dot.gallery.core.workers.startClassification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    repository: MediaRepository,
    private val workManager: WorkManager
) : ViewModel() {

    val indicatorState = combine(
        repository.getTrashed(),
        repository.getFavorites(MediaOrder.Default)
    ) { trashed, favorites ->
        LibraryIndicatorState(
            trashCount = trashed.data?.size ?: 0,
            favoriteCount = favorites.data?.size ?: 0
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), LibraryIndicatorState())

    val classifiedCategories = repository.getClassifiedCategories()
        .map { if (it.isNotEmpty()) it.distinct() else it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val mostPopularCategory = repository.getClassifiedMediaByMostPopularCategory()
        .map { it.groupBy { it.category!! }.toSortedMap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyMap())

    fun startClassification() {
        workManager.startClassification()
    }

}