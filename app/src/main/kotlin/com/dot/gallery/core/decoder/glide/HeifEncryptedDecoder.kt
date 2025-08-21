package com.dot.gallery.core.decoder.glide

import android.graphics.Bitmap
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapResource
import com.radzivon.bartoshyk.avif.coder.HeifCoder

class HeifEncryptedDecoder(
    private val bitmapPool: BitmapPool
) : ResourceDecoder<EncryptedMediaStream, Bitmap> {

    private val coder = HeifCoder()

    override fun handles(source: EncryptedMediaStream, options: Options): Boolean {
        if (source.isVideo) return false
        val mime = source.mimeType.lowercase()
        return mime.contains("heif") || mime.contains("heic") || mime.contains("avif") || mime.contains("avis")
    }

    override fun decode(
        source: EncryptedMediaStream,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Bitmap>? {
        val bytes = source.bytes
        val size = coder.getSize(bytes)
        val targetW = if (width > 0) width else size!!.width
        val targetH = if (height > 0) height else size!!.height
        val bitmap = coder.decodeSampled(bytes, targetW, targetH)
        return BitmapResource.obtain(bitmap, bitmapPool)
    }
}