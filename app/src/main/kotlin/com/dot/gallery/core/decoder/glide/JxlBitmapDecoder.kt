package com.dot.gallery.core.decoder.glide

import android.graphics.Bitmap
import com.awxkee.jxlcoder.JxlCoder
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapResource
import java.io.InputStream

class JxlBitmapDecoder(
    private val bitmapPool: BitmapPool
) : ResourceDecoder<InputStream, Bitmap> {

    override fun handles(source: InputStream, options: Options): Boolean {
        source.mark(12)
        val head = ByteArray(12)
        val read = source.read(head)
        source.reset()
        if (read < 2) return false
        // Raw JPEG XL codestream: starts with 0xFF 0x0A
        if (head[0] == 0xFF.toByte() && head[1] == 0x0A.toByte()) return true
        // ISO-BMFF container: 12-byte signature box with type 'JXL ' at offset 4
        if (read >= 12 &&
            head[0] == 0x00.toByte() && head[1] == 0x00.toByte() &&
            head[2] == 0x00.toByte() && head[3] == 0x0C.toByte() &&
            head[4] == 0x4A.toByte() && head[5] == 0x58.toByte() &&
            head[6] == 0x4C.toByte() && head[7] == 0x20.toByte() &&
            head[8] == 0x0D.toByte() && head[9] == 0x0A.toByte() &&
            head[10] == 0x87.toByte() && head[11] == 0x0A.toByte()
        ) return true
        return false
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