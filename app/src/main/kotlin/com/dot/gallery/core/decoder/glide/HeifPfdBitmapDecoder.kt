package com.dot.gallery.core.decoder.glide

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.util.Log
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapResource
import com.radzivon.bartoshyk.avif.coder.HeifCoder
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

    override fun handles(source: ParcelFileDescriptor, options: Options): Boolean {
        // Duplicate FD so we can read without advancing the original position.
        val dup: ParcelFileDescriptor = try {
            ParcelFileDescriptor.dup(source.fileDescriptor)
        } catch (e: IOException) {
            Log.d(TAG, "handles() dup fail: ${e.message}")
            return false
        }
        val header = ByteArray(128)
        val readBytes = try {
            FileInputStream(dup.fileDescriptor).use { it.read(header) }
        } catch (e: Exception) {
            Log.d(TAG, "handles() read fail: ${e.message}")
            dup.close()
            return false
        }
        dup.close()
        if (readBytes < 12) {
            Log.d(TAG, "handles() too small read=$readBytes")
            return false
        }
        val brand = HeifSniffer.findBrand(header, readBytes)
        val ok = brand != null
        if (!ok) {
            Log.d(TAG, "handles()=false brand=null sample=${HeifSniffer.hexSample(header, readBytes)}")
        } else {
            Log.d(TAG, "handles()=true brand=$brand")
        }
        return ok
    }

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
            val bmp = coder.decodeSampled(bytes, tw, th)
            Log.d(TAG, "decode() size=${size.width}x${size.height} -> ${bmp.width}x${bmp.height}")
            return BitmapResource.obtain(bmp, bitmapPool)
        }
    }
    companion object { private const val TAG = "HeifPfdBitmapDecoder" }
}
