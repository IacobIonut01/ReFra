package com.dot.gallery.core.metadata

import com.dot.gallery.core.metadata.MetadataCategory.DESCRIPTION_AUTHORSHIP
import com.dot.gallery.core.metadata.MetadataCategory.EDIT_HISTORY
import com.dot.gallery.core.metadata.MetadataCategory.IDENTITY_DEVICE
import com.dot.gallery.core.metadata.MetadataCategory.LOCATION
import com.dot.gallery.core.metadata.MetadataCategory.TIMESTAMPS
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * ISO base media files (MP4, QuickTime, 3GP, HEIF, AVIF) edited strictly in place: removed atoms
 * are renamed to `free` and zeroed, metadata values and header timestamps are zeroed, and Exif or
 * XMP items are rewritten at their original length. No byte moves, so chunk offsets (stco/co64),
 * item locations and any container that embeds this file stay valid.
 */
internal object IsoBmffMetadata {
    private val XMP_UUID = "BE7ACFCB97A942E89C71999491E3AFAC".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private const val MAX_DEPTH = 16
    private const val MAX_BOXES = 200_000
    private const val MAX_ITEM = 16 * 1024 * 1024
    private const val MAX_TABLE = 4 * 1024 * 1024
    private const val SECONDS_1904_TO_1970 = 2_082_844_800L
    private const val HASH_CHUNK = 1024 * 1024

    private val telemetryEntries = setOf("gpmd", "camm")
    private val descriptionAtoms = setOf(
        "titl", "dscp", "cprt", "perf", "auth", "gnre", "albm", "kywd", "rtng", "clsf", "desc", "ldes"
    )

    private class Box(val type: String, val start: Long, val fieldsEnd: Long, val dataStart: Long, val end: Long)

    private enum class Kind { ZERO, RENAME, XMP, TIFF, SKIP }

    /**
     * A metadata location. [start] until [end] is what removal touches; [hashStart] until [end] is
     * left out of the essence digest. [present] is false for values that are already blank.
     */
    private class Item(
        val kind: Kind,
        val metadataClass: MetadataClass,
        val start: Long,
        val end: Long,
        val hashStart: Long = start,
        val present: Boolean = true,
        val preview: String? = null,
        val signature: ByteArray? = null,
    )

    private class Scan(private val data: RandomBytes) {
        val items = mutableListOf<Item>()
        val blocked = linkedSetOf<MetadataCategory>()
        private var boxes = 0

        fun run(): Scan {
            top(0, data.size, 0)
            return this
        }

        private fun children(from: Long, to: Long): List<Box> {
            val list = mutableListOf<Box>()
            var at = from
            while (at + 8 <= to) {
                if (++boxes > MAX_BOXES) malformed("Too many boxes")
                val small = data.readU32(at)
                val type = data.readType(at + 4)
                var header = 8L
                val size = when (small) {
                    1L -> {
                        header = 16
                        if (at + 16 > to) malformed("Box $type header overruns its parent")
                        data.readU64(at + 8)
                    }
                    0L -> to - at
                    else -> small
                }
                if (size < header || size > to - at) malformed("Box $type overruns its parent")
                val fieldsEnd = at + header
                if (type == "uuid") header += 16
                if (header > size) malformed("Box $type is truncated")
                list += Box(type, at, fieldsEnd, at + header, at + size)
                at += size
            }
            return list
        }

        private fun isXmpUuid(box: Box): Boolean =
            box.type == "uuid" && data.read(box.fieldsEnd, 16).contentEquals(XMP_UUID)

        private fun top(from: Long, to: Long, depth: Int) {
            if (depth > MAX_DEPTH) malformed("Boxes nest too deeply")
            children(from, to).forEach { box ->
                when {
                    box.type == "moov" -> movie(box, depth + 1)
                    box.type == "meta" -> meta(box, depth + 1)
                    box.type == "mpvd" -> top(box.dataStart, box.end, depth + 1)
                    isXmpUuid(box) -> items += Item(Kind.XMP, MetadataClass.Keep, box.dataStart, box.end)
                }
            }
        }

        private fun movie(box: Box, depth: Int) {
            if (depth > MAX_DEPTH) malformed("Boxes nest too deeply")
            children(box.dataStart, box.end).forEach { child ->
                when {
                    child.type in setOf("trak", "mdia", "minf", "stbl") -> movie(child, depth + 1)
                    child.type in setOf("mvhd", "tkhd", "mdhd") -> times(child)
                    child.type == "stsd" -> sampleEntries(child)
                    child.type == "udta" -> userData(child, depth + 1)
                    child.type == "meta" -> meta(child, depth + 1)
                    isXmpUuid(child) -> items += Item(Kind.XMP, MetadataClass.Keep, child.dataStart, child.end)
                }
            }
        }

        private fun times(box: Box) {
            val version = data.read(box.dataStart, 1).u8(0)
            val width = if (version == 1) 8 else 4
            listOf(box.dataStart + 4, box.dataStart + 4 + width).forEachIndexed { index, position ->
                if (position + width > box.end) malformed("${box.type} is truncated")
                val value = if (width == 8) data.readU64(position) else data.readU32(position)
                val preview = if (box.type == "mvhd" && index == 0 && value != 0L) formatTime(value) else null
                items += Item(Kind.ZERO, MetadataClass.of(TIMESTAMPS), position, position + width, present = value != 0L, preview = preview)
            }
        }

        private fun sampleEntries(box: Box) {
            if (box.dataStart + 8 > box.end) malformed("stsd is truncated")
            children(box.dataStart + 8, box.end).forEach { entry ->
                // A GPS or camera-motion telemetry track is part of the media and cannot be removed in place.
                if (entry.type in telemetryEntries) blocked += LOCATION
            }
        }

        private fun userData(box: Box, depth: Int) {
            children(box.dataStart, box.end).forEach { child ->
                when {
                    child.type == "meta" -> meta(child, depth + 1)
                    child.type == "XMP_" -> items += Item(Kind.XMP, MetadataClass.Keep, child.dataStart, child.end)
                    isXmpUuid(child) -> items += Item(Kind.XMP, MetadataClass.Keep, child.dataStart, child.end)
                    child.type == "free" || child.type == "skip" ->
                        items += Item(Kind.SKIP, MetadataClass.Keep, child.start, child.end)
                    else -> {
                        val metadataClass = classifyAtom(child.type)
                        val text = atomText(child)
                        items += Item(
                            Kind.RENAME, metadataClass, child.fieldsEnd, child.end, hashStart = child.start,
                            present = !data.read(child.fieldsEnd, minOf(child.end - child.fieldsEnd, 4096L).toInt()).isBlankBytes(),
                            preview = text,
                            signature = text?.takeIf { metadataClass == MetadataClass.of(LOCATION) }?.toByteArray()
                        )
                    }
                }
            }
        }

        /** QuickTime international text atoms: a 16-bit length and language code before the text. */
        private fun atomText(box: Box): String? {
            if (!box.type.startsWith("©") || box.dataStart + 4 > box.end) return null
            val header = data.read(box.dataStart, 4)
            val length = minOf(header.u16(0).toLong(), box.end - box.dataStart - 4, 256L).toInt()
            return data.read(box.dataStart + 4, length).cleanText().takeIf { it.isNotEmpty() }
        }

        private fun meta(box: Box, depth: Int) {
            if (depth > MAX_DEPTH) malformed("Boxes nest too deeply")
            if (box.dataStart + 8 > box.end) return
            // QuickTime meta has no version field and starts directly with its handler box.
            val quickTime = data.readType(box.dataStart + 4) == "hdlr"
            val kids = children(if (quickTime) box.dataStart else box.dataStart + 4, box.end)
            val keys = kids.firstOrNull { it.type == "keys" }?.let(::keys)
            var iinf: Box? = null
            var iloc: Box? = null
            var idat: Box? = null
            kids.forEach { kid ->
                when (kid.type) {
                    "ilst" -> itemList(kid, keys)
                    "iinf" -> iinf = kid
                    "iloc" -> iloc = kid
                    "idat" -> idat = kid
                    "xml " -> items += Item(Kind.XMP, MetadataClass.Keep, kid.dataStart + 4, kid.end)
                    "free", "skip" -> items += Item(Kind.SKIP, MetadataClass.Keep, kid.start, kid.end)
                }
            }
            val info = iinf
            val locations = iloc
            if (info != null && locations != null) heifItems(info, locations, idat)
        }

        private fun table(box: Box): ByteArray {
            val length = box.end - box.dataStart
            if (length > MAX_TABLE) throw UnsupportedMetadataException("${box.type} table is too large")
            return data.read(box.dataStart, length.toInt())
        }

        private fun keys(box: Box): List<String> {
            val bytes = table(box)
            val count = bytes.u32(4)
            val names = mutableListOf<String>()
            var at = 8
            for (i in 0 until count) {
                val size = bytes.u32(at)
                if (size < 8 || size > bytes.size - at) malformed("Metadata key overruns its table")
                names += bytes.ascii(at + 8, size.toInt() - 8)
                at += size.toInt()
            }
            return names
        }

        private fun itemList(box: Box, keys: List<String>?) {
            children(box.dataStart, box.end).forEach { entry ->
                val metadataClass = if (keys != null) {
                    keys.getOrNull(data.readU32(entry.start + 4).toInt() - 1)?.let(::classifyKey) ?: MetadataClass.Other
                } else {
                    classifyAtom(entry.type)
                }
                children(entry.dataStart, entry.end).filter { it.type == "data" && it.dataStart + 8 <= it.end }.forEach { value ->
                    val valueStart = value.dataStart + 8
                    val sample = data.read(valueStart, minOf(value.end - valueStart, 256L).toInt())
                    val textual = data.readU32(value.dataStart) and 0xFFFFFF == 1L
                    val text = if (textual) sample.cleanText().takeIf { it.isNotEmpty() } else null
                    items += Item(
                        Kind.ZERO, metadataClass, valueStart, value.end,
                        present = !data.read(valueStart, minOf(value.end - valueStart, 4096L).toInt()).isBlankBytes(),
                        preview = text,
                        signature = text?.takeIf { metadataClass == MetadataClass.of(LOCATION) }?.toByteArray()
                    )
                }
            }
        }

        private class ItemInfo(val type: String, val contentType: String?)

        private fun heifItems(iinf: Box, iloc: Box, idat: Box?) {
            val info = mutableMapOf<Long, ItemInfo>()
            val version = data.read(iinf.dataStart, 1).u8(0)
            val entriesStart = iinf.dataStart + 4 + if (version == 0) 2 else 4
            children(entriesStart, iinf.end).filter { it.type == "infe" }.forEach { entry ->
                val bytes = table(entry)
                val entryVersion = bytes.u8(0)
                if (entryVersion < 2) return@forEach
                val idWidth = if (entryVersion == 2) 2 else 4
                val id = if (idWidth == 2) bytes.u16(4).toLong() else bytes.u32(4)
                val typeAt = 4 + idWidth + 2
                val type = bytes.ascii(typeAt, 4)
                val nameEnd = (typeAt + 4 until bytes.size).firstOrNull { bytes[it] == 0.toByte() } ?: bytes.size
                val contentType = if (type == "mime" && nameEnd + 1 < bytes.size) {
                    val end = (nameEnd + 1 until bytes.size).firstOrNull { bytes[it] == 0.toByte() } ?: bytes.size
                    bytes.ascii(nameEnd + 1, end - nameEnd - 1)
                } else null
                info[id] = ItemInfo(type, contentType)
            }
            val wanted = info.filterValues { it.type == "Exif" || (it.type == "mime" && it.contentType?.contains("rdf+xml") == true) }
            if (wanted.isEmpty()) return

            val bytes = table(iloc)
            val ilocVersion = bytes.u8(0)
            val offsetSize = bytes.u8(4) shr 4
            val lengthSize = bytes.u8(4) and 0x0F
            val baseSize = bytes.u8(5) shr 4
            val indexSize = if (ilocVersion >= 1) bytes.u8(5) and 0x0F else 0
            var at = 6
            fun read(width: Int): Long = when (width) {
                0 -> 0L
                4 -> bytes.u32(at).also { at += 4 }
                8 -> bytes.u64(at).also { at += 8 }
                else -> malformed("Unsupported iloc field width $width")
            }
            val count = if (ilocVersion < 2) bytes.u16(at).toLong().also { at += 2 } else read(4)
            for (i in 0 until count) {
                val id = if (ilocVersion < 2) bytes.u16(at).toLong().also { at += 2 } else read(4)
                val method = if (ilocVersion >= 1) (bytes.u16(at) and 0x0F).also { at += 2 } else 0
                at += 2 // data reference index
                val base = read(baseSize)
                val extentCount = bytes.u16(at).also { at += 2 }
                val extents = (0 until extentCount).map {
                    if (ilocVersion >= 1 && indexSize > 0) read(indexSize)
                    read(offsetSize) to read(lengthSize)
                }
                val item = wanted[id] ?: continue
                if (extents.size != 1) throw UnsupportedMetadataException("Fragmented HEIF metadata item")
                val (offset, length) = extents.single()
                val origin = when (method) {
                    0 -> 0L
                    1 -> idat?.dataStart ?: malformed("HEIF item refers to a missing idat")
                    else -> throw UnsupportedMetadataException("HEIF item construction method $method")
                }
                val start = origin + base + offset
                if (length <= 0 || start < 0 || start + length > data.size) malformed("HEIF item extent lies outside the file")
                if (length > MAX_ITEM) throw UnsupportedMetadataException("HEIF metadata item is too large")
                if (item.type == "Exif") {
                    val tiffOffset = data.readU32(start)
                    val tiffStart = start + 4 + tiffOffset
                    if (tiffStart >= start + length) malformed("HEIF Exif header offset is invalid")
                    items += Item(Kind.TIFF, MetadataClass.Keep, tiffStart, start + length, hashStart = start)
                } else {
                    items += Item(Kind.XMP, MetadataClass.Keep, start, start + length)
                }
            }
        }
    }

    private fun formatTime(secondsSince1904: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date((secondsSince1904 - SECONDS_1904_TO_1970) * 1000))

    private fun classifyAtom(type: String): MetadataClass = when (type) {
        "©xyz", "loci" -> MetadataClass.of(LOCATION)
        "©day", "yrrc" -> MetadataClass.of(TIMESTAMPS)
        "©mak", "©mod" -> MetadataClass.of(IDENTITY_DEVICE)
        "©swr", "©too", "©enc", "swr " -> MetadataClass.of(EDIT_HISTORY)
        in descriptionAtoms -> MetadataClass.of(DESCRIPTION_AUTHORSHIP)
        else -> if (type.startsWith("©")) MetadataClass.of(DESCRIPTION_AUTHORSHIP) else MetadataClass.Other
    }

    private val keptKeys = listOf("content.identifier", "live-photo", "still-image-time", "capture.fps", "video-orientation")
    private val identityKeys = listOf(".make", ".model", "camera.identifier", "camera.lens", "android.manufacturer", "android.version")
    private val descriptionKeys = listOf(
        "title", "description", "comment", "author", "artist", "keywords", "copyright", "displayname", "information",
        "publisher", "genre", "album", "rating"
    )

    private fun classifyKey(name: String): MetadataClass {
        val key = name.lowercase(Locale.US)
        return when {
            keptKeys.any { key.contains(it) } -> MetadataClass.Keep
            key.contains("location") -> MetadataClass.of(LOCATION)
            key.contains("creationdate") -> MetadataClass.of(TIMESTAMPS)
            identityKeys.any { key.contains(it) } -> MetadataClass.of(IDENTITY_DEVICE)
            key.endsWith(".software") -> MetadataClass.of(EDIT_HISTORY)
            descriptionKeys.any { key.contains(it) } -> MetadataClass.of(DESCRIPTION_AUTHORSHIP)
            else -> MetadataClass.Other
        }
    }

    private fun readItem(data: RandomBytes, item: Item): ByteArray {
        val length = item.end - item.start
        if (length > MAX_ITEM) throw UnsupportedMetadataException("Metadata item is too large")
        return data.read(item.start, length.toInt())
    }

    fun inspect(data: RandomBytes, inventory: MetadataInventory) {
        val scan = Scan(data).run()
        scan.blocked.forEach { inventory.record(MetadataClass.of(it)) }
        inventory.blocked += scan.blocked
        scan.items.forEach { item ->
            when (item.kind) {
                Kind.XMP -> XmpMetadata.inspect(readItem(data, item), inventory)
                Kind.TIFF -> TiffMetadata.inspect(readItem(data, item), inventory)
                Kind.SKIP -> Unit
                Kind.ZERO, Kind.RENAME -> if (item.present) {
                    inventory.record(item.metadataClass, item.preview)
                    item.signature?.let(inventory::locationSignature)
                }
            }
        }
    }

    /** Scrubs [data] in place; returns whether anything was changed. */
    fun scrub(data: RandomBytes, selection: MetadataSelection): Boolean {
        if (selection.isEmpty) return false
        val scan = Scan(data).run()
        var changed = false
        scan.items.forEach { item ->
            when (item.kind) {
                Kind.XMP -> {
                    val original = readItem(data, item)
                    val packet = XmpMetadata.scrub(original, selection, fixedLength = true)
                        ?: malformed("Fixed-length XMP scrub returned nothing")
                    if (packet !== original) {
                        data.write(item.start, packet)
                        changed = true
                    }
                }
                Kind.TIFF -> {
                    val result = TiffMetadata.scrub(readItem(data, item), selection)
                    if (result.changed) {
                        data.write(item.start, result.bytes)
                        changed = true
                    }
                }
                Kind.ZERO -> if (item.present && selection.removes(item.metadataClass)) {
                    zero(data, item.start, item.end)
                    changed = true
                }
                Kind.RENAME -> if (selection.removes(item.metadataClass)) {
                    data.write(item.hashStart + 4, "free".toByteArray(Charsets.ISO_8859_1))
                    zero(data, item.start, item.end)
                    changed = true
                }
                Kind.SKIP -> Unit
            }
        }
        return changed
    }

    private fun zero(data: RandomBytes, from: Long, to: Long) {
        var at = from
        while (at < to) {
            val length = minOf(to - at, HASH_CHUNK.toLong()).toInt()
            data.write(at, ByteArray(length))
            at += length
        }
    }

    /** Hashes every byte outside the metadata locations, which removal must leave untouched. */
    fun essence(data: RandomBytes, digest: MessageDigest) {
        val scan = Scan(data).run()
        digest.updateLong(data.size)
        var at = 0L
        mergeRanges(scan.items.map { it.hashStart until it.end }).forEach { range ->
            hash(data, at, range.first, digest)
            at = range.last + 1
        }
        hash(data, at, data.size, digest)
    }

    private fun hash(data: RandomBytes, from: Long, to: Long, digest: MessageDigest) {
        var at = from
        while (at < to) {
            val length = minOf(to - at, HASH_CHUNK.toLong()).toInt()
            digest.update(data.read(at, length))
            at += length
        }
    }
}
