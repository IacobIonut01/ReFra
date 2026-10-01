package com.dot.gallery.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.compositionLocalOf
import androidx.work.WorkManager
import com.dot.gallery.cloud.core.CloudUri
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.capabilities.RemoteMediaProvider
import com.dot.gallery.cloud.core.capabilities.SyncCapableProvider
import com.dot.gallery.cloud.data.dao.CloudMediaDao
import com.dot.gallery.cloud.data.dao.CloudServerConfigDao
import com.dot.gallery.cloud.sync.CloudMediaStoreWriter
import com.dot.gallery.core.decoder.format.ImageReencoder
import com.dot.gallery.core.logging.withLogScope
import com.dot.gallery.core.metadata.MetadataRemovalMode
import com.dot.gallery.core.metadata.MetadataSaveMode
import com.dot.gallery.core.metadata.SanitizationCapability
import com.dot.gallery.core.metadata.SanitizationResult
import com.dot.gallery.core.workers.VaultOperationWorker
import com.dot.gallery.core.workers.enqueueVaultOperation
import com.dot.gallery.core.workers.rotateImage
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isCloud
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.repository.CaptureDateEditCapability
import com.dot.gallery.feature_node.domain.repository.CaptureDateEditResult
import com.dot.gallery.feature_node.domain.repository.MediaMutationResult
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.presentation.util.printError
import com.dot.gallery.feature_node.presentation.util.printWarn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

val LocalMediaHandler = compositionLocalOf<MediaHandler> {
    error("No MediaHandler provided!!! This is likely due to a missing Hilt injection in the Composable hierarchy.")
}

class MediaHandlerImpl @Inject constructor(
    private val repository: MediaRepository,
    private val context: Context,
    private val workManager: WorkManager,
    private val providerRegistry: ProviderRegistry,
    private val cloudMediaDao: CloudMediaDao,
    private val cloudServerConfigDao: CloudServerConfigDao
) : MediaHandler {

    private fun <T : Media> extractCloudInfo(media: T): Triple<String, String, Long>? {
        if (!media.isCloud) return null
        val cloudUri = CloudUri.parse(media.getUri().toString()) ?: return null
        if (cloudUri.configId <= 0L) return null
        return Triple(cloudUri.providerType.name, cloudUri.remoteId, cloudUri.configId)
    }

    private fun getCloudProvider(providerName: String, configId: Long): RemoteMediaProvider? {
        val providerType = runCatching { ProviderType.valueOf(providerName) }.getOrNull() ?: return null
        return (providerRegistry.getByConfigId(configId) as? RemoteMediaProvider)
            ?.takeIf { it.providerType == providerType }
    }

    override suspend fun <T : Media> toggleFavorite(
        result: ActivityResultLauncher<IntentSenderRequest>,
        mediaList: List<T>,
        favorite: Boolean
    ) = withLogScope("media-ops") {
        val (cloudMedia, localMedia) = mediaList.partition { it.isCloud }
        if (localMedia.isNotEmpty()) {
            repository.toggleFavorite(result, localMedia, favorite)
        }
        if (cloudMedia.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                cloudMedia.forEach { media ->
                    val (providerName, remoteId, configId) = extractCloudInfo(media) ?: return@forEach
                    val providerType = try { ProviderType.valueOf(providerName) } catch (_: Exception) { return@forEach }
                    val provider = getCloudProvider(providerName, configId) ?: return@forEach
                    provider.toggleFavorite(remoteId, favorite).onFailure { e ->
                        printError(
                            tag = "MediaHandler",
                            message = "Cloud favorite failed",
                            throwable = e,
                            ctx = mapOf("provider" to providerName),
                        )
                    }
                    cloudMediaDao.updateFavorite(remoteId, providerType, configId, favorite)
                }
            }
        }
    }

    override suspend fun <T : Media> toggleFavorite(
        result: ActivityResultLauncher<IntentSenderRequest>,
        mediaList: List<T>
    ) {
        val turnToFavorite = mediaList.filter { it.favorite == 0 }
        val turnToNotFavorite = mediaList.filter { it.favorite == 1 }
        if (turnToFavorite.isNotEmpty()) {
            toggleFavorite(result, turnToFavorite, true)
        }
        if (turnToNotFavorite.isNotEmpty()) {
            toggleFavorite(result, turnToNotFavorite, false)
        }
    }

    override suspend fun <T : Media> trashMedia(
        result: ActivityResultLauncher<IntentSenderRequest>,
        mediaList: List<T>,
        trash: Boolean
    ): MediaMutationResult = withLogScope("media-ops") {
        val (cloudMedia, localMedia) = mediaList.partition { it.isCloud }

        if (cloudMedia.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                cloudMedia.forEach { media ->
                    val (providerName, remoteId, configId) = extractCloudInfo(media) ?: return@forEach
                    val provider = getCloudProvider(providerName, configId) ?: return@forEach
                    val outcome = if (trash) {
                        provider.trashAsset(remoteId)
                    } else {
                        provider.restoreAsset(remoteId)
                    }
                    outcome.onFailure { e ->
                        printError(
                            tag = "MediaHandler",
                            message = "Cloud ${if (trash) "trash" else "restore"} failed",
                            throwable = e,
                            ctx = mapOf("provider" to providerName),
                        )
                    }
                }
            }
        }

        if (localMedia.isNotEmpty()) {
            repository.trashMedia(result, localMedia, trash)
        } else {
            MediaMutationResult.COMPLETED
        }
    }

    override suspend fun <T : Media> addMedia(vault: Vault, media: T) {
        workManager.enqueueVaultOperation(
            operation = VaultOperationWorker.OP_ENCRYPT,
            media = listOf(media.getUri()),
            vault = vault
        )
    }

    override fun <T : Media> rotateImage(
        media: T,
        degrees: Int,
        forceCopy: Boolean
    ) = workManager.rotateImage(media, degrees, forceCopy)

    override suspend fun <T : Media> copyMedia(
        from: T,
        path: String
    ) = repository.copyMedia(from, path)

    override suspend fun <T : Media> copyMedia(vararg sets: Pair<T, String>) =
        repository.copyMedia(*sets)

    override suspend fun <T : Media> deleteMedia(
        result: ActivityResultLauncher<IntentSenderRequest>,
        mediaList: List<T>
    ): MediaMutationResult = withLogScope("media-ops") {
        val (cloudMedia, localMedia) = mediaList.partition { it.isCloud }
        val cloudDeleted = withContext(Dispatchers.IO) {
            cloudMedia.map { media ->
                val (providerName, remoteId, configId) = extractCloudInfo(media)
                    ?: return@map false
                val providerType = runCatching { ProviderType.valueOf(providerName) }.getOrNull()
                    ?: return@map false
                val provider = getCloudProvider(providerName, configId) ?: return@map false
                provider.deleteAsset(remoteId).onFailure { e ->
                    printError(
                        tag = "MediaHandler",
                        message = "Cloud delete failed",
                        throwable = e,
                        ctx = mapOf("provider" to providerName),
                    )
                }.isSuccess.also { deleted ->
                    if (deleted) cloudMediaDao.delete(remoteId, providerType, configId)
                }
            }.all { it }
        }
        val localResult = if (localMedia.isNotEmpty()) {
            repository.deleteMedia(result, localMedia)
        } else {
            MediaMutationResult.COMPLETED
        }
        if (cloudDeleted) localResult else MediaMutationResult.FAILED
    }

    override suspend fun <T : Media> renameMedia(
        media: T,
        newName: String
    ): Boolean = repository.renameMedia(media, newName)

    override suspend fun <T : Media> moveMedia(
        media: T,
        newPath: String
    ): Boolean = withLogScope("media-ops") { repository.moveMedia(media, newPath) }

    override suspend fun <T : Media> copyMediaForMove(
        mediaList: List<T>,
        newPath: String,
        onProgress: suspend (Float) -> Unit
    ): List<Uri> = withLogScope("media-ops") {
        repository.copyMediaForMove(mediaList, newPath, onProgress)
    }

    override suspend fun discardMediaCopies(uris: List<Uri>) =
        repository.discardMediaCopies(uris)

    override suspend fun probeMetadataSanitization(
        media: Media
    ): SanitizationCapability = repository.probeMetadataSanitization(media)

    override suspend fun sanitizeMediaMetadata(
        media: Media,
        mode: MetadataRemovalMode,
        saveMode: MetadataSaveMode
    ): SanitizationResult = repository.sanitizeMediaMetadata(media, mode, saveMode)

    override suspend fun probeCaptureDateEdit(media: Media): CaptureDateEditCapability =
        repository.probeCaptureDateEdit(media)

    override suspend fun updateMediaCaptureDate(
        media: Media,
        timestampMillis: Long
    ): CaptureDateEditResult = repository.updateMediaCaptureDate(media, timestampMillis)

    override suspend fun createDatedCopy(
        media: Media,
        timestampMillis: Long
    ): CaptureDateEditResult = repository.createDatedCopy(media, timestampMillis)

    override suspend fun <T : Media> updateMediaDescription(
        media: T,
        description: String
    ): Boolean = repository.updateMediaDescription(media, description)

    override suspend fun saveImage(
        bitmap: Bitmap,
        writeFormat: ImageReencoder.ImageWriteFormat,
        config: ImageReencoder.ReencodeConfig,
        mimeType: String,
        relativePath: String,
        displayName: String
    ): Uri? = repository.saveImage(bitmap, writeFormat, config, mimeType, relativePath, displayName)

    override suspend fun overrideImage(
        uri: Uri,
        bitmap: Bitmap,
        writeFormat: ImageReencoder.ImageWriteFormat,
        config: ImageReencoder.ReencodeConfig,
        mimeType: String,
        relativePath: String,
        displayName: String
    ): Boolean = repository.overrideImage(uri, bitmap, writeFormat, config, mimeType, relativePath, displayName)

    override suspend fun getCategoryForMediaId(mediaId: Long): String? =
        repository.getCategoryForMediaId(mediaId)

    override fun getClassifiedMediaCountAtCategory(category: String): Flow<Int> =
        repository.getClassifiedMediaCountAtCategory(category)

    override fun getClassifiedMediaThumbnailByCategory(category: String): Flow<Media.ClassifiedMedia?> =
        repository.getClassifiedMediaThumbnailByCategory(category)

    override suspend fun deleteAlbumThumbnail(albumId: Long) =
        repository.deleteAlbumThumbnail(albumId)

    override suspend fun updateAlbumThumbnail(albumId: Long, newThumbnail: Uri) =
        repository.updateAlbumThumbnail(albumId, newThumbnail)

    override fun hasAlbumThumbnail(albumId: Long): Flow<Boolean> =
        repository.hasAlbumThumbnail(albumId)

    override suspend fun collectMetadataFor(media: Media) = repository.collectMetadataFor(media)

    override suspend fun <T : Media> downloadCloudMedia(mediaList: List<T>): Result<Int> =
        withLogScope("media-ops") {
            withContext(Dispatchers.IO) {
            val cloudMedia = mediaList.filter { it.isCloud }
            if (cloudMedia.isEmpty()) return@withContext Result.success(0)

            var successCount = 0
            for (media in cloudMedia) {
                val (providerName, remoteId, configId) = extractCloudInfo(media) ?: continue
                val providerType = try {
                    ProviderType.valueOf(providerName)
                } catch (_: Exception) {
                    continue
                }
                val provider = providerRegistry.getByConfigId(configId)
                    ?.takeIf { it.providerType == providerType }
                val syncProvider = provider as? SyncCapableProvider ?: continue

                val downloadResult = syncProvider.downloadAsset(remoteId)
                downloadResult.onFailure { e ->
                    printWarn(
                        tag = "MediaHandler",
                        message = "Cloud download failed",
                        ctx = mapOf(
                            "provider" to providerName,
                            "reason" to (e.message ?: e.javaClass.simpleName),
                        ),
                    )
                }
                val cacheUri = downloadResult.getOrNull() ?: continue

                // Same destination rule as the automatic worker: the provider picks the
                // sub-path (remote folder mirror for path stores, account/album for
                // Immich) instead of dumping everything under `Cloud/`.
                val accountLabel = cloudServerConfigDao.getById(configId)
                    ?.displayName?.takeIf { it.isNotBlank() } ?: providerType.displayName
                val entity = cloudMediaDao.getByRemoteId(remoteId, providerType, configId)
                val subPath = entity?.let {
                    runCatching { syncProvider.downloadSubPath(it, accountLabel) }.getOrNull()
                }?.takeIf { it.isNotBlank() } ?: accountLabel
                val insertUri = CloudMediaStoreWriter.write(
                    context = context,
                    source = cacheUri,
                    request = CloudMediaStoreWriter.Request(
                        displayName = media.label,
                        mimeType = media.mimeType,
                        relativeSubPath = subPath,
                        fallbackSubPath = accountLabel
                    )
                )
                if (insertUri != null) {
                    CloudMediaStoreWriter.deleteSource(context, cacheUri)
                    successCount++
                }
            }
            Result.success(successCount)
            }
        }

}