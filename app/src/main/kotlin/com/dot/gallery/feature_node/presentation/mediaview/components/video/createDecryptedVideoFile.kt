package com.dot.gallery.feature_node.presentation.mediaview.components.video

import androidx.core.net.toFile
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Media.EncryptedMedia
import com.dot.gallery.feature_node.domain.util.getUri
import java.io.File
import java.io.FileOutputStream

fun <T: Media> createDecryptedVideoFile(keychainHolder: KeychainHolder, decryptedMedia: T): File {
    // Create a temporary file
    val tempFile = File.createTempFile("${decryptedMedia.id}.temp", null)
    val encryptedFile = decryptedMedia.getUri().toFile()
    val encryptedMedia = with(keychainHolder) {
        encryptedFile.decryptKotlin<EncryptedMedia>()
    }

    // Write the ByteArray to the temporary file
    FileOutputStream(tempFile).use { fileOutputStream ->
        fileOutputStream.write(encryptedMedia.bytes)
        fileOutputStream.flush()
    }

    return tempFile
}