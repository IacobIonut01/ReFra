package com.dot.gallery.editor.render.photo

/**
 * Which action produced the current [PhotoEditorState.error]. The UI maps the
 * kind to a localized, fixed string — never a raw exception message.
 * [Unsupported] covers a source in a format this slice does not read;
 * [TooLarge] covers a source that is a supported format but exceeds the
 * decode/import limits, so the UI can avoid recommending "open a JPEG" for a
 * JPEG that is simply too big.
 */
enum class PhotoErrorKind {
    Open,
    Preview,
    Export,
    Unsupported,
    TooLarge
}
