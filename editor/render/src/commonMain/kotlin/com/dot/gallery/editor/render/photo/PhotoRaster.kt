package com.dot.gallery.editor.render.photo

import com.dot.gallery.editor.core.model.PixelRect
import com.dot.gallery.editor.render.SrgbPixels

/**
 * A decoded photo source. Implementations hold whatever platform handle they
 * need (region decoder, image reader) until [close].
 *
 * [readRegion] returns the **raw** stored pixels — un-oriented, full
 * resolution — so callers apply orientation themselves through
 * [com.dot.gallery.editor.core.model.PhotoMapping]. [readPreview] returns a
 * cheap, already-upright image no larger than `maxEdge` on each axis with
 * aspect ratio preserved.
 */
interface PhotoRaster : AutoCloseable {

    val info: PhotoInfo

    suspend fun readRegion(rect: PixelRect): SrgbPixels

    suspend fun readPreview(maxEdge: Int): SrgbPixels

    override fun close()
}
