package com.dot.gallery.editor.render.photo

/**
 * The result an editor host reports back to its caller (an Android activity
 * result or a desktop `onClose` callback). [Saved] carries the published
 * copy; [Cancelled] means the user left without a newer save than the host
 * already reported; [Failed] means the editor itself could not run (bad
 * intent, unsupported input) — its message is a safe, user-facing string,
 * never a raw exception.
 */
sealed interface PhotoEditorResult {

    data class Saved(val photo: PhotoExportResult) : PhotoEditorResult

    data object Cancelled : PhotoEditorResult

    data class Failed(val message: String) : PhotoEditorResult
}
