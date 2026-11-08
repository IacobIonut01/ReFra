package com.dot.gallery.editor.videocodec

/** Desktop keeps software decoding — there is no platform decoder seam. */
internal actual fun platformSupportsHardwareDecoder(
    mime: String,
    decoderName: String,
    width: Int,
    height: Int,
    fps: Double,
    profile: Int
): Boolean = false
