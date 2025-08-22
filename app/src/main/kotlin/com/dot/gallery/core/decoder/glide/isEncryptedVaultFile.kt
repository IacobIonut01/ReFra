package com.dot.gallery.core.decoder.glide

import android.content.Context
import com.dot.gallery.BuildConfig
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.domain.model.Media
import java.io.File

fun isEncryptedVaultFile(file: File): Boolean =
    file.path.contains(BuildConfig.APPLICATION_ID) && file.extension == "enc"

/**
 * Decrypts a vault file and returns bytes + mime type.
 * Relies on your existing decryptKotlin<Media.EncryptedMedia>() extension.
 */
fun decryptVaultFile(file: File, context: Context): EncryptedMediaStream {
    val keychainHolder = KeychainHolder(context)
    val encrypted = with(keychainHolder) {
        file.decryptKotlin<Media.EncryptedMedia>()
    }
    val mime = encrypted.mimeType
    val isVideo = mime.startsWith("video")
    return EncryptedMediaStream(
        bytes = encrypted.bytes,
        mimeType = mime,
        isVideo = isVideo
    )
}