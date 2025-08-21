package com.dot.gallery.core.decoder.glide

import com.dot.gallery.BuildConfig
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.domain.model.Media
import java.io.File

data class DecryptedPayload(
    val bytes: ByteArray,
    val mimeType: String,
    val width: Int? = null,
    val height: Int? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DecryptedPayload

        if (width != other.width) return false
        if (height != other.height) return false
        if (!bytes.contentEquals(other.bytes)) return false
        if (mimeType != other.mimeType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = width ?: 0
        result = 31 * result + (height ?: 0)
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        return result
    }
}

fun isEncryptedVaultPath(file: File): Boolean =
    file.path.contains(BuildConfig.APPLICATION_ID)

/**
 * Decrypt file (image or video) into bytes. Reuses existing extension:
 * file.decryptKotlin<EncryptedMedia>() from your project (not shown here).
 */
@Suppress("UNCHECKED_CAST")
fun decryptMediaFile(file: File, keychainHolder: KeychainHolder): DecryptedPayload {
    val encrypted = with(keychainHolder) {
        file.decryptKotlin<Media.EncryptedMedia>()
    }
    return DecryptedPayload(
        bytes = encrypted.bytes,
        mimeType = encrypted.mimeType
    )
}