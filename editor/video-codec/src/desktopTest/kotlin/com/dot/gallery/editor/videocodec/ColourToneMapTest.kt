package com.dot.gallery.editor.videocodec

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.bytedeco.ffmpeg.global.avutil.AVCOL_PRI_BT2020
import org.bytedeco.ffmpeg.global.avutil.AVCOL_PRI_BT709
import org.bytedeco.ffmpeg.global.avutil.AVCOL_PRI_UNSPECIFIED
import org.bytedeco.ffmpeg.global.avutil.AVCOL_TRC_ARIB_STD_B67
import org.bytedeco.ffmpeg.global.avutil.AVCOL_TRC_BT709
import org.bytedeco.ffmpeg.global.avutil.AVCOL_TRC_SMPTE2084

class ColourToneMapTest {

    private fun map(toneMap: ColourToneMap, r: Float, g: Float, b: Float): Triple<Int, Int, Int> {
        fun sample(value: Float) = (value * 65535f).toInt().toShort()
        val out = IntArray(1)
        toneMap.apply(shortArrayOf(sample(r), sample(g), sample(b), -1), out)
        return Triple((out[0] shr 16) and 0xFF, (out[0] shr 8) and 0xFF, out[0] and 0xFF)
    }

    private val hlg = ColourToneMap.of(ColourToneMap.Transfer.Hlg, ColourToneMap.Gamut.Bt2020)
    private val pq = ColourToneMap.of(ColourToneMap.Transfer.Pq, ColourToneMap.Gamut.Bt2020)

    @Test
    fun onlyHdrOrWideGamutTagsNeedMapping() {
        assertNull(ColourToneMap.forTags(AVCOL_TRC_BT709, AVCOL_PRI_BT709))
        assertNull(ColourToneMap.forTags(AVCOL_TRC_BT709, AVCOL_PRI_UNSPECIFIED))
        assertNotNull(ColourToneMap.forTags(AVCOL_TRC_BT709, AVCOL_PRI_BT2020))
        assertNotNull(ColourToneMap.forTags(AVCOL_TRC_ARIB_STD_B67, AVCOL_PRI_UNSPECIFIED))
        assertNotNull(ColourToneMap.forTags(AVCOL_TRC_SMPTE2084, AVCOL_PRI_BT2020))
    }

    @Test
    fun blackStaysBlack() {
        assertEquals(Triple(0, 0, 0), map(hlg, 0f, 0f, 0f))
        assertEquals(Triple(0, 0, 0), map(pq, 0f, 0f, 0f))
    }

    @Test
    fun referenceWhiteLandsNearSdrWhiteAndStaysNeutral() {
        // BT.2408: 75 % HLG and 58 % PQ are both 203 nit reference white.
        listOf(map(hlg, 0.75f, 0.75f, 0.75f), map(pq, 0.5807f, 0.5807f, 0.5807f)).forEach { (r, g, b) ->
            assertTrue(r > 215, "reference white came out dark: $r")
            assertTrue(abs(r - g) <= 2 && abs(g - b) <= 2, "white tinted: ($r, $g, $b)")
        }
    }

    @Test
    fun brightnessRisesMonotonicallyAndNeverClipsBelowPeak() {
        listOf(hlg, pq).forEach { toneMap ->
            var previous = -1
            for (step in 0..20) {
                val signal = step / 20f
                val (r, _, _) = map(toneMap, signal, signal, signal)
                assertTrue(r >= previous, "fell from $previous to $r at $signal")
                previous = r
            }
        }
    }

    @Test
    fun aHighlightKeepsItsHueInsteadOfClippingToWhite() {
        val (r, g, b) = map(pq, 0.9f, 0.6f, 0.6f)
        assertTrue(r > g + 20 && r > b + 20, "highlight washed out: ($r, $g, $b)")
    }

    @Test
    fun wideGamutGreenStaysGreenWithoutNegativeChannels() {
        val (r, g, b) = map(
            ColourToneMap.of(ColourToneMap.Transfer.Gamma, ColourToneMap.Gamut.Bt2020),
            0f, 1f, 0f
        )
        assertEquals(0, r)
        assertEquals(0, b.coerceAtMost(0))
        assertTrue(g > 200)
    }
}
