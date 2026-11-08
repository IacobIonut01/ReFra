package com.dot.gallery.editor.render.video

/**
 * A microphone capture failure — device busy, no input, a write error
 * mid-take. Surfaced through the session's error channel like any other
 * media failure.
 */
class VoiceRecorderException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
