package com.dot.gallery.core.decoder.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class HeifBitstreamProbeTest {

    @Test
    fun av1cFlagsMapToBitDepths() {
        assertEquals(8, HeifBitstreamProbe.maxBitDepth(avifFile(av1c = av1c(0x24, 0x00))))
        assertEquals(10, HeifBitstreamProbe.maxBitDepth(avifFile(av1c = av1c(0x24, 0x40))))
        assertEquals(12, HeifBitstreamProbe.maxBitDepth(avifFile(av1c = av1c(0x44, 0x60))))
    }

    @Test
    fun pixiBitsAreUsedWhenAv1cIsAbsent() {
        val file = avifFile(av1c = null, pixi = pixi(12, 12, 12))
        assertEquals(12, HeifBitstreamProbe.maxBitDepth(file))
    }

    @Test
    fun highestDeclaredDepthWinsAcrossItems() {
        // An 8-bit thumbnail/alpha item must not mask a 12-bit primary item.
        val file = avifFile(av1c = av1c(0x24, 0x00), pixi = pixi(12, 12, 12))
        assertEquals(12, HeifBitstreamProbe.maxBitDepth(file))
    }

    @Test
    fun metaAfterMdatIsStillFound() {
        val file = concat(
            ftyp(),
            box("mdat", ByteArray(64) { 0x55 }),
            metaBox(av1c(0x44, 0x60), pixi(12, 12, 12)),
        )
        assertEquals(12, HeifBitstreamProbe.maxBitDepth(file))
    }

    @Test
    fun filesWithoutDeclarationsReturnZero() {
        assertEquals(0, HeifBitstreamProbe.maxBitDepth(ByteArray(0)))
        assertEquals(0, HeifBitstreamProbe.maxBitDepth(ftyp()))
        assertEquals(0, HeifBitstreamProbe.maxBitDepth(ByteArray(4096) { 0x7E }))
        // A 'meta' box with no iprp/ipco properties.
        assertEquals(0, HeifBitstreamProbe.maxBitDepth(concat(ftyp(), metaBox())))
    }

    @Test
    fun malformedBoxesDoNotCrashOrConfabulate() {
        val valid = avifFile(av1c = av1c(0x44, 0x60), pixi = pixi(12, 12, 12))
        // Every truncation must still return a bound — never throw.
        for (cut in 0 until valid.size) {
            HeifBitstreamProbe.maxBitDepth(valid.copyOf(cut))
        }
        // Corrupt the meta box size to run past the end of file.
        val corrupt = valid.copyOf()
        corrupt[0] = 0x7F
        corrupt[1] = 0x7F
        corrupt[2] = 0x7F
        corrupt[3] = 0x7F
        assertEquals(0, HeifBitstreamProbe.maxBitDepth(corrupt))
    }

    @Test
    fun needsSoftwareDecodeOnlyAboveTenBit() {
        assertFalse(HeifBitstreamProbe.needsSoftwareDecode(avifFile(av1c = av1c(0x24, 0x00))))
        assertFalse(HeifBitstreamProbe.needsSoftwareDecode(avifFile(av1c = av1c(0x24, 0x40))))
        assertTrue(HeifBitstreamProbe.needsSoftwareDecode(avifFile(av1c = av1c(0x44, 0x60))))
        assertFalse(HeifBitstreamProbe.needsSoftwareDecode(ByteArray(0)))
    }

    // --- Minimal ISO-BMFF fixture builders -------------------------------------------------

    /** 'ftyp' box declaring the avif brand. */
    private fun ftyp(): ByteArray = box("ftyp", "avif".toByteArray() + byteArrayOf(0, 0, 0, 0))

    /**
     * 'av1C' record bytes: 0x81 marker/version, then [profileByte] (seq_profile<<5 | level),
     * then [flagsByte] (tier | high_bitdepth | twelve_bit | mono | subx | suby | position).
     */
    private fun av1c(profileByte: Int, flagsByte: Int) = box(
        "av1C",
        byteArrayOf(0x81.toByte(), profileByte.toByte(), flagsByte.toByte(), 0),
    )

    /** 'pixi' FullBox: version/flags(4) + num_channels(1) + bits_per_channel[n]. */
    private fun pixi(vararg bits: Int) = box(
        "pixi",
        byteArrayOf(0, 0, 0, 0, bits.size.toByte()) + bits.map { it.toByte() }.toByteArray(),
    )

    /** 'meta' FullBox wrapping iprp→ipco with the given property boxes. */
    private fun metaBox(vararg properties: ByteArray): ByteArray {
        val ipco = box("ipco", concat(*properties))
        val iprp = box("iprp", ipco)
        return box("meta", byteArrayOf(0, 0, 0, 0) + iprp)
    }

    /** A plausible AVIF file: ftyp + meta(properties) + mdat. */
    private fun avifFile(av1c: ByteArray? = null, pixi: ByteArray? = null): ByteArray {
        val props = listOfNotNull(box("ispe", ByteArray(12)), av1c, pixi).toTypedArray()
        return concat(ftyp(), metaBox(*props), box("mdat", ByteArray(32) { 0x11 }))
    }

    private fun box(type: String, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(u32(8 + content.size))
        out.write(type.toByteArray(Charsets.US_ASCII))
        out.write(content)
        return out.toByteArray()
    }

    private fun u32(v: Int) = byteArrayOf(
        (v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte(),
    )

    private fun concat(vararg parts: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        parts.forEach { out.write(it) }
        return out.toByteArray()
    }
}
