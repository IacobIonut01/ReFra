package com.dot.gallery.core.decoder.glide

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapResource
import java.io.File
import java.io.FileOutputStream

/**
 * Extracts a frame from decrypted video bytes.
 * For large videos consider streaming decryption to a temp file (already what we do here).
 */
class EncryptedVideoFrameDecoder(
    private val bitmapPool: BitmapPool,
    private val tempDirProvider: () -> File,
) : ResourceDecoder<EncryptedMediaStream, Bitmap> {

    override fun handles(source: EncryptedMediaStream, options: Options): Boolean =
        source.isVideo

    override fun decode(
        source: EncryptedMediaStream,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Bitmap>? {
        val tmp = File.createTempFile("vault_vid_", ".tmp", tempDirProvider())
        try {
            FileOutputStream(tmp).use { it.write(source.bytes) }
            val retriever = MediaMetadataRetriever()
            val bmp = try {
                retriever.setDataSource(tmp.absolutePath)
                // Could add percent/time options. Defaults to first key frame.
                retriever.frameAtTime
            } finally {
                retriever.release()
            } ?: return null
            return BitmapResource.obtain(bmp, bitmapPool)
        } finally {
            tmp.delete()
        }
    }
}