package com.dot.gallery.feature_node.presentation.albumtimeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.cloud.core.CloudAlbum
import com.dot.gallery.cloud.core.CloudAlbumIdentity
import com.dot.gallery.cloud.data.repository.CloudRepository
import com.dot.gallery.core.MediaDistributor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Album-management actions for a cloud album open in the album view: rename,
 * delete, and remove-members. Calls are routed per-account through
 * [CloudRepository]; each successful mutation invalidates the distributor so
 * the album list and open timeline re-publish.
 */
@HiltViewModel
class CloudAlbumManageViewModel @Inject constructor(
    private val cloudRepository: CloudRepository,
    private val distributor: MediaDistributor
) : ViewModel() {

    suspend fun renameAlbum(identity: CloudAlbumIdentity, newName: String): Result<CloudAlbum> =
        cloudRepository.renameAlbum(
            type = identity.providerType,
            configId = identity.serverConfigId,
            remoteAlbumId = identity.remoteId,
            newName = newName
        ).also { if (it.isSuccess) refresh() }

    suspend fun deleteAlbum(identity: CloudAlbumIdentity): Result<Unit> =
        cloudRepository.deleteRemoteAlbum(
            type = identity.providerType,
            configId = identity.serverConfigId,
            remoteAlbumId = identity.remoteId
        ).also { if (it.isSuccess) refresh() }

    suspend fun removeMembers(
        identity: CloudAlbumIdentity,
        remoteIds: List<String>
    ): Result<Unit> =
        cloudRepository.removeFromAlbum(
            type = identity.providerType,
            configId = identity.serverConfigId,
            remoteAlbumId = identity.remoteId,
            assetIds = remoteIds
        ).also { if (it.isSuccess) refresh() }

    private fun refresh() {
        viewModelScope.launch { distributor.invalidate() }
    }
}
