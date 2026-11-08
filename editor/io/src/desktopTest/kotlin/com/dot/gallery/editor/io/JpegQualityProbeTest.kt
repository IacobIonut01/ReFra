package com.dot.gallery.editor.io

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JpegQualityProbeTest {

    /** A JPEG carrying exactly one 8-bit luminance DQT scaled for [quality]. */
    private fun jpegWithQuality(quality: Int): ByteArray {
        // IJG scaling: q < 50 -> 5000/q, q >= 50 -> 200 - 2q (percent).
        val scale = if (quality < 50) 5000 / quality else 200 - 2 * quality
        val table = IntArray(64) { index ->
            val value = (STD_TABLE[index] * scale + 50) / 100
            value.coerceIn(1, 255)
        }
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte())) // SOI
        out.write(byteArrayOf(0xFF.toByte(), 0xDB.toByte())) // DQT
        out.write(0)
        out.write(2 + 1 + 64) // segment length incl. itself
        out.write(0x00) // precision 0, table id 0
        table.forEach { out.write(it) }
        out.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte())) // EOI
        return out.toByteArray()
    }

    @Test
    fun estimatesTheIjgQualityScaleFromTheLuminanceTable() {
        // Integer table scaling leaves a rounding tolerance of about one point.
        assertEquals(25, JpegQualityProbe.estimate(jpegWithQuality(25)))
        assertEquals(50, JpegQualityProbe.estimate(jpegWithQuality(50)))
        for (quality in listOf(75, 90)) {
            val estimate = JpegQualityProbe.estimate(jpegWithQuality(quality))
            assertTrue(
                estimate != null && kotlin.math.abs(estimate - quality) <= 2,
                "expected ~$quality, got $estimate"
            )
        }
    }

    @Test
    fun estimatesARealEncodedJpegNearItsRequestedQuality() {
        val image = BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 64) for (x in 0 until 64) {
            image.setRGB(x, y, (x * 4 shl 16) or (y * 4 shl 8) or ((x + y) * 2))
        }
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val bytes = ByteArrayOutputStream().also { out ->
            ImageIO.createImageOutputStream(out).use { stream ->
                writer.output = stream
                val param = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = 0.9f
                }
                writer.write(null, IIOImage(image, null, null), param)
            }
        }
        writer.dispose()
        val estimate = JpegQualityProbe.estimate(bytes.toByteArray())
        assertTrue(
            estimate != null && estimate in 70..100,
            "expected a high quality estimate, got $estimate"
        )
    }

    @Test
    fun rejectsNonJpegAndTruncatedHeaders() {
        assertNull(JpegQualityProbe.estimate(ByteArray(0)))
        assertNull(JpegQualityProbe.estimate("not a jpeg at all".encodeToByteArray()))
        // A JPEG cut off before its DQT segment cannot be estimated.
        assertNull(JpegQualityProbe.estimate(byteArrayOf(0xFF.toByte(), 0xD8.toByte())))
        assertNull(
            JpegQualityProbe.estimate(
                byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xDB.toByte(), 0, 67)
            )
        )
    }

    private companion object {
        /** Standard IJG luminance table (quality 50 baseline). */
        val STD_TABLE = intArrayOf(
            16, 11, 10, 16, 24, 40, 51, 61,
            12, 12, 14, 19, 26, 58, 60, 55,
            14, 13, 16, 24, 40, 57, 69, 56,
            14, 17, 22, 29, 51, 87, 80, 62,
            18, 22, 37, 56, 68, 109, 103, 77,
            24, 35, 55, 64, 81, 104, 113, 92,
            49, 64, 78, 87, 103, 121, 120, 101,
            72, 92, 95, 98, 112, 100, 103, 99
        )
    }
}
