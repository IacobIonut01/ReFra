package com.dot.gallery.editor.render.video

import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.time.EditorTime

/**
 * Publication receipt for an exported copy: host reference, MIME type,
 * upright output [size] and [duration] of the written file.
 */
data class VideoExportResult(
    val reference: String,
    val mimeType: String,
    val size: PixelSize,
    val duration: EditorTime
)
