package com.dot.gallery.editor.android

import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PhotoExportFormat
import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.render.photo.PhotoExportResult
import com.dot.gallery.editor.render.video.VideoExportResult

/**
 * Optional export rerouting installed by the host app. When set, a media
 * access's `export` delegates to the dispatcher — the gallery routes it to a
 * background worker that publishes a live progress notification — while the
 * worker itself calls `exportNow` to run the real encode inline (no
 * recursion). Null keeps the synchronous behaviour the tests exercise.
 */
object EditorExportDispatch {

    fun interface Photo {
        suspend fun export(
            document: PhotoDocument,
            format: PhotoExportFormat?,
            onPublished: (PhotoExportResult) -> Unit,
            onProgress: (Float) -> Unit
        ): PhotoExportResult
    }

    fun interface Video {
        suspend fun export(
            sequence: VideoSequence,
            onPublished: (VideoExportResult) -> Unit,
            onProgress: (Float) -> Unit
        ): VideoExportResult
    }

    @Volatile
    var photo: Photo? = null

    @Volatile
    var video: Video? = null
}
