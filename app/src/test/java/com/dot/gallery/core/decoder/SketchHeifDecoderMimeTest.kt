package com.dot.gallery.core.decoder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * JVM tests for [SketchHeifDecoder.Factory.sniffHeifMimeType], the strict `ftyp` brand
 * sniff used to resolve HEIF/AVIF sources whose reported MIME is missing or generic.
 */
class SketchHeifDecoderMimeTest {

    private fun ftypHeader(brand: String): ByteArray {
        // ISO-BMFF layout: 4-byte box size, 'ftyp', 4-byte major brand, 4-byte version.
        val header = ByteArray(64)
        header[3] = 24
        "ftyp".toByteArray(Charsets.ISO_8859_1).copyInto(header, 4)
        brand.toByteArray(Charsets.ISO_8859_1).copyInto(header, 8)
        return header
    }

    @Test
    fun `avif primary brand resolves to image_avif`() {
        assertEquals("image/avif", SketchHeifDecoder.Factory.sniffHeifMimeType(ftypHeader("avif")))
    }

    @Test
    fun `avis primary brand resolves to image_avif`() {
        assertEquals("image/avif", SketchHeifDecoder.Factory.sniffHeifMimeType(ftypHeader("avis")))
    }

    @Test
    fun `heic primary brand resolves to image_heif`() {
        assertEquals("image/heif", SketchHeifDecoder.Factory.sniffHeifMimeType(ftypHeader("heic")))
    }

    @Test
    fun `mif1 primary brand resolves to image_heif`() {
        assertEquals("image/heif", SketchHeifDecoder.Factory.sniffHeifMimeType(ftypHeader("mif1")))
    }

    @Test
    fun `non heif primary brand resolves to null`() {
        assertNull(SketchHeifDecoder.Factory.sniffHeifMimeType(ftypHeader("mp41")))
    }

    @Test
    fun `brand substring without ftyp box does not sniff as heif`() {
        // A non-HEIF payload that happens to contain the "avif" byte sequence must not be
        // claimed — the heuristic substring pass in HeifSniffer is intentionally skipped.
        val payload = ByteArray(128) { 0x55 }
        "avif".toByteArray().copyInto(payload, 64)
        assertNull(SketchHeifDecoder.Factory.sniffHeifMimeType(payload))
    }

    @Test
    fun `truncated header resolves to null`() {
        assertNull(SketchHeifDecoder.Factory.sniffHeifMimeType(ByteArray(8)))
    }
}
