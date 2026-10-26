package com.dot.gallery.core

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.compositionLocalOf
import androidx.activity.result.IntentSenderRequest
import com.dot.gallery.core.decoder.format.ImageReencoder
import com.dot.gallery.core.metadata.MetadataCategory
import com.dot.gallery.core.metadata.MetadataSaveMode
import com.dot.gallery.core.metadata.SanitizationCapability
import com.dot.gallery.core.metadata.SanitizationResult
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.data.repository.CaptureDateEditCapability
import com.dot.gallery.data.repository.CaptureDateEditResult
import com.dot.gallery.data.repository.MediaMutationResult
import kotlinx.coroutines.flow.Flow
import com.dot.gallery.domain.model.ClassifiedMedia
import com.dot.gallery.domain.uri.path




val LocalMediaHandler = compositionLocalOf<MediaHandler> {
    error("No MediaHandler provided!!! This is likely due to a missing inject binding in the Composable hierarchy.")
}

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
    ): MediaMutationResult

    suspend fun <T: Media> copyMedia(from: T, path: String)

    suspend fun <T: Media> copyMedia(vararg sets: Pair<T, String>)

    suspend fun <T: Media> deleteMedia(
        result: ActivityResultLauncher<IntentSenderRequest>,
        mediaList: List<T>
    ): MediaMutationResult

    suspend fun <T: Media> renameMedia(media: T, newName: String): Boolean

    suspend fun <T: Media> moveMedia(media: T, newPath: String): Boolean

    suspend fun <T: Media> copyMediaForMove(
        mediaList: List<T>,
        newPath: String,
        onProgress: suspend (Float) -> Unit = {}
    ): List<Uri>

    suspend fun discardMediaCopies(uris: List<Uri>)

    suspend fun probeMetadataSanitization(media: Media): SanitizationCapability

    suspend fun sanitizeMediaMetadata(
        media: Media,
        categories: Set<MetadataCategory>,
        saveMode: MetadataSaveMode
    ): SanitizationResult

    suspend fun probeCaptureDateEdit(media: Media): CaptureDateEditCapability

    suspend fun updateMediaCaptureDate(media: Media, timestampMillis: Long): CaptureDateEditResult

    suspend fun createDatedCopy(media: Media, timestampMillis: Long): CaptureDateEditResult

    suspend fun <T: Media> updateMediaDescription(media: T, description: String): Boolean

    suspend fun saveImage(
        bitmap: Bitmap,
        writeFormat: ImageReencoder.ImageWriteFormat,
        config: ImageReencoder.ReencodeConfig,
        mimeType: String,
        relativePath: String,
        displayName: String
    ): Uri?

    suspend fun overrideImage(
        uri: Uri,
        bitmap: Bitmap,
        writeFormat: ImageReencoder.ImageWriteFormat,
        config: ImageReencoder.ReencodeConfig,
        mimeType: String,
        relativePath: String,
        displayName: String
    ): Boolean

    suspend fun getCategoryForMediaId(mediaId: Long): String?

    fun getClassifiedMediaCountAtCategory(category: String): Flow<Int>

    fun getClassifiedMediaThumbnailByCategory(category: String): Flow<ClassifiedMedia?>

    suspend fun deleteAlbumThumbnail(albumId: Long)
    suspend fun updateAlbumThumbnail(albumId: Long, newThumbnail: Uri)
    fun hasAlbumThumbnail(albumId: Long): Flow<Boolean>
    suspend fun collectMetadataFor(media: Media)
    suspend fun <T : Media> addMedia(vault: Vault, media: T)

    suspend fun <T: Media> rotateImage(media: T, degrees: Int, forceCopy: Boolean = false): String

    suspend fun <T: Media> downloadCloudMedia(mediaList: List<T>): Result<Int>
}