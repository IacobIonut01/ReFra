package com.dot.gallery.core.decoder.glide

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapResource
import com.dot.gallery.feature_node.presentation.util.printDebug
import com.radzivon.bartoshyk.avif.coder.HeifCoder
import com.radzivon.bartoshyk.avif.coder.PreferredColorConfig
import java.io.FileInputStream
import java.io.IOException

/**
 * HEIF/AVIF decoder for the MediaStore ParcelFileDescriptor load path.
 * Some URIs (esp. thumbnails) resolve to ParcelFileDescriptor rather than InputStream, so our
 * InputStream decoder never runs. This bridges that gap.
 */
class HeifPfdBitmapDecoder(
    private val bitmapPool: BitmapPool
) : ResourceDecoder<ParcelFileDescriptor, Bitmap> {

    private val coder = HeifCoder()

    override fun handles(source: ParcelFileDescriptor, options: Options): Boolean =
        source.sniffsDeepHeif()

    override fun decode(
        source: ParcelFileDescriptor,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Bitmap>? {
        FileInputStream(source.fileDescriptor).use { fis ->
            val bytes = fis.readBytes()
            val size = coder.getSize(bytes) ?: return null
            val tw = if (width > 0) width else size.width
            val th = if (height > 0) height else size.height
            val bmp = coder.decodeSampled(bytes, tw, th, PreferredColorConfig.RGBA_8888)
            printDebug(TAG, "decode() size=${size.width}x${size.height} -> ${bmp.width}x${bmp.height}")
            return BitmapResource.obtain(bmp, bitmapPool)
        }
    }
    companion object { private const val TAG = "decode.heif" }
}
