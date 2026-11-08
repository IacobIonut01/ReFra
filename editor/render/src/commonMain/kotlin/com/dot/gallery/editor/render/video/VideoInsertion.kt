package com.dot.gallery.editor.render.video

/**
 * Where a newly added video source lands in the sequence.
 * [AtPlayhead] ripple-inserts on the Main lane at the playhead (splitting
 * the clip under it first); [End] appends on the Main lane after its last
 * clip; [Overlay] places at the playhead on an overlay lane — reusing the
 * first lane with room, creating one otherwise — without rippling.
 */
enum class VideoInsertion {
    AtPlayhead,
    End,
    Overlay
}
