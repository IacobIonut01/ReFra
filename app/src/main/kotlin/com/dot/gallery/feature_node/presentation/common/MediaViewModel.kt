/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.common

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.core.Constants
import com.dot.gallery.core.Resource
import com.dot.gallery.core.Settings
import com.dot.gallery.feature_node.domain.model.IgnoredAlbum
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Media.UriMedia
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.TimelineSettings
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.model.VaultState
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.domain.use_case.MediaHandleUseCase
import com.dot.gallery.feature_node.presentation.util.collectMedia
import com.dot.gallery.feature_node.presentation.util.mapMediaToItem
import com.dot.gallery.feature_node.presentation.util.mediaFlow
import com.dot.gallery.feature_node.presentation.util.update
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.xdrop.fuzzywuzzy.FuzzySearch
import javax.inject.Inject

@HiltViewModel
open class MediaViewModel @Inject constructor(
    private val repository: MediaRepository,
    val handler: MediaHandleUseCase,
) : ViewModel() {

    var lastQuery = mutableStateOf("")
    val multiSelectState = mutableStateOf(false)
    private val _searchMediaState = MutableStateFlow(MediaState<Media>())
    val searchMediaState = _searchMediaState.asStateFlow()
    val selectedPhotoState = mutableStateListOf<UriMedia>()

    var albumId: Long = -1L
    var target: String? = null
    var category: String? = null

    var groupByMonth: Boolean
        get() = settingsFlow.value?.groupTimelineByMonth ?: false
        set(value) {
            viewModelScope.launch(Dispatchers.IO) {
                settingsFlow.value?.copy(groupTimelineByMonth = value)?.let {
                    repository.updateTimelineSettings(it)
                }
            }
        }

    private val settingsFlow = repository.getTimelineSettings()
        .stateIn(
            viewModelScope,
            started = SharingStarted.Eagerly,
            TimelineSettings()
        )

    private val blacklistedAlbums = repository.getBlacklistedAlbums()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val defaultDateFormat =
        repository.getSetting(Settings.Misc.DEFAULT_DATE_FORMAT, Constants.DEFAULT_DATE_FORMAT)
            .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.DEFAULT_DATE_FORMAT)

    private val extendedDateFormat =
        repository.getSetting(Settings.Misc.EXTENDED_DATE_FORMAT, Constants.EXTENDED_DATE_FORMAT)
            .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.EXTENDED_DATE_FORMAT)

    private val weeklyDateFormat =
        repository.getSetting(Settings.Misc.WEEKLY_DATE_FORMAT, Constants.WEEKLY_DATE_FORMAT)
            .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.WEEKLY_DATE_FORMAT)

    private val permissionState = MutableStateFlow(false)

    val mediaFlow by lazy {
        combine(
            repository.mediaFlow(albumId, target),
            settingsFlow,
            blacklistedAlbums,
            combine(
                defaultDateFormat,
                extendedDateFormat,
                weeklyDateFormat
            ) { defaultDateFormat, extendedDateFormat, weeklyDateFormat ->
                Triple(defaultDateFormat, extendedDateFormat, weeklyDateFormat)
            },
            permissionState
        ) { result, settings, blacklistedAlbums, (defaultDateFormat, extendedDateFormat, weeklyDateFormat), hasPermission ->
            if (result is Resource.Error) return@combine MediaState(
                error = result.message ?: "",
                isLoading = false
            )
            updateDatabase()
            mapMediaToItem(
                data = (result.data ?: emptyList()).toMutableList().apply {
                    removeAll { media -> blacklistedAlbums.any { it.shouldIgnore(media) } }
                },
                error = result.message ?: "",
                albumId = albumId,
                groupByMonth = settings?.groupTimelineByMonth == true,
                defaultDateFormat = defaultDateFormat,
                extendedDateFormat = extendedDateFormat,
                weeklyDateFormat = weeklyDateFormat
            )
        }.stateIn(viewModelScope, started = SharingStarted.Eagerly, MediaState())
    }

    val vaultsFlow = repository.getVaults()
        .map { it.data ?: emptyList() }
        .map { VaultState(it, isLoading = false) }
        .stateIn(viewModelScope, started = SharingStarted.Eagerly, VaultState())

    private sealed class Event {
        data object UpdateDatabase : Event()
    }

    private val updater = Channel<Event>()

    @Composable
    fun CollectDatabaseUpdates() {
        LaunchedEffect(Unit) {
            viewModelScope.launch(Dispatchers.IO) {
                updater.receiveAsFlow().collectLatest {
                    when (it) {
                        is Event.UpdateDatabase -> {
                            repository.updateInternalDatabase()
                        }
                    }
                }
            }
        }
    }

    fun updatePermissionState(hasPermission: Boolean) {
        permissionState.tryEmit(hasPermission)
    }

    private fun updateDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            updater.send(Event.UpdateDatabase)
        }
    }

    fun <T : Media> addMedia(vault: Vault, media: T) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addMedia(vault, media)
        }
    }

    fun clearQuery() {
        queryMedia("")
    }


    fun toggleFavorite(
        result: ActivityResultLauncher<IntentSenderRequest>,
        mediaList: List<UriMedia>,
        favorite: Boolean = false
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            handler.toggleFavorite(result, mediaList, favorite)
        }
    }

    fun toggleSelection(index: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = mediaFlow.value.media[index]
            val selectedPhoto = selectedPhotoState.find { it.id == item.id }
            if (selectedPhoto != null) {
                selectedPhotoState.remove(selectedPhoto)
            } else {
                selectedPhotoState.add(item)
            }
            multiSelectState.update(selectedPhotoState.isNotEmpty())
        }
    }

    fun queryMedia(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                lastQuery.value = query
            }
            if (query.isEmpty()) {
                _searchMediaState.tryEmit(MediaState(isLoading = false))
                return@launch
            } else {
                _searchMediaState.tryEmit(MediaState(isLoading = true))
                _searchMediaState.collectMedia(
                    data = mediaFlow.value.media.parseQuery(query),
                    error = mediaFlow.value.error,
                    albumId = albumId,
                    groupByMonth = groupByMonth,
                    defaultDateFormat = defaultDateFormat.value,
                    extendedDateFormat = extendedDateFormat.value,
                    weeklyDateFormat = weeklyDateFormat.value
                )
            }
        }
    }

    private suspend fun <T : Media> List<T>.parseQuery(query: String): List<T> {
        return withContext(Dispatchers.IO) {
            if (query.isEmpty())
                return@withContext emptyList()
            val matches = FuzzySearch.extractSorted(query, this@parseQuery, { it.toString() }, 60)
            return@withContext matches.map { it.referent }.ifEmpty { emptyList() }
        }
    }

    private fun IgnoredAlbum.shouldIgnore(media: Media) =
        matchesMedia(media) && (hiddenInTimeline && albumId == -1L || hiddenInAlbums && albumId != -1L)
}