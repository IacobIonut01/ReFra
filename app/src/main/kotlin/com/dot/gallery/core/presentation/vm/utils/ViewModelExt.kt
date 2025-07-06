package com.dot.gallery.core.presentation.vm.utils

import androidx.compose.runtime.MutableState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.presentation.util.add
import com.dot.gallery.feature_node.presentation.util.remove
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun <T: Media> ViewModel.toggleSelection(
    mediaState: MediaState<T>,
    index: Int,
    selectedPhotoState: MutableState<Set<Long>>,
    multiSelectState: MutableState<Boolean>
) {
    viewModelScope.launch(Dispatchers.IO) {
        val item = mediaState.media[index]
        val selectedPhoto = selectedPhotoState.value.find { it == item.id }
        if (selectedPhoto != null) {
            selectedPhotoState.remove(selectedPhoto)
        } else {
            selectedPhotoState.add(item.id)
        }
        multiSelectState.value = selectedPhotoState.value.isNotEmpty()
    }
}