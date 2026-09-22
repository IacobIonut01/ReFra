/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.data.repository

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.datastore.preferences.core.Preferences
import androidx.exifinterface.media.ExifInterface
import androidx.room.withTransaction
import androidx.work.WorkManager
import com.dot.gallery.cloud.data.entity.CloudMediaEntity
import com.dot.gallery.cloud.data.entity.CloudServerConfigEntity
import com.dot.gallery.cloud.util.CloudMediaDownloader
import com.dot.gallery.core.Constants
import com.dot.gallery.core.presentation.components.util.hasMediaAccess
import com.dot.gallery.core.startup.StartupMediaCache
import com.dot.gallery.core.startup.StartupWorkGate
import com.dot.gallery.core.startup.startupLoadFlow
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.core.Resource
import com.dot.gallery.core.activeDataStore
import com.dot.gallery.core.decoder.format.ImageReencoder
import com.dot.gallery.core.metadata.MetadataRemovalMode
import com.dot.gallery.core.metadata.MetadataSaveMode
import com.dot.gallery.core.metadata.MetadataSanitizer
import com.dot.gallery.core.metadata.SanitizationCapability
import com.dot.gallery.core.metadata.SanitizationResult
import com.dot.gallery.core.util.MediaStoreBuckets
import com.dot.gallery.core.util.ext.captureDateMillis
import com.dot.gallery.core.util.ext.copyToCancellable
import com.dot.gallery.core.util.ext.isSupportedFormatForSavingAttributes
import com.dot.gallery.core.util.ext.isVerifiedMediaCopy
import com.dot.gallery.core.util.ext.mapAsResource
import com.dot.gallery.core.util.ext.mediaDateModified
import com.dot.gallery.core.util.ext.mediaSize
import com.dot.gallery.core.util.ext.restoreMediaTimestamp
import com.dot.gallery.core.util.ext.overrideImageEncoded
import com.dot.gallery.core.util.ext.renameMedia
import com.dot.gallery.core.util.ext.saveImage
import com.dot.gallery.core.util.ext.saveImageEncoded
import com.dot.gallery.core.util.ext.saveRawImage
import com.dot.gallery.core.util.ext.saveVideo
import com.dot.gallery.core.util.ext.saveVideoStream
import com.dot.gallery.core.util.ext.saveRawStream
import com.dot.gallery.core.util.ext.updateCaptureDate
import com.dot.gallery.core.util.ext.updateImageDescription
import com.dot.gallery.core.util.ext.updateMedia
import com.dot.gallery.core.util.ext.updateMediaExif
import com.dot.gallery.core.workers.copyMedia
import com.dot.gallery.core.workers.enqueueCaptureTimeIndex
import com.dot.gallery.core.smart.SmartScanScheduler
import com.dot.gallery.feature_node.data.data_source.CategoryWithMediaCount
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
import com.dot.gallery.feature_node.data.data_source.MediaCaptureTimeEntity
import com.dot.gallery.feature_node.data.data_source.applyTo
import com.dot.gallery.feature_node.data.data_source.toEntity
import com.dot.gallery.feature_node.data.data_source.SmartScanFeature
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.data.data_source.mediastore.MediaQuery
import com.dot.gallery.feature_node.data.data_source.mediastore.queries.AlbumsFlow
import com.dot.gallery.feature_node.data.data_source.mediastore.queries.MediaFlow
import com.dot.gallery.feature_node.data.data_source.mediastore.queries.MediaUriFlow
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.AlbumGroup
import com.dot.gallery.feature_node.domain.model.AlbumGroupMember
import com.dot.gallery.feature_node.domain.model.AlbumSection
import com.dot.gallery.feature_node.domain.model.AlbumSectionMember
import com.dot.gallery.feature_node.domain.model.Collection
import com.dot.gallery.feature_node.domain.model.CollectionMedia
import com.dot.gallery.feature_node.domain.model.CollectionWithCount
import com.dot.gallery.feature_node.domain.model.AlbumThumbnail
import com.dot.gallery.feature_node.domain.model.CaptureTimeOrigin
import com.dot.gallery.feature_node.domain.model.Category
import com.dot.gallery.feature_node.domain.model.IgnoredAlbum
import com.dot.gallery.feature_node.domain.model.ImageEmbedding
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Media.ClassifiedMedia
import com.dot.gallery.feature_node.domain.model.Media.EncryptedMedia
import com.dot.gallery.feature_node.domain.model.Media.UriMedia
import com.dot.gallery.feature_node.domain.model.MediaCategory
import com.dot.gallery.feature_node.domain.model.MediaMetadata
import com.dot.gallery.feature_node.domain.model.MediaVersion
import com.dot.gallery.feature_node.domain.model.metadataParsingPolicy
import com.dot.gallery.feature_node.domain.model.shouldIgnore
import com.dot.gallery.core.Settings
import com.dot.gallery.core.sandbox.IsolatedMetadataParser
import com.dot.gallery.feature_node.domain.model.LockedAlbum
import com.dot.gallery.feature_node.domain.model.MergedSubfolderAlbum
import com.dot.gallery.feature_node.domain.model.PinnedAlbum
import com.dot.gallery.feature_node.domain.model.ResolvedCaptureTime
import com.dot.gallery.feature_node.domain.model.TimelineSettings
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.model.retrieveExtraMediaMetadata
import com.dot.gallery.feature_node.domain.model.toMediaMetadata
import com.dot.gallery.feature_node.domain.util.isCloud
import com.dot.gallery.feature_node.domain.repository.CaptureDateEditCapability
import com.dot.gallery.feature_node.domain.repository.CaptureDateEditResult
import com.dot.gallery.feature_node.domain.repository.MediaMutationResult
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.domain.util.MediaOrder
import com.dot.gallery.feature_node.domain.util.OrderType
import com.dot.gallery.feature_node.domain.util.asUriMedia
import com.dot.gallery.feature_node.domain.util.compatibleBitmapFormat
import com.dot.gallery.feature_node.domain.util.compatibleMimeType
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isImage
import com.dot.gallery.feature_node.domain.util.isRawFile
import com.dot.gallery.feature_node.domain.util.isVideo
import com.dot.gallery.feature_node.domain.util.mediaStoreVolumeName
import com.dot.gallery.feature_node.domain.util.resolveMediaStoreVolume
import com.dot.gallery.feature_node.domain.util.migrate
import com.dot.gallery.feature_node.domain.util.toEncryptedMedia2
import com.dot.gallery.feature_node.presentation.picker.AllowedMedia
import com.dot.gallery.feature_node.presentation.picker.AllowedMedia.BOTH
import com.dot.gallery.feature_node.presentation.picker.AllowedMedia.PHOTOS
import com.dot.gallery.feature_node.presentation.picker.AllowedMedia.VIDEOS
import com.dot.gallery.feature_node.presentation.util.printDebug
import com.dot.gallery.feature_node.presentation.util.printError
import com.dot.gallery.feature_node.presentation.util.mediaStoreVersion
import com.dot.gallery.feature_node.presentation.util.printInfo
import com.dot.gallery.feature_node.presentation.util.printWarning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

internal fun shouldUsePerFileMetadataIsolation(mode: String, bulk: Boolean): Boolean = when (mode) {
    Settings.Security.METADATA_ISOLATION_PER_FILE -> true
    Settings.Security.METADATA_ISOLATION_HYBRID -> !bulk
    else -> false
}

internal fun captureDateEditCapability(
    editableLocalImage: Boolean,
    canWriteMetadata: Boolean,
    mimeType: String
): CaptureDateEditCapability = when {
    !editableLocalImage -> CaptureDateEditCapability.UNSUPPORTED
    canWriteMetadata -> CaptureDateEditCapability.DIRECT_WRITE
    mimeType.lowercase() in setOf("image/bmp", "image/x-bmp", "image/x-ms-bmp") ->
        CaptureDateEditCapability.SAFE_COPY
    else -> CaptureDateEditCapability.COPY_ONLY
}

internal fun canTrashOriginalAfterDatedCopy(capability: CaptureDateEditCapability): Boolean =
    capability == CaptureDateEditCapability.SAFE_COPY

internal fun combineCategoryThumbnails(
    local: List<UriMedia>,
    cloud: List<CloudMediaEntity>,
    activeConfigs: List<CloudServerConfigEntity>,
    blacklisted: List<IgnoredAlbum>,
    locked: List<LockedAlbum>,
    hasMediaAccess: Boolean
): List<UriMedia> {
    val lockedIds = locked.mapTo(HashSet()) { it.id }
    val activeConfigIds = activeConfigs.mapTo(HashSet()) { it.id }
    val candidates = (if (hasMediaAccess) local else emptyList()) +
        cloud
            .filter { it.serverConfigId in activeConfigIds }
            .map { it.toUriMedia() }
    return candidates.filter { media ->
        media.albumID !in lockedIds && blacklisted.none { it.shouldIgnore(media) }
    }
}

class MediaRepositoryImpl(
    private val context: Context,
    private val workManager: WorkManager,
    private val database: InternalDatabase,
    private val keychainHolder: KeychainHolder,
    private val geocoder: Geocoder?,
    private val isolatedParser: IsolatedMetadataParser,
    private val metadataSanitizer: MetadataSanitizer,
    private val smartScanScheduler: SmartScanScheduler,
    private val startupCache: StartupMediaCache,
    private val startupGate: StartupWorkGate
) : MediaRepository {

    private val contentResolver = context.contentResolver
    private val captureTimeIndexFlow: Flow<Map<Long, MediaCaptureTimeEntity>> = flow {
        emit(emptyMap())
        startupGate.awaitFirstContent()
        emitAll(
            database.getMediaCaptureTimeDao().observeAll()
                .map { entries -> entries.associateBy(MediaCaptureTimeEntity::mediaId) }
        )
    }.distinctUntilChanged()

    private fun Flow<List<UriMedia>>.withCaptureTimeIndex(): Flow<List<UriMedia>> =
        combine(this, captureTimeIndexFlow) { media, index ->
            media.map { item -> index[item.id]?.applyTo(item) ?: item }
        }

    private fun Resource<List<UriMedia>>.withCaptureTimeIndex(
        index: Map<Long, MediaCaptureTimeEntity>
    ): Resource<List<UriMedia>> {
        val enriched = data?.map { item -> index[item.id]?.applyTo(item) ?: item }
        return when (this) {
            is Resource.Success -> Resource.Success(enriched.orEmpty(), isPartial = isPartial)
            is Resource.Error -> Resource.Error(message.orEmpty(), enriched)
        }
    }

    /**
     * Whether on-demand metadata operations should use per-file isolation.
     * This is true for both hybrid and per-file modes.
     */
    private suspend fun shouldUsePerFileIsolation(bulk: Boolean = false): Boolean {
        val mode = Settings.Security.getMetadataIsolationMode(context)
            .firstOrNull() ?: Settings.Security.METADATA_ISOLATION_SHARED
        return shouldUsePerFileMetadataIsolation(mode, bulk)
    }

    private val updateDatabaseMutex = Mutex()
    override suspend fun updateInternalDatabase() {
        updateDatabaseMutex.withLock {
            delay(5000) // Delay to ensure the database is not updated too frequently
            val mediaVersion = context.mediaStoreVersion
            val mediaDao = database.getMediaDao()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                !mediaDao.isMediaVersionUpToDate(mediaVersion)
            ) {
                getCompleteMedia().firstOrNull()?.data?.let { media ->
                    database.withTransaction {
                        mediaDao.updateMedia(media)
                        mediaDao.setMediaVersion(MediaVersion(mediaVersion))
                        if (media.isNotEmpty()) {
                            database.getClassifierDao().deleteDeclassifiedImages(media.map { it.id })
                        }
                    }
                }
            }
            smartScanScheduler.automaticIfNeeded(SmartScanFeature.ALL_MASK)
        }
        //workManager.scheduleMediaMigrationCheck()
    }

    /**
     * TODO: Add media reordering
     */
    private fun sortTimeline(media: List<UriMedia>): List<UriMedia> =
        MediaOrder.Date(OrderType.Descending).sortMedia(media)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getMedia(): Flow<Resource<List<UriMedia>>> {
        val source = startupLoadFlow(
            readCache = { startupCache.readMedia()?.let(::sortTimeline) },
            boundedSource = {
                sortTimeline(
                    MediaFlow(
                        contentResolver = contentResolver,
                        buckedId = MediaStoreBuckets.MEDIA_STORE_BUCKET_TIMELINE.id,
                        skipBatching = true,
                        queryLimit = STARTUP_MEDIA_LIMIT
                    ).flowData().first()
                )
            },
            awaitFirstContent = startupGate::awaitFirstContent,
            currentStamp = startupCache::currentStamp,
            liveSource = MediaFlow(
                contentResolver = contentResolver,
                buckedId = MediaStoreBuckets.MEDIA_STORE_BUCKET_TIMELINE.id,
                skipBatching = true
            ).flowData()
                .onEach { workManager.enqueueCaptureTimeIndex() }
                .map(::sortTimeline),
            writeCache = startupCache::writeMedia,
            onLiveError = { printWarning("Startup media load failed (${it.javaClass.simpleName})") }
        )
        return combine(source, captureTimeIndexFlow) { resource, index ->
            when (val enriched = resource.withCaptureTimeIndex(index)) {
                is Resource.Success -> Resource.Success(
                    sortTimeline(enriched.data.orEmpty()),
                    isPartial = enriched.isPartial
                )
                is Resource.Error -> Resource.Error(
                    enriched.message.orEmpty(),
                    enriched.data?.let(::sortTimeline)
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun getCompleteMedia(): Flow<Resource<List<UriMedia>>> =
        MediaFlow(
            contentResolver = contentResolver,
            buckedId = MediaStoreBuckets.MEDIA_STORE_BUCKET_TIMELINE.id,
            skipBatching = true
        ).flowData().withCaptureTimeIndex().map {
            Resource.Success(MediaOrder.Date(OrderType.Descending).sortMedia(it))
        }.flowOn(Dispatchers.IO)

    override fun getMediaByType(allowedMedia: AllowedMedia): Flow<Resource<List<UriMedia>>> =
        MediaFlow(
            contentResolver = contentResolver,
            buckedId = when (allowedMedia) {
                PHOTOS -> MediaStoreBuckets.MEDIA_STORE_BUCKET_PHOTOS.id
                VIDEOS -> MediaStoreBuckets.MEDIA_STORE_BUCKET_VIDEOS.id
                BOTH -> MediaStoreBuckets.MEDIA_STORE_BUCKET_TIMELINE.id
            },
            mimeType = allowedMedia.toStringAny()
        ).flowData().withCaptureTimeIndex().map {
            Resource.Success(it)
        }.flowOn(Dispatchers.IO)

    override fun getFavorites(mediaOrder: MediaOrder): Flow<Resource<List<UriMedia>>> =
        MediaFlow(
            contentResolver = contentResolver,
            buckedId = MediaStoreBuckets.MEDIA_STORE_BUCKET_FAVORITES.id
        ).flowData().withCaptureTimeIndex().map {
            Resource.Success(it)
        }.flowOn(Dispatchers.IO)

    override fun getTrashed(): Flow<Resource<List<UriMedia>>> =
        MediaFlow(
            contentResolver = contentResolver,
            buckedId = MediaStoreBuckets.MEDIA_STORE_BUCKET_TRASH.id
        ).flowData().withCaptureTimeIndex().map { Resource.Success(it) }.flowOn(Dispatchers.IO)

    override fun getAlbums(mediaOrder: MediaOrder): Flow<Resource<List<Album>>> = startupLoadFlow(
        readCache = { startupCache.readAlbums() },
        boundedSource = null,
        awaitFirstContent = startupGate::awaitFirstContent,
        currentStamp = startupCache::currentStamp,
        liveSource = AlbumsFlow(context).flowData(),
        writeCache = startupCache::writeAlbums,
        onLiveError = { printWarning("Startup albums load failed (${it.javaClass.simpleName})") },
        errorMessage = "Failed to load albums"
    ).map { resource ->
        when (resource) {
            is Resource.Success -> Resource.Success(
                withPinnedAlbums(mediaOrder.sortAlbums(resource.data ?: emptyList()))
            )
            else -> resource
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun withPinnedAlbums(albums: List<Album>): List<Album> {
        val pinnedIds = database.getPinnedDao().getPinnedAlbumIds().toHashSet()
        return albums.map { album ->
            album.copy(isPinned = album.id in pinnedIds)
        }
    }

    override fun getCategoryThumbnailMedia(ids: List<Long>): Flow<List<UriMedia>> {
        if (ids.isEmpty()) return flowOf(emptyList())
        require(ids.size <= CATEGORY_THUMBNAIL_MAX_IDS) {
            "getCategoryThumbnailMedia supports at most $CATEGORY_THUMBNAIL_MAX_IDS ids"
        }
        val localIds = ids.filter { it >= 0 }
        val cloudIds = ids.filter { it < 0 }
        val localFlow: Flow<List<UriMedia>> =
            if (localIds.isEmpty() || !context.hasMediaAccess()) {
                flowOf(emptyList())
            } else {
                MediaFlow(
                    contentResolver = contentResolver,
                    buckedId = MediaStoreBuckets.MEDIA_STORE_BUCKET_TIMELINE.id,
                    skipBatching = true,
                    mediaIds = localIds.toSet()
                ).flowData().withCaptureTimeIndex()
            }
        val cloudFlow: Flow<List<CloudMediaEntity>> =
            if (cloudIds.isEmpty()) {
                flowOf(emptyList())
            } else {
                database.getCloudMediaDao().observeByGlobalMediaIds(cloudIds)
            }
        return combine(
            localFlow,
            cloudFlow,
            database.getCloudServerConfigDao().getActive(),
            getBlacklistedAlbums(),
            getLockedAlbums()
        ) { local, cloud, activeConfigs, blacklisted, locked ->
            combineCategoryThumbnails(
                local, cloud, activeConfigs, blacklisted, locked,
                hasMediaAccess = context.hasMediaAccess()
            )
        }.flowOn(Dispatchers.IO)
    }

    override fun getAlbum(albumId: Long): Flow<Resource<Album>> =
        AlbumsFlow(context).flowData().map {
            withContext(Dispatchers.IO) {
                val pinnedIds = database.getPinnedDao().getPinnedAlbumIds().toHashSet()
                val album = it.firstOrNull { it -> it.id == albumId }
                    ?.copy(isPinned = albumId in pinnedIds)
                    ?: return@withContext Resource.Error("Album not found")
                Resource.Success(album)
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun insertPinnedAlbum(pinnedAlbum: PinnedAlbum) =
        database.getPinnedDao().insertPinnedAlbum(pinnedAlbum)

    override suspend fun insertPinnedAlbums(pinnedAlbums: List<PinnedAlbum>) =
        database.getPinnedDao().insertPinnedAlbums(pinnedAlbums)

    override suspend fun removePinnedAlbum(pinnedAlbum: PinnedAlbum) =
        database.getPinnedDao().removePinnedAlbum(pinnedAlbum)

    override suspend fun removePinnedAlbums(albumIds: List<Long>) {
        albumIds.chunked(SQLITE_BIND_CHUNK_SIZE).forEach {
            database.getPinnedDao().removePinnedAlbums(it)
        }
    }

    override fun getPinnedAlbums(): Flow<List<PinnedAlbum>> =
        database.getPinnedDao().getPinnedAlbums()

    override suspend fun insertLockedAlbum(lockedAlbum: LockedAlbum) {
        database.getLockedAlbumDao().insertLockedAlbum(lockedAlbum)
        database.getMediaDao().invalidateMediaVersion()
        smartScanScheduler.automatic(SmartScanFeature.ALL_MASK)
    }

    override suspend fun insertLockedAlbums(lockedAlbums: List<LockedAlbum>) {
        database.getLockedAlbumDao().insertLockedAlbums(lockedAlbums)
        database.getMediaDao().invalidateMediaVersion()
        smartScanScheduler.automatic(SmartScanFeature.ALL_MASK)
    }

    override suspend fun removeLockedAlbum(lockedAlbum: LockedAlbum) {
        database.getLockedAlbumDao().removeLockedAlbum(lockedAlbum)
        database.getMediaDao().invalidateMediaVersion()
        smartScanScheduler.automatic(SmartScanFeature.ALL_MASK)
    }

    override suspend fun removeLockedAlbums(albumIds: List<Long>) {
        albumIds.chunked(SQLITE_BIND_CHUNK_SIZE).forEach {
            database.getLockedAlbumDao().removeLockedAlbums(it)
        }
        database.getMediaDao().invalidateMediaVersion()
        smartScanScheduler.automatic(SmartScanFeature.ALL_MASK)
    }

    override fun getLockedAlbums(): Flow<List<LockedAlbum>> =
        database.getLockedAlbumDao().getLockedAlbums()

    override suspend fun addBlacklistedAlbum(ignoredAlbum: IgnoredAlbum) {
        database.getBlacklistDao().addBlacklistedAlbum(ignoredAlbum)
        database.getMediaDao().invalidateMediaVersion()
        smartScanScheduler.automatic(SmartScanFeature.ALL_MASK)
    }

    override suspend fun removeBlacklistedAlbum(ignoredAlbum: IgnoredAlbum) {
        database.getBlacklistDao().removeBlacklistedAlbum(ignoredAlbum)
        database.getMediaDao().invalidateMediaVersion()
        smartScanScheduler.automatic(SmartScanFeature.ALL_MASK)
    }

    override fun getBlacklistedAlbums(): Flow<List<IgnoredAlbum>> =
        database.getBlacklistDao().getBlacklistedAlbums()

    override suspend fun getBlacklistedAlbumsAsync(): List<IgnoredAlbum> =
        database.getBlacklistDao().getBlacklistedAlbumsAsync()

    override fun getMediaByAlbumId(albumId: Long, skipBatching: Boolean): Flow<Resource<List<UriMedia>>> =
        getMediaByAlbumIds(setOf(albumId), skipBatching)

    override fun getMediaByAlbumIds(
        albumIds: Set<Long>,
        skipBatching: Boolean
    ): Flow<Resource<List<UriMedia>>> = MediaFlow(
        contentResolver = contentResolver,
        buckedId = albumIds.firstOrNull() ?: MediaStoreBuckets.MEDIA_STORE_BUCKET_PLACEHOLDER.id,
        skipBatching = skipBatching,
        bucketIds = albumIds
    ).flowData().withCaptureTimeIndex().mapAsResource()

    override fun getMediaByAlbumIdWithType(
        albumId: Long,
        allowedMedia: AllowedMedia
    ): Flow<Resource<List<UriMedia>>> =
        MediaFlow(
            contentResolver = contentResolver,
            buckedId = albumId,
            mimeType = allowedMedia.toStringAny()
        ).flowData().withCaptureTimeIndex().mapAsResource()

    override fun getAlbumsWithType(allowedMedia: AllowedMedia): Flow<Resource<List<Album>>> =
        AlbumsFlow(
            context = context,
            mimeType = allowedMedia.toStringAny()
        ).flowData().mapAsResource()

    override fun getMediaListByUris(
        listOfUris: List<Uri>,
        reviewMode: Boolean,
        onlyMatching: Boolean
    ): Flow<Resource<List<UriMedia>>> =
        MediaUriFlow(
            contentResolver = contentResolver,
            uris = listOfUris,
            onlyMatchingUris = onlyMatching
        ).flowData().withCaptureTimeIndex()
            .mapAsResource(errorOnEmpty = true, errorMessage = "Media could not be opened")

    private suspend fun <T : Media> mutateMediaDirectly(
        mediaList: List<T>,
        operation: String,
        mutation: (T) -> Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        mediaList.map { media ->
            runCatching { mutation(media) }
                .onFailure {
                    printWarning("Failed to $operation media ${media.id}: ${it.message}")
                }
                .getOrDefault(false)
        }.all { it }
    }

    private fun <T : Media> List<T>.hasFilesCollectionItems(): Boolean =
        any { MediaQuery.isFilesCollectionUri(it.getUri()) }

    private fun includeTrashedQueryArgs() = Bundle().apply {
        putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
    }

    override suspend fun <T : Media> toggleFavorite(
        result: ActivityResultLauncher<IntentSenderRequest>,
        mediaList: List<T>,
        favorite: Boolean
    ) {
        if (!SdkCompat.supportsMediaStoreRequests) {
            // Favorites not supported on API 29
            return
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_FAVORITE, if (favorite) 1 else 0)
        }
        if (SdkCompat.hasFullFileAccess) {
            mutateMediaDirectly(mediaList, "favorite") {
                contentResolver.update(it.getUri(), values, includeTrashedQueryArgs()) > 0
            }
            return
        }
        if (mediaList.hasFilesCollectionItems()) {
            printWarning("Cannot favorite Files collection items without all-files access")
            return
        }
        val intentSender = try {
            MediaStore.createFavoriteRequest(
                contentResolver,
                mediaList.map { it.getUri() },
                favorite
            ).intentSender
        } catch (e: IllegalArgumentException) {
            printWarning("Failed to create favorite request: ${e.message}")
            return
        }
        val senderRequest: IntentSenderRequest = IntentSenderRequest.Builder(intentSender)
            .setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION, 0)
            .build()
        withContext(Dispatchers.Main.immediate) {
            result.launch(senderRequest)
        }
    }

    override suspend fun <T : Media> trashMedia(
        result: ActivityResultLauncher<IntentSenderRequest>,
        mediaList: List<T>,
        trash: Boolean
    ): MediaMutationResult {
        if (!SdkCompat.supportsMediaStoreRequests) {
            // Trash not supported on API 29
            return MediaMutationResult.FAILED
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_TRASHED, if (trash) 1 else 0)
        }
        if (SdkCompat.hasFullFileAccess) {
            val allSucceeded = mutateMediaDirectly(
                mediaList,
                if (trash) "trash" else "restore"
            ) {
                contentResolver.update(it.getUri(), values, includeTrashedQueryArgs()) > 0
            }
            return if (allSucceeded) MediaMutationResult.COMPLETED else MediaMutationResult.FAILED
        }
        if (mediaList.hasFilesCollectionItems()) {
            printWarning("Cannot trash Files collection items without all-files access")
            return MediaMutationResult.FAILED
        }
        val intentSender = try {
            MediaStore.createTrashRequest(
                contentResolver,
                mediaList.map { it.getUri() },
                trash
            ).intentSender
        } catch (e: IllegalArgumentException) {
            printWarning("Failed to create trash request: ${e.message}")
            return MediaMutationResult.FAILED
        }
        val senderRequest: IntentSenderRequest = IntentSenderRequest.Builder(intentSender)
            .setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION, 0)
            .build()
        withContext(Dispatchers.Main.immediate) {
            result.launch(senderRequest)
        }
        return MediaMutationResult.REQUEST_LAUNCHED
    }

    override suspend fun <T : Media> deleteMedia(
        result: ActivityResultLauncher<IntentSenderRequest>,
        mediaList: List<T>
    ): MediaMutationResult {
        if (!SdkCompat.supportsMediaStoreRequests) {
            // On API 29, delete directly via ContentResolver
            // requestLegacyExternalStorage grants full write access
            val allSucceeded = mutateMediaDirectly(mediaList, "delete") {
                contentResolver.delete(it.getUri(), null, null) > 0
            }
            return if (allSucceeded) MediaMutationResult.COMPLETED else MediaMutationResult.FAILED
        }
        if (SdkCompat.hasFullFileAccess) {
            val allSucceeded = mutateMediaDirectly(mediaList, "delete") {
                contentResolver.delete(it.getUri(), includeTrashedQueryArgs()) > 0
            }
            return if (allSucceeded) MediaMutationResult.COMPLETED else MediaMutationResult.FAILED
        }
        if (mediaList.hasFilesCollectionItems()) {
            printWarning("Cannot delete Files collection items without all-files access")
            return MediaMutationResult.FAILED
        }
        return try {
            val intentSender = MediaStore.createDeleteRequest(
                contentResolver,
                mediaList.map { it.getUri() }
            ).intentSender
            val senderRequest = IntentSenderRequest.Builder(intentSender)
                .setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION, 0)
                .build()
            withContext(Dispatchers.Main.immediate) {
                result.launch(senderRequest)
            }
            MediaMutationResult.REQUEST_LAUNCHED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            printWarning("Failed to create or launch delete request: ${e.message}")
            MediaMutationResult.FAILED
        }
    }

    override suspend fun <T : Media> copyMedia(
        from: T,
        path: String
    ) {
        workManager.copyMedia(
            from = from as UriMedia,
            path = path,
        )
    }

    override suspend fun <T : Media> copyMedia(vararg sets: Pair<T, String>) {
        workManager.copyMedia(*sets)
    }

    override suspend fun <T : Media> renameMedia(
        media: T,
        newName: String
    ): Boolean = context.renameMedia(
        media = media,
        newName = newName
    )

    override suspend fun <T : Media> moveMedia(
        media: T,
        newPath: String
    ): Boolean {
        val (destVolume, destRelPath) = resolveMediaStoreVolume(newPath)
        val sourceVolume = media.mediaStoreVolumeName

        if (destVolume == sourceVolume) {
            return context.updateMedia(
                media = media,
                contentValues = relativePath(destRelPath)
            )
        }

        return crossVolumeMove(media, destVolume, destRelPath)
    }

    private suspend fun <T : Media> crossVolumeMove(
        media: T,
        destVolume: String,
        destRelPath: String
    ): Boolean = withContext(Dispatchers.IO) {
        val copiedUri = copyMediaTo(media, destVolume, destRelPath)
            ?: return@withContext false
        try {
            currentCoroutineContext().ensureActive()
            if (contentResolver.delete(media.getUri(), null, null) <= 0) {
                runCatching { contentResolver.delete(copiedUri, null, null) }
                false
            } else {
                true
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                runCatching { contentResolver.delete(copiedUri, null, null) }
            }
            throw e
        } catch (e: Exception) {
            printWarning("Cross-volume move failed: ${e.message}")
            runCatching { contentResolver.delete(copiedUri, null, null) }
            false
        }
    }

    /**
     * Writes a copy of [media] into [destRelPath] on [destVolume] and returns its uri, or null
     * when the copy could not be completed. Nothing is written on failure.
     */
    private suspend fun <T : Media> copyMediaTo(
        media: T,
        destVolume: String,
        destRelPath: String
    ): Uri? = withContext(Dispatchers.IO) {
        val cr = contentResolver
        var targetUri: Uri? = null
        var committed = false
        try {
            val srcUri = media.getUri()
            val mediaType = if (media.isCloud) media.mimeType else cr.getType(srcUri)
                ?: return@withContext null
            val isVideo = mediaType.startsWith("video")
            val sourceDateModified = if (media.isCloud) media.timestamp else cr.mediaDateModified(srcUri)
            val sourceSize = if (media.isCloud) media.size else cr.mediaSize(srcUri)

            val insertedUri = cr.insert(
                if (isVideo) MediaStore.Video.Media.getContentUri(destVolume)
                else MediaStore.Images.Media.getContentUri(destVolume),
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, media.label)
                    put(MediaStore.MediaColumns.MIME_TYPE, mediaType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, destRelPath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            ) ?: return@withContext null
            targetUri = insertedUri

            val sourceInput = if (media.isCloud) {
                CloudMediaDownloader.downloadCloudMediaExact(srcUri)
            } else {
                cr.openInputStream(srcUri)
            }
            val copiedBytes = sourceInput?.use { input ->
                cr.openOutputStream(insertedUri)?.use { output ->
                    input.copyToCancellable(output)
                }
            } ?: throw IOException("Unable to open media streams")
            val targetSize = cr.mediaSize(insertedUri)
            if (!isVerifiedMediaCopy(sourceSize, copiedBytes, targetSize)) {
                throw IOException("Copied media size verification failed")
            }
            currentCoroutineContext().ensureActive()
            if (cr.update(
                    insertedUri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null
                ) <= 0
            ) {
                throw IOException("Unable to publish copied media")
            }
            // The copy belongs where the source was in the timeline, not at today's date.
            context.restoreMediaTimestamp(insertedUri, mediaType, sourceDateModified)
            committed = true
            insertedUri
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            printWarning("Copy to $destRelPath failed: ${e.message}")
            null
        } finally {
            if (!committed) {
                targetUri?.let { uri ->
                    runCatching { cr.delete(uri, null, null) }
                        .onFailure { error -> printWarning("Failed to discard partial copy: ${error.message}") }
                }
            }
        }
    }

    override suspend fun <T : Media> copyMediaForMove(
        mediaList: List<T>,
        newPath: String,
        onProgress: suspend (Float) -> Unit
    ): List<Uri> = withContext(Dispatchers.IO) {
        val (destVolume, destRelPath) = resolveMediaStoreVolume(newPath)
        val copies = mutableListOf<Uri>()
        try {
            mediaList.forEachIndexed { index, media ->
                val copiedUri = copyMediaTo(media, destVolume, destRelPath)
                if (copiedUri == null) {
                    // Never leave half a move behind: the originals are still untouched at this
                    // point, so drop the copies we own and report the failure.
                    discardMediaCopies(copies)
                    return@withContext emptyList()
                }
                copies += copiedUri
                onProgress((index + 1).toFloat() / mediaList.size)
            }
        } catch (e: CancellationException) {
            // Leaving the sheet mid-copy cancels this scope; clean up before giving up.
            withContext(NonCancellable) { discardMediaCopies(copies) }
            throw e
        } catch (e: Exception) {
            withContext(NonCancellable) { discardMediaCopies(copies) }
            throw e
        }
        copies
    }

    override suspend fun discardMediaCopies(uris: List<Uri>) = withContext(Dispatchers.IO) {
        uris.forEach {
            runCatching { contentResolver.delete(it, null, null) }
                .onFailure { error -> printWarning("Failed to discard copy: ${error.message}") }
        }
    }

    override suspend fun probeMetadataSanitization(media: Media): SanitizationCapability =
        metadataSanitizer.probe(media)

    override suspend fun sanitizeMediaMetadata(
        media: Media,
        mode: MetadataRemovalMode,
        saveMode: MetadataSaveMode
    ): SanitizationResult = metadataSanitizer.sanitize(media, mode, saveMode)

    override suspend fun refreshMetadataFor(media: Media) {
        database.getMetadataDao().deleteForMedia(media.id)
        context.retrieveExtraMediaMetadata(
            isolatedParser,
            geocoder,
            media,
            shouldUsePerFileIsolation()
        )?.let(database.getMetadataDao()::addMetadata)
    }

    override suspend fun probeCaptureDateEdit(media: Media): CaptureDateEditCapability =
        withContext(Dispatchers.IO) {
            val local = media as? UriMedia ?: return@withContext CaptureDateEditCapability.UNSUPPORTED
            val editableLocalImage = local.isImage &&
                local.uri.scheme == ContentResolver.SCHEME_CONTENT &&
                local.uri.authority == MediaStore.AUTHORITY
            if (!editableLocalImage) return@withContext CaptureDateEditCapability.UNSUPPORTED
            val canWrite = runCatching {
                contentResolver.openFileDescriptor(local.uri, "r")?.use {
                    ExifInterface(it.fileDescriptor).isSupportedFormatForSavingAttributes
                } == true
            }.getOrDefault(false)
            captureDateEditCapability(
                editableLocalImage = editableLocalImage,
                canWriteMetadata = canWrite,
                mimeType = local.mimeType
            )
        }

    override suspend fun updateMediaCaptureDate(
        media: Media,
        timestampMillis: Long
    ): CaptureDateEditResult = withContext(Dispatchers.IO) {
        val local = media as? UriMedia
            ?: return@withContext CaptureDateEditResult.Failed("Capture date editing is unavailable")
        val capability = probeCaptureDateEdit(local)
        if (capability != CaptureDateEditCapability.DIRECT_WRITE) {
            return@withContext CaptureDateEditResult.NeedsCopy(capability)
        }
        val updated = context.updateMediaExif(
            media = local,
            action = { updateCaptureDate(timestampMillis) },
            postAction = { updatedMedia ->
                val current = updatedMedia.copy(
                    timestamp = contentResolver.mediaDateModified(updatedMedia.uri)
                        .takeIf { it != 0L } ?: updatedMedia.timestamp,
                    size = contentResolver.mediaSize(updatedMedia.uri)
                        .takeIf { it != 0L } ?: updatedMedia.size
                )
                database.getMediaCaptureTimeDao().upsertAll(
                    listOf(
                        ResolvedCaptureTime(
                            timestampMillis = timestampMillis,
                            origin = CaptureTimeOrigin.EMBEDDED_IMAGE
                        ).toEntity(current, System.currentTimeMillis())
                    )
                )
                context.retrieveExtraMediaMetadata(
                    isolatedParser,
                    geocoder,
                    current,
                    shouldUsePerFileIsolation()
                )?.let(database.getMetadataDao()::addMetadata)
            }
        )
        if (!updated) return@withContext CaptureDateEditResult.Failed("Unable to write capture date")
        val verified = runCatching {
            contentResolver.openFileDescriptor(local.uri, "r")?.use {
                ExifInterface(it.fileDescriptor).captureDateMillis()
            }?.let { kotlin.math.abs(it - timestampMillis) < 1_000L } == true
        }.getOrDefault(false)
        if (!verified) return@withContext CaptureDateEditResult.Failed("Capture date verification failed")
        workManager.enqueueCaptureTimeIndex()
        CaptureDateEditResult.Updated
    }

    override suspend fun createDatedCopy(
        media: Media,
        timestampMillis: Long
    ): CaptureDateEditResult = withContext(Dispatchers.IO) {
        val local = media as? UriMedia
            ?: return@withContext CaptureDateEditResult.Failed("Dated copy is unavailable")
        val capability = probeCaptureDateEdit(local)
        if (capability == CaptureDateEditCapability.DIRECT_WRITE ||
            capability == CaptureDateEditCapability.UNSUPPORTED
        ) return@withContext CaptureDateEditResult.Failed("Dated copy is unavailable")

        val bitmap = runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, local.uri)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.getOrNull() ?: return@withContext CaptureDateEditResult.Failed("Unable to decode a dated copy")
        val baseName = local.label.substringBeforeLast('.', local.label)
        val targetUri = try {
            contentResolver.saveImageEncoded(
                bitmap = bitmap,
                writeFormat = ImageReencoder.ImageWriteFormat.PNG,
                config = ImageReencoder.ReencodeConfig(),
                mimeType = "image/png",
                relativePath = local.relativePath,
                displayName = "${baseName}_dated_${System.currentTimeMillis()}.png"
            )
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        } ?: return@withContext CaptureDateEditResult.Failed("Unable to create a dated copy")

        val written = runCatching {
            contentResolver.openFileDescriptor(targetUri, "rw")?.use {
                ExifInterface(it.fileDescriptor).apply {
                    updateCaptureDate(timestampMillis)
                    saveAttributes()
                }
            } ?: error("Dated copy descriptor unavailable")
            contentResolver.openFileDescriptor(targetUri, "r")?.use {
                ExifInterface(it.fileDescriptor).captureDateMillis()
            }?.let { kotlin.math.abs(it - timestampMillis) < 1_000L } == true
        }.getOrDefault(false)
        if (!written) {
            runCatching { contentResolver.delete(targetUri, null, null) }
            return@withContext CaptureDateEditResult.Failed("Dated copy verification failed")
        }
        contentResolver.notifyChange(targetUri, null)
        workManager.enqueueCaptureTimeIndex()
        CaptureDateEditResult.CopyCreated(
            uri = targetUri,
            canTrashOriginal = canTrashOriginalAfterDatedCopy(capability)
        )
    }

    override suspend fun <T : Media> updateMediaDescription(
        media: T,
        description: String
    ): Boolean {
        return if (media.isVideo) {
            // For videos, store description in the local database only
            // Video files don't support EXIF metadata like images do
            withContext(Dispatchers.IO) {
                runCatching {
                    database.getMetadataDao().upsertImageDescription(
                        mediaId = media.id,
                        description = description,
                        imageWidth = 0,
                        imageHeight = 0
                    )
                    if (Settings.Album.updateModifiedDate(context).firstOrNull() == true) {
                        context.restoreMediaTimestamp(
                            media.getUri(),
                            media.mimeType,
                            System.currentTimeMillis() / 1000L
                        )
                    }
                    true
                }.getOrElse {
                    printWarning("Failed to update video description in database: ${it.message}")
                    false
                }
            }
        } else {
            // For images, update EXIF metadata in the file
            context.updateMediaExif(
                media = media,
                action = { updateImageDescription(description) },
                postAction = {
                    context.retrieveExtraMediaMetadata(isolatedParser, geocoder, it, shouldUsePerFileIsolation())?.let { metadata ->
                        database.getMetadataDao().addMetadata(metadata)
                    }
                }
            )
        }
    }

    override suspend fun saveImage(
        bitmap: Bitmap,
        writeFormat: ImageReencoder.ImageWriteFormat,
        config: ImageReencoder.ReencodeConfig,
        mimeType: String,
        relativePath: String,
        displayName: String
    ) = contentResolver.saveImageEncoded(bitmap, writeFormat, config, mimeType, relativePath, displayName)

    override suspend fun overrideImage(
        uri: Uri,
        bitmap: Bitmap,
        writeFormat: ImageReencoder.ImageWriteFormat,
        config: ImageReencoder.ReencodeConfig,
        mimeType: String,
        relativePath: String,
        displayName: String
    ) = contentResolver.overrideImageEncoded(uri, bitmap, writeFormat, config)

    override fun getVaults(): Flow<Resource<List<Vault>>> = database
        .getVaultDao()
        .getVaults().map { vaults ->
            with(keychainHolder) {
                val newVaults = vaults.mapNotNull { vault ->
                    if (vaultFolder(vault).exists()) vault else {
                        printWarning("Vault ${vault.uuid} does not exist. It will be deleted from the database.")
                        database.getVaultDao().deleteVault(vault)
                        null
                    }
                }
                Resource.Success(newVaults)
            }
        }

    override suspend fun createVault(
        vault: Vault,
        transferable: Boolean,
        onSuccess: () -> Unit,
        onFailed: (reason: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        keychainHolder.writeVaultInfo(
            vault = vault,
            transferable = transferable,
            onSuccess = {
                launch(Dispatchers.IO) {
                    database.getVaultDao().insertVault(vault)
                    onSuccess()
                }
            },
            onFailed = onFailed
        )
    }

    override suspend fun deleteVault(
        vault: Vault,
        onSuccess: () -> Unit,
        onFailed: (reason: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        keychainHolder.deleteVault(
            vault = vault,
            onSuccess = {
                launch(Dispatchers.IO) {
                    database.getVaultDao().deleteVault(vault)
                    onSuccess()
                }
            },
            onFailed = onFailed
        )
    }

    override fun getEncryptedMedia(vault: Vault?): Flow<Resource<List<UriMedia>>> =
        database.getVaultDao().getMediaFromVault(vault?.uuid).map { mediaList ->
            with(keychainHolder) {
                val newMedia = mediaList.mapNotNull { media ->
                    try {
                        val encryptedFile = vault!!.mediaFile(media.id)
                        if (encryptedFile.exists()) {
                            media.asUriMedia(Uri.fromFile(encryptedFile))
                        } else {
                            printWarning("Encrypted Media ${media.id} under ${vault.uuid} does not exist. It will be deleted from the database.")
                            database.getVaultDao().deleteMediaFromVault(media)
                            null
                        }
                    } catch (e: Throwable) {
                        e.printStackTrace()
                        null
                    }
                }.sortedByDescending { it.timestamp }
                Resource.Success(newMedia)
            }
        }

    override suspend fun <T : Media> addMedia(vault: Vault, media: T): Boolean =
        withContext(Dispatchers.IO) {
            with(keychainHolder) {
                keychainHolder.checkVaultFolder(vault)
                // Skip duplicate: if this media ID already exists in this vault, treat as success
                if (database.getVaultDao().mediaExistsInVault(vault.uuid, media.id)) {
                    printInfo("Skipping duplicate: ${media.label} already in vault ${vault.name}")
                    return@withContext true
                }
                val output = vault.mediaFile(media.id).apply { if (exists()) delete() }
                // Ensure vault uses portable format for streaming encryption
                if (!isTransferable(vault)) {
                    writeVaultInfo(vault, transferable = true)
                }
                return@withContext try {
                    val inputStream = context.contentResolver.openInputStream(media.getUri())
                        ?: return@withContext false
                    inputStream.use { input ->
                        encryptPortableStream(vault, input, output)
                    }
                    output.setLastModified(System.currentTimeMillis())
                    database.getVaultDao().addMediaToVault(media.toEncryptedMedia2(vault.uuid))
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    printError("Failed to add file: ${media.label}")
                    output.delete()
                    false
                }
            }
        }

    override suspend fun <T : Media> restoreMedia(vault: Vault, media: T): Boolean =
        withContext(Dispatchers.IO) {
            with(keychainHolder) {
                checkVaultFolder(vault)
                return@withContext try {
                    val encFile = vault.mediaFile(media.id)
                    val restored: Boolean
                    if (isPortableFile(encFile)) {
                        // Portable format: stream-decrypt directly to MediaStore
                        restored = if (media.isRawFile) {
                            contentResolver.saveRawStream(
                                writeBlock = { out -> decryptPortableStream(vault, encFile, out) },
                                displayName = media.label,
                                mimeType = media.mimeType,
                                relativePath = Environment.DIRECTORY_PICTURES + "/Restored"
                            ) != null
                        } else if (media.isImage) {
                            // Images need bitmap decode/re-encode for format compatibility
                            contentResolver.saveRawStream(
                                writeBlock = { out -> decryptPortableStream(vault, encFile, out) },
                                displayName = media.label,
                                mimeType = media.mimeType,
                                relativePath = Environment.DIRECTORY_PICTURES + "/Restored"
                            ) != null
                        } else {
                            contentResolver.saveVideoStream(
                                writeBlock = { out -> decryptPortableStream(vault, encFile, out) },
                                displayName = media.label,
                                mimeType = media.compatibleMimeType(),
                                relativePath = Environment.DIRECTORY_MOVIES + "/Restored"
                            ) != null
                        }
                    } else {
                        // Legacy format: in-memory decryption (only for old small files)
                        val encryptedMedia = encFile.decryptKotlin<EncryptedMedia>()
                        restored = if (media.isRawFile) {
                            contentResolver.saveRawImage(
                                data = encryptedMedia.bytes,
                                displayName = media.label,
                                mimeType = media.mimeType,
                                relativePath = Environment.DIRECTORY_PICTURES + "/Restored"
                            ) != null
                        } else if (media.isImage) {
                            contentResolver.saveImage(
                                bitmap = BitmapFactory.decodeByteArray(
                                    encryptedMedia.bytes,
                                    0,
                                    encryptedMedia.bytes.size
                                ),
                                displayName = media.label,
                                mimeType = media.compatibleMimeType(),
                                format = media.compatibleBitmapFormat(),
                                relativePath = Environment.DIRECTORY_PICTURES + "/Restored"
                            ) != null
                        } else {
                            contentResolver.saveVideo(
                                data = encryptedMedia.bytes,
                                displayName = media.label,
                                mimeType = media.compatibleMimeType(),
                                relativePath = Environment.DIRECTORY_MOVIES + "/Restored"
                            ) != null
                        }
                    }
                    val deleted = if (restored) encFile.delete() else false
                    if (deleted) {
                        database.getVaultDao()
                            .deleteMediaFromVault(vault.uuid, media.id)
                    }
                    restored && deleted
                } catch (e: Exception) {
                    e.printStackTrace()
                    printError("Failed to restore file: ${media.label}")
                    false
                }
            }
        }

    override suspend fun <T : Media> transferMedia(
        sourceVault: Vault,
        targetVault: Vault,
        media: T,
        copy: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        with(keychainHolder) {
            checkVaultFolder(sourceVault)
            checkVaultFolder(targetVault)
            if (!isTransferable(targetVault)) {
                writeVaultInfo(targetVault, transferable = true)
            }
            return@withContext try {
                val sourceFile = sourceVault.mediaFile(media.id)
                if (!sourceFile.exists()) {
                    printError("Transfer failed: source file does not exist for ${media.label} (id=${media.id})")
                    return@withContext false
                }
                val targetFile = targetVault.mediaFile(media.id).apply { if (exists()) delete() }
                // Decrypt from source, re-encrypt into target
                val buffer = ByteArrayOutputStream()
                if (isPortableFile(sourceFile)) {
                    decryptPortableStream(sourceVault, sourceFile, buffer)
                } else {
                    val legacy = sourceFile.decryptKotlin<EncryptedMedia>()
                    buffer.write(legacy.bytes)
                }
                val decryptedBytes = buffer.toByteArray()
                if (decryptedBytes.isEmpty()) {
                    printError("Transfer failed: decrypted data is empty for ${media.label}")
                    return@withContext false
                }
                ByteArrayInputStream(decryptedBytes).use { input ->
                    encryptPortableStream(targetVault, input, targetFile)
                }
                targetFile.setLastModified(System.currentTimeMillis())
                database.getVaultDao().addMediaToVault(media.toEncryptedMedia2(targetVault.uuid))
                if (!copy) {
                    sourceFile.delete()
                    database.getVaultDao().deleteMediaFromVault(sourceVault.uuid, media.id)
                }
                printInfo("Transferred ${media.label} from ${sourceVault.name} to ${targetVault.name} (copy=$copy)")
                true
            } catch (e: Exception) {
                e.printStackTrace()
                printError("Failed to transfer file: ${media.label}: ${e.message}")
                false
            }
        }
    }

    override suspend fun <T : Media> deleteEncryptedMedia(vault: Vault, media: T): Boolean =
        withContext(Dispatchers.IO) {
            with(keychainHolder) {
                checkVaultFolder(vault)
                return@withContext try {
                    val deleted = vault.mediaFile(media.id).delete()
                    if (deleted) {
                        database.getVaultDao().deleteMediaFromVault(vault.uuid, media.id)
                    }
                    deleted
                } catch (e: Exception) {
                    e.printStackTrace()
                    printError("Failed to delete file: ${media.label}")
                    false
                }
            }
        }

    override suspend fun deleteAllEncryptedMedia(
        vault: Vault,
        onSuccess: () -> Unit,
        onFailed: (failedFiles: List<File>) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        with(keychainHolder) {
            checkVaultFolder(vault)
            val failedFiles = mutableListOf<File>()
            val files = vaultFolder(vault).listFiles()
            files?.forEach { file ->
                try {
                    val deleted = file.delete()
                    if (deleted) {
                        database.getVaultDao()
                            .deleteMediaFromVault(vault.uuid, file.nameWithoutExtension.toLong())
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    printError("Failed to delete file: ${file.name}")
                    failedFiles.add(file)
                }
            }
            if (failedFiles.isEmpty()) {
                onSuccess()
                true
            } else {
                onFailed(failedFiles)
                false
            }
        }
    }


    override suspend fun getUnmigratedVaultMediaSize(): Int {
        return withContext(Dispatchers.IO) {
            var size = 0
            with(keychainHolder) {
                val uuidRegex =
                    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$".toRegex()
                val vaults =
                    filesDir.listFiles { it.isDirectory && it.nameWithoutExtension.matches(uuidRegex) }
                vaults?.forEach { vaultFolder ->
                    (vaultFolder.listFiles()?.filter { it.name.endsWith("enc") }
                        ?: emptyList()).map { file ->
                        try {
                            file.decryptKotlin<EncryptedMedia>()
                        } catch (_: Throwable) {
                            printWarning("Un-migrated media found: ${file.nameWithoutExtension}")
                            size++
                        }
                    }
                }
            }
            size
        }
    }

    override suspend fun importPortableVault(
        vault: Vault,
        base64Key: String,
        force: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        keychainHolder.importPortableVault(vault, base64Key, force).also { success ->
            if (success) {
                // Ensure DB entry exists
                if (database.getVaultDao().getVault(vault.uuid) == null) {
                    database.getVaultDao().insertVault(vault)
                }
            }
        }
    }

    override suspend fun migrateVaultToPortable(
        vault: Vault,
        onProgress: (current: Int, total: Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        keychainHolder.migrateVaultToPortable(vault, onProgress)
    }

    override suspend fun migrateVault() {
        /*withContext(Dispatchers.IO) {
            printInfo("Vault Migration started")
            val databaseStoredVaults = database.getVaultDao().getVaults().firstOrNull()
            val databaseStoredEncryptedMedia = database.getVaultDao().getAllMedia().firstOrNull()
            printInfo("Database stored vaults: ${databaseStoredVaults?.size}")
            printInfo("Database stored encrypted media: ${databaseStoredEncryptedMedia?.size}")

            val keychainStoredVaults = with(keychainHolder) {
                filesDir.listFiles()
                    ?.filter { it.isDirectory && File(it, VAULT_INFO_FILE_NAME).exists() }
                    ?.mapNotNull {
                        val vaultInfo = File(it, VAULT_INFO_FILE_NAME)
                        try {
                            vaultInfo.decrypt<Vault>()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            printError("Failed to decrypt file: ${vaultInfo.name}.")
                            null
                        }
                    }
                    ?: emptyList()
            }
            printInfo("Keychain stored vaults: ${keychainStoredVaults.size}")

            keychainStoredVaults.forEach {
                if (databaseStoredVaults?.find { vault -> vault.uuid == it.uuid } == null) {
                    printInfo("Vault ${it.uuid} will be added to the database")
                    database.getVaultDao().insertVault(it)
                }
            }

            val keychainStoredEncryptedMedia = with(keychainHolder) {
                val uuidRegex =
                    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$".toRegex()
                val vaults =
                    filesDir.listFiles { it.isDirectory && it.nameWithoutExtension.matches(uuidRegex) }
                val encryptedMedia = mutableListOf<Media.EncryptedMedia2>()
                vaults?.forEach { vaultFolder ->
                    (vaultFolder.listFiles()?.filter { it.name.endsWith("enc") }
                        ?: emptyList()).forEach { file ->
                        try {
                            val id = file.nameWithoutExtension.toLong()
                            if (databaseStoredEncryptedMedia?.find { media -> media.id == id } != null) {
                                return@forEach
                            }
                            val oldEncryptedMedia = file.decrypt<EncryptedMedia>()
                            printInfo("Migrating old encrypted media: ${oldEncryptedMedia.id}")
                            file.delete()
                            val encryptedMedia2 =
                                oldEncryptedMedia.migrate(UUID.fromString(vaultFolder.nameWithoutExtension))
                            file.encryptKotlin(encryptedMedia2)
                            encryptedMedia.add(encryptedMedia2)
                        } catch (e: Throwable) {
                            e.printStackTrace()
                            printError("Failed to decrypt file: ${file.name}.")
                        }
                    }
                }
                encryptedMedia
            }

            printInfo("Keychain stored encrypted media: ${keychainStoredEncryptedMedia.size}")

            keychainStoredEncryptedMedia.forEach {
                if (databaseStoredEncryptedMedia?.find { media -> media.id == it.id } == null) {
                    printInfo("Encrypted Media ${it.id} will be added to the database")
                    database.getVaultDao().addMediaToVault(it)
                }
            }

            printInfo("Vault Migration finished")
        }*/
    }

    override suspend fun restoreVault(vault: Vault) {
        val media = database.getVaultDao().getMediaFromVault(vault.uuid).firstOrNull()
        media?.forEach {
            restoreMedia(vault, it)
        }
    }

    override fun getTimelineSettings(): Flow<TimelineSettings?> =
        database.getMediaDao().getTimelineSettings()

    override suspend fun updateTimelineSettings(settings: TimelineSettings) {
        database.getMediaDao().setTimelineSettings(settings)
    }

    override fun <Result> getSetting(
        key: Preferences.Key<Result>,
        defaultValue: Result
    ): Flow<Result> {
        return context.activeDataStore.data.map { it[key] ?: defaultValue }
    }

    override fun getClassifiedCategories(): Flow<List<String>> =
        database.getClassifierDao().getCategoriesFlow()

    override fun getClassifiedMediaByCategory(category: String?): Flow<List<ClassifiedMedia>> =
        if (!category.isNullOrEmpty())
            database.getClassifierDao().getClassifiedMediaByCategoryFlow(category)
        else emptyFlow()

    override fun getClassifiedMediaByMostPopularCategory(): Flow<List<ClassifiedMedia>> =
        database.getClassifierDao().getClassifiedMediaByMostPopularCategoryFlow()

    override suspend fun deleteClassifications() {
        database.getClassifierDao().deleteAllClassifiedMedia()
    }

    override fun getCategoriesWithMedia(): Flow<List<ClassifiedMedia>> =
        database.getClassifierDao().getCategoriesWithMedia()

    override fun getClassifiedMediaCount(): Flow<Int> =
        database.getClassifierDao().getClassifiedMediaCount()

    override suspend fun getCategoryForMediaId(mediaId: Long): String? {
        return database.getClassifierDao().getCategoryForMediaId(mediaId)
    }

    override fun getClassifiedMediaCountAtCategory(category: String): Flow<Int> =
        database.getClassifierDao().getClassifiedMediaCountAtCategory(category)

    override fun getClassifiedMediaThumbnailByCategory(category: String): Flow<ClassifiedMedia?> =
        database.getClassifierDao().getClassifiedMediaThumbnailByCategory(category)

    override suspend fun changeCategory(mediaId: Long, newCategory: String) =
        database.getClassifierDao().changeCategory(mediaId, newCategory)

    // ============ New Category System Implementation ============

    private val categoryDao get() = database.getCategoryDao()

    override suspend fun createCategory(category: Category): Long =
        categoryDao.insertCategory(category)

    override suspend fun updateCategory(category: Category) =
        categoryDao.updateCategory(category)

    override suspend fun deleteCategory(categoryId: Long) =
        categoryDao.deleteCategoryById(categoryId)

    override fun getCategory(categoryId: Long): Flow<Category?> =
        categoryDao.getCategoryByIdFlow(categoryId)

    override suspend fun getCategoryAsync(categoryId: Long): Category? =
        categoryDao.getCategoryById(categoryId)

    override fun getAllCategories(): Flow<List<Category>> =
        categoryDao.getAllCategories()

    override suspend fun getAllCategoriesAsync(): List<Category> =
        categoryDao.getAllCategoriesAsync()

    override fun getCategoriesWithMediaCount(): Flow<List<CategoryWithMediaCount>> =
        categoryDao.getCategoriesWithMediaCount()

    override fun getDistinctClassifiedMediaCount(): Flow<Int> =
        categoryDao.getDistinctClassifiedMediaCount()

    override fun getCategoryCount(): Flow<Int> =
        categoryDao.getCategoryCount()

    override fun getTopCategories(limit: Int): Flow<List<CategoryWithMediaCount>> =
        categoryDao.getTopCategoriesByMediaCount(limit)

    override suspend fun updateCategoryThreshold(categoryId: Long, threshold: Float) =
        categoryDao.updateCategoryThreshold(categoryId, threshold)

    override suspend fun updateCategoryName(categoryId: Long, name: String) =
        categoryDao.updateCategoryName(categoryId, name)

    override suspend fun toggleCategoryPinned(categoryId: Long, isPinned: Boolean) =
        categoryDao.updateCategoryPinned(categoryId, isPinned)

    override fun getMediaIdsInCategory(categoryId: Long): Flow<List<Long>> =
        categoryDao.getMediaIdsInCategory(categoryId)

    override suspend fun getMediaIdsInCategoryAsync(categoryId: Long): List<Long> =
        categoryDao.getMediaIdsInCategoryAsync(categoryId)

    override fun getCategoriesForMedia(mediaId: Long): Flow<List<Category>> =
        categoryDao.getCategoriesForMedia(mediaId)

    override suspend fun addMediaToCategory(
        mediaId: Long,
        categoryId: Long,
        similarity: Float,
        isManual: Boolean
    ) = categoryDao.insertMediaCategory(
        MediaCategory(
            mediaId = mediaId,
            categoryId = categoryId,
            similarityScore = similarity,
            isManuallyAdded = isManual
        )
    )

    override suspend fun removeMediaFromCategory(mediaId: Long, categoryId: Long) =
        categoryDao.removeMediaFromCategory(mediaId, categoryId)

    override fun getMediaCountInCategory(categoryId: Long): Flow<Int> =
        categoryDao.getMediaCountInCategory(categoryId)

    override fun getThumbnailMediaIdForCategory(categoryId: Long): Flow<Long?> =
        categoryDao.getThumbnailMediaIdForCategory(categoryId)

    override suspend fun initializeDefaultCategories() {
        val existingCategories = categoryDao.getAllCategoriesAsync()
        if (existingCategories.isEmpty()) {
            categoryDao.insertCategories(Category.DEFAULT_CATEGORIES)
        }
    }

    override suspend fun resetCategoryData() =
        categoryDao.resetAllCategoryData()

    override suspend fun getVideoCategoryMembershipCount() =
        categoryDao.getVideoCategoryMembershipCount()

    override suspend fun deleteVideoCategoryMemberships() =
        categoryDao.deleteVideoCategoryMemberships()

    override fun getMetadata(media: Media): Flow<MediaMetadata> {
        return database.getMetadataDao().getFullMetadata(media.id).map { it.toMediaMetadata() }
    }

    override fun getMetadata(): Flow<List<MediaMetadata>> {
        return database.getMetadataDao().getFullMetadata().map { list ->
            list.map { it.toMediaMetadata() }
        }
    }

    override suspend fun updateAlbumThumbnail(
        albumId: Long,
        thumbnail: Uri
    ) = database.getAlbumThumbnailDao().updateAlbumThumbnail(AlbumThumbnail(albumId, thumbnail))

    override suspend fun deleteAlbumThumbnail(albumId: Long) =
        database.getAlbumThumbnailDao().deleteAlbumThumbnail(albumId)

    override fun getAlbumThumbnail(albumId: Long): Flow<AlbumThumbnail?> =
        database.getAlbumThumbnailDao().getAlbumThumbnail(albumId)

    override fun hasAlbumThumbnail(albumId: Long): Flow<Boolean> =
        database.getAlbumThumbnailDao().hasAlbumThumbnail(albumId)

    override fun getAlbumThumbnails(): Flow<List<AlbumThumbnail>> =
        database.getAlbumThumbnailDao().getAlbumThumbnailsFlow()

    override suspend fun collectMetadataFor(media: Media, bulk: Boolean) {
        if (media.isCloud) {
            if (!collectCloudMetadata(media) && bulk) throw IllegalStateException("cloud_metadata_unavailable")
            return
        }
        val metadata = context.retrieveExtraMediaMetadata(
            isolatedParser = isolatedParser,
            geocoder = geocoder,
            media = media,
            usePerFileIsolation = shouldUsePerFileIsolation(bulk),
            policy = metadataParsingPolicy(bulk)
        )
        if (metadata != null) {
            database.getMetadataDao().addMetadata(metadata)
            printDebug("collectMetadataFor: saved metadata for ${media.id}")
        } else {
            if (bulk) throw IllegalStateException("metadata_unavailable")
            printWarning("collectMetadataFor: no metadata returned for ${media.id} (uri=${media.getUri()})")
        }
    }

    private suspend fun collectCloudMetadata(media: Media): Boolean = withContext(Dispatchers.IO) {
        val uri = media.getUri()
        val providerName = uri.authority ?: return@withContext false
        // remoteId may contain slashes (SMB/NFS/WebDAV paths like "Photos/IMG.jpg"); pathSegments
        // .first() would truncate it and never match the stored entity's remoteId.
        val remoteId = uri.path?.trimStart('/')?.takeIf { it.isNotEmpty() } ?: return@withContext false
        val providerType = try {
            com.dot.gallery.cloud.core.ProviderType.valueOf(providerName)
        } catch (_: Exception) { return@withContext false }
        val serverConfigId = uri.getQueryParameter("cfg")?.toLongOrNull() ?: return@withContext false
        val entity = database.getCloudMediaDao().getByRemoteId(remoteId, providerType, serverConfigId)
            ?: return@withContext false
        val locationName = listOfNotNull(entity.city, entity.state, entity.country)
            .joinToString(", ").ifBlank { null }
        val metadata = MediaMetadata(
            mediaId = media.id,
            imageDescription = entity.imageDescription,
            dateTimeOriginal = entity.dateTimeOriginal,
            manufacturerName = entity.cameraMake,
            modelName = entity.cameraModel,
            lensModel = entity.lensModel,
            aperture = entity.aperture,
            exposureTime = entity.exposureTime,
            iso = entity.iso?.toString(),
            focalLength = entity.focalLength,
            gpsLatitude = entity.latitude,
            gpsLongitude = entity.longitude,
            gpsLocationName = locationName,
            gpsLocationNameCountry = entity.country,
            gpsLocationNameCity = entity.city,
            imageWidth = entity.width,
            imageHeight = entity.height,
            imageResolutionX = null,
            imageResolutionY = null,
            resolutionUnit = null,
            durationMs = entity.duration?.let { parseDurationToMs(it) },
            videoWidth = if (media.duration != null) entity.width else null,
            videoHeight = if (media.duration != null) entity.height else null,
            frameRate = null,
            bitRate = null,
            isNightMode = false,
            isPanorama = false,
            isPhotosphere = false,
            isLongExposure = false,
            isMotionPhoto = false
        )
        database.getMetadataDao().addMetadata(metadata)
        printDebug("collectMetadataFor: saved cloud metadata for ${media.id}")
        true
    }

    private fun parseDurationToMs(duration: String): Long? {
        // Immich duration format: "0:00:05.123456" or "HH:MM:SS.fraction"
        return try {
            val parts = duration.split(":")
            if (parts.size == 3) {
                val hours = parts[0].toLong()
                val minutes = parts[1].toLong()
                val seconds = parts[2].toDouble()
                ((hours * 3600 + minutes * 60) * 1000 + (seconds * 1000)).toLong()
            } else null
        } catch (_: Exception) { null }
    }

    override suspend fun addImageEmbedding(imageEmbedding: ImageEmbedding) {
        database.getImageEmbeddingDao().addImageEmbedding(imageEmbedding)
    }

    override suspend fun getRecord(id: Long): ImageEmbedding? {
        return database.getImageEmbeddingDao().getRecord(id)
    }

    override fun getImageEmbeddings(): Flow<List<ImageEmbedding>> {
        return database.getImageEmbeddingDao().getRecords()
    }

    // ============ Album Groups ============

    override suspend fun insertAlbumGroup(group: AlbumGroup): Long =
        database.getAlbumGroupDao().insertGroup(group)

    override suspend fun updateAlbumGroup(group: AlbumGroup) =
        database.getAlbumGroupDao().updateGroup(group)

    override suspend fun deleteAlbumGroup(groupId: Long) =
        database.getAlbumGroupDao().deleteGroup(groupId)

    override fun getAllAlbumGroups(): Flow<List<AlbumGroup>> =
        database.getAlbumGroupDao().getAllGroups()

    override fun getAlbumGroup(groupId: Long): Flow<AlbumGroup?> =
        database.getAlbumGroupDao().getGroup(groupId)

    override suspend fun getAlbumGroupAsync(groupId: Long): AlbumGroup? =
        database.getAlbumGroupDao().getGroupAsync(groupId)

    override suspend fun addAlbumToGroup(member: AlbumGroupMember) =
        database.getAlbumGroupDao().addAlbumToGroup(member)

    override suspend fun removeAlbumFromGroup(member: AlbumGroupMember) =
        database.getAlbumGroupDao().removeAlbumFromGroup(member)

    override suspend fun removeAllAlbumsFromGroup(groupId: Long) =
        database.getAlbumGroupDao().removeAllAlbumsFromGroup(groupId)

    override fun getAlbumIdsInGroup(groupId: Long): Flow<List<Long>> =
        database.getAlbumGroupDao().getAlbumIdsInGroup(groupId)

    override fun getAllGroupMembers(): Flow<List<AlbumGroupMember>> =
        database.getAlbumGroupDao().getAllGroupMembers()

    override suspend fun getGroupIdForAlbum(albumId: Long): Long? =
        database.getAlbumGroupDao().getGroupIdForAlbum(albumId)

    // ============ Merged Subfolder Albums ============

    override suspend fun insertMergedSubfolderAlbum(mergedSubfolderAlbum: MergedSubfolderAlbum) =
        database.getMergedSubfolderDao().insertMergedSubfolderAlbum(mergedSubfolderAlbum)

    override suspend fun removeMergedSubfolderAlbum(mergedSubfolderAlbum: MergedSubfolderAlbum) =
        database.getMergedSubfolderDao().removeMergedSubfolderAlbum(mergedSubfolderAlbum)

    override suspend fun updateMergedSubfolderDisplayMode(id: Long, displayMode: String) =
        database.getMergedSubfolderDao().updateDisplayMode(id, displayMode)

    override fun getMergedSubfolderAlbums(): Flow<List<MergedSubfolderAlbum>> =
        database.getMergedSubfolderDao().getMergedSubfolderAlbums()

    // ============ Collections ============

    private val collectionDao get() = database.getCollectionDao()

    override suspend fun insertCollection(collection: Collection): Long =
        collectionDao.insertCollection(collection)

    override suspend fun updateCollection(collection: Collection) =
        collectionDao.updateCollection(collection)

    override suspend fun deleteCollection(collectionId: Long) =
        collectionDao.deleteCollection(collectionId)

    override fun getCollection(collectionId: Long): Flow<Collection?> =
        collectionDao.getCollectionFlow(collectionId)

    override suspend fun getCollectionAsync(collectionId: Long): Collection? =
        collectionDao.getCollectionAsync(collectionId)

    override fun getAllCollections(): Flow<List<Collection>> =
        collectionDao.getAllCollections()

    override fun getCollectionsWithCount(): Flow<List<CollectionWithCount>> =
        collectionDao.getCollectionsWithCount().map { list ->
            list.map { it.toCollectionWithCount() }
        }

    override suspend fun updateCollectionLabel(collectionId: Long, label: String) =
        collectionDao.updateCollectionLabel(collectionId, label)

    override suspend fun toggleCollectionPinned(collectionId: Long, isPinned: Boolean) =
        collectionDao.updateCollectionPinned(collectionId, isPinned)

    override suspend fun updateCollectionCover(collectionId: Long, mediaId: Long?) =
        collectionDao.updateCollectionCover(collectionId, mediaId)

    override suspend fun addMediaToCollection(collectionId: Long, mediaId: Long) =
        collectionDao.addMediaToCollection(CollectionMedia(collectionId, mediaId))

    override suspend fun addMediaListToCollection(collectionId: Long, mediaIds: List<Long>) =
        collectionDao.addMediaListToCollection(
            mediaIds.map { CollectionMedia(collectionId, it) }
        )

    override suspend fun removeMediaFromCollection(collectionId: Long, mediaId: Long) =
        collectionDao.removeMediaFromCollection(collectionId, mediaId)

    override fun getMediaIdsInCollection(collectionId: Long): Flow<List<Long>> =
        collectionDao.getMediaIdsInCollection(collectionId)

    override suspend fun getMediaIdsInCollectionAsync(collectionId: Long): List<Long> =
        collectionDao.getMediaIdsInCollectionAsync(collectionId)

    override fun getMediaCountInCollection(collectionId: Long): Flow<Int> =
        collectionDao.getMediaCountInCollection(collectionId)

    override fun getCollectionIdsForMedia(mediaId: Long): Flow<List<Long>> =
        collectionDao.getCollectionIdsForMedia(mediaId)

    override suspend fun cleanupOrphanedCollectionMedia(validMediaIds: List<Long>) =
        collectionDao.cleanupOrphanedCollectionMedia(validMediaIds)

    override suspend fun addAlbumsToCollection(collectionId: Long, albumIds: List<Long>) {
        collectionDao.addAlbumsToCollection(
            albumIds.map { com.dot.gallery.feature_node.domain.model.CollectionAlbum(collectionId, it) }
        )
    }

    override suspend fun removeAlbumFromCollection(collectionId: Long, albumId: Long) =
        collectionDao.removeAlbumFromCollection(collectionId, albumId)

    override suspend fun replaceAlbumsInCollection(collectionId: Long, albumIds: List<Long>) =
        collectionDao.replaceAlbumsInCollection(collectionId, albumIds)

    override fun getAllAlbumIdsInCollections(): Flow<List<Long>> =
        collectionDao.getAllAlbumIdsInCollections()

    override fun getAlbumIdsInCollection(collectionId: Long): Flow<List<Long>> =
        collectionDao.getAlbumIdsInCollection(collectionId)

    // ============ Album Sections ============

    override suspend fun insertAlbumSection(section: AlbumSection): Long =
        database.getAlbumSectionDao().insertSection(section)

    override suspend fun updateAlbumSection(section: AlbumSection) =
        database.getAlbumSectionDao().updateSection(section)

    override suspend fun deleteAlbumSection(sectionId: Long) =
        database.getAlbumSectionDao().deleteSection(sectionId)

    override fun getAllAlbumSections(): Flow<List<AlbumSection>> =
        database.getAlbumSectionDao().getAllSections()

    override suspend fun getAllAlbumSectionsAsync(): List<AlbumSection> =
        database.getAlbumSectionDao().getAllSectionsAsync()

    override suspend fun getAlbumSectionAsync(sectionId: Long): AlbumSection? =
        database.getAlbumSectionDao().getSectionAsync(sectionId)

    override suspend fun getAlbumSectionByType(type: Int): AlbumSection? =
        database.getAlbumSectionDao().getSectionByType(type)

    override suspend fun updateSectionDisplayOrder(sectionId: Long, order: Int) =
        database.getAlbumSectionDao().updateDisplayOrder(sectionId, order)

    override suspend fun updateSectionVisibility(sectionId: Long, visible: Boolean) =
        database.getAlbumSectionDao().updateVisibility(sectionId, visible)

    override suspend fun updateSectionExpanded(sectionId: Long, expanded: Boolean) =
        database.getAlbumSectionDao().updateExpanded(sectionId, expanded)

    override suspend fun getAlbumSectionCount(): Int =
        database.getAlbumSectionDao().getSectionCount()

    override suspend fun addAlbumToSection(member: AlbumSectionMember) =
        database.getAlbumSectionDao().addAlbumToSection(member)

    override suspend fun removeAlbumFromSection(member: AlbumSectionMember) =
        database.getAlbumSectionDao().removeAlbumFromSection(member)

    override suspend fun removeAlbumFromAllSections(albumId: Long) =
        database.getAlbumSectionDao().removeAlbumFromAllSections(albumId)

    override fun getAllSectionMembers(): Flow<List<AlbumSectionMember>> =
        database.getAlbumSectionDao().getAllSectionMembers()

    override suspend fun getSectionIdForAlbum(albumId: Long): Long? =
        database.getAlbumSectionDao().getSectionIdForAlbum(albumId)

    companion object {
        private const val SQLITE_BIND_CHUNK_SIZE = 900
        private const val STARTUP_MEDIA_LIMIT = 250
        private const val CATEGORY_THUMBNAIL_MAX_IDS = 100

        private fun relativePath(newPath: String) = ContentValues().apply {
            put(MediaStore.MediaColumns.RELATIVE_PATH, newPath)
        }
    }
}