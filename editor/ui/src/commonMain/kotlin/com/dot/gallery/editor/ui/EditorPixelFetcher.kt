package com.dot.gallery.editor.ui

import com.github.panpf.sketch.fetch.FetchResult
import com.github.panpf.sketch.fetch.Fetcher
import com.github.panpf.sketch.request.RequestContext

internal class EditorPixelFetcher(private val source: EditorPixelDataSource) : Fetcher {
    override suspend fun fetch(): Result<FetchResult> =
        Result.success(FetchResult(source, EditorPixelRegionDecoder.PIXEL_MIME))

    data class Factory(private val source: EditorPixelDataSource) : Fetcher.Factory {
        override val sortWeight = 0
        override fun create(requestContext: RequestContext): Fetcher? =
            if (requestContext.request.uri.toString() == source.key) EditorPixelFetcher(source) else null
    }
}
