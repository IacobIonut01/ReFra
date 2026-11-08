package com.dot.gallery.editor.ui

import com.dot.gallery.editor.render.SrgbPixels
import com.github.panpf.sketch.Sketch
import com.github.panpf.sketch.source.DataFrom
import com.github.panpf.sketch.source.DataSource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import okio.Buffer
import okio.IOException
import okio.Path
import okio.Source

@OptIn(ExperimentalUuidApi::class)
internal class EditorPixelDataSource(val pixels: SrgbPixels) : DataSource {
    override val key = "editor-preview://${Uuid.random()}"
    override val dataFrom = DataFrom.MEMORY
    override fun openSource(): Source = Buffer()
    override fun getFile(sketch: Sketch): Path =
        throw IOException("editor pixel snapshots have no encoded file")
}
