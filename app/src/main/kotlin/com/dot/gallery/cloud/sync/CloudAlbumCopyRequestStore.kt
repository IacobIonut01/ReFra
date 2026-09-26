/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.sync

import android.content.Context
import com.dot.gallery.cloud.core.CloudAlbumIdentity
import com.dot.gallery.cloud.core.capabilities.RemoteNameConflictPolicy
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.presentation.util.printWarn
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
enum class CloudAlbumTransferMode {
    COPY,
    MOVE
}

@Serializable
enum class CloudAlbumCopyItemState {
    PENDING,
    COPIED,
    ALREADY_PRESENT,
    ATTACH_PENDING,
    SOURCE_DELETE_PENDING,
    SOURCE_RETAINED,
    MOVED,
    FAILED
}

internal fun CloudAlbumCopyItemState.isTransferFailure(): Boolean =
    this == CloudAlbumCopyItemState.FAILED ||
        this == CloudAlbumCopyItemState.ATTACH_PENDING ||
        this == CloudAlbumCopyItemState.SOURCE_DELETE_PENDING ||
        this == CloudAlbumCopyItemState.SOURCE_RETAINED

internal fun shouldBeginCloudMoveCleanup(
    mode: CloudAlbumTransferMode,
    states: List<CloudAlbumCopyItemState>
): Boolean = mode == CloudAlbumTransferMode.MOVE && states.none {
    it == CloudAlbumCopyItemState.FAILED || it == CloudAlbumCopyItemState.ATTACH_PENDING
}

@Serializable
data class CloudAlbumCopyItem(
    val media: Media,
    val state: CloudAlbumCopyItemState = CloudAlbumCopyItemState.PENDING,
    val remoteId: String? = null,
    val message: String = "",
    val retryable: Boolean = false
)

@Serializable
data class CloudAlbumCopyRequest(
    val id: String,
    val albumId: Long,
    val destination: CloudAlbumIdentity,
    val destinationLabel: String,
    val mode: CloudAlbumTransferMode = CloudAlbumTransferMode.COPY,
    val conflictPolicy: RemoteNameConflictPolicy,
    val createdAt: Long,
    val items: List<CloudAlbumCopyItem>
)

@Singleton
class CloudAlbumCopyRequestStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val directory = File(context.noBackupFilesDir, DIRECTORY)
    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun create(
        albumId: Long,
        destination: CloudAlbumIdentity,
        destinationLabel: String,
        media: List<Media>,
        mode: CloudAlbumTransferMode = CloudAlbumTransferMode.COPY,
        conflictPolicy: RemoteNameConflictPolicy = RemoteNameConflictPolicy.KEEP_BOTH
    ): CloudAlbumCopyRequest = withContext(Dispatchers.IO) {
        mutex.withLock {
            pruneLocked(System.currentTimeMillis())
            val request = CloudAlbumCopyRequest(
                id = UUID.randomUUID().toString(),
                albumId = albumId,
                destination = destination,
                destinationLabel = destinationLabel,
                mode = mode,
                conflictPolicy = conflictPolicy,
                createdAt = System.currentTimeMillis(),
                items = media.map { CloudAlbumCopyItem(it) }
            )
            writeLocked(request)
            request
        }
    }

    suspend fun read(id: String): CloudAlbumCopyRequest? = withContext(Dispatchers.IO) {
        mutex.withLock {
            requestFile(id).takeIf(File::isFile)?.let {
                runCatching { json.decodeFromString<CloudAlbumCopyRequest>(it.readText()) }
                    .onFailure { e ->
                        printWarn("cloud.album-copy", "stored copy request could not be decoded: $e")
                    }
                    .getOrNull()
            }
        }
    }

    suspend fun update(
        id: String,
        transform: (CloudAlbumCopyRequest) -> CloudAlbumCopyRequest
    ): CloudAlbumCopyRequest? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = requestFile(id).takeIf(File::isFile)?.let {
                runCatching { json.decodeFromString<CloudAlbumCopyRequest>(it.readText()) }
                    .onFailure { e ->
                        printWarn("cloud.album-copy", "stored copy request could not be decoded: $e")
                    }
                    .getOrNull()
            } ?: return@withLock null
            transform(current).also(::writeLocked)
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock { requestFile(id).delete() }
    }

    private fun writeLocked(request: CloudAlbumCopyRequest) {
        check(directory.exists() || directory.mkdirs())
        val target = requestFile(request.id)
        val temporary = File(directory, ".${request.id}.tmp")
        temporary.writeText(json.encodeToString(request))
        runCatching {
            java.nio.file.Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        }.getOrElse {
            check(!target.exists() || target.delete())
            check(temporary.renameTo(target))
        }
    }

    private fun requestFile(id: String): File {
        require(ID_PATTERN.matches(id))
        return File(directory, "$id.json")
    }

    private fun pruneLocked(now: Long) {
        if (!directory.isDirectory) return
        directory.listFiles().orEmpty()
            .filter { it.isFile && now - it.lastModified() > RETENTION_MS }
            .forEach(File::delete)
    }

    companion object {
        private const val DIRECTORY = "cloud-album-copy"
        private const val RETENTION_MS = 7L * 24L * 60L * 60L * 1000L
        private val ID_PATTERN = Regex("[0-9a-fA-F-]{36}")
    }
}
