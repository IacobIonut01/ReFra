package com.dot.gallery.editor.ui

import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PhotoMapping
import com.dot.gallery.editor.core.model.PixelRect
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.render.photo.PhotoImageReader
import com.dot.gallery.editor.render.photo.PhotoRegionLimits
import com.github.panpf.zoomimage.subsampling.ImageSource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.sync.Semaphore
import okio.Buffer
import okio.Source

/** A recipe generation, never an encoded file and never a mutable pixel buffer. */
@OptIn(ExperimentalUuidApi::class)
internal class EditorPixelImage(
    val document: PhotoDocument,
    val preview: SrgbPixels?,
    val openReader: suspend () -> PhotoImageReader,
    val onError: (Throwable) -> Unit
) : ImageSource {
    override val key = "editor-pixels://${Uuid.random()}"
    val size = PhotoMapping(document.source.size, 1, document.geometry).outputSize
    // Bound live zone work even when a platform creates a larger decoder pool.
    val decoding = Semaphore(2)
    override fun openSource(): Source = Buffer()

    /** Reuse a rendered preview only when it has at least the requested density. */
    fun previewRegion(region: PixelRect, sampleSize: Int): SrgbPixels? {
        require(sampleSize > 0 && region.isInside(size))
        val pixels = preview ?: return null
        if (sampleSize.toLong() * pixels.size.width < size.width ||
            sampleSize.toLong() * pixels.size.height < size.height
        ) return null
        val width = (region.width - 1) / sampleSize + 1
        val height = (region.height - 1) / sampleSize + 1
        require(width.toLong() * height <= PhotoRegionLimits.MAX_DECODED_PIXELS) {
            "decoded zone ${width}x$height exceeds the output pixel budget"
        }
        val result = IntArray(width * height)
        for (y in 0 until height) {
            val py = ((region.top.toLong() + y.toLong() * sampleSize) *
                pixels.size.height / size.height).toInt()
            for (x in 0 until width) {
                val px = ((region.left.toLong() + x.toLong() * sampleSize) *
                    pixels.size.width / size.width).toInt()
                result[y * width + x] = pixels.argbAt(px, py)
            }
        }
        return SrgbPixels(PixelSize(width, height), result)
    }
}
