package com.dot.gallery.editor.render.photo

/**
 * A capability failure authored by this library — e.g. a source that is not a
 * supported JPEG/PNG image. Its [message] is a short, user-actionable string
 * written here (never a host exception message), so
 * [PhotoEditorSession] may surface it directly. Everything else that fails
 * maps to the fixed action strings ("Could not open photo" etc.) so internal
 * paths, URIs and stack details never reach [PhotoEditorState.error].
 */
open class UnsupportedPhotoException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
