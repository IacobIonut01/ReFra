package com.dot.gallery.editor.core.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WhiteBalanceModelTest {

    private fun applied(wb: PhotoWhiteBalance, rgb: FloatArray): FloatArray {
        val g = WhiteBalanceModel.gains(wb)
        return floatArrayOf(rgb[0] * g[0], rgb[1] * g[1], rgb[2] * g[2])
    }

    /** Largest channel over smallest — 1 is perfectly neutral. */
    private fun spread(rgb: FloatArray): Float =
        max(rgb[0], max(rgb[1], rgb[2])) /
            minOf(rgb[0], rgb[1], rgb[2])

    @Test
    fun `neutral recipe has unit gains`() {
        val g = WhiteBalanceModel.gains(PhotoWhiteBalance())
        assertTrue(g.all { it == 1f })
    }

    @Test
    fun `grey patch solves to the neutral recipe`() {
        val wb = assertNotNull(WhiteBalanceModel.neutralFor(0.18f, 0.18f, 0.18f))
        assertTrue(wb.isNeutral, "$wb")
    }

    @Test
    fun `picking a known cast recovers its inverse and neutralises the patch`() {
        val casts = listOf(
            PhotoWhiteBalance(9000f, 0f),
            PhotoWhiteBalance(3200f, 0f),
            PhotoWhiteBalance(5000f, 30f),
            PhotoWhiteBalance(7800f, -45f),
            PhotoWhiteBalance(4200f, 12f)
        )
        for (cast in casts) {
            for (level in listOf(0.05f, 0.18f, 0.6f)) {
                val grey = floatArrayOf(level, level, level)
                // The photo was shot under the cast: grey reads tinted.
                val tinted = applied(cast, grey)
                val pick = assertNotNull(
                    WhiteBalanceModel.neutralFor(tinted[0], tinted[1], tinted[2])
                )
                val corrected = applied(pick, tinted)
                assertTrue(
                    spread(corrected) < 1.002f,
                    "cast $cast at $level → $pick leaves spread ${spread(corrected)}"
                )
                // Composing the cast and its pick is near-neutral: the
                // remaining gains are within rounding of identity.
                val residual = applied(pick, applied(cast, floatArrayOf(1f, 1f, 1f)))
                val mean = (residual[0] + residual[1] + residual[2]) / 3f
                for (c in residual) {
                    assertTrue(abs(c / mean - 1f) < 0.002f, "$cast residual ${residual.toList()}")
                }
            }
        }
    }

    @Test
    fun `symmetric temperature casts invert to mirrored recipes`() {
        val warm = applied(PhotoWhiteBalance(12000f, 0f), floatArrayOf(0.2f, 0.2f, 0.2f))
        val pick = assertNotNull(WhiteBalanceModel.neutralFor(warm[0], warm[1], warm[2]))
        assertEquals(2000f, pick.temperatureKelvin)
        assertEquals(0f, pick.tint)
    }

    @Test
    fun `extreme casts clamp to the authored ranges`() {
        val blue = assertNotNull(WhiteBalanceModel.neutralFor(0.01f, 0.2f, 0.9f))
        assertEquals(12000f, blue.temperatureKelvin)
        val green = assertNotNull(WhiteBalanceModel.neutralFor(0.05f, 0.9f, 0.05f))
        assertEquals(100f, green.tint)
        val magenta = assertNotNull(WhiteBalanceModel.neutralFor(0.9f, 0.01f, 0.9f))
        assertEquals(-100f, magenta.tint)
        assertTrue(magenta.temperatureKelvin in 2000f..12000f)
    }

    @Test
    fun `dark or invalid patches are rejected`() {
        assertNull(WhiteBalanceModel.neutralFor(0f, 0.2f, 0.2f))
        assertNull(WhiteBalanceModel.neutralFor(0.2f, -0.1f, 0.2f))
        assertNull(WhiteBalanceModel.neutralFor(0.2f, 0.2f, Float.NaN))
        assertNull(WhiteBalanceModel.neutralFor(0.2f, Float.POSITIVE_INFINITY, 0.2f))
    }

    @Test
    fun `out-of-range scene values above one still solve`() {
        val pick = assertNotNull(WhiteBalanceModel.neutralFor(2.4f, 2.0f, 1.6f))
        val corrected = applied(pick, floatArrayOf(2.4f, 2.0f, 1.6f))
        assertTrue(spread(corrected) < 1.002f)
    }
}
