package com.dot.gallery.core.decoder.glide

/**
 * In-memory representation of a decrypted media asset.
 * bytes      -> decrypted bytes (image frames or full video file content if you choose memory path for short clips)
 * mimeType   -> original (real) MIME type
 * isVideo    -> true if original media is a video
 *
 * For large videos you should decrypt to a temp file instead of holding full bytes.
 * In that case adapt this class to carry a tempFile reference and only load a frame.
 */
data class EncryptedMediaStream(
    val bytes: ByteArray,
    val mimeType: String,
    val isVideo: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedMediaStream

        if (isVideo != other.isVideo) return false
        if (!bytes.contentEquals(other.bytes)) return false
        if (mimeType != other.mimeType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isVideo.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        return result
    }
}