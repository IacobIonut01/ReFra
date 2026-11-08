package com.dot.gallery.editor.render.photo

import com.dot.gallery.editor.core.model.PhotoRawDevelop
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PhotoExportFormat

/**
 * Platform media port: opens photos for reading and writes edited copies.
 *
 * Implementations are expected to keep blocking I/O and decode work off the
 * caller's dispatcher. Only JPEG and PNG sources are supported in this slice;
 * anything else fails clearly. Exports are new copies at the original
 * resolution. An export with no explicit format follows the source's own
 * container where the platform can (JPEG sources as JPEG at the source
 * quality, PNG as lossless PNG); [supportedExportFormats] lists the explicit
 * containers a host can write. Source files are never modified and no source
 * metadata or location is carried over.
 */
interface PhotoMediaAccess {

    /**
     * The containers [export] can write when a format is requested. The
     * default is lossless PNG; platform ports add the lossy formats they
     * carry an encoder for. "Follow the source container" is always
     * available and is not listed here.
     */
    val supportedExportFormats: Set<PhotoExportFormat>
        get() = setOf(PhotoExportFormat.PNG)

    suspend fun open(reference: String): PhotoRaster

    /**
     * Opens [reference] with a RAW [develop] applied to its pixels. Sources that
     * are not RAW ignore the develop, and that is the default.
     */
    suspend fun openDeveloped(reference: String, develop: PhotoRawDevelop): PhotoRaster =
        open(reference)

    /**
     * A preview of a RAW source under [develop], or null when this port has no
     * RAW support. With [reuseBase], a following call that shares the decode only
     * re-tones it, which keeps tone sliders fast.
     */
    suspend fun developPreview(
        reference: String,
        develop: PhotoRawDevelop,
        maxEdge: Int,
        reuseBase: Boolean = true
    ): SrgbPixels? = null

    /** Frees whatever [developPreview] holds between calls. */
    fun releaseDevelopCache() = Unit

    /**
     * Writes the document as a new copy in [format], or in the source's own
     * container when [format] is null. [onPublished] is invoked exactly once,
     * after the destination is durably published, and still inside the
     * non-cancellable commit section — it is the commit receipt: even when
     * cancellation prevents this function from returning its result, an
     * invoked [onPublished] means the file exists. The callback must be fast,
     * thread-safe and non-throwing (it runs on the implementation's I/O
     * context). [onProgress] reports 0→1 over encode+copy.
     */
    suspend fun export(
        document: PhotoDocument,
        format: PhotoExportFormat? = null,
        onPublished: (PhotoExportResult) -> Unit = {},
        onProgress: (Float) -> Unit
    ): PhotoExportResult
}
