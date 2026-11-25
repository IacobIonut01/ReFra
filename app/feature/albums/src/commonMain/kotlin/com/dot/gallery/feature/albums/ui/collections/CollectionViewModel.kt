/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.feature.albums.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.domain.model.Collection
import com.dot.gallery.domain.model.CollectionWithCount
import com.dot.gallery.data.repository.CoreMediaRepository
import com.dot.gallery.domain.util.IODispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject


class CollectionViewModel @Inject constructor(
    private val repository: CoreMediaRepository
) : ViewModel() {

    val collectionsWithCount: StateFlow<List<CollectionWithCount>> =
        repository.getCollectionsWithCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    fun createCollection(name: String, onCreated: ((Long) -> Unit)? = null) {
        viewModelScope.launch(IODispatcher) {
            val id = repository.insertCollection(
                Collection(label = name)
            )
            onCreated?.invoke(id)
        }
    }

    fun renameCollection(collectionId: Long, newName: String) {
        viewModelScope.launch(IODispatcher) {
            repository.updateCollectionLabel(collectionId, newName)
        }
    }

    fun deleteCollection(collectionId: Long) {
        viewModelScope.launch(IODispatcher) {
            repository.deleteCollection(collectionId)
        }
    }

    fun togglePin(collectionId: Long, isPinned: Boolean) {
        viewModelScope.launch(IODispatcher) {
            repository.toggleCollectionPinned(collectionId, isPinned)
        }
    }

    fun updateCover(collectionId: Long, mediaId: Long?) {
        viewModelScope.launch(IODispatcher) {
            repository.updateCollectionCover(collectionId, mediaId)
        }
    }

    fun addMediaToCollection(collectionId: Long, mediaId: Long) {
        viewModelScope.launch(IODispatcher) {
            repository.addMediaToCollection(collectionId, mediaId)
        }
    }

    fun addMediaListToCollection(collectionId: Long, mediaIds: List<Long>) {
        viewModelScope.launch(IODispatcher) {
            repository.addMediaListToCollection(collectionId, mediaIds)
        }
    }

    fun removeMediaFromCollection(collectionId: Long, mediaId: Long) {
        viewModelScope.launch(IODispatcher) {
            repository.removeMediaFromCollection(collectionId, mediaId)
        }
    }

    fun createCollectionAndAddMedia(
        name: String,
        mediaIds: List<Long>,
        onCreated: ((Long) -> Unit)? = null
    ) {
        viewModelScope.launch(IODispatcher) {
            val id = repository.insertCollection(
                Collection(label = name)
            )
            repository.addMediaListToCollection(id, mediaIds)
            onCreated?.invoke(id)
        }
    }

    fun addAlbumsToCollection(collectionId: Long, albumIds: List<Long>) {
        viewModelScope.launch(IODispatcher) {
            repository.addAlbumsToCollection(collectionId, albumIds)
        }
    }

    fun createCollectionAndAddAlbums(
        name: String,
        albumIds: List<Long>,
        onCreated: ((Long) -> Unit)? = null
    ) {
        viewModelScope.launch(IODispatcher) {
            val id = repository.insertCollection(
                Collection(label = name)
            )
            repository.addAlbumsToCollection(id, albumIds)
            onCreated?.invoke(id)
        }
    }
}
