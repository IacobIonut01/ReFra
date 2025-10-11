package com.dot.gallery.core.decoder.glide

import android.graphics.Bitmap
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool

/**
 * Bridges EncryptedMediaSource to existing EncryptedMediaStream based decoders for HEIF/JXL while migration proceeds.
 */
class HeifEncryptedSourceDecoder(private val pool: BitmapPool) : ResourceDecoder<EncryptedMediaSource, Bitmap> {
    private val delegate = HeifEncryptedDecoder(pool)
    override fun handles(source: EncryptedMediaSource, options: Options): Boolean =
        !source.isVideo && HeifMime.isHeifMime(source.mimeType.lowercase())
    override fun decode(source: EncryptedMediaSource, width: Int, height: Int, options: Options): Resource<Bitmap>? =
        delegate.decode(source.asMediaStream(), width, height, options)
}

class JxlEncryptedSourceDecoder(private val pool: BitmapPool) : ResourceDecoder<EncryptedMediaSource, Bitmap> {
    private val delegate = JxlEncryptedDecoder(pool)
    override fun handles(source: EncryptedMediaSource, options: Options): Boolean =
        !source.isVideo && source.mimeType.equals("image/jxl", true)
    override fun decode(source: EncryptedMediaSource, width: Int, height: Int, options: Options): Resource<Bitmap>? =
        delegate.decode(source.asMediaStream(), width, height, options)
}
