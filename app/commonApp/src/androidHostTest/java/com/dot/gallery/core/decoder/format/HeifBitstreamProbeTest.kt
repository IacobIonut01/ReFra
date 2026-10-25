package com.dot.gallery.core.decoder.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream




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

    @Test
    fun readMetaBoxSkipsMdatInsteadOfReadingIt() {
        // SD-card regression: the probe used to read a fixed 256 KB head per file. Only box
        // headers and the meta payload may be read; mdat must be skipped.
        val meta = metaBox(av1c(0x44, 0x60), pixi(12, 12, 12))
        val file = concat(ftyp(), box("mdat", ByteArray(200_000) { 0x55 }), meta)
        val stream = CountingStream(ByteArrayInputStream(file))

        val box = HeifBitstreamProbe.readMetaBox(stream)

        assertTrue(box!!.contentEquals(meta))
        assertEquals(12, HeifBitstreamProbe.maxBitDepth(box))
        assertTrue("read ${stream.bytesRead} bytes", stream.bytesRead < 1024)
    }

    @Test
    fun readMetaBoxStopsAtMetaForTypicalLayout() {
        val file = avifFile(av1c = av1c(0x44, 0x60)) + ByteArray(300_000)
        val stream = CountingStream(ByteArrayInputStream(file))

        assertTrue(HeifBitstreamProbe.needsSoftwareDecode(HeifBitstreamProbe.readMetaBox(stream)!!))
        assertTrue("read ${stream.bytesRead} bytes", stream.bytesRead < 1024)
    }

    @Test
    fun readMetaBoxMatchesFixedPrefixProbe() {
        val limit = HeifBitstreamProbe.HEADER_PROBE_LIMIT
        val meta = metaBox(av1c(0x44, 0x60), pixi(12, 12, 12))
        val cases = listOf(
            avifFile(av1c = av1c(0x44, 0x60)),
            avifFile(av1c = av1c(0x24, 0x40)),
            concat(ftyp(), box("mdat", ByteArray(64)), meta),
            // meta beyond the head window: unknown in both implementations.
            concat(ftyp(), box("mdat", ByteArray(limit)), meta),
            // meta straddling the head window.
            concat(ftyp(), box("mdat", ByteArray(limit - 40)), meta),
            // size==0 meta runs to end of file.
            concat(ftyp(), u32(0) + "meta".toByteArray() + meta.copyOfRange(8, meta.size)),
            // size==0 non-meta box swallows the rest of the file.
            concat(ftyp(), u32(0) + "mdat".toByteArray() + ByteArray(16), meta),
            // 64-bit size on the first box.
            concat(u32(1) + "ftyp".toByteArray() + ByteArray(4) + u32(24) + ByteArray(8), meta),
            // size smaller than the box header.
            concat(ftyp(), u32(4) + "free".toByteArray(), meta),
            ByteArray(0),
            ByteArray(4096) { 0x7E },
        )
        for ((index, file) in cases.withIndex()) {
            val expected = HeifBitstreamProbe.maxBitDepth(file.copyOf(minOf(file.size, limit)))
            val actual = HeifBitstreamProbe.readMetaBox(ByteArrayInputStream(file))
                ?.let(HeifBitstreamProbe::maxBitDepth) ?: 0
            assertEquals("case $index", expected, actual)
        }
    }

    @Test
    fun readMetaBoxToleratesStreamsThatRefuseToSkip() {
        val meta = metaBox(pixi(12, 12, 12))
        val file = concat(ftyp(), box("mdat", ByteArray(5_000)), meta)
        val noSkip = object : FilterInputStream(ByteArrayInputStream(file)) {
            override fun skip(n: Long): Long = 0
        }
        assertTrue(HeifBitstreamProbe.readMetaBox(noSkip)!!.contentEquals(meta))
    }

    @Test
    fun readMetaBoxReturnsNullForTruncatedStreams() {
        val valid = avifFile(av1c = av1c(0x44, 0x60))
        val metaEnd = ftyp().size + metaBox(box("ispe", ByteArray(12)), av1c(0x44, 0x60)).size
        for (cut in 0 until metaEnd) {
            assertNull("cut $cut", HeifBitstreamProbe.readMetaBox(ByteArrayInputStream(valid.copyOf(cut))))
        }
    }

    /** Counts bytes actually read (not skipped) from the wrapped stream. */
    private class CountingStream(source: InputStream) : FilterInputStream(source) {
        var bytesRead = 0L
            private set

        override fun read(): Int = super.read().also { if (it >= 0) bytesRead++ }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            super.read(b, off, len).also { if (it > 0) bytesRead += it }
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
