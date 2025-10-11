package com.dot.gallery.core.decoder.glide

import android.content.Context
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.domain.model.Media
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference

/**
 * Streaming representation of an encrypted media file.
 * Provides a lambda to open a fresh decrypted InputStream on demand (for Glide rewinds or retries).
 */
data class EncryptedMediaSource(
    val file: File,
    val mimeType: String,
    val isVideo: Boolean,
    val sizeBytes: Long,
    private val smallBytes: ByteArray?,
    /** Public so streaming decoders can reuse without re-spilling. May be null if content is small. */
    val tempFile: File?,
    private val decryptOnceRef: AtomicReference<Boolean>,
    private val contextRef: Context
) {
    /** Returns an InputStream over decrypted content (bytes array or temp file). */
    fun openStream(): InputStream {
        smallBytes?.let { return it.inputStream() }
        tempFile?.let { return it.inputStream() }
        // Fallback: decrypt on demand (should rarely happen if created correctly)
        val keychainHolder = KeychainHolder(contextRef)
        val again = with(keychainHolder) { file.decryptKotlin<Media.EncryptedMedia>() }
        return again.bytes.inputStream()
    }

    /** Materialize as EncryptedMediaStream (byte array) for decoders that still require bytes. */
    fun asMediaStream(): EncryptedMediaStream {
        val bytes = smallBytes ?: tempFile?.readBytes() ?: run {
            val keychainHolder = KeychainHolder(contextRef)
            val enc = with(keychainHolder) { file.decryptKotlin<Media.EncryptedMedia>() }
            enc.bytes
        }
        return EncryptedMediaStream(bytes, mimeType, isVideo)
    }
}

private const val FALLBACK_SMALL_DECRYPT_THRESHOLD = 2 * 1024 * 1024 // 2MB fallback if adaptive not available

internal fun createEncryptedMediaSource(context: Context, file: File): EncryptedMediaSource {
    // Obtain decrypt manager via Hilt entry point if available, else fallback to direct decrypt.
    val decryptResult = try {
        val ep = dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.dot.gallery.core.decryption.DecryptManagerEntryPoint::class.java
        )
        ep.decryptManager().decrypt(file)
    } catch (t: Throwable) {
        val keychainHolder = KeychainHolder(context)
        val enc = with(keychainHolder) { file.decryptKotlin<Media.EncryptedMedia>() }
        com.dot.gallery.core.decryption.DecryptResult(enc.bytes, enc.mimeType)
    }
    val mime = decryptResult.mimeType
    val isVideo = mime.startsWith("video")
    val bytes = decryptResult.bytes
    val size = bytes.size.toLong()
    // Extract lightweight metadata (width/height and duration for video) and write to sidecar cache (best-effort)
    runCatching {
        val ep = dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.dot.gallery.core.decryption.DecryptManagerEntryPoint::class.java
        )
        val sidecar = ep.sidecar()
        val metrics = runCatching {
            dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                com.dot.gallery.core.metrics.MetricsCollectorEntryPoint::class.java
            ).metrics()
        }.getOrNull()
        val existing = sidecar.read(sidecar.keyForFile(file))
        if (existing == null) {
            var width: Int? = null
            var height: Int? = null
            var duration: Long? = null
            if (isVideo) {
                android.media.MediaMetadataRetriever().apply {
                    try {
                        // Need a file: if large we'll spill soon anyway; for now create a temp or reuse below.
                        val tmpForMeta = if (size <= FALLBACK_SMALL_DECRYPT_THRESHOLD) {
                            val tmp = File.createTempFile("vault_meta_vid_", ".tmp", context.cacheDir)
                            FileOutputStream(tmp).use { it.write(bytes) }
                            tmp
                        } else null // For large we will create tmp below; defer reading after spill.
                        val path = tmpForMeta?.absolutePath
                        if (path != null) setDataSource(path)
                        duration = extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                        width = extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                        height = extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                    } catch (_: Throwable) { }
                    finally { try { release() } catch (_: Throwable) {} }
                }
            } else {
                // Image: parse dimensions via BitmapFactory decode bounds to avoid full decode
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                if (opts.outWidth > 0 && opts.outHeight > 0) {
                    width = opts.outWidth
                    height = opts.outHeight
                }
            }
            sidecar.write(
                com.dot.gallery.core.decryption.MediaMetadataCacheEntry(
                    path = file.path,
                    mimeType = mime,
                    width = width,
                    height = height,
                    durationMs = duration
                )
            )
            metrics?.incSidecarWrite()
        } else {
            metrics?.incSidecarRead()
        }
    }
    val adaptiveThreshold = runCatching {
        val ep = dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.dot.gallery.core.memory.AdaptiveDecryptConfigEntryPoint::class.java
        )
        ep.adaptiveConfig().threshold()
    }.getOrElse { FALLBACK_SMALL_DECRYPT_THRESHOLD }
    val (smallArray, tempFile) = if (size <= adaptiveThreshold) {
        bytes to null
    } else {
        val tmp = File.createTempFile("vault_stream_", ".tmp", context.cacheDir)
        FileOutputStream(tmp).use { it.write(bytes) }
        null to tmp
    }
    return EncryptedMediaSource(
        file = file,
        mimeType = mime,
        isVideo = isVideo,
        sizeBytes = size,
        smallBytes = smallArray,
        tempFile = tempFile,
        decryptOnceRef = AtomicReference(true),
        contextRef = context.applicationContext
    )
}
