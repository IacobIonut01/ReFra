/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.classifier

import ai.onnxruntime.OrtSession
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.core.Constants
import com.dot.gallery.core.Settings
import com.dot.gallery.feature_node.domain.model.Category
import com.dot.gallery.feature_node.domain.model.ImageEmbedding
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.presentation.search.SearchHelper
import com.dot.gallery.feature_node.presentation.search.util.dot
import com.dot.gallery.feature_node.presentation.util.SystemDateFormatField
import com.dot.gallery.feature_node.presentation.util.mapMediaToItem
import com.dot.gallery.feature_node.presentation.util.resolvedDateFormat
import com.dot.gallery.core.smart.SmartScanScheduler
import com.dot.gallery.feature_node.data.data_source.SmartScanFeature
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Unified ViewModel for both creating and editing categories.
 * When [categoryId] is null, operates in create mode; otherwise in edit mode.
 */
@HiltViewModel
class CategoryEditorViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val searchHelper: SearchHelper,
    private val smartScanScheduler: SmartScanScheduler,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    // Date format settings
    private val defaultDateFormat = repository.getSetting(
        Settings.Misc.DEFAULT_DATE_FORMAT,
        ""
    ).map { resolvedDateFormat(context, it, SystemDateFormatField.DEFAULT) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.DEFAULT_DATE_FORMAT)

    private val extendedDateFormat = repository.getSetting(
        Settings.Misc.EXTENDED_DATE_FORMAT,
        ""
    ).map { resolvedDateFormat(context, it, SystemDateFormatField.EXTENDED) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.EXTENDED_DATE_FORMAT)

    private val weeklyDateFormat = repository.getSetting(
        Settings.Misc.WEEKLY_DATE_FORMAT,
        ""
    ).map { resolvedDateFormat(context, it, SystemDateFormatField.WEEKLY) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.WEEKLY_DATE_FORMAT)

    // ============ Mode ============

    private val _categoryId = MutableStateFlow<Long?>(null)
    val isEditMode: Boolean get() = _categoryId.value != null

    private val _category = MutableStateFlow<Category?>(null)
    val category: StateFlow<Category?> = _category.asStateFlow()

    // ============ Form State ============

    private val _categoryName = MutableStateFlow("")
    val categoryName: StateFlow<String> = _categoryName.asStateFlow()

    private val _searchTerms = MutableStateFlow("")
    val searchTerms: StateFlow<String> = _searchTerms.asStateFlow()

    private val _threshold = MutableStateFlow(Category.DEFAULT_THRESHOLD)
    val threshold: StateFlow<Float> = _threshold.asStateFlow()

    // ============ Reference Images (image-to-image) ============

    private val _referenceImageIds = MutableStateFlow<List<Long>>(emptyList())
    val referenceImageIds: StateFlow<List<Long>> = _referenceImageIds.asStateFlow()

    // ============ Preview State ============

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _previewMedia = MutableStateFlow<List<Media.UriMedia>>(emptyList())
    val previewMedia: StateFlow<List<Media.UriMedia>> = _previewMedia.asStateFlow()

    private val _previewCount = MutableStateFlow(0)
    val previewCount: StateFlow<Int> = _previewCount.asStateFlow()

    private val _previewMediaState = MutableStateFlow<MediaState<Media.UriMedia>>(MediaState())
    val previewMediaState: StateFlow<MediaState<Media.UriMedia>> = _previewMediaState.asStateFlow()

    // ============ Save State ============

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    // ============ Data Ready ============

    private val _isDataReady = MutableStateFlow(false)
    val isDataReady: StateFlow<Boolean> = _isDataReady.asStateFlow()

    // ============ Internal ============

    private var textSession: OrtSession? = null
    private var searchJob: Job? = null
    private var imageEmbeddings: List<ImageEmbedding> = emptyList()

    private val _allMedia = MutableStateFlow<List<Media.UriMedia>>(emptyList())
    val allMedia: StateFlow<List<Media.UriMedia>> = _allMedia.asStateFlow()

    private var allMediaList: List<Media.UriMedia> = emptyList()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            imageEmbeddings = repository.getImageEmbeddings().first()
            val mediaResource = repository.getMedia().first()
            allMediaList = mediaResource.data?.filterIsInstance<Media.UriMedia>() ?: emptyList()
            _allMedia.value = allMediaList
            _isDataReady.value = true
        }
    }

    /**
     * Initialize in edit mode by loading an existing category.
     */
    fun loadCategory(categoryId: Long) {
        _categoryId.value = categoryId
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val cat = repository.getCategoryAsync(categoryId)
                if (cat != null) {
                    _category.value = cat
                    _categoryName.value = cat.name
                    _searchTerms.value = cat.searchTerms
                    _referenceImageIds.value = cat.referenceImageIds
                    _threshold.value = cat.threshold
                    // Wait for data to be ready, then trigger preview
                    while (!_isDataReady.value) {
                        kotlinx.coroutines.delay(50)
                    }
                    searchPreview(cat.searchTerms)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Apply a template category (from suggestions).
     */
    fun applyTemplate(template: Category) {
        _categoryName.value = template.name
        _searchTerms.value = template.searchTerms
        _threshold.value = template.threshold
        triggerPreview(template.searchTerms)
    }

    fun toggleReferenceImage(mediaId: Long) {
        val current = _referenceImageIds.value
        _referenceImageIds.value = if (mediaId in current) {
            current - mediaId
        } else {
            current + mediaId
        }
        triggerPreview(_searchTerms.value)
    }

    fun addReferenceImage(mediaId: Long) {
        val current = _referenceImageIds.value
        if (mediaId !in current) {
            _referenceImageIds.value = current + mediaId
            triggerPreview(_searchTerms.value)
        }
    }

    fun removeReferenceImage(mediaId: Long) {
        _referenceImageIds.value = _referenceImageIds.value - mediaId
        triggerPreview(_searchTerms.value)
    }

    fun updateCategoryName(name: String) {
        _categoryName.value = name
    }

    fun updateSearchTerms(terms: String) {
        _searchTerms.value = terms
        triggerPreview(terms)
    }

    fun updateThreshold(value: Float) {
        _threshold.value = value
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(300)
            searchPreview(_searchTerms.value)
        }
    }

    private fun triggerPreview(terms: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(500)
            searchPreview(terms)
        }
    }

    private suspend fun searchPreview(terms: String) {
        val refIds = _referenceImageIds.value
        if (terms.isBlank() && refIds.isEmpty()) {
            _previewMedia.value = emptyList()
            _previewMediaState.value = MediaState()
            _previewCount.value = 0
            return
        }

        _isLoading.value = true

        if (!searchHelper.isAvailable) {
            _isLoading.value = false
            return
        }

        try {
            withContext(Dispatchers.IO) {
                // Build text embedding if we have search terms
                val textEmbedding = if (terms.isNotBlank()) {
                    if (textSession == null) {
                        textSession = searchHelper.setupTextSession()
                    }
                    textSession?.let { searchHelper.getTextEmbedding(it, terms) }
                } else null

                // Collect reference image embeddings
                val refEmbeddings = if (refIds.isNotEmpty()) {
                    imageEmbeddings.filter { it.id in refIds }
                } else emptyList()

                if (textEmbedding == null && refEmbeddings.isEmpty()) return@withContext

                val matches = mutableListOf<Pair<Long, Float>>()
                val currentThreshold = _threshold.value
                val refIdSet = refIds.toSet()

                imageEmbeddings.forEach { imageEmbedding ->
                    // Skip reference images themselves
                    if (imageEmbedding.id in refIdSet) return@forEach

                    var bestScore = 0f

                    // Text-to-image similarity
                    if (textEmbedding != null) {
                        bestScore = maxOf(bestScore, textEmbedding.dot(imageEmbedding.embedding))
                    }

                    // Image-to-image similarity (against each reference)
                    refEmbeddings.forEach { ref ->
                        bestScore = maxOf(bestScore, ref.embedding.dot(imageEmbedding.embedding))
                    }

                    if (bestScore >= currentThreshold) {
                        matches.add(imageEmbedding.id to bestScore)
                    }
                }

                val sortedMatches = matches.sortedByDescending { it.second }
                val matchingIds = sortedMatches.map { it.first }.toSet()

                val matchingMedia = allMediaList.filter { it.id in matchingIds }
                    .sortedByDescending { media ->
                        sortedMatches.find { it.first == media.id }?.second ?: 0f
                    }

                _previewCount.value = sortedMatches.size
                _previewMedia.value = matchingMedia

                val mediaState = mapMediaToItem(
                    data = matchingMedia,
                    error = "",
                    albumId = -1L,
                    groupByMonth = false,
                    withMonthHeader = false,
                    defaultDateFormat = defaultDateFormat.value,
                    extendedDateFormat = extendedDateFormat.value,
                    weeklyDateFormat = weeklyDateFormat.value
                )

                _previewMediaState.value = mediaState
            }
        } finally {
            _isLoading.value = false
        }
    }

    fun saveCategory(onComplete: () -> Unit) {
        val name = _categoryName.value.trim()
        val terms = _searchTerms.value.trim()
        val refIds = _referenceImageIds.value

        if (name.isBlank() || (terms.isBlank() && refIds.isEmpty())) return

        viewModelScope.launch(Dispatchers.IO) {
            _isSaving.value = true
            try {
                if (isEditMode) {
                    val existingCategory = _category.value ?: return@launch
                    val searchTermsChanged = existingCategory.searchTerms != terms
                    val thresholdChanged = existingCategory.threshold != _threshold.value
                    val refImagesChanged = existingCategory.referenceImageIds != refIds
                    val updatedCategory = existingCategory.copy(
                        name = name,
                        searchTerms = terms,
                        referenceImageIds = refIds,
                        threshold = _threshold.value,
                        updatedAt = System.currentTimeMillis(),
                        embedding = if (searchTermsChanged) null else existingCategory.embedding
                    )
                    repository.updateCategory(updatedCategory)
                    if (searchTermsChanged || thresholdChanged || refImagesChanged) {
                        smartScanScheduler.automatic(SmartScanFeature.CATEGORIES.bit)
                    }
                } else {
                    val category = Category(
                        name = name,
                        searchTerms = terms,
                        referenceImageIds = refIds,
                        threshold = _threshold.value,
                        isUserCreated = true
                    )
                    repository.createCategory(category)
                    smartScanScheduler.automatic(SmartScanFeature.CATEGORIES.bit)
                }
                _saveSuccess.value = true
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun deleteCategory(onComplete: () -> Unit) {
        val categoryId = _categoryId.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _isSaving.value = true
            try {
                repository.deleteCategory(categoryId)
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            } finally {
                _isSaving.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        textSession?.close()
    }
}
