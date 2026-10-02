/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.decoder.format

/**
 * Lightweight magic-byte detection for image formats that Android cannot decode natively
 * and whose MIME types are unreliable (often null / application/octet-stream coming from
 * MediaStore or MimeTypeMap). Detection is done on the file header so it works regardless
 * of the reported MIME type.
 */
object ImageFormatSniffer {

    /** Adobe Photoshop document: starts with the ASCII signature "8BPS". */
    fun isPsd(header: ByteArray, length: Int = header.size): Boolean {
        if (length < 4) return false
        return header[0] == 0x38.toByte() && // '8'
                header[1] == 0x42.toByte() && // 'B'
                header[2] == 0x50.toByte() && // 'P'
                header[3] == 0x53.toByte()    // 'S'
    }

    /**
     * JPEG 2000: either the JP2/JPX file-format signature box
     * (00 00 00 0C 6A 50 20 20 0D 0A 87 0A) or a raw J2K codestream (FF 4F FF 51).
     */
    fun isJp2(header: ByteArray, length: Int = header.size): Boolean {
        if (length >= 12 &&
            header[0] == 0x00.toByte() && header[1] == 0x00.toByte() &&
            header[2] == 0x00.toByte() && header[3] == 0x0C.toByte() &&
            header[4] == 0x6A.toByte() && header[5] == 0x50.toByte() && // 'jP'
            header[6] == 0x20.toByte() && header[7] == 0x20.toByte() &&
            header[8] == 0x0D.toByte() && header[9] == 0x0A.toByte() &&
            header[10] == 0x87.toByte() && header[11] == 0x0A.toByte()
        ) return true
        // Raw J2K codestream
        return length >= 4 &&
                header[0] == 0xFF.toByte() && header[1] == 0x4F.toByte() &&
                header[2] == 0xFF.toByte() && header[3] == 0x51.toByte()
    }

    /**
     * JPEG XL: raw codestream (`FF 0A`) or ISO-BMFF container signature box
     * (`00 00 00 0C` + `JXL ` + `0D 0A 87 0A`).
     */
    fun isJxl(header: ByteArray, length: Int = header.size): Boolean {
        if (length >= 2 && header[0] == 0xFF.toByte() && header[1] == 0x0A.toByte()) return true
        return length >= 12 &&
                header[0] == 0x00.toByte() && header[1] == 0x00.toByte() &&
                header[2] == 0x00.toByte() && header[3] == 0x0C.toByte() &&
                header[4] == 0x4A.toByte() && header[5] == 0x58.toByte() && // 'JX'
                header[6] == 0x4C.toByte() && header[7] == 0x20.toByte() && // 'L '
                header[8] == 0x0D.toByte() && header[9] == 0x0A.toByte() &&
                header[10] == 0x87.toByte() && header[11] == 0x0A.toByte()
    }

    /** JPEG: SOI marker `FF D8 FF`. */
    fun isJpeg(header: ByteArray, length: Int = header.size): Boolean =
        length >= 3 &&
                header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()

    /** PNG: 8-byte signature `89 50 4E 47 0D 0A 1A 0A`. */
    fun isPng(header: ByteArray, length: Int = header.size): Boolean =
        length >= 8 &&
                header[0] == 0x89.toByte() && header[1] == 0x50.toByte() &&
                header[2] == 0x4E.toByte() && header[3] == 0x47.toByte() &&
                header[4] == 0x0D.toByte() && header[5] == 0x0A.toByte() &&
                header[6] == 0x1A.toByte() && header[7] == 0x0A.toByte()

    /** WebP: RIFF container with a `WEBP` form type (`RIFF….WEBP`). Static or animated. */
    fun isWebp(header: ByteArray, length: Int = header.size): Boolean =
        length >= 12 &&
                header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() &&
                header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte() &&
                header[8] == 'W'.code.toByte() && header[9] == 'E'.code.toByte() &&
                header[10] == 'B'.code.toByte() && header[11] == 'P'.code.toByte()

    /** GIF: `GIF87a` or `GIF89a`. */
    fun isGif(header: ByteArray, length: Int = header.size): Boolean =
        length >= 6 &&
                header[0] == 'G'.code.toByte() && header[1] == 'I'.code.toByte() &&
                header[2] == 'F'.code.toByte() && header[3] == '8'.code.toByte() &&
                (header[4] == '7'.code.toByte() || header[4] == '9'.code.toByte()) &&
                header[5] == 'a'.code.toByte()

    /** BMP: `BM`. */
    fun isBmp(header: ByteArray, length: Int = header.size): Boolean =
        length >= 2 && header[0] == 'B'.code.toByte() && header[1] == 'M'.code.toByte()

    /** Classic TIFF header: little-endian (`II 2A 00`) or big-endian (`MM 00 2A`) — also the container magic for most camera RAW (CR2/NEF/ARW/DNG/…). */
    fun isTiffMagic(header: ByteArray, length: Int = header.size): Boolean {
        if (length < 4) return false
        val le = header[0] == 0x49.toByte() && header[1] == 0x49.toByte() &&
                header[2] == 0x2A.toByte() && header[3] == 0x00.toByte()
        val be = header[0] == 0x4D.toByte() && header[1] == 0x4D.toByte() &&
                header[2] == 0x00.toByte() && header[3] == 0x2A.toByte()
        return le || be
    }

    /**
     * True when the header is a standard raster the platform decodes directly
     * ([android.graphics.BitmapFactory] / [android.graphics.ImageDecoder] / [android.graphics.BitmapRegionDecoder]):
     * JPEG, PNG, WebP, GIF, BMP. Used to keep such files on the native decode path even when
     * MediaStore mislabels them with a RAW/TIFF MIME (#1054). HEIF/AVIF are deliberately excluded
     * so they keep their dedicated hardware-first decoders.
     */
    fun isNativelyDecodable(header: ByteArray, length: Int = header.size): Boolean =
        isJpeg(header, length) || isPng(header, length) || isWebp(header, length) ||
                isGif(header, length) || isBmp(header, length)

    /**
     * The canonical `image/<type>` MIME for a natively-decodable header, or `null` when the header is
     * not one of [isNativelyDecodable]'s formats. Used to rewrite a bogus reported MIME (#1054).
     */
    fun standardMimeFor(header: ByteArray, length: Int = header.size): String? = when {
        isJpeg(header, length) -> "image/jpeg"
        isPng(header, length) -> "image/png"
        isWebp(header, length) -> "image/webp"
        isGif(header, length) -> "image/gif"
        isBmp(header, length) -> "image/bmp"
        else -> null
    }

    /**
     * SVG: an XML/text document containing a "<svg" tag near the start. Tolerates a UTF-8 BOM,
     * leading whitespace, and an optional XML prolog / DOCTYPE before the root element.
     */
    fun isSvg(header: ByteArray, length: Int = header.size): Boolean {
        if (length < 4) return false
        var i = 0
        // Skip UTF-8 BOM
        if (length >= 3 &&
            header[0] == 0xEF.toByte() && header[1] == 0xBB.toByte() && header[2] == 0xBF.toByte()
        ) i = 3
        // Skip leading whitespace
        while (i < length) {
            val c = header[i].toInt()
            if (c != 0x20 && c != 0x09 && c != 0x0A && c != 0x0D) break
            i++
        }
        // Must start with a tag (XML prolog, DOCTYPE, comment, or the <svg> root)
        if (i >= length || header[i] != '<'.code.toByte()) return false
        // Cheap byte scan for "<svg" anywhere in the sniffed header
        val end = length - 4
        var j = i
        while (j <= end) {
            if (header[j] == '<'.code.toByte() &&
                (header[j + 1] == 's'.code.toByte() || header[j + 1] == 'S'.code.toByte()) &&
                (header[j + 2] == 'v'.code.toByte() || header[j + 2] == 'V'.code.toByte()) &&
                (header[j + 3] == 'g'.code.toByte() || header[j + 3] == 'G'.code.toByte())
            ) return true
            j++
        }
        return false
    }
}
