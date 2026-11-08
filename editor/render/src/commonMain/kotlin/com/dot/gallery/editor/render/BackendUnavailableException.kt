package com.dot.gallery.editor.render

/**
 * Raised by an accelerated backend when it cannot take a request at all
 * (missing device, lost context, unmet capability). Only this and
 * [UnsupportedOperationException] are eligible for CPU fallback in
 * [FallbackFrameRenderer] — real work failures, cancellation and fatal errors
 * always propagate.
 */
class BackendUnavailableException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
