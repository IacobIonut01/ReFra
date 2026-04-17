package com.dot.gallery.feature_node.presentation.classifier

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo.State
import androidx.work.WorkManager
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.workers.CategoryWorker
import com.dot.gallery.core.workers.VaultOperationWorker
import com.dot.gallery.core.workers.enqueueVaultOperation
import com.dot.gallery.core.workers.startCategoryClassification
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.core.workers.startClassification
import com.dot.gallery.core.workers.stopCategoryClassification
import com.dot.gallery.core.workers.stopClassification
import com.dot.gallery.feature_node.data.data_source.CategoryWithMediaCount
import com.dot.gallery.feature_node.domain.model.Category
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.presentation.util.update
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Categories feature.
 * Manages both the legacy classification system and the new embedding-based category system.
 */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val distributor: MediaDistributor,
    private val workManager: WorkManager
) : ViewModel() {

    // ============ Locations ============
    
    /**
     * Flow of all locations with their media
     */
    val locations = distributor.locationsMediaFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Flow of all geo-tagged media with GPS coordinates for map display
     */
    val geoMedia = distributor.geoMediaFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ============ New Category System ============
    
    /**
     * Flow of all categories with their media counts
     */
    val categoriesWithCount: StateFlow<List<CategoryWithMediaCount>> = repository.getCategoriesWithMediaCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Flow of all categories (including empty ones)
     */
    val allCategories: StateFlow<List<Category>> = repository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Top categories for carousel display
     */
    val topCategories: StateFlow<List<CategoryWithMediaCount>> = repository.getTopCategories(10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Worker state for the new category classification
     */
    val isCategoryWorkerRunning: StateFlow<Boolean> = workManager.getWorkInfosByTagFlow(CategoryWorker.TAG)
        .map { workInfos -> workInfos.any { it.state == State.RUNNING || it.state == State.ENQUEUED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val categoryWorkerProgress: StateFlow<Float> = workManager.getWorkInfosByTagFlow(CategoryWorker.TAG)
        .map { workInfos ->
            workInfos.firstOrNull { it.state == State.RUNNING }
                ?.progress?.getFloat(CategoryWorker.KEY_PROGRESS, 0f) ?: 0f
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val categoryWorkerStatus: StateFlow<String> = workManager.getWorkInfosByTagFlow(CategoryWorker.TAG)
        .map { workInfos ->
            workInfos.firstOrNull { it.state == State.RUNNING }
                ?.progress?.getString(CategoryWorker.KEY_STATUS) ?: ""
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // ============ Legacy Classification System (kept for backward compatibility) ============
    
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

    // ============ UI State ============
    
    val selectionState = mutableStateOf(false)
    val selectedMedia = mutableStateListOf<Media.ClassifiedMedia>()

    private val _editingCategory = MutableStateFlow<Category?>(null)
    val editingCategory = _editingCategory.asStateFlow()

    val metadataState = distributor.metadataFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        MediaMetadataState()
    )

    // ============ Category Actions ============

    /**
     * Start the new embedding-based category classification
     */
    fun startCategoryClassification() {
        viewModelScope.launch {
            // Initialize default categories if needed
            repository.initializeDefaultCategories()
            // Start the worker
            workManager.startCategoryClassification()
        }
    }

    /**
     * Stop the category classification worker
     */
    fun stopCategoryClassification() {
        workManager.stopCategoryClassification()
    }

    /**
     * Create a new user-defined category
     */
    fun createCategory(name: String, searchTerms: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val category = Category(
                name = name,
                searchTerms = searchTerms,
                isUserCreated = true
            )
            repository.createCategory(category)
            // Trigger reclassification to include the new category
            workManager.startCategoryClassification()
        }
    }

    /**
     * Update a category's settings
     */
    fun updateCategory(category: Category) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateCategory(category.copy(
                updatedAt = System.currentTimeMillis(),
                embedding = null // Clear embedding so it gets regenerated
            ))
            // Trigger reclassification
            workManager.startCategoryClassification()
        }
    }

    /**
     * Update category threshold
     */
    fun updateCategoryThreshold(categoryId: Long, threshold: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateCategoryThreshold(categoryId, threshold.coerceIn(Category.MIN_THRESHOLD, Category.MAX_THRESHOLD))
            // Trigger reclassification
            workManager.startCategoryClassification()
        }
    }

    /**
     * Update category name
     */
    fun updateCategoryName(categoryId: Long, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateCategoryName(categoryId, name)
        }
    }

    /**
     * Toggle category pinned status
     */
    fun toggleCategoryPinned(categoryId: Long, isPinned: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleCategoryPinned(categoryId, isPinned)
        }
    }

    /**
     * Delete a category
     */
    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteCategory(categoryId)
        }
    }

    /**
     * Manually add media to a category
     */
    fun addMediaToCategory(mediaId: Long, categoryId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addMediaToCategory(mediaId, categoryId, similarity = 1f, isManual = true)
        }
    }

    /**
     * Remove media from a category
     */
    fun removeMediaFromCategory(mediaId: Long, categoryId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeMediaFromCategory(mediaId, categoryId)
        }
    }

    /**
     * Reset all category data and reinitialize with defaults
     */
    fun resetCategories() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.resetCategoryData()
            repository.initializeDefaultCategories()
            workManager.startCategoryClassification()
        }
    }

    // ============ Dialog State ============

    fun showEditCategoryDialog(category: Category) {
        _editingCategory.value = category
    }

    fun hideEditCategoryDialog() {
        _editingCategory.value = null
    }

    // ============ Legacy Actions ============

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
        workManager.enqueueVaultOperation(
            operation = VaultOperationWorker.OP_ENCRYPT,
            media = listOf(media.getUri()),
            vault = vault
        )
    }

    /**
     * Start the category classification using the new CLIP-based system.
     * This replaces the old ONNX-based classification.
     */
    fun startClassification() {
        // Use the new CLIP-based category classification
        workManager.startCategoryClassification()
    }

    fun deleteClassifications() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteClassifications()
        }
    }

    /**
     * Stop the category classification
     */
    fun stopClassification() {
        // Stop both the old and new systems for safety
        workManager.stopCategoryClassification()
        workManager.stopClassification()
    }

}