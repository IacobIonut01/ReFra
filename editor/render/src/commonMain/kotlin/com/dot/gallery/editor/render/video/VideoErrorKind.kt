package com.dot.gallery.editor.render.video

/** Which stage produced a video editor state error. */
enum class VideoErrorKind {
    Open,
    Preview,
    Playback,
    Export,
    Unsupported
}
