package com.dot.gallery.core.metadata

import com.dot.gallery.core.metadata.MetadataCategory.DESCRIPTION_AUTHORSHIP
import com.dot.gallery.core.metadata.MetadataCategory.PROVENANCE
import com.dot.gallery.core.metadata.MetadataCategory.THUMBNAIL_PREVIEW
import java.security.MessageDigest

/**
 * JPEG, including multi-picture files: Ultra HDR gain maps (MPF), Motion Photos (an MP4 after
 * the primary image) and vendor trailers. The primary image's header segments are rebuilt and the
 * entropy-coded data is copied untouched. Anything after the primary image keeps its exact layout,
 * because XMP item lengths and MPF offsets point into it: embedded JPEGs and MP4s there are
 * scrubbed in place, and unrecognised trailer data is copied as is.
 */
internal object JpegMetadata {
    private val EXIF_HEADER = "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1)
    private val XMP_HEADER = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.ISO_8859_1)
    private val EXTENDED_XMP_HEADER = "http://ns.adobe.com/xmp/extension/\u0000".toByteArray(Charsets.ISO_8859_1)
    private val IRB_HEADER = "Photoshop 3.0\u0000".toByteArray(Charsets.ISO_8859_1)
    private const val MAX_PAYLOAD = 65533
    private const val EXTENDED_DATA_OFFSET = 75
    private const val MAX_EXTENDED_XMP = 64 * 1024 * 1024

    private enum class Kind { EXIF, XMP, EXTENDED_XMP, IRB, MPF, OTHER }

    /** A marker segment; [start] is its 0xFF byte and [end] is exclusive. */
    private class Segment(val marker: Int, val start: Int, val end: Int) {
        val payload: Int get() = start + 4
    }

    private class Layout(val segments: List<Segment>, val bodyStart: Int, val eoiEnd: Int)

    private sealed class Region(val start: Int, val end: Int) {
        class Jpeg(start: Int, val layout: Layout) : Region(start, layout.eoiEnd)
        class Movie(start: Int, end: Int) : Region(start, end)
        class Raw(start: Int, end: Int) : Region(start, end)
    }

    private fun parse(bytes: ByteArray, from: Int = 0, embedded: Boolean = false): Layout {
        if (bytes.u16(from) != 0xFFD8) malformed("Missing JPEG start of image")
        val segments = mutableListOf<Segment>()
        var offset = from + 2
        while (true) {
            if (bytes.u8(offset) != 0xFF) malformed("JPEG marker expected at $offset")
            var at = offset
            while (bytes.u8(at + 1) == 0xFF) at++
            val marker = bytes.u8(at + 1)
            if (marker == 0xD9) return Layout(segments, at, at + 2)
            if (marker == 0x01 || marker in 0xD0..0xD7) {
                offset = at + 2
                continue
            }
            val length = bytes.u16(at + 2)
            val end = at + 2 + length
            if (length < 2 || end > bytes.size) malformed("JPEG segment overruns the file")
            if (marker == 0xDA) {
                val eoi = findEoi(bytes, end)
                if (eoi < 0 && embedded) malformed("Embedded JPEG has no end of image")
                return Layout(segments, at, if (eoi < 0) bytes.size else eoi)
            }
            segments += Segment(marker, at, end)
            offset = end
        }
    }

    /** Walks entropy-coded scans (and the tables between progressive scans) to the end of image. */
    private fun findEoi(bytes: ByteArray, from: Int): Int {
        var i = from
        while (i + 1 < bytes.size) {
            if (bytes[i] != 0xFF.toByte()) {
                i++
                continue
            }
            val next = bytes[i + 1].toInt() and 0xFF
            when {
                next == 0xFF -> i++
                next == 0x00 || next in 0xD0..0xD7 -> i += 2
                next == 0xD9 -> return i + 2
                else -> {
                    if (i + 4 > bytes.size) return -1
                    val length = bytes.u16(i + 2)
                    if (length < 2) return -1
                    i += 2 + length
                }
            }
        }
        return -1
    }

    private fun kind(bytes: ByteArray, segment: Segment): Kind = when (segment.marker) {
        0xE1 -> when {
            bytes.startsWith(EXIF_HEADER, segment.payload, segment.end) -> Kind.EXIF
            bytes.startsWith(XMP_HEADER, segment.payload, segment.end) -> Kind.XMP
            bytes.startsWith(EXTENDED_XMP_HEADER, segment.payload, segment.end) &&
                segment.end - segment.payload >= EXTENDED_DATA_OFFSET -> Kind.EXTENDED_XMP
            else -> Kind.OTHER
        }
        0xED -> if (bytes.startsWith(IRB_HEADER, segment.payload, segment.end)) Kind.IRB else Kind.OTHER
        0xE2 -> if (bytes.startsWithAscii("MPF\u0000", segment.payload)) Kind.MPF else Kind.OTHER
        else -> Kind.OTHER
    }

    private fun classify(bytes: ByteArray, segment: Segment): MetadataClass = when (segment.marker) {
        0xE0 -> when {
            bytes.startsWithAscii("JFXX\u0000", segment.payload) -> MetadataClass.of(THUMBNAIL_PREVIEW)
            bytes.startsWithAscii("JFIF\u0000", segment.payload) -> MetadataClass.Keep
            else -> MetadataClass.Other
        }
        0xE2 -> if (bytes.startsWithAscii("ICC_PROFILE\u0000", segment.payload) ||
            bytes.startsWithAscii("MPF\u0000", segment.payload)
        ) MetadataClass.Keep else MetadataClass.Other
        0xEB -> MetadataClass.of(PROVENANCE)
        0xEE -> MetadataClass.Keep
        in 0xE1..0xEF -> MetadataClass.Other
        0xFE -> if (bytes.isBlankBytes(segment.payload, segment.end - segment.payload)) MetadataClass.Keep
        else MetadataClass.of(DESCRIPTION_AUTHORSHIP)
        else -> MetadataClass.Keep
    }

    private fun ByteArray.startsWith(prefix: ByteArray, offset: Int, end: Int): Boolean =
        offset + prefix.size <= end && prefix.indices.all { this[offset + it] == prefix[it] }

    private fun ByteArray.slice(from: Int, to: Int): ByteArray = copyOfRange(from, to)

    fun inspect(bytes: ByteArray, inventory: MetadataInventory) {
        val layout = parse(bytes)
        inspectImage(bytes, layout, inventory, embedded = false)
        inventory.functional += "jpeg.trailer=${bytes.size - layout.eoiEnd}"
        trailerRegions(bytes, layout.eoiEnd).forEach { region ->
            when (region) {
                is Region.Jpeg -> inspectImage(bytes, region.layout, inventory, embedded = true)
                is Region.Movie -> IsoBmffMetadata.inspect(window(bytes, region), inventory)
                is Region.Raw -> Unit
            }
        }
    }

    private fun inspectImage(bytes: ByteArray, layout: Layout, inventory: MetadataInventory, embedded: Boolean) {
        val extended = mutableListOf<Segment>()
        layout.segments.forEach { segment ->
            when (kind(bytes, segment)) {
                Kind.EXIF -> TiffMetadata.inspect(bytes.slice(segment.payload + EXIF_HEADER.size, segment.end), inventory)
                Kind.XMP -> XmpMetadata.inspect(bytes.slice(segment.payload + XMP_HEADER.size, segment.end), inventory)
                Kind.EXTENDED_XMP -> extended += segment
                Kind.IRB -> IptcMetadata.inspectIrb(bytes.slice(segment.payload + IRB_HEADER.size, segment.end), inventory)
                Kind.MPF -> inventory.functional += "jpeg.mpf.entries=${mpfEntries(bytes, segment)?.size ?: 0}"
                Kind.OTHER -> inventory.record(
                    classify(bytes, segment),
                    if (segment.marker == 0xFE) bytes.cleanText(segment.payload, segment.end - segment.payload) else null
                )
            }
        }
        if (extended.isNotEmpty()) {
            // An embedded image has no room to rewrite its extended packet, so it is removed whole.
            if (embedded) inventory.record(MetadataClass.Opaque)
            extendedPackets(bytes, extended).values.forEach { XmpMetadata.inspect(it, inventory) }
        }
    }

    fun scrub(bytes: ByteArray, selection: MetadataSelection): ByteArray {
        val layout = parse(bytes)
        val out = ByteSink(bytes.size)
        out.writeU16(0xFFD8)
        val extended = scrubExtended(bytes, layout.segments.filter { kind(bytes, it) == Kind.EXTENDED_XMP }, selection)
        var extendedWritten = false
        var mpf: Segment? = null
        var mpfAt = -1
        layout.segments.forEach { segment ->
            when (kind(bytes, segment)) {
                Kind.EXIF -> {
                    val result = TiffMetadata.scrub(bytes.slice(segment.payload + EXIF_HEADER.size, segment.end), selection)
                    if (result.changed) {
                        writeSegment(out, 0xE1, EXIF_HEADER, result.bytes, result.usedLength)
                    } else {
                        out.write(bytes, segment.start, segment.end - segment.start)
                    }
                }
                Kind.XMP -> {
                    val original = bytes.slice(segment.payload + XMP_HEADER.size, segment.end)
                    var packet = XmpMetadata.scrub(original, selection) ?: return@forEach
                    extended.guids.forEach { (old, new) -> packet = packet.replaceAscii(old, new) }
                    if (packet === original) {
                        out.write(bytes, segment.start, segment.end - segment.start)
                    } else {
                        writeSegment(out, 0xE1, XMP_HEADER, packet, packet.size)
                    }
                }
                Kind.EXTENDED_XMP -> if (!extendedWritten) {
                    extendedWritten = true
                    extended.chunks.forEach { writeSegment(out, 0xE1, EXTENDED_XMP_HEADER, it, it.size) }
                }
                Kind.IRB -> {
                    val original = bytes.slice(segment.payload + IRB_HEADER.size, segment.end)
                    val irb = IptcMetadata.scrubIrb(original, selection) ?: return@forEach
                    if (irb === original) {
                        out.write(bytes, segment.start, segment.end - segment.start)
                    } else {
                        writeSegment(out, 0xED, IRB_HEADER, irb, irb.size)
                    }
                }
                Kind.MPF -> {
                    mpf = segment
                    mpfAt = out.size
                    out.write(bytes, segment.start, segment.end - segment.start)
                }
                Kind.OTHER -> if (!selection.removes(classify(bytes, segment))) {
                    out.write(bytes, segment.start, segment.end - segment.start)
                }
            }
        }
        out.write(bytes, layout.bodyStart, layout.eoiEnd - layout.bodyStart)
        val newEoiEnd = out.size
        out.write(bytes, layout.eoiEnd, bytes.size - layout.eoiEnd)
        val result = out.toByteArray()
        mpf?.let { patchMpf(bytes, it, result, mpfAt, layout.eoiEnd, newEoiEnd) }
        trailerRegions(result, newEoiEnd).forEach { region ->
            when (region) {
                is Region.Jpeg -> scrubEmbedded(result, region.layout, selection)
                is Region.Movie -> IsoBmffMetadata.scrub(window(result, region), selection)
                is Region.Raw -> Unit
            }
        }
        return result
    }

    private fun writeSegment(out: ByteSink, marker: Int, header: ByteArray, data: ByteArray, length: Int) {
        val payload = header.size + length
        if (payload > MAX_PAYLOAD) malformed("Rewritten JPEG segment exceeds 64 KB")
        out.write(0xFF)
        out.write(marker)
        out.writeU16(payload + 2)
        out.write(header)
        out.write(data, 0, length)
    }

    private fun ByteArray.replaceAscii(old: String, new: String): ByteArray {
        val at = indexOf(old.toByteArray(Charsets.ISO_8859_1))
        if (at < 0) return this
        return copyOf().also { new.toByteArray(Charsets.ISO_8859_1).copyInto(it, at) }
    }

    /** Scrubs an image inside the trailer without changing its length. */
    private fun scrubEmbedded(bytes: ByteArray, layout: Layout, selection: MetadataSelection) {
        fun blank(segment: Segment) {
            bytes[segment.start + 1] = 0xFE.toByte()
            bytes.fill(0x20, segment.payload, segment.end)
        }
        layout.segments.forEach { segment ->
            when (kind(bytes, segment)) {
                Kind.EXIF -> {
                    val start = segment.payload + EXIF_HEADER.size
                    val result = TiffMetadata.scrub(bytes.slice(start, segment.end), selection)
                    if (result.changed) result.bytes.copyInto(bytes, start)
                }
                Kind.XMP -> {
                    val start = segment.payload + XMP_HEADER.size
                    val packet = XmpMetadata.scrub(bytes.slice(start, segment.end), selection, fixedLength = true)
                        ?: malformed("Fixed-length XMP scrub returned nothing")
                    packet.copyInto(bytes, start)
                }
                Kind.EXTENDED_XMP -> if (selection.removes(MetadataClass.Opaque)) blank(segment)
                Kind.IRB -> {
                    val original = bytes.slice(segment.payload + IRB_HEADER.size, segment.end)
                    if (IptcMetadata.scrubIrb(original, selection) !== original) blank(segment)
                }
                Kind.MPF -> Unit
                Kind.OTHER -> if (selection.removes(classify(bytes, segment))) blank(segment)
            }
        }
    }

    private fun mpfEntries(bytes: ByteArray, segment: Segment): List<Int>? {
        val tiff = segment.payload + 4
        val little = when (bytes.ascii(tiff, 2)) {
            "II" -> true
            "MM" -> false
            else -> return null
        }
        val ifd = tiff + bytes.u32(tiff + 4, little).toInt()
        if (ifd < tiff || ifd + 2 > segment.end) malformed("MPF index lies outside its segment")
        val count = bytes.u16(ifd, little)
        for (i in 0 until count) {
            val entry = ifd + 2 + 12 * i
            if (entry + 12 > segment.end) malformed("MPF index overruns its segment")
            if (bytes.u16(entry, little) != 0xB002) continue
            val total = bytes.u32(entry + 4, little)
            val data = tiff + bytes.u32(entry + 8, little)
            if (total % 16 != 0L || data < tiff || data + total > segment.end) malformed("MPF entries overrun their segment")
            return (0 until (total / 16).toInt()).map { data.toInt() + 16 * it }
        }
        return null
    }

    /**
     * MP entry offsets are relative to the MPF header, so they shift by however much the
     * distance between that header and the end of the primary image changed.
     */
    private fun patchMpf(source: ByteArray, segment: Segment, out: ByteArray, outAt: Int, oldEoiEnd: Int, newEoiEnd: Int) {
        val entries = mpfEntries(source, segment) ?: return
        val little = source.ascii(segment.payload + 4, 2) == "II"
        val oldBase = segment.payload + 4
        val newBase = outAt + 4 + 4
        val delta = (newEoiEnd - newBase).toLong() - (oldEoiEnd - oldBase).toLong()
        entries.forEachIndexed { index, entry ->
            val at = entry - segment.start + outAt
            if (index == 0) {
                out.putU32(at + 4, newEoiEnd.toLong(), little)
            } else {
                val offset = source.u32(entry + 8, little)
                if (offset != 0L) out.putU32(at + 8, offset + delta, little)
            }
        }
    }

    private class ExtendedResult(val chunks: List<ByteArray>, val guids: Map<String, String>)

    private fun extendedPackets(bytes: ByteArray, segments: List<Segment>): Map<String, ByteArray> =
        segments.groupBy { bytes.ascii(it.payload + EXTENDED_XMP_HEADER.size, 32) }.mapValues { (_, chunks) ->
            val total = bytes.u32(chunks.first().payload + 67)
            if (total > MAX_EXTENDED_XMP) malformed("Extended XMP is too large")
            val packet = ByteArray(total.toInt())
            chunks.forEach { chunk ->
                val offset = bytes.u32(chunk.payload + 71)
                val dataStart = chunk.payload + EXTENDED_DATA_OFFSET
                val length = chunk.end - dataStart
                if (offset + length > total) malformed("Extended XMP chunk overruns its packet")
                bytes.copyInto(packet, offset.toInt(), dataStart, chunk.end)
            }
            packet
        }

    /** Returns the extended-XMP chunk payloads (after the namespace header) and any GUID renames. */
    private fun scrubExtended(bytes: ByteArray, segments: List<Segment>, selection: MetadataSelection): ExtendedResult {
        if (segments.isEmpty()) return ExtendedResult(emptyList(), emptyMap())
        val chunks = mutableListOf<ByteArray>()
        val guids = linkedMapOf<String, String>()
        val packets = extendedPackets(bytes, segments)
        packets.forEach { (guid, packet) ->
            val scrubbed = XmpMetadata.scrub(packet, selection)
            when {
                scrubbed === packet -> segments.filter { bytes.ascii(it.payload + EXTENDED_XMP_HEADER.size, 32) == guid }
                    .forEach { chunks += bytes.slice(it.payload + EXTENDED_XMP_HEADER.size, it.end) }
                scrubbed == null -> Unit
                else -> {
                    val newGuid = MessageDigest.getInstance("MD5").digest(scrubbed)
                        .joinToString("") { "%02X".format(it) }
                    guids[guid] = newGuid
                    val step = MAX_PAYLOAD - EXTENDED_DATA_OFFSET
                    var offset = 0
                    while (offset < scrubbed.size) {
                        val length = minOf(step, scrubbed.size - offset)
                        val chunk = ByteSink(length + 40)
                        chunk.ascii(newGuid)
                        chunk.writeU32(scrubbed.size.toLong())
                        chunk.writeU32(offset.toLong())
                        chunk.write(scrubbed, offset, length)
                        chunks += chunk.toByteArray()
                        offset += length
                    }
                }
            }
        }
        return ExtendedResult(chunks, guids)
    }

    private fun window(bytes: ByteArray, region: Region): RandomBytes =
        OffsetRandomBytes(ArrayRandomBytes(bytes), region.start.toLong(), (region.end - region.start).toLong())

    private fun trailerRegions(bytes: ByteArray, from: Int): List<Region> {
        val regions = mutableListOf<Region>()
        var rawStart = -1
        var position = from
        while (position < bytes.size) {
            val region = jpegAt(bytes, position) ?: movieAt(bytes, position)
            if (region == null) {
                if (rawStart < 0) rawStart = position
                position = nextCandidate(bytes, position + 1)
                continue
            }
            if (rawStart >= 0) regions += Region.Raw(rawStart, position)
            rawStart = -1
            regions += region
            position = region.end
        }
        if (rawStart >= 0) regions += Region.Raw(rawStart, bytes.size)
        return regions
    }

    private fun nextCandidate(bytes: ByteArray, from: Int): Int {
        var i = from
        while (i < bytes.size) {
            if (bytes[i] == 0xFF.toByte() && i + 2 < bytes.size && bytes[i + 1] == 0xD8.toByte() && bytes[i + 2] == 0xFF.toByte()) return i
            if (bytes.startsWithAscii("ftyp", i + 4)) return i
            i++
        }
        return bytes.size
    }

    private fun jpegAt(bytes: ByteArray, position: Int): Region.Jpeg? {
        if (position + 3 > bytes.size || bytes[position] != 0xFF.toByte() || bytes[position + 1] != 0xD8.toByte() ||
            bytes[position + 2] != 0xFF.toByte()
        ) return null
        return try {
            Region.Jpeg(position, parse(bytes, position, embedded = true))
        } catch (e: MalformedMetadataException) {
            null
        }
    }

    /** An MP4 is recognised only when its top-level boxes chain cleanly and include a movie box. */
    private fun movieAt(bytes: ByteArray, position: Int): Region.Movie? {
        if (!bytes.startsWithAscii("ftyp", position + 4)) return null
        val ftypSize = bytes.u32(position)
        if (ftypSize !in 8L..1024L) return null
        var cursor = position.toLong()
        var sawMovie = false
        while (cursor + 8 <= bytes.size) {
            val at = cursor.toInt()
            val type = bytes.ascii(at + 4, 4)
            if (!type.all { it.code in 0x20..0x7E }) break
            val size = when (val small = bytes.u32(at)) {
                1L -> if (at + 16 <= bytes.size) bytes.u64(at + 8) else 0L
                0L -> bytes.size - cursor
                else -> small
            }
            if (size < 8 || cursor + size > bytes.size) break
            if (type == "moov") sawMovie = true
            cursor += size
        }
        return if (sawMovie) Region.Movie(position, cursor.toInt()) else null
    }

    /** Hashes everything removal must leave untouched: image data, structural segments and the trailer. */
    fun essence(bytes: ByteArray, digest: MessageDigest) {
        val layout = parse(bytes)
        imageEssence(bytes, layout, digest)
        digest.updateLong((bytes.size - layout.eoiEnd).toLong())
        trailerRegions(bytes, layout.eoiEnd).forEach { region ->
            digest.updateLong((region.start - layout.eoiEnd).toLong())
            digest.updateLong((region.end - region.start).toLong())
            when (region) {
                is Region.Jpeg -> imageEssence(bytes, region.layout, digest)
                is Region.Movie -> IsoBmffMetadata.essence(window(bytes, region), digest)
                is Region.Raw -> digest.update(bytes, region.start, region.end - region.start)
            }
        }
    }

    private fun imageEssence(bytes: ByteArray, layout: Layout, digest: MessageDigest) {
        layout.segments.forEach { segment ->
            if (segment.marker != 0xFE && kind(bytes, segment) == Kind.OTHER && classify(bytes, segment) == MetadataClass.Keep) {
                digest.update(bytes, segment.start, segment.end - segment.start)
            }
        }
        digest.update(bytes, layout.bodyStart, layout.eoiEnd - layout.bodyStart)
    }
}
