package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.model.PixelSize
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame

class LinearRgbaFrameTest {

    @Test
    fun `constructor copies the input array`() {
        val src = floatArrayOf(0.1f, 0.2f, 0.3f, 1.0f)
        val frame = LinearRgbaFrame(PixelSize(1, 1), src)
        src[0] = 99f
        assertContentEquals(floatArrayOf(0.1f, 0.2f, 0.3f, 1.0f), frame.copyPixels())
    }

    @Test
    fun `copyPixels returns an independent copy`() {
        val frame = LinearRgbaFrame(PixelSize(1, 1), floatArrayOf(0.1f, 0.2f, 0.3f, 1.0f))
        val copy = frame.copyPixels()
        assertNotSame(copy, frame.copyPixels())
        copy[0] = 99f
        assertContentEquals(floatArrayOf(0.1f, 0.2f, 0.3f, 1.0f), frame.copyPixels())
    }

    @Test
    fun `out-of-range colour and zero alpha are legal`() {
        val frame = LinearRgbaFrame(
            PixelSize(2, 1),
            floatArrayOf(2.0f, -0.5f, 1e30f, 0.0f, -1f, 4f, 0f, 0.5f)
        )
        assertContentEquals(
            floatArrayOf(2.0f, -0.5f, 1e30f, 0.0f, -1f, 4f, 0f, 0.5f),
            frame.copyPixels()
        )
    }

    @Test
    fun `wrong component count is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            LinearRgbaFrame(PixelSize(2, 2), FloatArray(15))
        }
        assertFailsWith<IllegalArgumentException> {
            LinearRgbaFrame(PixelSize(2, 2), FloatArray(17))
        }
    }

    @Test
    fun `non-finite components and bad alpha are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            LinearRgbaFrame(PixelSize(1, 1), floatArrayOf(Float.NaN, 0f, 0f, 1f))
        }
        assertFailsWith<IllegalArgumentException> {
            LinearRgbaFrame(PixelSize(1, 1), floatArrayOf(0f, Float.POSITIVE_INFINITY, 0f, 1f))
        }
        assertFailsWith<IllegalArgumentException> {
            LinearRgbaFrame(PixelSize(1, 1), floatArrayOf(0f, 0f, 0f, 1.5f))
        }
        assertFailsWith<IllegalArgumentException> {
            LinearRgbaFrame(PixelSize(1, 1), floatArrayOf(0f, 0f, 0f, -0.1f))
        }
        assertFailsWith<IllegalArgumentException> {
            LinearRgbaFrame(PixelSize(1, 1), floatArrayOf(0f, 0f, 0f, Float.NaN))
        }
    }

    @Test
    fun `frame too large for a FloatArray is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            LinearRgbaFrame(PixelSize(Int.MAX_VALUE, Int.MAX_VALUE), FloatArray(0))
        }
        // Int.MAX_VALUE pixels needs Int.MAX_VALUE * 4 components: the count
        // guard must reject before the multiplication.
        assertFailsWith<IllegalArgumentException> {
            LinearRgbaFrame(PixelSize(Int.MAX_VALUE, 1), FloatArray(0))
        }
    }

    @Test
    fun `fromOwned wraps without copying and still validates`() {
        val buffer = floatArrayOf(0.1f, 0.2f, 0.3f, 1.0f)
        val frame = LinearRgbaFrame.fromOwned(PixelSize(1, 1), buffer)
        // Ownership was transferred: mutating the array is visible.
        buffer[0] = 0.9f
        assertContentEquals(floatArrayOf(0.9f, 0.2f, 0.3f, 1.0f), frame.copyPixels())

        assertFailsWith<IllegalArgumentException> {
            LinearRgbaFrame.fromOwned(PixelSize(2, 2), FloatArray(15))
        }
        assertFailsWith<IllegalArgumentException> {
            LinearRgbaFrame.fromOwned(
                PixelSize(1, 1),
                floatArrayOf(0f, 0f, 0f, Float.NaN)
            )
        }
    }
}
