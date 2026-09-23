/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.cloud.core.PersonInfo
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.cloud.data.repository.CloudRepository
import com.dot.gallery.cloud.local.FaceCropLoader
import com.dot.gallery.core.Constants
import com.dot.gallery.core.Resource
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.util.mapMediaToItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PersonDetailUiState(
    val person: PersonInfo? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

/** A detected face crop shown in the detail screen's face strip. */
data class FaceCropItem(
    val faceId: Long,
    val mediaId: Long,
    val imageUri: String
)

@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CloudRepository,
    private val registry: ProviderRegistry,
    private val blurrer: com.dot.gallery.cloud.local.LocalPeopleBlurrer,
    private val faceCropLoader: FaceCropLoader
) : ViewModel() {

    private val personId: String = savedStateHandle["personId"] ?: ""
    private val configId: Long = savedStateHandle["configId"] ?: Long.MIN_VALUE

    private val _uiState = MutableStateFlow(PersonDetailUiState())
    val uiState: StateFlow<PersonDetailUiState> = _uiState.asStateFlow()

    private val _mediaState = MutableStateFlow(MediaState<Media.UriMedia>())
    val mediaState: StateFlow<MediaState<Media.UriMedia>> = _mediaState.asStateFlow()

    /** Non-null (done,total) while a "blur everywhere" batch is running. */
    private val _blurProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val blurProgress: StateFlow<Pair<Int, Int>?> = _blurProgress.asStateFlow()

    /** Other on-device people this person can be merged into. */
    private val _mergeCandidates = MutableStateFlow<List<PersonInfo>>(emptyList())
    val mergeCandidates: StateFlow<List<PersonInfo>> = _mergeCandidates.asStateFlow()

    /** This person's detected face crops, best-confidence first — the purity strip. */
    private val _personFaces = MutableStateFlow<List<FaceCropItem>>(emptyList())
    val personFaces: StateFlow<List<FaceCropItem>> = _personFaces.asStateFlow()

    /** Local people the clusterer finds similar to this person — merge shortcuts. */
    private val _similarPeople = MutableStateFlow<List<PersonInfo>>(emptyList())
    val similarPeople: StateFlow<List<PersonInfo>> = _similarPeople.asStateFlow()

    /** Raw media of this person, used to pick a new cover face. */
    private val _personMedia = MutableStateFlow<List<Media.UriMedia>>(emptyList())
    val personMedia: StateFlow<List<Media.UriMedia>> = _personMedia.asStateFlow()

    fun setCover(media: Media.UriMedia) {
        val id = _uiState.value.person?.id ?: return
        val uri = media.getUri().toString()
        viewModelScope.launch {
            localProvider()?.setCover(id, media.id, uri)
            _uiState.value = _uiState.value.copy(
                person = _uiState.value.person?.copy(thumbnailUrl = uri)
            )
        }
    }

    /** Set the person's cover to a detected face crop — tighter than a full photo. */
    fun setCoverFace(face: FaceCropItem) {
        val id = _uiState.value.person?.id ?: return
        viewModelScope.launch {
            localProvider()?.setCover(id, face.mediaId, face.imageUri)
            _uiState.value = _uiState.value.copy(
                person = _uiState.value.person?.copy(thumbnailUrl = face.imageUri)
            )
        }
    }

    /** True when the current person is an on-device (local) cluster that supports management. */
    val isLocalPerson: Boolean
        get() = _uiState.value.person?.providerType == com.dot.gallery.cloud.core.ProviderType.LOCAL_PEOPLE

    private fun localProvider(): com.dot.gallery.cloud.local.LocalPeopleProvider? =
        registry.getByConfigId(configId) as? com.dot.gallery.cloud.local.LocalPeopleProvider

    fun hidePerson(onDone: () -> Unit) {
        val id = _uiState.value.person?.id ?: return
        viewModelScope.launch {
            localProvider()?.setHidden(id, true)
            onDone()
        }
    }

    /**
     * Merge this person into [targetPersonId], then invoke [onMerged]. The caller
     * navigates away in the callback — launching the write on [viewModelScope] and
     * popping the screen immediately would cancel the transaction before it commits.
     */
    fun mergeInto(targetPersonId: String, onMerged: () -> Unit) {
        val sourceId = _uiState.value.person?.id ?: return
        if (sourceId == targetPersonId) return
        viewModelScope.launch {
            localProvider()?.mergePeople(sourceId, targetPersonId)
            onMerged()
        }
    }

    /** Merge a look-alike person INTO the current one — the current identity is kept. */
    fun mergeSimilarIntoCurrent(otherPersonId: String) {
        val targetId = _uiState.value.person?.id ?: return
        if (targetId == otherPersonId) return
        viewModelScope.launch { localProvider()?.mergePeople(otherPersonId, targetId) }
    }

    /**
     * Remove a single detected face ("this is not me"): writes a durable EXCLUDE
     * link so the batch clusterer keeps the face out on the next re-group.
     */
    fun removeFace(faceId: Long, onPersonGone: () -> Unit) {
        val id = _uiState.value.person?.id ?: return
        viewModelScope.launch {
            val stillExists = localProvider()?.removeFacesFromPerson(id, listOf(faceId)) ?: true
            if (!stillExists) onPersonGone()
        }
    }

    /**
     * Un-assign [mediaIds] from this person without deleting the media. The grid updates
     * via the reactive people flow; [onPersonGone] fires when the removal emptied the
     * person and it was deleted.
     */
    fun removeMediaFromPerson(mediaIds: List<Long>, onPersonGone: () -> Unit) {
        val id = _uiState.value.person?.id ?: return
        viewModelScope.launch {
            val stillExists = localProvider()?.removeMediaFromPerson(id, mediaIds) ?: true
            if (!stillExists) onPersonGone()
        }
    }

    fun blurEverywhere(useMosaic: Boolean) {
        val id = _uiState.value.person?.id ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _blurProgress.value = 0 to 0
            blurrer.blurPersonEverywhere(
                personId = id,
                brush = if (useMosaic) com.dot.gallery.feature_node.domain.model.editor.MarkupBrush.Mosaic
                else com.dot.gallery.feature_node.domain.model.editor.MarkupBrush.Blur,
                onProgress = { done, total -> _blurProgress.value = done to total }
            )
            _blurProgress.value = null
        }
    }

    init {
        loadPerson()
        if (personId.isNotBlank() && configId != Long.MIN_VALUE) {
            viewModelScope.launch(Dispatchers.IO) {
                localProvider()?.observePersonFaces(personId)?.collect { faces ->
                    val mediaIdByFace = faces.associate { it.id to it.mediaId }
                    _personFaces.value = faceCropLoader.faceCrops(faces)
                        .mapNotNull { (faceId, uri) ->
                            mediaIdByFace[faceId]?.let { FaceCropItem(faceId, it, uri) }
                        }
                }
            }
            viewModelScope.launch {
                localProvider()?.observeMergeSuggestions()?.collect { suggestions ->
                    _similarPeople.value = suggestions.mapNotNull { s ->
                        when {
                            s.first.id == personId -> s.second
                            s.second.id == personId -> s.first
                            else -> null
                        }
                    }
                }
            }
        }
    }

    private fun loadPerson() {
        if (personId.isBlank() || configId == Long.MIN_VALUE) return
        _uiState.value = _uiState.value.copy(isLoading = true)

        viewModelScope.launch {
            repository.getAllPeople().collect { resource ->
                if (resource is Resource.Success) {
                    val person = resource.data?.find {
                        it.id == personId && it.serverConfigId == configId
                    }
                    _uiState.value = _uiState.value.copy(person = person)
                    _mergeCandidates.value = resource.data
                        ?.filter {
                            it.accountKey != person?.accountKey &&
                                it.serverConfigId == configId &&
                                it.providerType == com.dot.gallery.cloud.core.ProviderType.LOCAL_PEOPLE
                        } ?: emptyList()
                }
            }
        }

        viewModelScope.launch {
            val peopleResource = repository.getAllPeople().first { it is Resource.Success }
            val person = peopleResource.data.orEmpty().find {
                it.id == personId && it.serverConfigId == configId
            }
            if (person == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Person account not available"
                )
                return@launch
            }

            repository.getPersonMedia(
                type = person.providerType,
                configId = person.serverConfigId,
                personId = person.id
            ).collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        val mediaList = resource.data?.filterIsInstance<Media.UriMedia>() ?: emptyList()
                        _personMedia.value = mediaList
                        val mapped = mapMediaToItem(
                            data = mediaList,
                            error = "",
                            albumId = -1L,
                            groupByMonth = false,
                            withMonthHeader = false,
                            groupSimilarMedia = false,
                            defaultDateFormat = Constants.DEFAULT_DATE_FORMAT,
                            extendedDateFormat = Constants.EXTENDED_DATE_FORMAT,
                            weeklyDateFormat = Constants.WEEKLY_DATE_FORMAT
                        )
                        _mediaState.value = mapped
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                    is Resource.Error -> _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = resource.message
                    )
                }
            }
        }
    }

    fun updateName(name: String) {
        if (personId.isBlank() || configId == Long.MIN_VALUE) return
        val person = _uiState.value.person ?: return
        viewModelScope.launch {
            repository.updatePersonName(
                type = person.providerType,
                configId = person.serverConfigId,
                personId = person.id,
                name = name
            ).onSuccess {
                _uiState.value = _uiState.value.copy(
                    person = _uiState.value.person?.copy(name = name)
                )
            }
        }
    }

    fun updateBirthDate(birthDate: String) {
        if (personId.isBlank() || configId == Long.MIN_VALUE) return
        val person = _uiState.value.person ?: return
        viewModelScope.launch {
            repository.updatePersonBirthDate(
                type = person.providerType,
                configId = person.serverConfigId,
                personId = person.id,
                birthDate = birthDate
            ).onSuccess {
                _uiState.value = _uiState.value.copy(
                    person = _uiState.value.person?.copy(birthDate = birthDate)
                )
            }
        }
    }
}
