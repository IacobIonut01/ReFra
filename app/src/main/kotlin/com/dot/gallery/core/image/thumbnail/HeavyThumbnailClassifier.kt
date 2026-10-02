/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.image.thumbnail

import android.content.ContentResolver
import android.net.Uri
import com.dot.gallery.core.decoder.format.ImageFormatSniffer
import com.dot.gallery.core.decoder.glide.HeifSniffer
import com.dot.gallery.core.decoder.glide.HeifUriProbe
import com.dot.gallery.core.decoder.glide.RawMime

/**
 * #1276: formats the platform thumbnailer cannot serve (`ContentResolver.loadThumbnail` throws)
 * and the app decodes in software — the expensive decodes [LocalThumbnailStore] exists to
 * amortize across Glide disk-cache evictions and process death.
 */
enum class HeavyThumbFormat { JXL, PSD, JP2, TIFF, RAW, HEIF_SW }

/**
 * MIME/head-byte classifier deciding whether a local media Uri is a [HeavyThumbFormat].
 * Results are memoized per Uri for the process lifetime, so classification (a `getType` binder
 * call plus — only when the MIME is unusable — a small head sniff) runs at most once per item.
 */
object HeavyThumbnailClassifier {

    /** Head bytes covering every magic-byte check (JXL box, 8BPS, JP2 box/codestream, TIFF, ftyp). */
    private const val SNIFF_BYTES = 64

    private val JXL_MIMES = setOf("image/jxl", "image/x-jxl")

    private val PSD_MIMES = setOf(
        "image/vnd.adobe.photoshop", "image/x-photoshop", "image/photoshop",
        "image/psd", "image/x-psd"
    )

    private val JP2_MIMES = setOf(
        "image/jp2", "image/jpx", "image/jpm", "image/j2k", "image/x-jp2", "image/x-jpx",
        "image/jpeg2000", "image/jpeg2000-image", "image/x-jpeg2000-image"
    )

    private val TIFF_MIMES = setOf("image/tiff", "image/tif", "image/x-tiff")

    /** Mirrors the HEIF-family table in `HeifUriProbe` — the depth probe makes the decision. */
    private val HEIF_MIMES = setOf(
        "image/heif", "image/heic", "image/heif-sequence", "image/heic-sequence",
        "image/avif", "image/avis"
    )

    /**
     * Bounded per-Uri memo; null entries mean "not heavy". LinkedHashMap rather than
     * android.util.LruCache so the same code survives JVM unit tests (LruCache is Android API).
     */
    private val decisions = object : LinkedHashMap<String, HeavyThumbFormat?>(1024, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, HeavyThumbFormat?>?
        ): Boolean = size > 1024
    }

    /** The Uri's [HeavyThumbFormat], or null when the normal/platform decode path is correct. */
    fun classify(resolver: ContentResolver, uri: Uri): HeavyThumbFormat? {
        val key = uri.toString()
        synchronized(decisions) {
            if (decisions.containsKey(key)) return decisions[key]
        }
        val kind = classifyUncached(resolver, uri)
        synchronized(decisions) { decisions[key] = kind }
        return kind
    }

    private fun classifyUncached(resolver: ContentResolver, uri: Uri): HeavyThumbFormat? {
        val mime = resolver.getType(uri)
        classifyMime(mime)?.let { return it }
        // HEIF-family MIME: only >10-bit streams are heavy — the platform corrupts them (#1244).
        if (mime?.lowercase()?.substringBefore(';')?.trim() in HEIF_MIMES) {
            return if (HeifUriProbe.needsSoftwareDecode(resolver, uri, mime)) {
                HeavyThumbFormat.HEIF_SW
            } else null
        }
        // A concrete non-heavy MIME (JPEG/PNG/WebP/video/…) is trusted — the platform path is fine.
        if (HeifUriProbe.isUsableMime(mime)) return null
        // Missing or generic MIME: decide from magic bytes.
        val head = ByteArray(SNIFF_BYTES)
        val read = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                var total = 0
                while (total < SNIFF_BYTES) {
                    val n = stream.read(head, total, SNIFF_BYTES - total)
                    if (n <= 0) break
                    total += n
                }
                total
            } ?: -1
        }.getOrDefault(-1)
        if (read <= 0) return null
        val sniffed = sniffKind(head, read) ?: return null
        // An ftyp brand proves HEIF-family but not bit depth — probe before treating as heavy.
        if (sniffed == HeavyThumbFormat.HEIF_SW) {
            return if (HeifUriProbe.needsSoftwareDecode(resolver, uri, mime)) sniffed else null
        }
        return sniffed
    }

    /**
     * MIME → format when the MIME alone proves it. Returns null for HEIF-family MIMEs (the bit
     * depth probe decides) and for everything the platform decodes natively. Ordering matters:
     * `image/x-jxl` and `image/x-tiff` would otherwise match [RawMime]'s `image/x-` prefix rule.
     */
    internal fun classifyMime(mime: String?): HeavyThumbFormat? {
        if (mime.isNullOrEmpty()) return null
        val m = mime.lowercase().substringBefore(';').trim()
        return when {
            m in JXL_MIMES -> HeavyThumbFormat.JXL
            m in PSD_MIMES -> HeavyThumbFormat.PSD
            m in JP2_MIMES -> HeavyThumbFormat.JP2
            m in TIFF_MIMES -> HeavyThumbFormat.TIFF
            m in HEIF_MIMES -> null // candidate only — bit-depth probe required
            RawMime.isCameraRaw(m) -> HeavyThumbFormat.RAW
            else -> null
        }
    }

    /**
     * Magic bytes → format, for unusable/missing MIMEs. [HeavyThumbFormat.HEIF_SW] is a
     * *candidate* marker here: an ftyp brand proves HEIF-family but not bit depth, so the caller
     * must still run the >10-bit probe before treating it as heavy.
     */
    internal fun sniffKind(head: ByteArray, length: Int): HeavyThumbFormat? = when {
        length < 4 -> null
        ImageFormatSniffer.isJxl(head, length) -> HeavyThumbFormat.JXL
        ImageFormatSniffer.isPsd(head, length) -> HeavyThumbFormat.PSD
        ImageFormatSniffer.isJp2(head, length) -> HeavyThumbFormat.JP2
        // A TIFF container without a TIFF MIME is more likely camera RAW (CR2/NEF/ARW/DNG share
        // the magic); the embedded-preview route is correct for both.
        ImageFormatSniffer.isTiffMagic(head, length) -> HeavyThumbFormat.RAW
        HeifSniffer.findBrand(head, length) != null -> HeavyThumbFormat.HEIF_SW
        else -> null
    }
}
