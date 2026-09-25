package com.dot.gallery.core.decoder.glide

import android.content.ContentResolver
import android.net.Uri
import com.dot.gallery.core.decoder.format.HeifBitstreamProbe

/**
 * Shared ">10-bit HEIF/AVIF" decision for Glide fetchers that sit in front of platform decode
 * paths. The platform thumbnailer and ImageDecoder corrupt >10-bit streams instead of failing,
 * so a confident probe must reroute those URIs before a platform path can claim them.
 *
 * Provider-reported MIME is unreliable for AVIF (MediaStore may file it under `MEDIA_TYPE_NONE`
 * or serve a `file/` collection URI with a generic/null MIME), so the decision sniffs the
 * ISO-BMFF `ftyp` brand whenever the MIME does not already prove or disprove HEIF.
 */
internal object HeifUriProbe {

    /** Header bytes read for the >10-bit probe (matches HeifBitstreamProbe's usable window). */
    private const val PROBE_BYTES = 256 * 1024

    /** Head bytes read for the ISO-BMFF `ftyp` brand sniff (matches HeifSniffer's window). */
    private const val SNIFF_BYTES = 512

    private val HEIF_MIMETYPES = setOf(
        "image/heif",
        "image/heic",
        "image/heif-sequence",
        "image/heic-sequence",
        "image/avif",
        "image/avis",
    )

    /**
     * True when [uri] declares a bit depth above the platform-safe threshold. [mime] is the
     * value previously returned by [ContentResolver.getType]; pass null when unknown.
     * A concrete non-HEIF image/video MIME is trusted as-is — only HEIF, missing, or generic
     * MIMEs trigger header reads.
     */
    fun needsSoftwareDecode(
        resolver: ContentResolver,
        uri: Uri,
        mime: String? = resolver.getType(uri)
    ): Boolean = when {
        mime in HEIF_MIMETYPES -> probesAboveSafeDepth(resolver, uri)
        isUsableMime(mime) -> false
        else -> sniffsAsHeif(resolver, uri) && probesAboveSafeDepth(resolver, uri)
    }

    /** MIME values that carry usable format information (platform decode is trustworthy). */
    fun isUsableMime(mime: String?): Boolean {
        if (mime.isNullOrBlank()) return false
        val lower = mime.lowercase()
        return (lower.startsWith("image/") || lower.startsWith("video/")) &&
                lower != "image/*" &&
                lower.substringAfter('/') !in setOf("octet-stream", "binary", "x-binary", "unknown")
    }

    /** Reads up to [PROBE_BYTES] of [uri]'s head and reports whether the declared depth is >10-bit. */
    private fun probesAboveSafeDepth(resolver: ContentResolver, uri: Uri): Boolean {
        val header = ByteArray(PROBE_BYTES)
        var read = 0
        resolver.openInputStream(uri)?.use { stream ->
            while (read < PROBE_BYTES) {
                val n = stream.read(header, read, PROBE_BYTES - read)
                if (n <= 0) break
                read += n
            }
        }
        return read > 0 && HeifBitstreamProbe.needsSoftwareDecode(header.copyOf(read))
    }

    /** True when [uri]'s first bytes carry a HEIF-family `ftyp` brand (heic/heif/avif/…). */
    private fun sniffsAsHeif(resolver: ContentResolver, uri: Uri): Boolean {
        val head = ByteArray(SNIFF_BYTES)
        val read = resolver.openInputStream(uri)?.use { stream -> stream.read(head) } ?: -1
        return read > 0 && HeifSniffer.findBrand(head, read) != null
    }
}
