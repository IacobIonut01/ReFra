/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.standalone

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Media.UriMedia
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.model.VaultState
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.domain.use_case.MediaHandleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Suppress("UNCHECKED_CAST")
@HiltViewModel
class StandaloneViewModel @Inject constructor(
    @ApplicationContext
    private val applicationContext: Context,
    private val repository: MediaRepository,
    val handler: MediaHandleUseCase
) : ViewModel() {

    var reviewMode: Boolean = false
        set(value) {
            field = value
            mediaState =
                repository.getMediaListByUris(dataList, reviewMode)
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
        }
    var dataList: List<Uri> = emptyList()
        set(value) {
            field = value
            mediaState =
                repository.getMediaListByUris(value, reviewMode)
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


    val vaults = repository.getVaults().map { it.data ?: emptyList() }
        .map { VaultState(it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, VaultState())

    fun addMedia(vault: Vault, media: UriMedia) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addMedia(vault, media)
        }
    }

    private fun <T: Media> mediaFromUris(): MediaState<T> {
        val list = mutableListOf<T>()
        dataList.forEach {
            Media.createFromUri(applicationContext, it)?.let { it1 -> list.add(it1 as T) }
        }
        return MediaState(media = list, isLoading = false)
    }

}