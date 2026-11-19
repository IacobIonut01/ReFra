package com.dot.gallery.editor.ui

import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.SrgbPixels
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The blurred backdrop's thumbnail pixel maths: it keeps the image's
 * average colour.
 */
class EditorSliderTrackTest {

    private fun pixels(width: Int, height: Int, argb: (Int, Int) -> Int): SrgbPixels {
        val data = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                data[y * width + x] = argb(x, y)
            }
        }
        return SrgbPixels(PixelSize(width, height), data)
    }

    private fun channel(pixels: SrgbPixels, x: Int, y: Int, shift: Int): Int =
        (pixels.argbAt(x, y) ushr shift) and 0xFF

    private fun argb(r: Int, g: Int, b: Int) =
        (-0x1000000) or (r shl 16) or (g shl 8) or b

    @Test
    fun backdropThumbnailDownsamplesAndKeepsTheAverage() {
        val source = pixels(100, 50) { _, _ -> argb(100, 150, 200) }
        val thumb = backdropThumbnail(source)

        assertEquals(24, thumb.size.width)
        assertEquals(12, thumb.size.height)
        for (y in listOf(0, 5, 11)) {
            for (x in listOf(0, 11, 23)) {
                assertTrue(abs(channel(thumb, x, y, 16) - 100) <= 2)
                assertTrue(abs(channel(thumb, x, y, 8) - 150) <= 2)
                assertTrue(abs(channel(thumb, x, y, 0) - 200) <= 2)
            }
        }
    }
}
