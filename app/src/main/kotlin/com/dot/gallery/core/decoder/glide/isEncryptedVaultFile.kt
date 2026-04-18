package com.dot.gallery.core.decoder.glide

import android.content.Context
import com.dot.gallery.BuildConfig
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import java.io.File

fun isEncryptedVaultFile(file: File): Boolean =
    file.path.contains(BuildConfig.APPLICATION_ID) && file.extension == "enc"

/**
 * Decrypts a vault file and returns bytes + mime type.
 * Handles both portable (VLTv1) and legacy (EncryptedFile) formats.
 */
fun decryptVaultFile(file: File, context: Context): EncryptedMediaStream {
    val keychainHolder = KeychainHolder(context)
    val decrypted = keychainHolder.decryptVaultMedia(file)
    return EncryptedMediaStream(
        bytes = decrypted.bytes,
        mimeType = decrypted.mimeType,
        isVideo = decrypted.mimeType.startsWith("video")
    )
}