package com.dot.gallery.core.decoder.glide

import android.graphics.Bitmap
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapResource

/**
 * HEIF/AVIF decoder operating on MimeInputStream so we can rely on MIME instead of header scan.
 */
class HeifMimeInputStreamDecoder(
    private val bitmapPool: BitmapPool
) : ResourceDecoder<MimeInputStream, Bitmap> {
    private val core = HeifDecoderCore(bitmapPool, "HeifMimeDecoder")

    override fun handles(source: MimeInputStream, options: Options): Boolean =
        HeifMime.isHeifMime(source.mimeType)

    override fun decode(
        source: MimeInputStream,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Bitmap>? {
        return try {
            val bytes = source.inputStream.readBytes()
            core.decodeBytes(bytes, width, height, source.mimeType).resource
        } catch (e: Throwable) {
            android.util.Log.e("HeifMimeDecoder", "stream read failed mime=${source.mimeType}: ${e.message}", e)
            null
        }
    }
}
