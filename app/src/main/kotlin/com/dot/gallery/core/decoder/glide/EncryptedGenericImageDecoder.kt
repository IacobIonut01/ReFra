package com.dot.gallery.core.decoder.glide

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapResource
import com.github.panpf.sketch.decode.internal.ExifOrientationHelper
import java.io.ByteArrayInputStream

/**
 * Fallback decoder for decrypted images that are not handled by Heif/Jxl decoders.
 */
class EncryptedGenericImageDecoder(
    private val bitmapPool: BitmapPool
) : ResourceDecoder<EncryptedMediaStream, Bitmap> {

    override fun handles(source: EncryptedMediaStream, options: Options): Boolean {
        if (source.isVideo) return false
        val mime = source.mimeType.lowercase()
        return mime.startsWith("image/")  // Let more specific decoders run first
    }

    override fun decode(
        source: EncryptedMediaStream,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Bitmap>? {
        val bytes = source.bytes
        // First pass bounds decode for sampling.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val targetW = if (width > 0) width else bounds.outWidth
        val targetH = if (height > 0) height else bounds.outHeight
        val inSample = computeInSampleSize(bounds.outWidth, bounds.outHeight, targetW, targetH)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = inSample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        // Only instantiate ExifInterface if size large enough to plausibly contain orientation,
        // avoiding noisy ExifInterface logs about missing thumbnails on very small images.
        val orientation = if (bytes.size > 512) {
            runCatching {
                ExifInterface(ByteArrayInputStream(bytes))
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
            }.getOrDefault(ExifInterface.ORIENTATION_UNDEFINED)
        } else ExifInterface.ORIENTATION_UNDEFINED
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null
        val oriented = ExifOrientationHelper(orientation).applyToBitmap(bitmap) ?: bitmap
        return BitmapResource.obtain(oriented, bitmapPool)
    }

    private fun computeInSampleSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
        var inSample = 1
        if (srcH > reqH || srcW > reqW) {
            var halfH = srcH / 2
            var halfW = srcW / 2
            while (halfH / inSample >= reqH && halfW / inSample >= reqW) {
                inSample *= 2
            }
        }
        return inSample.coerceAtLeast(1)
    }
}