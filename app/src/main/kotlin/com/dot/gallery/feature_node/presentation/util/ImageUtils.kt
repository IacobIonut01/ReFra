/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.util

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.PersistableBundle
import android.provider.MediaStore
import android.widget.Toast
import com.dot.gallery.core.util.SdkCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
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
import androidx.core.graphics.scale
import androidx.core.net.toFile
import com.dot.gallery.BuildConfig
import com.dot.gallery.R
import com.dot.gallery.core.Settings.Misc.rememberExifDateFormat
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.domain.model.InfoRow
import com.dot.gallery.feature_node.domain.model.Media

import com.dot.gallery.feature_node.domain.model.MediaMetadata
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isCloud
import com.dot.gallery.feature_node.domain.util.isEncrypted
import com.dot.gallery.feature_node.presentation.mediaview.components.retrieveMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.dot.gallery.cloud.util.CloudMediaDownloader
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

private const val TAG = "media-ops.share"

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

/**
 * Apply a full 4×5 color matrix to a [Bitmap] using Android's native
 * [android.graphics.ColorMatrixColorFilter].  This produces pixel-identical
 * results to Compose's [androidx.compose.ui.graphics.ColorFilter.colorMatrix].
 */
fun applyColorMatrix(src: Bitmap, matrix: FloatArray): Bitmap {
    val result = createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix))
    }
    canvas.drawBitmap(src, 0f, 0f, paint)
    return result
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
): IntentSenderRequest? = getUri().writeRequest(contentResolver)

fun <T : Media> List<T>.writeRequest(
    contentResolver: ContentResolver,
): IntentSenderRequest? {
    if (!SdkCompat.supportsMediaStoreRequests) return null
    // createWriteRequest rejects URIs that aren't in a media collection (e.g. formats the OS didn't
    // index as images such as JXL/AVIF land in the generic Files collection). Fall back to a direct
    // write attempt instead of crashing.
    return try {
        IntentSenderRequest.Builder(MediaStore.createWriteRequest(contentResolver, map { it.getUri() }))
            .build()
    } catch (e: IllegalArgumentException) {
        printWarning("writeRequest: not a media item, skipping permission request: ${e.message}")
        null
    }
}

fun Uri.writeRequest(
    contentResolver: ContentResolver,
): IntentSenderRequest? {
    if (!SdkCompat.supportsMediaStoreRequests) return null
    return try {
        IntentSenderRequest.Builder(MediaStore.createWriteRequest(contentResolver, arrayListOf(this)))
            .build()
    } catch (e: IllegalArgumentException) {
        printWarning("writeRequest: not a media item, skipping permission request: ${e.message}")
        null
    }
}

fun Uri.trashRequest(contentResolver: ContentResolver): IntentSenderRequest? {
    if (!SdkCompat.supportsTrash) return null
    return try {
        IntentSenderRequest.Builder(MediaStore.createTrashRequest(contentResolver, listOf(this), true))
            .build()
    } catch (e: IllegalArgumentException) {
        printWarning("trashRequest: not a trashable media item: ${e.message}")
        null
    }
}

suspend fun Uri.matchesSha256(contentResolver: ContentResolver, expected: String): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            contentResolver.openInputStream(this@matchesSha256)?.use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
                digest.digest().joinToString("") { "%02x".format(it) } == expected
            } ?: false
        }.getOrDefault(false)
    }

/**
 * Launch an [IntentSenderRequest] or, if [request] is null (API 29 with legacy storage),
 * directly invoke [onGranted] since write permission is already available.
 */
fun ActivityResultLauncher<IntentSenderRequest>.launchWriteRequest(
    request: IntentSenderRequest?,
    onGranted: () -> Unit
) {
    if (request != null) {
        launch(request)
    } else {
        onGranted()
    }
}

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

fun Uri.authorizedUri(context: Context): Uri = when {
    this.toString().startsWith("content://") -> this
    this.scheme == "cloud" -> this // cloud URIs need async resolution; see resolveShareableUri
    else -> FileProvider.getUriForFile(
        context,
        BuildConfig.CONTENT_AUTHORITY,
        this.toFile()
    )
}

suspend fun <T : Media> Context.resolveShareableUri(media: T): Uri = withContext(Dispatchers.IO) {
    val originalUri = media.getUri()
    if (!media.isCloud) {
        return@withContext if (originalUri.toString().startsWith("content://")) {
            originalUri
        } else {
            FileProvider.getUriForFile(this@resolveShareableUri, BuildConfig.CONTENT_AUTHORITY, originalUri.toFile())
        }
    }
    // Download cloud media to a temp file. remoteId may contain slashes (SMB/NFS/WebDAV paths);
    // pathSegments.first() would truncate it to the first folder.
    val remoteId = originalUri.path?.trimStart('/')?.takeIf { it.isNotEmpty() }
        ?: throw IllegalStateException("No remoteId in cloud URI")
    val stream = CloudMediaDownloader.downloadCloudMedia(originalUri)
        ?: throw Exception("Failed to download cloud media")

    val ext = media.label.substringAfterLast('.', "jpg")
    val tempFile = File(cacheDir, "cloud_share_${remoteId.substringAfterLast('/').take(8)}.$ext")
    stream.use { input ->
        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
    }

    FileProvider.getUriForFile(this@resolveShareableUri, BuildConfig.CONTENT_AUTHORITY, tempFile)
}

suspend fun <T : Media> Context.copyMediaToClipboard(media: T) {
    val uri = resolveShareableUri(media)
    withContext(Dispatchers.Main) {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newUri(contentResolver, media.label, uri)
        clipboardManager.setPrimaryClip(clip)
        if (!SdkCompat.showsClipboardConfirmation) {
            Toast.makeText(this@copyMediaToClipboard, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
        }
    }
}

suspend fun <T : Media> Context.copyEncryptedMediaToClipboard(
    media: T,
    keychainHolder: KeychainHolder
) = withContext(Dispatchers.IO) {
    try {
        val tempFile = createDecryptedTempFile(media, keychainHolder, cacheDir)
        val tempUri = FileProvider.getUriForFile(
            this@copyEncryptedMediaToClipboard,
            BuildConfig.CONTENT_AUTHORITY,
            tempFile
        )
        withContext(Dispatchers.Main) {
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newUri(contentResolver, media.label, tempUri)
            clip.description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
            clipboardManager.setPrimaryClip(clip)
            if (!SdkCompat.showsClipboardConfirmation) {
                Toast.makeText(
                    this@copyEncryptedMediaToClipboard,
                    getString(R.string.copied_to_clipboard),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    } catch (e: Exception) {
        printError(TAG, "Failed to copy encrypted media to clipboard", e)
        withContext(Dispatchers.Main) {
            Toast.makeText(
                this@copyEncryptedMediaToClipboard,
                getString(R.string.error_copying_to_clipboard),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

suspend fun <T : Media> Context.shareMedia(media: T) {
    val uri = resolveShareableUri(media)

    withContext(Dispatchers.Main) {
        ShareCompat
            .IntentBuilder(this@shareMedia)
            .setType(media.mimeType)
            .addStream(uri)
            .startChooser()
    }
}

suspend fun <T : Media> Context.shareMedia(mediaList: List<T>) {
    val mimeTypes =
        if (mediaList.find { it.duration != null } != null) {
            if (mediaList.find { it.duration == null } != null) "video/*,image/*" else "video/*"
        } else "image/*"

    val uris = withContext(Dispatchers.IO) {
        mediaList.map { resolveShareableUri(it) }
    }

    withContext(Dispatchers.Main) {
        val shareCompat = ShareCompat
            .IntentBuilder(this@shareMedia)
            .setType(mimeTypes)
        uris.forEach { shareCompat.addStream(it) }
        shareCompat.startChooser()
    }
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
        val tempFile = createDecryptedTempFile(media, keychainHolder, cacheDir)
        
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
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            
            startActivity(shareIntent)
        }
        
    } catch (e: Exception) {
        printError(TAG, "Failed to share encrypted media ${media.label}", e)
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
                    val tempFile = createDecryptedTempFile(media, keychainHolder, cacheDir)
                    val tempUri = FileProvider.getUriForFile(
                        this@shareMediaWithVaultSupport,
                        BuildConfig.CONTENT_AUTHORITY,
                        tempFile
                    )
                    shareStreams.add(tempUri)
                } catch (e: Exception) {
                    // If decryption fails, skip this media or use original URI
                    printError(TAG, "Failed to decrypt ${media.label} for sharing", e)
                    shareStreams.add(media.getUri())
                }
            } else {
                shareStreams.add(resolveShareableUri(media))
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
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                startActivity(shareIntent)
            }
        }
    } catch (e: Exception) {
        printError(TAG, "Failed to share media", e)
        // Fallback to regular sharing
        withContext(Dispatchers.Main) {
            shareMedia(mediaList)
        }
    }
}

/**
 * Create a temporary decrypted file from encrypted vault media
 */
internal fun <T : Media> createDecryptedTempFile(
    media: T,
    keychainHolder: KeychainHolder,
    tempDirectory: File = keychainHolder.filesDir,
): File {
    // Get the encrypted file
    val encryptedFile = media.getUri().toFile()
    
    // Decrypt the media
    val decryptedMedia = keychainHolder.decryptVaultMedia(encryptedFile)

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
    
    var tempFile: File? = null
    return try {
        val createdFile = File.createTempFile(
            "shared_${media.label.replace("[^a-zA-Z0-9]".toRegex(), "_")}_",
            extension,
            tempDirectory,
        )
        tempFile = createdFile
        // Write decrypted content to a worker-managed cache file (streaming for large files).
        decryptedMedia.openStream().use { input ->
            FileOutputStream(createdFile).use { outputStream ->
                input.copyTo(outputStream)
            }
        }
        createdFile
    } catch (error: Throwable) {
        tempFile?.delete()
        throw error
    } finally {
        decryptedMedia.cleanup()
    }
}