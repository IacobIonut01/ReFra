package com.dot.gallery.core.decoder.glide

import android.graphics.Bitmap
import android.content.Context
import android.media.MediaMetadataRetriever
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.Option
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapResource
import com.bumptech.glide.util.pool.GlideTrace
import java.io.File
import java.io.FileOutputStream

/**
 * Streaming video frame decoder that works on [EncryptedMediaSource].
 * If the source already backed large content by a temp file we reuse it; otherwise
 * for small in-memory decrypted bytes we spill to a temp file just for the retriever lifecycle.
 */
class StreamingEncryptedVideoFrameDecoder(
    private val bitmapPool: BitmapPool,
    private val appContext: Context,
    private val tempDirProvider: () -> File,
) : ResourceDecoder<EncryptedMediaSource, Bitmap> {

    companion object {
        // Explicit frame time in microseconds
        val FRAME_TIME_US: Option<Long> = Option.memory("vault.video.frameTimeUs", -1L)
        // Percentage (0f..1f) of video duration
        val FRAME_PERCENT: Option<Float> = Option.memory("vault.video.framePercent", -1f)
        // Retrieval option for MediaMetadataRetriever; defaults to OPTION_CLOSEST_SYNC
        val FRAME_OPTION: Option<Int> = Option.memory("vault.video.frameOption", MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    }

    override fun handles(source: EncryptedMediaSource, options: Options): Boolean = source.isVideo

    override fun decode(
        source: EncryptedMediaSource,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Bitmap>? {
        GlideTrace.beginSection("StreamingEncryptedVideoFrameDecoder.decode")
        try {
            val backingFile: File = source.tempFile ?: run {
                val tmp = File.createTempFile("vault_vid_stream_", ".tmp", tempDirProvider())
                source.openStream().use { input ->
                    FileOutputStream(tmp).use { out ->
                        val pool = runCatching {
                            dagger.hilt.android.EntryPointAccessors.fromApplication(
                                appContext,
                                com.dot.gallery.core.memory.ByteArrayPoolEntryPoint::class.java
                            ).pool()
                        }.getOrNull()
                        val buf = pool?.borrow(32 * 1024) ?: ByteArray(32 * 1024)
                        try {
                            while (true) {
                                val r = input.read(buf)
                                if (r <= 0) break
                                out.write(buf, 0, r)
                            }
                            out.flush()
                        } finally { pool?.recycle(buf) }
                    }
                }
                tmp
            }
            val retriever = MediaMetadataRetriever()
            val bmp = try {
                retriever.setDataSource(backingFile.absolutePath)
                val frameTimeUs = options.get(FRAME_TIME_US).takeIf { it != null && it >= 0 } ?: run {
                    val percent = options.get(FRAME_PERCENT).takeIf { it != null && it in 0f..1f }
                    if (percent != null) {
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull()?.let { (it * 1000L * percent).toLong() }
                    } else null
                }
                val frameOpt = options.get(FRAME_OPTION) ?: MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                if (frameTimeUs != null) {
                    retriever.getFrameAtTime(frameTimeUs, frameOpt)
                } else {
                    retriever.frameAtTime
                }
            } finally {
                retriever.release()
            } ?: return null
            // Only delete if we created the temp file ourselves (when source.tempFile was null)
            val resource = BitmapResource.obtain(bmp, bitmapPool)
            if (source.tempFile == null) backingFile.delete()
            return resource
        } finally {
            GlideTrace.endSection()
        }
    }
}
