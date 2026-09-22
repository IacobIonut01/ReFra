/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.netfs

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.text.format.Formatter
import androidx.core.net.toUri
import com.dot.gallery.cloud.core.CloudAlbum
import com.dot.gallery.cloud.core.CloudAuthToken
import com.dot.gallery.cloud.core.CloudServerConfig
import com.dot.gallery.cloud.core.CloudServerInfo
import com.dot.gallery.cloud.core.CloudStorageInfo
import com.dot.gallery.cloud.core.CloudTrace
import com.dot.gallery.cloud.core.ConnectionState
import com.dot.gallery.cloud.core.Disconnectable
import com.dot.gallery.cloud.core.ProviderCapability
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.SharedLinkInfo
import com.dot.gallery.cloud.core.SyncState
import com.dot.gallery.cloud.core.ThumbnailSize
import com.dot.gallery.cloud.core.capabilities.RemoteAlbumCopyResult
import com.dot.gallery.cloud.core.capabilities.RemoteAlbumCopyState
import com.dot.gallery.cloud.core.capabilities.RemoteAlbumWriteProvider
import com.dot.gallery.cloud.core.capabilities.RemoteMediaProvider
import com.dot.gallery.cloud.core.capabilities.RemoteNameConflictPolicy
import com.dot.gallery.cloud.core.capabilities.ShareLinkCapableProvider
import com.dot.gallery.cloud.core.capabilities.SyncDelta
import com.dot.gallery.cloud.core.capabilities.remoteAlbumFilePath
import com.dot.gallery.cloud.core.capabilities.remoteCopyFileName
import com.dot.gallery.cloud.data.dao.CloudMediaDao
import com.dot.gallery.cloud.data.entity.CloudMediaEntity
import com.dot.gallery.cloud.sync.deleteAppLocalCopies
import com.dot.gallery.cloud.netfs.bridge.NetFsLoopback
import com.dot.gallery.cloud.netfs.bridge.NetFsLoopbackSource
import com.dot.gallery.core.Resource
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.util.printDebug
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

internal class NetFsThumbnailSingleFlight {
    private data class Entry(val monitor: Any = Any(), var users: Int = 0)

    private val entries = mutableMapOf<String, Entry>()

    fun <T> run(key: String, block: () -> T): T {
        val entry = synchronized(entries) {
            entries.getOrPut(key) { Entry() }.also { it.users++ }
        }
        return try {
            synchronized(entry.monitor) { block() }
        } finally {
            synchronized(entries) {
                entry.users--
                if (entry.users == 0 && entries[key] === entry) entries.remove(key)
            }
        }
    }
}

/**
 * Generic network-filesystem [RemoteMediaProvider]. All protocol-agnostic behavior lives
 * here; per-protocol I/O is delegated to a [FileSystemBackend]. SMB and NFS are instances
 * of this class with different backends — mirroring `WebDavMediaProvider` + `WebDavDialect`.
 *
 * Media bytes are exposed to the app's HTTP-only image/video pipeline through the
 * [NetFsLoopback] bridge: [getOriginalUrl]/[getThumbnailUrl] return `http://127.0.0.1` URLs
 * that the bridge resolves back to this provider via [NetFsLoopbackSource].
 */
open class NetworkFileSystemProvider(
    private val context: Context,
    private val cloudMediaDao: CloudMediaDao,
    private val backend: FileSystemBackend
) : RemoteMediaProvider,
    ShareLinkCapableProvider,
    RemoteAlbumWriteProvider,
    Disconnectable,
    NetFsLoopbackSource {

    override val providerType: ProviderType get() = backend.providerType
    override val displayName: String get() = backend.displayName

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var currentConfig: CloudServerConfig? = null

    @Volatile
    private var connection: NetFsConnection? = null

    @Volatile
    private var connectionGeneration = 0L

    @Volatile
    private var mediaIndex: NetFsMediaIndex? = null
    private val mediaIndexLock = Any()
    private val mutationMutex = Mutex()

    override val isAvailable: Boolean
        get() = currentConfig != null && _connectionState.value == ConnectionState.CONNECTED

    override val capabilities: Set<ProviderCapability> = setOf(
        ProviderCapability.REMOTE_ASSETS,
        ProviderCapability.REMOTE_ALBUMS,
        ProviderCapability.SYNC,
        ProviderCapability.ALBUM_WRITE
    )

    @Synchronized
    override fun configure(config: CloudServerConfig) {
        connection?.let { runCatching { backend.close(it) } }
        connection = null
        connectionGeneration++
        mediaIndex = null
        currentConfig = config
        _connectionState.value = ConnectionState.DISCONNECTED
        startTransportWatcher()
        printDebug("${backend.displayName}Provider: Configured with ${config.serverUrl}")
    }

    @Synchronized
    override fun disconnect() {
        stopTransportWatcher()
        connection?.let { runCatching { backend.close(it) } }
        connection = null
        connectionGeneration++
        mediaIndex = null
        _connectionState.value = ConnectionState.DISCONNECTED
        currentConfig = null
    }

    @Synchronized
    private fun requireConnection(): NetFsConnection {
        connection?.let { existing ->
            if (existing.isAlive()) return existing
            // The peer closed (or locally torn down) sessions are never useful again —
            // drop them so we re-dial instead of failing every subsequent op.
            runCatching { backend.close(existing) }
            connection = null
            connectionGeneration++
        }
        val config = currentConfig ?: throw IllegalStateException("Not configured")
        return CloudTrace.time("${backend.providerType} connect") {
            backend.connect(config)
        }.also { connection = it }
    }

    /**
     * Drops the live session without touching [mediaIndex]: a transport change doesn't
     * alter remote content, and the cached listing lets albums/thumbnails recover
     * instantly once the next op re-dials. Any in-flight index build notices via the
     * generation bump and discards its result.
     */
    @Synchronized
    private fun dropConnection() {
        val conn = connection ?: return
        CloudTrace.d("${backend.providerType} dropping session (transport change or dead link)")
        runCatching { backend.close(conn) }
        connection = null
        connectionGeneration++
    }

    // === Transport-change watcher ===
    //
    // A cached session binds to the network it was dialed on. When the device's default
    // network changes transport (Wi-Fi → 5G, VPN added/dropped), the old TCP session is
    // either dead or — worse — half-open: it still reports connected but every read
    // stalls into a timeout. Dropping on the observed transport transition makes the
    // next op re-dial on the new route instead of surfacing "albums unavailable" or
    // pixelated surrogates until the app is killed.

    private val connectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastTransports: Set<Int>? = null

    @Synchronized
    private fun startTransportWatcher() {
        if (networkCallback != null) return
        val cm = connectivityManager ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                caps: NetworkCapabilities
            ) {
                val transports = TRANSPORT_KINDS.filter(caps::hasTransport).toSet()
                val previous = lastTransports
                lastTransports = transports
                if (previous != null && previous != transports) {
                    CloudTrace.d(
                        "${backend.providerType} default network transports $previous -> $transports"
                    )
                    dropConnection()
                }
            }

            override fun onLost(network: Network) {
                // The default network went away; any session bound to it is dead.
                lastTransports = null
                dropConnection()
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
            .onSuccess { networkCallback = callback }
    }

    @Synchronized
    private fun stopTransportWatcher() {
        val callback = networkCallback ?: return
        networkCallback = null
        lastTransports = null
        runCatching { connectivityManager?.unregisterNetworkCallback(callback) }
    }

    @Synchronized
    private fun invalidateMediaIndex() {
        connectionGeneration++
        mediaIndex = null
    }

    @Synchronized
    private fun resetConnection(expected: NetFsConnection) {
        if (connection !== expected) return
        runCatching { backend.close(expected) }
        connection = null
        connectionGeneration++
        mediaIndex = null
    }

    // === Auth ===

    override suspend fun testConnection(config: CloudServerConfig): Result<CloudServerInfo> =
        withContext(Dispatchers.IO) {
            try {
                val conn = backend.connect(config)
                val info = CloudServerInfo(version = backend.displayName, serverName = conn.rootDisplay)
                runCatching { backend.close(conn) }
                Result.success(info)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun authenticate(config: CloudServerConfig): Result<CloudAuthToken> =
        withContext(Dispatchers.IO) {
            try {
                configure(config)
                requireConnection()
                _connectionState.value = ConnectionState.CONNECTED
                Result.success(CloudAuthToken(accessToken = "", userId = config.username))
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.ERROR
                Result.failure(e)
            }
        }

    override suspend fun getServerVersion(): Result<String> =
        Result.success(backend.displayName)

    // === Remote assets ===

    override fun getRemoteAssets(page: Int, pageSize: Int): Flow<Resource<List<CloudMediaEntity>>> = flow {
        try {
            val conn = requireConnection()
            val configId = currentConfig?.id ?: 0L
            val index = mediaIndex(conn)
            if (page == 0 && configId > 0L) {
                val pruned = cloudMediaDao.deleteMissingRemoteMedia(
                    configId,
                    backend.providerType,
                    index.inAlbum("").map { it.relativePath }
                )
                if (currentConfig?.syncRemoteDeletions == true && pruned.isNotEmpty()) {
                    deleteAppLocalCopies(context, pruned)
                }
            }
            val paged = index.page(page, pageSize).map { it.toEntity(configId) }
            cloudMediaDao.insertAll(paged)
            emit(Resource.Success(paged))
        } catch (e: CancellationException) {
            // Consumer cancelled (e.g. a `first()`/`take()` prefetch). Re-throw so cancellation
            // propagates cleanly — emitting from this catch would violate flow exception
            // transparency (AbortFlowException) and abort the whole prefetch.
            throw e
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    override fun getRemoteFavorites(): Flow<Resource<List<CloudMediaEntity>>> =
        flow { emit(Resource.Success(emptyList<CloudMediaEntity>())) }

    override fun getRemoteTrashed(): Flow<Resource<List<CloudMediaEntity>>> =
        flow { emit(Resource.Success(emptyList<CloudMediaEntity>())) }

    override fun getRemoteArchived(): Flow<Resource<List<CloudMediaEntity>>> =
        flow { emit(Resource.Success(emptyList<CloudMediaEntity>())) }

    // === Albums (folders) ===

    override fun getRemoteAlbums(): Flow<Resource<List<CloudAlbum>>> = flow {
        try {
            val conn = requireConnection()
            val configId = currentConfig?.id ?: 0L
            val albums = CloudTrace.time("${backend.providerType} getRemoteAlbums") {
                val index = mediaIndex(conn)
                index.rootAlbumPaths().map { albumPath ->
                    val stats = index.albumStats(albumPath)
                    CloudAlbum(
                        remoteId = albumPath,
                        providerType = backend.providerType,
                        serverConfigId = configId,
                        name = albumPath.substringAfterLast('/'),
                        assetCount = stats.assetCount,
                        thumbnailAssetId = stats.thumbnailAssetId,
                        isShared = false
                    )
                }
            }
            CloudTrace.d("${backend.providerType} getRemoteAlbums -> ${albums.size} folders")
            emit(Resource.Success(albums))
        } catch (e: CancellationException) {
            // Consumer cancelled (e.g. a `first()`/`take()` prefetch). Re-throw so cancellation
            // propagates cleanly — emitting from this catch would violate flow exception
            // transparency (AbortFlowException) and abort the whole prefetch.
            throw e
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    override fun getRemoteAlbumMedia(albumId: String): Flow<Resource<List<CloudMediaEntity>>> = flow {
        try {
            val conn = requireConnection()
            val configId = currentConfig?.id ?: 0L
            val media = CloudTrace.time("${backend.providerType} getRemoteAlbumMedia '$albumId'") {
                mediaIndex(conn).inAlbum(albumId).map { it.toEntity(configId) }
            }
            CloudTrace.d("${backend.providerType} getRemoteAlbumMedia '$albumId' -> ${media.size} items")
            emit(Resource.Success(media))
        } catch (e: CancellationException) {
            // Consumer cancelled (e.g. a `first()`/`take()` prefetch). Re-throw so cancellation
            // propagates cleanly — emitting from this catch would violate flow exception
            // transparency (AbortFlowException) and abort the whole prefetch.
            throw e
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun createAlbum(name: String): Result<CloudAlbum> = withContext(Dispatchers.IO) {
        try {
            withReconnectOnFailure { backend.mkdir(requireConnection(), name) }
            invalidateMediaIndex()
            Result.success(
                CloudAlbum(
                    remoteId = name,
                    providerType = backend.providerType,
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
        Result.failure(UnsupportedOperationException("${backend.displayName} folders don't support adding by id"))

    // === Mutations ===

    override suspend fun toggleFavorite(remoteId: String, favorite: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            // Network shares have no server-side favorites — persist locally only.
            runCatching {
                val configId = currentConfig?.id ?: error("Not configured")
                cloudMediaDao.updateFavorite(remoteId, backend.providerType, configId, favorite)
            }
        }

    override suspend fun toggleArchive(remoteId: String, archived: Boolean): Result<Unit> =
        Result.failure(UnsupportedOperationException("${backend.displayName} does not support archive"))

    override suspend fun trashAsset(remoteId: String): Result<Unit> = deleteAsset(remoteId)

    override suspend fun restoreAsset(remoteId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("${backend.displayName} does not support trash"))

    override suspend fun deleteAsset(remoteId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val configId = currentConfig?.id ?: error("Not configured")
            withReconnectOnFailure { backend.delete(requireConnection(), remoteId) }
            invalidateMediaIndex()
            cloudMediaDao.delete(remoteId, backend.providerType, configId)
            Result.success(Unit)
        } catch (e: Exception) {
            CloudTrace.w("${backend.providerType} delete '$remoteId' failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun emptyTrash(): Result<Unit> =
        Result.failure(UnsupportedOperationException("${backend.displayName} does not support trash"))

    override suspend fun restoreAllTrash(): Result<Unit> =
        Result.failure(UnsupportedOperationException("${backend.displayName} does not support trash"))

    override suspend fun search(query: String): Result<List<CloudMediaEntity>> = withContext(Dispatchers.IO) {
        try {
            val conn = requireConnection()
            val configId = currentConfig?.id ?: 0L
            val matched = mediaIndex(conn).inAlbum("")
                .filter { it.name.contains(query, ignoreCase = true) }
                .map { it.toEntity(configId) }
            Result.success(matched)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getStorageInfo(): Result<CloudStorageInfo> = withContext(Dispatchers.IO) {
        try {
            val storage = withReconnectOnFailure { backend.storage(requireConnection()) }
                ?: return@withContext Result.failure(UnsupportedOperationException("No storage info"))
            val pct = if (storage.totalBytes > 0)
                storage.usedBytes.toDouble() / storage.totalBytes.toDouble() * 100.0 else 0.0
            Result.success(
                CloudStorageInfo(
                    usedBytes = storage.usedBytes,
                    totalBytes = storage.totalBytes,
                    usedPercentage = pct,
                    usedFormatted = Formatter.formatShortFileSize(context, storage.usedBytes),
                    totalFormatted = Formatter.formatShortFileSize(context, storage.totalBytes)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // === Media-pipeline URLs (loopback bridge) ===

    override fun getThumbnailUrl(remoteId: String, size: ThumbnailSize): String =
        NetFsLoopback.thumbnailUrl(
            backend.providerType, currentConfig?.id ?: 0L, remoteId, size
        )

    override fun getOriginalUrl(remoteId: String): String =
        NetFsLoopback.originalUrl(backend.providerType, currentConfig?.id ?: 0L, remoteId)

    override fun getAuthHeaders(): Map<String, String> = emptyMap() // token is embedded in the loopback URL

    // === NetFsLoopbackSource (called from the loopback server thread) ===

    override fun loopbackSize(path: String): Long =
        withReconnectOnFailure { backend.fileSize(requireConnection(), path) }

    override fun loopbackOpen(path: String, offset: Long): InputStream = withReconnectOnFailure {
        val fileSize = runCatching { backend.fileSize(requireConnection(), path) }.getOrDefault(-1L)
        val cacheFile = if (fileSize > 0) originalCacheFile(path, fileSize) else null

        // Serve from the on-disk original cache when we already have the complete file: avoids
        // re-downloading the whole multi-MB original on every zoom/range request (traces showed the
        // same 16 MB file streamed repeatedly). Range requests skip into the local file.
        if (cacheFile != null && cacheFile.isFile && cacheFile.length() == fileSize) {
            CloudTrace.d("NetFs original cache HIT '$path' offset=$offset (${CloudTrace.bytes(fileSize)})")
            val fis = FileInputStream(cacheFile)
            if (offset > 0) fis.channel.position(offset)
            return@withReconnectOnFailure BufferedInputStream(fis, LOOPBACK_READ_BUFFER_BYTES)
        }

        // Buffer with a large window so consumers reading in small chunks (NanoHTTPD streams the
        // original in 16 KB reads; stdlib copy uses 8 KB) still trigger few, large SMB2/NFS READs
        // instead of hundreds of round-trips — the latter timed out the image pipeline.
        val raw = BufferedInputStream(backend.openRead(requireConnection(), path, offset), LOOPBACK_READ_BUFFER_BYTES)

        // Tee a full read (offset 0, bounded size) into the cache. This also warms the cache during
        // thumbnail generation (which reads the whole file), so a later zoom is an instant local read.
        if (offset == 0L && cacheFile != null && fileSize in 1..ORIGINAL_CACHE_MAX_FILE_BYTES) {
            // Unique temp per stream so concurrent tees of the same file (e.g. thumbnail gen + zoom)
            // don't clobber one another; the first to complete wins the rename, the rest discard.
            val tmp = File(originalCacheDir, "${cacheFile.name}.${System.nanoTime()}.tmp")
            CachingInputStream(raw, cacheFile, tmp, fileSize) { trimOriginalCache() }
        } else raw
    }

    override fun loopbackMime(path: String): String = mimeOf(path)

    override fun loopbackThumbnail(path: String, size: ThumbnailSize): ByteArray? {
        val mime = mimeOf(path)
        val fileSize = runCatching {
            withReconnectOnFailure { backend.fileSize(requireConnection(), path) }
        }.getOrDefault(0L)
        val cacheFile = thumbnailCacheFile(path, size, fileSize)

        // Fast path: a previously generated thumbnail. Network filesystems have no server-side
        // preview, so without this every grid cell re-downloads the entire multi-MB original (the
        // 16 s/file seen in traces) — and Glide + Sketch would each generate their own copy.
        cacheFile.takeIf { it.isFile && it.length() > 0 }?.let { f ->
            runCatching { f.readBytes() }.getOrNull()?.let { cached ->
                CloudTrace.d("NetFs thumb cache HIT $size '$path' (${CloudTrace.bytes(cached.size.toLong())})")
                return cached
            }
        }

        // Single-flight per cache key: when Glide and Sketch request the same thumbnail at once,
        // only one thread reads+decodes the original; the rest reuse the just-written cache file.
        return thumbnailFlights.run(cacheFile.name) {
            cacheFile.takeIf { it.isFile && it.length() > 0 }?.let { f ->
                runCatching { f.readBytes() }.getOrNull()?.let { return@run it }
            }
            val bytes = if (mime.startsWith("video/")) {
                NetFsThumbnailer.fromVideoUrl(context, getOriginalUrl(path), size)
            } else {
                NetFsThumbnailer.fromImage(
                    open = { loopbackOpen(path, 0L) },
                    declaredSize = fileSize,
                    size = size
                )
            }
            if (bytes != null && bytes.isNotEmpty()) {
                runCatching {
                    val tmp = File(thumbCacheDir, "${cacheFile.name}.tmp")
                    tmp.writeBytes(bytes)
                    if (!tmp.renameTo(cacheFile)) {
                        cacheFile.delete(); tmp.renameTo(cacheFile)
                    }
                    CloudTrace.d("NetFs thumb cache STORE $size '$path' (${CloudTrace.bytes(bytes.size.toLong())})")
                }.onFailure { CloudTrace.w("NetFs thumb cache write failed for '$path': ${it.message}") }
            }
            bytes
        }
    }

    private val thumbCacheDir: File by lazy {
        File(context.cacheDir, "netfs_thumb_cache").apply { mkdirs() }
    }

    /** Exact-key single-flight entries are removed as soon as their callers finish. */
    private val thumbnailFlights = NetFsThumbnailSingleFlight()

    /** Cache file keyed by account + path + size + file size (so edits/replacements invalidate). */
    private fun thumbnailCacheFile(path: String, size: ThumbnailSize, fileSize: Long): File {
        val raw = "${backend.providerType.name}|${currentConfig?.id ?: 0L}|$path|${size.name}|$fileSize"
        return File(thumbCacheDir, sha1(raw) + ".jpg")
    }

    private val originalCacheDir: File by lazy {
        File(context.cacheDir, "netfs_orig_cache").apply { mkdirs() }
    }

    /** Cache file for a full original, keyed by account + path + file size (replacements invalidate). */
    private fun originalCacheFile(path: String, fileSize: Long): File {
        val raw = "${backend.providerType.name}|${currentConfig?.id ?: 0L}|$path|$fileSize"
        return File(originalCacheDir, sha1(raw) + ".bin")
    }

    private fun sha1(s: String): String =
        java.security.MessageDigest.getInstance("SHA-1")
            .digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** Evict oldest cached originals (by last access) once the cache exceeds its size cap. */
    @Synchronized
    private fun trimOriginalCache() {
        runCatching {
            val files = originalCacheDir.listFiles()?.filter { it.isFile } ?: return
            var total = files.sumOf { it.length() }
            if (total <= ORIGINAL_CACHE_DIR_CAP_BYTES) return
            files.sortedBy { it.lastModified() }.forEach { f ->
                if (total <= ORIGINAL_CACHE_DIR_CAP_BYTES) return
                total -= f.length()
                f.delete()
            }
        }
    }

    /**
     * Tees a network stream to a temp file as it is read, then atomically promotes it to [target]
     * once the full [expectedSize] has been read (EOF or exact count). A partial/cancelled read
     * discards the temp file, so only complete originals are cached. [onStored] runs after a
     * successful store (used to trim the cache).
     */
    private class CachingInputStream(
        private val delegate: InputStream,
        private val target: File,
        private val tmp: File,
        private val expectedSize: Long,
        private val onStored: () -> Unit
    ) : InputStream() {
        private var out: OutputStream? = runCatching { FileOutputStream(tmp) }.getOrNull()
        private var written = 0L
        private var done = false

        override fun read(): Int {
            val b = delegate.read()
            if (b >= 0) writeByte(b) else finishIfComplete()
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = delegate.read(b, off, len)
            if (n > 0) write(b, off, n) else if (n < 0) finishIfComplete()
            return n
        }

        override fun available(): Int = delegate.available()

        private val one = ByteArray(1)
        private fun writeByte(v: Int) { one[0] = v.toByte(); write(one, 0, 1) }

        private fun write(b: ByteArray, off: Int, n: Int) {
            val o = out ?: return
            try {
                o.write(b, off, n)
                written += n
                if (written >= expectedSize) finishIfComplete()
            } catch (_: Exception) { abort() }
        }

        private fun finishIfComplete() {
            if (done) return
            val o = out ?: return
            out = null
            done = true
            try {
                o.close()
                if (written == expectedSize) {
                    when {
                        target.exists() -> tmp.delete()        // another stream already cached it
                        tmp.renameTo(target) -> {}             // promoted
                        else -> tmp.delete()                   // lost the race / rename failed
                    }
                    onStored()
                } else {
                    tmp.delete()
                }
            } catch (_: Exception) { tmp.delete() }
        }

        private fun abort() {
            done = true
            runCatching { out?.close() }
            out = null
            tmp.delete()
        }

        override fun close() {
            // Cached only if the whole file was read; partial (cancelled/range) reads are discarded.
            if (!done) {
                if (written == expectedSize) finishIfComplete() else abort()
            }
            delegate.close()
        }
    }

    // === Share links (unsupported) ===

    override suspend fun createShareLink(assetIds: List<String>, expiresAt: Long?): Result<String> =
        Result.failure(UnsupportedOperationException("${backend.displayName} does not support share links"))

    override fun getSharedLinks(): Flow<Resource<List<SharedLinkInfo>>> =
        flow { emit(Resource.Success(emptyList())) }

    override suspend fun deleteSharedLink(linkId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("${backend.displayName} does not support share links"))

    override suspend fun updateSharedLink(linkId: String, updates: Map<String, Any>): Result<Unit> =
        Result.failure(UnsupportedOperationException("${backend.displayName} does not support share links"))

    // === Sync ===

    override fun deterministicRemoteId(localMedia: Media, targetPath: String?): String =
        (targetPath?.trimEnd('/')?.ifEmpty { null } ?: "Photos") + "/${localMedia.label}"

    override suspend fun uploadAsset(localMedia: Media, targetPath: String?): Result<CloudMediaEntity> =
        withContext(Dispatchers.IO) {
            mutationMutex.withLock {
                writeAsset(localMedia, deterministicRemoteId(localMedia, targetPath), null, false)
            }
        }

    override suspend fun uploadAsset(
        localMedia: Media,
        targetPath: String?,
        checksum: String
    ): Result<CloudMediaEntity> = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            writeAsset(localMedia, deterministicRemoteId(localMedia, targetPath), checksum, false)
        }
    }

    override suspend fun copyToAlbum(
        media: Media,
        remoteAlbumId: String,
        conflictPolicy: RemoteNameConflictPolicy,
        checksum: String?,
        continuationRemoteId: String?
    ): RemoteAlbumCopyResult = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            var operationConnection: NetFsConnection? = null
            try {
                val conn = requireConnection().also { operationConnection = it }
                val initialPath = remoteAlbumFilePath(remoteAlbumId, media.label)
                if (backend.exists(conn, initialPath) && checksum != null &&
                    remoteHash(conn, initialPath).equals(checksum, ignoreCase = true)
                ) {
                    return@withLock RemoteAlbumCopyResult(
                        state = RemoteAlbumCopyState.ALREADY_PRESENT,
                        remoteId = initialPath
                    )
                }
                var remotePath = initialPath
                var copyNumber = 1
                while (backend.exists(conn, remotePath)) {
                    remotePath = remoteAlbumFilePath(
                        remoteAlbumId,
                        remoteCopyFileName(media.label, copyNumber++)
                    )
                }
                writeAsset(media, remotePath, checksum, true).fold(
                    onSuccess = {
                        RemoteAlbumCopyResult(
                            state = RemoteAlbumCopyState.COPIED,
                            remoteId = it.remoteId
                        )
                    },
                    onFailure = {
                        RemoteAlbumCopyResult(
                            state = RemoteAlbumCopyState.FAILED,
                            message = copyFailureMessage(it),
                            retryable = isRetryableCopyFailure(it)
                        )
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isNetFsConnectionFailure(e)) operationConnection?.let { resetConnection(it) }
                RemoteAlbumCopyResult(
                    state = RemoteAlbumCopyState.FAILED,
                    message = copyFailureMessage(e),
                    retryable = isRetryableCopyFailure(e)
                )
            }
        }
    }

    private suspend fun writeAsset(
        localMedia: Media,
        remotePath: String,
        checksum: String?,
        cleanupOnFailure: Boolean,
        reconnectAttempted: Boolean = false
    ): Result<CloudMediaEntity> = try {
        var conn = requireConnection()
        val configId = currentConfig?.id ?: throw IllegalStateException("Not configured")
        val input = context.contentResolver.openInputStream(localMedia.getUri())
            ?: return Result.failure(Exception("Cannot open media file"))
        val size = runCatching {
            context.contentResolver.openAssetFileDescriptor(localMedia.getUri(), "r")?.use { it.length }
        }.getOrNull() ?: -1L
        try {
            input.use { backend.write(conn, remotePath, it, size) }
        } catch (e: Exception) {
            var verified = remoteContentMatches(conn, remotePath, localMedia, size, checksum)
            if (!verified && !reconnectAttempted && isNetFsConnectionFailure(e)) {
                resetConnection(conn)
                conn = requireConnection()
                verified = remoteContentMatches(conn, remotePath, localMedia, size, checksum)
                if (!verified) {
                    return writeAsset(localMedia, remotePath, checksum, cleanupOnFailure, true)
                }
            }
            if (!verified) {
                if (cleanupOnFailure) runCatching { backend.delete(conn, remotePath) }
                throw e
            }
        }
        if (checksum != null && !remoteContentMatches(conn, remotePath, localMedia, size, checksum)) {
            resetConnection(conn)
            conn = requireConnection()
            if (!remoteContentMatches(conn, remotePath, localMedia, size, checksum)) {
                if (cleanupOnFailure) runCatching { backend.delete(conn, remotePath) }
                return Result.failure(Exception("Remote copy verification failed"))
            }
        } else if (checksum == null && size > 0L) {
            var remoteSize = runCatching { backend.fileSize(conn, remotePath) }.getOrNull()
            if (remoteSize == null) {
                resetConnection(conn)
                conn = requireConnection()
                remoteSize = runCatching { backend.fileSize(conn, remotePath) }.getOrNull()
            }
            if (remoteSize != size) {
                if (cleanupOnFailure) runCatching { backend.delete(conn, remotePath) }
                return Result.failure(Exception("Remote copy size verification failed"))
            }
        }
        invalidateMediaIndex()
        val entity = CloudMediaEntity(
            remoteId = remotePath,
            providerType = backend.providerType,
            serverConfigId = configId,
            label = remotePath.substringAfterLast('/'),
            path = remotePath,
            relativePath = remotePath.substringBeforeLast('/'),
            mimeType = localMedia.mimeType,
            timestamp = System.currentTimeMillis(),
            size = if (size > 0) size else 0L,
            syncState = SyncState.SYNCED,
            localCopyPath = localMedia.getUri().toString(),
            contentHash = checksum
        )
        cloudMediaDao.insert(entity)
        Result.success(entity)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun remoteContentMatches(
        conn: NetFsConnection,
        remotePath: String,
        localMedia: Media,
        expectedSize: Long,
        checksum: String?
    ): Boolean = runCatching {
        if (!backend.exists(conn, remotePath)) return@runCatching false
        if (expectedSize > 0L && backend.fileSize(conn, remotePath) != expectedSize) {
            return@runCatching false
        }
        val expectedHash = checksum ?: context.contentResolver.openInputStream(localMedia.getUri())
            ?.use(::contentSha1) ?: return@runCatching false
        remoteHash(conn, remotePath).equals(expectedHash, ignoreCase = true)
    }.getOrDefault(false)

    private fun remoteHash(conn: NetFsConnection, remotePath: String): String =
        backend.openRead(conn, remotePath, 0L).use(::contentSha1)

    private fun copyFailureMessage(error: Throwable): String {
        val message = error.message.orEmpty().lowercase()
        return if (listOf("permission", "access denied", "nfsstatus:13", "read-only")
                .any(message::contains)
        ) "Remote folder is not writable"
        else error.message ?: "Remote copy failed"
    }

    private fun isRetryableCopyFailure(error: Throwable): Boolean {
        val message = error.message.orEmpty().lowercase()
        if (error is IllegalArgumentException ||
            error is IllegalStateException && "not configured" in message
        ) return false
        return listOf(
            "permission",
            "access denied",
            "authentication",
            "credential",
            "nfsstatus:13",
            "read-only"
        ).none(message::contains)
    }

    override suspend fun downloadAsset(remoteId: String): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val ext = remoteId.substringAfterLast('.', "")
            val cacheFile = File(context.cacheDir, "netfs_${remoteId.hashCode()}.$ext")
            withReconnectOnFailure {
                backend.openRead(requireConnection(), remoteId, 0L).use { input ->
                    cacheFile.outputStream().use { input.copyTo(it) }
                }
            }
            Result.success(cacheFile.toUri())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSyncDelta(timestamp: Long, reconcileIndex: Boolean): Result<SyncDelta> =
        withContext(Dispatchers.IO) {
            try {
                val conn = requireConnection()
                val configId = currentConfig?.id ?: 0L
                invalidateMediaIndex()
                // The index is always a complete recursive listing, so the caller can
                // prune the cache against it on every sync — that's how remote deletions
                // reach the timeline between full scans.
                val entities = mediaIndex(conn).inAlbum("").map { it.toEntity(configId) }
                Result.success(
                    SyncDelta(
                        items = entities,
                        completeRemoteIds = entities.map { it.remoteId }
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
            val configId = currentConfig?.id ?: throw IllegalStateException("Not configured")
            val remotePath = deterministicRemoteId(localMedia, targetPath)
            val remoteSize = runInterruptible {
                withReconnectOnFailure { backend.fileSize(requireConnection(), remotePath) }
            }
            if (localMedia.size > 0L && remoteSize != localMedia.size) {
                return@withContext Result.success(false)
            }
            val remoteHash = runInterruptible {
                withReconnectOnFailure {
                    backend.openRead(requireConnection(), remotePath, 0L).use(::contentSha1)
                }
            }
            if (cloudMediaDao.updateContentHash(remotePath, backend.providerType, configId, remoteHash) == 0) {
                cloudMediaDao.insert(
                    CloudMediaEntity(
                        remoteId = remotePath,
                        providerType = backend.providerType,
                        serverConfigId = configId,
                        label = localMedia.label,
                        path = remotePath,
                        relativePath = remotePath.substringBeforeLast('/'),
                        mimeType = localMedia.mimeType,
                        timestamp = System.currentTimeMillis(),
                        size = remoteSize,
                        syncState = SyncState.REMOTE_ONLY,
                        contentHash = remoteHash
                    )
                )
            }
            Result.success(remoteHash.equals(contentHash, ignoreCase = true))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * SMB/NFS have no content-hash index, so an asset is "already uploaded" when a file
     * with the same name and byte size already exists at the deterministic upload target
     * (mirrors the path built by [uploadAsset]). This lets the backup worker skip files
     * it already pushed instead of re-transferring the whole album every run.
     */
    override suspend fun remoteExists(localMedia: Media, targetPath: String?): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val remotePath = deterministicRemoteId(localMedia, targetPath)
                val remoteSize = runCatching {
                    withReconnectOnFailure { backend.fileSize(requireConnection(), remotePath) }
                }.getOrNull() ?: return@withContext false
                if (remoteSize <= 0L) return@withContext false
                val localSize = runCatching {
                    context.contentResolver.openAssetFileDescriptor(localMedia.getUri(), "r")?.use { it.length }
                }.getOrNull() ?: return@withContext true // present remotely; can't stat local, treat as done
                remoteSize == localSize
            } catch (_: Exception) {
                false
            }
        }

    // === Helpers ===

    private val mediaExtensions = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heif", "heic", "avif",
        "mp4", "mkv", "mov", "avi", "webm", "3gp"
    )

    private fun mediaIndex(conn: NetFsConnection): NetFsMediaIndex {
        mediaIndex?.let { return it }
        return synchronized(mediaIndexLock) {
            mediaIndex ?: run {
                val generation = connectionGeneration
                val index = CloudTrace.time("${backend.providerType} scan media index") {
                    NetFsMediaIndex(scanMedia())
                }
                check(generation == connectionGeneration && connection === conn) {
                    "Network filesystem connection changed during indexing"
                }
                mediaIndex = index
                index
            }
        }
    }

    private fun scanMedia(): List<NetFsEntry> {
        val media = ArrayList<NetFsEntry>()
        val pending = java.util.ArrayDeque<String>().apply { add("") }
        val visited = HashSet<String>()
        while (pending.isNotEmpty()) {
            val path = pending.removeFirst()
            if (!visited.add(path)) continue
            listDirWithRetry(path).forEach { entry ->
                if (entry.isDirectory) {
                    pending.addLast(entry.relativePath)
                } else if (entry.name.substringAfterLast('.', "").lowercase() in mediaExtensions) {
                    media.add(entry)
                }
            }
        }
        return media.sortedBy { it.relativePath }
    }

    private fun listDirWithRetry(path: String): List<NetFsEntry> {
        var failure: Exception? = null
        var reconnectAttempted = false
        repeat(SCAN_LIST_RETRIES) { attempt ->
            try {
                // Resolved per attempt: after a drop the next call re-dials on a fresh session.
                return backend.listDir(requireConnection(), path)
            } catch (e: Exception) {
                failure = e
                CloudTrace.w(
                    "${backend.providerType} scan failed at '$path' " +
                        "(attempt ${attempt + 1}/$SCAN_LIST_RETRIES): ${e.message}"
                )
                when {
                    !reconnectAttempted && isNetFsConnectionFailure(e) -> {
                        // A dead session can't heal by re-reading — drop it so the next
                        // attempt re-dials instead of burning retries on the same socket.
                        reconnectAttempted = true
                        dropConnection()
                    }
                    attempt + 1 < SCAN_LIST_RETRIES -> {
                        try {
                            Thread.sleep(SCAN_RETRY_DELAY_MILLIS * (attempt + 1))
                        } catch (interrupted: InterruptedException) {
                            Thread.currentThread().interrupt()
                            throw interrupted
                        }
                    }
                }
            }
        }
        throw failure ?: IllegalStateException("Unable to list '$path'")
    }

    /**
     * Runs [block] on the live session; on a connection-flavored failure, drops the
     * session and retries once — the next [requireConnection] inside [block] re-dials
     * on a fresh session. The second failure propagates to the caller.
     */
    private fun <T> withReconnectOnFailure(block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        if (!isNetFsConnectionFailure(e)) throw e
        CloudTrace.w(
            "${backend.providerType} op failed on a dead session (${e.message}); reconnecting"
        )
        dropConnection()
        block()
    }

    private fun mimeOf(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "heif", "heic" -> "image/heif"
        "avif" -> "image/avif"
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        "avi" -> "video/x-msvideo"
        "webm" -> "video/webm"
        "3gp" -> "video/3gpp"
        else -> "application/octet-stream"
    }

    private companion object {
        const val SCAN_LIST_RETRIES = 3
        const val SCAN_RETRY_DELAY_MILLIS = 250L

        val TRANSPORT_KINDS = listOf(
            NetworkCapabilities.TRANSPORT_WIFI,
            NetworkCapabilities.TRANSPORT_CELLULAR,
            NetworkCapabilities.TRANSPORT_ETHERNET,
            NetworkCapabilities.TRANSPORT_BLUETOOTH,
            NetworkCapabilities.TRANSPORT_VPN,
            NetworkCapabilities.TRANSPORT_USB
        )

        // ~1 MB: large enough that smbj fills it with a single SMB2 READ (its typical negotiated max),
        // collapsing the per-file round-trips that were timing out the image pipeline.
        const val LOOPBACK_READ_BUFFER_BYTES = 1024 * 1024

        // Don't cache originals larger than this on disk (e.g. long videos) — keep the cache for
        // photos/short clips where a full re-download is the dominant cost.
        const val ORIGINAL_CACHE_MAX_FILE_BYTES = 256L * 1024 * 1024
        // Total on-disk budget for cached originals; oldest are evicted past this.
        const val ORIGINAL_CACHE_DIR_CAP_BYTES = 1024L * 1024 * 1024
    }

    private fun NetFsEntry.toEntity(configId: Long): CloudMediaEntity {
        val ts = if (lastModified > 0) lastModified else System.currentTimeMillis()
        return CloudMediaEntity(
            remoteId = relativePath,
            providerType = backend.providerType,
            serverConfigId = configId,
            label = name,
            path = relativePath,
            relativePath = relativePath.substringBeforeLast('/', ""),
            mimeType = mimeOf(name),
            timestamp = ts,
            size = size,
            syncState = SyncState.REMOTE_ONLY,
            thumbnailUrl = NetFsLoopback.thumbnailUrl(
                backend.providerType, configId, relativePath, ThumbnailSize.PREVIEW
            ),
            originalUrl = NetFsLoopback.originalUrl(backend.providerType, configId, relativePath)
        )
    }
}
