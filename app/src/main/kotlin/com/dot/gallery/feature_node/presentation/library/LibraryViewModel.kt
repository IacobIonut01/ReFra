package com.dot.gallery.feature_node.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.cloud.core.PersonInfo
import com.dot.gallery.cloud.core.ProviderCapability
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.ModelManager
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.core.smart.SmartScanScheduler
import com.dot.gallery.feature_node.data.data_source.CategoryWithMediaCount
import com.dot.gallery.feature_node.data.data_source.SmartScanFeature
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * Data class for category with its thumbnail media
 */
data class CategoryMedia(
    val category: CategoryWithMediaCount,
    val thumbnailMedia: Media.UriMedia?
)

internal fun categoriesLoaded(items: List<*>?): Boolean = items != null

internal fun noCategories(items: List<*>?): Boolean = items?.isEmpty() == true

@Serializable
data class CloudLibraryState(
    val hasCloud: Boolean = false,
    val isConnected: Boolean = false,
    val connectedCapabilities: Set<ProviderCapability> = emptySet(),
    val hasCachedMedia: Boolean = false,
    val archivedCount: Int = 0,
    val sharedLinkCount: Int = 0,
    val totalCloudCount: Int = 0,
    val people: List<PersonInfo> = emptyList(),
    /**
     * Hidden local people — kept outside [people] so the Library row stays
     * visible-only while the section header remains reachable (#1262).
     */
    val hiddenPeopleCount: Int = 0,
    val hasArchive: Boolean = false,
    val hasMemories: Boolean = false,
    val hasShareLink: Boolean = false,
    val hasPeople: Boolean = false,
    val hasMap: Boolean = false
)

internal data class CloudLibraryAvailability(
    val hasCloud: Boolean,
    val isConnected: Boolean,
    val hasArchive: Boolean,
    val hasMemories: Boolean,
    val hasShareLink: Boolean,
    val hasPeople: Boolean,
    val hasMap: Boolean,
)

internal fun resolveCloudLibraryAvailability(
    hasConfiguredAccounts: Boolean,
    configuredCapabilities: Set<ProviderCapability>,
    isConnected: Boolean,
): CloudLibraryAvailability = CloudLibraryAvailability(
    hasCloud = hasConfiguredAccounts,
    isConnected = isConnected,
    hasArchive = ProviderCapability.ARCHIVE in configuredCapabilities,
    hasMemories = ProviderCapability.MEMORIES in configuredCapabilities,
    hasShareLink = ProviderCapability.SHARE_MANAGE in configuredCapabilities,
    hasPeople = ProviderCapability.PEOPLE in configuredCapabilities,
    hasMap = ProviderCapability.MAP in configuredCapabilities,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val source: LibraryContentSource,
    private val repository: MediaRepository,
    private val modelManager: ModelManager,
    private val smartScanScheduler: SmartScanScheduler,
) : ViewModel() {

    val areAiFeaturesAvailable: Boolean get() = modelManager.areAiFeaturesAvailable

    val modelStatus: StateFlow<ModelStatus> = modelManager.status(ModelGroup.SEARCH)

    val state: StateFlow<LibrarySnapshot> = source.state

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
