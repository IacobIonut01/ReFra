package com.dot.gallery.editor.render.video

/**
 * One open microphone take from [VoiceRecorder.start] — the session
 * polls [level] for the input meter, then either [stop]s (finalizing
 * the WAV and returning the take) or [cancel]s (abandoning it; the temp
 * file is deleted).
 */
interface VoiceCapture {

    /** The live input peak in [0, 1] — cheap to poll for the meter. */
    fun level(): Float

    /**
     * Stops capture and finalizes the take's file. Throws on a write or
     * device failure — the session surfaces it as an explicit error and
     * leaves the document untouched.
     */
    suspend fun stop(): VoiceTake

    /**
     * Abandons the take synchronously — releases the input and deletes
     * the temp file. Safe to call from session teardown paths that
     * cannot suspend.
     */
    fun cancel()
}
