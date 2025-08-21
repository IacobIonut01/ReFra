package com.dot.gallery.core.decoder.glide

import android.graphics.Bitmap
import com.awxkee.jxlcoder.JxlCoder
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapResource

class JxlEncryptedDecoder(
    private val bitmapPool: BitmapPool
) : ResourceDecoder<EncryptedMediaStream, Bitmap> {

    override fun handles(source: EncryptedMediaStream, options: Options): Boolean {
        return !source.isVideo && source.mimeType.equals("image/jxl", ignoreCase = true)
    }

    override fun decode(
        source: EncryptedMediaStream,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Bitmap>? {
        val bytes = source.bytes
        val size = JxlCoder.getSize(bytes)
        val tw = if (width > 0) width else size!!.width
        val th = if (height > 0) height else size!!.height
        val bmp = JxlCoder.decodeSampled(bytes, tw, th)
        return BitmapResource.obtain(bmp, bitmapPool)
    }
}