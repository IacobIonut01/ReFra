package com.dot.gallery.feature_node.presentation.ignored.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.IgnoredAlbum
import com.dot.gallery.feature_node.domain.model.matchesAlbum
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.presentation.ignored.IgnoredState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class IgnoredSetupViewModel @Inject constructor(
    private val repository: MediaRepository
) : ViewModel() {

    private val _internalState = MutableStateFlow(IgnoredSetupUiState())
    
    private val blacklistState = repository.getBlacklistedAlbums()
        .map { IgnoredState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), IgnoredState())

    val uiState = combine(
        _internalState,
        blacklistState
    ) { state, blacklist ->
        state.copy(
            ignoredAlbums = blacklist.albums,
            canProceed = calculateCanProceed(state)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), IgnoredSetupUiState())

    private fun calculateCanProceed(state: IgnoredSetupUiState): Boolean {
        return when (state.currentStep) {
            SetupStep.TYPE_SELECTION -> true
            SetupStep.ALBUM_SELECTION -> when (state.type) {
                is IgnoredType.SINGLE -> state.type.selectedAlbum != null
                is IgnoredType.MULTIPLE -> state.type.selectedAlbums.isNotEmpty()
                is IgnoredType.REGEX -> {
                    val regex = state.type.regex
                    regex.isNotEmpty() && runCatching { regex.toRegex() }.isSuccess
                }
            }
            SetupStep.CONFIRMATION -> state.matchedAlbums.isNotEmpty()
        }
    }

    fun onAction(action: IgnoredSetupAction) {
        when (action) {
            is IgnoredSetupAction.NavigateBack -> navigateBack()
            is IgnoredSetupAction.NavigateNext -> navigateNext()
            is IgnoredSetupAction.Cancel -> reset()
            is IgnoredSetupAction.Confirm -> confirmAndSave()
            is IgnoredSetupAction.SetLocation -> setLocation(action.location)
            is IgnoredSetupAction.SetType -> setType(action.type)
            is IgnoredSetupAction.SetRegex -> setRegex(action.regex)
            is IgnoredSetupAction.ToggleAlbum -> toggleAlbum(action.album)
            is IgnoredSetupAction.SelectAlbum -> selectAlbum(action.album)
        }
    }

    fun updateAlbums(albums: List<Album>) {
        _internalState.update { it.copy(albums = albums) }
    }

    private fun navigateBack() {
        _internalState.update { state ->
            val newStep = when (state.currentStep) {
                SetupStep.CONFIRMATION -> SetupStep.ALBUM_SELECTION
                SetupStep.ALBUM_SELECTION -> SetupStep.TYPE_SELECTION
                SetupStep.TYPE_SELECTION -> SetupStep.TYPE_SELECTION
            }
            state.copy(currentStep = newStep)
        }
    }

    private fun navigateNext() {
        _internalState.update { state ->
            when (state.currentStep) {
                SetupStep.TYPE_SELECTION -> state.copy(currentStep = SetupStep.ALBUM_SELECTION)
                SetupStep.ALBUM_SELECTION -> {
                    val matchedAlbums = calculateMatchedAlbums(state)
                    state.copy(
                        currentStep = SetupStep.CONFIRMATION,
                        matchedAlbums = matchedAlbums
                    )
                }
                SetupStep.CONFIRMATION -> state
            }
        }
    }

    private fun calculateMatchedAlbums(state: IgnoredSetupUiState): List<Album> {
        return when (val type = state.type) {
            is IgnoredType.SINGLE -> listOfNotNull(type.selectedAlbum)
            is IgnoredType.MULTIPLE -> type.selectedAlbums
            is IgnoredType.REGEX -> {
                if (type.regex.isNotEmpty()) {
                    try {
                        val regex = type.regex.toRegex()
                        state.albums.filter(regex::matchesAlbum)
                    } catch (e: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            }
        }
    }

    private fun setLocation(location: Int) {
        _internalState.update { it.copy(location = location) }
    }

    private fun setType(type: IgnoredType) {
        _internalState.update { it.copy(type = type) }
    }

    private fun setRegex(regex: String) {
        _internalState.update { state ->
            if (state.type is IgnoredType.REGEX) {
                state.copy(type = IgnoredType.REGEX(regex))
            } else {
                state
            }
        }
    }

    private fun toggleAlbum(album: Album) {
        _internalState.update { state ->
            when (val type = state.type) {
                is IgnoredType.MULTIPLE -> {
                    val newList = if (album in type.selectedAlbums) {
                        type.selectedAlbums - album
                    } else {
                        type.selectedAlbums + album
                    }
                    state.copy(type = IgnoredType.MULTIPLE(newList))
                }
                else -> state
            }
        }
    }

    private fun selectAlbum(album: Album?) {
        _internalState.update { state ->
            when (state.type) {
                is IgnoredType.SINGLE -> state.copy(type = IgnoredType.SINGLE(album))
                else -> state
            }
        }
    }

    fun reset() {
        _internalState.value = IgnoredSetupUiState()
    }

    private fun confirmAndSave() {
        val state = _internalState.value
        val ignoredAlbums = uiState.value.ignoredAlbums
        
        val typeName = when (state.type) {
            is IgnoredType.SINGLE -> "Single"
            is IgnoredType.MULTIPLE -> "Multiple"
            is IgnoredType.REGEX -> "Regex"
        }

        val existingCount = ignoredAlbums.count { album ->
            album.label?.startsWith(typeName) == true
        }

        var labelNumber = existingCount + 1
        var generatedLabel = "$typeName #$labelNumber"
        while (ignoredAlbums.any { it.label == generatedLabel }) {
            labelNumber++
            generatedLabel = "$typeName #$labelNumber"
        }

        val ignored = when (val type = state.type) {
            is IgnoredType.SINGLE -> {
                val album = type.selectedAlbum!!
                IgnoredAlbum(
                    id = album.id,
                    label = generatedLabel,
                    location = state.location,
                    matchedAlbums = listOf(album.label)
                )
            }
            is IgnoredType.MULTIPLE -> {
                IgnoredAlbum(
                    id = UUID.randomUUID().mostSignificantBits,
                    label = generatedLabel,
                    albumIds = type.selectedAlbums.map { it.id },
                    location = state.location,
                    matchedAlbums = type.selectedAlbums.map { it.label }
                )
            }
            is IgnoredType.REGEX -> {
                IgnoredAlbum(
                    id = UUID.randomUUID().mostSignificantBits,
                    label = generatedLabel,
                    location = state.location,
                    wildcard = type.regex,
                    matchedAlbums = state.matchedAlbums.map { it.label }
                )
            }
        }

        viewModelScope.launch {
            repository.addBlacklistedAlbum(ignored)
        }
        
        reset()
    }
}