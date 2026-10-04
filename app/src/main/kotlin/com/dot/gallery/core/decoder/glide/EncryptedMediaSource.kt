package com.dot.gallery.core.decoder.glide

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import com.dot.gallery.core.decryption.DecryptManagerEntryPoint
import com.dot.gallery.core.decryption.DecryptResult
import com.dot.gallery.core.decryption.MediaMetadataCacheEntry
import com.dot.gallery.core.decryption.VaultDecryptStore
import com.dot.gallery.core.memory.AdaptiveDecryptConfigEntryPoint
import com.dot.gallery.core.metrics.MetricsCollectorEntryPoint
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.presentation.util.printError
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Streaming representation of an encrypted media file.
 * Provides a lambda to open a fresh decrypted InputStream on demand (for Glide rewinds or retries).
 *
 * Large files are backed by the canonical decrypted file in [VaultDecryptStore] (shared,
 * refcounted, budget-evicted) — callers must invoke [release] when done so the store can
 * reclaim the entry (#1282).
 */
data class EncryptedMediaSource(
    val file: File,
    val mimeType: String,
    val isVideo: Boolean,
    val sizeBytes: Long,
    private val smallBytes: ByteArray?,
    /** Decrypted backing file (canonical, shared via VaultDecryptStore). Never delete directly. */
    val tempFile: File?,
    private val releaseAction: (() -> Unit)?,
    private val contextRef: Context
) {
    private val released = AtomicBoolean(false)

    /** Release this source's claim on shared decrypted content. Idempotent. */
    fun release() {
        if (released.compareAndSet(false, true)) releaseAction?.invoke()
    }

    /** Returns an InputStream over decrypted content (bytes array or canonical file). */
    fun openStream(): InputStream {
        smallBytes?.let { return it.inputStream() }
        tempFile?.let { return it.inputStream() }
        // Fallback: borrow the canonical decrypted file; released when the stream closes.
        val handle = VaultDecryptStore.acquire(KeychainHolder(contextRef), file)
        return object : FileInputStream(handle.file) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    handle.release()
                }
            }
        }
    }

    /** Materialize as EncryptedMediaStream (byte array) for decoders that still require bytes. */
    fun asMediaStream(): EncryptedMediaStream {
        val bytes = smallBytes ?: tempFile?.readBytes() ?: run {
            VaultDecryptStore.acquire(KeychainHolder(contextRef), file).let { handle ->
                try {
                    handle.file.readBytes()
                } finally {
                    handle.release()
                }
            }
        }
        return EncryptedMediaStream(bytes, mimeType, isVideo)
    }
}

private const val FALLBACK_SMALL_DECRYPT_THRESHOLD = 2 * 1024 * 1024 // 2MB fallback if adaptive not available

internal fun createEncryptedMediaSource(context: Context, file: File): EncryptedMediaSource {
    val adaptiveThreshold = runCatching {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AdaptiveDecryptConfigEntryPoint::class.java
        ).adaptiveConfig().threshold()
    }.getOrElse { FALLBACK_SMALL_DECRYPT_THRESHOLD }

    // Large files: one canonical decrypted copy shared via VaultDecryptStore — refcounted,
    // budget-evicted, reused by every consumer instead of a private copy per request (#1282).
    if (file.length() > adaptiveThreshold) {
        val handle = VaultDecryptStore.acquire(KeychainHolder(context), file)
        try {
            val mime = handle.mimeType
            val isVideo = mime.startsWith("video")
            writeMetadataSidecar(context, file, mime, isVideo, bytes = null, decryptedFile = handle.file)
            return EncryptedMediaSource(
                file = file,
                mimeType = mime,
                isVideo = isVideo,
                sizeBytes = handle.file.length(),
                smallBytes = null,
                tempFile = handle.file,
                releaseAction = { handle.release() },
                contextRef = context.applicationContext
            )
        } catch (t: Throwable) {
            handle.release()
            throw t
        }
    }

    // Small files: single in-memory decrypt, coalesced + LRU-cached by DecryptManager.
    val decryptResult = try {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            DecryptManagerEntryPoint::class.java
        ).decryptManager().decrypt(file)
    } catch (t: Throwable) {
        val d = KeychainHolder(context).decryptVaultMedia(file)
        try {
            DecryptResult(d.readBytes(), d.mimeType)
        } finally {
            d.cleanup()
        }
    }
    val mime = decryptResult.mimeType
    val isVideo = mime.startsWith("video")
    writeMetadataSidecar(context, file, mime, isVideo, bytes = decryptResult.bytes, decryptedFile = null)
    return EncryptedMediaSource(
        file = file,
        mimeType = mime,
        isVideo = isVideo,
        sizeBytes = decryptResult.bytes.size.toLong(),
        smallBytes = decryptResult.bytes,
        tempFile = null,
        releaseAction = null,
        contextRef = context.applicationContext
    )
}

/**
 * Best-effort width/height/duration sidecar so viewers can size vault media without decrypting.
 * Reads from the decrypted bytes (small path) or the canonical decrypted file (large path);
 * never writes an all-null entry — a failed extraction must not suppress future retries.
 */
private fun writeMetadataSidecar(
    context: Context,
    file: File,
    mime: String,
    isVideo: Boolean,
    bytes: ByteArray?,
    decryptedFile: File?
) {
    runCatching {
        val sidecar = EntryPointAccessors.fromApplication(
            context.applicationContext,
            DecryptManagerEntryPoint::class.java
        ).sidecar()
        val metrics = runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                MetricsCollectorEntryPoint::class.java
            ).metrics()
        }.getOrNull()
        if (sidecar.read(sidecar.keyForFile(file)) != null) {
            metrics?.incSidecarRead()
            return@runCatching
        }
        var width: Int? = null
        var height: Int? = null
        var duration: Long? = null
        if (isVideo) {
            var tmpForMeta: File? = null
            try {
                val path = decryptedFile?.absolutePath ?: bytes?.let { b ->
                    File.createTempFile("vault_meta_vid_", ".tmp", context.cacheDir)
                        .also { tmp ->
                            tmpForMeta = tmp
                            FileOutputStream(tmp).use { it.write(b) }
                        }.absolutePath
                }
                if (path != null) {
                    MediaMetadataRetriever().apply {
                        try {
                            setDataSource(path)
                            duration = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                            width = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                            height = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                        } catch (e: Throwable) {
                            printError("vault.decrypt", "encrypted metadata extract failed", e)
                        } finally {
                            try {
                                release()
                            } catch (e: Throwable) {
                                printError("vault.decrypt", "encrypted metadata retriever release failed", e)
                            }
                        }
                    }
                }
            } finally {
                tmpForMeta?.delete()
            }
        } else {
            // Image: parse dimensions via decode-bounds only
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            when {
                bytes != null -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                decryptedFile != null -> BitmapFactory.decodeFile(decryptedFile.absolutePath, opts)
            }
            if (opts.outWidth > 0 && opts.outHeight > 0) {
                width = opts.outWidth
                height = opts.outHeight
            }
        }
        if (width == null && height == null && duration == null) return@runCatching
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
    }
}
