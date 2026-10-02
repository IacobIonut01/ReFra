/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.decoder.format

import android.content.Context
import android.net.Uri
import android.util.Size
import com.awxkee.jxlcoder.JxlCoder

/**
 * Header-only intrinsic-size probe for image formats that Android cannot decode natively and that
 * metadata-extractor / [android.graphics.BitmapFactory] cannot read (JXL, JP2, PSD, SVG, and
 * HEIF/AVIF/TIFF gaps).
 *
 * The metadata pipeline (properties sheet + "View all metadata") funnels through metadata-extractor,
 * which has no JXL reader, and its in-process fallback uses BitmapFactory, which has no native JXL
 * codec — so these files show no resolution at all. This probe reuses the same native decoders the
 * app already uses for rendering to recover width/height without a full decode, guaranteeing the
 * properties sheet is never blank.
 *
 * Only the (cheap, header-only) native `getSize` calls run here — all untrusted full parsing stays
 * inside the isolated metadata service.
 */
object SpecialFormatProbe {

    /** Bytes read from the file head; every supported probe only needs the header. */
    private const val DEFAULT_HEADER_BYTES = 512 * 1024

    /**
     * Reads a bounded prefix of [uri] and returns its intrinsic pixel size, or `null` if the format
     * is not one of the special formats handled here or the size could not be determined.
     */
    fun getSize(
        context: Context,
        uri: Uri,
        maxHeaderBytes: Int = DEFAULT_HEADER_BYTES
    ): Size? = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(maxHeaderBytes)
            var total = 0
            while (total < maxHeaderBytes) {
                val read = stream.read(buffer, total, maxHeaderBytes - total)
                if (read < 0) break
                total += read
            }
            if (total == maxHeaderBytes) buffer else buffer.copyOf(total)
        } ?: return null
        getSize(bytes)
    }.getOrNull()

    /**
     * Returns the intrinsic pixel size for the supported special formats, or `null` otherwise.
     * The input may be a bounded header prefix — all probes here are header-only.
     */
    fun getSize(bytes: ByteArray): Size? {
        return when {
            isJxl(bytes) -> runCatching { JxlCoder.getSize(bytes) }.getOrNull()
            isHeifOrAvif(bytes) -> HeifDecodeEngine.getSize(bytes)
            ImageFormatSniffer.isJp2(bytes) -> Jp2ImageDecoder.getSize(bytes)
            ImageFormatSniffer.isPsd(bytes) -> PsdImageDecoder.getSize(bytes)
            isTiff(bytes) -> TiffImageDecoder.getSize(bytes)
            ImageFormatSniffer.isSvg(bytes) -> SvgImageDecoder.renderSize(bytes, 0, 0)
            else -> null
        }?.takeIf { it.width > 0 && it.height > 0 }
    }

    /** JPEG XL: raw codestream (FF 0A) or ISO-BMFF container signature box. */
    private fun isJxl(b: ByteArray): Boolean = ImageFormatSniffer.isJxl(b, b.size)

    /** HEIF/HEIC/AVIF: ISO-BMFF with an 'ftyp' box whose major/compatible brand is HEIF-family. */
    private fun isHeifOrAvif(b: ByteArray): Boolean {
        if (b.size < 12) return false
        if (b[4] != 'f'.code.toByte() || b[5] != 't'.code.toByte() ||
            b[6] != 'y'.code.toByte() || b[7] != 'p'.code.toByte()
        ) return false
        val brand = String(b, 8, 4, Charsets.US_ASCII).lowercase()
        return brand in HEIF_BRANDS
    }

    /** TIFF: little-endian (II 2A 00) or big-endian (MM 00 2A) header. */
    private fun isTiff(b: ByteArray): Boolean {
        if (b.size < 4) return false
        val le = b[0] == 0x49.toByte() && b[1] == 0x49.toByte() &&
                b[2] == 0x2A.toByte() && b[3] == 0x00.toByte()
        val be = b[0] == 0x4D.toByte() && b[1] == 0x4D.toByte() &&
                b[2] == 0x00.toByte() && b[3] == 0x2A.toByte()
        return le || be
    }

    private val HEIF_BRANDS = setOf(
        "heic", "heix", "heim", "heis", "hevc", "hevx", "heif",
        "mif1", "msf1", "avif", "avis"
    )
}
