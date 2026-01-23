package com.dot.gallery.feature_node.presentation.ignored.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.IgnoredAlbum
import com.dot.gallery.feature_node.domain.model.matchesAlbum
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IgnoredOptionsViewModel @Inject constructor(
    private val repository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(IgnoredOptionsUiState())
    val uiState = _uiState.asStateFlow()

    private var onUpdateCallback: ((IgnoredAlbum) -> Unit)? = null
    private var onDeleteCallback: ((IgnoredAlbum) -> Unit)? = null

    fun initialize(
        ignoredAlbum: IgnoredAlbum?,
        albums: List<Album>,
        onUpdate: (IgnoredAlbum) -> Unit,
        onDelete: (IgnoredAlbum) -> Unit
    ) {
        onUpdateCallback = onUpdate
        onDeleteCallback = onDelete

        if (ignoredAlbum == null) {
            reset()
            return
        }

        val albumIds = ignoredAlbum.albumIds.ifEmpty { listOf(ignoredAlbum.id) }
        val selectedAlbums = albums.filter { it.id in albumIds }

        _uiState.value = IgnoredOptionsUiState(
            currentStep = OptionsStep.OPTIONS,
            ignoredAlbum = ignoredAlbum,
            editLocation = ignoredAlbum.location,
            editRegex = ignoredAlbum.wildcard ?: "",
            editSelectedAlbums = selectedAlbums,
            editMatchedAlbums = emptyList(),
            albums = albums,
            canProceed = calculateCanProceed(
                IgnoredOptionsUiState(
                    ignoredAlbum = ignoredAlbum,
                    editSelectedAlbums = selectedAlbums,
                    editRegex = ignoredAlbum.wildcard ?: ""
                )
            )
        )
    }

    fun updateAlbums(albums: List<Album>) {
        _uiState.update { state ->
            val ignoredAlbum = state.ignoredAlbum
            val albumIds = ignoredAlbum?.albumIds?.ifEmpty { listOfNotNull(ignoredAlbum.id) } ?: emptyList()
            val selectedAlbums = albums.filter { it.id in albumIds }
            state.copy(
                albums = albums,
                editSelectedAlbums = selectedAlbums.ifEmpty { state.editSelectedAlbums }
            )
        }
    }

    private fun calculateCanProceed(state: IgnoredOptionsUiState): Boolean {
        return when {
            state.isWildcard -> {
                val regex = state.editRegex
                regex.isNotEmpty() && runCatching { regex.toRegex() }.isSuccess
            }
            state.isMultiple -> state.editSelectedAlbums.isNotEmpty()
            else -> state.editSelectedAlbums.isNotEmpty()
        }
    }

    fun onAction(action: IgnoredOptionsAction) {
        when (action) {
            is IgnoredOptionsAction.NavigateBack -> navigateBack()
            is IgnoredOptionsAction.NavigateNext -> navigateNext()
            is IgnoredOptionsAction.Cancel -> { /* Handled by sheet */ }
            is IgnoredOptionsAction.Confirm -> confirmUpdate()
            is IgnoredOptionsAction.Delete -> performDelete()
            is IgnoredOptionsAction.Edit -> startEdit()
            is IgnoredOptionsAction.SetLocation -> setLocation(action.location)
            is IgnoredOptionsAction.SetRegex -> setRegex(action.regex)
            is IgnoredOptionsAction.ToggleAlbum -> toggleAlbum(action.album)
            is IgnoredOptionsAction.SelectAlbum -> selectAlbum(action.album)
        }
    }

    private fun navigateBack() {
        _uiState.update { state ->
            val newStep = when (state.currentStep) {
                OptionsStep.EDIT_CONFIRMATION -> OptionsStep.EDIT_ALBUMS
                OptionsStep.EDIT_ALBUMS -> OptionsStep.EDIT_LOCATION
                OptionsStep.EDIT_LOCATION -> OptionsStep.OPTIONS
                OptionsStep.OPTIONS -> OptionsStep.OPTIONS
            }
            state.copy(currentStep = newStep)
        }
    }

    private fun navigateNext() {
        _uiState.update { state ->
            when (state.currentStep) {
                OptionsStep.OPTIONS -> state
                OptionsStep.EDIT_LOCATION -> state.copy(currentStep = OptionsStep.EDIT_ALBUMS)
                OptionsStep.EDIT_ALBUMS -> {
                    val matchedAlbums = calculateMatchedAlbums(state)
                    state.copy(
                        currentStep = OptionsStep.EDIT_CONFIRMATION,
                        editMatchedAlbums = matchedAlbums
                    )
                }
                OptionsStep.EDIT_CONFIRMATION -> state
            }
        }
    }

    private fun calculateMatchedAlbums(state: IgnoredOptionsUiState): List<Album> {
        return if (state.isWildcard && state.editRegex.isNotEmpty()) {
            try {
                val regex = state.editRegex.toRegex()
                state.albums.filter(regex::matchesAlbum)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            state.editSelectedAlbums
        }
    }

    private fun startEdit() {
        _uiState.update { it.copy(currentStep = OptionsStep.EDIT_LOCATION) }
    }

    private fun setLocation(location: Int) {
        _uiState.update { it.copy(editLocation = location) }
    }

    private fun setRegex(regex: String) {
        _uiState.update { state ->
            state.copy(
                editRegex = regex,
                canProceed = calculateCanProceed(state.copy(editRegex = regex))
            )
        }
    }

    private fun toggleAlbum(album: Album) {
        _uiState.update { state ->
            val newList = if (album in state.editSelectedAlbums) {
                state.editSelectedAlbums - album
            } else {
                state.editSelectedAlbums + album
            }
            state.copy(
                editSelectedAlbums = newList,
                canProceed = calculateCanProceed(state.copy(editSelectedAlbums = newList))
            )
        }
    }

    private fun selectAlbum(album: Album) {
        _uiState.update { state ->
            state.copy(
                editSelectedAlbums = listOf(album),
                canProceed = true
            )
        }
    }

    private fun confirmUpdate() {
        val state = _uiState.value
        val ignoredAlbum = state.ignoredAlbum ?: return

        val updatedAlbum = when {
            state.isWildcard -> {
                ignoredAlbum.copy(
                    location = state.editLocation,
                    wildcard = state.editRegex,
                    matchedAlbums = state.editMatchedAlbums.map { it.label }
                )
            }
            state.isMultiple -> {
                ignoredAlbum.copy(
                    location = state.editLocation,
                    albumIds = state.editSelectedAlbums.map { it.id },
                    matchedAlbums = state.editSelectedAlbums.map { it.label }
                )
            }
            else -> {
                val album = state.editSelectedAlbums.firstOrNull()
                if (album != null) {
                    // For single selection, if the album ID changed, we need to delete
                    // the old entry first since the ID is the primary key
                    val newId = album.id
                    if (newId != ignoredAlbum.id) {
                        // Delete the old entry first
                        viewModelScope.launch {
                            onDeleteCallback?.invoke(ignoredAlbum)
                        }
                    }
                    ignoredAlbum.copy(
                        id = newId,
                        location = state.editLocation,
                        matchedAlbums = listOf(album.label)
                    )
                } else {
                    ignoredAlbum
                }
            }
        }

        viewModelScope.launch {
            onUpdateCallback?.invoke(updatedAlbum)
        }
    }

    private fun performDelete() {
        val ignoredAlbum = _uiState.value.ignoredAlbum ?: return
        viewModelScope.launch {
            onDeleteCallback?.invoke(ignoredAlbum)
        }
    }

    fun reset() {
        _uiState.value = IgnoredOptionsUiState()
    }
}
