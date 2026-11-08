package com.dot.gallery.editor.core.project

import com.dot.gallery.editor.core.model.PhotoEdits

/**
 * The restorable state of a photo editing session: the full recipe —
 * colour, geometry and every image layer with its transform, blend and
 * mask strokes — never decoded pixels. [PhotoProjectCodec] turns this
 * into deterministic JSON; a restore re-opens every referenced image,
 * so persisted metadata is never trusted over the media itself.
 */
data class PhotoProjectSnapshot(
    val edits: PhotoEdits
)
