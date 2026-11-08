package com.dot.gallery.editor.ui

import com.dot.gallery.editor.core.model.PixelRect
import com.dot.gallery.editor.render.photo.PhotoImageReader
import com.github.panpf.zoomimage.subsampling.ImageInfo
import com.github.panpf.zoomimage.subsampling.ImageSource
import com.github.panpf.zoomimage.subsampling.RegionDecoder
import com.github.panpf.zoomimage.subsampling.SubsamplingImage
import com.github.panpf.zoomimage.subsampling.TileBitmap
import com.github.panpf.zoomimage.util.IntRectCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** ZoomImage's synchronous worker API bridged to the editor's suspend pixel port. */
internal class EditorPixelRegionDecoder(private val image: EditorPixelImage) : RegionDecoder {
    private val owner = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var opened: PhotoImageReader? = null
    private val reader = owner.async(start = CoroutineStart.LAZY) {
        image.openReader().also { opened = it }
    }

    init {
        owner.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    reader.cancelAndJoin()
                    try {
                        opened?.close()
                    } catch (error: Throwable) {
                        image.onError(error)
                    }
                }
            }
        }
    }

    override fun getImageInfo() = ImageInfo(image.size.width, image.size.height, PIXEL_MIME)
    override fun prepare() = Unit

    override fun decodeRegion(region: IntRectCompat, sampleSize: Int): TileBitmap =
        runBlocking {
            withContext(owner.coroutineContext) {
                image.decoding.withPermit {
                    try {
                        require(sampleSize > 0)
                        val zone = PixelRect(region.left, region.top, region.width, region.height)
                        require(zone.isInside(image.size))
                        val pixels = image.previewRegion(zone, sampleSize)
                            ?: reader.await().readRegion(zone, sampleSize)
                        pixels.toEditorTileBitmap()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        image.onError(error)
                        throw error
                    }
                }
            }
        }

    override fun copy(): RegionDecoder = EditorPixelRegionDecoder(image)
    override fun close() = owner.cancel()
    override fun equals(other: Any?) = other is EditorPixelRegionDecoder && other.image === image
    override fun hashCode() = image.hashCode()
    override fun toString() = "EditorPixelRegionDecoder(${image.key})"

    class Factory : RegionDecoder.Factory {
        override suspend fun accept(subsamplingImage: SubsamplingImage): Boolean =
            subsamplingImage.imageSource.create() is EditorPixelImage

        override fun checkSupport(mimeType: String): Boolean = mimeType == PIXEL_MIME

        override suspend fun create(
            subsamplingImage: SubsamplingImage,
            imageSource: ImageSource
        ): RegionDecoder = EditorPixelRegionDecoder(imageSource as EditorPixelImage)

        override fun equals(other: Any?) = other is Factory
        override fun hashCode() = Factory::class.hashCode()
        override fun toString() = "EditorPixelRegionDecoder.Factory"
    }

    companion object {
        const val PIXEL_MIME = "image/x-refra-pixels"
    }
}
