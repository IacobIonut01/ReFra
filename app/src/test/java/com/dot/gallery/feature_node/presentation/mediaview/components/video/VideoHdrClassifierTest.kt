package com.dot.gallery.feature_node.presentation.mediaview.components.video

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoHdrClassifierTest {

    @Test
    fun dolbyVisionMimeIsClassified() {
        val info = classifyVideoHdr("video/dolby-vision", "dvh1.08.09", null)
        assertEquals(VideoHdrType.DOLBY_VISION, info.type)
        assertTrue(info.isHdr)
    }

    @Test
    fun dolbyVisionCodecStringsAreClassified() {
        for (codecs in listOf("dvhe.05.06", "dvh1.08.09", "dvav.09.16", "dva1.08.09", "dav1.10.09")) {
            val info = classifyVideoHdr("video/hevc", codecs, null)
            assertEquals("codecs=$codecs", VideoHdrType.DOLBY_VISION, info.type)
        }
    }

    @Test
    fun dolbyVisionProfileAndLevelAreParsed() {
        val info = classifyVideoHdr("video/dolby-vision", "dvh1.08.09", null)
        assertEquals(8, info.dolbyVisionProfile)
        assertEquals(9, info.dolbyVisionLevel)
        assertEquals("dvh1.08.09", info.codecs)
    }

    @Test
    fun profileFiveIsParsed() {
        val info = classifyVideoHdr("video/dolby-vision", "dvhe.05.06", null)
        assertEquals(5, info.dolbyVisionProfile)
        assertEquals(6, info.dolbyVisionLevel)
        assertFalse(info.hasCompatibleDolbyVisionBaseLayer())
    }

    @Test
    fun st2084IsHdr10() {
        val info = classifyVideoHdr("video/hevc", "hvc1.2.4.H150.b0", C.COLOR_TRANSFER_ST2084)
        assertEquals(VideoHdrType.HDR10, info.type)
        assertTrue(info.isHdr)
    }

    @Test
    fun hlgIsClassified() {
        val info = classifyVideoHdr("video/hevc", "hvc1.2.4.H150.b0", C.COLOR_TRANSFER_HLG)
        assertEquals(VideoHdrType.HLG, info.type)
        assertTrue(info.isHdr)
    }

    @Test
    fun sdrTracksAreNone() {
        assertEquals(
            VideoHdrInfo.NONE,
            classifyVideoHdr("video/avc", "avc1.640028", C.COLOR_TRANSFER_SDR)
        )
        assertEquals(
            VideoHdrInfo.NONE,
            classifyVideoHdr("video/hevc", "hvc1.2.4.L120.90", null)
        )
        assertEquals(VideoHdrInfo.NONE, classifyVideoHdr("video/avc", null, null))
        assertFalse(classifyVideoHdr("video/avc", null, null).isHdr)
    }

    @Test
    fun dolbyVisionWinsOverBaseLayerColorTransfer() {
        // A DV stream riding on an HEVC-compatible base layer still reports DV first.
        val info = classifyVideoHdr("video/hevc", "dvh1.08.09", C.COLOR_TRANSFER_ST2084)
        assertEquals(VideoHdrType.DOLBY_VISION, info.type)
    }

    @Test
    fun compatibleProfilesUseBaseLayerWithoutDecoder() {
        val info = classifyVideoHdr("video/dolby-vision", "dvh1.08.09", null)
        assertTrue(info.hasCompatibleDolbyVisionBaseLayer())
        assertEquals(
            DolbyVisionPlayback.BASE_LAYER,
            dolbyVisionPlayback(info, hasDolbyVisionDecoder = false, hasDolbyVisionDisplay = false)
        )
    }

    @Test
    fun profileFiveWithoutDecoderIsUnsupported() {
        val info = classifyVideoHdr("video/dolby-vision", "dvhe.05.06", null)
        assertEquals(
            DolbyVisionPlayback.UNSUPPORTED,
            dolbyVisionPlayback(info, hasDolbyVisionDecoder = false, hasDolbyVisionDisplay = true)
        )
    }

    @Test
    fun nativePlaybackNeedsDecoderAndDisplay() {
        val info = classifyVideoHdr("video/dolby-vision", "dvh1.08.09", null)
        assertEquals(
            DolbyVisionPlayback.NATIVE,
            dolbyVisionPlayback(info, hasDolbyVisionDecoder = true, hasDolbyVisionDisplay = true)
        )
        assertEquals(
            DolbyVisionPlayback.DECODE_ONLY,
            dolbyVisionPlayback(info, hasDolbyVisionDecoder = true, hasDolbyVisionDisplay = false)
        )
    }

    @Test
    fun nonDolbyTracksHaveNoPlaybackVerdict() {
        val info = classifyVideoHdr("video/hevc", "hvc1", C.COLOR_TRANSFER_ST2084)
        assertNull(
            dolbyVisionPlayback(info, hasDolbyVisionDecoder = true, hasDolbyVisionDisplay = true)
        )
    }
}
