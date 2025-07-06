package com.dot.gallery.core

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.compositionLocalOf
import com.dot.gallery.core.Settings.Misc.getTrashEnabled
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.presentation.util.mediaPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject

val LocalMediaHandler = compositionLocalOf<MediaHandler> {
    error("No MediaHandler provided!!! This is likely due to a missing Hilt injection in the Composable hierarchy.")
}

class MediaHandlerImpl @Inject constructor(
    private val repository: MediaRepository,
    private val context: Context
) : MediaHandler {
    override suspend fun <T : Media> toggleFavorite(
        result: ActivityResultLauncher<IntentSenderRequest>,
        mediaList: List<T>,
        favorite: Boolean
    ) = repository.toggleFavorite(result, mediaList, favorite)

    override suspend fun <T : Media> toggleFavorite(
        result: ActivityResultLauncher<IntentSenderRequest>,
        mediaList: List<T>
    ) {
        val turnToFavorite = mediaList.filter { it.favorite == 0 }
        val turnToNotFavorite = mediaList.filter { it.favorite == 1 }
        if (turnToFavorite.isNotEmpty()) {
            repository.toggleFavorite(result, turnToFavorite, true)
        }
        if (turnToNotFavorite.isNotEmpty()) {
            repository.toggleFavorite(result, turnToNotFavorite, false)
        }
    }

    override suspend fun <T : Media> trashMedia(
        result: ActivityResultLauncher<IntentSenderRequest>,
        mediaList: List<T>,
        trash: Boolean
    ) = withContext(Dispatchers.Default) {
        val isTrashEnabled = getTrashEnabled(context).firstOrNull() ?: true
        /**
         * Trash media only if user enabled the Trash Can
         * Or if user wants to remove existing items from the trash
         * */
        if ((isTrashEnabled || !trash)) {
            val pair = mediaList.mediaPair()
            if (pair.first.isNotEmpty()) {
                repository.trashMedia(result, mediaList, trash)
            }
            if (pair.second.isNotEmpty()) {
                repository.deleteMedia(result, mediaList)
            }
        } else {
            repository.deleteMedia(result, mediaList)
        }
    }

    override suspend fun <T : Media> copyMedia(
        from: T,
        path: String
    ): Boolean = repository.copyMedia(from, path)

    override suspend fun <T : Media> deleteMedia(
        result: ActivityResultLauncher<IntentSenderRequest>,
        mediaList: List<T>
    ) = repository.deleteMedia(result, mediaList)

    override suspend fun <T : Media> renameMedia(
        media: T,
        newName: String
    ): Boolean = repository.renameMedia(media, newName)

    override suspend fun <T : Media> moveMedia(
        media: T,
        newPath: String
    ): Boolean = repository.moveMedia(media, newPath)

    override suspend fun <T : Media> deleteMediaMetadata(
        media: T
    ): Boolean = repository.deleteMediaMetadata(media)

    override suspend fun <T : Media> deleteMediaGPSMetadata(
        media: T
    ): Boolean = repository.deleteMediaGPSMetadata(media)

    override suspend fun <T : Media> updateMediaImageDescription(
        media: T,
        description: String
    ): Boolean = repository.updateMediaImageDescription(media, description)

    override fun saveImage(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        mimeType: String,
        relativePath: String,
        displayName: String
    ): Uri? = repository.saveImage(bitmap, format, mimeType, relativePath, displayName)

    override fun overrideImage(
        uri: Uri,
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        mimeType: String,
        relativePath: String,
        displayName: String
    ): Boolean = repository.overrideImage(uri, bitmap, format, mimeType, relativePath, displayName)

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

}