package com.dot.gallery.editor.render.photo

import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.NormalizedCrop
import com.dot.gallery.editor.core.model.NormalizedPoint
import com.dot.gallery.editor.core.model.PhotoBlendMode
import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PhotoMapping
import com.dot.gallery.editor.core.model.PhotoCurves
import com.dot.gallery.editor.core.model.PhotoDetails
import com.dot.gallery.editor.core.model.PhotoFinishing
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoGrain
import com.dot.gallery.editor.core.model.PhotoImageLayer
import com.dot.gallery.editor.core.model.PhotoLensBlur
import com.dot.gallery.editor.core.model.PhotoSpatialEffects
import com.dot.gallery.editor.core.model.PhotoToneCurve
import com.dot.gallery.editor.core.model.PhotoVignette
import com.dot.gallery.editor.core.model.PhotoWhiteBalance
import com.dot.gallery.editor.core.model.PhotoLayerMask
import com.dot.gallery.editor.core.model.PhotoLayerTransform
import com.dot.gallery.editor.core.model.PhotoMaskMode
import com.dot.gallery.editor.core.model.PhotoMaskStroke
import com.dot.gallery.editor.core.model.PhotoWarp
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
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PhotoStripExporterTest {

    /** In-memory raster over a distinct-valued raw ARGB buffer. */
    private class FakeRaster(
        reference: String,
        private val rawSize: PixelSize,
        orientation: Int = 1,
        private val pixels: IntArray =
            IntArray(rawSize.pixelCount.toInt()) { it + 1 }
    ) : PhotoRaster {
        override val info = PhotoInfo(reference, "img", rawSize, orientation)
        val requestedRects = mutableListOf<PixelRect>()
        var closed = false

        override suspend fun readRegion(rect: PixelRect): SrgbPixels {
            requestedRects += rect
            val out = IntArray(rect.width * rect.height)
            for (y in 0 until rect.height) {
                for (x in 0 until rect.width) {
                    out[y * rect.width + x] =
                        pixels[(rect.top + y) * rawSize.width + rect.left + x]
                }
            }
            return SrgbPixels(PixelSize(rect.width, rect.height), out)
        }

        override suspend fun readPreview(maxEdge: Int): SrgbPixels =
            readRegion(PixelRect(0, 0, rawSize.width, rawSize.height))

        override fun close() {
            closed = true
        }
    }

    private class CollectingSink(
        val expectedWidth: Int,
        private val failOnRow: Int = -1
    ) : ScanlineSink {
        val rows = mutableListOf<IntArray>()
        var finished = false
        var closed = false

        override fun writeRow(argb: IntArray, offset: Int) {
            if (rows.size == failOnRow) throw CancellationException("cancelled")
            rows += argb.copyOfRange(offset, offset + expectedWidth)
        }

        override fun finish() {
            finished = true
        }

        override fun close() {
            closed = true
        }
    }

    private fun document(
        reference: String,
        size: PixelSize,
        geometry: PhotoGeometry = PhotoGeometry(),
        color: ColorAdjustments = ColorAdjustments()
    ) = PhotoDocument(
        source = MediaAsset.Image(reference, reference, size),
        adjustments = color,
        geometry = geometry
    )

    private fun flatten(rows: List<IntArray>): IntArray =
        rows.flatMap { it.toList() }.toIntArray()

    @Test
    fun outputZonesMatchExportAcrossAllExifOrientations() = runTest {
        val rawSize = PixelSize(19, 23)
        val pixels = IntArray(19 * 23) { i ->
            0xFF000000.toInt() or ((i * 37 and 255) shl 16) or
                ((i * 73 and 255) shl 8) or (i * 11 and 255)
        }
        for (orientation in 1..8) {
            val raster = FakeRaster("ref", rawSize, orientation, pixels)
            val geometry = PhotoGeometry(
                crop = NormalizedCrop(0.1, 0.1, 0.9, 0.9),
                quarterTurns = orientation % 4,
                flipHorizontal = true,
                flipVertical = orientation % 2 == 0
            )
            val doc = document("ref", raster.info.uprightSize, geometry)
            val size = PhotoMapping(rawSize, orientation, geometry).outputSize
            val zone = PixelRect(2, 3, size.width - 4, size.height - 6)
            assertZoneMatchesExport(raster, doc, zone)
        }
    }

    @Test
    fun outputZonesShareExportWarpLayerMaskSpatialAndFinishingStages() = runTest {
        val size = PixelSize(73, 173)
        val base = FakeRaster(
            "ref", size, pixels = IntArray(73 * 173) { i ->
                0xFF000000.toInt() or ((i * 17 and 255) shl 16) or
                    ((i * 31 and 255) shl 8) or (i * 47 and 255)
            }
        )
        val overlay = FakeRaster(
            "overlay", PixelSize(39, 51),
            pixels = IntArray(39 * 51) { i -> 0x800055AA.toInt() or ((i and 127) shl 16) }
        )
        val imageLayer = layer(
            "one", "overlay", overlay.info.uprightSize,
            blendMode = PhotoBlendMode.Screen,
            opacity = 0.6f,
            mask = PhotoLayerMask(
                strokes = persistentListOf(
                    PhotoMaskStroke(
                        points = persistentListOf(NormalizedPoint(0.2f, 0.3f)),
                        diameter = 0.4f,
                        mode = PhotoMaskMode.Erase
                    )
                )
            )
        ).copy(transform = PhotoLayerTransform(translationX = 8f, translationY = -3f))
        val doc = document(
            "ref", size,
            geometry = PhotoGeometry(warp = PhotoWarp(rotationDegrees = 1.3f, scale = 1.04f)),
            color = ColorAdjustments(exposureStops = 0.3f, contrast = 1.1f)
        ).copy(
            layers = persistentListOf(imageLayer),
            spatial = PhotoSpatialEffects(
                details = PhotoDetails(texture = 0.3f, sharpening = 0.2f),
                lensBlur = PhotoLensBlur(strength = 0.5f, centerX = 0.3f, centerY = 0.6f)
            ),
            finishing = PhotoFinishing(
                whiteBalance = PhotoWhiteBalance(temperatureKelvin = 4800f, tint = 0.1f),
                vignette = PhotoVignette(amount = -0.3f),
                grain = PhotoGrain(amount = 0.2f, seed = 123)
            )
        )
        val rasters = mapOf(imageLayer.source.id to overlay)
        // Nonzero x/y and more than one band exercise both halo axes and grain origin.
        assertZoneMatchesExport(base, doc, PixelRect(7, 9, 53, 143), rasters)
        assertZoneMatchesExport(base, doc, PixelRect(0, 0, 17, 23), rasters)
        assertZoneMatchesExport(base, doc, PixelRect(58, 150, 15, 23), rasters)
    }

    @Test
    fun zonesReadOnlyTheirBoundedSourceRegionAndRejectInvalidRequests() = runTest {
        val size = PixelSize(300, 300)
        val raster = FakeRaster("ref", size)
        val doc = document("ref", size)
        val zone = PixelRect(71, 83, 13, 17)
        val renderer = PhotoStripExporter()
        val result = renderer.renderRegion(raster, doc, zone)
        assertEquals(PixelSize(13, 17), result.size)
        assertEquals(listOf(zone), raster.requestedRects)
        assertFailsWith<IllegalArgumentException> {
            renderer.renderRegion(raster, doc, zone, sampleSize = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            renderer.renderRegion(raster, doc, PixelRect(299, 299, 2, 2))
        }
    }

    @Test
    fun platformSizedZonesSpanMultipleBoundedWorkingBands() = runTest {
        val size = PixelSize(1024, 1024)
        val raster = FakeRaster("ref", size)
        val result = PhotoStripExporter().renderRegion(
            raster, document("ref", size), PixelRect(0, 0, size.width, size.height)
        )
        assertEquals(size, result.size)
        assertEquals(1, result.argbAt(0, 0))
        assertEquals(1024 * 1024, result.argbAt(1023, 1023))
        assertEquals(8, raster.requestedRects.size)
        assertTrue(raster.requestedRects.all { it.width.toLong() * it.height <= 262_144 })
    }

    private suspend fun assertZoneMatchesExport(
        raster: PhotoRaster,
        doc: PhotoDocument,
        zone: PixelRect,
        layers: Map<String, PhotoRaster> = emptyMap()
    ) {
        val size = PhotoMapping(raster.info.rawSize, raster.info.orientation, doc.geometry).outputSize
        val sink = CollectingSink(size.width)
        val renderer = PhotoStripExporter()
        renderer.export(raster, doc, sink, layers) {}
        for (sample in listOf(1, 2, 3)) {
            val actual = renderer.renderRegion(raster, doc, zone, sample, layers)
            val expected = buildList {
                for (y in zone.top until zone.bottomExclusive step sample) {
                    for (x in zone.left until zone.rightExclusive step sample) add(sink.rows[y][x])
                }
            }.toIntArray()
            assertContentEquals(expected, actual.copyPixels(), "zone=$zone sample=$sample")
        }
    }

    @Test
    fun `identity export streams every raw row in order`() = runTest {
        val raster = FakeRaster("ref", PixelSize(2, 3))
        val sink = CollectingSink(expectedWidth = 2)
        val progress = mutableListOf<Float>()
        PhotoStripExporter().export(
            raster, document("ref", PixelSize(2, 3)), sink
        ) { progress += it }
        assertContentEquals(intArrayOf(1, 2, 3, 4, 5, 6), flatten(sink.rows))
        assertTrue(sink.finished)
        // One band covers the whole 3-row image -> a single progress report.
        assertEquals(listOf(1f), progress)
    }

    @Test
    fun `orientation 6 and a quarter turn map raw reads exactly`() = runTest {
        // Raw 2x3; upright o6 is 4 2 0-style over stored indices+1:
        // upright rows = [5,3,1] then [6,4,2].
        val raster = FakeRaster("ref", PixelSize(2, 3), orientation = 6)
        val sink = CollectingSink(expectedWidth = 3)
        PhotoStripExporter().export(
            raster, document("ref", PixelSize(3, 2)), sink
        ) {}
        assertContentEquals(intArrayOf(5, 3, 1, 6, 4, 2), flatten(sink.rows))

        // Add q1 (upright rotates CW): 6 5 / 4 3 / 2 1 (2x3).
        val rotated = CollectingSink(expectedWidth = 2)
        PhotoStripExporter().export(
            raster, document("ref", PixelSize(2, 3),
                PhotoGeometry(quarterTurns = 1)), rotated
        ) {}
        assertContentEquals(intArrayOf(6, 5, 4, 3, 2, 1), flatten(rotated.rows))
    }

    @Test
    fun `crop and flips limit reads to the mapped region`() = runTest {
        val raster = FakeRaster("ref", PixelSize(2, 3))
        val sink = CollectingSink(expectedWidth = 2)
        // Bottom row only, mirrored: raw row 2 is (5, 6) -> output (6, 5).
        PhotoStripExporter().export(
            raster,
            document("ref", PixelSize(2, 1),
                PhotoGeometry(
                    crop = NormalizedCrop(0.0, 2.0 / 3.0, 1.0, 1.0),
                    flipHorizontal = true
                )),
            sink
        ) {}
        assertContentEquals(intArrayOf(6, 5), flatten(sink.rows))
        // Only raw row 2 was touched.
        assertEquals(listOf(PixelRect(0, 2, 2, 1)), raster.requestedRects)
    }

    @Test
    fun `colour adjustments apply after geometry`() = runTest {
        val raster = FakeRaster("ref", PixelSize(2, 1))
        val neutral = CollectingSink(expectedWidth = 2)
        PhotoStripExporter().export(
            raster, document("ref", PixelSize(2, 1)), neutral
        ) {}
        val adjusted = CollectingSink(expectedWidth = 2)
        PhotoStripExporter().export(
            raster,
            document("ref", PixelSize(2, 1),
                color = ColorAdjustments(exposureStops = 1f)),
            adjusted
        ) {}
        // +1 stop brightens every stored 8-bit channel that can move.
        for (i in 0..1) {
            for (shift in intArrayOf(0, 8, 16)) {
                val before = (neutral.rows[0][i] shr shift) and 0xFF
                val after = (adjusted.rows[0][i] shr shift) and 0xFF
                assertTrue(after >= before, "channel $shift of pixel $i darkened")
            }
        }
    }

    @Test
    fun `tonal endpoints export the defined 8 bit colours`() = runTest {
        // Contract-checked expectations: opaque black with blacks=+1
        // exports (99,99,99,255); opaque white with whites=-1 exports
        // (240,240,240,255) — linear luminances 0.125/0.875 through the
        // sRGB transfer function and round-to-nearest encoding.
        val blackPixels = IntArray(2) { 0xFF000000.toInt() }
        val black = FakeRaster("ref", PixelSize(2, 1), pixels = blackPixels)
        val blackSink = CollectingSink(expectedWidth = 2)
        PhotoStripExporter().export(
            black,
            document("ref", PixelSize(2, 1),
                color = ColorAdjustments(blacks = 1f)),
            blackSink
        ) {}
        assertContentEquals(
            intArrayOf(0xFF636363.toInt(), 0xFF636363.toInt()),
            flatten(blackSink.rows)
        )
        // The export reads — never mutates — the source raster's pixels.
        assertContentEquals(
            IntArray(2) { 0xFF000000.toInt() }, blackPixels
        )

        val white = FakeRaster(
            "ref", PixelSize(2, 1),
            pixels = IntArray(2) { 0xFFFFFFFF.toInt() }
        )
        val whiteSink = CollectingSink(expectedWidth = 2)
        PhotoStripExporter().export(
            white,
            document("ref", PixelSize(2, 1),
                color = ColorAdjustments(whites = -1f)),
            whiteSink
        ) {}
        assertContentEquals(
            intArrayOf(0xFFF0F0F0.toInt(), 0xFFF0F0F0.toInt()),
            flatten(whiteSink.rows)
        )
    }

    @Test
    fun `tall output is produced in bounded bands`() = runTest {
        val raster = FakeRaster("ref", PixelSize(2, 200))
        val sink = CollectingSink(expectedWidth = 2)
        val progress = mutableListOf<Float>()
        PhotoStripExporter().export(
            raster, document("ref", PixelSize(2, 200)), sink
        ) { progress += it }
        assertEquals(200, sink.rows.size)
        assertTrue(sink.finished)
        // Progress is reported per band: 128 rows, then the remaining 72.
        assertEquals(listOf(128f / 200f, 1f), progress)
        // Bands are capped at 128 rows -> at least two region reads.
        assertTrue(raster.requestedRects.size >= 2)
        // The union of reads covers the image exactly once, top to bottom.
        var covered = 0
        for (rect in raster.requestedRects) {
            assertEquals(0, rect.left)
            assertEquals(2, rect.width)
            covered += rect.height
        }
        assertEquals(200, covered)
    }

    private class SingleFrameSource(
        private val frame: LinearRgbaFrame
    ) : FrameSource {
        override suspend fun read(sourceId: String): LinearRgbaFrame = frame
    }

    /**
     * An active warp must export the same pixels the CPU render graph
     * produces end-to-end — colour runs on the source region before the
     * bilinear sample, exactly like the graph's colour->geometry order.
     */
    @Test
    fun `active warp export matches the CPU render graph`() = runTest {
        val raw = PixelSize(8, 8)
        val pixels = IntArray(64) { i ->
            val x = i % 8
            val y = i / 8
            (0xFF shl 24) or ((x * 32) shl 16) or
                ((y * 32) shl 8) or ((x + y) * 16)
        }
        val raster = FakeRaster("ref", raw, pixels = pixels)
        val geometry = PhotoGeometry(
            warp = PhotoWarp(
                rotationDegrees = 20f,
                scale = 1.2f,
                verticalPerspective = 30f,
                horizontalPerspective = -15f
            )
        )
        val doc = document(
            "ref", raw, geometry,
            color = ColorAdjustments(exposureStops = 0.5f, saturation = 1.4f)
        )
        val sink = CollectingSink(expectedWidth = 8)
        PhotoStripExporter().export(raster, doc, sink) {}
        assertTrue(sink.finished)
        assertEquals(8, sink.rows.size)

        val expected = CpuFrameRenderer(
            SingleFrameSource(SrgbPixels(raw, pixels).toLinearFrame())
        ).render(RenderRequest(1, doc.renderGraph())).toSrgbPixels()
        val exp = expected.copyPixels()
        val actual = flatten(sink.rows)
        assertEquals(exp.size, actual.size)
        for (i in exp.indices) {
            for (shift in intArrayOf(24, 16, 8, 0)) {
                val diff = abs(
                    (exp[i] shr shift and 0xFF) - (actual[i] shr shift and 0xFF)
                )
                assertTrue(
                    diff <= 2,
                    "pixel $i channel ${shift / 8} off by $diff " +
                        "(cpu ${exp[i]}, export ${actual[i]})"
                )
            }
        }
        // The export reads — never mutates — the source raster's pixels.
        assertContentEquals(
            IntArray(64) { i ->
                val x = i % 8
                val y = i / 8
                (0xFF shl 24) or ((x * 32) shl 16) or
                    ((y * 32) shl 8) or ((x + y) * 16)
            },
            pixels
        )
    }

    /**
     * A large source under the most extreme legal warp must stay bounded:
     * every region read fits the 262144-pixel source budget and no read
     * pulls the whole stored raster. A 2400x1600 source is 3.84M pixels,
     * so an unbounded implementation would fail this immediately.
     */
    @Test
    fun `warped export keeps every source read bounded`() = runTest {
        val raw = PixelSize(2400, 1600)
        val raster = FakeRaster("big", raw)
        val sink = CollectingSink(expectedWidth = raw.width)
        PhotoStripExporter().export(
            raster,
            document(
                "big", raw,
                PhotoGeometry(
                    warp = PhotoWarp(
                        verticalPerspective = 100f,
                        horizontalPerspective = 100f
                    )
                )
            ),
            sink
        ) {}
        assertTrue(sink.finished)
        assertEquals(raw.height, sink.rows.size)
        val full = PixelRect(0, 0, raw.width, raw.height)
        assertTrue(raster.requestedRects.isNotEmpty())
        for (rect in raster.requestedRects) {
            assertTrue(rect.isInside(raw), "read $rect escapes the source")
            assertTrue(
                rect.width.toLong() * rect.height <= 262_144L,
                "read $rect exceeds the source budget"
            )
            assertFalse(rect == full, "read pulled the whole source")
        }
    }

    /**
     * The finishing stage runs per band in absolute output coordinates:
     * a 200-row export crosses the 128-row band boundary mid-image, and
     * the whole finishing recipe — white balance, curves, vignette and
     * grain — must produce exactly the frame the CPU render graph makes
     * in one pass. Vignette masks and grain cells must not restart at
     * the boundary.
     */
    @Test
    fun `finishing export across bands matches the CPU render graph`() =
        runTest {
        val size = PixelSize(17, 200)
        val pixels = IntArray(size.width * size.height) { i ->
            val x = i % size.width
            val y = i / size.width
            (0xFF shl 24) or (((x * 13 + y) % 256) shl 16) or
                (((x + y * 7) % 256) shl 8) or ((x * y) % 256)
        }
        val raster = FakeRaster("ref", size, pixels = pixels)
        val doc = document("ref", size).copy(
            finishing = PhotoFinishing(
                whiteBalance = PhotoWhiteBalance(3200f, -10f),
                curves = PhotoCurves(
                    master = PhotoToneCurve.ofQuarterOrdinates(
                    listOf(0f, 0.3f, 0.55f, 0.8f, 1f)
                ),
                    red = PhotoToneCurve.ofQuarterOrdinates(
                    listOf(0.05f, 0.25f, 0.5f, 0.75f, 0.95f)
                )
                ),
                vignette = PhotoVignette(
                    amount = -0.6f, midpoint = 0.4f,
                    roundness = 0.7f, highlights = 0.5f
                ),
                grain = PhotoGrain(
                    amount = 0.5f, size = 0.5f, roughness = 0.2f, seed = 7
                )
            )
        )
        val sink = CollectingSink(expectedWidth = 17)
        PhotoStripExporter().export(raster, doc, sink) {}
        assertTrue(sink.finished)
        assertEquals(200, sink.rows.size)

        val expected = CpuFrameRenderer(
            SingleFrameSource(SrgbPixels(size, pixels).toLinearFrame())
        ).render(RenderRequest(1, doc.renderGraph())).toSrgbPixels()
        // The same processor on the same pixels — byte-exact, band
        // boundaries invisible.
        assertContentEquals(expected.copyPixels(), flatten(sink.rows))
        // The export reads — never mutates — the source raster's pixels.
        assertContentEquals(
            IntArray(size.width * size.height) { i ->
                val x = i % size.width
                val y = i / size.width
                (0xFF shl 24) or (((x * 13 + y) % 256) shl 16) or
                    (((x + y * 7) % 256) shl 8) or ((x * y) % 256)
            },
            pixels
        )
    }

    /**
     * The spatial stage runs per expanded band: each output band
     * composites the recipe's halo of extra rows, Details and the
     * lens blur evaluate them in absolute output coordinates, and the
     * central rows are cropped before finishing. A 200-row export
     * crosses the 128-row boundary inside the blur's reach, so the
     * focus mask and the disk taps must not restart there — the result
     * must equal the one-pass CPU render graph byte for byte.
     */
    @Test
    fun `spatial export across expanded bands matches the CPU graph`() =
        runTest {
        val size = PixelSize(25, 200)
        val pixels = IntArray(size.width * size.height) { i ->
            val x = i % size.width
            val y = i / size.width
            (0xFF shl 24) or (((x * 29 + y * 3) % 256) shl 16) or
                (((x * 5 + y * 11) % 256) shl 8) or ((x * y + y) % 256)
        }
        val raster = FakeRaster("ref", size, pixels = pixels)
        val doc = document("ref", size).copy(
            spatial = PhotoSpatialEffects(
                details = PhotoDetails(
                    texture = 0.7f, clarity = 0.4f,
                    sharpening = 0.9f, noiseReduction = 0.3f
                ),
                // Radial focus just off-centre so the blur disk crosses
                // the band boundary in both directions.
                lensBlur = PhotoLensBlur(
                    strength = 0.8f, transition = 0.5f,
                    highlightBokeh = 0.6f,
                    centerX = 0.4f, centerY = 0.62f
                )
            ),
            finishing = PhotoFinishing(
                vignette = PhotoVignette(amount = -0.4f),
                grain = PhotoGrain(amount = 0.3f, seed = 3)
            )
        )
        val sink = CollectingSink(expectedWidth = 25)
        PhotoStripExporter().export(raster, doc, sink) {}
        assertTrue(sink.finished)
        assertEquals(200, sink.rows.size)

        val expected = CpuFrameRenderer(
            SingleFrameSource(SrgbPixels(size, pixels).toLinearFrame())
        ).render(RenderRequest(1, doc.renderGraph())).toSrgbPixels()
        assertContentEquals(expected.copyPixels(), flatten(sink.rows))
        // The export reads — never mutates — the source raster.
        assertContentEquals(
            IntArray(size.width * size.height) { i ->
                val x = i % size.width
                val y = i / size.width
                (0xFF shl 24) or (((x * 29 + y * 3) % 256) shl 16) or
                    (((x * 5 + y * 11) % 256) shl 8) or ((x * y + y) % 256)
            },
            pixels
        )
        // Band reads widened past the raw band but stayed bounded.
        assertTrue(raster.requestedRects.isNotEmpty())
        for (rect in raster.requestedRects) {
            assertTrue(rect.isInside(size), "read $rect escapes the source")
        }
        // At least one read must have expanded: without the halo the
        // bottom band's blur would read past its composited rows.
        assertTrue(
            raster.requestedRects.any { it.height > 72 },
            "the halo must widen at least one band's source read"
        )
    }

    @Test
    fun `a neutral spatial stage exports byte-identical to none`() =
        runTest {
        val raster = FakeRaster("ref", PixelSize(2, 3))
        val plain = CollectingSink(expectedWidth = 2)
        PhotoStripExporter().export(
            raster, document("ref", PixelSize(2, 3)), plain
        ) {}
        val spatial = CollectingSink(expectedWidth = 2)
        PhotoStripExporter().export(
            raster,
            document("ref", PixelSize(2, 3)).copy(
                spatial = PhotoSpatialEffects()
            ),
            spatial
        ) {}
        assertContentEquals(flatten(plain.rows), flatten(spatial.rows))
    }

    @Test
    fun `a neutral finishing exports byte-identical to no finishing`() =
        runTest {
        val raster = FakeRaster("ref", PixelSize(2, 3))
        val plain = CollectingSink(expectedWidth = 2)
        PhotoStripExporter().export(
            raster, document("ref", PixelSize(2, 3)), plain
        ) {}
        val finished = CollectingSink(expectedWidth = 2)
        PhotoStripExporter().export(
            raster,
            document("ref", PixelSize(2, 3)).copy(
                finishing = PhotoFinishing()
            ),
            finished
        ) {}
        assertContentEquals(flatten(plain.rows), flatten(finished.rows))
    }

    @Test
    fun `reference mismatch and oversized sources are rejected`() = runTest {
        val raster = FakeRaster("real", PixelSize(2, 2))
        assertFailsWith<IllegalArgumentException> {
            PhotoStripExporter().export(
                raster, document("other", PixelSize(2, 2)), CollectingSink(2)
            ) {}
        }
        val huge = FakeRaster("big", PixelSize(70_000, 1))
        assertFailsWith<IllegalArgumentException> {
            PhotoStripExporter().export(
                huge, document("big", PixelSize(70_000, 1)), CollectingSink(1)
            ) {}
        }
    }

    @Test
    fun `cancellation propagates and never finishes the sink`() = runTest {
        val raster = FakeRaster("ref", PixelSize(2, 3))
        val sink = CollectingSink(expectedWidth = 2, failOnRow = 1)
        assertFailsWith<CancellationException> {
            PhotoStripExporter().export(
                raster, document("ref", PixelSize(2, 3)), sink
            ) {}
        }
        assertFalse(sink.finished)
        assertEquals(1, sink.rows.size)
    }

    private class MapFrameSource(
        private val frames: Map<String, LinearRgbaFrame>
    ) : FrameSource {
        override suspend fun read(sourceId: String): LinearRgbaFrame =
            frames.getValue(sourceId)
    }

    private fun layer(
        id: String,
        reference: String,
        size: PixelSize,
        blendMode: PhotoBlendMode = PhotoBlendMode.Normal,
        opacity: Float = 1f,
        visible: Boolean = true,
        mask: PhotoLayerMask = PhotoLayerMask()
    ) = PhotoImageLayer(
        id = id,
        name = "layer $id",
        source = MediaAsset.Image("src-$id", reference, size),
        blendMode = blendMode,
        opacity = opacity,
        visible = visible,
        mask = mask
    )

    private fun assertArgbClose(
        expected: IntArray,
        actual: IntArray,
        tolerance: Int = 2
    ) {
        assertEquals(expected.size, actual.size)
        for (i in expected.indices) {
            for (shift in intArrayOf(24, 16, 8, 0)) {
                val diff = abs(
                    (expected[i] shr shift and 0xFF) - (actual[i] shr shift and 0xFF)
                )
                assertTrue(
                    diff <= tolerance,
                    "pixel $i channel ${shift / 8} off by $diff " +
                        "(cpu ${expected[i].toUInt().toString(16)}, " +
                        "export ${actual[i].toUInt().toString(16)})"
                )
            }
        }
    }

    @Test
    fun `layered export matches the CPU render graph`() = runTest {
        val size = PixelSize(4, 4)
        val basePixels = IntArray(16) { i ->
            val x = i % 4
            val y = i / 4
            (0xFF shl 24) or ((x * 80) shl 16) or ((y * 80) shl 8) or 40
        }
        val raster = FakeRaster("base", size, pixels = basePixels)
        val layerARaster = FakeRaster(
            "la", size, pixels = IntArray(16) { 0x80FF0000.toInt() }
        )
        val layerBRaster = FakeRaster(
            "lb", size, pixels = IntArray(16) { 0xFF00FF00.toInt() }
        )
        val doc = document("base", size).copy(
            layers = persistentListOf(
                layer("a", "la", size, blendMode = PhotoBlendMode.Multiply),
                layer("b", "lb", size, blendMode = PhotoBlendMode.Screen, opacity = .5f)
            )
        )
        val sink = CollectingSink(expectedWidth = 4)
        PhotoStripExporter().export(
            raster, doc, sink,
            mapOf("src-a" to layerARaster, "src-b" to layerBRaster)
        ) {}
        assertTrue(sink.finished)

        val graphPixels = CpuFrameRenderer(
            MapFrameSource(
                mapOf(
                    "base" to SrgbPixels(size, basePixels).toLinearFrame(),
                    "src-a" to layerARaster.readRegion(
                        PixelRect(0, 0, 4, 4)
                    ).toLinearFrame(),
                    "src-b" to layerBRaster.readRegion(
                        PixelRect(0, 0, 4, 4)
                    ).toLinearFrame()
                )
            )
        ).render(RenderRequest(1, doc.renderGraph())).toSrgbPixels()
        assertArgbClose(graphPixels.copyPixels(), flatten(sink.rows))
    }

    @Test
    fun `a masked layer removes alpha where the stroke erases`() = runTest {
        val size = PixelSize(4, 4)
        val raster = FakeRaster("base", size)
        val layerRaster = FakeRaster(
            "lw", size, pixels = IntArray(16) { 0xFFFFFFFF.toInt() }
        )
        // A wide erase segment down the left edge removes the layer there.
        val mask = PhotoLayerMask(
            strokes = persistentListOf(
                PhotoMaskStroke(
                    points = persistentListOf(
                        NormalizedPoint(0f, 0f), NormalizedPoint(0f, 1f)
                    ),
                    diameter = .6f,
                    mode = PhotoMaskMode.Erase
                )
            )
        )
        val doc = document("base", size).copy(
            layers = persistentListOf(layer("w", "lw", size, mask = mask))
        )
        val sink = CollectingSink(expectedWidth = 4)
        PhotoStripExporter().export(
            raster, doc, sink, mapOf("src-w" to layerRaster)
        ) {}
        assertTrue(sink.finished)

        // Left column loses the white layer entirely; the right column
        // still reads white.
        val rows = flatten(sink.rows)
        for (y in 0 until 4) {
            val left = rows[y * 4]
            val right = rows[y * 4 + 3]
            assertTrue(
                (right shr 16 and 0xFF) > (left shr 16 and 0xFF),
                "row $y: right ${right.toUInt().toString(16)} should stay whiter " +
                    "than left ${left.toUInt().toString(16)}"
            )
        }

        // And it agrees with the CPU graph pixel for pixel.
        val graphPixels = CpuFrameRenderer(
            MapFrameSource(
                mapOf(
                    "base" to SrgbPixels(
                        size, IntArray(16) { it + 1 }
                    ).toLinearFrame(),
                    "src-w" to layerRaster.readRegion(
                        PixelRect(0, 0, 4, 4)
                    ).toLinearFrame()
                )
            )
        ).render(RenderRequest(1, doc.renderGraph())).toSrgbPixels()
        assertArgbClose(graphPixels.copyPixels(), rows)
    }

    @Test
    fun `invisible layers are never read and export as the base alone`() = runTest {
        val size = PixelSize(4, 4)
        val raster = FakeRaster("base", size)
        val hiddenRaster = FakeRaster("hidden", size)
        val doc = document("base", size).copy(
            layers = persistentListOf(
                layer("h", "hidden", size, visible = false)
            )
        )
        val sink = CollectingSink(expectedWidth = 4)
        PhotoStripExporter().export(raster, doc, sink, emptyMap()) {}
        assertTrue(sink.finished)
        assertTrue(hiddenRaster.requestedRects.isEmpty())

        val baseOnly = CollectingSink(expectedWidth = 4)
        PhotoStripExporter().export(raster, document("base", size), baseOnly) {}
        assertContentEquals(flatten(baseOnly.rows), flatten(sink.rows))
    }

    @Test
    fun `missing or mismatched layer rasters fail before any output`() = runTest {
        val size = PixelSize(4, 4)
        val raster = FakeRaster("base", size)
        val doc = document("base", size).copy(
            layers = persistentListOf(layer("a", "la", size))
        )
        val missing = CollectingSink(expectedWidth = 4)
        assertFailsWith<IllegalStateException> {
            PhotoStripExporter().export(raster, doc, missing, emptyMap()) {}
        }
        assertTrue(missing.rows.isEmpty())
        assertFalse(missing.finished)

        val wrong = FakeRaster("other", size)
        val mismatch = CollectingSink(expectedWidth = 4)
        assertFailsWith<IllegalArgumentException> {
            PhotoStripExporter().export(
                raster, doc, mismatch, mapOf("src-a" to wrong)
            ) {}
        }
        assertTrue(mismatch.rows.isEmpty())

        val wrongSize = FakeRaster("la", PixelSize(8, 8))
        assertFailsWith<IllegalArgumentException> {
            PhotoStripExporter().export(
                raster, doc, CollectingSink(4), mapOf("src-a" to wrongSize)
            ) {}
        }
    }

    @Test
    fun `layer source reads stay inside the bounded tile budget`() = runTest {
        val size = PixelSize(4, 4)
        val raster = FakeRaster("base", size)
        // A 1Mpx layer fitting inside a 4x4 canvas forces recursive splits.
        val bigRaster = FakeRaster("big", PixelSize(1024, 1024))
        val doc = document("base", size).copy(
            layers = persistentListOf(
                layer("big", "big", PixelSize(1024, 1024))
            )
        )
        val sink = CollectingSink(expectedWidth = 4)
        PhotoStripExporter().export(
            raster, doc, sink, mapOf("src-big" to bigRaster)
        ) {}
        assertTrue(sink.finished)
        assertTrue(bigRaster.requestedRects.isNotEmpty())
        for (rect in bigRaster.requestedRects) {
            assertTrue(
                rect.width.toLong() * rect.height <= 262_144L,
                "layer read $rect exceeds the source tile budget"
            )
        }
    }
}
