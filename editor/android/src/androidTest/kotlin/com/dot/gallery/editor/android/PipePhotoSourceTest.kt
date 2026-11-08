package com.dot.gallery.editor.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PixelSize
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the non-seekable staging path: [PipeContentProvider] hands back
 * a pipe descriptor (pread -> ESPIPE), so [AndroidPhotoMediaAccess] must
 * copy the stream into a private staging file and delete it on close.
 */
@RunWith(AndroidJUnit4::class)
class PipePhotoSourceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val cleanupErrors = mutableListOf<Throwable>()
    private val access = AndroidPhotoMediaAccess(context, { cleanupErrors += it })
    private val createdUris = mutableListOf<Uri>()
    private var sourcePng: ByteArray = ByteArray(0)

    @Before
    fun setUp() {
        val bitmap = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(
            IntArray(8) { (0xFF shl 24) or (it * 1000) },
            0, 4, 0, 0, 4, 2
        )
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        sourcePng = out.toByteArray()
        PipeContentProvider.payloads.clear()
        PipeContentProvider.payloads["pipe_sample.png"] = sourcePng
    }

    @After
    fun tearDown() {
        for (uri in createdUris) {
            context.contentResolver.delete(uri, null, null)
        }
        PipeContentProvider.payloads.clear()
        // Staged imports are private to cacheDir; nothing may linger.
        val imports = java.io.File(context.cacheDir, "editor/imports")
        if (imports.isDirectory) {
            assertTrue(
                "staged imports must be deleted on close: ${imports.list()}",
                imports.listFiles().isNullOrEmpty()
            )
        }
    }

    @Test
    fun pipeBackedSourceOpensPreviewsAndExports() = runBlocking {
        val uri = PipeContentProvider.uri("pipe_sample.png")
        val raster = access.open(uri.toString())
        raster.use {
            assertEquals(PixelSize(4, 2), it.info.rawSize)
            // DISPLAY_NAME comes from the provider's query, not a row number.
            assertEquals("pipe_sample.png", it.info.displayName)
            val preview = it.readPreview(512)
            assertEquals(PixelSize(4, 2), preview.size)
        }

        val result = access.export(
            PhotoDocument(
                source = MediaAsset.Image(uri.toString(), uri.toString(), PixelSize(4, 2))
            )
        ) {}
        createdUris += Uri.parse(result.reference)
        assertEquals("image/png", result.mimeType)
        assertEquals(PixelSize(4, 2), result.size)
        val decoded = context.contentResolver
            .openInputStream(Uri.parse(result.reference)).use {
                BitmapFactory.decodeStream(it)
            }
        assertNotNull(decoded)
        assertTrue(cleanupErrors.isEmpty())
    }
}
