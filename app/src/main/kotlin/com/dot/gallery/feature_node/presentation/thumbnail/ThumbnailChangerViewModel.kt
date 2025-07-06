package com.dot.gallery.feature_node.presentation.thumbnail

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThumbnailChangerViewModel @Inject constructor(
    private val repository: MediaRepository
): ViewModel() {

    var albumId: Long = -1L

    val hasAlbumThumbnail = repository.hasAlbumThumbnail(albumId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    val albumDetails = repository.getAlbum(albumId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val albumThumbnail = repository.getAlbumThumbnail(albumId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun updateAlbumThumbnail(uri: Uri) {
        viewModelScope.launch {
            repository.updateAlbumThumbnail(albumId, uri)
        }
    }

}