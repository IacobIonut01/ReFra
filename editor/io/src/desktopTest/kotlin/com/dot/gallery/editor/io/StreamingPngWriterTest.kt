package com.dot.gallery.editor.io

import com.dot.gallery.editor.core.model.PixelSize
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StreamingPngWriterTest {

    private fun encode(size: PixelSize, pixels: IntArray): ByteArray {
        val out = ByteArrayOutputStream()
        StreamingPngWriter(out, size).use { writer ->
            for (y in 0 until size.height) {
                writer.writeRow(pixels, y * size.width)
            }
            writer.finish()
        }
        return out.toByteArray()
    }

    private fun decode(bytes: ByteArray) = ImageIO.read(ByteArrayInputStream(bytes))

    private fun argbPixels(size: PixelSize): IntArray =
        IntArray(size.pixelCount.toInt()) {
            (0xA0 + it % 0x50 shl 24) or (it * 37 % 256 shl 16) or
                (it * 91 % 256 shl 8) or (it * 53 % 256)
        }

    @Test
    fun `produces a PNG ImageIO decodes to identical pixels`() {
        val size = PixelSize(3, 2)
        val pixels = argbPixels(size)
        val decoded = decode(encode(size, pixels))
        assertNotNull(decoded)
        assertEquals(3, decoded.width)
        assertEquals(2, decoded.height)
        val actual = IntArray(6)
        decoded.getRGB(0, 0, 3, 2, actual, 0, 3)
        assertContentEquals(pixels, actual)
    }

    @Test
    fun `alpha channel survives the round trip`() {
        val size = PixelSize(1, 1)
        val pixel = (0x33 shl 24) or (0xAB shl 16) or (0xCD shl 8) or 0xEF
        val decoded = decode(encode(size, intArrayOf(pixel)))
        assertNotNull(decoded)
        val out = IntArray(1)
        decoded.getRGB(0, 0, 1, 1, out, 0, 1)
        assertEquals(pixel, out[0])
    }

    @Test
    fun `incompressible data still yields multiple bounded IDAT chunks`() {
        val size = PixelSize(600, 300)
        val random = Random(4242)
        val pixels = IntArray(size.pixelCount.toInt()) { random.nextInt() }
        val bytes = encode(size, pixels)
        var idatCount = 0
        var i = 8
        while (i + 8 <= bytes.size) {
            val length = ((bytes[i].toInt() and 0xFF) shl 24) or
                ((bytes[i + 1].toInt() and 0xFF) shl 16) or
                ((bytes[i + 2].toInt() and 0xFF) shl 8) or
                (bytes[i + 3].toInt() and 0xFF)
            val type = String(bytes, i + 4, 4, Charsets.US_ASCII)
            if (type == "IDAT") {
                idatCount++
                assertTrue(length <= 65_536, "IDAT chunk exceeds bound: $length")
            }
            i += 12 + length
        }
        assertTrue(idatCount > 1, "expected multiple IDAT chunks, got $idatCount")
        val decoded = decode(bytes)
        assertNotNull(decoded)
        val actual = IntArray(size.pixelCount.toInt())
        decoded.getRGB(0, 0, size.width, size.height, actual, 0, size.width)
        assertContentEquals(pixels, actual)
    }

    @Test
    fun `finish rejects too few rows`() {
        val out = ByteArrayOutputStream()
        val writer = StreamingPngWriter(out, PixelSize(2, 2))
        writer.writeRow(IntArray(4), 0)
        assertFailsWith<IllegalStateException> { writer.finish() }
        writer.close()
    }

    @Test
    fun `extra rows and writes after finish or close are rejected`() {
        val out = ByteArrayOutputStream()
        val writer = StreamingPngWriter(out, PixelSize(2, 1))
        writer.writeRow(IntArray(2))
        writer.finish()
        assertFailsWith<IllegalStateException> { writer.writeRow(IntArray(2)) }
        assertFailsWith<IllegalStateException> { writer.finish() }
        writer.close()
        assertFailsWith<IllegalStateException> { writer.writeRow(IntArray(2)) }
        writer.close() // idempotent
    }

    @Test
    fun `invalid row arguments are rejected`() {
        val writer = StreamingPngWriter(ByteArrayOutputStream(), PixelSize(2, 1))
        assertFailsWith<IllegalArgumentException> { writer.writeRow(IntArray(2), -1) }
        assertFailsWith<IllegalArgumentException> { writer.writeRow(IntArray(2), 1) }
        assertFailsWith<IllegalArgumentException> { writer.writeRow(IntArray(1)) }
        writer.close()
    }

    @Test
    fun `close without finish leaves a truncated file with no IEND`() {
        val out = ByteArrayOutputStream()
        val writer = StreamingPngWriter(out, PixelSize(2, 3))
        writer.writeRow(IntArray(2))
        writer.close()
        val bytes = out.toByteArray()
        // No IEND trailer — a cancelled export is never silently "complete".
        // (ImageIO may still hand back a partially decoded image, so check
        // the chunk stream, not the decoder's tolerance.)
        var foundIend = false
        var i = 8
        while (i + 8 <= bytes.size) {
            val length = ((bytes[i].toInt() and 0xFF) shl 24) or
                ((bytes[i + 1].toInt() and 0xFF) shl 16) or
                ((bytes[i + 2].toInt() and 0xFF) shl 8) or
                (bytes[i + 3].toInt() and 0xFF)
            if (String(bytes, i + 4, 4, Charsets.US_ASCII) == "IEND") foundIend = true
            i += 12 + length
        }
        assertFalse(foundIend)
    }

    @Test
    fun `IO failures propagate and do not produce a finished file`() {
        var writes = 0
        val failing = object : OutputStream() {
            override fun write(b: Int) {
                writes++
                throw IOException("disk gone")
            }
        }
        assertFailsWith<IOException> {
            StreamingPngWriter(failing, PixelSize(1, 1))
        }
        assertTrue(writes > 0)
    }

    @Test
    fun `a failed finish is terminal`() {
        // Output dies only once the deflater flushes its tail at finish().
        var failWrites = false
        val partiallyFailing = object : OutputStream() {
            val bytes = ByteArrayOutputStream()
            override fun write(b: Int) {
                if (failWrites) throw IOException("disk gone")
                bytes.write(b)
            }
            override fun write(b: ByteArray, off: Int, len: Int) {
                if (failWrites) throw IOException("disk gone")
                bytes.write(b, off, len)
            }
        }
        val writer = StreamingPngWriter(partiallyFailing, PixelSize(64, 64))
        for (y in 0 until 64) writer.writeRow(IntArray(64), 0)
        failWrites = true
        assertFailsWith<IOException> { writer.finish() }
        // The deflater is gone: further writes must reject, not corrupt.
        assertFailsWith<IllegalStateException> { writer.writeRow(IntArray(64)) }
        assertFailsWith<IllegalStateException> { writer.finish() }
        writer.close()
        writer.close() // idempotent
    }
}
