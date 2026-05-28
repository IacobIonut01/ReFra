package com.dot.gallery.core.decoder.glide

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import com.dot.gallery.core.decryption.DecryptManagerEntryPoint
import com.dot.gallery.core.decryption.DecryptResult
import com.dot.gallery.core.decryption.MediaMetadataCacheEntry
import com.dot.gallery.core.memory.AdaptiveDecryptConfigEntryPoint
import com.dot.gallery.core.metrics.MetricsCollectorEntryPoint
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import dagger.hilt.android.EntryPointAccessors
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
        val result = try {
            val ep = EntryPointAccessors.fromApplication(
                contextRef.applicationContext,
                DecryptManagerEntryPoint::class.java
            )
            ep.decryptManager().decrypt(file)
        } catch (_: Throwable) {
            val keychainHolder = KeychainHolder(contextRef)
            val d = keychainHolder.decryptVaultMedia(file)
            DecryptResult(d.readBytes(), d.mimeType)
        }
        return result.bytes.inputStream()
    }

    /** Materialize as EncryptedMediaStream (byte array) for decoders that still require bytes. */
    fun asMediaStream(): EncryptedMediaStream {
        val bytes = smallBytes ?: tempFile?.readBytes() ?: run {
            val result = try {
                val ep = EntryPointAccessors.fromApplication(
                    contextRef.applicationContext,
                    DecryptManagerEntryPoint::class.java
                )
                ep.decryptManager().decrypt(file)
            } catch (_: Throwable) {
                val keychainHolder = KeychainHolder(contextRef)
                val d = keychainHolder.decryptVaultMedia(file)
                DecryptResult(d.readBytes(), d.mimeType)
            }
            result.bytes
        }
        return EncryptedMediaStream(bytes, mimeType, isVideo)
    }
}

private const val FALLBACK_SMALL_DECRYPT_THRESHOLD = 2 * 1024 * 1024 // 2MB fallback if adaptive not available

internal fun createEncryptedMediaSource(context: Context, file: File): EncryptedMediaSource {
    // Obtain decrypt manager via Hilt entry point if available, else fallback to direct decrypt.
    val decryptResult = try {
        val ep = EntryPointAccessors.fromApplication(
            context.applicationContext,
            DecryptManagerEntryPoint::class.java
        )
        ep.decryptManager().decrypt(file)
    } catch (t: Throwable) {
        val keychainHolder = KeychainHolder(context)
        val d = keychainHolder.decryptVaultMedia(file)
        DecryptResult(d.readBytes(), d.mimeType)
    }
    val mime = decryptResult.mimeType
    val isVideo = mime.startsWith("video")
    val bytes = decryptResult.bytes
    val size = bytes.size.toLong()
    // Extract lightweight metadata (width/height and duration for video) and write to sidecar cache (best-effort)
    runCatching {
        val ep = EntryPointAccessors.fromApplication(
            context.applicationContext,
            DecryptManagerEntryPoint::class.java
        )
        val sidecar = ep.sidecar()
        val metrics = runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                MetricsCollectorEntryPoint::class.java
            ).metrics()
        }.getOrNull()
        val existing = sidecar.read(sidecar.keyForFile(file))
        if (existing == null) {
            var width: Int? = null
            var height: Int? = null
            var duration: Long? = null
            if (isVideo) {
                MediaMetadataRetriever().apply {
                    try {
                        // Need a file: if large we'll spill soon anyway; for now create a temp or reuse below.
                        val tmpForMeta = if (size <= FALLBACK_SMALL_DECRYPT_THRESHOLD) {
                            val tmp = File.createTempFile("vault_meta_vid_", ".tmp", context.cacheDir)
                            FileOutputStream(tmp).use { it.write(bytes) }
                            tmp
                        } else null // For large we will create tmp below; defer reading after spill.
                        val path = tmpForMeta?.absolutePath
                        if (path != null) setDataSource(path)
                        duration = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                        width = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                        height = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                    } catch (_: Throwable) { }
                    finally { try { release() } catch (_: Throwable) {} }
                }
            } else {
                // Image: parse dimensions via BitmapFactory decode bounds to avoid full decode
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                if (opts.outWidth > 0 && opts.outHeight > 0) {
                    width = opts.outWidth
                    height = opts.outHeight
                }
            }
            sidecar.write(
                MediaMetadataCacheEntry(
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
        val ep = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AdaptiveDecryptConfigEntryPoint::class.java
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
