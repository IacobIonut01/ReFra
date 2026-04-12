package com.dot.gallery.feature_node.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.workers.startCategoryClassification
import com.dot.gallery.feature_node.data.data_source.CategoryWithMediaCount
import com.dot.gallery.feature_node.domain.model.LibraryIndicatorState
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.domain.util.MediaOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Data class for category with its thumbnail media
 */
data class CategoryMedia(
    val category: CategoryWithMediaCount,
    val thumbnailMedia: Media.UriMedia?
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val mediaDistributor: MediaDistributor,
    private val workManager: WorkManager
) : ViewModel() {

    val locations = mediaDistributor.locationsMediaFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val indicatorState = combine(
        repository.getTrashed(),
        repository.getFavorites(MediaOrder.Default)
    ) { trashed, favorites ->
        LibraryIndicatorState(
            trashCount = trashed.data?.size ?: 0,
            favoriteCount = favorites.data?.size ?: 0
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), LibraryIndicatorState())

    // New category system - top categories for library display with thumbnails
    private val topCategoriesRaw = repository.getTopCategories(5)
    
    val topCategories = combine(
        topCategoriesRaw,
        mediaDistributor.timelineMediaFlow
    ) { categories, mediaState ->
        val mediaMap = mediaState.media.associateBy { it.id }
        categories.map { category ->
            CategoryMedia(
                category = category,
                thumbnailMedia = category.thumbnailMediaId?.let { mediaMap[it] }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    
    // Total count of categories with media (for the "See all" indicator)
    val totalCategoryCount = repository.getCategoryCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0)

    // Legacy classification system (for backwards compatibility)
    val classifiedCategories = repository.getClassifiedCategories()
        .map { if (it.isNotEmpty()) it.distinct() else it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val mostPopularCategory = repository.getClassifiedMediaByMostPopularCategory()
        .map { it.groupBy { it.category!! }.toSortedMap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyMap())

    /**
     * Start the category classification using the new CLIP-based system
     */
    fun startClassification() {
        workManager.startCategoryClassification()
    }

}