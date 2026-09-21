/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.feature_node.presentation.picker

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.BuildConfig
import com.dot.gallery.cloud.data.repository.CloudRepository
import com.dot.gallery.core.Constants
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.Resource
import com.dot.gallery.core.Settings
import com.dot.gallery.core.sandbox.PrivateFolderRepository
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.IgnoredAlbum
import com.dot.gallery.feature_node.domain.model.LockedAlbum
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.model.VaultState
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.domain.util.isEncrypted
import com.dot.gallery.feature_node.domain.util.isImage
import com.dot.gallery.feature_node.domain.util.isVideo
import com.dot.gallery.feature_node.presentation.privatefolder.PrivateFolderViewModel.Companion.PRIVATE_FOLDER_ALBUM_ID
import com.dot.gallery.feature_node.presentation.util.SystemDateFormatField
import com.dot.gallery.feature_node.presentation.util.createDecryptedTempFile
import com.dot.gallery.feature_node.presentation.util.getDate
import com.dot.gallery.feature_node.presentation.util.mapMedia
import com.dot.gallery.feature_node.presentation.util.mapMediaToItem
import com.dot.gallery.feature_node.presentation.util.mediaFlowWithType
import com.dot.gallery.feature_node.presentation.util.resolvedDateFormat
import com.dot.gallery.feature_node.presentation.util.resolveShareableUri
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
open class PickerViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: MediaRepository,
    private val privateFolderRepository: PrivateFolderRepository,
    private val cloudRepository: CloudRepository,
    private val keychainHolder: KeychainHolder,
    private val distributor: MediaDistributor
) : ViewModel() {

    init {
        // Vault items are decrypted (plaintext) and cloud items downloaded into the cache dir on
        // "Add"; they can't be deleted immediately because the receiving app reads the granted
        // content:// URI after we finish. Sweep leftovers from prior picks that have outlived their
        // read grant so decrypted/downloaded content doesn't linger.
        cleanupStaleShareTempFiles()
    }

    private fun cleanupStaleShareTempFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val cutoff = System.currentTimeMillis() - SHARE_TEMP_TTL_MS
                context.cacheDir.listFiles()?.forEach { file ->
                    val name = file.name
                    if ((name.startsWith("shared_") || name.startsWith("cloud_share_")) &&
                        file.isFile && file.lastModified() < cutoff
                    ) {
                        file.delete()
                    }
                }
            }
        }
    }

    private val defaultDateFormat = repository.getSetting(Settings.Misc.DEFAULT_DATE_FORMAT, "")
        .map { resolvedDateFormat(context, it, SystemDateFormatField.DEFAULT) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.DEFAULT_DATE_FORMAT)

    private val extendedDateFormat = repository.getSetting(Settings.Misc.EXTENDED_DATE_FORMAT, "")
        .map { resolvedDateFormat(context, it, SystemDateFormatField.EXTENDED) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.EXTENDED_DATE_FORMAT)

    private val weeklyDateFormat = repository.getSetting(Settings.Misc.WEEKLY_DATE_FORMAT, "")
        .map { resolvedDateFormat(context, it, SystemDateFormatField.WEEKLY) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.WEEKLY_DATE_FORMAT)

    var allowedMedia: AllowedMedia = AllowedMedia.BOTH
    var albumId: Long = -1L
        set(value) {
            field = value
            mediaState = lazy {
                combine(
                    repository.getBlacklistedAlbums(),
                    repository.getLockedAlbums(),
                    repository.mediaFlowWithType(value, allowedMedia)
                ) { blacklisted, lockedAlbums, mediaResult ->
                    val lockedIds = lockedAlbums.mapTo(HashSet()) { it.id }
                    val data = (mediaResult.data ?: emptyList()).toMutableList().apply {
                        removeAll { media -> blacklisted.any { it.shouldIgnore(media) } }
                        if (value == -1L) {
                            removeAll { media -> media.albumID in lockedIds }
                        }
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
            repository.getLockedAlbums(),
            repository.mediaFlowWithType(albumId, allowedMedia)
        ) { blacklisted, lockedAlbums, mediaResult ->
            val lockedIds = lockedAlbums.mapTo(HashSet()) { it.id }
            val data = (mediaResult.data ?: emptyList()).toMutableList().apply {
                removeAll { media -> blacklisted.any { it.shouldIgnore(media) } }
                if (albumId == -1L) {
                    removeAll { media -> media.albumID in lockedIds }
                }
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
            repository.getLockedAlbums(),
            repository.getAlbumsWithType(allowedMedia),
            repository.getAlbumThumbnails()
        ) { blacklisted, lockedAlbums, albumsResult, thumbnails ->
            val lockedIds = lockedAlbums.mapTo(HashSet()) { it.id }
            val thumbnailMap = thumbnails.associateBy { it.albumId }
            val data = (albumsResult.data ?: emptyList()).toMutableList().apply {
                removeAll { album -> blacklisted.any { it.matchesAlbum(album) && it.hiddenInAlbums } }
            }.map { album ->
                val customThumbnail = thumbnailMap[album.id]
                album.copy(
                    isLocked = album.id in lockedIds,
                    uri = customThumbnail?.thumbnailUri ?: album.uri
                )
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

    val privateFolderMediaState: StateFlow<MediaState<Media>> by lazy {
        privateFolderRepository.listMedia().map { list ->
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
                    fullDate = (pm.lastModified / 1000).getDate(Constants.DEFAULT_DATE_FORMAT),
                    mimeType = pm.mimeType,
                    favorite = 0,
                    trashed = 0,
                    size = pm.size,
                    // Placeholder duration so video files register as videos
                    // (Media.isVideo requires a non-null duration) — #1003.
                    duration = if (pm.isVideo) "" else null
                ) as Media
            }
        }.combine(distributor.dateFormatsFlow) { media, (defaultFormat, extendedFormat, weeklyFormat) ->
            mapMediaToItem(
                data = media,
                error = "",
                albumId = PRIVATE_FOLDER_ALBUM_ID,
                defaultDateFormat = defaultFormat,
                extendedDateFormat = extendedFormat,
                weeklyDateFormat = weeklyFormat
            )
        }.flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MediaState())
    }

    /**
     * Cloud
     */
    val cloudAvailable: Boolean get() = cloudRepository.hasConfiguredProviders

    val cloudMediaState: StateFlow<MediaState<Media>> by lazy {
        cloudRepository.getCachedMedia()
            .map { entities ->
                entities.asSequence()
                    .filterNot { it.trashed }
                    .map { it.toUriMedia() as Media }
                    .filter { allowedMedia.matchesType(it) }
                    .toList()
            }
            .combine(distributor.dateFormatsFlow) { media, (defaultFormat, extendedFormat, weeklyFormat) ->
                mapMediaToItem(
                    data = media,
                    error = "",
                    albumId = -1L,
                    defaultDateFormat = defaultFormat,
                    extendedDateFormat = extendedFormat,
                    weeklyDateFormat = weeklyFormat
                )
            }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MediaState())
    }

    /**
     * Vault
     */
    val vaultState: StateFlow<VaultState> = distributor.vaultsMediaFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VaultState())

    /**
     * Build an observable media state for [vault]'s encrypted contents. Callers must wrap this in
     * `remember(vault) { ... }` so switching vaults re-subscribes to the correct flow. (Avoid a
     * mutable-var + lazy here: Compose can't observe a plain var reassignment.)
     */
    fun createVaultMediaState(vault: Vault?): StateFlow<MediaState<Media>> =
        repository.getEncryptedMedia(vault)
            .map { result ->
                val data = (result.data ?: emptyList()).filter { allowedMedia.matchesType(it) }
                Resource.Success<List<Media>>(data)
            }
            .mapMedia(
                albumId = -1L,
                groupByMonth = false,
                withMonthHeader = false,
                updateDatabase = {},
                defaultDateFormat = defaultDateFormat.value,
                extendedDateFormat = extendedDateFormat.value,
                weeklyDateFormat = weeklyDateFormat.value
            ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MediaState())

    /**
     * Materialize the selected media into shareable content:// URIs that the calling app can read.
     * Local (MediaStore) and private-folder (SAF) items pass through instantly; vault items are
     * decrypted and cloud items downloaded to temp files exposed via FileProvider.
     */
    suspend fun materializeToShareableUris(
        media: List<Media>,
        onProgress: (done: Int, total: Int) -> Unit
    ): List<Uri> = withContext(Dispatchers.IO) {
        val total = media.size
        val result = ArrayList<Uri>(total)
        media.forEachIndexed { index, item ->
            try {
                val uri = if (item.isEncrypted) {
                    FileProvider.getUriForFile(
                        context,
                        BuildConfig.CONTENT_AUTHORITY,
                        createDecryptedTempFile(item, keychainHolder)
                    )
                } else {
                    context.resolveShareableUri(item)
                }
                result.add(uri)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            onProgress(index + 1, total)
        }
        result
    }

    private fun AllowedMedia.matchesType(media: Media): Boolean = when (this) {
        AllowedMedia.PHOTOS -> media.isImage
        AllowedMedia.VIDEOS -> media.isVideo
        AllowedMedia.BOTH -> true
    }

    private fun IgnoredAlbum.shouldIgnore(media: Media) =
        matchesMedia(media) && (hiddenInTimeline && albumId == -1L || hiddenInAlbums && albumId != -1L)

    companion object {
        /** How long a materialized share temp file is kept before the next picker launch sweeps it. */
        private const val SHARE_TEMP_TTL_MS = 5 * 60 * 1000L
    }
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