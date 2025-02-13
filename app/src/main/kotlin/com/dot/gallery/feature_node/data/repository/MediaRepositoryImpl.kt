/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.data.repository

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.app.ActivityOptionsCompat
import androidx.datastore.preferences.core.Preferences
import androidx.work.WorkManager
import com.dot.gallery.core.Resource
import com.dot.gallery.core.dataStore
import com.dot.gallery.core.updateDatabase
import com.dot.gallery.core.util.MediaStoreBuckets
import com.dot.gallery.core.util.ext.copyMedia
import com.dot.gallery.core.util.ext.mapAsResource
import com.dot.gallery.core.util.ext.overrideImage
import com.dot.gallery.core.util.ext.renameMedia
import com.dot.gallery.core.util.ext.saveImage
import com.dot.gallery.core.util.ext.saveVideo
import com.dot.gallery.core.util.ext.updateMedia
import com.dot.gallery.core.util.ext.updateMediaExif
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.data.data_source.KeychainHolder.Companion.VAULT_INFO_FILE_NAME
import com.dot.gallery.feature_node.data.data_source.mediastore.queries.AlbumsFlow
import com.dot.gallery.feature_node.data.data_source.mediastore.queries.MediaFlow
import com.dot.gallery.feature_node.data.data_source.mediastore.queries.MediaUriFlow
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.ExifAttributes
import com.dot.gallery.feature_node.domain.model.IgnoredAlbum
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Media.ClassifiedMedia
import com.dot.gallery.feature_node.domain.model.Media.EncryptedMedia
import com.dot.gallery.feature_node.domain.model.Media.UriMedia
import com.dot.gallery.feature_node.domain.model.PinnedAlbum
import com.dot.gallery.feature_node.domain.model.TimelineSettings
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.domain.util.MediaOrder
import com.dot.gallery.feature_node.domain.util.OrderType
import com.dot.gallery.feature_node.domain.util.asUriMedia
import com.dot.gallery.feature_node.domain.util.compatibleBitmapFormat
import com.dot.gallery.feature_node.domain.util.compatibleMimeType
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isImage
import com.dot.gallery.feature_node.domain.util.migrate
import com.dot.gallery.feature_node.domain.util.toEncryptedMedia
import com.dot.gallery.feature_node.presentation.picker.AllowedMedia
import com.dot.gallery.feature_node.presentation.picker.AllowedMedia.BOTH
import com.dot.gallery.feature_node.presentation.picker.AllowedMedia.PHOTOS
import com.dot.gallery.feature_node.presentation.picker.AllowedMedia.VIDEOS
import com.dot.gallery.feature_node.presentation.util.printError
import com.dot.gallery.feature_node.presentation.util.printInfo
import com.dot.gallery.feature_node.presentation.util.printWarning
import com.dot.gallery.feature_node.presentation.vault.scheduleMediaMigrationCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MediaRepositoryImpl(
    private val context: Context,
    private val workManager: WorkManager,
    private val database: InternalDatabase,
    private val keychainHolder: KeychainHolder
) : MediaRepository {

    private val contentResolver = context.contentResolver

    override suspend fun updateInternalDatabase() {
        workManager.updateDatabase()
        workManager.scheduleMediaMigrationCheck()
    }

    /**
     * TODO: Add media reordering
     */
    override fun getMedia(): Flow<Resource<List<UriMedia>>> =
        MediaFlow(
            contentResolver = contentResolver,
            buckedId = MediaStoreBuckets.MEDIA_STORE_BUCKET_TIMELINE.id
        ).flowData().map {
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
        ).flowData().map {
            Resource.Success(it)
        }.flowOn(Dispatchers.IO)

    override fun getFavorites(mediaOrder: MediaOrder): Flow<Resource<List<UriMedia>>> =
        MediaFlow(
            contentResolver = contentResolver,
            buckedId = MediaStoreBuckets.MEDIA_STORE_BUCKET_FAVORITES.id
        ).flowData().map {
            Resource.Success(it)
        }.flowOn(Dispatchers.IO)

    override fun getTrashed(): Flow<Resource<List<UriMedia>>> =
        MediaFlow(
            contentResolver = contentResolver,
            buckedId = MediaStoreBuckets.MEDIA_STORE_BUCKET_TRASH.id
        ).flowData().map { Resource.Success(it) }.flowOn(Dispatchers.IO)

    override fun getAlbums(mediaOrder: MediaOrder): Flow<Resource<List<Album>>> =
        AlbumsFlow(context).flowData().map {
            withContext(Dispatchers.IO) {
                val data = it.toMutableList().apply {
                    replaceAll { album ->
                        album.copy(isPinned = database.getPinnedDao().albumIsPinned(album.id))
                    }
                }

                Resource.Success(mediaOrder.sortAlbums(data))
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun insertPinnedAlbum(pinnedAlbum: PinnedAlbum) =
        database.getPinnedDao().insertPinnedAlbum(pinnedAlbum)

    override suspend fun removePinnedAlbum(pinnedAlbum: PinnedAlbum) =
        database.getPinnedDao().removePinnedAlbum(pinnedAlbum)

    override fun getPinnedAlbums(): Flow<List<PinnedAlbum>> =
        database.getPinnedDao().getPinnedAlbums()

    override suspend fun addBlacklistedAlbum(ignoredAlbum: IgnoredAlbum) =
        database.getBlacklistDao().addBlacklistedAlbum(ignoredAlbum)

    override suspend fun removeBlacklistedAlbum(ignoredAlbum: IgnoredAlbum) =
        database.getBlacklistDao().removeBlacklistedAlbum(ignoredAlbum)

    override fun getBlacklistedAlbums(): Flow<List<IgnoredAlbum>> =
        database.getBlacklistDao().getBlacklistedAlbums()

    override fun getMediaByAlbumId(albumId: Long): Flow<Resource<List<UriMedia>>> =
        MediaFlow(
            contentResolver = contentResolver,
            buckedId = albumId,
        ).flowData().mapAsResource()

    override fun getMediaByAlbumIdWithType(
        albumId: Long,
        allowedMedia: AllowedMedia
    ): Flow<Resource<List<UriMedia>>> =
        MediaFlow(
            contentResolver = contentResolver,
            buckedId = albumId,
            mimeType = allowedMedia.toStringAny()
        ).flowData().mapAsResource()

    override fun getAlbumsWithType(allowedMedia: AllowedMedia): Flow<Resource<List<Album>>> =
        AlbumsFlow(
            context = context,
            mimeType = allowedMedia.toStringAny()
        ).flowData().mapAsResource()

    override fun getMediaListByUris(
        listOfUris: List<Uri>,
        reviewMode: Boolean
    ): Flow<Resource<List<UriMedia>>> =
        MediaUriFlow(
            contentResolver = contentResolver,
            uris = listOfUris,
            reviewMode = reviewMode
        ).flowData().mapAsResource(errorOnEmpty = true, errorMessage = "Media could not be opened")

    override suspend fun <T : Media> toggleFavorite(
        result: ActivityResultLauncher<IntentSenderRequest>,
        mediaList: List<T>,
        favorite: Boolean
    ) {
        val intentSender = MediaStore.createFavoriteRequest(
            contentResolver,
            mediaList.map { it.getUri() },
            favorite
        ).intentSender
        val senderRequest: IntentSenderRequest = IntentSenderRequest.Builder(intentSender)
            .setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION, 0)
            .build()
        result.launch(senderRequest)
    }

    override suspend fun <T : Media> trashMedia(
        result: ActivityResultLauncher<IntentSenderRequest>,
        mediaList: List<T>,
        trash: Boolean
    ) {
        val intentSender = MediaStore.createTrashRequest(
            contentResolver,
            mediaList.map { it.getUri() },
            trash
        ).intentSender
        val senderRequest: IntentSenderRequest = IntentSenderRequest.Builder(intentSender)
            .setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION, 0)
            .build()
        result.launch(senderRequest, ActivityOptionsCompat.makeTaskLaunchBehind())
    }

    override suspend fun <T : Media> deleteMedia(
        result: ActivityResultLauncher<IntentSenderRequest>,
        mediaList: List<T>
    ) {
        val intentSender =
            MediaStore.createDeleteRequest(
                contentResolver,
                mediaList.map { it.getUri() }).intentSender
        val senderRequest: IntentSenderRequest = IntentSenderRequest.Builder(intentSender)
            .setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION, 0)
            .build()
        result.launch(senderRequest)
    }

    override suspend fun <T : Media> copyMedia(
        from: T,
        path: String
    ): Boolean = contentResolver.copyMedia(
        from = from,
        path = path
    )

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
    ): Boolean = context.updateMedia(
        media = media,
        contentValues = relativePath(newPath)
    )

    override suspend fun <T : Media> updateMediaExif(
        media: T,
        exifAttributes: ExifAttributes
    ): Boolean = contentResolver.updateMediaExif(
        media = media,
        exifAttributes = exifAttributes
    )

    override fun saveImage(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        mimeType: String,
        relativePath: String,
        displayName: String
    ) = contentResolver.saveImage(bitmap, format, mimeType, relativePath, displayName)

    override fun overrideImage(
        uri: Uri,
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        mimeType: String,
        relativePath: String,
        displayName: String
    ) = contentResolver.overrideImage(uri, bitmap, format)

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
        onSuccess: () -> Unit,
        onFailed: (reason: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        keychainHolder.writeVaultInfo(
            vault = vault,
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
                val output = vault.mediaFile(media.id)
                if (output.exists()) output.delete()
                val encryptedMedia =
                    getBytes(media.getUri())?.let { bytes -> media.toEncryptedMedia(bytes) }
                return@withContext try {
                    encryptedMedia?.let {
                        output.encryptKotlin(it)
                        output.setLastModified(System.currentTimeMillis())
                        database.getVaultDao().addMediaToVault(it.migrate(vault.uuid))
                        true
                    } == true
                } catch (e: Exception) {
                    e.printStackTrace()
                    printError("Failed to add file: ${media.label}")
                    false
                }
            }
        }

    override suspend fun <T : Media> restoreMedia(vault: Vault, media: T): Boolean =
        withContext(Dispatchers.IO) {
            with(keychainHolder) {
                checkVaultFolder(vault)
                return@withContext try {
                    val output = vault.mediaFile(media.id)
                    val encryptedMedia = output.decryptKotlin<EncryptedMedia>()
                    val restored: Boolean
                    if (media.isImage) {
                        restored = saveImage(
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
                        restored = contentResolver.saveVideo(
                            bytes = encryptedMedia.bytes,
                            displayName = media.label,
                            mimeType = media.compatibleMimeType(),
                            relativePath = Environment.DIRECTORY_MOVIES + "/Restored"
                        ) != null
                    }
                    val deleted = if (restored) output.delete() else false
                    if (deleted) {
                        database.getVaultDao().deleteMediaFromVault(encryptedMedia.migrate(vault.uuid))
                    }
                    restored && deleted
                } catch (e: Exception) {
                    e.printStackTrace()
                    printError("Failed to restore file: ${media.label}")
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
                        database.getVaultDao().deleteMediaFromVault(vault.uuid, file.nameWithoutExtension.toLong())
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
                val uuidRegex = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$".toRegex()
                val vaults = filesDir.listFiles { it.isDirectory && it.nameWithoutExtension.matches(uuidRegex) }
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

    override suspend fun migrateVault() {
        withContext(Dispatchers.IO) {
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
                val uuidRegex = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$".toRegex()
                val vaults = filesDir.listFiles { it.isDirectory && it.nameWithoutExtension.matches(uuidRegex) }
                val encryptedMedia = mutableListOf<Media.EncryptedMedia2>()
                vaults?.forEach { vaultFolder ->
                    (vaultFolder.listFiles()?.filter { it.name.endsWith("enc") } ?: emptyList()).forEach { file ->
                        try {
                            val id = file.nameWithoutExtension.toLong()
                            if (databaseStoredEncryptedMedia?.find { media -> media.id == id } != null) {
                                return@forEach
                            }
                            val oldEncryptedMedia = file.decrypt<EncryptedMedia>()
                            printInfo("Migrating old encrypted media: ${oldEncryptedMedia.id}")
                            file.delete()
                            val encryptedMedia2 = oldEncryptedMedia.migrate(UUID.fromString(vaultFolder.nameWithoutExtension))
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
        }
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
        return context.dataStore.data.map { it[key] ?: defaultValue }
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

    companion object {
        private fun displayName(newName: String) = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
        }

        private fun relativePath(newPath: String) = ContentValues().apply {
            put(MediaStore.MediaColumns.RELATIVE_PATH, newPath)
        }
    }
}