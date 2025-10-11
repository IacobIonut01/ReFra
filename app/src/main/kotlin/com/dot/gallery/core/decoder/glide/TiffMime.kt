package com.dot.gallery.core.decoder.glide

/** Central TIFF MIME registry. */
object TiffMime {
    private val TIFF_MIME_TYPES = setOf(
        "image/tiff",
        "image/tif"
    )

    fun isTiff(mime: String?): Boolean {
        if (mime.isNullOrEmpty()) return false
        val lower = mime.lowercase()
        if (lower in TIFF_MIME_TYPES) return true
        val base = lower.substringBefore(';')
        return base in TIFF_MIME_TYPES
    }
}
