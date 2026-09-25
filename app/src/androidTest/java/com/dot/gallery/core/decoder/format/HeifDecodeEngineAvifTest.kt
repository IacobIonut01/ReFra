package com.dot.gallery.core.decoder.format

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test for 12-bit AVIF support (#1244): the platform AV1 stack silently decodes
 * 12-bit streams as 8-bit and returns the corrupted bitmap as a success, so the decode engine
 * must route such files to the bundled software decoder instead of trusting the hardware path.
 *
 * [test12.avif] is a real 12-bit AVIF (YUV444, 640x480) encoded by avifenc/libaom.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class HeifDecodeEngineAvifTest {

    private fun asset(name: String): ByteArray =
        InstrumentationRegistry.getInstrumentation().context.assets.open(name)
            .use { it.readBytes() }

    @Test
    fun probeReportsTwelveBit() {
        val bytes = asset("test12.avif")
        assertEquals(12, HeifBitstreamProbe.maxBitDepth(bytes))
        assertTrue(HeifBitstreamProbe.needsSoftwareDecode(bytes))
    }

    @Test
    fun twelveBitAvifDecodesThroughSoftwarePath() {
        val bytes = asset("test12.avif")
        val decoded = HeifDecodeEngine.decode(bytes, 0, 0)
        val software = HeifDecodeEngine.decodeSoftware(bytes, 0, 0)
        assertNotNull(decoded)
        assertNotNull(software)
        // The engine must have bypassed the corrupt platform decoder: its output is exactly the
        // software decoder's output, not the platform's 8-bit-misdecoded variant.
        assertTrue(decoded!!.sameAs(software!!))
    }
}
