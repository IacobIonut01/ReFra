package com.dot.gallery.editor.render.video

/**
 * Whether this host can offer voice-over recording at all, published in
 * [VideoEditorState.voiceSupport]: [Unknown] until
 * [VideoEditorSession.refreshVoicePermission] runs, [Unavailable] when
 * there is no recorder or no usable input, [RequiresPermission] when the
 * platform prompt must run first, [Denied] after a refusal, and [Ready]
 * when capture can start now.
 */
enum class VoiceSupport {
    Unknown,
    Unavailable,
    RequiresPermission,
    Denied,
    Ready
}
