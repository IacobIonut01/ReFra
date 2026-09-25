package com.dot.gallery.core.decoder.glide

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.util.Log
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapResource
import com.dot.gallery.core.decoder.format.HeifBitstreamProbe
import com.radzivon.bartoshyk.avif.coder.HeifCoder
import com.radzivon.bartoshyk.avif.coder.PreferredColorConfig
import java.io.FileInputStream
import java.io.InputStream

/** Head bytes read for the ISO-BMFF `ftyp` brand sniff (matches HeifSniffer's window). */
internal const val HEIF_SNIFF_BYTES = 512

/** Bounded head window for the av1C/pixi depth probe (HEIF-branded sources only). */
internal const val HEIF_PROBE_BYTES = 256 * 1024

/**
 * #1244: two-stage ">10-bit HEIF" check for markable streams. Stage one sniffs the `ftyp`
 * brand on a small window so non-HEIF streams exit cheaply; stage two reads the bounded head
 * for the bitstream probe. The stream position is restored before returning.
 */
internal fun InputStream.sniffsDeepHeif(): Boolean {
    mark(HEIF_SNIFF_BYTES)
    val sniff = ByteArray(HEIF_SNIFF_BYTES)
    val sniffed = read(sniff)
    reset()
    if (sniffed <= 0 || HeifSniffer.findBrand(sniff, sniffed) == null) return false

    mark(HEIF_PROBE_BYTES)
    val head = ByteArray(HEIF_PROBE_BYTES)
    var read = 0
    while (read < HEIF_PROBE_BYTES) {
        val n = read(head, read, HEIF_PROBE_BYTES - read)
        if (n <= 0) break
        read += n
    }
    reset()
    return read > 0 && HeifBitstreamProbe.needsSoftwareDecode(head.copyOf(read))
}

/** Same check for a file descriptor: duplicates it so the caller's position is preserved. */
internal fun ParcelFileDescriptor.sniffsDeepHeif(): Boolean {
    val dup = try {
        ParcelFileDescriptor.dup(fileDescriptor)
    } catch (_: Exception) {
        return false
    }
    return try {
        val head = ByteArray(HEIF_PROBE_BYTES)
        var read = 0
        FileInputStream(dup.fileDescriptor).use { stream ->
            while (read < HEIF_PROBE_BYTES) {
                val n = stream.read(head, read, HEIF_PROBE_BYTES - read)
                if (n <= 0) break
                read += n
            }
        }
        read > 0 && HeifSniffer.findBrand(head, read) != null &&
                HeifBitstreamProbe.needsSoftwareDecode(head.copyOf(read))
    } finally {
        dup.close()
    }
}

/**
 * Decodes >10-bit HEIF/AVIF from an InputStream (already decrypted if using
 * EncryptedFileModelLoader). Relies on HeifCoder (same as SketchHeifDecoder).
 *
 * #1244: handles() is gated on the declared bit depth so only streams the platform decoder
 * corrupts (>10-bit) are claimed; <=10-bit streams continue to the platform path and get a
 * hardware decode instead of paying for HeifCoder.
 */
class HeifBitmapDecoder(
    private val bitmapPool: BitmapPool
) : ResourceDecoder<InputStream, Bitmap> {

    private val coder = HeifCoder()

    override fun handles(source: InputStream, options: Options): Boolean =
        source.sniffsDeepHeif()

    override fun decode(
        source: InputStream,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Bitmap>? {
        val allBytes = source.readBytes() // TODO: consider bounded read / streaming if memory pressure observed
        val size = coder.getSize(allBytes) ?: return null // null only on invalid data
        val targetW = if (width > 0) width else size.width
        val targetH = if (height > 0) height else size.height
        val bmp = coder.decodeSampled(allBytes, targetW, targetH, PreferredColorConfig.RGBA_8888)
        Log.d("HeifBitmapDecoder", "decode() size=${size.width}x${size.height} -> ${bmp.width}x${bmp.height}")
        return BitmapResource.obtain(bmp, bitmapPool)
    }
}
