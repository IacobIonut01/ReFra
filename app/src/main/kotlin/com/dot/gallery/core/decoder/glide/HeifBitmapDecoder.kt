package com.dot.gallery.core.decoder.glide

import android.graphics.Bitmap
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
        // Light sniff: read header without consuming full stream
        source.mark(32)
        val header = ByteArray(16)
        val read = source.read(header)
        source.reset()
        if (read <= 0) return false
        val headerStr = header.decodeToString()
        // We fallback to mime detection via option if provided; else naive heuristics
        return headerStr.contains("ftypheic") ||
                headerStr.contains("ftypheix") ||
                headerStr.contains("ftypmif1") ||
                headerStr.contains("ftypavif")
    }

    override fun decode(
        source: InputStream,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Bitmap>? {
        val allBytes = source.readBytes()
        val size = coder.getSize(allBytes)
        val targetW = if (width > 0) width else size!!.width
        val targetH = if (height > 0) height else size!!.height
        val bmp = coder.decodeSampled(allBytes, targetW, targetH)
        return BitmapResource.obtain(bmp, bitmapPool)
    }
}