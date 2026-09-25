/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.decoder.format

/**
 * Header-only probe for the declared luma bit depth of a HEIF/HEIC/AVIF (ISO-BMFF) image.
 *
 * Android's platform AV1 stack ([android.graphics.ImageDecoder] / [android.graphics.BitmapRegionDecoder]
 * / [android.provider.MediaStore] thumbnails) silently decodes 12-bit AVIF streams as 8-bit,
 * returning a "successful" but corrupted bitmap — so a decoder that would handle the file
 * correctly is never reached. Callers use [maxBitDepth] to refuse the platform path for
 * >10-bit files up front and fall back to the bundled libheif/libavif software decoder.
 *
 * Bit depth is read from the item property container (`meta` → `iprp` → `ipco`):
 * - `av1C` (AV1 Codec Configuration Record) carries the sequence header's `high_bitdepth` /
 *   `twelve_bit` / `seq_profile` flags — authoritative for AVIF.
 * - `pixi` (Pixel Information) lists bits per channel — covers HEIC/HEIF and files whose codec
 *   record cannot be parsed.
 *
 * The result is the maximum across all declared items (a thumbnail/alpha item at a lower depth
 * never masks a 12-bit primary item). Returns 0 when no readable declaration is found; callers
 * must treat 0 as "unknown — platform path allowed".
 */
object HeifBitstreamProbe {

    /** Bit depth above which the platform codec must not be trusted (12-bit and beyond). */
    const val MAX_SAFE_PLATFORM_BIT_DEPTH = 10

    /** True when the declared bit depth of [bytes] exceeds what the platform decodes correctly. */
    fun needsSoftwareDecode(bytes: ByteArray): Boolean =
        maxBitDepth(bytes) > MAX_SAFE_PLATFORM_BIT_DEPTH

    /**
     * Max declared bit depth among the image items in [bytes] (8, 10, 12 …), or 0 when nothing
     * readable is declared. Input may be a bounded header prefix — boxes truncated by the prefix
     * are skipped, so a caller reading only the head of the file still gets a correct answer for
     * typical `meta`-before-`mdat` layouts.
     */
    fun maxBitDepth(bytes: ByteArray): Int {
        var max = 0
        forEachBox(bytes, 0, bytes.size) { type, contentStart, contentEnd ->
            if (type != "meta") return@forEachBox
            // 'meta' is a FullBox: skip its 4-byte version/flags header before the children.
            forEachBox(bytes, contentStart + 4, contentEnd) { t2, c2, e2 ->
                if (t2 != "iprp") return@forEachBox
                forEachBox(bytes, c2, e2) { t3, c3, e3 ->
                    if (t3 != "ipco") return@forEachBox
                    forEachBox(bytes, c3, e3) { t4, c4, e4 ->
                        when (t4) {
                            "av1C" -> max = maxOf(max, av1cBitDepth(bytes, c4, e4))
                            "pixi" -> max = maxOf(max, pixiBitDepth(bytes, c4, e4))
                        }
                    }
                }
            }
        }
        return max
    }

    /**
     * Iterates the sibling ISO-BMFF boxes in `[start, end)`, invoking [action] with
     * (4-char box type, content start, content end) for each well-formed box. Stops at the first
     * truncated or malformed box — callers' content range is authoritative, not the declared size.
     */
    private inline fun forEachBox(
        bytes: ByteArray,
        start: Int,
        end: Int,
        action: (type: String, contentStart: Int, contentEnd: Int) -> Unit,
    ) {
        var pos = start
        while (pos + 8 <= end) {
            val size32 = readU32(bytes, pos)
            val type = String(bytes, pos + 4, 4, Charsets.US_ASCII)
            var header = 8
            var size = size32
            when (size32) {
                // 64-bit extended size in the 8 bytes following the box type.
                1L -> {
                    if (pos + 16 > end) return
                    size = readU64(bytes, pos + 8)
                    header = 16
                }
                // size==0 means "to end of file/parent".
                0L -> size = (end - pos).toLong()
            }
            if (size < header || pos + size > end) return
            action(type, pos + header, (pos + size).toInt())
            pos += size.toInt()
        }
    }

    /**
     * Decodes the bit depth from an `av1C` (AV1CodecConfigurationRecord) payload.
     * Layout: marker(1)=1 | version(7) | seq_profile(3) | seq_level_idx_0(5) | seq_tier_0(1) |
     * high_bitdepth(1) | twelve_bit(1) | monochrome(1) | subsampling_x(1) | subsampling_y(1) |
     * chroma_sample_position(2). Per the AV1 spec: 12-bit requires profile 2 with the twelve_bit
     * flag; otherwise high_bitdepth selects 10 vs 8.
     */
    private fun av1cBitDepth(bytes: ByteArray, start: Int, end: Int): Int {
        if (end - start < 4) return 0
        if (bytes[start].toInt() and 0xFF != 0x81) return 0 // marker=1, version=1
        val seqProfile = (bytes[start + 1].toInt() and 0xFF) ushr 5
        val flags = bytes[start + 2].toInt() and 0xFF
        val highBitdepth = (flags ushr 6) and 1
        val twelveBit = (flags ushr 5) and 1
        return if (highBitdepth == 0) 8
        else if (seqProfile == 2 && twelveBit == 1) 12
        else 10
    }

    /**
     * Decodes the max bits-per-channel from a `pixi` (Pixel Information) FullBox payload:
     * version(1) | flags(3) | num_channels(1) | bits_per_channel[num_channels].
     */
    private fun pixiBitDepth(bytes: ByteArray, start: Int, end: Int): Int {
        if (end - start < 6) return 0
        val numChannels = bytes[start + 4].toInt() and 0xFF
        var max = 0
        var i = 0
        while (i < numChannels && start + 5 + i < end) {
            max = maxOf(max, bytes[start + 5 + i].toInt() and 0xFF)
            i++
        }
        return max
    }

    private fun readU32(bytes: ByteArray, pos: Int): Long =
        ((bytes[pos].toInt() and 0xFF).toLong() shl 24) or
                ((bytes[pos + 1].toInt() and 0xFF).toLong() shl 16) or
                ((bytes[pos + 2].toInt() and 0xFF).toLong() shl 8) or
                (bytes[pos + 3].toInt() and 0xFF).toLong()

    private fun readU64(bytes: ByteArray, pos: Int): Long =
        (readU32(bytes, pos) shl 32) or readU32(bytes, pos + 4)
}
