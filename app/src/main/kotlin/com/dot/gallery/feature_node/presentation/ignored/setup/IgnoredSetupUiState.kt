package com.dot.gallery.feature_node.presentation.ignored.setup

import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.IgnoredAlbum

// ========== Setup Sheet State ==========

enum class SetupStep {
    TYPE_SELECTION,
    ALBUM_SELECTION,
    CONFIRMATION
}

data class IgnoredSetupUiState(
    val currentStep: SetupStep = SetupStep.TYPE_SELECTION,
    val location: Int = IgnoredAlbum.ALBUMS_ONLY,
    val type: IgnoredType = IgnoredType.SINGLE(null),
    val matchedAlbums: List<Album> = emptyList(),
    val albums: List<Album> = emptyList(),
    val ignoredAlbums: List<IgnoredAlbum> = emptyList(),
    val albumGridSize: Int = 3,
    val canProceed: Boolean = false
) {
    val isWildcard: Boolean get() = type is IgnoredType.REGEX
    val isMultiple: Boolean get() = type is IgnoredType.MULTIPLE
    val isSingle: Boolean get() = type is IgnoredType.SINGLE
    
    val selectedAlbums: List<Album> get() = when (type) {
        is IgnoredType.SINGLE -> listOfNotNull(type.selectedAlbum)
        is IgnoredType.MULTIPLE -> type.selectedAlbums
        is IgnoredType.REGEX -> emptyList()
    }
    
    val regex: String get() = (type as? IgnoredType.REGEX)?.regex ?: ""
}

sealed interface IgnoredSetupAction {
    data object NavigateBack : IgnoredSetupAction
    data object NavigateNext : IgnoredSetupAction
    data object Cancel : IgnoredSetupAction
    data object Confirm : IgnoredSetupAction
    data class SetLocation(val location: Int) : IgnoredSetupAction
    data class SetType(val type: IgnoredType) : IgnoredSetupAction
    data class SetRegex(val regex: String) : IgnoredSetupAction
    data class ToggleAlbum(val album: Album) : IgnoredSetupAction
    data class SelectAlbum(val album: Album?) : IgnoredSetupAction
}

// ========== Options Sheet State ==========

enum class OptionsStep {
    OPTIONS,
    EDIT_LOCATION,
    EDIT_ALBUMS,
    EDIT_CONFIRMATION
}

data class IgnoredOptionsUiState(
    val currentStep: OptionsStep = OptionsStep.OPTIONS,
    val ignoredAlbum: IgnoredAlbum? = null,
    val editLocation: Int = IgnoredAlbum.ALBUMS_ONLY,
    val editRegex: String = "",
    val editSelectedAlbums: List<Album> = emptyList(),
    val editMatchedAlbums: List<Album> = emptyList(),
    val albums: List<Album> = emptyList(),
    val albumGridSize: Int = 3,
    val canProceed: Boolean = false
) {
    val isWildcard: Boolean get() = ignoredAlbum?.wildcard != null
    val isMultiple: Boolean get() = (ignoredAlbum?.albumIds?.size ?: 0) > 1
    val isSingle: Boolean get() = !isWildcard && !isMultiple
}

sealed interface IgnoredOptionsAction {
    data object NavigateBack : IgnoredOptionsAction
    data object NavigateNext : IgnoredOptionsAction
    data object Cancel : IgnoredOptionsAction
    data object Confirm : IgnoredOptionsAction
    data object Delete : IgnoredOptionsAction
    data object Edit : IgnoredOptionsAction
    data class SetLocation(val location: Int) : IgnoredOptionsAction
    data class SetRegex(val regex: String) : IgnoredOptionsAction
    data class ToggleAlbum(val album: Album) : IgnoredOptionsAction
    data class SelectAlbum(val album: Album) : IgnoredOptionsAction
}
