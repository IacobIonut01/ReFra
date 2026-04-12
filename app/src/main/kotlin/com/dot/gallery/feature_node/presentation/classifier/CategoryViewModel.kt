package com.dot.gallery.feature_node.presentation.classifier

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.core.Constants
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.Settings
import com.dot.gallery.feature_node.domain.model.Category
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.presentation.util.add
import com.dot.gallery.feature_node.presentation.util.mapMediaToItem
import com.dot.gallery.feature_node.presentation.util.remove
import com.dot.gallery.feature_node.presentation.util.update
import dagger.hilt.android.lifecycle.HiltViewModel
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

/**
 * ViewModel for viewing media in a single category.
 * Supports both the legacy string-based categories and the new ID-based system.
 */
@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val distributor: MediaDistributor
) : ViewModel() {

    // Legacy string-based category (for backward compatibility)
    var category: String = ""

    // New ID-based category
    private val _categoryId = MutableStateFlow<Long?>(null)
    val categoryId = _categoryId.asStateFlow()

    private val _currentCategory = MutableStateFlow<Category?>(null)
    val currentCategory: StateFlow<Category?> = _currentCategory.asStateFlow()

    private val defaultDateFormat =
        repository.getSetting(Settings.Misc.DEFAULT_DATE_FORMAT, Constants.DEFAULT_DATE_FORMAT)
            .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.DEFAULT_DATE_FORMAT)

    private val extendedDateFormat =
        repository.getSetting(Settings.Misc.EXTENDED_DATE_FORMAT, Constants.EXTENDED_DATE_FORMAT)
            .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.EXTENDED_DATE_FORMAT)

    private val weeklyDateFormat =
        repository.getSetting(Settings.Misc.WEEKLY_DATE_FORMAT, Constants.WEEKLY_DATE_FORMAT)
            .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.WEEKLY_DATE_FORMAT)

    // Media for legacy string-based category
    val mediaByCategory by lazy {
        combine(
            repository.getClassifiedMediaByCategory(category),
            combine(
                defaultDateFormat,
                extendedDateFormat,
                weeklyDateFormat
            ) { defaultDateFormat, extendedDateFormat, weeklyDateFormat ->
                Triple(defaultDateFormat, extendedDateFormat, weeklyDateFormat)
            }
        ) { data, (defaultDateFormat, extendedDateFormat, weeklyDateFormat) ->
            mapMediaToItem(
                data = data,
                error = "",
                albumId = -1,
                defaultDateFormat = defaultDateFormat,
                extendedDateFormat = extendedDateFormat,
                weeklyDateFormat = weeklyDateFormat
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), MediaState())
    }

    // Media for new ID-based category
    @OptIn(ExperimentalCoroutinesApi::class)
    val mediaByCategoryId: StateFlow<MediaState<Media.UriMedia>> = _categoryId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(MediaState())
            } else {
                combine(
                    repository.getMediaIdsInCategory(id),
                    distributor.timelineMediaFlow,
                    combine(
                        defaultDateFormat,
                        extendedDateFormat,
                        weeklyDateFormat
                    ) { d, e, w -> Triple(d, e, w) }
                ) { mediaIds, allMedia, (defaultDateFormat, extendedDateFormat, weeklyDateFormat) ->
                    val mediaList = allMedia.media.filter { it.id in mediaIds }
                    mapMediaToItem(
                        data = mediaList,
                        error = "",
                        albumId = -1,
                        defaultDateFormat = defaultDateFormat,
                        extendedDateFormat = extendedDateFormat,
                        weeklyDateFormat = weeklyDateFormat
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MediaState())

    // Category details flow
    @OptIn(ExperimentalCoroutinesApi::class)
    val categoryDetails: StateFlow<Category?> = _categoryId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else repository.getCategory(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectionState = mutableStateOf(false)
    val selectedMedia = mutableStateOf<Set<Long>>(emptySet())

    /**
     * Set the category ID to load
     */
    fun setCategoryId(id: Long) {
        _categoryId.value = id
        viewModelScope.launch(Dispatchers.IO) {
            _currentCategory.value = repository.getCategoryAsync(id)
        }
    }

    fun toggleSelection(mediaState: MediaState<Media.ClassifiedMedia>, index: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = mediaState.media[index]
            val selectedPhoto = selectedMedia.value.find { it == item.id }
            if (selectedPhoto != null) {
                selectedMedia.remove(selectedPhoto)
            } else {
                selectedMedia.add(item.id)
            }
            selectionState.update(selectedMedia.value.isNotEmpty())
        }
    }

    fun toggleSelectionUriMedia(mediaState: MediaState<Media.UriMedia>, index: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = mediaState.media[index]
            val selectedPhoto = selectedMedia.value.find { it == item.id }
            if (selectedPhoto != null) {
                selectedMedia.remove(selectedPhoto)
            } else {
                selectedMedia.add(item.id)
            }
            selectionState.update(selectedMedia.value.isNotEmpty())
        }
    }

    fun <T : Media> addMedia(vault: Vault, media: T) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addMedia(vault, media)
        }
    }

    /**
     * Remove selected media from this category
     */
    fun removeSelectedFromCategory() {
        val catId = _categoryId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            selectedMedia.value.forEach { mediaId ->
                repository.removeMediaFromCategory(mediaId, catId)
            }
            selectedMedia.value = emptySet()
            selectionState.update(false)
        }
    }

    /**
     * Update category settings
     */
    fun updateCategory(category: Category) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateCategory(category)
            _currentCategory.value = category
        }
    }

}