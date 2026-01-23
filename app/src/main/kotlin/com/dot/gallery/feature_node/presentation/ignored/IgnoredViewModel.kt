package com.dot.gallery.feature_node.presentation.ignored

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.feature_node.domain.model.IgnoredAlbum
import com.dot.gallery.feature_node.domain.model.matchesAlbum
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IgnoredViewModel @Inject constructor(
    private val repository: MediaRepository
): ViewModel() {

    val blacklistState = combine(
        repository.getBlacklistedAlbums(),
        repository.getAlbums(com.dot.gallery.feature_node.domain.util.MediaOrder.Default)
    ) { ignoredAlbums, albumsResource ->
        val allAlbums = albumsResource.data ?: emptyList()
        val updatedIgnoredAlbums = ignoredAlbums.map { ignored ->
            if (ignored.wildcard != null) {
                try {
                    val regex = ignored.wildcard.toRegex()
                    val matchedAlbums = allAlbums.filter(regex::matchesAlbum)
                    ignored.copy(matchedAlbums = matchedAlbums.map { it.label })
                } catch (e: Exception) {
                    ignored
                }
            } else {
                ignored
            }
        }
        IgnoredState(updatedIgnoredAlbums)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), IgnoredState())

    fun removeFromBlacklist(ignoredAlbum: IgnoredAlbum) {
        viewModelScope.launch {
            repository.removeBlacklistedAlbum(ignoredAlbum)
        }
    }
    
    fun updateIgnoredAlbum(ignoredAlbum: IgnoredAlbum) {
        viewModelScope.launch {
            repository.addBlacklistedAlbum(ignoredAlbum)
        }
    }
}