package com.dot.gallery.core.decoder.glide

import java.io.InputStream

/** Wrapper pairing an InputStream with a resolved MIME type from ContentResolver. */
data class MimeInputStream(
    val inputStream: InputStream,
    val mimeType: String?
)
