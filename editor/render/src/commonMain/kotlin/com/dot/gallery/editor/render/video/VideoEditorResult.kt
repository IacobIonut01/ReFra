package com.dot.gallery.editor.render.video

/**
 * The result a video editor host reports back to its caller (an Android
 * activity result or a desktop `onClose` callback). [Saved] carries the
 * published copy; [Cancelled] means the user left without a newer save than
 * the host already reported; [Failed] means the editor itself could not run
 * (bad intent, unsupported input) — its message is a safe, user-facing
 * string, never a raw exception.
 */
sealed interface VideoEditorResult {

    data class Saved(val video: VideoExportResult) : VideoEditorResult

    data object Cancelled : VideoEditorResult

    data class Failed(val message: String) : VideoEditorResult
}
