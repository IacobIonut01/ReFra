package com.dot.gallery.core.metadata

import com.dot.gallery.core.metadata.MetadataCategory.LOCATION
import com.dot.gallery.core.metadata.MetadataCategory.PROVENANCE
import java.security.MessageDigest

/**
 * JPEG 2000 (JP2) and JPEG XL containers: top-level Exif, XML and UUID boxes, JUMBF provenance
 * and GeoJP2 location. The codestream boxes are copied unchanged; a bare JPEG XL codestream has
 * no container and therefore no removable metadata.
 */
internal object BoxMetadata {
    private val XMP_UUID = uuid("BE7ACFCB97A942E89C71999491E3AFAC")
    private val GEOJP2_UUID = uuid("B14BF8BD083D4B43A5AE8CD7D5A6CE03")

    private fun uuid(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private enum class Kind { EXIF, XMP, RECONSTRUCTION, OTHER }

    private class Box(val type: String, val start: Int, val dataStart: Int, val end: Int)

    private fun isBareCodestream(bytes: ByteArray) =
        bytes.size >= 2 && bytes.u8(0) == 0xFF && bytes.u8(1) == 0x0A

    private fun boxes(bytes: ByteArray): List<Box> {
        val boxes = mutableListOf<Box>()
        var at = 0
        while (at < bytes.size) {
            if (at + 8 > bytes.size) malformed("Box header overruns the file")
            val small = bytes.u32(at)
            var header = 8
            val size = when (small) {
                1L -> {
                    header = 16
                    if (at + 16 > bytes.size) malformed("Box header overruns the file")
                    bytes.u64(at + 8)
                }
                0L -> (bytes.size - at).toLong()
                else -> small
            }
            if (size < header || size > bytes.size - at) malformed("Box overruns the file")
            boxes += Box(bytes.ascii(at + 4, 4), at, at + header, at + size.toInt())
            at += size.toInt()
        }
        if (boxes.isEmpty()) malformed("Container has no boxes")
        return boxes
    }

    private fun data(bytes: ByteArray, box: Box) = bytes.copyOfRange(box.dataStart, box.end)

    private fun kind(bytes: ByteArray, box: Box): Kind = when {
        box.type == "Exif" -> Kind.EXIF
        box.type == "xml " -> Kind.XMP
        box.type == "uuid" && box.end - box.dataStart >= 16 &&
            bytes.copyOfRange(box.dataStart, box.dataStart + 16).contentEquals(XMP_UUID) -> Kind.XMP
        box.type == "jbrd" -> Kind.RECONSTRUCTION
        else -> Kind.OTHER
    }

    private fun classify(bytes: ByteArray, box: Box): MetadataClass = when (box.type) {
        "jumb" -> MetadataClass.of(PROVENANCE)
        "asoc" -> MetadataClass.of(LOCATION)
        // Brotli-compressed boxes cannot be inspected without a decoder, so they go whole.
        "brob" -> MetadataClass.Opaque
        "uuid" -> if (box.end - box.dataStart >= 16 &&
            bytes.copyOfRange(box.dataStart, box.dataStart + 16).contentEquals(GEOJP2_UUID)
        ) MetadataClass.of(LOCATION) else MetadataClass.Other
        "uinf" -> MetadataClass.Other
        else -> MetadataClass.Keep
    }

    private fun xmpPacket(bytes: ByteArray, box: Box): ByteArray =
        if (box.type == "uuid") bytes.copyOfRange(box.dataStart + 16, box.end) else data(bytes, box)

    private fun exifTiff(bytes: ByteArray, box: Box): Pair<ByteArray, ByteArray> {
        val data = data(bytes, box)
        if (data.size < 4) malformed("Exif box is truncated")
        val start = 4 + data.u32(0)
        if (start >= data.size) malformed("Exif box offset is invalid")
        return data.copyOfRange(0, start.toInt()) to data.copyOfRange(start.toInt(), data.size)
    }

    fun inspect(bytes: ByteArray, inventory: MetadataInventory) {
        if (isBareCodestream(bytes)) return
        boxes(bytes).forEach { box ->
            when (kind(bytes, box)) {
                Kind.EXIF -> TiffMetadata.inspect(exifTiff(bytes, box).second, inventory)
                Kind.XMP -> XmpMetadata.inspect(xmpPacket(bytes, box), inventory)
                Kind.RECONSTRUCTION -> Unit
                Kind.OTHER -> inventory.record(classify(bytes, box))
            }
        }
    }

    fun scrub(bytes: ByteArray, selection: MetadataSelection): ByteArray {
        if (isBareCodestream(bytes) || selection.isEmpty) return bytes
        val out = ByteSink(bytes.size)
        var reconstructionStale = false
        val pending = mutableListOf<Pair<Box, ByteArray?>>()
        boxes(bytes).forEach { box ->
            when (kind(bytes, box)) {
                Kind.EXIF -> {
                    val (prefix, tiff) = exifTiff(bytes, box)
                    val result = TiffMetadata.scrub(tiff, selection)
                    if (result.changed) {
                        reconstructionStale = true
                        pending += box to (prefix + result.bytes.copyOf(result.usedLength))
                    } else {
                        pending += box to null
                    }
                }
                Kind.XMP -> {
                    val original = xmpPacket(bytes, box)
                    val packet = XmpMetadata.scrub(original, selection)
                    when {
                        packet == null -> reconstructionStale = true
                        packet === original -> pending += box to null
                        else -> {
                            reconstructionStale = true
                            val prefix = if (box.type == "uuid") XMP_UUID else ByteArray(0)
                            pending += box to (prefix + packet)
                        }
                    }
                }
                Kind.RECONSTRUCTION, Kind.OTHER -> {
                    if (!selection.removes(classify(bytes, box))) pending += box to null
                }
            }
        }
        pending.forEach { (box, replacement) ->
            when {
                // JPEG reconstruction data embeds the original Exif and XMP; it is invalid once they change.
                kind(bytes, box) == Kind.RECONSTRUCTION && reconstructionStale -> Unit
                replacement == null -> out.write(bytes, box.start, box.end - box.start)
                else -> {
                    out.writeU32((replacement.size + 8).toLong())
                    out.ascii(box.type)
                    out.write(replacement)
                }
            }
        }
        return out.toByteArray()
    }

    fun essence(bytes: ByteArray, digest: MessageDigest) {
        if (isBareCodestream(bytes)) {
            digest.update(bytes)
            return
        }
        boxes(bytes).forEach { box ->
            if (kind(bytes, box) == Kind.OTHER && classify(bytes, box) == MetadataClass.Keep) {
                digest.update(box.type.toByteArray(Charsets.ISO_8859_1))
                digest.update(bytes, box.dataStart, box.end - box.dataStart)
            }
        }
    }
}
