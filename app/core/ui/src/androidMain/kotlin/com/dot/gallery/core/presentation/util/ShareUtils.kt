/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PersistableBundle
import android.widget.Toast
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import com.dot.gallery.cloud.util.CloudMediaDownloadHook
import com.dot.gallery.core.AppIdentity
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.core.util.or
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.domain.uri.path
import com.dot.gallery.domain.uri.scheme
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.domain.util.isCloud
import com.dot.gallery.domain.util.isEncrypted
import com.dot.gallery.data.data_source.KeychainHolder
import com.dot.gallery.core.logging.printError
import com.dot.gallery.core.ui.generated.resources.Res
import com.dot.gallery.core.ui.generated.resources.copied_to_clipboard
import com.dot.gallery.core.ui.generated.resources.error_copying_to_clipboard
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TAG = "media-ops.share"

object FileProviderShareDirs {
    const val ROOT_DIRECTORY = "share"
    const val MEDIA_DIRECTORY = "media"
    const val CUTOUT_DIRECTORY = "cutout"
    const val VISUAL_SEARCH_DIRECTORY = "visual_search"
    const val EDITOR_SUBJECT_DIRECTORY = "editor_subject"
    const val LOGS_DIRECTORY = "logs"

    fun cache(context: Context, child: String): File =
        File(context.cacheDir, "$ROOT_DIRECTORY/$child").apply { mkdirs() }

    fun files(baseDir: File, child: String): File =
        File(baseDir, "$ROOT_DIRECTORY/$child").apply { mkdirs() }
}

fun Uri.authorizedUri(context: Context): Uri = when {
    this.toString().startsWith("content://") -> this
    this.scheme == "cloud" -> this // cloud URIs need async resolution; see resolveShareableUri
    else -> FileProvider.getUriForFile(
        context,
        AppIdentity.contentAuthority,
        this.toFile()
    )
}

suspend fun <T : Media> Context.resolveShareableUri(media: T): Uri = withContext(Dispatchers.IO) {
    val originalUri = media.getUri()
    if (!media.isCloud) {
        return@withContext if (originalUri.toString().startsWith("content://")) {
            originalUri
        } else {
            FileProvider.getUriForFile(this@resolveShareableUri, AppIdentity.contentAuthority, originalUri.toFile())
        }
    }
    // Download cloud media to a temp file. remoteId may contain slashes (SMB/NFS/WebDAV paths);
    // pathSegments.first() would truncate it to the first folder.
    val remoteId = originalUri.path?.trimStart('/')?.takeIf { it.isNotEmpty() }
        ?: throw IllegalStateException("No remoteId in cloud URI")
    val stream = CloudMediaDownloadHook.downloadCloudMedia(originalUri)
        ?: throw Exception("Failed to download cloud media")

    val ext = media.label.substringAfterLast('.', "jpg")
    val tempFile = File(
        FileProviderShareDirs.cache(this@resolveShareableUri, FileProviderShareDirs.MEDIA_DIRECTORY),
        "cloud_share_${remoteId.substringAfterLast('/').take(8)}.$ext",
    )
    stream.use { input ->
        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
    }

    FileProvider.getUriForFile(this@resolveShareableUri, AppIdentity.contentAuthority, tempFile)
}

suspend fun <T : Media> Context.copyMediaToClipboard(media: T) {
    val uri = resolveShareableUri(media)
    withContext(Dispatchers.Main) {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newUri(contentResolver, media.label, uri)
        clipboardManager.setPrimaryClip(clip)
        if (!SdkCompat.showsClipboardConfirmation) {
            Toast.makeText(this@copyMediaToClipboard, getString(Res.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
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
            AppIdentity.contentAuthority,
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
                    getString(Res.string.copied_to_clipboard),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    } catch (e: Exception) {
        printError(TAG, "Failed to copy encrypted media to clipboard", e)
        withContext(Dispatchers.Main) {
            Toast.makeText(
                this@copyEncryptedMediaToClipboard,
                getString(Res.string.error_copying_to_clipboard),
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
            AppIdentity.contentAuthority,
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
                        AppIdentity.contentAuthority,
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
fun <T : Media> createDecryptedTempFile(
    media: T,
    keychainHolder: KeychainHolder,
    tempDirectory: File = keychainHolder.filesDir,
): File {
    val shareDirectory = FileProviderShareDirs.files(tempDirectory, FileProviderShareDirs.MEDIA_DIRECTORY)
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
            shareDirectory,
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
