package com.dot.gallery.feature.ignored.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.data.repository.CoreMediaRepository
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.IgnoredAlbum
import com.dot.gallery.feature.ignored.ui.customLabel
import com.dot.gallery.feature.ignored.ui.ruleAlbumIds
import com.dot.gallery.feature.ignored.ui.toRuleWrite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import kotlin.random.Random

/** Drives the ignore sheet, both for a new rule and for editing an existing one. */
class IgnoredSetupViewModel @Inject constructor(
    private val repository: CoreMediaRepository
) : ViewModel() {

    private val _internalState = MutableStateFlow(IgnoredSetupUiState())

    val uiState = combine(
        _internalState,
        repository.getBlacklistedAlbums()
    ) { state, ignored ->
        state.copy(ignoredAlbums = ignored)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), IgnoredSetupUiState())

    fun onAction(action: IgnoredSetupAction) {
        when (action) {
            IgnoredSetupAction.NavigateBack -> _internalState.update { state ->
                if (state.canGoBack) state.copy(currentStep = SetupStep.CHOOSE) else state
            }
            IgnoredSetupAction.NavigateNext -> _internalState.update { state ->
                if (state.currentStep == SetupStep.CHOOSE && uiState.value.canContinue) {
                    state.copy(currentStep = SetupStep.WHERE)
                } else state
            }
            IgnoredSetupAction.Cancel -> reset()
            IgnoredSetupAction.Confirm -> confirmAndSave()
            is IgnoredSetupAction.SetMethod -> _internalState.update { it.copy(method = action.method) }
            is IgnoredSetupAction.ToggleAlbum -> _internalState.update { state ->
                val id = action.album.id
                val ids = if (id in state.selectedAlbumIds) state.selectedAlbumIds - id else state.selectedAlbumIds + id
                state.copy(selectedAlbumIds = ids)
            }
            is IgnoredSetupAction.SetRegex -> _internalState.update { it.copy(regex = action.regex) }
            is IgnoredSetupAction.SetSearch -> _internalState.update { it.copy(search = action.query) }
            is IgnoredSetupAction.SetLocation -> _internalState.update { it.copy(location = action.location) }
            is IgnoredSetupAction.SetName -> _internalState.update { it.copy(name = action.name) }
        }
    }

    fun updateAlbums(albums: List<Album>) {
        _internalState.update { it.copy(albums = albums) }
    }

    /** Starts a new rule on the first step. */
    fun startNew() {
        _internalState.update { IgnoredSetupUiState(albums = it.albums) }
    }

    /** Opens [rule] for editing on [step], prefilled with what it stores today. */
    fun startEdit(rule: IgnoredAlbum, step: SetupStep) {
        _internalState.update {
            IgnoredSetupUiState(
                currentStep = step,
                entryStep = step,
                editing = rule,
                method = if (rule.wildcard != null) IgnoreMethod.PATTERN else IgnoreMethod.ALBUMS,
                selectedAlbumIds = rule.ruleAlbumIds,
                regex = rule.wildcard.orEmpty(),
                location = rule.location,
                name = rule.customLabel.orEmpty(),
                albums = it.albums,
            )
        }
    }

    fun reset() {
        _internalState.update { IgnoredSetupUiState(albums = it.albums) }
    }

    private fun confirmAndSave() {
        val state = uiState.value
        if (!state.canContinue) return
        val write = state.toRuleWrite(newId = { Random.nextLong() })
        viewModelScope.launch {
            write.remove?.let { repository.removeBlacklistedAlbum(it) }
            repository.addBlacklistedAlbum(write.save)
        }
        reset()
    }
}
