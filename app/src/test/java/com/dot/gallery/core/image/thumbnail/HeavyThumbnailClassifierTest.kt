/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.image.thumbnail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeavyThumbnailClassifierTest {

    // ---------- MIME table ----------

    @Test
    fun jxlMimesClassifyAsJxl() {
        assertEquals(HeavyThumbFormat.JXL, HeavyThumbnailClassifier.classifyMime("image/jxl"))
        assertEquals(HeavyThumbFormat.JXL, HeavyThumbnailClassifier.classifyMime("image/x-jxl"))
        assertEquals(HeavyThumbFormat.JXL, HeavyThumbnailClassifier.classifyMime("IMAGE/JXL"))
    }

    @Test
    fun psdMimesClassifyAsPsd() {
        assertEquals(
            HeavyThumbFormat.PSD,
            HeavyThumbnailClassifier.classifyMime("image/vnd.adobe.photoshop")
        )
        assertEquals(HeavyThumbFormat.PSD, HeavyThumbnailClassifier.classifyMime("image/x-photoshop"))
        assertEquals(HeavyThumbFormat.PSD, HeavyThumbnailClassifier.classifyMime("image/psd"))
    }

    @Test
    fun jp2MimesClassifyAsJp2() {
        assertEquals(HeavyThumbFormat.JP2, HeavyThumbnailClassifier.classifyMime("image/jp2"))
        assertEquals(HeavyThumbFormat.JP2, HeavyThumbnailClassifier.classifyMime("image/j2k"))
        assertEquals(HeavyThumbFormat.JP2, HeavyThumbnailClassifier.classifyMime("image/jpeg2000"))
    }

    @Test
    fun tiffMimesClassifyAsTiff() {
        assertEquals(HeavyThumbFormat.TIFF, HeavyThumbnailClassifier.classifyMime("image/tiff"))
        assertEquals(HeavyThumbFormat.TIFF, HeavyThumbnailClassifier.classifyMime("image/tif"))
        assertEquals(HeavyThumbFormat.TIFF, HeavyThumbnailClassifier.classifyMime("image/x-tiff"))
    }

    @Test
    fun cameraRawMimesClassifyAsRaw() {
        assertEquals(HeavyThumbFormat.RAW, HeavyThumbnailClassifier.classifyMime("image/x-nikon-nef"))
        assertEquals(HeavyThumbFormat.RAW, HeavyThumbnailClassifier.classifyMime("image/vnd.canon-cr2"))
        assertEquals(HeavyThumbFormat.RAW, HeavyThumbnailClassifier.classifyMime("image/x-adobe-dng"))
    }

    @Test
    fun rawPrefixRuleDoesNotStealJxlPsdOrTiff() {
        // image/x-* would otherwise match RawMime's prefix rule — explicit entries win.
        assertEquals(HeavyThumbFormat.JXL, HeavyThumbnailClassifier.classifyMime("image/x-jxl"))
        assertEquals(HeavyThumbFormat.PSD, HeavyThumbnailClassifier.classifyMime("image/x-photoshop"))
        assertEquals(HeavyThumbFormat.TIFF, HeavyThumbnailClassifier.classifyMime("image/x-tiff"))
    }

    @Test
    fun heifMimesAreCandidatesNotVerdicts() {
        // Bit depth decides software vs platform — the MIME alone must return null.
        assertNull(HeavyThumbnailClassifier.classifyMime("image/heic"))
        assertNull(HeavyThumbnailClassifier.classifyMime("image/heif"))
        assertNull(HeavyThumbnailClassifier.classifyMime("image/avif"))
    }

    @Test
    fun nativelyDecodableAndBlankMimesClassifyNull() {
        assertNull(HeavyThumbnailClassifier.classifyMime("image/jpeg"))
        assertNull(HeavyThumbnailClassifier.classifyMime("image/png"))
        assertNull(HeavyThumbnailClassifier.classifyMime("image/webp"))
        assertNull(HeavyThumbnailClassifier.classifyMime("video/mp4"))
        assertNull(HeavyThumbnailClassifier.classifyMime(null))
        assertNull(HeavyThumbnailClassifier.classifyMime(""))
    }

    @Test
    fun mimeParametersAndCaseAreNormalized() {
        assertEquals(
            HeavyThumbFormat.JXL,
            HeavyThumbnailClassifier.classifyMime("Image/JXL; charset=binary")
        )
    }

    @Test
    fun everyHeavyMimeKindIsPlatformUnsupported() {
        // The MIME-level bypass used before probing deeper — these must classify non-null so
        // MediaStoreThumbnailModelLoader never attempts the doomed platform thumbnail call.
        for (mime in listOf(
            "image/jxl", "image/x-jxl", "image/vnd.adobe.photoshop", "image/x-photoshop",
            "image/jp2", "image/j2k", "image/tiff", "image/tif", "image/x-nikon-nef",
            "image/vnd.canon-cr2"
        )) {
            assertTrue("expected heavy: $mime", HeavyThumbnailClassifier.classifyMime(mime) != null)
        }
        assertNull(HeavyThumbnailClassifier.classifyMime("image/jpeg"))
        assertNull(HeavyThumbnailClassifier.classifyMime("image/heic"))
        assertNull(HeavyThumbnailClassifier.classifyMime(null))
    }

    // ---------- magic-byte sniff ----------

    @Test
    fun sniffJxlRawCodestream() {
        val head = byteArrayOf(0xFF.toByte(), 0x0A, 0x01, 0x02)
        assertEquals(HeavyThumbFormat.JXL, HeavyThumbnailClassifier.sniffKind(head, head.size))
    }

    @Test
    fun sniffJxlContainerSignatureBox() {
        val head = byteArrayOf(
            0x00, 0x00, 0x00, 0x0C, 0x4A, 0x58, 0x4C, 0x20, 0x0D, 0x0A, 0x87.toByte(), 0x0A
        )
        assertEquals(HeavyThumbFormat.JXL, HeavyThumbnailClassifier.sniffKind(head, head.size))
    }

    @Test
    fun sniffPsdSignature() {
        val head = "8BPS\u0000\u0001".toByteArray(Charsets.US_ASCII)
        assertEquals(HeavyThumbFormat.PSD, HeavyThumbnailClassifier.sniffKind(head, head.size))
    }

    @Test
    fun sniffJp2SignatureBox() {
        val head = byteArrayOf(
            0x00, 0x00, 0x00, 0x0C, 0x6A, 0x50, 0x20, 0x20, 0x0D, 0x0A, 0x87.toByte(), 0x0A
        )
        assertEquals(HeavyThumbFormat.JP2, HeavyThumbnailClassifier.sniffKind(head, head.size))
    }

    @Test
    fun sniffJ2kCodestream() {
        val head = byteArrayOf(0xFF.toByte(), 0x4F, 0xFF.toByte(), 0x51)
        assertEquals(HeavyThumbFormat.JP2, HeavyThumbnailClassifier.sniffKind(head, head.size))
    }

    @Test
    fun sniffTiffMagicBecomesRawCandidate() {
        val le = byteArrayOf(0x49, 0x49, 0x2A, 0x00)
        val be = byteArrayOf(0x4D, 0x4D, 0x00, 0x2A)
        assertEquals(HeavyThumbFormat.RAW, HeavyThumbnailClassifier.sniffKind(le, le.size))
        assertEquals(HeavyThumbFormat.RAW, HeavyThumbnailClassifier.sniffKind(be, be.size))
    }

    @Test
    fun sniffHeifBrandIsCandidate() {
        // size(24) 'ftyp' 'heic' — the brand proves HEIF-family; depth is probed by the caller.
        val heic = byteArrayOf(0, 0, 0, 0x18) +
                "ftyp".toByteArray(Charsets.US_ASCII) +
                "heic".toByteArray(Charsets.US_ASCII) +
                byteArrayOf(0, 0, 0, 0)
        assertEquals(HeavyThumbFormat.HEIF_SW, HeavyThumbnailClassifier.sniffKind(heic, heic.size))
    }

    @Test
    fun sniffJpegAndPngAreNotHeavy() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
        assertNull(HeavyThumbnailClassifier.sniffKind(jpeg, jpeg.size))
        assertNull(HeavyThumbnailClassifier.sniffKind(png, png.size))
    }

    @Test
    fun sniffTooShortOrEmptyReturnsNull() {
        assertNull(HeavyThumbnailClassifier.sniffKind(byteArrayOf(1, 2), 2))
        assertNull(HeavyThumbnailClassifier.sniffKind(ByteArray(0), 0))
        assertNull(HeavyThumbnailClassifier.sniffKind(byteArrayOf(1, 2, 3, 4, 5, 6), 6))
    }
}
