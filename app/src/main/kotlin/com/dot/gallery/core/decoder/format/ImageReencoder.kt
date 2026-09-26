/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.decoder.format

import android.graphics.Bitmap
import com.awxkee.jxlcoder.JxlChannelsConfiguration
import com.awxkee.jxlcoder.JxlCoder
import com.awxkee.jxlcoder.JxlCompressionOption
import com.awxkee.jxlcoder.JxlDecodingSpeed
import com.awxkee.jxlcoder.JxlEffort
import com.dot.gallery.feature_node.presentation.util.printError
import com.radzivon.bartoshyk.avif.coder.HeifCoder
import com.radzivon.bartoshyk.avif.coder.HeifQualityArg
import com.radzivon.bartoshyk.avif.coder.PreciseMode
import java.io.OutputStream

/**
 * Single source of truth for **re-encoding** an edited/rotated [Bitmap] back into the *same* image
 * format as its source, so an overwrite (or a matching new copy) never silently degrades e.g. a
 * JXL/AVIF/HEIC image into JPEG or PNG.
 *
 * JPEG/PNG/WebP use the platform [Bitmap.compress]. JXL/AVIF/HEIC use the native prebuilt coders
 * ([JxlCoder], [HeifCoder]) which return raw `ByteArray`s. Formats with no Android encoder
 * (RAW/DNG, TIFF, PSD, JP2, SVG, animated GIF/WebP) are reported as **not re-encodable** via
 * [formatForMime] returning `null`, so callers can offer a copy-as-PNG fallback instead.
 *
 * Every native call is guarded so a failure surfaces as an exception the caller can catch and
 * degrade from, rather than crashing.
 */
object ImageReencoder {

    private const val TAG = "decode.reencode"

    private val heifCoder by lazy { HeifCoder() }

    /** A concrete output format the app is able to write bitmap pixels into. */
    enum class ImageWriteFormat(val mimeType: String, val fileExtension: String) {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png"),
        WEBP_LOSSLESS("image/webp", "webp"),
        WEBP_LOSSY("image/webp", "webp"),
        JXL("image/jxl", "jxl"),
        AVIF("image/avif", "avif"),
        HEIC("image/heic", "heic");

        val isNextGen: Boolean get() = this == JXL || this == AVIF || this == HEIC
    }

    /** How re-encode quality is resolved for lossy/lossless formats. */
    enum class QualityMode { AUTO, MANUAL }

    /**
     * Resolved encode parameters. [detectedQuality] (when non-null) is a best-effort estimate of
     * the source's original quality; it overrides the configured lossy quality in [QualityMode.AUTO].
     */
    data class ReencodeConfig(
        val mode: QualityMode = QualityMode.AUTO,
        val lossyQuality: Int = 90,
        val jxlEffort: Int = 7,
        val jxlLossless: Boolean = true,
        val detectedQuality: Int? = null,
    ) {
        /** Effective lossy quality (1..100) for JPEG/WebP/AVIF/HEIC. */
        val effectiveLossyQuality: Int
            get() = when (mode) {
                QualityMode.MANUAL -> lossyQuality
                QualityMode.AUTO -> (detectedQuality ?: lossyQuality)
            }.coerceIn(1, 100)

        /** Whether JXL should be written losslessly (AUTO with lossless toggle, and no lossy hint). */
        val jxlUseLossless: Boolean
            get() = when (mode) {
                QualityMode.MANUAL -> false
                QualityMode.AUTO -> jxlLossless && detectedQuality == null
            }
    }

    /**
     * Resolves the write format for a source [mime]/[label], or `null` when the source format has
     * no Android encoder (RAW, TIFF, PSD, JP2, SVG, animated GIF/WebP) and therefore cannot be
     * overwritten in place.
     */
    fun formatForMime(mime: String?, label: String?): ImageWriteFormat? {
        val m = mime?.lowercase().orEmpty()
        val ext = label?.substringAfterLast('.', "")?.lowercase().orEmpty()

        fun any(vararg tokens: String) = tokens.any { m.contains(it) || ext == it }

        return when {
            // Animated / no-encoder formats: cannot re-encode a single bitmap losslessly.
            m == "image/gif" || ext == "gif" -> null
            m == "image/apng" || ext == "apng" -> null
            any("svg") -> null
            any("tiff", "tif") -> null
            m.contains("photoshop") || any("psd", "psb") -> null
            m.contains("jp2") || m.contains("jpeg2000") || m.contains("jpx") ||
                ext in JP2_EXTENSIONS -> null
            isRawMime(m) || ext in RAW_EXTENSIONS -> null

            // Re-encodable formats.
            any("jxl") -> ImageWriteFormat.JXL
            m == "image/avif" || m == "image/avis" || ext == "avif" -> ImageWriteFormat.AVIF
            m.contains("heic") || m.contains("heif") || ext in HEIF_EXTENSIONS -> ImageWriteFormat.HEIC
            m.contains("png") || ext == "png" -> ImageWriteFormat.PNG
            m.contains("webp") || ext == "webp" -> ImageWriteFormat.WEBP_LOSSLESS
            m.contains("jpeg") || m.contains("jpg") || ext in JPEG_EXTENSIONS -> ImageWriteFormat.JPEG
            else -> null
        }
    }

    /** True if a source can be overwritten in place (i.e. an encoder exists). */
    fun isReencodable(mime: String?, label: String?): Boolean = formatForMime(mime, label) != null

    /**
     * Encodes [bitmap] into [format] and returns the raw bytes. Throws on failure.
     */
    fun encodeToBytes(
        bitmap: Bitmap,
        format: ImageWriteFormat,
        config: ReencodeConfig,
    ): ByteArray = when (format) {
        ImageWriteFormat.JPEG ->
            compressToBytes(bitmap, Bitmap.CompressFormat.JPEG, config.effectiveLossyQuality)

        ImageWriteFormat.PNG ->
            compressToBytes(bitmap, Bitmap.CompressFormat.PNG, 100)

        ImageWriteFormat.WEBP_LOSSLESS ->
            compressToBytes(bitmap, webpLossless(), 100)

        ImageWriteFormat.WEBP_LOSSY ->
            compressToBytes(bitmap, webpLossy(), config.effectiveLossyQuality)

        ImageWriteFormat.JXL -> encodeJxl(bitmap, config)
        ImageWriteFormat.AVIF -> encodeAvif(bitmap, config)
        ImageWriteFormat.HEIC -> encodeHeic(bitmap, config)
    }

    /** Encodes [bitmap] into [format] and writes it to [out]. Throws on failure. */
    fun writeToStream(
        bitmap: Bitmap,
        format: ImageWriteFormat,
        config: ReencodeConfig,
        out: OutputStream,
    ) {
        when (format) {
            ImageWriteFormat.JPEG ->
                compressToStream(bitmap, Bitmap.CompressFormat.JPEG, config.effectiveLossyQuality, out)

            ImageWriteFormat.PNG ->
                compressToStream(bitmap, Bitmap.CompressFormat.PNG, 100, out)

            ImageWriteFormat.WEBP_LOSSLESS ->
                compressToStream(bitmap, webpLossless(), 100, out)

            ImageWriteFormat.WEBP_LOSSY ->
                compressToStream(bitmap, webpLossy(), config.effectiveLossyQuality, out)

            ImageWriteFormat.JXL, ImageWriteFormat.AVIF, ImageWriteFormat.HEIC ->
                out.write(encodeToBytes(bitmap, format, config))
        }
    }

    // ---- native encoders ----

    private fun encodeJxl(bitmap: Bitmap, config: ReencodeConfig): ByteArray {
        val src = bitmap.toArgb8888()
        val channels =
            if (src.hasAlpha()) JxlChannelsConfiguration.RGBA else JxlChannelsConfiguration.RGB
        val lossless = config.jxlUseLossless
        val compression =
            if (lossless) JxlCompressionOption.LOSSLESS else JxlCompressionOption.LOSSY
        // libjxl quality: 100 = lossless; lower = lossy. AUTO-lossless pins 100.
        val quality = if (lossless) 100 else config.effectiveLossyQuality
        return try {
            JxlCoder.encode(
                bitmap = src,
                channelsConfiguration = channels,
                compressionOption = compression,
                effort = effortFor(config.jxlEffort),
                quality = quality,
                decodingSpeed = JxlDecodingSpeed.SLOWEST,
            )
        } finally {
            if (src !== bitmap) src.recycle()
        }
    }

    private fun encodeAvif(bitmap: Bitmap, config: ReencodeConfig): ByteArray {
        val src = bitmap.toArgb8888()
        return try {
            heifCoder.encodeAvif(src, config.effectiveLossyQuality)
        } finally {
            if (src !== bitmap) src.recycle()
        }
    }

    private fun encodeHeic(bitmap: Bitmap, config: ReencodeConfig): ByteArray {
        val src = bitmap.toArgb8888()
        return try {
            heifCoder.encodeHeic(
                src,
                PreciseMode.LOSSY,
                HeifQualityArg.Quality(config.effectiveLossyQuality),
            )
        } finally {
            if (src !== bitmap) src.recycle()
        }
    }

    private fun effortFor(effort: Int): JxlEffort {
        val entries = JxlEffort.entries
        val idx = (effort - 1).coerceIn(0, entries.size - 1)
        return entries[idx]
    }

    // ---- platform compress helpers ----

    private fun compressToBytes(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        quality: Int,
    ): ByteArray = java.io.ByteArrayOutputStream().use { bos ->
        if (!bitmap.compress(format, quality.coerceIn(0, 100), bos)) {
            error("Bitmap.compress($format) returned false")
        }
        bos.toByteArray()
    }

    private fun compressToStream(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        quality: Int,
        out: OutputStream,
    ) {
        if (!bitmap.compress(format, quality.coerceIn(0, 100), out)) {
            error("Bitmap.compress($format) returned false")
        }
    }

    @Suppress("DEPRECATION")
    private fun webpLossless(): Bitmap.CompressFormat =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSLESS
        } else {
            Bitmap.CompressFormat.WEBP
        }

    @Suppress("DEPRECATION")
    private fun webpLossy(): Bitmap.CompressFormat =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }

    private fun Bitmap.toArgb8888(): Bitmap =
        if (config == Bitmap.Config.ARGB_8888) this
        else try {
            copy(Bitmap.Config.ARGB_8888, false) ?: this
        } catch (e: Throwable) {
            printError(TAG, "ARGB_8888 copy failed: ${e.message}")
            this
        }

    private fun isRawMime(m: String): Boolean =
        m.startsWith("image/x-") || m.startsWith("image/vnd.")

    private val JPEG_EXTENSIONS = setOf("jpg", "jpeg", "jpe", "jfif")
    private val HEIF_EXTENSIONS = setOf("heic", "heif", "hif")
    private val JP2_EXTENSIONS = setOf("jp2", "j2k", "jpf", "jpx", "j2c", "jpc")
    private val RAW_EXTENSIONS = setOf(
        "dng", "cr2", "cr3", "nef", "nrw", "arw", "rw2", "orf", "raf", "pef", "srw",
        "raw", "3fr", "fff", "dcr", "kdc", "mef", "mos", "iiq", "sr2", "srf"
    )
}
