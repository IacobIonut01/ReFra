package com.dot.gallery.editor.render.photo

/**
 * A supported source that exceeds this slice's decode limits (dimension,
 * pixel count or staged-import size). Distinct from a plain
 * [UnsupportedPhotoException] so the UI can say "format or size" instead of
 * wrongly recommending JPEG/PNG for an oversized JPEG.
 */
class PhotoTooLargeException(message: String, cause: Throwable? = null) :
    UnsupportedPhotoException(message, cause)
