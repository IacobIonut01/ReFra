package com.dot.gallery.editor.ui

import com.github.panpf.sketch.decode.Decoder
import com.github.panpf.sketch.decode.ImageInfo
import com.github.panpf.sketch.fetch.FetchResult
import com.github.panpf.sketch.request.ImageData
import com.github.panpf.sketch.request.RequestContext
import com.github.panpf.sketch.resize.Resize
import com.github.panpf.sketch.source.DataFrom

/** Translate immutable editor pixels directly; never encode and re-decode a file. */
internal class EditorPixelDecoder(private val source: EditorPixelDataSource) : Decoder {
    override suspend fun getImageInfo() = ImageInfo(
        source.pixels.size.width, source.pixels.size.height, EditorPixelRegionDecoder.PIXEL_MIME
    )

    override suspend fun decode(): ImageData = ImageData(
        image = source.pixels.toEditorSketchImage(),
        imageInfo = getImageInfo(),
        dataFrom = DataFrom.MEMORY,
        resize = Resize(source.pixels.size.width, source.pixels.size.height),
        transformeds = null,
        extras = null
    )

    class Factory : Decoder.Factory {
        override val key = "EditorPixelDecoder"
        override val sortWeight = 0
        override fun create(requestContext: RequestContext, fetchResult: FetchResult): Decoder? =
            (fetchResult.dataSource as? EditorPixelDataSource)?.let(::EditorPixelDecoder)
        override fun equals(other: Any?) = other is Factory
        override fun hashCode() = Factory::class.hashCode()
        override fun toString() = key
    }
}
