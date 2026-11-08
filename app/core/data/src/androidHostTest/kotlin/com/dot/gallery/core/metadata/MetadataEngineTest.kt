package com.dot.gallery.core.metadata

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.util.zip.CRC32

class MetadataEngineTest {
    @get:Rule
    val folder = TemporaryFolder()

    private class Cleaned(val bytes: ByteArray, val inventory: MetadataInventory, val verification: String?)

    private fun clean(format: MediaContainerFormat, input: ByteArray, categories: Set<MetadataCategory>): Cleaned {
        val source = folder.newFile().apply { writeBytes(input) }
        val target = folder.newFile()
        val selection = MetadataSelection(categories)
        MetadataEngine.scrub(format, source, target, selection)
        val inventory = FileInputStream(target).use { MetadataEngine.inspect(format, it.channel) }
        return Cleaned(target.readBytes(), inventory, MetadataEngine.verify(format, source, target, selection))
    }

    private fun inspect(format: MediaContainerFormat, input: ByteArray): MetadataInventory {
        val file = folder.newFile().apply { writeBytes(input) }
        return FileInputStream(file).use { MetadataEngine.inspect(format, it.channel) }
    }

    private fun ByteArray.contains(text: String) = indexOf(text.toByteArray(Charsets.ISO_8859_1)) >= 0

    @Test
    fun jpegLocationRemovalKeepsOrientationAndDevice() {
        val input = jpeg(exif = tiff())
        assertTrue(MetadataCategory.LOCATION in inspect(MediaContainerFormat.JPEG, input).present)

        val result = clean(MediaContainerFormat.JPEG, input, MetadataPolicy.LOCATION)

        assertNull(result.verification)
        assertFalse(MetadataCategory.LOCATION in result.inventory.present)
        assertTrue(MetadataCategory.IDENTITY_DEVICE in result.inventory.present)
        assertTrue(result.bytes.contains("ReFraCam"))
        assertTrue(result.inventory.functional.any { "274" in it || "rientation" in it })
    }

    @Test
    fun jpegRemoveAllKeepsOrientationAndImageData() {
        val input = jpeg(exif = tiff())

        val result = clean(MediaContainerFormat.JPEG, input, MetadataPolicy.ALL)

        assertNull(result.verification)
        assertTrue(result.inventory.present.none { it in MetadataPolicy.ALL })
        assertFalse(result.bytes.contains("ReFraCam"))
        assertTrue(result.bytes.contains(String(SCAN, Charsets.ISO_8859_1)))
        assertTrue(result.inventory.functional.any { "274" in it || "rientation" in it })
    }

    @Test
    fun motionPhotoTrailerSurvivesRemoveAll() {
        val trailer = mp4(udta = ByteArray(0))
        val xmp = """<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">""" +
            """<rdf:Description xmlns:GCamera="http://ns.google.com/photos/1.0/camera/" GCamera:MotionPhoto="1" """ +
            """GCamera:MotionPhotoVersion="1" GCamera:MotionPhotoPresentationTimestampUs="0" """ +
            """xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:creator><rdf:Seq><rdf:li>Synthetic Author</rdf:li></rdf:Seq></dc:creator>""" +
            """</rdf:Description></rdf:RDF></x:xmpmeta>"""
        val input = jpeg(exif = tiff(), xmp = xmp) + trailer

        val result = clean(MediaContainerFormat.JPEG, input, MetadataPolicy.ALL)

        assertNull(result.verification)
        assertArrayEquals(trailer, result.bytes.copyOfRange(result.bytes.size - trailer.size, result.bytes.size))
        assertTrue(result.bytes.contains("MotionPhoto"))
        assertFalse(result.bytes.contains("Synthetic Author"))
    }

    @Test
    fun pngRemoveAllDropsTextAndKeepsPixels() {
        val idat = chunk("IDAT", byteArrayOf(0x78, 0x9C.toByte(), 0x63, 0x60, 0, 0, 0, 2, 0, 1))
        val input = PNG_SIGNATURE + chunk("IHDR", IHDR) + chunk("tEXt", "Author\u0000Synthetic Author".toByteArray()) +
            chunk("eXIf", tiff()) + idat + chunk("IEND", ByteArray(0))

        val result = clean(MediaContainerFormat.PNG, input, MetadataPolicy.ALL)

        assertNull(result.verification)
        assertFalse(result.bytes.contains("Synthetic Author"))
        assertFalse(result.bytes.contains("ReFraCam"))
        assertTrue(result.bytes.indexOf(idat) >= 0)
    }

    @Test
    fun webpRemoveAllKeepsFeatureFlagsInStep() {
        val vp8x = ByteArray(10).also { it[0] = 0x0C }
        val image = chunk4("VP8L", byteArrayOf(0x2F, 0, 0, 0, 0))
        val xmp = """<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">""" +
            """<rdf:Description xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:creator><rdf:Seq><rdf:li>Synthetic Author</rdf:li>""" +
            """</rdf:Seq></dc:creator></rdf:Description></rdf:RDF></x:xmpmeta>"""
        val body = "WEBP".toByteArray() + chunk4("VP8X", vp8x) + image + chunk4("EXIF", tiff()) + chunk4("XMP ", xmp.toByteArray())
        val input = "RIFF".toByteArray() + le32(body.size) + body

        val result = clean(MediaContainerFormat.WEBP, input, MetadataPolicy.ALL)

        assertNull(result.verification)
        assertFalse(result.bytes.contains("Synthetic Author"))
        assertFalse(result.bytes.contains("ReFraCam"))
        // Orientation keeps a trimmed EXIF chunk; the emptied XMP chunk is dropped with its flag.
        assertEquals(0x08, result.bytes[20].toInt() and 0x0C)
        assertEquals(result.bytes.size - 8, le32Read(result.bytes, 4))
        assertTrue(result.bytes.indexOf(image) >= 0)
    }

    @Test
    fun mp4LocationRemovalKeepsLengthAndMediaData() {
        val location = "+44.4268+026.1025/".toByteArray()
        val xyz = box("©xyz", be16(location.size) + be16(0x15C7) + location)
        val input = mp4(udta = xyz)

        assertTrue(MetadataCategory.LOCATION in inspect(MediaContainerFormat.MP4, input).present)
        val result = clean(MediaContainerFormat.MP4, input, MetadataPolicy.LOCATION)

        assertNull(result.verification)
        assertEquals(input.size, result.bytes.size)
        assertFalse(MetadataCategory.LOCATION in result.inventory.present)
        assertFalse(result.bytes.contains("+44.4268"))
        assertTrue(result.bytes.indexOf(MDAT) >= 0)
    }

    @Test
    fun ultraHdrKeepsGainMapLinkageAfterRemoveAll() {
        val gainMap = jpeg(exif = null)
        val xmp = """<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">""" +
            """<rdf:Description xmlns:hdrgm="http://ns.adobe.com/hdr-gain-map/1.0/" hdrgm:Version="1.0" """ +
            """xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:creator><rdf:Seq><rdf:li>Synthetic Author</rdf:li></rdf:Seq></dc:creator>""" +
            """</rdf:Description></rdf:RDF></x:xmpmeta>"""
        val input = ultraHdr(xmp, gainMap)

        val result = clean(MediaContainerFormat.JPEG, input, MetadataPolicy.ALL)

        assertNull(result.verification)
        assertTrue(result.bytes.size < input.size)
        assertTrue(result.bytes.contains("hdrgm"))
        assertFalse(result.bytes.contains("Synthetic Author"))
        val base = result.bytes.indexOf("MPF\u0000".toByteArray()) + 4
        val secondOffset = be32Read(result.bytes, base + MPF_ENTRIES + 16 + 8)
        val gainMapAt = base + secondOffset
        assertArrayEquals(gainMap, result.bytes.copyOfRange(gainMapAt, gainMapAt + gainMap.size))
        assertEquals(gainMapAt, be32Read(result.bytes, base + MPF_ENTRIES + 4))
    }

    @Test
    fun telemetryTrackBlocksLocationRemoval() {
        val stsd = box("stsd", be32(0) + be32(1) + box("gpmd", ByteArray(8)))
        val trak = box("trak", box("mdia", box("minf", box("stbl", stsd))))
        val input = box("ftyp", "isom".toByteArray() + be32(0) + "isom".toByteArray()) +
            box("moov", box("mvhd", ByteArray(100)) + trak) + MDAT
        val file = folder.newFile().apply { writeBytes(input) }

        val capability = FileInputStream(file).use { MetadataEngine.capability(MediaContainerFormat.MP4, it.channel) }

        assertFalse(MetadataCategory.LOCATION in capability.removableCategories)
        assertFalse(capability.supports(MetadataPolicy.LOCATION))
        assertTrue(capability.limitation != null)
    }

    @Test
    fun unsupportedFormatsReportALimitation() {
        assertNull(MetadataEngine.limitation(MediaContainerFormat.JPEG))
        assertNull(MetadataEngine.limitation(MediaContainerFormat.MP4))
        assertTrue(MetadataEngine.limitation(MediaContainerFormat.TIFF) != null)
    }

    private companion object {
        val SCAN = "SYNTHETICSCANDATA".toByteArray()
        val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val IHDR = be32(1) + be32(1) + byteArrayOf(8, 0, 0, 0, 0)
        val MDAT = box("mdat", "SYNTHETICMEDIA".toByteArray())

        fun be16(v: Int) = byteArrayOf((v shr 8).toByte(), v.toByte())
        fun be32(v: Int) = byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
        fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
        fun be32Read(b: ByteArray, at: Int) =
            ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or
                ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)

        /** Offset of the MP entry array from the MPF TIFF header: header, one-entry IFD, next-IFD link. */
        const val MPF_ENTRIES = 8 + 2 + 12 + 4

        fun le32Read(b: ByteArray, at: Int) =
            (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or
                ((b[at + 2].toInt() and 0xFF) shl 16) or ((b[at + 3].toInt() and 0xFF) shl 24)

        fun box(type: String, data: ByteArray) = be32(data.size + 8) + type.toByteArray(Charsets.ISO_8859_1) + data

        fun chunk(type: String, data: ByteArray): ByteArray {
            val crc = CRC32().apply { update(type.toByteArray()); update(data) }.value.toInt()
            return be32(data.size) + type.toByteArray() + data + be32(crc)
        }

        fun chunk4(type: String, data: ByteArray): ByteArray {
            val padded = if (data.size % 2 == 1) data + 0 else data
            return type.toByteArray() + le32(data.size) + padded
        }

        fun segment(marker: Int, payload: ByteArray) =
            byteArrayOf(0xFF.toByte(), marker.toByte()) + be16(payload.size + 2) + payload

        /** Big-endian TIFF: Make, Orientation = 6 and a GPS IFD with a latitude. */
        fun tiff(): ByteArray {
            val make = "ReFraCam\u0000".toByteArray()
            val ifd0 = 8
            val ifd0Size = 2 + 3 * 12 + 4
            val makeAt = ifd0 + ifd0Size
            val gpsAt = makeAt + make.size + 1
            val gpsSize = 2 + 2 * 12 + 4
            val latAt = gpsAt + gpsSize
            val out = ByteArrayOutputStream()
            fun entry(tag: Int, type: Int, count: Int, value: ByteArray) {
                out.write(be16(tag)); out.write(be16(type)); out.write(be32(count)); out.write(value)
            }
            out.write("MM".toByteArray()); out.write(be16(42)); out.write(be32(ifd0))
            out.write(be16(3))
            entry(0x010F, 2, make.size, be32(makeAt))
            entry(0x0112, 3, 1, be16(6) + be16(0))
            entry(0x8825, 4, 1, be32(gpsAt))
            out.write(be32(0))
            out.write(make); out.write(0)
            out.write(be16(2))
            entry(0x0001, 2, 2, byteArrayOf('N'.code.toByte(), 0, 0, 0))
            entry(0x0002, 5, 3, be32(latAt))
            out.write(be32(0))
            listOf(44, 25, 36).forEach { out.write(be32(it)); out.write(be32(1)) }
            return out.toByteArray()
        }

        fun mpf(primarySize: Int, secondSize: Int, secondOffset: Int): ByteArray {
            val tiff = "MM".toByteArray() + be16(42) + be32(8) + be16(1) +
                be16(0xB002) + be16(7) + be32(32) + be32(MPF_ENTRIES) + be32(0) +
                be32(0x030000) + be32(primarySize) + be32(0) + be32(0) +
                be32(0) + be32(secondSize) + be32(secondOffset) + be32(0)
            return segment(0xE2, "MPF\u0000".toByteArray() + tiff)
        }

        /** Primary JPEG carrying Exif, XMP and an MPF index, followed by the gain map image. */
        fun ultraHdr(xmp: String, gainMap: ByteArray): ByteArray {
            val primary = jpeg(exif = tiff(), xmp = xmp, mpf = mpf(0, 0, 0))
            val mpfAt = primary.indexOf("MPF\u0000".toByteArray())
            val base = mpfAt + 4
            val fixed = mpf(primary.size, gainMap.size, primary.size - base)
            fixed.copyInto(primary, mpfAt - 4)
            return primary + gainMap
        }

        fun jpeg(exif: ByteArray?, xmp: String? = null, mpf: ByteArray? = null): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
            if (exif != null) out.write(segment(0xE1, "Exif\u0000\u0000".toByteArray() + exif))
            if (mpf != null) out.write(mpf)
            if (xmp != null) out.write(segment(0xE1, "http://ns.adobe.com/xap/1.0/\u0000".toByteArray() + xmp.toByteArray()))
            out.write(segment(0xDB, byteArrayOf(0) + ByteArray(64) { 1 }))
            out.write(segment(0xC0, byteArrayOf(8, 0, 1, 0, 1, 1, 1, 0x11, 0)))
            out.write(segment(0xDA, byteArrayOf(1, 1, 0, 0, 0x3F, 0)))
            out.write(SCAN)
            out.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
            return out.toByteArray()
        }

        fun mp4(udta: ByteArray): ByteArray {
            val mvhd = box("mvhd", ByteArray(100).also { it[15] = 0x03; it[14] = 0xE8.toByte() })
            val moov = box("moov", mvhd + if (udta.isEmpty()) ByteArray(0) else box("udta", udta))
            return box("ftyp", "isom".toByteArray() + be32(0) + "isom".toByteArray()) + moov + MDAT
        }
    }
}
