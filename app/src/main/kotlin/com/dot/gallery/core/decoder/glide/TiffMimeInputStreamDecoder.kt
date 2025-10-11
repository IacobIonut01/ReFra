package com.dot.gallery.core.decoder.glide

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapResource
import java.io.ByteArrayInputStream

/**
 * Simple TIFF decoder relying on platform ImageDecoder (API 28+). Many devices support baseline
 * TIFF via ImageDecoder. If unsupported or <28 we return null allowing Glide fallback chain.
 */
class TiffMimeInputStreamDecoder(
    private val bitmapPool: BitmapPool
): ResourceDecoder<MimeInputStream, Bitmap> {

    override fun handles(source: MimeInputStream, options: Options): Boolean {
        val match = TiffMime.isTiff(source.mimeType)
        if (match) Log.v("TiffMimeDecoder", "handles() mime=${source.mimeType}")
        return match
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun decode(
        source: MimeInputStream,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Bitmap>? {
        if (Build.VERSION.SDK_INT < 28) {
            Log.w("TiffMimeDecoder", "decode() skipping – API < 28")
            return null
        }
        return try {
            val data = source.inputStream.readBytes()
            // Size guard: avoid attempting full decode of extremely large TIFFs (> 64MB here)
            if (data.size > 64 * 1024 * 1024) {
                Log.w("TiffMimeDecoder", "decode() abort – data too large=${data.size} bytes; trying thumbnail fallback")
                return attemptExifThumbnail(data)
            }

            if (isBigTiff(data)) {
                Log.w("TiffMimeDecoder", "decode() BigTIFF detected – ImageDecoder unsupported; trying thumbnail fallback")
                return attemptExifThumbnail(data)
            }

            val src = ImageDecoder.createSource(data)
            val bmp = ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                if (width > 0 && height > 0) decoder.setTargetSize(width, height)
            }
            Log.d("TiffMimeDecoder", "decode() success mime=${source.mimeType} full result=${bmp.width}x${bmp.height}")
            BitmapResource.obtain(bmp, bitmapPool)
        } catch (e: Throwable) {
            Log.e("TiffMimeDecoder", "decode() failed primary path mime=${source.mimeType}: ${e.message}; attempting thumbnail", e)
            attemptExifThumbnailSafely(source)
        }
    }

    private fun isBigTiff(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        // TIFF signatures: II 2A 00 or MM 00 2A. BigTIFF uses II 2B 00 08 00 00 00 00 (or MM 00 2B 00 08 00 00 00 00)
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        val b2 = bytes[2].toInt() and 0xFF
        val b3 = bytes[3].toInt() and 0xFF
        // Check endian markers
        val little = b0 == 0x49 && b1 == 0x49
        val big = b0 == 0x4D && b1 == 0x4D
        if (!little && !big) return false
        // BigTIFF has magic 43 (0x2B) as third or fourth depending on endianness pattern
        // Little endian: II 2B 00 08 00 00 00 00
        // Big endian:   MM 00 2B 00 08 00 00 00
        return (little && b2 == 0x2B && b3 == 0x00) || (big && b2 == 0x00 && b3 == 0x2B)
    }

    private fun attemptExifThumbnailSafely(source: MimeInputStream): Resource<Bitmap>? {
        return try {
            val data = source.inputStream.readBytes()
            attemptExifThumbnail(data)
        } catch (e: Throwable) {
            Log.e("TiffMimeDecoder", "thumbnail fallback failed (stream reread) mime=${source.mimeType}: ${e.message}")
            null
        }
    }

    private fun attemptExifThumbnail(data: ByteArray): Resource<Bitmap>? {
        return try {
            val exif = ExifInterface(ByteArrayInputStream(data))
            if (exif.hasThumbnail()) {
                val thumb = exif.thumbnailBitmap
                if (thumb != null) {
                    Log.d("TiffMimeDecoder", "decode() using EXIF thumbnail fallback ${thumb.width}x${thumb.height}")
                    return BitmapResource.obtain(thumb, bitmapPool)
                }
            }
            Log.w("TiffMimeDecoder", "decode() no EXIF thumbnail available")
            null
        } catch (e: Throwable) {
            Log.e("TiffMimeDecoder", "EXIF thumbnail extraction failed: ${e.message}")
            null
        }
    }
}
