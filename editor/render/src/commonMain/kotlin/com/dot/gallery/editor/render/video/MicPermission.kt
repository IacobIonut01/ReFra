package com.dot.gallery.editor.render.video

/**
 * The platform microphone gate reported by [VoiceRecorder.permissionState]
 * — the voice-over surface's three states: ready, ask, or permanently
 * refused (deep link to settings is the honest remedy there).
 */
enum class MicPermission {
    Granted,
    Requestable,
    Denied
}
