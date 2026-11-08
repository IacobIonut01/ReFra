package com.dot.gallery.editor.io

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class JpegExifOrientationTest {

    private fun be16(out: ByteArrayOutputStream, v: Int) {
        out.write(v shr 8); out.write(v)
    }

    private fun be32(out: ByteArrayOutputStream, v: Int) {
        out.write(v shr 24); out.write(v shr 16); out.write(v shr 8); out.write(v)
    }

    /** A minimal JPEG: SOI, one APP1/Exif with IFD0 -> [orientation], EOI. */
    private fun jpeg(orientation: Int, littleEndian: Boolean): ByteArray {
        val tiff = ByteArrayOutputStream()
        if (littleEndian) {
            tiff.write(byteArrayOf(0x49, 0x49))
            tiff.write(byteArrayOf(42, 0))
            tiff.write(byteArrayOf(8, 0, 0, 0))
            tiff.write(byteArrayOf(1, 0))
            tiff.write(byteArrayOf(0x12, 0x01))       // tag 0x0112
            tiff.write(byteArrayOf(3, 0))             // SHORT
            tiff.write(byteArrayOf(1, 0, 0, 0))       // count 1
            tiff.write(byteArrayOf(orientation.toByte(), 0, 0, 0))
            tiff.write(byteArrayOf(0, 0, 0, 0))       // next IFD
        } else {
            val big = ByteArrayOutputStream()
            big.write(byteArrayOf(0x4D, 0x4D))
            be16(big, 42); be32(big, 8)
            be16(big, 1)
            be16(big, 0x0112); be16(big, 3); be32(big, 1)
            be16(big, orientation); be16(big, 0)
            be32(big, 0)
            tiff.write(big.toByteArray())
        }
        val payload = ByteArrayOutputStream()
        payload.write(byteArrayOf(0x45, 0x78, 0x69, 0x66, 0, 0)) // Exif\0\0
        payload.write(tiff.toByteArray())
        val jpg = ByteArrayOutputStream()
        jpg.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))     // SOI
        jpg.write(byteArrayOf(0xFF.toByte(), 0xE1.toByte()))     // APP1
        be16(jpg, payload.size() + 2)
        jpg.write(payload.toByteArray())
        jpg.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))     // EOI
        return jpg.toByteArray()
    }

    private fun read(bytes: ByteArray) =
        JpegExifOrientation.read(ByteArrayInputStream(bytes))

    @Test
    fun `reads big-endian and little-endian orientations`() {
        assertEquals(6, read(jpeg(6, littleEndian = false)))
        assertEquals(8, read(jpeg(8, littleEndian = true)))
        assertEquals(1, read(jpeg(1, littleEndian = false)))
    }

    @Test
    fun `defaults to 1 for non-JPEG PNG input`() {
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52
        )
        assertEquals(1, read(png))
        assertEquals(1, read(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `missing or out-of-range orientation tags default to 1`() {
        // Valid Exif TIFF with an unrelated single tag (0x0100 width).
        val tiff = ByteArrayOutputStream()
        tiff.write(byteArrayOf(0x4D, 0x4D))
        be16(tiff, 42); be32(tiff, 8); be16(tiff, 1)
        be16(tiff, 0x0100); be16(tiff, 3); be32(tiff, 1); be32(tiff, 640)
        be32(tiff, 0)
        val payload = ByteArrayOutputStream()
        payload.write(byteArrayOf(0x45, 0x78, 0x69, 0x66, 0, 0))
        payload.write(tiff.toByteArray())
        val jpg = ByteArrayOutputStream()
        jpg.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        jpg.write(byteArrayOf(0xFF.toByte(), 0xE1.toByte()))
        be16(jpg, payload.size() + 2)
        jpg.write(payload.toByteArray())
        jpg.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
        assertEquals(1, read(jpg.toByteArray()))

        // Orientation values outside 1..8 are not honoured.
        assertEquals(1, read(jpeg(0, littleEndian = false)))
        assertEquals(1, read(jpeg(9, littleEndian = false)))
    }

    @Test
    fun `truncated segments default to 1`() {
        val full = jpeg(6, littleEndian = false)
        // Cut inside the TIFF block.
        assertEquals(1, read(full.copyOf(10)))
        assertEquals(1, read(full.copyOf(20)))
        // Just the SOI.
        assertEquals(1, read(full.copyOf(2)))
        // A declared-but-absent APP1 payload.
        val dangling = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), 0, 40)
        assertEquals(1, read(dangling))
    }

    @Test
    fun `unrelated APP1 segments are skipped for a later Exif APP1`() {
        val xmp = ByteArrayOutputStream()
        xmp.write(byteArrayOf(0xFF.toByte(), 0xE1.toByte()))
        val xmpPayload = "http://ns.adobe.com/xap/1.0/\u0000<x:xmpmeta/>".toByteArray()
        be16(xmp, xmpPayload.size + 2)
        xmp.write(xmpPayload)
        val exif = jpeg(8, littleEndian = false)
        val combined = exif.copyOf(2) + xmp.toByteArray() + exif.copyOfRange(2, exif.size)
        assertEquals(8, read(combined))
    }

    @Test
    fun `malformed TIFF inside APP1 falls back to 1`() {
        // Exif header followed by garbage instead of a byte-order mark.
        val payload = ByteArrayOutputStream()
        payload.write(byteArrayOf(0x45, 0x78, 0x69, 0x66, 0, 0))
        payload.write(byteArrayOf(0x5A, 0x5A, 1, 2, 3, 4, 5, 6, 7, 8))
        val jpg = ByteArrayOutputStream()
        jpg.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        jpg.write(byteArrayOf(0xFF.toByte(), 0xE1.toByte()))
        be16(jpg, payload.size() + 2)
        jpg.write(payload.toByteArray())
        assertEquals(1, read(jpg.toByteArray()))

        // IFD offset pointing outside the segment.
        val bad = ByteArrayOutputStream()
        bad.write(byteArrayOf(0x45, 0x78, 0x69, 0x66, 0, 0, 0x4D, 0x4D))
        be16(bad, 42); be32(bad, 0x7FFFFFF0)
        val jpg2 = ByteArrayOutputStream()
        jpg2.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        jpg2.write(byteArrayOf(0xFF.toByte(), 0xE1.toByte()))
        be16(jpg2, bad.size() + 2)
        jpg2.write(bad.toByteArray())
        assertEquals(1, read(jpg2.toByteArray()))
    }

    @Test
    fun `exif segment without the TIFF magic is not parsed`() {
        // Correct byte order but magic 41 instead of 42.
        val tiff = ByteArrayOutputStream()
        tiff.write(byteArrayOf(0x4D, 0x4D))
        be16(tiff, 41); be32(tiff, 8); be16(tiff, 1)
        be16(tiff, 0x0112); be16(tiff, 3); be32(tiff, 1)
        be16(tiff, 6); be16(tiff, 0)
        be32(tiff, 0)
        val payload = ByteArrayOutputStream()
        payload.write(byteArrayOf(0x45, 0x78, 0x69, 0x66, 0, 0))
        payload.write(tiff.toByteArray())
        val jpg = ByteArrayOutputStream()
        jpg.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        jpg.write(byteArrayOf(0xFF.toByte(), 0xE1.toByte()))
        be16(jpg, payload.size() + 2)
        jpg.write(payload.toByteArray())
        assertEquals(1, read(jpg.toByteArray()))
    }

    @Test
    fun `ifd offsets below the TIFF header are rejected`() {
        // offset 0 points back into the byte-order/magic header itself.
        for (ifdOffset in intArrayOf(0, 4)) {
            val tiff = ByteArrayOutputStream()
            tiff.write(byteArrayOf(0x4D, 0x4D))
            be16(tiff, 42); be32(tiff, ifdOffset); be16(tiff, 1)
            be16(tiff, 0x0112); be16(tiff, 3); be32(tiff, 1)
            be16(tiff, 6); be16(tiff, 0)
            be32(tiff, 0)
            val payload = ByteArrayOutputStream()
            payload.write(byteArrayOf(0x45, 0x78, 0x69, 0x66, 0, 0))
            payload.write(tiff.toByteArray())
            val jpg = ByteArrayOutputStream()
            jpg.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
            jpg.write(byteArrayOf(0xFF.toByte(), 0xE1.toByte()))
            be16(jpg, payload.size() + 2)
            jpg.write(payload.toByteArray())
            assertEquals(1, read(jpg.toByteArray()), "ifdOffset=$ifdOffset")
        }
    }

    @Test
    fun `streams that return zero-length reads still parse`() {
        val bytes = jpeg(6, littleEndian = false)
        val stalling = object : java.io.InputStream() {
            private val inner = ByteArrayInputStream(bytes)
            private var stall = false
            override fun read(): Int = inner.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                // Alternate legal 0-length returns with tiny reads.
                if (stall) {
                    stall = false
                    return 0
                }
                stall = true
                return inner.read(b, off, 1)
            }
        }
        assertEquals(6, JpegExifOrientation.read(stalling))
    }
}
