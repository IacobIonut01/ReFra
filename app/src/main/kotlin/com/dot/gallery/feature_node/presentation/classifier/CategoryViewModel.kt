package com.dot.gallery.feature_node.presentation.classifier

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.core.Constants
import com.dot.gallery.core.Settings
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val repository: MediaRepository
) : ViewModel() {

    var category: String = ""

    private val defaultDateFormat =
        repository.getSetting(Settings.Misc.DEFAULT_DATE_FORMAT, Constants.DEFAULT_DATE_FORMAT)
            .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.DEFAULT_DATE_FORMAT)

    private val extendedDateFormat =
        repository.getSetting(Settings.Misc.EXTENDED_DATE_FORMAT, Constants.EXTENDED_DATE_FORMAT)
            .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.EXTENDED_DATE_FORMAT)

    private val weeklyDateFormat =
        repository.getSetting(Settings.Misc.WEEKLY_DATE_FORMAT, Constants.WEEKLY_DATE_FORMAT)
            .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.WEEKLY_DATE_FORMAT)

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

    val selectionState = mutableStateOf(false)
    val selectedMedia = mutableStateOf<Set<Long>>(emptySet())

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

    fun <T : Media> addMedia(vault: Vault, media: T) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addMedia(vault, media)
        }
    }

}