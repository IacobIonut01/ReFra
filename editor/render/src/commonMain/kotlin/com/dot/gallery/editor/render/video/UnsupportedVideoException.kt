package com.dot.gallery.editor.render.video

/**
 * Raised when a video source or operation is outside the supported envelope
 * (missing 64-bit codec bundle, >8-bit/HDR pixels, >2 audio channels,
 * non-quarter-turn rotation, multi-source/multi-track export, oversized
 * dimensions). A typed capability failure — never silently ignored and
 * never a reason to drop clips or effects.
 */
class UnsupportedVideoException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
