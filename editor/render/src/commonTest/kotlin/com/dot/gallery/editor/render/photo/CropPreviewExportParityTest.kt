package com.dot.gallery.editor.render.photo

import com.dot.gallery.editor.core.geometry.CropFitting
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.NormalizedCrop
import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoWarp
import com.dot.gallery.editor.core.model.PhotoWarpMapping
import com.dot.gallery.editor.core.model.PixelRect
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.CpuFrameRenderer
import com.dot.gallery.editor.render.FrameSource
import com.dot.gallery.editor.render.LinearRgbaFrame
import com.dot.gallery.editor.render.RenderRequest
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.render.renderGraph
import com.dot.gallery.editor.render.toLinearFrame
import com.dot.gallery.editor.render.toSrgbPixels
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The crop tool's contract: what the frame shows is what export writes.
 *
 * While the tool is open the canvas renders the crop-dropped display
 * document — the whole warp plane — and frames the crop rectangle on it.
 * For rotated, keystoned and aspect-locked crops (also turned/flipped),
 * the exported pixels must equal the display render's pixels at the
 * crop's plane offset, and the export must contain no transparent (blank)
 * pixel.
 */
class CropPreviewExportParityTest {

    private val raw = PixelSize(64, 48)
    private val pixels = IntArray(raw.pixelCount.toInt()) { i ->
        val x = i % raw.width
        val y = i / raw.width
        (0xFF shl 24) or ((x * 4) shl 16) or ((y * 5) shl 8) or ((x * y) and 0xFF)
    }

    private class Raster(private val size: PixelSize, private val px: IntArray) : PhotoRaster {
        override val info = PhotoInfo("ref", "img", size, 1)
        override suspend fun readRegion(rect: PixelRect): SrgbPixels {
            val out = IntArray(rect.width * rect.height)
            for (y in 0 until rect.height) for (x in 0 until rect.width) {
                out[y * rect.width + x] = px[(rect.top + y) * size.width + rect.left + x]
            }
            return SrgbPixels(PixelSize(rect.width, rect.height), out)
        }
        override suspend fun readPreview(maxEdge: Int) = readRegion(PixelRect(0, 0, size.width, size.height))
        override fun close() = Unit
    }

    private class Sink(val width: Int) : ScanlineSink {
        val rows = mutableListOf<IntArray>()
        override fun writeRow(argb: IntArray, offset: Int) {
            rows += argb.copyOfRange(offset, offset + width)
        }
        override fun finish() = Unit
        override fun close() = Unit
    }

    private class Source(private val frame: LinearRgbaFrame) : FrameSource {
        override suspend fun read(sourceId: String) = frame
    }

    private fun doc(geometry: PhotoGeometry) = PhotoDocument(
        source = MediaAsset.Image("ref", "ref", raw),
        geometry = geometry
    )

    private fun geometries(): List<PhotoGeometry> {
        val out = mutableListOf<PhotoGeometry>()
        val starts = listOf(
            PhotoGeometry(),
            PhotoGeometry(crop = NormalizedCrop(0.1, 0.05, 0.8, 0.9), quarterTurns = 1),
            PhotoGeometry(quarterTurns = 2, flipHorizontal = true)
        )
        val warps = listOf(
            PhotoWarp(rotationDegrees = 7.5f),
            PhotoWarp(rotationDegrees = -3f, verticalPerspective = 30f),
            PhotoWarp(horizontalPerspective = -40f, rotationDegrees = 12f),
            PhotoWarp(rotationDegrees = 1.2f, verticalPerspective = -20f, horizontalPerspective = 15f)
        )
        for (start in starts) for (warp in warps) for (aspect in listOf(null, 1.0, 4.0 / 5.0, 16.0 / 9.0)) {
            out += CropFitting.withWarp(raw, start, warp, aspect)
        }
        return out
    }

    @Test
    fun frameShowsExactlyWhatExportWrites() = runTest {
        val linear = SrgbPixels(raw, pixels).toLinearFrame()
        for (geometry in geometries()) {
            val recipe = doc(geometry)
            val display = doc(geometry.copy(crop = NormalizedCrop()))
            val preview = CpuFrameRenderer(Source(linear))
                .render(RenderRequest(1, display.renderGraph())).toSrgbPixels()
            val mapping = PhotoWarpMapping(raw, 1, geometry)
            val out = mapping.outputSize
            val sink = Sink(out.width)
            PhotoStripExporter().export(Raster(raw, pixels), recipe, sink) {}
            assertEquals(out.height, sink.rows.size, "$geometry rows")
            val pv = preview.copyPixels()
            val pw = preview.size.width
            val off = mapping.planeOffset
            for (y in 0 until out.height) for (x in 0 until out.width) {
                val e = sink.rows[y][x]
                val p = pv[(off.y + y) * pw + off.x + x]
                assertTrue(e ushr 24 == 0xFF, "blank export pixel ($x,$y) for $geometry")
                for (shift in intArrayOf(24, 16, 8, 0)) {
                    val diff = abs((e shr shift and 0xFF) - (p shr shift and 0xFF))
                    assertTrue(diff <= 2, "($x,$y) ch${shift / 8} off by $diff for $geometry")
                }
            }
        }
    }
}
