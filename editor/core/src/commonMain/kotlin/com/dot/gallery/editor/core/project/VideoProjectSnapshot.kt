package com.dot.gallery.editor.core.project

import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.core.time.EditorTime

/**
 * The restorable state of a video editing session: the full multi-source
 * [sequence] document, the selected clip (or null), and the playhead.
 * [VideoProjectCodec] turns this into deterministic JSON; a restore probes
 * every referenced source again — persisted metadata is never trusted over
 * the media itself.
 */
data class VideoProjectSnapshot(
    val sequence: VideoSequence,
    val selectedClipId: String?,
    val position: EditorTime
)
