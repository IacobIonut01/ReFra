/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.webdav

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.net.toUri
import com.dot.gallery.cloud.core.CloudAlbum
import com.dot.gallery.cloud.core.CloudAuthToken
import com.dot.gallery.cloud.core.CloudServerConfig
import com.dot.gallery.cloud.core.CloudServerInfo
import com.dot.gallery.cloud.core.CloudStorageInfo
import com.dot.gallery.cloud.core.ConnectionState
import com.dot.gallery.cloud.core.Disconnectable
import com.dot.gallery.cloud.core.ProviderCapability
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.SharedLinkInfo
import com.dot.gallery.cloud.core.SyncState
import com.dot.gallery.cloud.core.ThumbnailSize
import com.dot.gallery.cloud.core.auth.CloudConnectionErrorKind
import com.dot.gallery.cloud.core.auth.CloudConnectionException
import com.dot.gallery.cloud.core.capabilities.RemoteAlbumCopyResult
import com.dot.gallery.cloud.core.capabilities.RemoteAlbumCopyState
import com.dot.gallery.cloud.core.capabilities.RemoteAlbumWriteProvider
import com.dot.gallery.cloud.core.capabilities.RemoteMediaProvider
import com.dot.gallery.cloud.core.capabilities.RemoteNameConflictPolicy
import com.dot.gallery.cloud.core.capabilities.ShareLinkCapableProvider
import com.dot.gallery.cloud.core.capabilities.SyncDelta
import com.dot.gallery.cloud.core.capabilities.remoteAlbumFilePath
import com.dot.gallery.cloud.core.capabilities.remoteCopyFileName
import com.dot.gallery.cloud.image.firstAvailableVideoFrame
import com.dot.gallery.cloud.data.dao.CloudMediaDao
import com.dot.gallery.cloud.data.entity.CloudMediaEntity
import com.dot.gallery.cloud.sync.deleteAppLocalCopies
import com.dot.gallery.cloud.webdav.data.api.WebDavClient
import com.dot.gallery.cloud.webdav.data.api.WebDavException
import com.dot.gallery.cloud.webdav.data.api.WebDavResource
import com.dot.gallery.cloud.webdav.data.api.buildWebDavOkHttp
import com.dot.gallery.core.Resource
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.parseCaptureTimestamp
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.util.printDebug
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLException

internal fun canCacheWebDavChecksum(etag: String): Boolean = etag.isNotBlank()

internal fun shouldReconcileWebDavScan(page: Int, configId: Long, complete: Boolean): Boolean =
    page == 0 && configId > 0L && complete

/**
 * Generic WebDAV-backed [RemoteMediaProvider]. All server-agnostic behavior lives
 * here; per-server specialization is delegated to a [WebDavDialect]. ownCloud,
 * Nextcloud and plain WebDAV are all instances of this class with different dialects.
 */
open class WebDavMediaProvider(
    private val context: Context,
    private val cloudMediaDao: CloudMediaDao,
    private val dialect: WebDavDialect
) : RemoteMediaProvider,
    ShareLinkCapableProvider,
    RemoteAlbumWriteProvider,
    Disconnectable {

    override val providerType: ProviderType get() = dialect.providerType
    override val displayName: String get() = dialect.displayName

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var currentConfig: CloudServerConfig? = null
    private var session: WebDavSession? = null
    private val webDavClient: WebDavClient? get() = session?.webDavClient
    private val checksumMutex = Mutex()
    private val copyMutex = Mutex()
    private val remoteChecksumCache = ConcurrentHashMap<String, CachedRemoteChecksum>()

    private data class CachedRemoteChecksum(
        val revision: String,
        val hash: String
    )

    override val isAvailable: Boolean
        get() = currentConfig != null && _connectionState.value == ConnectionState.CONNECTED

    override val capabilities: Set<ProviderCapability> = buildSet {
        add(ProviderCapability.REMOTE_ASSETS)
        add(ProviderCapability.REMOTE_ALBUMS)
        add(ProviderCapability.SYNC)
        add(ProviderCapability.ALBUM_WRITE)
        if (WebDavFeatureKey.SHARE_LINK in dialect.features) add(ProviderCapability.SHARE_CREATE)
        // Server-side favorites (Nextcloud). ownCloud/generic WebDAV don't expose the flag.
        if (WebDavFeatureKey.FAVORITES in dialect.features) add(ProviderCapability.FAVORITE)
        // No ProviderCapability.TRASH: WebDAV trashAsset == deleteAsset (hard-delete, no restore).
    }

    override fun disconnect() {
        _connectionState.value = ConnectionState.DISCONNECTED
        currentConfig = null
        session = null
        remoteChecksumCache.clear()
    }

    override fun configure(config: CloudServerConfig) {
        currentConfig = config
        remoteChecksumCache.clear()
        val baseUrl = config.serverUrl.trimEnd('/')
        val username = config.username ?: ""
        val password = config.password ?: ""
        val okHttp = buildWebDavOkHttp(60)
        val client = WebDavClient(okHttp, baseUrl, username, password, dialect.filesEndpoint(username))
        session = WebDavSession(context, okHttp, baseUrl, username, password, config, client)
        printDebug("${dialect.displayName}Provider: Configured with server $baseUrl")
    }

    // === Auth ===

    override suspend fun testConnection(config: CloudServerConfig): Result<CloudServerInfo> =
        withContext(Dispatchers.IO) {
            try {
                val okHttp = buildWebDavOkHttp(15)
                val username = config.username ?: ""
                val client = WebDavClient(
                    okHttp, config.serverUrl, username, config.password ?: "",
                    dialect.filesEndpoint(username)
                )
                val tempSession = WebDavSession(
                    context, okHttp, config.serverUrl.trimEnd('/'), username,
                    config.password ?: "", config, client
                )
                client.checkConnection()
                Result.success(dialect.serverInfo(tempSession))
            } catch (e: Exception) {
                Result.failure(e.toConnectionException())
            }
        }

    override suspend fun authenticate(config: CloudServerConfig): Result<CloudAuthToken> =
        withContext(Dispatchers.IO) {
            try {
                _connectionState.value = ConnectionState.AUTHENTICATING
                configure(config)
                val s = session ?: throw IllegalStateException("Not configured")
                s.webDavClient.checkConnection()
                _connectionState.value = ConnectionState.CONNECTED
                Result.success(
                    CloudAuthToken(
                        accessToken = "",
                        userId = dialect.currentUserId(s) ?: config.username,
                        userEmail = dialect.currentUserEmail(s)
                    )
                )
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.ERROR
                Result.failure(e.toConnectionException())
            }
        }

    override suspend fun getServerVersion(): Result<String> = withContext(Dispatchers.IO) {
        val s = session ?: return@withContext Result.failure(IllegalStateException("Not configured"))
        dialect.serverVersion(s)
    }

    // === Remote Assets ===

    override fun getRemoteAssets(page: Int, pageSize: Int): Flow<Resource<List<CloudMediaEntity>>> = flow {
        try {
            val client = webDavClient ?: throw IllegalStateException("Not configured")
            val configId = currentConfig?.id ?: 0L
            val scan = scanMediaFiles(client, "")
            if (shouldReconcileWebDavScan(page, configId, scan.complete)) {
                val pruned = cloudMediaDao.deleteMissingRemoteMedia(
                    configId,
                    dialect.providerType,
                    scan.files.map { client.relativePath(it.href) }
                )
                if (currentConfig?.syncRemoteDeletions == true && pruned.isNotEmpty()) {
                    deleteAppLocalCopies(context, pruned)
                }
            }
            val paged = scan.files.drop(page * pageSize).take(pageSize)
            val entities = paged.map { it.toCloudMediaEntity(configId) }
            cloudMediaDao.insertAll(entities)
            emit(Resource.Success(entities))
        } catch (e: CancellationException) {
            // The consumer cancelled (e.g. a `first()`/`take()` prefetch). Re-throw so the
            // cancellation propagates cleanly — emitting from this catch would violate flow
            // exception transparency (AbortFlowException) and abort the whole prefetch.
            throw e
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    override fun getRemoteFavorites(): Flow<Resource<List<CloudMediaEntity>>> = flow {
        if (!dialect.readsFavoriteFlag) {
            emit(Resource.Success(emptyList<CloudMediaEntity>()))
            return@flow
        }
        try {
            val client = webDavClient ?: throw IllegalStateException("Not configured")
            val configId = currentConfig?.id ?: 0L
            val favorites = scanMediaFiles(client, "").files
                .filter { it.favorite }
                .map { it.toCloudMediaEntity(configId) }
            emit(Resource.Success(favorites))
        } catch (e: CancellationException) {
            // The consumer cancelled (e.g. a `first()`/`take()` prefetch). Re-throw so the
            // cancellation propagates cleanly — emitting from this catch would violate flow
            // exception transparency (AbortFlowException) and abort the whole prefetch.
            throw e
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    override fun getRemoteTrashed(): Flow<Resource<List<CloudMediaEntity>>> =
        flow { emit(Resource.Success(emptyList<CloudMediaEntity>())) }

    // === Albums / Folders ===

    override fun getRemoteAlbums(): Flow<Resource<List<CloudAlbum>>> = flow {
        try {
            val client = webDavClient ?: throw IllegalStateException("Not configured")
            val configId = currentConfig?.id ?: 0L
            val resources = client.propFind("", depth = 1)
            val folders = resources.mapNotNull { res ->
                if (!res.isCollection) return@mapNotNull null
                // Folder remoteId is the relative path: getRemoteAlbumMedia() uses it
                // directly as the PROPFIND path, so it must never be the numeric fileid.
                val relativePath = client.relativePath(res.href)
                // A blank relative path is the files-collection root itself (the PROPFIND
                // target), not a child album — skip it so the whole tree isn't listed as
                // one giant album (which would also double the path and fetch nothing).
                if (relativePath.isBlank()) return@mapNotNull null
                CloudAlbum(
                    remoteId = relativePath,
                    providerType = dialect.providerType,
                    serverConfigId = configId,
                    name = res.displayName,
                    assetCount = 0,
                    isShared = false
                )
            }
            emit(Resource.Success(folders))
        } catch (e: CancellationException) {
            // The consumer cancelled (e.g. a `first()`/`take()` prefetch). Re-throw so the
            // cancellation propagates cleanly — emitting from this catch would violate flow
            // exception transparency (AbortFlowException) and abort the whole prefetch.
            throw e
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    override fun getRemoteAlbumMedia(albumId: String): Flow<Resource<List<CloudMediaEntity>>> = flow {
        try {
            val client = webDavClient ?: throw IllegalStateException("Not configured")
            val configId = currentConfig?.id ?: 0L
            val mediaFiles = scanMediaFiles(client, albumId).files
            emit(Resource.Success(mediaFiles.map { it.toCloudMediaEntity(configId) }))
        } catch (e: CancellationException) {
            // The consumer cancelled (e.g. a `first()`/`take()` prefetch). Re-throw so the
            // cancellation propagates cleanly — emitting from this catch would violate flow
            // exception transparency (AbortFlowException) and abort the whole prefetch.
            throw e
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun createAlbum(name: String): Result<CloudAlbum> =
        withContext(Dispatchers.IO) {
            try {
                webDavClient?.mkdir(name) ?: throw IllegalStateException("Not configured")
                Result.success(
                    CloudAlbum(
                        remoteId = name,
                        providerType = dialect.providerType,
                        serverConfigId = currentConfig?.id ?: 0L,
                        name = name,
                        assetCount = 0
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun addToAlbum(albumId: String, assetIds: List<String>): Result<Unit> =
        Result.failure(UnsupportedOperationException("WebDAV folders don't support adding by asset ID"))

    override suspend fun toggleFavorite(remoteId: String, favorite: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            val s = session ?: return@withContext Result.failure(IllegalStateException("Not configured"))
            if (WebDavFeatureKey.FAVORITES !in dialect.features) {
                return@withContext Result.failure(
                    UnsupportedOperationException("${dialect.displayName} does not support favorites")
                )
            }
            dialect.toggleFavorite(s, remoteId, favorite)
        }

    override suspend fun trashAsset(remoteId: String): Result<Unit> = deleteAsset(remoteId)

    override suspend fun restoreAsset(remoteId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("${dialect.displayName} does not support restoring from trash"))

    override suspend fun deleteAsset(remoteId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val client = webDavClient ?: throw IllegalStateException("Not configured")
                val configId = currentConfig?.id ?: throw IllegalStateException("Not configured")
                client.delete(resolveRemotePath(remoteId))
                cloudMediaDao.delete(remoteId, dialect.providerType, configId)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun search(query: String): Result<List<CloudMediaEntity>> {
        return try {
            val client = webDavClient ?: throw IllegalStateException("Not configured")
            val configId = currentConfig?.id ?: 0L
            val matched = scanMediaFiles(client, "").files
                .filter { it.displayName.contains(query, ignoreCase = true) }
                .map { it.toCloudMediaEntity(configId) }
            Result.success(matched)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getStorageInfo(): Result<CloudStorageInfo> = withContext(Dispatchers.IO) {
        val s = session ?: return@withContext Result.failure(IllegalStateException("Not configured"))
        if (WebDavFeatureKey.QUOTA !in dialect.features) {
            return@withContext Result.failure(
                UnsupportedOperationException("${dialect.displayName} does not report storage info")
            )
        }
        dialect.storageInfo(s)
    }

    override fun getThumbnailUrl(remoteId: String, size: ThumbnailSize): String =
        getThumbnailUrl(remoteId, size, null)

    override fun getThumbnailUrl(remoteId: String, size: ThumbnailSize, fileId: String?): String {
        val s = session ?: return ""
        val remotePath = resolveRemotePath(remoteId)
        val previewUrl = dialect.previewUrl(
            s,
            fileId = fileId?.takeIf { it.isNotBlank() } ?: remoteId,
            remotePath = remotePath,
            size = size
        )
        if (previewUrl != null) return previewUrl
        // No server preview is available for this item. For videos we must NOT fall
        // back to the full original download (it isn't a decodable still image and
        // only produces noisy decode failures); signal "no preview" with an empty
        // string instead. Images always resolve a preview URL above.
        return if (isVideoPath(remotePath)) "" else getOriginalUrl(remoteId)
    }

    override fun getOriginalUrl(remoteId: String): String =
        webDavClient?.getDownloadUrl(resolveRemotePath(remoteId)) ?: ""

    override fun getAuthHeaders(): Map<String, String> =
        webDavClient?.getAuthHeaders() ?: emptyMap()

    /**
     * WebDAV has no server-side preview for videos, so decode a poster frame locally from
     * the authenticated original URL. MediaMetadataRetriever reads only the moov atom and
     * the frames it needs, so it doesn't download the whole remote file.
     */
    override suspend fun getVideoThumbnailBytes(remoteId: String, size: ThumbnailSize): ByteArray? =
        withContext(Dispatchers.IO) {
            val remotePath = resolveRemotePath(remoteId)
            if (!isVideoPath(remotePath)) return@withContext null
            val url = getOriginalUrl(remoteId).ifBlank { return@withContext null }
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(url, getAuthHeaders())
                val durationMillis = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                val frame = firstAvailableVideoFrame(durationMillis) { timeUs ->
                    runCatching {
                        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    }.getOrNull()
                } ?: return@withContext null
                val target = if (size == ThumbnailSize.THUMBNAIL) 256 else 1024
                val scaled = scaleBitmap(frame, target)
                ByteArrayOutputStream().use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    out.toByteArray()
                }
            } catch (_: Exception) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }

    private fun scaleBitmap(bitmap: Bitmap, target: Int): Bitmap {
        val max = maxOf(bitmap.width, bitmap.height)
        if (max <= target) return bitmap
        val scale = target.toFloat() / max
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    // === Share Link ===

    override suspend fun createShareLink(assetIds: List<String>, expiresAt: Long?): Result<String> =
        withContext(Dispatchers.IO) {
            val s = session ?: return@withContext Result.failure(IllegalStateException("Not configured"))
            if (WebDavFeatureKey.SHARE_LINK !in dialect.features) {
                return@withContext Result.failure(
                    UnsupportedOperationException("${dialect.displayName} does not support share links")
                )
            }
            val path = assetIds.firstOrNull()
                ?: return@withContext Result.failure(Exception("No path"))
            dialect.createShareLink(s, resolveRemotePath(path), expiresAt)
        }

    // === Sync ===

    override fun deterministicRemoteId(localMedia: Media, targetPath: String?): String =
        (targetPath?.trimEnd('/') ?: dialect.defaultUploadFolder) + "/${localMedia.label}"

    override suspend fun uploadAsset(localMedia: Media, targetPath: String?): Result<CloudMediaEntity> =
        withContext(Dispatchers.IO) {
            try {
                val client = webDavClient ?: throw IllegalStateException("Not configured")
                val configId = currentConfig?.id ?: 0L
                val inputStream = context.contentResolver.openInputStream(localMedia.getUri())
                    ?: return@withContext Result.failure(Exception("Cannot open media file"))
                val fileName = localMedia.label
                val remotePath = deterministicRemoteId(localMedia, targetPath)
                val mimeType = localMedia.mimeType
                val tempFile = File(context.cacheDir, "wd_upload_${System.currentTimeMillis()}_$fileName")
                try {
                    inputStream.use { i -> tempFile.outputStream().use { o -> i.copyTo(o) } }
                    // Servers reject a PUT into a missing collection (often 403/409), so
                    // create the target folder chain before uploading.
                    client.ensureParentCollections(remotePath)
                    client.upload(remotePath, tempFile, mimeType)
                    val entity = CloudMediaEntity(
                        remoteId = remotePath,
                        providerType = dialect.providerType,
                        serverConfigId = configId,
                        label = fileName,
                        path = remotePath,
                        relativePath = remotePath.substringBeforeLast('/'),
                        mimeType = mimeType,
                        timestamp = System.currentTimeMillis(),
                        size = tempFile.length(),
                        syncState = SyncState.SYNCED,
                        localCopyPath = localMedia.getUri().toString()
                    )
                    cloudMediaDao.insert(entity)
                    Result.success(entity)
                } finally {
                    tempFile.delete()
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun copyToAlbum(
        media: Media,
        remoteAlbumId: String,
        conflictPolicy: RemoteNameConflictPolicy,
        checksum: String?,
        continuationRemoteId: String?
    ): RemoteAlbumCopyResult = withContext(Dispatchers.IO) {
        copyMutex.withLock {
            try {
                val client = webDavClient ?: throw IllegalStateException("Not configured")
                val initialPath = remoteAlbumFilePath(remoteAlbumId, media.label)
                if (remoteFileExists(client, initialPath) && checksum != null &&
                    client.sha1(initialPath).equals(checksum, ignoreCase = true)
                ) {
                    return@withLock RemoteAlbumCopyResult(
                        state = RemoteAlbumCopyState.ALREADY_PRESENT,
                        remoteId = initialPath
                    )
                }
                var remotePath = initialPath
                var copyNumber = 1
                while (remoteFileExists(client, remotePath)) {
                    remotePath = remoteAlbumFilePath(
                        remoteAlbumId,
                        remoteCopyFileName(media.label, copyNumber++)
                    )
                }
                writeCopyAsset(client, media, remotePath, checksum).fold(
                    onSuccess = {
                        RemoteAlbumCopyResult(
                            state = RemoteAlbumCopyState.COPIED,
                            remoteId = it.remoteId
                        )
                    },
                    onFailure = {
                        RemoteAlbumCopyResult(
                            state = RemoteAlbumCopyState.FAILED,
                            message = it.message ?: "Remote copy failed",
                            retryable = isRetryableCopyFailure(it)
                        )
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RemoteAlbumCopyResult(
                    state = RemoteAlbumCopyState.FAILED,
                    message = e.message ?: "Remote copy failed",
                    retryable = isRetryableCopyFailure(e)
                )
            }
        }
    }

    private suspend fun writeCopyAsset(
        client: WebDavClient,
        media: Media,
        remotePath: String,
        checksum: String?
    ): Result<CloudMediaEntity> = try {
        val configId = currentConfig?.id ?: throw IllegalStateException("Not configured")
        val input = context.contentResolver.openInputStream(media.getUri())
            ?: return Result.failure(Exception("Cannot open media file"))
        val tempFile = File(context.cacheDir, "wd_copy_${System.currentTimeMillis()}_${media.label}")
        try {
            input.use { source -> tempFile.outputStream().use { source.copyTo(it) } }
            client.ensureParentCollections(remotePath)
            try {
                client.upload(remotePath, tempFile, media.mimeType, overwrite = false)
            } catch (e: Exception) {
                val verified = checksum != null && runCatching {
                    client.sha1(remotePath).equals(checksum, ignoreCase = true)
                }.getOrDefault(false)
                if (!verified) throw e
            }
            if (checksum != null && !client.sha1(remotePath).equals(checksum, ignoreCase = true)) {
                runCatching { client.delete(remotePath) }
                return Result.failure(Exception("Remote copy verification failed"))
            }
            val entity = CloudMediaEntity(
                remoteId = remotePath,
                providerType = dialect.providerType,
                serverConfigId = configId,
                label = remotePath.substringAfterLast('/'),
                path = remotePath,
                relativePath = remotePath.substringBeforeLast('/'),
                mimeType = media.mimeType,
                timestamp = System.currentTimeMillis(),
                size = tempFile.length(),
                syncState = SyncState.SYNCED,
                localCopyPath = media.getUri().toString(),
                contentHash = checksum
            )
            cloudMediaDao.insert(entity)
            Result.success(entity)
        } finally {
            tempFile.delete()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun remoteFileExists(client: WebDavClient, remotePath: String): Boolean = try {
        client.propFind(remotePath, depth = 0).any { !it.isCollection }
    } catch (e: WebDavException) {
        if (e.statusCode == 404) false else throw e
    }

    private fun isRetryableCopyFailure(error: Throwable): Boolean = when (error) {
        is WebDavException -> error.statusCode == 408 || error.statusCode == 429 ||
            error.statusCode in 500..599
        is IOException -> true
        else -> false
    }

    override suspend fun downloadAsset(remoteId: String): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val client = webDavClient ?: throw IllegalStateException("Not configured")
            val path = resolveRemotePath(remoteId)
            val bytes = client.download(path)
            val ext = path.substringAfterLast('.', "")
            val cacheFile = File(context.cacheDir, "wd_download_${remoteId.hashCode()}.$ext")
            cacheFile.writeBytes(bytes)
            Result.success(cacheFile.toUri())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSyncDelta(timestamp: Long, reconcileIndex: Boolean): Result<SyncDelta> {
        return try {
            val client = webDavClient ?: throw IllegalStateException("Not configured")
            val configId = currentConfig?.id ?: 0L
            // WebDAV has no delta endpoint — the "delta" is a full recursive scan. Report
            // the complete remote index whenever the scan finished cleanly so the caller
            // can prune vanished files; an incomplete scan must not prune (see AGENTS.md).
            val scan = scanMediaFiles(client, "")
            Result.success(
                SyncDelta(
                    items = scan.files.map { it.toCloudMediaEntity(configId) },
                    completeRemoteIds = if (scan.complete) {
                        scan.files.map { client.relativePath(it.href) }
                    } else {
                        null
                    }
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun bulkUploadCheck(hashes: List<String>): Result<Map<String, Boolean>> =
        Result.success(hashes.indices.associate { it.toString() to false })

    override suspend fun verifyRemoteContent(
        localMedia: Media,
        targetPath: String?,
        contentHash: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val client = webDavClient ?: throw IllegalStateException("Not configured")
            val configId = currentConfig?.id ?: throw IllegalStateException("Not configured")
            val remotePath = deterministicRemoteId(localMedia, targetPath)
            val resource = client.propFind(remotePath, depth = 0).firstOrNull()
                ?.takeUnless { it.isCollection }
                ?: return@withContext Result.success(false)
            currentCoroutineContext().ensureActive()
            val revision = "${resource.etag}|${resource.contentLength}|${resource.lastModified}"
            val canCache = canCacheWebDavChecksum(resource.etag)
            val remoteHash = checksumMutex.withLock {
                val cached = remoteChecksumCache[remotePath]
                    ?.takeIf { canCache && it.revision == revision }
                    ?.hash
                cached ?: runInterruptible { client.sha1(remotePath) }.also { hash ->
                    if (canCache) {
                        remoteChecksumCache[remotePath] = CachedRemoteChecksum(revision, hash)
                    } else {
                        remoteChecksumCache.remove(remotePath)
                    }
                }
            }
            if (cloudMediaDao.updateContentHash(remotePath, dialect.providerType, configId, remoteHash) == 0) {
                cloudMediaDao.insert(resource.toCloudMediaEntity(configId).copy(contentHash = remoteHash))
            }
            Result.success(remoteHash.equals(contentHash, ignoreCase = true))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Plain WebDAV has no content-hash index, so an asset is "already uploaded" when a
     * file with the same name (and matching size, when the server reports it) already
     * exists at the deterministic upload target (mirrors the path built by [uploadAsset]).
     * Lets the backup worker skip files it already pushed instead of re-transferring
     * the whole album every run.
     */
    override suspend fun remoteExists(localMedia: Media, targetPath: String?): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val client = webDavClient ?: return@withContext false
                val remotePath = deterministicRemoteId(localMedia, targetPath)
                val resource = runCatching { client.propFind(remotePath, depth = 0) }
                    .getOrNull()?.firstOrNull() ?: return@withContext false
                if (resource.isCollection) return@withContext false
                val localSize = runCatching {
                    context.contentResolver.openAssetFileDescriptor(localMedia.getUri(), "r")?.use { it.length }
                }.getOrNull() ?: return@withContext true // present remotely; can't stat local, treat as done
                // Some servers omit getcontentlength; if unknown, trust name-match presence.
                resource.contentLength <= 0L || resource.contentLength == localSize
            } catch (_: Exception) {
                false
            }
        }

    // === Unsupported across all WebDAV flavors ===

    override suspend fun toggleArchive(remoteId: String, archived: Boolean): Result<Unit> =
        Result.failure(UnsupportedOperationException("${dialect.displayName} does not support archive"))

    override fun getRemoteArchived(): Flow<Resource<List<CloudMediaEntity>>> =
        flow { emit(Resource.Success(emptyList())) }

    override suspend fun emptyTrash(): Result<Unit> =
        Result.failure(UnsupportedOperationException("${dialect.displayName} does not support empty trash"))

    override suspend fun restoreAllTrash(): Result<Unit> =
        Result.failure(UnsupportedOperationException("${dialect.displayName} does not support restore all trash"))

    override fun getSharedLinks(): Flow<Resource<List<SharedLinkInfo>>> =
        flow { emit(Resource.Success(emptyList())) }

    override suspend fun deleteSharedLink(linkId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("${dialect.displayName} does not support shared link deletion"))

    override suspend fun updateSharedLink(linkId: String, updates: Map<String, Any>): Result<Unit> =
        Result.failure(UnsupportedOperationException("${dialect.displayName} does not support shared link updates"))

    // === Helpers ===

    private fun Exception.toConnectionException(): CloudConnectionException {
        if (this is CloudConnectionException) return this
        val kind = when (this) {
            is WebDavException -> when (statusCode) {
                401, 403 -> CloudConnectionErrorKind.AUTHENTICATION
                404 -> CloudConnectionErrorKind.NOT_FOUND
                in 500..599 -> CloudConnectionErrorKind.SERVER
                else -> CloudConnectionErrorKind.UNKNOWN
            }
            is SSLException -> CloudConnectionErrorKind.TLS
            is UnknownHostException, is SocketTimeoutException -> CloudConnectionErrorKind.NETWORK
            else -> CloudConnectionErrorKind.UNKNOWN
        }
        val message = when (kind) {
            CloudConnectionErrorKind.AUTHENTICATION -> "Authentication failed. Use an app password when two-factor authentication is enabled."
            CloudConnectionErrorKind.NOT_FOUND -> "Nextcloud WebDAV endpoint was not found. Check the server address."
            CloudConnectionErrorKind.NETWORK -> "The server could not be reached."
            CloudConnectionErrorKind.TLS -> "The secure connection could not be verified."
            CloudConnectionErrorKind.SERVER -> "The server returned an error."
            CloudConnectionErrorKind.UNKNOWN -> this.message ?: "Connection failed."
        }
        return CloudConnectionException(kind, message, this)
    }

    /**
     * Maps a stored remoteId to the value WebDAV/preview calls expect. For plain
     * WebDAV the remoteId already *is* the relative path; for ownCloud/Nextcloud
     * it is the stable file id consumed by the preview endpoint. Both are passed
     * through unchanged, matching each dialect's URL builders.
     */
    private fun resolveRemotePath(remoteId: String): String = remoteId

    private val mediaExtensions = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heif", "heic", "avif",
        "mp4", "mkv", "mov", "avi", "webm", "3gp"
    )

    private val videoExtensions = setOf("mp4", "mkv", "mov", "avi", "webm", "3gp")

    private fun isVideoPath(path: String): Boolean =
        path.substringAfterLast('.', "").lowercase() in videoExtensions

    private data class WebDavMediaScan(
        val files: List<WebDavResource>,
        val complete: Boolean
    )

    private fun scanMediaFiles(client: WebDavClient, path: String, depth: Int = 0): WebDavMediaScan {
        if (depth > 3) return WebDavMediaScan(emptyList(), false)
        return try {
            val files = mutableListOf<WebDavResource>()
            var complete = true
            client.propFind(path, depth = 1).drop(1).forEach { resource ->
                if (resource.isCollection) {
                    val subPath = client.relativePath(resource.href)
                    if (subPath.isNotBlank()) {
                        val nested = scanMediaFiles(client, subPath, depth + 1)
                        files += nested.files
                        complete = complete && nested.complete
                    }
                } else {
                    val ext = resource.displayName.substringAfterLast('.', "").lowercase()
                    if (ext in mediaExtensions) files += resource
                }
            }
            WebDavMediaScan(files, complete)
        } catch (_: Exception) {
            WebDavMediaScan(emptyList(), false)
        }
    }

    private fun WebDavResource.toCloudMediaEntity(configId: Long): CloudMediaEntity {
        val ext = displayName.substringAfterLast('.', "").lowercase()
        val mimeType = when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "heif", "heic" -> "image/heif"
            "avif" -> "image/avif"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "webm" -> "video/webm"
            else -> contentType.ifBlank { "application/octet-stream" }
        }
        val remotePath = webDavClient?.relativePath(href) ?: href
        val ts = try {
            java.time.ZonedDateTime.parse(
                lastModified,
                java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
            ).toInstant().toEpochMilli()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
        // RFC 4918 `creationdate` is ISO-8601. Most servers don't return it (SabreDAV
        // omits it by default), but when present it's the closest provider-side
        // capture time available; embedded EXIF resolution refines it later.
        val creationTs = parseCaptureTimestamp(creationDate)
        // Use the file path as the stable remoteId for all WebDAV flavors. The
        // path is what every WebDAV/OCS call expects (preview, delete, favorite,
        // download); the numeric `oc:fileid` is only reliably present on some
        // servers and the path-based preview endpoint does not need it.
        val remoteId = remotePath
        // The server's numeric file id (oc:fileid) is required to request video
        // frame previews via core/preview; pass it to the dialect and persist it so
        // the image pipelines can rebuild the same preview URL on demand.
        val serverFileId = fileId
        val s = session
        val thumbUrl = if (s != null) {
            dialect.previewUrl(
                s,
                fileId = serverFileId.ifBlank { remoteId },
                remotePath = remotePath,
                size = ThumbnailSize.PREVIEW
            )
        } else null

        return CloudMediaEntity(
            remoteId = remoteId,
            providerType = dialect.providerType,
            serverConfigId = configId,
            label = displayName,
            path = remotePath,
            relativePath = remotePath.substringBeforeLast('/'),
            mimeType = mimeType,
            timestamp = ts,
            takenTimestamp = creationTs,
            size = contentLength,
            syncState = SyncState.REMOTE_ONLY,
            favorite = dialect.readsFavoriteFlag && favorite,
            thumbnailUrl = thumbUrl ?: (webDavClient?.getDownloadUrl(remotePath) ?: ""),
            originalUrl = webDavClient?.getDownloadUrl(remotePath) ?: "",
            fileId = serverFileId
        )
    }
}
