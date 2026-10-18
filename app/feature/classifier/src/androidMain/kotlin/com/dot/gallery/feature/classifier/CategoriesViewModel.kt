package com.dot.gallery.feature.classifier

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.core.smart.SmartScanPlan
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.data.dao.CategoryWithMediaCount
import com.dot.gallery.data.dao.SmartScanDao
import com.dot.gallery.data.entity.SmartScanFeature
import com.dot.gallery.data.entity.SmartScanPhaseEntity
import com.dot.gallery.data.entity.SmartScanRunEntity
import com.dot.gallery.domain.model.Category
import com.dot.gallery.domain.model.Media
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.data.repository.MediaRepository
import com.dot.gallery.core.presentation.util.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.dot.gallery.domain.model.ClassifiedMedia
import com.dot.gallery.core.ui.components.settings
import com.dot.gallery.core.util.or
import com.dot.gallery.core.presentation.util.add
import com.dot.gallery.core.presentation.util.remove
import com.dot.gallery.core.presentation.util.selectedMedia
import com.dot.gallery.core.ml.ModelManager
import com.dot.gallery.feature.classifier.ClassifierRuntime





/**
 * ViewModel for the Categories feature.
 * Manages both the legacy classification system and the new embedding-based category system.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val distributor: MediaDistributor,
    private val modelManager: ModelManager,
    private val classifierRuntime: ClassifierRuntime,
    private val smartScanDao: SmartScanDao,
) : ViewModel() {

    val modelStatus: StateFlow<ModelStatus> = modelManager.activeSearchModelStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ModelStatus.NOT_INSTALLED)

    // ============ Locations ============
    
    /**
     * Local EXIF and account-qualified cloud media with usable coordinates.
     */
    val geoMedia = classifierRuntime.mergedGeoMedia(
        localGeoMedia = distributor.geoMediaFlow,
        timelineMedia = distributor.timelineMediaFlow,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Flow of named and coordinate-only locations. Entries remain actionable when reverse
     * geocoding is incomplete, and cached cloud locations stay visible while offline.
     */
    val locations = classifierRuntime.mergedLocations(
        localLocations = distributor.locationsMediaFlow,
        geoMedia = geoMedia,
        timelineMedia = distributor.timelineMediaFlow,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ============ New Category System ============
    
    /**
     * Flow of all categories with their media counts
     */
    val categoriesWithCount: StateFlow<List<CategoryWithMediaCount>> = repository.getCategoriesWithMediaCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val distinctClassifiedMediaCount: StateFlow<Int> = repository.getDistinctClassifiedMediaCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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
    private val activeSmartScan = smartScanDao.observeActiveRun()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * The active scan run when it visibly includes category classification; null when
     * idle or when only a hidden background-scheduled run is queued.
     */
    val categoryScan: StateFlow<SmartScanRunEntity?> = activeSmartScan
        .map { run ->
            run?.takeIf {
                SmartScanPlan.runRequestsFeature(it, SmartScanFeature.CATEGORIES) &&
                    SmartScanPlan.shouldShowRun(it.userVisible, it.totalMedia)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isCategoryWorkerRunning: StateFlow<Boolean> = categoryScan
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val categoryPhases = SmartScanPlan.phasesFor(SmartScanFeature.CATEGORIES.bit).toSet()

    private val categoryScanPhases: StateFlow<List<SmartScanPhaseEntity>> = categoryScan
        .flatMapLatest { run ->
            if (run == null) flowOf(emptyList())
            else smartScanDao.observePhases(run.runId).map { phases ->
                phases.filter { it.phase in categoryPhases }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categoryWorkerProgress: StateFlow<Float> =
        combine(categoryScan, categoryScanPhases) { run, phases ->
            when {
                run == null -> 0f
                phases.isNotEmpty() -> SmartScanPlan.overallProgress(phases) * 100f
                run.totalMedia > 0 -> run.processedMedia.toFloat() / run.totalMedia * 100f
                else -> 0f
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

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

    val isRunning = classifierRuntime.classifierRunning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    val progress = classifierRuntime.classifierProgress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0f)

    // ============ UI State ============
    
    val selectionState = mutableStateOf(false)
    val selectedMedia = mutableStateListOf<ClassifiedMedia>()

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
            classifierRuntime.smartScanManual(SmartScanFeature.CATEGORIES.bit)
        }
    }

    /**
     * Stop the category classification worker
     */
    fun stopCategoryClassification() {
        val runId = activeSmartScan.value?.runId ?: return
        viewModelScope.launch { classifierRuntime.smartScanCancel(runId) }
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
            classifierRuntime.smartScanAutomatic(SmartScanFeature.CATEGORIES.bit)
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
            classifierRuntime.smartScanAutomatic(SmartScanFeature.CATEGORIES.bit)
        }
    }

    /**
     * Update category threshold
     */
    fun updateCategoryThreshold(categoryId: Long, threshold: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateCategoryThreshold(categoryId, threshold.coerceIn(Category.MIN_THRESHOLD, Category.MAX_THRESHOLD))
            // Trigger reclassification
            classifierRuntime.smartScanAutomatic(SmartScanFeature.CATEGORIES.bit)
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
     * Count of category memberships held by videos — used to confirm the purge when
     * the exclude-videos setting is enabled (#948).
     */
    suspend fun getVideoCategoryMembershipCount(): Int =
        repository.getVideoCategoryMembershipCount()

    /**
     * Removes every video membership from all categories after user confirmation (#948).
     */
    fun purgeVideoCategoryMemberships() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteVideoCategoryMemberships()
        }
    }

    /**
     * Reset all category data and reinitialize with defaults
     */
    fun resetCategories() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.resetCategoryData()
            repository.initializeDefaultCategories()
            classifierRuntime.smartScanAutomatic(SmartScanFeature.CATEGORIES.bit)
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

    fun toggleSelection(mediaState: MediaState<ClassifiedMedia>, index: Int) {
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
        viewModelScope.launch {
            classifierRuntime.enqueueVaultEncrypt(
                media = listOf(media.getUri()),
                vault = vault
            )
        }
    }

    /**
     * Start the category classification using the new CLIP-based system.
     * This replaces the old ONNX-based classification.
     */
    fun startClassification() {
        viewModelScope.launch { classifierRuntime.smartScanAutomatic(SmartScanFeature.CATEGORIES.bit) }
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
        stopCategoryClassification()
        classifierRuntime.stopClassification()
    }

}