/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.domain.repository

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.datastore.preferences.core.Preferences
import com.dot.gallery.core.Resource
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.ExifAttributes
import com.dot.gallery.feature_node.domain.model.IgnoredAlbum
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Media.ClassifiedMedia
import com.dot.gallery.feature_node.domain.model.Media.UriMedia
import com.dot.gallery.feature_node.domain.model.PinnedAlbum
import com.dot.gallery.feature_node.domain.model.TimelineSettings
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.util.MediaOrder
import com.dot.gallery.feature_node.presentation.picker.AllowedMedia
import kotlinx.coroutines.flow.Flow
import java.io.File

interface MediaRepository {

    suspend fun updateInternalDatabase()

    fun getMedia(): Flow<Resource<List<UriMedia>>>

    fun getMediaByType(allowedMedia: AllowedMedia): Flow<Resource<List<UriMedia>>>

    fun getFavorites(mediaOrder: MediaOrder): Flow<Resource<List<UriMedia>>>

    fun getTrashed(): Flow<Resource<List<UriMedia>>>

    fun getAlbums(mediaOrder: MediaOrder): Flow<Resource<List<Album>>>

    suspend fun insertPinnedAlbum(pinnedAlbum: PinnedAlbum)

    suspend fun removePinnedAlbum(pinnedAlbum: PinnedAlbum)

    fun getPinnedAlbums(): Flow<List<PinnedAlbum>>

    suspend fun addBlacklistedAlbum(ignoredAlbum: IgnoredAlbum)

    suspend fun removeBlacklistedAlbum(ignoredAlbum: IgnoredAlbum)

    fun getBlacklistedAlbums(): Flow<List<IgnoredAlbum>>

    fun getMediaByAlbumId(albumId: Long): Flow<Resource<List<UriMedia>>>

    fun getMediaByAlbumIdWithType(
        albumId: Long,
        allowedMedia: AllowedMedia
    ): Flow<Resource<List<UriMedia>>>

    fun getAlbumsWithType(allowedMedia: AllowedMedia): Flow<Resource<List<Album>>>

    fun getMediaListByUris(listOfUris: List<Uri>, reviewMode: Boolean): Flow<Resource<List<UriMedia>>>

    suspend fun <T: Media> toggleFavorite(
        result: ActivityResultLauncher<IntentSenderRequest>,
        mediaList: List<T>,
        favorite: Boolean
    )

    suspend fun <T: Media> trashMedia(
        result: ActivityResultLauncher<IntentSenderRequest>,
        mediaList: List<T>,
        trash: Boolean
    )

    suspend fun <T: Media> copyMedia(
        from: T,
        path: String
    ): Boolean

    suspend fun <T: Media> deleteMedia(
        result: ActivityResultLauncher<IntentSenderRequest>,
        mediaList: List<T>
    )

    suspend fun <T: Media> renameMedia(
        media: T,
        newName: String
    ): Boolean

    suspend fun <T: Media> moveMedia(
        media: T,
        newPath: String
    ): Boolean

    suspend fun <T: Media> updateMediaExif(
        media: T,
        exifAttributes: ExifAttributes
    ): Boolean

    fun saveImage(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        mimeType: String,
        relativePath: String,
        displayName: String
    ): Uri?

    fun overrideImage(
        uri: Uri,
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        mimeType: String,
        relativePath: String,
        displayName: String
    ): Boolean

    fun getVaults(): Flow<Resource<List<Vault>>>

    suspend fun createVault(
        vault: Vault,
        onSuccess: () -> Unit,
        onFailed: (reason: String) -> Unit
    )

    suspend fun deleteVault(
        vault: Vault,
        onSuccess: () -> Unit,
        onFailed: (reason: String) -> Unit
    )

    fun getEncryptedMedia(vault: Vault?): Flow<Resource<List<UriMedia>>>

    suspend fun <T: Media> addMedia(vault: Vault, media: T): Boolean

    suspend fun <T: Media> restoreMedia(vault: Vault, media: T): Boolean

    suspend fun <T: Media> deleteEncryptedMedia(vault: Vault, media: T): Boolean

    suspend fun deleteAllEncryptedMedia(
        vault: Vault,
        onSuccess: () -> Unit,
        onFailed: (failedFiles: List<File>) -> Unit
    ): Boolean

    suspend fun getUnmigratedVaultMediaSize(): Int

    suspend fun migrateVault()

    suspend fun restoreVault(vault: Vault)

    fun getTimelineSettings(): Flow<TimelineSettings?>

    suspend fun updateTimelineSettings(settings: TimelineSettings)

    fun <Result> getSetting(key: Preferences.Key<Result>, defaultValue: Result): Flow<Result>

    fun getClassifiedCategories(): Flow<List<String>>

    fun getClassifiedMediaByCategory(category: String?): Flow<List<ClassifiedMedia>>

    fun getClassifiedMediaByMostPopularCategory(): Flow<List<ClassifiedMedia>>

    fun getCategoriesWithMedia(): Flow<List<ClassifiedMedia>>

    fun getClassifiedMediaCount(): Flow<Int>

    fun getClassifiedMediaCountAtCategory(category: String): Flow<Int>

    fun getClassifiedMediaThumbnailByCategory(category: String): Flow<ClassifiedMedia?>

    suspend fun getCategoryForMediaId(mediaId: Long): String?
    suspend fun changeCategory(mediaId: Long, newCategory: String)

    suspend fun deleteClassifications()

}