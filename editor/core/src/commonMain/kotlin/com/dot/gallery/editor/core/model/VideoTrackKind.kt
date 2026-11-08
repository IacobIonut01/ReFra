package com.dot.gallery.editor.core.model

/**
 * What a [VideoTrack] carries. [Main] is the primary visual lane — at most
 * one per sequence — holding the packed clip programme whose audio is the
 * sequence's audio in this scope. [Overlay] lanes stack composited visual
 * clips above it. [Title] is reserved for text overlays (declared now so
 * documents can describe them; nothing renders them yet). [Audio] is
 * reserved for the dedicated audio-mixer lanes and is not yet accepted in
 * a sequence at all.
 */
enum class VideoTrackKind {
    Main,
    Overlay,
    Title,
    Audio
}
