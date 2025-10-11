package com.dot.gallery.core.decoder.glide

/** Central HEIF/AVIF MIME registry. */
object HeifMime {
    private val HEIF_MIME_TYPES = setOf(
        "image/heif",
        "image/heic",
        "image/heif-sequence",
        "image/heic-sequence",
        "image/avif",
        // Non-standard or legacy/typo forms encountered in some OEM builds
        "image/avis",
        "image/heifs", // occasionally seen
        "image/heics"
    )

    fun isHeifMime(mime: String?): Boolean {
        if (mime.isNullOrEmpty()) return false
        val lower = mime.lowercase()
        if (lower in HEIF_MIME_TYPES) return true
        // Some providers append parameters (e.g., image/heif; charset=binary)
        val base = lower.substringBefore(';')
        return base in HEIF_MIME_TYPES
    }
}