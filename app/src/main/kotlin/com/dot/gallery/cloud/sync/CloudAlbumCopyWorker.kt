/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dot.gallery.R
import com.dot.gallery.cloud.core.CloudUri
import com.dot.gallery.cloud.core.ProviderCapability
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.cloud.core.isUnsupportedSameAccountCloudMove
import com.dot.gallery.cloud.core.shouldDeleteCloudSourceAfterTransfer
import com.dot.gallery.cloud.core.capabilities.RemoteAlbumCopyState
import com.dot.gallery.cloud.core.capabilities.RemoteAlbumWriteProvider
import com.dot.gallery.cloud.core.capabilities.RemoteMediaProvider
import com.dot.gallery.cloud.data.dao.CloudMediaDao
import com.dot.gallery.cloud.data.dao.CloudServerConfigDao
import com.dot.gallery.cloud.data.repository.CloudRepository
import com.dot.gallery.cloud.offline.OfflineModeManager
import com.dot.gallery.cloud.util.CloudMediaDownloader
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isCloud
import com.dot.gallery.feature_node.presentation.util.printWarn
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

fun WorkManager.enqueueCloudAlbumCopy(requestId: String): UUID {
    val request = OneTimeWorkRequestBuilder<CloudAlbumCopyWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10L, TimeUnit.SECONDS)
        .setInputData(workDataOf(CloudAlbumCopyWorker.KEY_REQUEST_ID to requestId))
        .addTag(CloudAlbumCopyWorker.TAG)
        .addTag("${CloudAlbumCopyWorker.TAG}:$requestId")
        .build()
    enqueue(request)
    return request.id
}

@HiltWorker
class CloudAlbumCopyWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val store: CloudAlbumCopyRequestStore,
    private val repository: CloudRepository,
    private val registry: ProviderRegistry,
    private val cloudMediaDao: CloudMediaDao,
    private val configDao: CloudServerConfigDao,
    private val offlineModeManager: OfflineModeManager
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): ListenableWorker.Result = withContext(Dispatchers.IO) {
        val requestId = inputData.getString(KEY_REQUEST_ID)
            ?: return@withContext failure("Missing copy request")
        var request = store.read(requestId)
            ?: return@withContext failure("Copy request is unavailable")
        val destination = request.destination
        if (!destination.matches(request.albumId)) {
            return@withContext failure("Invalid cloud album destination")
        }
        val config = configDao.getById(destination.serverConfigId)
            ?: return@withContext failure("Cloud account is unavailable")
        if (!config.isActive || config.providerType != destination.providerType) {
            return@withContext failure("Cloud account is unavailable")
        }
        if (config.readOnlyMode) return@withContext failure("Cloud account is read-only")
        if (offlineModeManager.forceOffline.value) return@withContext failure("Offline mode is enabled")
        validateMoveSources(request)?.let { validation ->
            return@withContext if (validation.retryable) retryOrFail(validation.message)
            else failure(validation.message)
        }
        val accountProvider = registry.getByConfigId(destination.serverConfigId)
            ?.takeIf { it.providerType == destination.providerType }
            ?: return@withContext retryOrFail("Cloud account is not connected")
        val provider = accountProvider as? RemoteAlbumWriteProvider
            ?: return@withContext failure("Cloud account does not support album copies")
        if (ProviderCapability.ALBUM_WRITE !in provider.capabilities) {
            return@withContext failure("Cloud account does not support album copies")
        }
        if (!provider.isAvailable) return@withContext retryOrFail("Cloud account is not connected")
        if (request.items.isEmpty()) {
            store.delete(requestId)
            return@withContext ListenableWorker.Result.success(summaryData(request))
        }

        runCatching { setForeground(foregroundInfo(request, 0)) }
        setProgress(progressData(request, 0))
        request = copyItems(requestId, request)
        if (shouldBeginCloudMoveCleanup(request.mode, request.items.map { it.state })) {
            request = deleteCloudSources(requestId, request)
        }
        finishRequest(requestId, request)
    }

    private data class MoveSourceValidation(val message: String, val retryable: Boolean)

    private data class MaterializedMedia(val media: Media, val temporaryFile: File? = null)

    private class SourceMaterializationException(
        message: String,
        val retryable: Boolean
    ) : IOException(message)

    private suspend fun validateMoveSources(request: CloudAlbumCopyRequest): MoveSourceValidation? {
        if (request.mode != CloudAlbumTransferMode.MOVE) return null
        for (item in request.items) {
            if (!item.media.isCloud) continue
            val source = CloudUri.parse(item.media.getUri().toString())
                ?: return MoveSourceValidation("Cloud source is invalid", false)
            if (source.configId <= 0L) {
                return MoveSourceValidation("Cloud source is missing its account", false)
            }
            if (isUnsupportedSameAccountCloudMove(source, request.destination, item.media.label)) {
                return MoveSourceValidation(
                    "Moving within the same cloud account is not supported; use Copy instead",
                    false
                )
            }
            val config = configDao.getById(source.configId)
                ?: return MoveSourceValidation("Source cloud account is unavailable", false)
            if (!config.isActive || config.providerType != source.providerType) {
                return MoveSourceValidation("Source cloud account is unavailable", false)
            }
            if (config.readOnlyMode) {
                return MoveSourceValidation("Source cloud account is read-only", false)
            }
            val provider = registry.getByConfigId(source.configId)
                ?.takeIf { it.providerType == source.providerType } as? RemoteMediaProvider
                ?: return MoveSourceValidation("Source cloud account is not connected", true)
            if (!provider.isAvailable) {
                return MoveSourceValidation("Source cloud account is not connected", true)
            }
        }
        return null
    }

    private suspend fun copyItems(
        requestId: String,
        initial: CloudAlbumCopyRequest
    ): CloudAlbumCopyRequest {
        var request = initial
        for (index in request.items.indices) {
            currentCoroutineContext().ensureActive()
            val item = request.items[index]
            if (item.state == CloudAlbumCopyItemState.COPIED ||
                item.state == CloudAlbumCopyItemState.ALREADY_PRESENT ||
                item.state == CloudAlbumCopyItemState.SOURCE_DELETE_PENDING ||
                item.state == CloudAlbumCopyItemState.SOURCE_RETAINED ||
                item.state == CloudAlbumCopyItemState.MOVED ||
                item.state == CloudAlbumCopyItemState.FAILED && !item.retryable
            ) continue
            var temporaryFile: File? = null
            val updated = try {
                val materialized = materialize(item.media, requestId, index)
                temporaryFile = materialized.temporaryFile
                val checksum = computeSha1(materialized.media)
                    ?: throw SourceMaterializationException("Could not read source media", false)
                val result = repository.copyAssetToAlbum(
                    type = request.destination.providerType,
                    configId = request.destination.serverConfigId,
                    remoteAlbumId = request.destination.remoteId,
                    localMedia = materialized.media,
                    conflictPolicy = request.conflictPolicy,
                    checksum = checksum,
                    continuationRemoteId = item.remoteId
                )
                if (item.media.isCloud && result.remoteId != null &&
                    result.state != RemoteAlbumCopyState.FAILED
                ) {
                    clearStagedLocalCopyPath(result.remoteId, request)
                }
                when (result.state) {
                    RemoteAlbumCopyState.COPIED -> item.copy(
                        state = CloudAlbumCopyItemState.COPIED,
                        remoteId = result.remoteId,
                        message = "",
                        retryable = false
                    )
                    RemoteAlbumCopyState.ALREADY_PRESENT -> item.copy(
                        state = CloudAlbumCopyItemState.ALREADY_PRESENT,
                        remoteId = result.remoteId,
                        message = "",
                        retryable = false
                    )
                    RemoteAlbumCopyState.ATTACH_PENDING -> item.copy(
                        state = CloudAlbumCopyItemState.ATTACH_PENDING,
                        remoteId = result.remoteId,
                        message = result.message,
                        retryable = result.retryable
                    )
                    RemoteAlbumCopyState.FAILED -> item.copy(
                        state = CloudAlbumCopyItemState.FAILED,
                        remoteId = result.remoteId,
                        message = result.message,
                        retryable = result.retryable
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SourceMaterializationException) {
                item.copy(
                    state = CloudAlbumCopyItemState.FAILED,
                    message = e.message ?: "Could not read source media",
                    retryable = e.retryable
                )
            } catch (e: Exception) {
                item.copy(
                    state = CloudAlbumCopyItemState.FAILED,
                    message = e.message ?: "Cloud transfer failed",
                    retryable = item.media.isCloud
                )
            } finally {
                temporaryFile?.delete()
            }
            request = publishItem(requestId, request, index, updated)
        }
        return request
    }

    private suspend fun clearStagedLocalCopyPath(
        remoteId: String,
        request: CloudAlbumCopyRequest
    ) {
        val entity = cloudMediaDao.getByRemoteId(
            remoteId,
            request.destination.providerType,
            request.destination.serverConfigId
        ) ?: return
        val stagedPrefix = File(appContext.cacheDir, "cloud-transfer-").absolutePath
        if (entity.localCopyPath.removePrefix("file://").startsWith(stagedPrefix)) {
            cloudMediaDao.clearLocalCopyPath(
                remoteId,
                request.destination.providerType,
                request.destination.serverConfigId
            )
        }
    }

    private fun materialize(media: Media, requestId: String, index: Int): MaterializedMedia {
        val uri = runCatching { media.getUri() }.getOrNull()
            ?: throw SourceMaterializationException("Source media is not readable", false)
        if (!media.isCloud) {
            if (uri.scheme != "content" && uri.scheme != "file") {
                throw SourceMaterializationException("Source media is not readable", false)
            }
            return MaterializedMedia(media)
        }
        val source = CloudUri.parse(uri.toString())
            ?: throw SourceMaterializationException("Cloud source is invalid", false)
        if (source.configId <= 0L) {
            throw SourceMaterializationException("Cloud source is missing its account", false)
        }
        val provider = registry.getByConfigId(source.configId)
            ?.takeIf { it.providerType == source.providerType } as? RemoteMediaProvider
            ?: throw SourceMaterializationException("Source cloud account is not connected", true)
        if (!provider.isAvailable) {
            throw SourceMaterializationException("Source cloud account is not connected", true)
        }
        val extension = media.label.substringAfterLast('.', "tmp")
            .take(12).filter(Char::isLetterOrDigit).ifBlank { "tmp" }
        val temporary = File.createTempFile(
            "cloud-transfer-${requestId.take(8)}-$index-",
            ".$extension",
            appContext.cacheDir
        )
        try {
            val input = CloudMediaDownloader.downloadCloudMediaExact(uri)
                ?: throw SourceMaterializationException("Could not download cloud source", true)
            input.use { sourceInput ->
                temporary.outputStream().use { output ->
                    sourceInput.copyTo(output)
                    output.flush()
                    output.fd.sync()
                }
            }
            if (temporary.length() <= 0L) {
                throw SourceMaterializationException("Cloud source is empty", false)
            }
            val sourceMedia = media as? Media.UriMedia
                ?: throw SourceMaterializationException("Cloud source type is unsupported", false)
            return MaterializedMedia(
                media = sourceMedia.copy(
                    uri = temporary.toUri(),
                    path = temporary.absolutePath,
                    size = temporary.length()
                ),
                temporaryFile = temporary
            )
        } catch (e: Exception) {
            temporary.delete()
            throw e
        }
    }

    private suspend fun deleteCloudSources(
        requestId: String,
        initial: CloudAlbumCopyRequest
    ): CloudAlbumCopyRequest {
        var request = initial
        for (index in request.items.indices) {
            currentCoroutineContext().ensureActive()
            val item = request.items[index]
            if (!item.media.isCloud || item.state !in setOf(
                    CloudAlbumCopyItemState.COPIED,
                    CloudAlbumCopyItemState.ALREADY_PRESENT,
                    CloudAlbumCopyItemState.SOURCE_DELETE_PENDING
                )
            ) continue
            val source = CloudUri.parse(item.media.getUri().toString())
            val sourceConfig = source?.takeIf { it.configId > 0L }?.let {
                configDao.getById(it.configId)
            }
            val updated = when {
                source == null || source.configId <= 0L || sourceConfig == null ||
                    !sourceConfig.isActive || sourceConfig.providerType != source.providerType -> item.copy(
                    state = CloudAlbumCopyItemState.SOURCE_RETAINED,
                    message = "Destination is ready, but the cloud source identity is invalid",
                    retryable = false
                )
                sourceConfig.readOnlyMode -> item.copy(
                    state = CloudAlbumCopyItemState.SOURCE_RETAINED,
                    message = "Destination is ready, but the source cloud account is read-only",
                    retryable = false
                )
                !shouldDeleteCloudSourceAfterTransfer(
                    source,
                    request.destination,
                    item.remoteId
                ) -> item.copy(
                    state = CloudAlbumCopyItemState.SOURCE_RETAINED,
                    message = "Destination is ready, but the source asset is also the destination asset",
                    retryable = false
                )
                else -> repository.deleteAsset(
                    source.providerType,
                    source.configId,
                    source.remoteId
                ).fold(
                    onSuccess = {
                        item.copy(
                            state = CloudAlbumCopyItemState.MOVED,
                            message = "",
                            retryable = false
                        )
                    },
                    onFailure = { error ->
                        if (isSourceAlreadyAbsent(error)) {
                            cloudMediaDao.delete(
                                source.remoteId,
                                source.providerType,
                                source.configId
                            )
                            item.copy(
                                state = CloudAlbumCopyItemState.MOVED,
                                message = "",
                                retryable = false
                            )
                        } else {
                            item.copy(
                                state = CloudAlbumCopyItemState.SOURCE_DELETE_PENDING,
                                message = sourceDeleteFailureMessage(error),
                                retryable = isRetryableSourceDelete(error)
                            )
                        }
                    }
                )
            }
            request = publishItem(requestId, request, index, updated)
        }
        return request
    }

    private suspend fun publishItem(
        requestId: String,
        request: CloudAlbumCopyRequest,
        index: Int,
        item: CloudAlbumCopyItem
    ): CloudAlbumCopyRequest {
        val updated = updateItem(request, index, item)
        store.update(requestId) { updated }
        setProgress(progressData(updated, index + 1))
        runCatching { setForeground(foregroundInfo(updated, index + 1)) }
        return updated
    }

    private suspend fun finishRequest(
        requestId: String,
        request: CloudAlbumCopyRequest
    ): ListenableWorker.Result {
        val retryable = request.items.any {
            it.state.isTransferFailure() && it.retryable
        }
        val failed = request.items.any { it.state.isTransferFailure() }
        return when {
            retryable && runAttemptCount + 1 < MAX_ATTEMPTS -> ListenableWorker.Result.retry()
            failed -> ListenableWorker.Result.failure(summaryData(request))
            else -> {
                store.delete(requestId)
                ListenableWorker.Result.success(summaryData(request))
            }
        }
    }

    private fun isSourceAlreadyAbsent(error: Throwable): Boolean {
        val message = error.message.orEmpty().lowercase()
        return listOf(
            "404",
            "not found",
            "no such file",
            "object_name_not_found",
            "nfsstatus:2"
        ).any(message::contains)
    }

    private fun sourceDeleteFailureMessage(error: Throwable): String {
        val message = error.message.orEmpty().lowercase()
        return if (listOf("permission", "access denied", "forbidden", "nfsstatus:13", "read-only")
                .any(message::contains)
        ) "Destination is ready, but the source could not be removed"
        else "Destination is ready, but source removal failed; try again"
    }

    private fun isRetryableSourceDelete(error: Throwable): Boolean {
        val message = error.message.orEmpty().lowercase()
        return listOf(
            "permission",
            "access denied",
            "forbidden",
            "authentication",
            "nfsstatus:13",
            "read-only"
        )
            .none(message::contains)
    }

    private fun updateItem(
        request: CloudAlbumCopyRequest,
        index: Int,
        item: CloudAlbumCopyItem
    ): CloudAlbumCopyRequest = request.copy(
        items = request.items.toMutableList().apply { set(index, item) }
    )

    private suspend fun computeSha1(media: Media): String? = try {
        appContext.contentResolver.openInputStream(media.getUri())?.use { input ->
            val digest = MessageDigest.getInstance("SHA-1")
            val buffer = ByteArray(8192)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        printWarn("worker.album-copy", "checksum digest failed: $e")
        null
    }

    private fun progressData(request: CloudAlbumCopyRequest, currentIndex: Int) = workDataOf(
        KEY_REQUEST_ID to request.id,
        KEY_DESTINATION_LABEL to request.destinationLabel,
        KEY_DESTINATION_ALBUM_ID to request.albumId,
        KEY_DESTINATION_PROVIDER to request.destination.providerType.name,
        KEY_DESTINATION_CONFIG_ID to request.destination.serverConfigId,
        KEY_DESTINATION_REMOTE_ALBUM_ID to request.destination.remoteId,
        KEY_TARGET_REMOTE_ID to if (request.items.size == 1) {
            request.items.single().remoteId.orEmpty()
        } else "",
        KEY_TOTAL to request.items.size,
        KEY_COMPLETED to request.items.count {
            it.state == CloudAlbumCopyItemState.COPIED ||
                it.state == CloudAlbumCopyItemState.ALREADY_PRESENT ||
                it.state == CloudAlbumCopyItemState.MOVED
        },
        KEY_ALREADY_PRESENT to request.items.count { it.state == CloudAlbumCopyItemState.ALREADY_PRESENT },
        KEY_FAILED to request.items.count { it.state.isTransferFailure() },
        KEY_SOURCE_RETAINED to request.items.count {
            it.state == CloudAlbumCopyItemState.SOURCE_RETAINED
        },
        KEY_RETRYABLE to request.items.any { it.state.isTransferFailure() && it.retryable },
        KEY_MODE to request.mode.name,
        KEY_CURRENT to currentIndex
    )

    private fun summaryData(request: CloudAlbumCopyRequest) = androidx.work.Data.Builder()
        .putAll(progressData(request, request.items.size))
        .putString(
            KEY_MESSAGE,
            request.items.firstOrNull { it.state.isTransferFailure() }?.message.orEmpty()
        )
        .build()

    private fun failure(message: String, retryable: Boolean = false) =
        ListenableWorker.Result.failure(
            workDataOf(KEY_MESSAGE to message, KEY_RETRYABLE to retryable)
        )

    private fun retryOrFail(message: String): ListenableWorker.Result =
        if (runAttemptCount + 1 < MAX_ATTEMPTS) ListenableWorker.Result.retry()
        else failure(message, retryable = true)

    private fun foregroundInfo(request: CloudAlbumCopyRequest, current: Int): ForegroundInfo {
        val manager = appContext.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    appContext.getString(R.string.cloud_backup_channel_progress),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cloud_upload)
            .setContentTitle(
                appContext.getString(
                    if (request.mode == CloudAlbumTransferMode.MOVE) R.string.move else R.string.copy
                )
            )
            .setContentText(request.destinationLabel)
            .setProgress(request.items.size, current, false)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId(request.id), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId(request.id), notification)
        }
    }

    private fun notificationId(id: String): Int = 31_000 + (id.hashCode() and 0x0FFF)

    companion object {
        const val TAG = "CloudAlbumCopyWorker"
        const val KEY_REQUEST_ID = "request_id"
        const val KEY_DESTINATION_LABEL = "destination_label"
        const val KEY_DESTINATION_ALBUM_ID = "destination_album_id"
        const val KEY_DESTINATION_PROVIDER = "destination_provider"
        const val KEY_DESTINATION_CONFIG_ID = "destination_config_id"
        const val KEY_DESTINATION_REMOTE_ALBUM_ID = "destination_remote_album_id"
        const val KEY_TARGET_REMOTE_ID = "target_remote_id"
        const val KEY_TOTAL = "total"
        const val KEY_COMPLETED = "completed"
        const val KEY_ALREADY_PRESENT = "already_present"
        const val KEY_FAILED = "failed"
        const val KEY_SOURCE_RETAINED = "source_retained"
        const val KEY_RETRYABLE = "retryable"
        const val KEY_MODE = "mode"
        const val KEY_CURRENT = "current"
        const val KEY_MESSAGE = "message"
        private const val CHANNEL_ID = "cloud_album_copy"
        private const val MAX_ATTEMPTS = 3
    }
}
