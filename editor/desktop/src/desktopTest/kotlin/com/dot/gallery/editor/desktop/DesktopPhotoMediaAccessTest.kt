package com.dot.gallery.editor.desktop

import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.NormalizedCrop
import com.dot.gallery.editor.core.model.PhotoCurves
import com.dot.gallery.editor.core.model.PhotoDetails
import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PhotoFinishing
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoGrain
import com.dot.gallery.editor.core.model.PhotoImageLayer
import com.dot.gallery.editor.core.model.PhotoLensBlur
import com.dot.gallery.editor.core.model.PhotoSpatialEffects
import com.dot.gallery.editor.core.model.PhotoToneCurve
import com.dot.gallery.editor.core.model.PhotoVignette
import com.dot.gallery.editor.core.model.PhotoWhiteBalance
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
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.name
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

class DesktopPhotoMediaAccessTest {

    private lateinit var dir: Path
    private lateinit var access: DesktopPhotoMediaAccess
    private lateinit var errors: MutableList<Throwable>

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("editor-desktop-test")
        errors = mutableListOf()
        access = DesktopPhotoMediaAccess(onError = { errors += it })
    }

    @AfterTest
    fun tearDown() {
        dir.toFile().deleteRecursively()
    }

    /** A 4x2 RGB image; every pixel carries a unique packed colour. */
    private fun fixtureImage(): BufferedImage {
        val image = BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB)
        val pixels = IntArray(8) { (0xFF shl 24) or (0x1000 + it * 0x010203) }
        image.setRGB(0, 0, 4, 2, pixels, 0, 4)
        return image
    }

    private fun writeFixture(name: String, format: String): Path {
        val path = dir.resolve(name)
        ImageIO.write(fixtureImage(), format, path.toFile())
        return path
    }

    /** Splices a minimal Exif APP1 segment (big-endian, tag 0x0112) after SOI. */
    private fun withExifOrientation(jpeg: ByteArray, orientation: Int): ByteArray {
        val app1 = ByteArrayOutputStream()
        val tiff = ByteArrayOutputStream()
        tiff.write(byteArrayOf(0x4D, 0x4D))
        be16at(tiff, 42); be32at(tiff, 8); be16at(tiff, 1)
        be16at(tiff, 0x0112); be16at(tiff, 3); be32at(tiff, 1)
        be16at(tiff, orientation); be16at(tiff, 0); be32at(tiff, 0)
        val payload = "Exif\u0000\u0000".toByteArray() + tiff.toByteArray()
        app1.write(0xFF); app1.write(0xE1)
        be16at(app1, payload.size + 2)
        app1.write(payload)
        return jpeg.copyOf(2) + app1.toByteArray() + jpeg.copyOfRange(2, jpeg.size)
    }

    private fun be16at(out: ByteArrayOutputStream, v: Int) { out.write(v shr 8); out.write(v) }
    private fun be32at(out: ByteArrayOutputStream, v: Int) {
        out.write(v shr 24); out.write(v shr 16); out.write(v shr 8); out.write(v)
    }

    private fun document(path: Path, size: PixelSize, geometry: PhotoGeometry = PhotoGeometry()) =
        PhotoDocument(
            MediaAsset.Image(path.toAbsolutePath().toString(), path.toAbsolutePath().toString(), size),
            geometry = geometry
        )

    @Test
    fun `open probes size and applies EXIF orientation to upright size`() = runTest {
        val jpegBytes = Files.readAllBytes(writeFixture("probe.jpg", "jpeg"))
        val rotated = dir.resolve("rotated.jpg")
        Files.write(rotated, withExifOrientation(jpegBytes, 6))
        access.open(rotated.toAbsolutePath().toString()).use { raster ->
            assertEquals(PixelSize(4, 2), raster.info.rawSize)
            assertEquals(6, raster.info.orientation)
            assertEquals(PixelSize(2, 4), raster.info.uprightSize)
            assertEquals(PixelSize(2, 4), raster.readPreview(1024).size)
        }
    }

    @Test
    fun `readRegion returns raw un-oriented pixels`() = runTest {
        val path = writeFixture("region.png", "png")
        access.open(path.toAbsolutePath().toString()).use { raster ->
            val region = raster.readRegion(PixelRect(1, 0, 2, 2))
            assertEquals(PixelSize(2, 2), region.size)
            val expected = IntArray(4)
            fixtureImage().getRGB(1, 0, 2, 2, expected, 0, 2)
            assertContentEquals(expected, region.copyPixels())
        }
    }

    @Test
    fun `export writes an exact pixel PNG sibling and leaves the source alone`() = runTest {
        val path = writeFixture("photo.png", "png")
        val sourceBytesBefore = Files.readAllBytes(path)
        var receipt: com.dot.gallery.editor.render.photo.PhotoExportResult? = null
        val result = access.export(
            document(path, PixelSize(4, 2), PhotoGeometry(quarterTurns = 1)),
            onPublished = { receipt = it }
        ) {}
        assertEquals("image/png", result.mimeType)
        assertEquals(PixelSize(2, 4), result.size)
        // The commit receipt fired exactly once with the same result.
        assertEquals(result, receipt)
        val target = Path.of(java.net.URI.create(result.reference))
        assertTrue(Files.exists(target))
        val decoded = ImageIO.read(target.toFile())
        assertNotNull(decoded)
        val out = IntArray(8)
        decoded.getRGB(0, 0, 2, 4, out, 0, 2)
        val source = IntArray(8)
        fixtureImage().getRGB(0, 0, 4, 2, source, 0, 4)
        // 90 CW: output rows are source columns read bottom-up.
        assertContentEquals(
            intArrayOf(source[4], source[0], source[5], source[1],
                source[6], source[2], source[7], source[3]),
            out
        )
        // Source is a byte-identical, unmodified file.
        assertContentEquals(sourceBytesBefore, Files.readAllBytes(path))
        // No staging files left behind, no cleanup diagnostics.
        assertTrue(Files.list(dir).use { s -> s.noneMatch { it.name.startsWith(".refra-") } })
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `crop export produces the cropped dimensions`() = runTest {
        val path = writeFixture("cropped.png", "png")
        val result = access.export(
            document(
                path, PixelSize(4, 2),
                PhotoGeometry(crop = NormalizedCrop(0.0, 0.0, 0.5, 1.0))
            )
        ) {}
        assertEquals(PixelSize(2, 2), result.size)
        val decoded = ImageIO.read(Path.of(java.net.URI.create(result.reference)).toFile())
        assertEquals(2, decoded.width)
        assertEquals(2, decoded.height)
    }

    @Test
    fun `bad references and unsupported content fail without artifacts`() = runTest {
        assertFailsWith<java.io.IOException> {
            access.open(dir.resolve("missing.png").toAbsolutePath().toString())
        }
        val text = dir.resolve("notes.txt")
        Files.writeString(text, "not an image")
        assertFailsWith<com.dot.gallery.editor.render.photo.UnsupportedPhotoException> {
            access.open(text.toAbsolutePath().toString())
        }
        assertFailsWith<IllegalArgumentException> {
            access.open("https://example.com/x.png")
        }
        assertFailsWith<Exception> {
            access.export(document(text, PixelSize(1, 1))) {}
        }
        assertTrue(Files.list(dir).use { s -> s.noneMatch { it.name.startsWith(".refra-") } })
        assertTrue(Files.list(dir).use { s -> s.noneMatch { it.name.contains("-edited-") } })
    }

    @Test
    fun `export inside a directory with spaces and symbols returns a usable URI`() = runTest {
        val weird = dir.resolve("space # ü")
        Files.createDirectories(weird)
        val path = weird.resolve("photo.png")
        ImageIO.write(fixtureImage(), "png", path.toFile())
        val result = access.export(document(path, PixelSize(4, 2))) {}
        // A raw "file://" + path string would corrupt spaces/#/non-ASCII.
        val reopened = Path.of(java.net.URI.create(result.reference))
        assertTrue(Files.exists(reopened))
        val decoded = ImageIO.read(reopened.toFile())
        assertNotNull(decoded)
        assertEquals(4, decoded.width)
        assertEquals(2, decoded.height)
    }

    @Test
    fun `cancelling during export leaves neither staging nor output`() = runTest {
        val path = writeFixture("cancel.png", "png")
        val exportJob = Job()
        try {
            withContext(exportJob) {
                access.export(document(path, PixelSize(4, 2))) { exportJob.cancel() }
            }
            fail("cancelled export must not return")
        } catch (expected: CancellationException) {
        }
        assertTrue(Files.list(dir).use { s ->
            s.noneMatch { it.name.startsWith(".refra-") || it.name.contains("-edited-") }
        })
    }

    /** A flat opaque-red 4x2 image, readable as a second composition source. */
    private fun writeFlatFixture(name: String, argb: Int): Path {
        val image = BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, 4, 2, IntArray(8) { argb }, 0, 4)
        val path = dir.resolve(name)
        ImageIO.write(image, "png", path.toFile())
        return path
    }

    @Test
    fun `export composites a visible image layer and leaves no handles`() = runTest {
        val base = writeFixture("base.png", "png")
        val overlay = writeFlatFixture("overlay.png", 0xFFFF0000.toInt())
        val doc = document(base, PixelSize(4, 2)).copy(
            layers = persistentListOf(
                PhotoImageLayer(
                    id = "l1", name = "overlay",
                    source = MediaAsset.Image(
                        "src-l1",
                        overlay.toAbsolutePath().toString(),
                        PixelSize(4, 2)
                    )
                )
            )
        )
        val result = access.export(doc) {}
        val decoded = ImageIO.read(Path.of(java.net.URI.create(result.reference)).toFile())
        assertEquals(PixelSize(4, 2), result.size)
        // The opaque layer covers the canvas — every pixel is its colour.
        val out = IntArray(8)
        decoded.getRGB(0, 0, 4, 2, out, 0, 4)
        assertContentEquals(IntArray(8) { 0xFFFF0000.toInt() }, out)
        // All rasters closed cleanly: no staging left, no diagnostics.
        assertTrue(Files.list(dir).use { s -> s.noneMatch { it.name.startsWith(".refra-") } })
        assertTrue(errors.isEmpty())
    }

    /**
     * The finishing recipe through a real PNG round-trip: the exported
     * pixels must equal the CPU render graph's output over the same
     * decoded source — white balance, curves, vignette and grain all
     * applied in absolute output coordinates — while the source file
     * stays byte-identical.
     */
    @Test
    fun `finishing export matches the CPU graph on a real PNG`() = runTest {
        val size = PixelSize(24, 32)
        val pixels = IntArray(size.width * size.height) { i ->
            val x = i % size.width
            val y = i / size.width
            (0xFF shl 24) or (((x * 9 + y * 3) % 256) shl 16) or
                (((x * 2 + y * 11) % 256) shl 8) or ((x * 5 + y * 7) % 256)
        }
        val image = BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, size.width, size.height, pixels, 0, size.width)
        val path = dir.resolve("finishing.png")
        ImageIO.write(image, "png", path.toFile())
        val sourceBytesBefore = Files.readAllBytes(path)

        val doc = document(path, size).copy(
            finishing = PhotoFinishing(
                whiteBalance = PhotoWhiteBalance(3800f, -15f),
                curves = PhotoCurves(
                    master = PhotoToneCurve.ofQuarterOrdinates(
                    listOf(0f, 0.3f, 0.55f, 0.8f, 1f)
                ),
                    blue = PhotoToneCurve.ofQuarterOrdinates(
                    listOf(0f, 0.25f, 0.5f, 0.8f, 0.92f)
                )
                ),
                vignette = PhotoVignette(
                    amount = -0.5f, midpoint = 0.4f, highlights = 0.6f
                ),
                grain = PhotoGrain(
                    amount = 0.4f, size = 0.3f, roughness = 0.5f
                )
            )
        )
        val result = access.export(doc) {}
        assertEquals("image/png", result.mimeType)

        val decoded = ImageIO.read(Path.of(java.net.URI.create(result.reference)).toFile())
        val out = IntArray(size.width * size.height)
        decoded.getRGB(0, 0, size.width, size.height, out, 0, size.width)
        val expected = CpuFrameRenderer(object : FrameSource {
            override suspend fun read(sourceId: String): LinearRgbaFrame =
                SrgbPixels(size, pixels).toLinearFrame()
        }).render(RenderRequest(1, doc.renderGraph()))
            .toSrgbPixels().copyPixels()
        // Same processor, same coordinates — the PNG round-trip is
        // lossless, so this is byte-exact, not a tolerance.
        assertContentEquals(expected, out)
        // Source untouched, no staging debris.
        assertContentEquals(sourceBytesBefore, Files.readAllBytes(path))
        assertTrue(Files.list(dir).use { s ->
            s.noneMatch { it.name.startsWith(".refra-") }
        })
    }

    @Test
    fun `spatial export matches the CPU graph on a real PNG`() = runTest {
        // 24x200 forces the strip exporter across its 128-row band
        // boundary inside the blur disk's reach — the halo expansion
        // must make the bands invisible.
        val size = PixelSize(24, 200)
        val pixels = IntArray(size.width * size.height) { i ->
            val x = i % size.width
            val y = i / size.width
            (0xFF shl 24) or (((x * 9 + y * 3) % 256) shl 16) or
                (((x * 2 + y * 11) % 256) shl 8) or ((x * 5 + y * 7) % 256)
        }
        val image = BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, size.width, size.height, pixels, 0, size.width)
        val path = dir.resolve("spatial.png")
        ImageIO.write(image, "png", path.toFile())
        val sourceBytesBefore = Files.readAllBytes(path)

        val doc = document(path, size).copy(
            spatial = PhotoSpatialEffects(
                details = PhotoDetails(
                    texture = 0.6f, clarity = 0.3f,
                    sharpening = 0.8f, noiseReduction = 0.2f
                ),
                lensBlur = PhotoLensBlur(
                    strength = 0.7f, transition = 0.5f,
                    highlightBokeh = 0.5f,
                    centerX = 0.4f, centerY = 0.6f
                )
            ),
            finishing = PhotoFinishing(
                vignette = PhotoVignette(amount = -0.3f)
            )
        )
        val result = access.export(doc) {}
        assertEquals("image/png", result.mimeType)

        val decoded = ImageIO.read(Path.of(java.net.URI.create(result.reference)).toFile())
        val out = IntArray(size.width * size.height)
        decoded.getRGB(0, 0, size.width, size.height, out, 0, size.width)
        val expected = CpuFrameRenderer(object : FrameSource {
            override suspend fun read(sourceId: String): LinearRgbaFrame =
                SrgbPixels(size, pixels).toLinearFrame()
        }).render(RenderRequest(1, doc.renderGraph()))
            .toSrgbPixels().copyPixels()
        assertContentEquals(expected, out)
        assertContentEquals(sourceBytesBefore, Files.readAllBytes(path))
        assertTrue(Files.list(dir).use { s ->
            s.noneMatch { it.name.startsWith(".refra-") }
        })
    }

    @Test
    fun `a missing layer source fails the export without artifacts`() = runTest {
        val base = writeFixture("base.png", "png")
        val doc = document(base, PixelSize(4, 2)).copy(
            layers = persistentListOf(
                PhotoImageLayer(
                    id = "l1", name = "overlay",
                    source = MediaAsset.Image(
                        "src-l1", dir.resolve("missing.png").toAbsolutePath().toString(),
                        PixelSize(4, 2)
                    )
                )
            )
        )
        assertFailsWith<Exception> {
            access.export(doc) {}
        }
        assertTrue(Files.list(dir).use { s ->
            s.noneMatch { it.name.startsWith(".refra-") || it.name.contains("-edited-") }
        })
    }
}
