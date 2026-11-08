/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.vault.privatefolder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.sandbox.PrivateFolderRepository
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.core.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.core.util.or
import com.dot.gallery.domain.uri.path
import com.dot.gallery.domain.util.isVideo
import com.dot.gallery.domain.util.mapMediaToItem
import com.dot.gallery.domain.date.getDate


class PrivateFolderViewModel @Inject constructor(
    private val repository: PrivateFolderRepository,
    private val distributor: MediaDistributor
) : ViewModel() {

    companion object {
        const val PRIVATE_FOLDER_ALBUM_ID = com.dot.gallery.core.sandbox.PRIVATE_FOLDER_ALBUM_ID
    }

    private val scanState = repository.listMediaProgressive()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            PrivateFolderRepository.ScanState(emptyList(), isLoading = true)
        )

    private val uriMediaFlow = scanState.map { state ->
        state.isLoading to state.media.map { pm ->
            UriMedia(
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
                // SAF documents carry no duration, but Media.isVideo requires a
                // non-null duration; a null here made private-folder videos count
                // as images so the viewer showed a still frame instead of playing
                // them. Use a placeholder for videos so isVideo is true.
                duration = if (pm.isVideo) "" else null
            )
        }
    }.flowOn(Dispatchers.IO)

    val mediaState: StateFlow<MediaState<UriMedia>> = combine(
        uriMediaFlow,
        distributor.dateFormatsFlow
    ) { (isLoading, mediaList), (defaultFmt, extendedFmt, weeklyFmt) ->
        if (mediaList.isEmpty()) {
            MediaState(isLoading = isLoading)
        } else {
            mapMediaToItem(
                data = mediaList,
                error = "",
                albumId = PRIVATE_FOLDER_ALBUM_ID,
                defaultDateFormat = defaultFmt,
                extendedDateFormat = extendedFmt,
                weeklyDateFormat = weeklyFmt
            ).copy(isLoading = isLoading)
        }
    }.flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MediaState())
}
