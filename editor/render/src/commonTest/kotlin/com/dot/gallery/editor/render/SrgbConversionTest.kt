package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.model.PixelSize
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SrgbConversionTest {

    private fun frameOf(values: FloatArray, width: Int, height: Int) =
        LinearRgbaFrame(PixelSize(width, height), values)

    @Test
    fun `decode follows the sRGB transfer curve at both segment boundaries`() = runTest {
        // 10/255 = 0.0392 sits below the 0.04045 pivot (linear segment);
        // 11/255 = 0.0431 sits above it (power segment).
        val pixels = intArrayOf(
            (255 shl 24) or (10 shl 16) or (10 shl 8) or 10,
            (255 shl 24) or (11 shl 16) or (11 shl 8) or 11
        )
        val frame = SrgbPixels(PixelSize(2, 1), pixels).toLinearFrame()
        val low = frame.component(0)
        val high = frame.component(4)
        assertEquals((10f / 255f) / 12.92f, low, 1e-7f)
        val expectedHigh = (((11.0 / 255.0) + 0.055) / 1.055).pow(2.4).toFloat()
        assertEquals(expectedHigh, high, 1e-6f)
        assertEquals(1f, frame.component(3))
    }

    @Test
    fun `encode pivots at 0_0031308 and rounds to the nearest step`() = runTest {
        val frame = frameOf(
            floatArrayOf(
                0.0031308f, 0.0031f, 0.004f, 1f
            ), 1, 1
        )
        val pixel = frame.toSrgbPixels().copyPixels().single()
        // Linear segment: 12.92 * 0.0031308 = 0.0404 -> step 10.
        assertEquals(10, (pixel shr 16) and 0xFF)
        assertEquals(10, (pixel shr 8) and 0xFF)
        // Above the pivot the power segment applies (~0.0508 -> step 13).
        assertEquals(13, pixel and 0xFF)
    }

    @Test
    fun `mid grey encodes near the familiar 188`() = runTest {
        val frame = frameOf(floatArrayOf(0.5f, 0.5f, 0.5f, 1f), 1, 1)
        val pixel = frame.toSrgbPixels().copyPixels().single()
        assertEquals(188, (pixel shr 16) and 0xFF)
        assertEquals(188, (pixel shr 8) and 0xFF)
        assertEquals(188, pixel and 0xFF)
        assertEquals(255, (pixel shr 24) and 0xFF)
    }

    @Test
    fun `every neutral 8-bit channel survives a round trip`() = runTest {
        val pixels = IntArray(256) { v -> (255 shl 24) or (v shl 16) or (v shl 8) or v }
        val roundTripped = SrgbPixels(PixelSize(256, 1), pixels)
            .toLinearFrame()
            .toSrgbPixels()
            .copyPixels()
        assertContentEquals(pixels, roundTripped)
    }

    @Test
    fun `alpha is preserved and transparent RGB is untouched`() = runTest {
        val pixel = (0 shl 24) or (200 shl 16) or (30 shl 8) or 90
        val half = (128 shl 24) or (255 shl 16)
        val result = SrgbPixels(PixelSize(2, 1), intArrayOf(pixel, half))
            .toLinearFrame()
        assertEquals(0f, result.component(3))
        val roundTripped = result.toSrgbPixels().copyPixels()
        assertEquals(pixel, roundTripped[0])
        assertEquals(half, roundTripped[1])
    }

    @Test
    fun `out-of-range scene colour clamps only at the display boundary`() = runTest {
        // Alpha is contractually inside [0, 1] in a LinearRgbaFrame; scene
        // colour may exceed it and clamps only here, on encode.
        val frame = frameOf(floatArrayOf(4f, -0.25f, 1.0000001f, 1f), 1, 1)
        val pixel = frame.toSrgbPixels().copyPixels().single()
        assertEquals(255, (pixel shr 16) and 0xFF)
        assertEquals(0, (pixel shr 8) and 0xFF)
        assertEquals(255, pixel and 0xFF)
        assertEquals(255, (pixel shr 24) and 0xFF)
    }

    @Test
    fun `SrgbPixels copies input and returns defensive copies`() {
        val backing = intArrayOf(1, 2, 3, 4)
        val pixels = SrgbPixels(PixelSize(2, 2), backing)
        backing[0] = 99
        assertEquals(1, pixels.copyPixels()[0])
        val copy = pixels.copyPixels()
        copy[0] = 55
        assertEquals(1, pixels.copyPixels()[0])
        assertNotSame(backing, pixels.copyPixels())
        assertFailsWith<IllegalArgumentException> {
            SrgbPixels(PixelSize(2, 2), IntArray(3))
        }
    }

    @Test
    fun `conversion is finite for extreme legitimate input`() = runTest {
        val frame = frameOf(
            floatArrayOf(Float.MIN_VALUE, -Float.MIN_VALUE, 1e-10f, 0.999f), 1, 1
        )
        val pixel = frame.toSrgbPixels().copyPixels().single()
        assertTrue(abs(((pixel shr 16) and 0xFF)) <= 255)
    }
}
