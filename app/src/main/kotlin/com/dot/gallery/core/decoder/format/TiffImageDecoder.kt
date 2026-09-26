/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.decoder.format

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import com.dot.gallery.core.decoder.RawOrientation
import com.dot.gallery.feature_node.presentation.util.printError
import com.dot.gallery.feature_node.presentation.util.printWarn
import mil.nga.tiff.TiffReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max

/**
 * TIFF decoder relying on the platform [ImageDecoder] (API 28+), which supports baseline TIFF on
 * most devices. When the platform decoder rejects a file (e.g. LZW/Deflate/PackBits compression,
 * multi-strip, tiled, CMYK or 16-bit samples) it falls back to a pure-Java software decode
 * ([mil.nga.tiff]). When the raster cannot be read (JPEG-compressed, 12-bit/odd sample formats,
 * BigTIFF) it extracts an embedded JPEG preview (DNG SubIFD previews, the JPEGInterchangeFormat
 * tags, or JPEG strips), and finally falls back to an embedded EXIF thumbnail. Returns null when
 * nothing can be decoded so the caller can fall through.
 */
object TiffImageDecoder {

    private const val TAG = "decode.tiff"

    /**
     * Above this size a TIFF is never loaded fully onto the Java heap: the file IS the (often
     * uncompressed 16-bit) raster, so a `readByteArray()` of a 150 MB TIFF throws OOM. Larger files
     * are memory-mapped and downsampled by the platform decoder instead. Exposed so callers (e.g.
     * the subsampling region decoder) can avoid the full-decode path for oversized TIFFs.
     */
    const val MAX_BYTES = 64 * 1024 * 1024

    /** Guard against OOM when software-decoding very large rasters into a Bitmap. */
    private const val MAX_PIXELS = 40_000_000

    private fun getSize(exif: ExifInterface): Size? {
        val w = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
        val h = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
        if (w <= 0 || h <= 0) return null
        // Report orientation-swapped dimensions so the size matches the oriented preview/tiles.
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
        )
        return if (RawOrientation.swapsDimensions(orientation)) Size(h, w) else Size(w, h)
    }

    fun getSize(bytes: ByteArray): Size? = try {
        getSize(ExifInterface(ByteArrayInputStream(bytes)))
    } catch (_: Throwable) {
        null
    }

    /** Memory-safe size probe from a content [uri]: reads the header via a seekable descriptor. */
    fun getSize(context: Context, uri: Uri): Size? = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            getSize(ExifInterface(pfd.fileDescriptor))
        }
    } catch (_: Throwable) {
        null
    }

    /** Memory-safe size probe from a [file]: reads the header via a seekable descriptor. */
    fun getSize(file: File): Size? = try {
        getSize(ExifInterface(file))
    } catch (_: Throwable) {
        null
    }

    /**
     * Memory-safe decode from a content [uri]. For files small enough to fit on the heap this reads
     * the bytes and reuses the full [decode] path (software decode, BigTIFF, embedded JPEG). Larger
     * files are memory-mapped and downsampled by the platform decoder so the whole raster never
     * lands on the Java heap; if that fails (e.g. 16-bit samples the platform rejects) it falls back
     * to the embedded EXIF thumbnail. Returns null when nothing can be decoded.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    fun decode(context: Context, uri: Uri, reqW: Int, reqH: Int): Bitmap? = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            decodeChannel(FileInputStream(pfd.fileDescriptor), reqW, reqH) {
                ExifInterface(pfd.fileDescriptor)
            }
        }
    } catch (e: Throwable) {
        printWarn(TAG, "decode(uri) failed: ${e.message}")
        null
    }

    /** Memory-safe decode from a [file]; see [decode] (Context, Uri, …) for the strategy. */
    @RequiresApi(Build.VERSION_CODES.P)
    fun decode(file: File, reqW: Int, reqH: Int): Bitmap? = try {
        decodeChannel(FileInputStream(file), reqW, reqH) { ExifInterface(file) }
    } catch (e: Throwable) {
        printWarn(TAG, "decode(file) failed: ${e.message}")
        null
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private inline fun decodeChannel(
        stream: FileInputStream,
        reqW: Int,
        reqH: Int,
        exifProvider: () -> ExifInterface,
    ): Bitmap? = stream.use { fis ->
        val channel = fis.channel
        val size = channel.size()
        if (size <= 0) return null

        // Memory-map the file so no strip and no whole-file copy ever lands on the Java heap.
        val buffer = try {
            channel.map(FileChannel.MapMode.READ_ONLY, 0, size)
        } catch (e: Throwable) {
            printWarn(TAG, "mmap failed: ${e.message}")
            null
        }
        if (buffer != null) {
            // 1. Strip decoder first: handles the common strip layout (RGB/gray, NONE/Deflate,
            //    8/16-bit) the app itself exports, decoding one strip at a time and skipping strides
            //    for thumbnails — fast and low-memory, and covers 16-bit which the platform rejects.
            StripedTiffDecoder.decode(buffer, reqW, reqH)?.let { return it }
            // 2. Platform ImageDecoder for everything else it can handle (tiled / LZW / other 8-bit).
            buffer.rewind()
            try {
                decodeMapped(buffer, reqW, reqH)?.let { return it }
            } catch (e: Throwable) {
                printWarn(TAG, "mapped ImageDecoder failed: ${e.message}")
            }
        }
        // 3. Small files only: pure-Java mil.nga fallback for exotic variants (predictor, CMYK,
        //    JPEG-in-TIFF, BigTIFF, …). Bounded by MAX_BYTES so we never blow the heap.
        if (size in 1..MAX_BYTES.toLong()) {
            decode(fis.readBytes(), reqW, reqH)?.let { return it }
        }
        // 4. Last resort: embedded EXIF thumbnail (seekable, tiny).
        runCatching {
            val exif = exifProvider()
            if (exif.hasThumbnail()) exif.thumbnailBitmap else null
        }.getOrNull()
    }

    /**
     * Decode a memory-mapped TIFF via the platform [ImageDecoder], downsampled to the requested
     * size. The source raster stays in the (native) mmap region — never the Java heap — and the
     * output bitmap is bounded by the target sample size.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeMapped(buffer: ByteBuffer, reqW: Int, reqH: Int): Bitmap? {
        val src = ImageDecoder.createSource(buffer)
        return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val sw = info.size.width
            val sh = info.size.height
            if (reqW > 0 && reqH > 0 && sw > 0 && sh > 0) {
                var sample = 1
                while (sw / (sample * 2) >= reqW && sh / (sample * 2) >= reqH) sample *= 2
                decoder.setTargetSampleSize(sample)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun decode(bytes: ByteArray, reqW: Int, reqH: Int): Bitmap? {
        if (bytes.size > MAX_BYTES) {
            printWarn(TAG, "data too large=${bytes.size}; trying embedded JPEG / EXIF thumbnail")
            return embeddedJpeg(bytes, reqW, reqH) ?: exifThumbnail(bytes)
        }
        if (isBigTiff(bytes)) {
            // mil.nga.tiff cannot read BigTIFF; go straight to the embedded JPEG preview.
            return embeddedJpeg(bytes, reqW, reqH) ?: exifThumbnail(bytes)
        }
        return try {
            val src = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
            ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                if (reqW > 0 && reqH > 0) decoder.setTargetSize(reqW, reqH)
            }
        } catch (e: Throwable) {
            printWarn(TAG, "ImageDecoder failed: ${e.message}; trying software decode")
            softwareDecode(bytes, reqW, reqH)
                ?: embeddedJpeg(bytes, reqW, reqH)
                ?: exifThumbnail(bytes)
        }
    }

    /**
     * Pure-Java software TIFF decode for variants the platform [ImageDecoder] rejects
     * (LZW/Deflate/PackBits, multi-strip, tiled, CMYK, 16-bit). Downsamples with an integer stride
     * to honour the requested size and bound memory.
     */
    private fun softwareDecode(bytes: ByteArray, reqW: Int, reqH: Int): Bitmap? {
        return try {
            val tiff = TiffReader.readTiff(bytes)
            val directory = tiff.fileDirectories.firstOrNull() ?: return null
            val srcW = directory.imageWidth.toInt()
            val srcH = directory.imageHeight.toInt()
            if (srcW <= 0 || srcH <= 0) return null
            if (srcW.toLong() * srcH.toLong() > MAX_PIXELS) {
                printWarn(TAG, "software decode skipped: ${srcW}x$srcH exceeds pixel cap")
                return null
            }
            val rasters = directory.readRasters()
            val samples = rasters.samplesPerPixel
            val stride = computeStride(srcW, srcH, reqW, reqH)
            val outW = (srcW + stride - 1) / stride
            val outH = (srcH + stride - 1) / stride
            val pixels = IntArray(outW * outH)
            var idx = 0
            var y = 0
            while (y < srcH) {
                var x = 0
                while (x < srcW) {
                    pixels[idx++] = rasterToArgb(rasters.getPixel(x, y), samples)
                    x += stride
                }
                y += stride
            }
            Bitmap.createBitmap(pixels, outW, outH, Bitmap.Config.ARGB_8888)
        } catch (e: Throwable) {
            printWarn(TAG, "software raster decode failed: ${e.message}; will try embedded JPEG")
            null
        }
    }

    /**
     * Public entry for any TIFF-based container (including camera RAW: CR2/NEF/ARW/DNG/ORF/PEF/…):
     * returns the largest decodable embedded JPEG preview, falling back to the EXIF thumbnail.
     * Used by the RAW Sketch decoder, which never reaches [decode]'s TIFF-MIME gate.
     */
    fun decodePreview(bytes: ByteArray, reqW: Int, reqH: Int): Bitmap? {
        // #1054 safety net: if the "RAW/TIFF" container is actually a standard natively-decodable
        // image (mislabeled by MediaStore), decode it directly at full resolution instead of pulling
        // a tiny embedded preview / EXIF thumbnail. Guards against any routing that slips past the
        // MIME/header checks upstream.
        if (ImageFormatSniffer.isNativelyDecodable(bytes)) {
            decodeStandard(bytes, reqW, reqH)?.let { return it }
        }
        val bmp = embeddedJpeg(bytes, reqW, reqH) ?: exifThumbnail(bytes) ?: return null
        // The container's Orientation tag is the source of truth; embedded JPEGs/thumbnails are
        // stored un-rotated, so apply it here to agree with the demosaic + export paths.
        return RawOrientation.applyToBitmap(bmp, RawOrientation.exifOrientation(bytes))
    }

    /**
     * Extracts an embedded JPEG preview from a TIFF/DNG/RAW container and decodes it with
     * [BitmapFactory]. Covers JPEG-compressed TIFF (compression 6/7), DNG previews stored in
     * SubIFDs, and full-resolution JPEGs referenced by the JPEGInterchangeFormat tags. Walks both
     * classic TIFF and BigTIFF IFDs. Picks the largest candidate that decodes successfully.
     */
    private fun embeddedJpeg(bytes: ByteArray, reqW: Int, reqH: Int): Bitmap? {
        return try {
            val candidates = ArrayList<IntArray>() // [offset, length]
            TiffIfdWalker(bytes).collectJpegCandidates(candidates)
            if (candidates.isEmpty()) return null
            candidates.sortByDescending { it[1] } // largest first = best quality
            for (c in candidates) {
                val off = c[0]
                val len = c[1]
                if (off < 0 || len <= 2 || off.toLong() + len > bytes.size) continue
                if ((bytes[off].toInt() and 0xFF) != 0xFF ||
                    (bytes[off + 1].toInt() and 0xFF) != 0xD8
                ) continue
                decodeJpegRegion(bytes, off, len, reqW, reqH)?.let { return it }
            }
            null
        } catch (e: Throwable) {
            printWarn(TAG, "embedded JPEG extraction failed: ${e.message}")
            null
        }
    }

    /**
     * Full decode (downsampled to the requested size) of a standard natively-decodable image
     * (JPEG/PNG/WebP/GIF/BMP) via [BitmapFactory]. Used by [decodePreview]'s #1054 safety net when a
     * file mislabeled as RAW/TIFF is actually a standard image.
     */
    private fun decodeStandard(bytes: ByteArray, reqW: Int, reqH: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, reqW, reqH)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun decodeJpegRegion(bytes: ByteArray, off: Int, len: Int, reqW: Int, reqH: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, off, len, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, reqW, reqH)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, off, len, opts)
    }

    private fun computeSampleSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
        if (reqW <= 0 || reqH <= 0) return 1
        var sample = 1
        while (srcW / (sample * 2) >= reqW && srcH / (sample * 2) >= reqH) sample *= 2
        return sample
    }

    /**
     * Minimal TIFF/BigTIFF IFD reader that locates embedded JPEG byte ranges without decoding
     * rasters. Recurses into SubIFDs (tag 330, used by DNG for previews) and chained IFDs.
     */
    private class TiffIfdWalker(private val data: ByteArray) {
        private val little: Boolean
        private val bigTiff: Boolean
        private val firstIfd: Long

        init {
            val b0 = u8(0)
            val b1 = u8(1)
            little = b0 == 0x49 && b1 == 0x49
            val bigEndian = b0 == 0x4D && b1 == 0x4D
            require(little || bigEndian) { "not a TIFF" }
            val version = u16(2)
            if (version == 0x2B) {
                bigTiff = true
                firstIfd = u64(8)
            } else {
                bigTiff = false
                firstIfd = u32(4)
            }
        }

        fun collectJpegCandidates(out: MutableList<IntArray>) {
            walk(firstIfd, out, HashSet(), 0)
        }

        private fun walk(ifdOffset: Long, out: MutableList<IntArray>, visited: HashSet<Long>, depth: Int) {
            if (depth > 4 || ifdOffset <= 0 || ifdOffset >= data.size) return
            if (!visited.add(ifdOffset)) return
            var p = ifdOffset.toInt()
            val entrySize: Int
            val count: Long
            if (bigTiff) {
                count = u64(p); p += 8; entrySize = 20
            } else {
                count = u16(p).toLong(); p += 2; entrySize = 12
            }
            val n = minOf(count, 4096L)
            var jpegOffset = -1L
            var jpegLength = -1L
            var compression = -1L
            var stripOffset = -1L
            var stripCount = -1L
            val subIfds = ArrayList<Long>()
            for (i in 0 until n) {
                val e = p + (i * entrySize).toInt()
                if (e + entrySize > data.size) break
                val tag = u16(e)
                val type = u16(e + 2)
                val cnt = if (bigTiff) u64(e + 4) else u32(e + 4)
                val valOff = if (bigTiff) e + 12 else e + 8
                when (tag) {
                    259 -> compression = readScalar(type, valOff)
                    273 -> if (cnt == 1L) stripOffset = readScalar(type, valOff)
                    279 -> if (cnt == 1L) stripCount = readScalar(type, valOff)
                    513 -> jpegOffset = readScalar(type, valOff)
                    514 -> jpegLength = readScalar(type, valOff)
                    330 -> readLongArray(type, cnt, valOff, subIfds)
                }
            }
            if (jpegOffset > 0 && jpegLength > 0) {
                out.add(intArrayOf(jpegOffset.toInt(), jpegLength.toInt()))
            }
            if ((compression == 6L || compression == 7L) && stripOffset > 0 && stripCount > 0) {
                out.add(intArrayOf(stripOffset.toInt(), stripCount.toInt()))
            }
            for (sub in subIfds) walk(sub, out, visited, depth + 1)
            val nextPos = p + (n * entrySize).toInt()
            if (nextPos + (if (bigTiff) 8 else 4) <= data.size) {
                walk(if (bigTiff) u64(nextPos) else u32(nextPos), out, visited, depth + 1)
            }
        }

        private fun readScalar(type: Int, valOff: Int): Long = when (type) {
            1 -> u8(valOff).toLong()
            3 -> u16(valOff).toLong()
            16 -> u64(valOff)
            else -> u32(valOff)
        }

        private fun readLongArray(type: Int, cnt: Long, valOff: Int, out: MutableList<Long>) {
            val typeSize = if (type == 16) 8 else 4
            val fieldSize = if (bigTiff) 8 else 4
            val base = if (cnt * typeSize <= fieldSize) valOff
            else (if (bigTiff) u64(valOff) else u32(valOff)).toInt()
            val n = minOf(cnt, 32L)
            for (i in 0 until n) {
                val pos = base + (i * typeSize).toInt()
                if (pos + typeSize > data.size) break
                out.add(if (type == 16) u64(pos) else u32(pos))
            }
        }

        private fun u8(p: Int): Int = data[p].toInt() and 0xFF

        private fun u16(p: Int): Int = if (little) {
            u8(p) or (u8(p + 1) shl 8)
        } else {
            (u8(p) shl 8) or u8(p + 1)
        }

        private fun u32(p: Int): Long = if (little) {
            u8(p).toLong() or (u8(p + 1).toLong() shl 8) or
                    (u8(p + 2).toLong() shl 16) or (u8(p + 3).toLong() shl 24)
        } else {
            (u8(p).toLong() shl 24) or (u8(p + 1).toLong() shl 16) or
                    (u8(p + 2).toLong() shl 8) or u8(p + 3).toLong()
        }

        private fun u64(p: Int): Long {
            val lo = u32(p)
            val hi = u32(p + 4)
            return if (little) lo or (hi shl 32) else (lo shl 32) or hi
        }
    }

    private fun computeStride(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
        if (reqW <= 0 || reqH <= 0) return 1
        val sx = srcW / reqW
        val sy = srcH / reqH
        return max(1, minOf(sx, sy))
    }

    /** Converts a TIFF pixel (1=gray, 3=RGB, 4=RGBA/CMYK) to packed ARGB, normalising 16-bit. */
    private fun rasterToArgb(pixel: Array<Number>, samples: Int): Int {
        fun s(i: Int): Int {
            val v = pixel[i].toInt() and 0xFFFF
            return if (v > 255) v ushr 8 else v
        }
        return when {
            samples >= 4 -> {
                // Treat as RGBA; if it is actually CMYK the colours degrade gracefully.
                val r = s(0); val g = s(1); val b = s(2); val a = s(3)
                (a shl 24) or (r shl 16) or (g shl 8) or b
            }
            samples == 3 -> {
                val r = s(0); val g = s(1); val b = s(2)
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            else -> {
                val gray = s(0)
                (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
            }
        }
    }

    private fun isBigTiff(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        val b2 = bytes[2].toInt() and 0xFF
        val b3 = bytes[3].toInt() and 0xFF
        val little = b0 == 0x49 && b1 == 0x49
        val big = b0 == 0x4D && b1 == 0x4D
        if (!little && !big) return false
        return (little && b2 == 0x2B && b3 == 0x00) || (big && b2 == 0x00 && b3 == 0x2B)
    }

    private fun exifThumbnail(bytes: ByteArray): Bitmap? {
        return try {
            val exif = ExifInterface(ByteArrayInputStream(bytes))
            if (exif.hasThumbnail()) exif.thumbnailBitmap else null
        } catch (e: Throwable) {
            printError(TAG, "EXIF thumbnail extraction failed: ${e.message}")
            null
        }
    }
}
