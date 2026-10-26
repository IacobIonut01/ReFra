package com.dot.gallery.core.metadata

import com.dot.gallery.core.metadata.MetadataCategory.CAPTURE_SETTINGS
import com.dot.gallery.core.metadata.MetadataCategory.DESCRIPTION_AUTHORSHIP
import com.dot.gallery.core.metadata.MetadataCategory.EDIT_HISTORY
import com.dot.gallery.core.metadata.MetadataCategory.IDENTITY_DEVICE
import com.dot.gallery.core.metadata.MetadataCategory.LOCATION
import com.dot.gallery.core.metadata.MetadataCategory.THUMBNAIL_PREVIEW
import com.dot.gallery.core.metadata.MetadataCategory.TIMESTAMPS
import java.util.Locale

/**
 * EXIF/TIFF metadata blocks (the TIFF structure found in JPEG APP1, PNG eXIf, WebP EXIF and
 * HEIF/JXL Exif items). Removal is done in place without moving anything: removed entries are
 * compacted out of their IFD and their out-of-line values are zeroed, so every offset that other
 * entries (or a MakerNote) hold stays valid.
 */
internal object TiffMetadata {
    private const val IFD0 = 0
    private const val EXIF = 1
    private const val GPS = 2
    private const val INTEROP = 3
    private const val IFD1 = 4

    private const val TAG_EXIF_IFD = 0x8769
    private const val TAG_GPS_IFD = 0x8825
    private const val TAG_INTEROP_IFD = 0xA005
    private const val MAX_ENTRIES = 1024

    /** [usedLength] is the shortest prefix of [bytes] holding everything that was kept. */
    class Result(val bytes: ByteArray, val usedLength: Int, val changed: Boolean)

    fun inspect(tiff: ByteArray, inventory: MetadataInventory) {
        Walker(tiff, null, inventory).run()
    }

    fun scrub(tiff: ByteArray, selection: MetadataSelection): Result {
        val walker = Walker(tiff.copyOf(), selection, null)
        walker.run()
        return walker.result()
    }

    private val timestamps = setOf(0x0132, 0x9003, 0x9004, 0x9010, 0x9011, 0x9012, 0x9290, 0x9291, 0x9292)
    private val identity = setOf(
        0x010F, 0x0110, 0x927C, 0xA430, 0xA431, 0xA432, 0xA433, 0xA434, 0xA435, 0xA420, 0xC4A5,
        0x013C, 0xC614, 0xC621, 0xC622, 0xC623, 0xC624, 0xC625, 0xC62F
    )
    private val description = setOf(
        0x010D, 0x010E, 0x011D, 0x013B, 0x8298, 0x9286, 0x9C9B, 0x9C9C, 0x9C9D, 0x9C9E, 0x9C9F,
        0x4746, 0x4749, 0xA436, 0xA437
    )
    private val edit = setOf(0x0131, 0xA438, 0xA439, 0xA43A, 0xA43B, 0xC65A, 0xC65B)
    private val capture = setOf(
        0x829A, 0x829D, 0x8822, 0x8824, 0x8827, 0x8828, 0x8830, 0x8831, 0x8832, 0x8833, 0x8834, 0x8835,
        0x9201, 0x9202, 0x9203, 0x9204, 0x9205, 0x9206, 0x9207, 0x9208, 0x9209, 0x920A, 0x920B, 0x920C,
        0x920D, 0x920E, 0x920F, 0x9210, 0x9211, 0x9212, 0x9213, 0x9214, 0x9215, 0x9216, 0x9217,
        0x9400, 0x9401, 0x9402, 0x9403, 0x9404, 0x9405, 0x9406, 0x9407,
        0xA20B, 0xA20C, 0xA20E, 0xA20F, 0xA210, 0xA214, 0xA215, 0xA217, 0xA300, 0xA301, 0xA302,
        0xA401, 0xA402, 0xA403, 0xA404, 0xA405, 0xA406, 0xA407, 0xA408, 0xA409, 0xA40A, 0xA40B, 0xA40C,
        0xA460, 0xA461, 0xA462
    )
    private val keep = setOf(
        0x00FE, 0x00FF, 0x0100, 0x0101, 0x0102, 0x0103, 0x0106, 0x0107, 0x0108, 0x0109, 0x010A,
        0x0111, 0x0112, 0x0115, 0x0116, 0x0117, 0x0118, 0x0119, 0x011A, 0x011B, 0x011C, 0x0122,
        0x0123, 0x0128, 0x0129, 0x012D, 0x013D, 0x013E, 0x013F, 0x0140, 0x0141, 0x0142, 0x0143,
        0x0144, 0x0145, 0x014A, 0x014C, 0x0150, 0x0151, 0x0152, 0x0153, 0x0154, 0x0155, 0x0156,
        0x0200, 0x0201, 0x0202, 0x0211, 0x0212, 0x0213, 0x0214, 0x8773, 0x9000, 0x9101, 0x9102,
        0xA000, 0xA001, 0xA002, 0xA003, 0xA500, 0x9C40, 0x9C41, 0x9C42, 0x9C43, 0x9C44, 0x9C45,
        TAG_EXIF_IFD, TAG_GPS_IFD, TAG_INTEROP_IFD
    )

    /** XMP, IPTC and Photoshop blocks embedded in a TIFF tag can only be dropped whole here. */
    private val nestedBlocks = setOf(0x02BC, 0x83BB, 0x8649)

    private fun classify(kind: Int, tag: Int): MetadataClass = when (kind) {
        GPS -> when (tag) {
            0x0000 -> MetadataClass.Keep
            0x0007, 0x001D -> MetadataClass.of(LOCATION, TIMESTAMPS)
            else -> MetadataClass.of(LOCATION)
        }
        INTEROP -> MetadataClass.Keep
        else -> when (tag) {
            in keep -> MetadataClass.Keep
            in nestedBlocks -> MetadataClass.Opaque
            in timestamps -> MetadataClass.of(TIMESTAMPS)
            in identity -> MetadataClass.of(IDENTITY_DEVICE)
            in description -> MetadataClass.of(DESCRIPTION_AUTHORSHIP)
            in edit -> MetadataClass.of(EDIT_HISTORY)
            in capture -> MetadataClass.of(CAPTURE_SETTINGS)
            else -> MetadataClass.Other
        }
    }

    private fun typeSize(type: Int): Int = when (type) {
        1, 2, 6, 7 -> 1
        3, 8 -> 2
        4, 9, 11, 13 -> 4
        5, 10, 12 -> 8
        else -> 0
    }

    private class Entry(val position: Int, val tag: Int, val type: Int, val count: Long, val dataStart: Int, val dataLength: Int) {
        val outOfLine: Boolean get() = dataLength > 4
    }

    private class IfdPlan(val offset: Int, val kind: Int, val count: Int) {
        val kept = mutableListOf<Entry>()
        val keptData = mutableListOf<IntRange>()
        val removedData = mutableListOf<IntRange>()
        var next: Long = 0
        var removedAny = false
        var dropped = false
        val regionEnd: Int get() = offset + 2 + count * 12 + 4
    }

    private class Walker(
        val bytes: ByteArray,
        val selection: MetadataSelection?,
        val inventory: MetadataInventory?
    ) {
        private var little = false
        private val plans = mutableListOf<IfdPlan>()
        private val visited = hashSetOf<Int>()
        private var changed = false
        private val gps = HashMap<Int, ByteArray>()
        private var make: String? = null
        private var model: String? = null

        fun run() {
            if (bytes.size < 8) malformed("TIFF header truncated")
            little = when {
                bytes.startsWithAscii("II") -> true
                bytes.startsWithAscii("MM") -> false
                else -> malformed("Bad TIFF byte order")
            }
            if (bytes.u16(2, little) != 42) malformed("Unsupported TIFF variant")
            val ifd0 = walk(bytes.u32(4, little), IFD0) ?: malformed("Missing IFD0")
            if (ifd0.next != 0L) {
                val ifd1 = walk(ifd0.next, IFD1)
                if (ifd1 != null) {
                    inventory?.record(MetadataClass.of(THUMBNAIL_PREVIEW))
                    if (ifd1.next != 0L) inventory?.record(MetadataClass.Other)
                    if (selection?.removes(THUMBNAIL_PREVIEW) == true) {
                        ifd1.dropped = true
                        ifd0.next = 0
                        ifd0.removedAny = true
                    }
                }
            }
            inventory?.let(::recordPreviews)
            if (selection != null) apply()
        }

        private fun walk(offset: Long, kind: Int): IfdPlan? {
            if (offset <= 0 || offset + 2 > bytes.size) {
                if (kind == IFD1) return null
                malformed("IFD offset out of range")
            }
            val start = offset.toInt()
            if (!visited.add(start)) malformed("IFD loop")
            val count = bytes.u16(start, little)
            if (count > MAX_ENTRIES || start + 2 + count * 12 + 4 > bytes.size) {
                if (kind == IFD1) return null
                malformed("IFD entries out of range")
            }
            val plan = IfdPlan(start, kind, count)
            plans += plan
            plan.next = bytes.u32(start + 2 + count * 12, little)
            for (i in 0 until count) {
                val entry = readEntry(start + 2 + i * 12)
                handle(plan, entry)
            }
            if (kind == GPS && plan.removedAny && plan.kept.all { it.tag == 0x0000 }) {
                plan.dropped = true
            }
            return plan
        }

        private fun readEntry(position: Int): Entry {
            val tag = bytes.u16(position, little)
            val type = bytes.u16(position + 2, little)
            val count = bytes.u32(position + 4, little)
            val length = typeSize(type).toLong() * count
            return when {
                length <= 4 -> Entry(position, tag, type, count, position + 8, length.toInt())
                else -> {
                    val dataOffset = bytes.u32(position + 8, little)
                    if (dataOffset + length > bytes.size) {
                        Entry(position, tag, type, count, -1, 0)
                    } else {
                        Entry(position, tag, type, count, dataOffset.toInt(), length.toInt())
                    }
                }
            }
        }

        private fun handle(plan: IfdPlan, entry: Entry) {
            val pointerKind = when {
                plan.kind == IFD0 || plan.kind == EXIF -> when (entry.tag) {
                    TAG_EXIF_IFD -> EXIF
                    TAG_GPS_IFD -> GPS
                    TAG_INTEROP_IFD -> INTEROP
                    else -> null
                }
                else -> null
            }
            if (pointerKind != null) {
                val child = walk(bytes.u32(entry.position + 8, little), pointerKind)
                if (child != null && child.dropped) {
                    plan.removedAny = true
                    changed = true
                } else {
                    plan.kept += entry
                }
                return
            }
            val metadataClass = classify(plan.kind, entry.tag)
            inventory?.let { observe(plan.kind, entry, metadataClass, it) }
            if (plan.kind == IFD1 && (entry.tag == 0x0201 || entry.tag == 0x0111)) {
                thumbnailData(entry, plan)?.let { plan.keptData += it }
            }
            if (selection != null && selection.removes(metadataClass)) {
                plan.removedAny = true
                changed = true
                if (entry.outOfLine) plan.removedData += entry.dataStart until entry.dataStart + entry.dataLength
            } else {
                plan.kept += entry
                if (entry.outOfLine) plan.keptData += entry.dataStart until entry.dataStart + entry.dataLength
            }
        }

        /** The thumbnail image bytes an IFD1 offset entry points at. */
        private fun thumbnailData(entry: Entry, plan: IfdPlan): List<IntRange>? {
            val lengthTag = if (entry.tag == 0x0201) 0x0202 else 0x0117
            val lengthEntry = (0 until plan.count).map { readEntry(plan.offset + 2 + it * 12) }
                .firstOrNull { it.tag == lengthTag } ?: return null
            if (entry.dataStart < 0 || lengthEntry.dataStart < 0) return null
            val n = minOf(entry.count, lengthEntry.count).toInt().coerceAtMost(MAX_ENTRIES)
            return (0 until n).mapNotNull { i ->
                val start = value(entry, i)
                val length = value(lengthEntry, i)
                if (start <= 0 || length <= 0 || start + length > bytes.size) null
                else start.toInt() until (start + length).toInt()
            }
        }

        private fun value(entry: Entry, index: Int): Long = when (entry.type) {
            3 -> bytes.u16(entry.dataStart + index * 2, little).toLong()
            else -> bytes.u32(entry.dataStart + index * 4, little)
        }

        private fun observe(kind: Int, entry: Entry, metadataClass: MetadataClass, inventory: MetadataInventory) {
            inventory.record(metadataClass)
            if (entry.dataStart < 0) return
            val raw = bytes.copyOfRange(entry.dataStart, entry.dataStart + entry.dataLength)
            if (kind == GPS) {
                gps[entry.tag] = raw
                if (entry.tag == 0x0002 || entry.tag == 0x0004) inventory.locationSignature(raw)
                return
            }
            when (entry.tag) {
                0x0112 -> if (kind == IFD0) inventory.functional += "tiff.orientation=${bytes.u16(entry.dataStart, little)}"
                0x010F -> make = raw.cleanText()
                0x0110 -> model = raw.cleanText()
                0x9003 -> inventory.previews[TIMESTAMPS] = raw.cleanText()
                0x0132 -> inventory.preview(TIMESTAMPS, raw.cleanText())
                0x0131 -> inventory.preview(EDIT_HISTORY, raw.cleanText())
                0x013B, 0x010E, 0x8298 -> inventory.preview(DESCRIPTION_AUTHORSHIP, raw.cleanText())
                0x8827 -> if (entry.type == 3) inventory.preview(CAPTURE_SETTINGS, "ISO ${bytes.u16(entry.dataStart, little)}")
            }
        }

        private fun recordPreviews(inventory: MetadataInventory) {
            val device = listOfNotNull(make, model).filter { it.isNotEmpty() }
                .let { parts -> if (parts.size == 2 && parts[1].startsWith(parts[0], ignoreCase = true)) listOf(parts[1]) else parts }
                .joinToString(" ")
            if (device.isNotEmpty()) inventory.preview(IDENTITY_DEVICE, device)
            val latitude = coordinate(0x0002, 0x0001, 'S')
            val longitude = coordinate(0x0004, 0x0003, 'W')
            if (latitude != null && longitude != null) {
                inventory.previews[LOCATION] = String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
            }
        }

        private fun coordinate(tag: Int, refTag: Int, negative: Char): Double? {
            val raw = gps[tag]?.takeIf { it.size == 24 } ?: return null
            fun rational(i: Int): Double {
                val denominator = raw.u32(i * 8 + 4, little)
                return if (denominator == 0L) 0.0 else raw.u32(i * 8, little).toDouble() / denominator
            }
            val value = rational(0) + rational(1) / 60 + rational(2) / 3600
            val ref = gps[refTag]?.firstOrNull()?.toInt()?.toChar()
            return if (ref == negative) -value else value
        }

        private fun apply() {
            if (!changed && plans.none { it.removedAny || it.dropped }) return
            changed = true
            val protected = BooleanArray(bytes.size)
            fun protect(range: IntRange) {
                for (i in range.first.coerceAtLeast(0)..range.last.coerceAtMost(bytes.size - 1)) protected[i] = true
            }
            protect(0 until 8)
            val live = plans.filter { !it.dropped }
            live.forEach { plan ->
                protect(plan.offset until plan.regionEnd)
                plan.keptData.forEach(::protect)
            }
            val removals = plans.flatMap { plan ->
                if (plan in live) plan.removedData
                else plan.removedData + plan.keptData + listOf(plan.offset until plan.regionEnd)
            }
            removals.forEach { range ->
                for (i in range.first.coerceAtLeast(0)..range.last.coerceAtMost(bytes.size - 1)) {
                    if (!protected[i]) bytes[i] = 0
                }
            }
            live.forEach { plan ->
                if (!plan.removedAny) return@forEach
                val entries = plan.kept.map { bytes.copyOfRange(it.position, it.position + 12) }
                bytes.putU16(plan.offset, entries.size, little)
                entries.forEachIndexed { i, entry -> entry.copyInto(bytes, plan.offset + 2 + i * 12) }
                val nextAt = plan.offset + 2 + entries.size * 12
                bytes.putU32(nextAt, plan.next, little)
                for (i in nextAt + 4 until plan.regionEnd) bytes[i] = 0
            }
            // Trimming is limited to a dropped thumbnail, which normally sits last; a kept MakerNote
            // may reference bytes outside its own range that no IFD entry accounts for.
            if (plans.any { it.kind == IFD1 && it.dropped }) {
                usedLength = (protected.indexOfLast { it } + 1).coerceAtLeast(8)
            }
        }

        private var usedLength = bytes.size

        fun result(): Result = Result(bytes, if (changed) usedLength else bytes.size, changed)
    }
}
