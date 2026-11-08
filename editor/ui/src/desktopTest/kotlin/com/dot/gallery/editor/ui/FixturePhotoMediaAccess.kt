package com.dot.gallery.editor.ui

import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PhotoMapping
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.render.photo.PhotoExportResult
import com.dot.gallery.editor.render.photo.PhotoMediaAccess
import com.dot.gallery.editor.render.photo.PhotoRaster
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory media access: each open returns a fresh raster over the fixture
 * pixels; export reports a real publish receipt (no file is written — the
 * port contract is the receipt, which the session consumes).
 */
internal class FixturePhotoMediaAccess(
    private val pixels: () -> SrgbPixels
) : PhotoMediaAccess {
    val openedRasters = mutableListOf<FixturePhotoRaster>()
    var exportCalls = 0
        private set

    /** When set, export suspends before publishing — tests hold the
     * receipt to observe mid-export session state. */
    var exportPrePublishGate: CompletableDeferred<Unit>? = null

    override suspend fun open(reference: String): PhotoRaster =
        FixturePhotoRaster(pixels(), reference).also { openedRasters += it }

    override suspend fun export(
        document: PhotoDocument,
        format: com.dot.gallery.editor.core.model.PhotoExportFormat?,
        onPublished: (PhotoExportResult) -> Unit,
        onProgress: (Float) -> Unit
    ): PhotoExportResult {
        exportCalls++
        exportPrePublishGate?.await()
        onProgress(1f)
        val result = PhotoExportResult(
            reference = "file:///fixture_copy.png",
            mimeType = "image/png",
            size = PhotoMapping(document.source.size, 1, document.geometry)
                .outputSize
        )
        onPublished(result)
        return result
    }
}
