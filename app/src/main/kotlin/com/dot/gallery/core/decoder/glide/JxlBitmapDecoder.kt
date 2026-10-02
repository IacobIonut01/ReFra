package com.dot.gallery.core.decoder.glide

import android.graphics.Bitmap
import com.awxkee.jxlcoder.JxlCoder
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapResource
import com.dot.gallery.core.decoder.format.ImageFormatSniffer
import java.io.InputStream

class JxlBitmapDecoder(
    private val bitmapPool: BitmapPool
) : ResourceDecoder<InputStream, Bitmap> {

    override fun handles(source: InputStream, options: Options): Boolean {
        source.mark(12)
        val head = ByteArray(12)
        val read = source.read(head)
        source.reset()
        return ImageFormatSniffer.isJxl(head, read)
    }

    override fun decode(
        source: InputStream,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Bitmap>? {
        val bytes = source.readBytes()
        val size = JxlCoder.getSize(bytes)
        val tw = if (width > 0) width else size!!.width
        val th = if (height > 0) height else size!!.height
        val bmp = JxlCoder.decodeSampled(bytes, tw, th)
        return BitmapResource.obtain(bmp, bitmapPool)
    }
}