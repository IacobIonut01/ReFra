package com.dot.gallery.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.data.dao.CategoryWithMediaCount
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.core.ml.ModelManager
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.core.smart.SmartScanScheduler
import com.dot.gallery.data.entity.SmartScanFeature
import com.dot.gallery.feature.library.ui.LibrarySnapshot
import com.dot.gallery.feature.library.ui.LibraryViewport
import com.dot.gallery.data.repository.MediaRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject


class LibraryViewModel @Inject constructor(
    private val source: LibraryContentSource,
    private val repository: MediaRepository,
    private val modelManager: ModelManager,
    private val smartScanScheduler: SmartScanScheduler,
) : ViewModel() {

    val areAiFeaturesAvailable: Boolean get() = modelManager.areAiFeaturesAvailable

    val modelStatus: StateFlow<ModelStatus> = modelManager.activeSearchModelStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ModelStatus.NOT_INSTALLED)

    val state: StateFlow<LibrarySnapshot> = source.state

    /** Null until the vault list loads. The caller decides whether the count may be shown. */
    val vaultCount: StateFlow<Int?> = repository.getVaults()
        .map { it.data?.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun onVisible() = source.onVisible()

    fun onHidden() = source.onHidden()

    fun onContentDrawn() = source.onContentDrawn()

    fun updateViewport(viewport: LibraryViewport) = source.updateViewport(viewport)

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
        viewModelScope.launch { smartScanScheduler.manual(SmartScanFeature.CATEGORIES.bit) }
    }

}
