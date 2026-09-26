/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.standalone

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
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
import com.dot.gallery.feature_node.domain.util.getUri
import androidx.work.WorkManager
import com.dot.gallery.core.workers.VaultOperationWorker
import com.dot.gallery.core.workers.enqueueVaultOperation
import com.dot.gallery.feature_node.presentation.util.printError
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = StandaloneViewModel.Factory::class)
class StandaloneViewModel @AssistedInject constructor(
    @param:ApplicationContext
    private val applicationContext: Context,
    private val repository: MediaRepository,
    private val workManager: WorkManager,
    distributor: MediaDistributor,
    @Assisted("reviewMode") private val reviewMode: Boolean,
    @Assisted("isSecure") private val isSecure: Boolean,
    @Assisted private val dataList: List<Uri>
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("reviewMode") reviewMode: Boolean,
            @Assisted("isSecure") isSecure: Boolean,
            dataList: List<Uri>
        ): StandaloneViewModel
    }

    companion object {
        private const val TAG = "ui.standalone"
        private const val PENDING_POLL_INTERVAL_MS = 250L
        private const val PENDING_WAIT_TIMEOUT_MS = 30_000L
    }

    private val _mediaId = MutableStateFlow(-1L)
    val mediaId: StateFlow<Long> = _mediaId.asStateFlow()

    private val targetId: Long = dataList.firstOrNull()?.let { uri ->
        try { ContentUris.parseId(uri) } catch (_: NumberFormatException) { null }
    } ?: -1L

    var mediaState = repository.getMediaListByUris(dataList, reviewMode, onlyMatching = isSecure)
        .flatMapLatest {
            val data = it.data
            if (data != null) {
                _mediaId.value = if (targetId != -1L && data.any { m -> m.id == targetId }) {
                    targetId
                } else {
                    data.first().id
                }
                flowOf(MediaState(media = data, isLoading = false))
            } else {
                // The item isn't visible through MediaStore yet. This is the
                // common case for a freshly captured photo (e.g. Google Camera),
                // which is written as IS_PENDING=1 and cannot be read reliably
                // until it is finalized. Wait gracefully instead of rendering a
                // broken/unreadable item; the repository flow re-emits once the
                // camera clears the pending flag.
                pendingAwareFallbackFlow()
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
        workManager.enqueueVaultOperation(
            operation = VaultOperationWorker.OP_ENCRYPT,
            media = listOf(media.getUri()),
            vault = vault
        )
    }

    /**
     * Emits a graceful state while a freshly captured (still IS_PENDING) URI is
     * being finalized by the producing app (e.g. the camera). While the target
     * URI is pending we keep showing a loading state and poll until it clears,
     * then build the media from the URI. If the URI is not pending (regular
     * VIEW/REVIEW or a file/vault URI) we build the media immediately.
     *
     * This is a safety net in addition to the repository flow, which re-emits on
     * its own once the camera clears the pending flag (its query observes the
     * MediaStore collection for changes).
     */
    private fun <T : Media> pendingAwareFallbackFlow() = flow<MediaState<T>> {
        val target = dataList.firstOrNull()
        if (target == null || !isPending(target)) {
            emit(mediaFromUris())
            return@flow
        }
        emit(MediaState(isLoading = true))
        val deadline = SystemClock.elapsedRealtime() + PENDING_WAIT_TIMEOUT_MS
        while (isPending(target) && SystemClock.elapsedRealtime() < deadline) {
            delay(PENDING_POLL_INTERVAL_MS)
        }
        emit(mediaFromUris())
    }.flowOn(Dispatchers.IO)

    /**
     * Returns true when [uri] is a MediaStore content URI whose IS_PENDING flag
     * is still set. Querying the granted single-item URI works even for items
     * owned by another app, thanks to the per-URI read grant.
     */
    private fun isPending(uri: Uri): Boolean {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return try {
            val projection = arrayOf(MediaStore.MediaColumns.IS_PENDING)
            val queryArgs = Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
            }
            applicationContext.contentResolver.query(uri, projection, queryArgs, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(MediaStore.MediaColumns.IS_PENDING)
                        idx >= 0 && c.getInt(idx) == 1
                    } else false
                } ?: false
        } catch (e: Exception) {
            printError(TAG, "Failed to query IS_PENDING for $uri", e)
            false
        }
    }

    private fun <T: Media> mediaFromUris(): MediaState<T> {
        val mediaList = dataList.mapNotNull {
            Media.createFromUri(applicationContext, it) as T?
        }
        if (mediaList.isNotEmpty()) {
            _mediaId.value = if (targetId != -1L && mediaList.any { m -> m.id == targetId }) {
                targetId
            } else {
                mediaList.first().id
            }
        }
        return MediaState(media = mediaList, isLoading = false)
    }

}