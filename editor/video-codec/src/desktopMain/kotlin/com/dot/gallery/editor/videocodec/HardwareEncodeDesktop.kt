package com.dot.gallery.editor.videocodec

/**
 * Desktop has no MediaCodec — [HardwareEncode] always stays on the
 * `libopenh264` software path.
 */
internal actual fun platformSupportsHardwareEncoder(
    mime: String,
    width: Int,
    height: Int,
    fps: Double
): Boolean = false
