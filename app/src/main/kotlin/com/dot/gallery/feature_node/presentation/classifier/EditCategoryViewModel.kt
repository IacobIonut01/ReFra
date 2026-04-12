/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.classifier

import ai.onnxruntime.OrtSession
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
import com.dot.gallery.feature_node.presentation.util.mapMediaToItem
import androidx.work.WorkManager
import com.dot.gallery.core.workers.startCategoryClassification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class EditCategoryViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val searchHelper: SearchHelper,
    private val workManager: WorkManager
) : ViewModel() {

    // Date format settings
    private val defaultDateFormat = repository.getSetting(
        Settings.Misc.DEFAULT_DATE_FORMAT,
        Constants.DEFAULT_DATE_FORMAT
    ).stateIn(viewModelScope, SharingStarted.Eagerly, Constants.DEFAULT_DATE_FORMAT)

    private val extendedDateFormat = repository.getSetting(
        Settings.Misc.EXTENDED_DATE_FORMAT,
        Constants.EXTENDED_DATE_FORMAT
    ).stateIn(viewModelScope, SharingStarted.Eagerly, Constants.EXTENDED_DATE_FORMAT)

    private val weeklyDateFormat = repository.getSetting(
        Settings.Misc.WEEKLY_DATE_FORMAT,
        Constants.WEEKLY_DATE_FORMAT
    ).stateIn(viewModelScope, SharingStarted.Eagerly, Constants.WEEKLY_DATE_FORMAT)

    private val _categoryId = MutableStateFlow<Long?>(null)
    
    private val _category = MutableStateFlow<Category?>(null)
    val category: StateFlow<Category?> = _category.asStateFlow()
    
    private val _categoryName = MutableStateFlow("")
    val categoryName: StateFlow<String> = _categoryName.asStateFlow()

    private val _searchTerms = MutableStateFlow("")
    val searchTerms: StateFlow<String> = _searchTerms.asStateFlow()

    private val _threshold = MutableStateFlow(Category.DEFAULT_THRESHOLD)
    val threshold: StateFlow<Float> = _threshold.asStateFlow()

    private val _previewMediaState = MutableStateFlow<MediaState<Media.UriMedia>>(MediaState())
    val previewMediaState: StateFlow<MediaState<Media.UriMedia>> = _previewMediaState.asStateFlow()

    private val _previewCount = MutableStateFlow(0)
    val previewCount: StateFlow<Int> = _previewCount.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private var textSession: OrtSession? = null
    private var searchJob: Job? = null

    // Image embeddings cache
    private var imageEmbeddings: List<ImageEmbedding> = emptyList()
    // Media cache for building MediaState
    private var allMedia: List<Media.UriMedia> = emptyList()
    
    fun loadCategory(categoryId: Long) {
        _categoryId.value = categoryId
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                // Load embeddings and media first
                imageEmbeddings = repository.getImageEmbeddings().first()
                val mediaResource = repository.getMedia().first()
                allMedia = mediaResource.data?.filterIsInstance<Media.UriMedia>() ?: emptyList()
                
                // Load category details
                val cat = repository.getCategoryAsync(categoryId)
                if (cat != null) {
                    _category.value = cat
                    _categoryName.value = cat.name
                    _searchTerms.value = cat.searchTerms
                    _threshold.value = cat.threshold
                    
                    // Trigger initial preview search
                    searchPreview(cat.searchTerms)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun updateCategoryName(name: String) {
        _categoryName.value = name
    }
    
    fun updateSearchTerms(terms: String) {
        _searchTerms.value = terms
        // Trigger preview search with debounce
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(500) // Debounce
            searchPreview(terms)
        }
    }
    
    fun updateThreshold(value: Float) {
        _threshold.value = value
        // Re-run preview with new threshold
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(300)
            searchPreview(_searchTerms.value)
        }
    }

    private suspend fun searchPreview(terms: String) {
        if (terms.isBlank()) {
            _previewMediaState.value = MediaState()
            _previewCount.value = 0
            return
        }

        try {
            withContext(Dispatchers.IO) {
                // Initialize session if needed
                if (textSession == null) {
                    textSession = searchHelper.setupTextSession()
                }

                val session = textSession ?: return@withContext

                // Get text embedding for search terms
                val textEmbedding = searchHelper.getTextEmbedding(session, terms)
                
                // Find matching images
                val matches = mutableListOf<Pair<Long, Float>>()
                val currentThreshold = _threshold.value

                imageEmbeddings.forEach { imageEmbedding ->
                    val similarity = textEmbedding.dot(imageEmbedding.embedding)
                    if (similarity >= currentThreshold) {
                        matches.add(imageEmbedding.id to similarity)
                    }
                }

                // Sort by similarity
                val sortedMatches = matches.sortedByDescending { it.second }
                val matchingIds = sortedMatches.map { it.first }.toSet()
                
                // Get the actual media objects
                val matchingMedia = allMedia.filter { it.id in matchingIds }
                    .sortedByDescending { media ->
                        sortedMatches.find { it.first == media.id }?.second ?: 0f
                    }
                
                _previewCount.value = sortedMatches.size

                // Build MediaState
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
        } catch (e: Exception) {
            // Handle error silently
        }
    }
    
    fun saveCategory(onComplete: () -> Unit) {
        val categoryId = _categoryId.value ?: return
        val name = _categoryName.value.trim()
        val terms = _searchTerms.value.trim()
        
        if (name.isBlank()) return
        
        viewModelScope.launch(Dispatchers.IO) {
            _isSaving.value = true
            try {
                val existingCategory = _category.value ?: return@launch
                val searchTermsChanged = existingCategory.searchTerms != terms
                val thresholdChanged = existingCategory.threshold != _threshold.value
                val updatedCategory = existingCategory.copy(
                    name = name,
                    searchTerms = terms,
                    threshold = _threshold.value,
                    updatedAt = System.currentTimeMillis(),
                    embedding = if (searchTermsChanged) null else existingCategory.embedding
                )
                repository.updateCategory(updatedCategory)
                if (searchTermsChanged || thresholdChanged) {
                    workManager.startCategoryClassification()
                }
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
