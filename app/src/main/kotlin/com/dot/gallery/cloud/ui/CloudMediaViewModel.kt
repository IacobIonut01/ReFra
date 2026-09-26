/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.cloud.core.CloudAlbum
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.cloudAlbumId
import com.dot.gallery.cloud.data.entity.CloudMediaEntity
import com.dot.gallery.cloud.data.repository.CloudRepository
import com.dot.gallery.core.Resource
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.presentation.util.printWarn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class CloudMediaUiState(
    val isLoading: Boolean = false,
    val media: List<CloudMediaEntity> = emptyList(),
    val albums: List<CloudAlbum> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class CloudMediaViewModel @Inject constructor(
    private val repository: CloudRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CloudMediaUiState())
    val uiState: StateFlow<CloudMediaUiState> = _uiState.asStateFlow()

    val cachedMedia = repository.getCachedMedia()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cloudTimelineMedia: StateFlow<List<Media.UriMedia>> = repository.getCachedMedia()
        .map { entities -> entities.filter { !it.trashed }.map { it.toUriMedia() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val cloudAlbums: StateFlow<List<Album>> = _uiState
        .map { state -> state.albums.map { it.toAlbum() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hasConfiguredProviders: Boolean
        get() = repository.hasConfiguredProviders

    private val albumsLoadMutex = Mutex()
    private var albumsLoaded = false

    init {
        if (hasConfiguredProviders) {
            loadRemoteAlbums()
        }
    }

    private suspend fun ensureAlbumsLoaded() {
        albumsLoadMutex.withLock {
            if (!albumsLoaded && _uiState.value.albums.isEmpty()) {
                try {
                    val resource = repository.getAllRemoteAlbums().first()
                    if (resource is Resource.Success) {
                        _uiState.value = _uiState.value.copy(
                            albums = resource.data ?: emptyList()
                        )
                    }
                    albumsLoaded = true
                } catch (e: Exception) {
                    // Network error — albums remain empty
                    printWarn("cloud.ui", "album load failed: $e")
                }
            }
        }
    }

    fun loadRemoteMedia(page: Int = 0, pageSize: Int = 100) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            repository.getAllRemoteAssets(page, pageSize).collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            media = resource.data ?: emptyList()
                        )
                    }
                    is Resource.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            media = resource.data ?: _uiState.value.media,
                            error = resource.message
                        )
                    }
                }
            }
        }
    }

    fun loadRemoteAlbums() {
        viewModelScope.launch {
            repository.getAllRemoteAlbums().collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        _uiState.value = _uiState.value.copy(
                            albums = resource.data ?: emptyList()
                        )
                        albumsLoaded = true
                    }
                    is Resource.Error -> {
                        if (resource.data != null) {
                            _uiState.value = _uiState.value.copy(
                                albums = resource.data ?: emptyList()
                            )
                        }
                    }
                }
            }
        }
    }

    fun loadAlbumMedia(type: ProviderType, configId: Long, albumId: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            repository.getAlbumMedia(type, configId, albumId).collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            media = resource.data ?: emptyList()
                        )
                    }
                    is Resource.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = resource.message
                        )
                    }
                }
            }
        }
    }

    private val _cloudAlbumMedia = MutableStateFlow<List<Media.UriMedia>>(emptyList())
    val cloudAlbumMedia: StateFlow<List<Media.UriMedia>> = _cloudAlbumMedia.asStateFlow()

    fun findCloudAlbumByComputedId(computedId: Long): CloudAlbum? {
        return _uiState.value.albums.find {
            cloudAlbumId(it.providerType, it.serverConfigId, it.remoteId) == computedId
        }
    }

    fun isCloudAlbumId(albumId: Long): Boolean {
        if (albumId >= 0) return false
        return findCloudAlbumByComputedId(albumId) != null
    }

    fun loadCloudAlbumMedia(computedAlbumId: Long) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            ensureAlbumsLoaded()
            val cloudAlbum = findCloudAlbumByComputedId(computedAlbumId)
            if (cloudAlbum == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Cloud album not found"
                )
                return@launch
            }
            repository.getAlbumMedia(
                cloudAlbum.providerType,
                cloudAlbum.serverConfigId,
                cloudAlbum.remoteId
            ).collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        val media = resource.data?.map { it.toUriMedia() } ?: emptyList()
                        _cloudAlbumMedia.value = media
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                    is Resource.Error -> {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = resource.message)
                    }
                }
            }
        }
    }

    suspend fun search(query: String): List<CloudMediaEntity> {
        return repository.search(query)
            .onFailure { printWarn("cloud.ui", "search failed: $it") }
            .getOrDefault(emptyList())
    }
}
