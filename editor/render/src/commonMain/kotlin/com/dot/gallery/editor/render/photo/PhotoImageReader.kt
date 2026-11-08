package com.dot.gallery.editor.render.photo

import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PhotoMapping
import com.dot.gallery.editor.core.model.PixelRect
import com.dot.gallery.editor.render.SrgbPixels
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Independent, immutable-recipe reader used by the image viewer's zone decoders. */
class PhotoImageReader private constructor(
    private val document: PhotoDocument,
    private val rasters: Map<String, PhotoRaster>,
    private val dispatcher: CoroutineDispatcher
) {
    private val mutex = Mutex()
    private var closed = false
    private val base = rasters.getValue(document.source.id)
    val size = PhotoMapping(base.info.rawSize, base.info.orientation, document.geometry).outputSize

    suspend fun readRegion(region: PixelRect, sampleSize: Int = 1): SrgbPixels =
        withContext(dispatcher) {
            mutex.withLock {
                check(!closed) { "image reader is closed" }
                PhotoStripExporter().renderRegion(base, document, region, sampleSize, rasters)
            }
        }

    suspend fun close() = withContext(NonCancellable + dispatcher) {
        mutex.withLock {
            if (!closed) {
                closed = true
                closeRasters(rasters.values)
            }
        }
    }

    companion object {
        suspend fun open(
            media: PhotoMediaAccess,
            document: PhotoDocument,
            dispatcher: CoroutineDispatcher = Dispatchers.Default
        ): PhotoImageReader {
            val acquired = LinkedHashMap<String, PhotoRaster>()
            try {
                return withContext(dispatcher) {
                    val sources = listOf(document.source) +
                        document.layers.filter { it.visible }.map { it.source }
                    for (source in sources.distinctBy { it.id }) {
                        // Zoomed regions always read the full frame, even when
                        // the develop exports at half size.
                        val raster = if (source.id == document.source.id) {
                            media.openDeveloped(
                                source.reference, document.develop.copy(halfSize = false)
                            )
                        } else {
                            media.open(source.reference)
                        }
                        acquired[source.id] = raster
                        require(raster.info.uprightSize == source.size) {
                            "source dimensions changed for '${source.id}'"
                        }
                    }
                    PhotoImageReader(document, acquired.toMap(), dispatcher)
                }
            } catch (error: Throwable) {
                withContext(NonCancellable + dispatcher) {
                    try {
                        closeRasters(acquired.values)
                    } catch (cleanup: Throwable) {
                        error.addSuppressed(cleanup)
                    }
                }
                throw error
            }
        }

        private fun closeRasters(rasters: Collection<PhotoRaster>) {
            var failure: Throwable? = null
            for (raster in rasters) {
                try {
                    raster.close()
                } catch (error: Throwable) {
                    val previous = failure
                    if (previous == null) failure = error else previous.addSuppressed(error)
                }
            }
            failure?.let { throw it }
        }
    }
}
