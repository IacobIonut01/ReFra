package com.dot.gallery.metadata

import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.core.metadata.MetadataPolicy
import com.dot.gallery.core.metadata.MetadataSaveMode
import com.dot.gallery.core.metadata.SanitizationResult
import com.dot.gallery.core.util.ext.saveImageStreaming
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.core.presentation.util.update
import com.dot.gallery.domain.uri.Uri
import com.dot.gallery.core.metadata.AndroidMetadataSanitizer
import com.dot.gallery.domain.uri.path





@RunWith(AndroidJUnit4::class)
class MetadataSanitizerTest {
    private val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    @Test
    fun replacePreparesVerifiedCandidateBeforeOriginalIsTrashed() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val original = pngWithoutText()
        val uri = resolver.saveImageStreaming(
            mimeType = "image/png",
            relativePath = Environment.DIRECTORY_PICTURES + "/MetadataTests",
            displayName = "sanitize-${System.nanoTime()}.png"
        ) { fd ->
            android.os.ParcelFileDescriptor.AutoCloseOutputStream(
                android.os.ParcelFileDescriptor.fromFd(fd)
            ).use { it.write(original) }
            true
        }
        val target = requireNotNull(uri)
        var replacement: android.net.Uri? = null
        try {
            val media = media(target, original.size.toLong(), "image/png")
            val result = AndroidMetadataSanitizer(context).sanitize(
                media,
                MetadataPolicy.LOCATION,
                MetadataSaveMode.REPLACE_ORIGINAL
            ) as SanitizationResult.Success
            val replacementUri = result.outputUri
            replacement = replacementUri

            assertTrue(result.saveMode == MetadataSaveMode.REPLACE_ORIGINAL)
            assertFalse(target == replacementUri)
            assertArrayEquals(original, resolver.openInputStream(target)?.use { it.readBytes() })
            assertValidPng(resolver.openInputStream(replacementUri)?.use { it.readBytes() })
        } finally {
            replacement?.let { resolver.delete(it, null, null) }
            resolver.delete(target, null, null)
        }
    }

    @Test
    fun saveCopyLeavesOriginalUntouchedAndPublishesVerifiedCandidate() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val original = pngWithoutText()
        val uri = resolver.saveImageStreaming(
            mimeType = "image/png",
            relativePath = Environment.DIRECTORY_PICTURES + "/MetadataTests",
            displayName = "sanitize-copy-${System.nanoTime()}.png"
        ) { fd ->
            android.os.ParcelFileDescriptor.AutoCloseOutputStream(
                android.os.ParcelFileDescriptor.fromFd(fd)
            ).use { it.write(original) }
            true
        }
        val target = requireNotNull(uri)
        var copy: android.net.Uri? = null
        try {
            val result = AndroidMetadataSanitizer(context).sanitize(
                media(target, original.size.toLong(), "image/png"),
                MetadataPolicy.LOCATION,
                MetadataSaveMode.SAVE_COPY
            )
            val success = result as SanitizationResult.Success
            val copyUri = success.outputUri
            copy = copyUri

            assertTrue(success.saveMode == MetadataSaveMode.SAVE_COPY)
            assertFalse(target == copyUri)
            assertArrayEquals(original, resolver.openInputStream(target)?.use { it.readBytes() })
            assertValidPng(resolver.openInputStream(copyUri)?.use { it.readBytes() })
        } finally {
            copy?.let { resolver.delete(it, null, null) }
            resolver.delete(target, null, null)
        }
    }

    @Test
    fun malformedInputFailsClosedAndLeavesOriginalUntouched() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val original = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte())
        val uri = resolver.saveImageStreaming(
            mimeType = "image/jpeg",
            relativePath = Environment.DIRECTORY_PICTURES + "/MetadataTests",
            displayName = "sanitize-malformed-${System.nanoTime()}.jpg"
        ) { fd ->
            android.os.ParcelFileDescriptor.AutoCloseOutputStream(
                android.os.ParcelFileDescriptor.fromFd(fd)
            ).use { it.write(original) }
            true
        }
        val target = requireNotNull(uri)
        try {
            val result = AndroidMetadataSanitizer(context).sanitize(
                media(target, original.size.toLong(), "image/jpeg"),
                MetadataPolicy.LOCATION,
                MetadataSaveMode.REPLACE_ORIGINAL
            )

            assertTrue(result is SanitizationResult.Unsupported)
            assertArrayEquals(original, resolver.openInputStream(target)?.use { it.readBytes() })
        } finally {
            resolver.delete(target, null, null)
        }
    }

    private fun media(uri: android.net.Uri, size: Long, mime: String) = UriMedia(
        id = System.nanoTime(),
        label = if (mime == "image/png") "fixture.png" else "fixture.jpg",
        uri = uri,
        path = "",
        relativePath = Environment.DIRECTORY_PICTURES + "/MetadataTests/",
        albumID = 0,
        albumLabel = "MetadataTests",
        timestamp = System.currentTimeMillis() / 1000,
        fullDate = "",
        mimeType = mime,
        favorite = 0,
        trashed = 0,
        size = size
    )

    private fun assertValidPng(bytes: ByteArray?) {
        assertTrue(bytes != null && bytes.size >= pngSignature.size)
        assertArrayEquals(pngSignature, requireNotNull(bytes).copyOfRange(0, pngSignature.size))
    }

    private fun pngWithoutText(): ByteArray = png(null)

    private fun png(text: String?): ByteArray {
        val output = ByteArrayOutputStream()
        output.write(pngSignature)
        writeChunk(output, "IHDR", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0))
        if (text != null) writeChunk(output, "tEXt", "Comment\u0000$text".toByteArray())
        writeChunk(output, "IDAT", byteArrayOf(0x78, 0x01, 0x01, 0x04, 0x00, 0xFB.toByte(), 0xFF.toByte(), 0, 0, 0, 0, 0, 4, 0, 1))
        writeChunk(output, "IEND", ByteArray(0))
        return output.toByteArray()
    }

    private fun writeChunk(output: ByteArrayOutputStream, type: String, data: ByteArray) {
        output.write(be32(data.size))
        val typeBytes = type.toByteArray()
        output.write(typeBytes)
        output.write(data)
        val crc = CRC32().apply {
            update(typeBytes)
            update(data)
        }
        output.write(be32(crc.value.toInt()))
    }

    private fun be32(value: Int) = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte()
    )
}
