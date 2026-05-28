/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.privatefolder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.sandbox.PrivateFolderRepository
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.core.Constants
import com.dot.gallery.feature_node.presentation.util.getDate
import com.dot.gallery.feature_node.presentation.util.mapMediaToItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PrivateFolderViewModel @Inject constructor(
    private val repository: PrivateFolderRepository,
    private val distributor: MediaDistributor
) : ViewModel() {

    companion object {
        const val PRIVATE_FOLDER_ALBUM_ID = -300L
    }

    private val rawMedia = repository.listMedia()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val uriMediaFlow = rawMedia.map { list ->
        list.map { pm ->
            Media.UriMedia(
                id = pm.uri.hashCode().toLong() or (1L shl 62),
                label = pm.displayName,
                uri = pm.uri,
                path = pm.uri.toString(),
                relativePath = "",
                albumID = PRIVATE_FOLDER_ALBUM_ID,
                albumLabel = "Private folder",
                timestamp = pm.lastModified / 1000,
                fullDate = (pm.lastModified / 1000).getDate(
                    Constants.DEFAULT_DATE_FORMAT
                ),
                mimeType = pm.mimeType,
                favorite = 0,
                trashed = 0,
                size = pm.size,
                duration = null
            )
        }
    }.flowOn(Dispatchers.IO)

    val mediaState: StateFlow<MediaState<Media.UriMedia>> = combine(
        uriMediaFlow,
        distributor.dateFormatsFlow
    ) { mediaList, (defaultFmt, extendedFmt, weeklyFmt) ->
        if (mediaList.isEmpty()) {
            MediaState(isLoading = false)
        } else {
            mapMediaToItem(
                data = mediaList,
                error = "",
                albumId = PRIVATE_FOLDER_ALBUM_ID,
                defaultDateFormat = defaultFmt,
                extendedDateFormat = extendedFmt,
                weeklyDateFormat = weeklyFmt
            )
        }
    }.flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MediaState())
}
