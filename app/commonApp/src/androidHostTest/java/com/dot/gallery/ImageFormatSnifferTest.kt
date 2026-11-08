package com.dot.gallery

import com.dot.gallery.core.decoder.format.ImageFormatSniffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.dot.gallery.core.util.or
import com.dot.gallery.domain.util.isGif



/**
 * Unit tests for [ImageFormatSniffer] magic-byte detection — the core of fix that keeps
 * standard images on the native decode path even when MediaStore reports a RAW/TIFF MIME.
 */
class ImageFormatSnifferTest {

    private fun bytes(vararg ints: Int): ByteArray = ByteArray(ints.size) { ints[it].toByte() }

    // Real SOI + APP1(Exif) prefix, as in attachment (RawTherapee/Sony JPEG).
    private val jpegExif = bytes(0xFF, 0xD8, 0xFF, 0xE1, 0x2A, 0xE6, 0x45, 0x78, 0x69, 0x66)
    private val jpegJfif = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46)
    private val png = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00)
    private val webp = bytes(0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50)
    private val gif89 = bytes(0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x10, 0x00)
    private val gif87 = bytes(0x47, 0x49, 0x46, 0x38, 0x37, 0x61, 0x10, 0x00)
    private val bmp = bytes(0x42, 0x4D, 0x36, 0x00, 0x00, 0x00)
    private val tiffLe = bytes(0x49, 0x49, 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00)
    private val tiffBe = bytes(0x4D, 0x4D, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08)
    // CR2 (Canon RAW) is a TIFF container: II 2A 00 then 0x10 marker + "CR".
    private val cr2 = bytes(0x49, 0x49, 0x2A, 0x00, 0x10, 0x00, 0x00, 0x00, 0x43, 0x52)

    @Test
    fun detectsStandardFormats() {
        assertTrue(ImageFormatSniffer.isJpeg(jpegExif))
        assertTrue(ImageFormatSniffer.isJpeg(jpegJfif))
        assertTrue(ImageFormatSniffer.isPng(png))
        assertTrue(ImageFormatSniffer.isWebp(webp))
        assertTrue(ImageFormatSniffer.isGif(gif89))
        assertTrue(ImageFormatSniffer.isGif(gif87))
        assertTrue(ImageFormatSniffer.isBmp(bmp))
    }

    @Test
    fun nativelyDecodableCoversAllStandardFormats() {
        listOf(jpegExif, jpegJfif, png, webp, gif89, gif87, bmp).forEach {
            assertTrue(ImageFormatSniffer.isNativelyDecodable(it))
        }
    }

    @Test
    fun tiffAndRawAreNotFlaggedAsNativelyDecodable() {
        assertTrue(ImageFormatSniffer.isTiffMagic(tiffLe))
        assertTrue(ImageFormatSniffer.isTiffMagic(tiffBe))
        assertTrue(ImageFormatSniffer.isTiffMagic(cr2))
        // Critical: TIFF/RAW containers must NOT be treated as standard, or a real RAW would lose
        // its embedded-preview extraction.
        assertFalse(ImageFormatSniffer.isNativelyDecodable(tiffLe))
        assertFalse(ImageFormatSniffer.isNativelyDecodable(tiffBe))
        assertFalse(ImageFormatSniffer.isNativelyDecodable(cr2))
    }

    @Test
    fun standardMimeForReturnsCanonicalMime() {
        assertEquals("image/jpeg", ImageFormatSniffer.standardMimeFor(jpegExif))
        assertEquals("image/png", ImageFormatSniffer.standardMimeFor(png))
        assertEquals("image/webp", ImageFormatSniffer.standardMimeFor(webp))
        assertEquals("image/gif", ImageFormatSniffer.standardMimeFor(gif89))
        assertEquals("image/bmp", ImageFormatSniffer.standardMimeFor(bmp))
        assertNull(ImageFormatSniffer.standardMimeFor(tiffLe))
        assertNull(ImageFormatSniffer.standardMimeFor(cr2))
    }

    @Test
    fun honoursExplicitLengthAndShortBuffers() {
        // A JPEG SOI whose valid prefix is only 2 bytes must not be misread as JPEG (needs 3).
        val partial = bytes(0xFF, 0xD8, 0x00)
        assertFalse(ImageFormatSniffer.isJpeg(partial, length = 2))
        assertTrue(ImageFormatSniffer.isJpeg(jpegExif, length = 3))
        assertFalse(ImageFormatSniffer.isNativelyDecodable(ByteArray(0)))
        assertNull(ImageFormatSniffer.standardMimeFor(ByteArray(1)))
    }
}
