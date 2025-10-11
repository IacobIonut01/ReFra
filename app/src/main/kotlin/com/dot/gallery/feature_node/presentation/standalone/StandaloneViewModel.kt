/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.standalone

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Media.UriMedia
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.model.VaultState
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Suppress("UNCHECKED_CAST")
@HiltViewModel(assistedFactory = StandaloneViewModel.Factory::class)
class StandaloneViewModel @AssistedInject constructor(
    @param:ApplicationContext
    private val applicationContext: Context,
    private val repository: MediaRepository,
    distributor: MediaDistributor,
    @Assisted private val reviewMode: Boolean,
    @Assisted private val dataList: List<Uri>
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            reviewMode: Boolean,
            dataList: List<Uri>
        ): StandaloneViewModel
    }

    var mediaId: Long = -1

    var mediaState = repository.getMediaListByUris(dataList, reviewMode)
        .map {
            val data = it.data
            if (data != null) {
                mediaId = data.first().id
                MediaState(media = data, isLoading = false)
            } else {
                mediaFromUris()
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MediaState())


    val vaults = distributor.vaultsMediaFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, VaultState())

    val albumsState = distributor.albumsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AlbumState())

    val metadataState = distributor.metadataFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, MediaMetadataState())


    fun addMedia(vault: Vault, media: UriMedia) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addMedia(vault, media)
        }
    }

    private fun <T: Media> mediaFromUris(): MediaState<T> {
        val mediaList = dataList.mapNotNull {
            Media.createFromUri(applicationContext, it) as T?
        }
        return MediaState(media = mediaList, isLoading = false)
    }

}