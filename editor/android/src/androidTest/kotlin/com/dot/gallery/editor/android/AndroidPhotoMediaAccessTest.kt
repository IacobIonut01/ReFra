package com.dot.gallery.editor.android

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.NormalizedCrop
import com.dot.gallery.editor.core.model.PhotoBlendMode
import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PhotoExportFormat
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoImageLayer
import com.dot.gallery.editor.core.model.PhotoLayerTransform
import com.dot.gallery.editor.core.model.PixelRect
import com.dot.gallery.editor.core.model.PixelSize
import java.io.File
import java.io.FileOutputStream
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidPhotoMediaAccessTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val cleanupErrors = mutableListOf<Throwable>()
    private val access = AndroidPhotoMediaAccess(context, { cleanupErrors += it })
    private val stagingDir = File(context.cacheDir, "media-access-test")
    private val createdUris = mutableListOf<Uri>()

    @Before
    fun setUp() {
        stagingDir.mkdirs()
    }

    @After
    fun tearDown() {
        for (uri in createdUris) {
            context.contentResolver.delete(uri, null, null)
        }
        stagingDir.deleteRecursively()
    }

    /** A 4x2 ARGB bitmap whose every pixel is uniquely coloured. */
    private fun fixtureBitmap(width: Int = 4, height: Int = 2): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height) { (0xFF shl 24) or (it * 1000) }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun writeFixture(name: String, format: Bitmap.CompressFormat): File {
        val file = File(stagingDir, name)
        FileOutputStream(file).use { fixtureBitmap().compress(format, 95, it) }
        return file
    }

    private fun document(reference: String, size: PixelSize, geometry: PhotoGeometry = PhotoGeometry()) =
        PhotoDocument(MediaAsset.Image(reference, reference, size), geometry = geometry)

    /** Publishes a bitmap through MediaStore as a real content:// source. */
    private fun insertPng(name: String, bitmap: Bitmap): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.IS_PENDING, 1)
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "Pictures/EditorMediaAccessTest"
            )
        }
        val uri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        )!!
        resolver.openOutputStream(uri)!!.use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        createdUris += uri
        return uri
    }

    private fun readBytes(uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }

    /** IEC sRGB electro-optical decode — mirrors render/SrgbConversion. */
    private fun srgbToLinear(channel: Int): Float {
        val c = channel / 255f
        return if (c <= 0.04045f) {
            c / 12.92f
        } else {
            ((c + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
        }
    }

    private fun linearToSrgb(linear: Float): Int {
        val c = if (linear <= 0.0031308f) {
            12.92f * linear
        } else {
            (1.055 * linear.toDouble().pow(1.0 / 2.4) - 0.055).toFloat()
        }
        return (c * 255f).roundToInt().coerceIn(0, 255)
    }

    /**
     * `compositeLayerPixel` for a fully covered output pixel on an opaque
     * backdrop: `as=opacity`, `ao=1`, so `Co = as*Blend + (1-as)*Cb` —
     * Screen's blend is `cb + cs - cb*cs`, all in linear light.
     */
    private fun expectedScreenPixel(baseArgb: Int, layerArgb: Int, opacity: Float): Int {
        var out = 0xFF shl 24
        for (shift in intArrayOf(16, 8, 0)) {
            val cb = srgbToLinear((baseArgb shr shift) and 0xFF)
            val cs = srgbToLinear((layerArgb shr shift) and 0xFF)
            val blended = cb + cs - cb * cs
            out = out or (
                linearToSrgb(opacity * blended + (1f - opacity) * cb) shl shift
                )
        }
        return out
    }

    private fun assertChannelClose(expected: Int, actual: Int, label: String) {
        for (shift in intArrayOf(16, 8, 0)) {
            val e = (expected shr shift) and 0xFF
            val a = (actual shr shift) and 0xFF
            assertTrue(
                "$label channel shift $shift: expected $e got $a",
                abs(e - a) <= 2
            )
        }
    }

    /** The export/import paths must leave no pending rows or staged files. */
    private fun assertNoArtifacts() {
        val pending = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            MediaStore.Images.Media.IS_PENDING + " != 0 AND (" +
                MediaStore.Images.Media.RELATIVE_PATH + " LIKE ? OR " +
                MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?)",
            arrayOf("Pictures/ReFra%", "Pictures/EditorMediaAccessTest%"),
            null
        )?.use { it.count } ?: 0
        assertEquals(0, pending)
        assertTrue(
            File(context.cacheDir, "editor/exports").listFiles().isNullOrEmpty()
        )
        assertTrue(
            File(context.cacheDir, "editor/imports").listFiles().isNullOrEmpty()
        )
    }

    @Test
    fun openProbesDimensionsAndOrientation() = runBlocking {
        val file = writeFixture("probe.jpg", Bitmap.CompressFormat.JPEG)
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, "6")
            saveAttributes()
        }
        val raster = access.open(Uri.fromFile(file).toString())
        raster.use {
            assertEquals(6, it.info.orientation)
            assertEquals(PixelSize(4, 2), it.info.rawSize)
            assertEquals(PixelSize(2, 4), it.info.uprightSize)
            val preview = it.readPreview(1024)
            assertEquals(PixelSize(2, 4), preview.size)
        }
    }

    @Test
    fun previewSubsamplesPanoramasToMaxEdge() = runBlocking {
        // A narrow panorama must not bypass subsampling: inSampleSize is
        // driven by the largest axis, not by both axes fitting.
        val wide = Bitmap.createBitmap(8192, 32, Bitmap.Config.ARGB_8888)
        val file = File(stagingDir, "pano.png")
        FileOutputStream(file).use { wide.compress(Bitmap.CompressFormat.PNG, 100, it) }
        wide.recycle()
        val raster = access.open(Uri.fromFile(file).toString())
        raster.use {
            val preview = it.readPreview(512)
            assertTrue(preview.size.width <= 512)
            assertTrue(preview.size.height <= 512)
            assertEquals(512, preview.size.width)
        }
    }

    @Test
    fun readRegionReturnsRawUnorientedPixels() = runBlocking {
        val file = writeFixture("region.png", Bitmap.CompressFormat.PNG)
        val raster = access.open(Uri.fromFile(file).toString())
        raster.use {
            val region = it.readRegion(PixelRect(1, 0, 2, 2))
            assertEquals(PixelSize(2, 2), region.size)
            val source = IntArray(8)
            fixtureBitmap().getPixels(source, 0, 4, 0, 0, 4, 2)
            // Rect (1,0)-(3,2) -> pixels (1,0) (2,0) (1,1) (2,1).
            org.junit.Assert.assertArrayEquals(
                intArrayOf(source[1], source[2], source[5], source[6]),
                region.copyPixels()
            )
        }
    }

    @Test
    fun exportWritesAnEditedPngCopyToMediaStore() = runBlocking {
        val file = writeFixture("export.png", Bitmap.CompressFormat.PNG)
        val sourceBytesBefore = file.readBytes()
        val reference = Uri.fromFile(file).toString()
        val rotated = PhotoGeometry(quarterTurns = 1)
        var receipt: com.dot.gallery.editor.render.photo.PhotoExportResult? = null
        val result = access.export(
            document(reference, PixelSize(4, 2), rotated),
            onPublished = { receipt = it }
        ) {}
        createdUris += Uri.parse(result.reference)
        assertEquals("image/png", result.mimeType)
        assertEquals(PixelSize(2, 4), result.size)
        // The commit receipt fired exactly once with the same result.
        assertEquals(result, receipt)
        val decoded = context.contentResolver.openInputStream(Uri.parse(result.reference)).use {
            BitmapFactory.decodeStream(it)
        }
        assertNotNull(decoded)
        assertEquals(2, decoded!!.width)
        assertEquals(4, decoded.height)
        // Rotating 4x2 clockwise: output row 0 = source column 0 bottom-up.
        val source = IntArray(8)
        fixtureBitmap().getPixels(source, 0, 4, 0, 0, 4, 2)
        val out = IntArray(8)
        decoded.getPixels(out, 0, 2, 0, 0, 2, 4)
        assertEquals(source[4], out[0])
        assertEquals(source[0], out[1])
        // The source file is byte-identical afterwards.
        org.junit.Assert.assertArrayEquals(sourceBytesBefore, file.readBytes())
        org.junit.Assert.assertTrue(cleanupErrors.isEmpty())
    }

    @Test
    fun jpegSourceExportsAsJpegAtTheSameResolution() = runBlocking {
        // The regression this covers: a JPEG original re-encoded as lossless
        // RGBA PNG (a 50 MP camera photo became ~60 MB). The copy must stay the
        // same container, source quality and full resolution.
        val bitmap = fixtureBitmap(64, 48)
        val file = File(stagingDir, "export-source.jpg")
        FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
        }
        bitmap.recycle()
        assertTrue(
            "the native JPEG encoder is required for JPEG sources",
            JpegScanlineEncoder.isAvailable()
        )
        val sourceBytesBefore = file.readBytes()
        val reference = Uri.fromFile(file).toString()
        var receipt: com.dot.gallery.editor.render.photo.PhotoExportResult? = null
        val result = access.export(
            document(reference, PixelSize(64, 48)),
            onPublished = { receipt = it }
        ) {}
        createdUris += Uri.parse(result.reference)
        assertEquals("image/jpeg", result.mimeType)
        assertEquals(PixelSize(64, 48), result.size)
        assertEquals(result, receipt)
        assertTrue(
            "output name must keep the JPEG container",
            Uri.parse(result.reference).let { uri ->
                context.contentResolver.query(
                    uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null, null, null
                )?.use { cursor ->
                    cursor.moveToFirst() &&
                        cursor.getString(0).endsWith(".jpg")
                } ?: false
            }
        )
        val decoded = context.contentResolver.openInputStream(Uri.parse(result.reference)).use {
            BitmapFactory.decodeStream(it)
        }
        assertNotNull(decoded)
        assertEquals(64, decoded!!.width)
        assertEquals(48, decoded.height)
        // The source file is byte-identical afterwards.
        org.junit.Assert.assertArrayEquals(sourceBytesBefore, file.readBytes())
        assertTrue(cleanupErrors.isEmpty())
    }

    @Test
    fun explicitFormatsAreOffered() {
        val formats = access.supportedExportFormats
        assertTrue(formats.contains(PhotoExportFormat.JPEG))
        assertTrue(formats.contains(PhotoExportFormat.PNG))
        assertTrue(formats.contains(PhotoExportFormat.WEBP))
        if (HeifScanlineEncoder.isAvailable()) {
            assertTrue(formats.contains(PhotoExportFormat.HEIF))
        }
        if (JxlBitmapEncoder.isAvailable()) {
            assertTrue(formats.contains(PhotoExportFormat.JXL))
        }
    }

    @Test
    fun webpExportWritesAWebpCopyAtFullResolution() = runBlocking {
        val file = writeFixture("format.png", Bitmap.CompressFormat.PNG)
        val bytesBefore = file.readBytes()
        val result = access.export(
            document(Uri.fromFile(file).toString(), PixelSize(4, 2)),
            format = PhotoExportFormat.WEBP
        ) {}
        createdUris += Uri.parse(result.reference)
        assertEquals("image/webp", result.mimeType)
        assertEquals(PixelSize(4, 2), result.size)
        val decoded = context.contentResolver.openInputStream(Uri.parse(result.reference)).use {
            BitmapFactory.decodeStream(it)
        }
        assertNotNull(decoded)
        assertEquals(4, decoded!!.width)
        assertEquals(2, decoded.height)
        org.junit.Assert.assertArrayEquals(bytesBefore, file.readBytes())
        assertTrue(cleanupErrors.isEmpty())
    }

    @Test
    fun heifExportWritesAGridCopyAtFullResolution() = runBlocking {
        assertTrue(
            "the native HEIF encoder is required for HEIF export",
            HeifScanlineEncoder.isAvailable()
        )
        // Larger than one 512 px grid cell in both axes, so the last tile
        // row/column is partial — the case the padding clamp exists for.
        val bitmap = Bitmap.createBitmap(600, 520, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF2E7D32.toInt())
        val file = File(stagingDir, "heif-source.png")
        FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        val result = access.export(
            document(Uri.fromFile(file).toString(), PixelSize(600, 520)),
            format = PhotoExportFormat.HEIF
        ) {}
        createdUris += Uri.parse(result.reference)
        assertEquals("image/heic", result.mimeType)
        assertEquals(PixelSize(600, 520), result.size)
        val decoded = context.contentResolver.openInputStream(Uri.parse(result.reference)).use {
            BitmapFactory.decodeStream(it)
        }
        assertNotNull(decoded)
        assertEquals(600, decoded!!.width)
        assertEquals(520, decoded.height)
        assertTrue(cleanupErrors.isEmpty())
    }

    @Test
    fun jxlExportWritesAJxlCopyAtFullResolution() = runBlocking {
        assertTrue(
            "the libjxl encoder is required for JPEG XL export",
            JxlBitmapEncoder.isAvailable()
        )
        val file = writeFixture("format.png", Bitmap.CompressFormat.PNG)
        val result = access.export(
            document(Uri.fromFile(file).toString(), PixelSize(4, 2)),
            format = PhotoExportFormat.JXL
        ) {}
        createdUris += Uri.parse(result.reference)
        assertEquals("image/jxl", result.mimeType)
        assertEquals(PixelSize(4, 2), result.size)
        val encoded = context.contentResolver
            .openInputStream(Uri.parse(result.reference))!!.use { it.readBytes() }
        val decoded = com.awxkee.jxlcoder.JxlCoder.decode(encoded)
        assertNotNull(decoded)
        assertEquals(4, decoded!!.width)
        assertEquals(2, decoded.height)
        assertTrue(cleanupErrors.isEmpty())
    }

    @Test
    fun cropExportsOnlyTheMappedRegion() = runBlocking {
        val file = writeFixture("crop.png", Bitmap.CompressFormat.PNG)
        val reference = Uri.fromFile(file).toString()
        val crop = PhotoGeometry(
            crop = NormalizedCrop(0.0, 0.0, 0.5, 1.0)
        )
        val result = access.export(document(reference, PixelSize(4, 2), crop)) {}
        createdUris += Uri.parse(result.reference)
        assertEquals(PixelSize(2, 2), result.size)
    }

    @Test
    fun unsupportedAndMissingSourcesFailWithoutOutput() = runBlocking {
        try {
            access.open("https://example.com/x.png")
            fail("remote references must be rejected")
        } catch (expected: IllegalArgumentException) {
        }
        try {
            access.export(document("file:///nonexistent/x.png", PixelSize(1, 1))) {}
            fail("missing source must fail")
        } catch (expected: Exception) {
            // Any failure is acceptable; the point is no output is produced.
        }
    }

    @Test
    fun layeredExportCompositesTheScreenLayerThroughMediaStore() = runBlocking {
        // Two real MediaStore sources: a uniquely coloured 8x8 base and a
        // solid 4x4 overlay. A solid fill makes every bilinear tap equal,
        // so the analytic expectation is exact for fully covered pixels.
        val overlayArgb = 0xFF3CA05A.toInt()
        val baseBitmap = fixtureBitmap(8, 8)
        val overlayBitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(overlayArgb) }
        val baseUri = insertPng("layered-base.png", baseBitmap)
        val overlayUri = insertPng("layered-overlay.png", overlayBitmap)
        val baseBytesBefore = readBytes(baseUri)
        val overlayBytesBefore = readBytes(overlayUri)

        // fit = min(8/4, 8/4) = 2, effective = 1.3, centre (5,5): pixels
        // 3..6 in both axes sit fully inside the layer domain, x<=1 or
        // y<=1 never reach it; the rest is antialiased edge (not probed).
        val layer = PhotoImageLayer(
            id = "overlay-layer",
            name = "Overlay",
            source = MediaAsset.Image(
                "overlay-source", overlayUri.toString(), PixelSize(4, 4)
            ),
            transform = PhotoLayerTransform(
                scale = .65f, translationX = 1f, translationY = 1f
            ),
            blendMode = PhotoBlendMode.Screen,
            opacity = .65f
        )
        val document = PhotoDocument(
            source = MediaAsset.Image(
                baseUri.toString(), baseUri.toString(), PixelSize(8, 8)
            ),
            layers = persistentListOf(layer)
        )
        var receipt: com.dot.gallery.editor.render.photo.PhotoExportResult? = null
        val result = access.export(document, onPublished = { receipt = it }) {}
        createdUris += Uri.parse(result.reference)
        assertEquals("image/png", result.mimeType)
        assertEquals(PixelSize(8, 8), result.size)
        assertEquals(result, receipt)

        val decoded = context.contentResolver
            .openInputStream(Uri.parse(result.reference)).use {
                BitmapFactory.decodeStream(it)
            }
        assertNotNull(decoded)
        assertEquals(8, decoded!!.width)
        assertEquals(8, decoded.height)

        val base = IntArray(64)
        baseBitmap.getPixels(base, 0, 8, 0, 0, 8, 8)
        val out = IntArray(64)
        decoded.getPixels(out, 0, 8, 0, 0, 8, 8)
        // Uncovered pixels keep the base colour; covered pixels are the
        // linear-light Screen composite at 65% effective source alpha.
        assertChannelClose(base[0], out[0], "(0,0)")
        assertChannelClose(base[7 * 8 + 1], out[7 * 8 + 1], "(1,7)")
        for ((x, y) in listOf(4 to 4, 5 to 5, 6 to 3)) {
            val i = y * 8 + x
            assertChannelClose(
                expectedScreenPixel(base[i], overlayArgb, .65f),
                out[i], "($x,$y)"
            )
            assertEquals(0xFF, out[i] ushr 24)
        }

        // Both sources are untouched and still readable; nothing is left
        // pending in MediaStore or staged on disk.
        assertArrayEquals(baseBytesBefore, readBytes(baseUri))
        assertArrayEquals(overlayBytesBefore, readBytes(overlayUri))
        assertNoArtifacts()
        assertTrue(cleanupErrors.isEmpty())
        baseBitmap.recycle()
        overlayBitmap.recycle()
    }
}
