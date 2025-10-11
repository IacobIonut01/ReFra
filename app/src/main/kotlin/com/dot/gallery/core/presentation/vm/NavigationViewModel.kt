package com.dot.gallery.core.presentation.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.VaultState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val distributor: MediaDistributor
): ViewModel() {

    val albumsState = distributor.albumsFlow.stateIn(
        viewModelScope, Eagerly, AlbumState()
    )

    val trashedMediaState = distributor.trashMediaFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(), MediaState()
    )

    val favoriteMediaState = distributor.favoritesMediaFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(), MediaState()
    )

    val allAlbumsMediaState = distributor.albumsTimelinesMediaFlow.stateIn(
        viewModelScope, Eagerly, emptyMap()
    )

    val timelineMediaState = distributor.timelineMediaFlow.stateIn(
        viewModelScope, Eagerly, MediaState()
    )

    val metadataState = distributor.metadataFlow.stateIn(
        viewModelScope, Eagerly, MediaMetadataState()
    )

    val vaultState = distributor.vaultsMediaFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(), VaultState()
    )

    fun updateGroupByMonth(value: Boolean) {
        viewModelScope.launch {
            distributor.groupByMonth = value
        }
    }

    fun updatePermissionGranted(permissionState: Boolean) {
        viewModelScope.launch {
            distributor.hasPermission.tryEmit(permissionState)
        }
    }

}