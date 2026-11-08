package com.dot.gallery.editor.render.photo

import com.dot.gallery.editor.core.model.PixelSize

/** Result of a completed export: where the copy landed and its shape. */
data class PhotoExportResult(
    val reference: String,
    val mimeType: String,
    val size: PixelSize
)
