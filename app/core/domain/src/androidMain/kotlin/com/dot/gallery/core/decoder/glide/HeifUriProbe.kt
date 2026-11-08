package com.dot.gallery.core.decoder.glide

import android.content.ContentResolver
import android.net.Uri
import com.dot.gallery.core.decoder.format.HeifBitstreamProbe
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.SequenceInputStream
import com.dot.gallery.cloud.core.matches
import com.dot.gallery.core.util.and
import com.dot.gallery.core.util.or




/**
 * Shared ">10-bit HEIF/AVIF" decision for Glide fetchers that sit in front of platform decode
 * paths. The platform thumbnailer and ImageDecoder corrupt >10-bit streams instead of failing,
 * so a confident probe must reroute those URIs before a platform path can claim them.
 *
 * Provider-reported MIME is unreliable for AVIF (MediaStore may file it under `MEDIA_TYPE_NONE`
 * or serve a `file/` collection URI with a generic/null MIME), so the decision sniffs the
 * ISO-BMFF `ftyp` brand whenever the MIME does not already prove or disprove HEIF.
 */
// public: app-side Glide loaders + SmartThumbnailLoader call needsSoftwareDecode
object HeifUriProbe {

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
        else -> sniffsAsDeepHeif(resolver, uri)
    }

    /** MIME values that carry usable format information (platform decode is trustworthy). */
    fun isUsableMime(mime: String?): Boolean {
        if (mime.isNullOrBlank()) return false
        val lower = mime.lowercase()
        return (lower.startsWith("image/") || lower.startsWith("video/")) &&
                lower != "image/*" &&
                lower.substringAfter('/') !in setOf("octet-stream", "binary", "x-binary", "unknown")
    }

    /** Streams [uri]'s box headers up to `meta` and reports whether the declared depth is >10-bit. */
    private fun probesAboveSafeDepth(resolver: ContentResolver, uri: Uri): Boolean =
        resolver.openInputStream(uri)?.use { stream -> metaDeclaresUnsafeDepth(stream) } ?: false

    /**
     * Brand sniff and depth probe over a single open: the sniffed head is replayed in front of
     * the rest of the stream, so slow storage pays one open instead of two.
     */
    private fun sniffsAsDeepHeif(resolver: ContentResolver, uri: Uri): Boolean =
        resolver.openInputStream(uri)?.use { stream ->
            val head = ByteArray(SNIFF_BYTES)
            var read = 0
            while (read < SNIFF_BYTES) {
                val n = stream.read(head, read, SNIFF_BYTES - read)
                if (n <= 0) break
                read += n
            }
            read > 0 && HeifSniffer.findBrand(head, read) != null &&
                    metaDeclaresUnsafeDepth(SequenceInputStream(ByteArrayInputStream(head, 0, read), stream))
        } ?: false

    private fun metaDeclaresUnsafeDepth(stream: InputStream): Boolean =
        HeifBitstreamProbe.readMetaBox(stream)?.let(HeifBitstreamProbe::needsSoftwareDecode) == true
}
