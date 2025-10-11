/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.feature_node.presentation.picker

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.core.Constants
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.Resource
import com.dot.gallery.core.Settings
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.IgnoredAlbum
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.presentation.util.mapMedia
import com.dot.gallery.feature_node.presentation.util.mediaFlowWithType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
open class PickerViewModel @Inject constructor(
    private val repository: MediaRepository,
    distributor: MediaDistributor
) : ViewModel() {

    private val defaultDateFormat = repository.getSetting(Settings.Misc.DEFAULT_DATE_FORMAT, Constants.DEFAULT_DATE_FORMAT)
        .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.DEFAULT_DATE_FORMAT)

    private val extendedDateFormat = repository.getSetting(Settings.Misc.EXTENDED_DATE_FORMAT, Constants.EXTENDED_DATE_FORMAT)
        .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.EXTENDED_DATE_FORMAT)

    private val weeklyDateFormat = repository.getSetting(Settings.Misc.WEEKLY_DATE_FORMAT, Constants.WEEKLY_DATE_FORMAT)
        .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.WEEKLY_DATE_FORMAT)

    var allowedMedia: AllowedMedia = AllowedMedia.BOTH
    var albumId: Long = -1L
        set(value) {
            field = value
            mediaState = lazy {
                combine(
                    repository.getBlacklistedAlbums(),
                    repository.mediaFlowWithType(value, allowedMedia)
                ) { blacklisted, mediaResult ->
                    val data = (mediaResult.data ?: emptyList()).toMutableList().apply {
                        removeAll { media -> blacklisted.any { it.matchesMedia(media) && it.hiddenInTimeline } }
                    }
                    val error = if (mediaResult is Resource.Error) mediaResult.message
                        ?: "An error occurred" else ""
                    if (error.isNotEmpty()) {
                        return@combine Resource.Error(message = error)
                    }
                    Resource.Success<List<Media>>(data)
                }.mapMedia(
                    albumId = value,
                    groupByMonth = false,
                    withMonthHeader = false,
                    updateDatabase = {},
                    defaultDateFormat = defaultDateFormat.value,
                    extendedDateFormat = extendedDateFormat.value,
                    weeklyDateFormat = weeklyDateFormat.value
                ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(), MediaState())
            }
        }

    var mediaState = lazy {
        combine(
            repository.getBlacklistedAlbums(),
            repository.mediaFlowWithType(albumId, allowedMedia)
        ) { blacklisted, mediaResult ->
            val data = (mediaResult.data ?: emptyList()).toMutableList().apply {
                removeAll { media -> blacklisted.any { it.shouldIgnore(media) } }
            }
            val error = if (mediaResult is Resource.Error) mediaResult.message
                ?: "An error occurred" else ""
            if (error.isNotEmpty()) {
                return@combine Resource.Error(message = error)
            }
            Resource.Success<List<Media>>(data)
        }.mapMedia(
            albumId = albumId,
            groupByMonth = false,
            withMonthHeader = false,
            updateDatabase = {},
            defaultDateFormat = defaultDateFormat.value,
            extendedDateFormat = extendedDateFormat.value,
            weeklyDateFormat = weeklyDateFormat.value
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(), MediaState())
    }

    val albumsState by lazy {
        combine(
            repository.getBlacklistedAlbums(),
            repository.getAlbumsWithType(allowedMedia)
        ) { blacklisted, albumsResult ->
            val data = (albumsResult.data ?: emptyList()).toMutableList().apply {
                removeAll { album -> blacklisted.any { it.matchesAlbum(album) && it.hiddenInAlbums } }
            }
            val error = if (albumsResult is Resource.Error) albumsResult.message
                ?: "An error occurred" else ""
            if (data.isEmpty()) {
                return@combine AlbumState(albums = listOf(emptyAlbum), error = error)
            }
            val albums = mutableListOf<Album>().apply {
                add(emptyAlbum)
                addAll(data)
            }
            AlbumState(albums = albums, error = error)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), AlbumState())
    }

    val metadataState = distributor.metadataFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, MediaMetadataState())


    private val emptyAlbum = Album(
        id = -1,
        label = "All",
        uri = Uri.EMPTY,
        pathToThumbnail = "",
        timestamp = 0,
        relativePath = ""
    )

    private fun IgnoredAlbum.shouldIgnore(media: Media) =
        matchesMedia(media) && (hiddenInTimeline && albumId == -1L || hiddenInAlbums && albumId != -1L)
}

enum class AllowedMedia {
    PHOTOS, VIDEOS, BOTH;

    override fun toString(): String {
        return when (this) {
            PHOTOS -> "image%"
            VIDEOS -> "video%"
            BOTH -> "%/%"
        }
    }

    fun toStringAny(): String {
        return when (this) {
            PHOTOS -> "image/*"
            VIDEOS -> "video/*"
            BOTH -> "*/*"
        }
    }
}