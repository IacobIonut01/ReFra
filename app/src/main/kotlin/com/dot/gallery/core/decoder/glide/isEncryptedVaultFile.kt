package com.dot.gallery.core.decoder.glide

import android.content.Context
import com.dot.gallery.BuildConfig
import com.dot.gallery.core.decryption.VaultDecryptStore
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import java.io.File

fun isEncryptedVaultFile(file: File): Boolean =
    file.path.contains(BuildConfig.APPLICATION_ID) && file.extension == "enc"

/**
 * Decrypts a vault file and returns bytes + mime type.
 * Handles both portable (VLTv1) and legacy (EncryptedFile) formats.
 * Reads from the canonical shared decrypted file managed by [VaultDecryptStore] (#1282).
 */
fun decryptVaultFile(file: File, context: Context): EncryptedMediaStream {
    val handle = VaultDecryptStore.acquire(KeychainHolder(context), file)
    return try {
        EncryptedMediaStream(
            bytes = handle.file.inputStream().use { it.readBytes() },
            mimeType = handle.mimeType,
            isVideo = handle.mimeType.startsWith("video")
        )
    } finally {
        handle.release()
    }
}