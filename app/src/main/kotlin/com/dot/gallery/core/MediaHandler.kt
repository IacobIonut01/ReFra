package com.dot.gallery.core

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Vault
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface MediaHandler {

    suspend fun <T: Media> toggleFavorite(
        result: ActivityResultLauncher<IntentSenderRequest>,
        mediaList: List<T>,
        favorite: Boolean
    )

    suspend fun <T: Media> toggleFavorite(
        result: ActivityResultLauncher<IntentSenderRequest>,
        mediaList: List<T>
    )

    suspend fun <T: Media> trashMedia(
        result: ActivityResultLauncher<IntentSenderRequest>,
        mediaList: List<T>,
        trash: Boolean = true
    )

    suspend fun <T: Media> copyMedia(from: T, path: String)

    suspend fun <T: Media> copyMedia(vararg sets: Pair<T, String>)

    suspend fun <T: Media> deleteMedia(
        result: ActivityResultLauncher<IntentSenderRequest>,
        mediaList: List<T>
    )

    suspend fun <T: Media> renameMedia(media: T, newName: String): Boolean

    suspend fun <T: Media> moveMedia(media: T, newPath: String): Boolean

    suspend fun <T: Media> deleteMediaMetadata(media: T): Boolean

    suspend fun <T: Media> deleteMediaGPSMetadata(media: T): Boolean

    suspend fun <T: Media> updateMediaImageDescription(media: T, description: String): Boolean

    suspend fun saveImage(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        mimeType: String,
        relativePath: String,
        displayName: String
    ): Uri?

    suspend fun overrideImage(
        uri: Uri,
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        mimeType: String,
        relativePath: String,
        displayName: String
    ): Boolean

    suspend fun getCategoryForMediaId(mediaId: Long): String?

    fun getClassifiedMediaCountAtCategory(category: String): Flow<Int>

    fun getClassifiedMediaThumbnailByCategory(category: String): Flow<Media.ClassifiedMedia?>

    suspend fun deleteAlbumThumbnail(albumId: Long)
    suspend fun updateAlbumThumbnail(albumId: Long, newThumbnail: Uri)
    fun hasAlbumThumbnail(albumId: Long): Flow<Boolean>
    suspend fun collectMetadataFor(media: Media)
    suspend fun <T : Media> addMedia(vault: Vault, media: T)

    fun <T: Media> rotateImage(media: T, degrees: Int): UUID
}