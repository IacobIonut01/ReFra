package com.dot.gallery.core.decoder.glide

import android.graphics.Bitmap
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool

class HeifEncryptedDecoder(
    private val bitmapPool: BitmapPool
) : ResourceDecoder<EncryptedMediaStream, Bitmap> {

    private val core = HeifDecoderCore(bitmapPool, "HeifEncryptedDecoder")

    override fun handles(source: EncryptedMediaStream, options: Options): Boolean {
        if (source.isVideo) return false
    return HeifMime.isHeifMime(source.mimeType.lowercase())
    }

    override fun decode(
        source: EncryptedMediaStream,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Bitmap>? {
        val result = core.decodeBytes(source.bytes, width, height, source.mimeType)
        return result.resource
    }
}