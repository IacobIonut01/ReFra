package com.dot.gallery.core.decoder.glide

import android.graphics.Bitmap
import android.util.Log
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapResource
import com.radzivon.bartoshyk.avif.coder.HeifCoder
import java.io.InputStream

/**
 * Decodes HEIF/AVIF from an InputStream (already decrypted if using EncryptedFileModelLoader).
 * Relies on HeifCoder (same as SketchHeifDecoder).
 */
class HeifBitmapDecoder(
    private val bitmapPool: BitmapPool
) : ResourceDecoder<InputStream, Bitmap> {

    private val coder = HeifCoder()

    override fun handles(source: InputStream, options: Options): Boolean {
        // Robust ISO BMFF brand sniff (HEIF/AVIF). Layout: size(4) + 'ftyp'(4) + brand(4)
        source.mark(64) // plenty for initial box
        val header = ByteArray(128)
        val read = source.read(header)
        source.reset()
        if (read < 12) return false
        val brand = HeifSniffer.findBrand(header, read)
        val ok = brand != null
        if (!ok) {
            Log.d("HeifBitmapDecoder", "handles()=false brand=null sample=${HeifSniffer.hexSample(header, read)}")
        } else {
            Log.d("HeifBitmapDecoder", "handles()=true brand=$brand")
        }
        return ok
    }

    override fun decode(
        source: InputStream,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Bitmap>? {
        val allBytes = source.readBytes() // TODO: consider bounded read / streaming if memory pressure observed
        val size = coder.getSize(allBytes) // returns null only on invalid data
        val targetW = if (width > 0) width else size!!.width
        val targetH = if (height > 0) height else size!!.height
        val bmp = coder.decodeSampled(allBytes, targetW, targetH)
        Log.d("HeifBitmapDecoder", "decode() size=${size?.width}x${size?.height} -> ${bmp.width}x${bmp.height}")
        return BitmapResource.obtain(bmp, bitmapPool)
    }
}