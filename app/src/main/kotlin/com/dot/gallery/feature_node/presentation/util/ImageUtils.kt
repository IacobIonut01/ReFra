/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.util

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.net.toFile
import com.dot.gallery.BuildConfig
import com.dot.gallery.core.Settings.Misc.rememberExifDateFormat
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.domain.model.InfoRow
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Media.EncryptedMedia
import com.dot.gallery.feature_node.domain.model.MediaMetadata
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isEncrypted
import com.dot.gallery.feature_node.presentation.mediaview.components.retrieveMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

val sdcardRegex = "^/storage/[A-Z0-9]+-[A-Z0-9]+/.*$".toRegex()


@Composable
fun rememberBitmapPainter(bitmap: Bitmap): State<Painter> {
    return remember(bitmap) { derivedStateOf { BitmapPainter(image = bitmap.asImageBitmap()) } }
}

fun FloatArray.to3x3Matrix(): FloatArray {
    return floatArrayOf(
        this[0], this[1], this[2],
        this[5], this[6], this[7],
        this[10], this[11], this[12]
    )
}

fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val aspectRatio = width.toFloat() / height.toFloat()
    val newWidth: Int
    val newHeight: Int

    if (width > height) {
        newWidth = maxWidth
        newHeight = (maxWidth / aspectRatio).toInt()
    } else {
        newHeight = maxHeight
        newWidth = (maxHeight * aspectRatio).toInt()
    }

    return bitmap.scale(newWidth, newHeight)
}

fun overlayBitmaps(currentImage: Bitmap, markupBitmap: Bitmap): Bitmap {
    // Create a new bitmap with the same dimensions as the current image
    val resultBitmap = createBitmap(
        currentImage.width,
        currentImage.height,
        currentImage.config ?: Bitmap.Config.ARGB_8888
    )

    // Create a canvas to draw on the new bitmap
    val canvas = Canvas(resultBitmap)

    // Draw the current image on the canvas
    canvas.drawBitmap(currentImage, 0f, 0f, null)

    // Draw the markup bitmap on top of the current image
    canvas.drawBitmap(markupBitmap.copy(Bitmap.Config.ARGB_8888, true), 0f, 0f, null)

    return resultBitmap
}

fun Bitmap.flipHorizontally(): Bitmap {
    val matrix = Matrix().apply { postScale(-1f, 1f, width / 2f, height / 2f) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

fun Bitmap.flipVertically(): Bitmap {
    val matrix = Matrix().apply { postScale(1f, -1f, width / 2f, height / 2f) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

fun Bitmap.rotate(degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

fun List<Media>.canBeTrashed(): Boolean {
    return find { it.path.matches(sdcardRegex) } == null
}

/**
 * first pair = trashable
 * second pair = non-trashable
 */
fun List<Media>.mediaPair(): Pair<List<Media>, List<Media>> {
    val trashableMedia = ArrayList<Media>()
    val nonTrashableMedia = ArrayList<Media>()
    forEach {
        if (it.path.matches(sdcardRegex)) {
            nonTrashableMedia.add(it)
        } else {
            trashableMedia.add(it)
        }
    }
    return trashableMedia to nonTrashableMedia
}

fun Media.canBeTrashed(): Boolean {
    return !path.matches(sdcardRegex)
}

@Composable
fun rememberActivityResult(onResultCanceled: () -> Unit = {}, onResultOk: () -> Unit = {}) =
    rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
        onResult = {
            if (it.resultCode == RESULT_OK) onResultOk()
            if (it.resultCode == RESULT_CANCELED) onResultCanceled()
        }
    )


fun <T : Media> T.writeRequest(
    contentResolver: ContentResolver,
) = IntentSenderRequest.Builder(
    MediaStore.createWriteRequest(
        contentResolver,
        arrayListOf(getUri())
    )
)
    .build()

fun <T : Media> List<T>.writeRequest(
    contentResolver: ContentResolver,
) = IntentSenderRequest.Builder(MediaStore.createWriteRequest(contentResolver, map { it.getUri() }))
    .build()

fun Uri.writeRequest(
    contentResolver: ContentResolver,
) = IntentSenderRequest.Builder(MediaStore.createWriteRequest(contentResolver, arrayListOf(this)))
    .build()

@Composable
fun <T : Media> rememberMediaInfo(
    media: T,
    exifMetadata: MediaMetadata?,
    onLabelClick: () -> Unit
): List<InfoRow> {
    val context = LocalContext.current
    val exifDateFormat by rememberExifDateFormat()
    return remember(exifMetadata, media, exifDateFormat) {
        media.retrieveMetadata(context, exifDateFormat, exifMetadata, onLabelClick)
    }
}

fun Uri.authorizedUri(context: Context): Uri = if (this.toString()
        .startsWith("content://")
) this else FileProvider.getUriForFile(
    context,
    BuildConfig.CONTENT_AUTHORITY,
    this.toFile()
)

fun <T : Media> Context.shareMedia(media: T) {
    val originalUri = media.getUri()
    val uri = if (originalUri.toString()
            .startsWith("content://")
    ) originalUri else FileProvider.getUriForFile(
        this,
        BuildConfig.CONTENT_AUTHORITY,
        originalUri.toFile()
    )

    ShareCompat
        .IntentBuilder(this)
        .setType(media.mimeType)
        .addStream(uri)
        .startChooser()
}

fun <T : Media> Context.shareMedia(mediaList: List<T>) {
    val mimeTypes =
        if (mediaList.find { it.duration != null } != null) {
            if (mediaList.find { it.duration == null } != null) "video/*,image/*" else "video/*"
        } else "image/*"

    val shareCompat = ShareCompat
        .IntentBuilder(this)
        .setType(mimeTypes)
    mediaList.forEach {
        shareCompat.addStream(it.getUri())
    }
    shareCompat.startChooser()
}

/**
 * Share encrypted media from vault by temporarily decrypting it
 */
suspend fun <T : Media> Context.shareEncryptedMedia(
    media: T,
    vault: Vault,
    keychainHolder: KeychainHolder
) = withContext(Dispatchers.IO) {
    try {
        // Create temporary file for decrypted content
        val tempFile = createDecryptedTempFile(media, keychainHolder)
        
        // Create content URI for the temp file
        val tempUri = FileProvider.getUriForFile(
            this@shareEncryptedMedia,
            BuildConfig.CONTENT_AUTHORITY,
            tempFile
        )
        
        // Share the decrypted file
        withContext(Dispatchers.Main) {
            val shareIntent = ShareCompat
                .IntentBuilder(this@shareEncryptedMedia)
                .setType(media.mimeType)
                .addStream(tempUri)
                .createChooserIntent()
                .apply {
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            
            startActivity(shareIntent)
        }
        
    } catch (e: Exception) {
        e.printStackTrace()
        // Fallback to regular sharing if decryption fails
        withContext(Dispatchers.Main) {
            shareMedia(media)
        }
    }
}

/**
 * Share multiple media items, handling both encrypted and non-encrypted media
 */
suspend fun <T : Media> Context.shareMediaWithVaultSupport(
    mediaList: List<T>,
    currentVault: Vault? = null
) = withContext(Dispatchers.IO) {
    try {
        val keychainHolder = if (currentVault != null) KeychainHolder(this@shareMediaWithVaultSupport) else null
        val shareStreams = mutableListOf<Uri>()
        
        for (media in mediaList) {
            if (media.isEncrypted && currentVault != null && keychainHolder != null) {
                // Handle encrypted media by creating temp decrypted file
                try {
                    val tempFile = createDecryptedTempFile(media, keychainHolder)
                    val tempUri = FileProvider.getUriForFile(
                        this@shareMediaWithVaultSupport,
                        BuildConfig.CONTENT_AUTHORITY,
                        tempFile
                    )
                    shareStreams.add(tempUri)
                } catch (e: Exception) {
                    // If decryption fails, skip this media or use original URI
                    e.printStackTrace()
                    shareStreams.add(media.getUri())
                }
            } else {
                // Handle regular media
                val originalUri = media.getUri()
                val uri = if (originalUri.toString().startsWith("content://")) {
                    originalUri
                } else {
                    FileProvider.getUriForFile(
                        this@shareMediaWithVaultSupport,
                        BuildConfig.CONTENT_AUTHORITY,
                        originalUri.toFile()
                    )
                }
                shareStreams.add(uri)
            }
        }
        
        if (shareStreams.isNotEmpty()) {
            // Determine appropriate mime type
            val mimeTypes = if (mediaList.find { it.duration != null } != null) {
                if (mediaList.find { it.duration == null } != null) "video/*,image/*" else "video/*"
            } else "image/*"
            
            withContext(Dispatchers.Main) {
                val shareBuilder = ShareCompat
                    .IntentBuilder(this@shareMediaWithVaultSupport)
                    .setType(mimeTypes)
                
                shareStreams.forEach { uri ->
                    shareBuilder.addStream(uri)
                }
                
                val shareIntent = shareBuilder.createChooserIntent().apply {
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                startActivity(shareIntent)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        // Fallback to regular sharing
        withContext(Dispatchers.Main) {
            shareMedia(mediaList)
        }
    }
}

/**
 * Create a temporary decrypted file from encrypted vault media
 */
private fun <T : Media> createDecryptedTempFile(
    media: T,
    keychainHolder: KeychainHolder
): File {
    // Get the encrypted file
    val encryptedFile = media.getUri().toFile()
    
    // Decrypt the media
    val encryptedMedia = with(keychainHolder) {
        encryptedFile.decryptKotlin<EncryptedMedia>()
    }
    
    // Create temp file with appropriate extension
    val extension = when {
        media.mimeType.startsWith("image/") -> when (media.mimeType) {
            "image/jpeg" -> ".jpg"
            "image/png" -> ".png"
            "image/gif" -> ".gif"
            "image/webp" -> ".webp"
            else -> ".jpg"
        }
        media.mimeType.startsWith("video/") -> when (media.mimeType) {
            "video/mp4" -> ".mp4"
            "video/avi" -> ".avi"
            "video/mov" -> ".mov"
            "video/webm" -> ".webm"
            else -> ".vid"
        }
        else -> ".mp4"
    }
    
    val tempFile = File.createTempFile(
        "shared_${media.label.replace("[^a-zA-Z0-9]".toRegex(), "_")}_",
        extension
    )
    
    // Write decrypted bytes to temp file
    FileOutputStream(tempFile).use { outputStream ->
        outputStream.write(encryptedMedia.bytes)
        outputStream.flush()
    }
    
    return tempFile
}