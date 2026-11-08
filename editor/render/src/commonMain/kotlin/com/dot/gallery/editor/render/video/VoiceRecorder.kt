package com.dot.gallery.editor.render.video

/**
 * Platform port for microphone capture — the voice-over tool's only
 * hardware seam. A host supplies one [VoiceRecorder] (Android's
 * `AudioRecord`, desktop's JavaSound `TargetDataLine`); a null port in
 * the session means recording is explicitly unavailable — the surface
 * says so rather than offering a dead Record.
 *
 * Capture contract: 48 kHz mono signed-16-bit PCM, written to a fresh
 * temp file the [VoiceCapture] finalizes into a `file://` WAV reference
 * on [VoiceCapture.stop] or deletes on [VoiceCapture.cancel].
 * [permissionState] reports the platform mic gate so the UI can show the
 * real permission flow instead of guessing.
 */
interface VoiceRecorder {

    /** The capture input's display label — the surface's Input row. */
    val inputLabel: String

    /**
     * The mic gate: [MicPermission.Granted] can start immediately,
     * [MicPermission.Requestable] needs the host's runtime prompt,
     * [MicPermission.Denied] needs the user to unblock the platform
     * setting. Implementations answer without prompting.
     */
    suspend fun permissionState(): MicPermission

    /**
     * Starts capturing; [monitor] pipes the input back to the output —
     * the honest headphone cue, and the reason the surface tells users
     * to plug headphones in. Throws [VoiceRecorderException] (or an
     * [UnsupportedVideoException] when the device has no input) on any
     * failure — the session surfaces it, never swallows it.
     */
    suspend fun start(monitor: Boolean): VoiceCapture
}
