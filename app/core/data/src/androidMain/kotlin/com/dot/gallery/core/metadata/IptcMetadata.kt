package com.dot.gallery.core.metadata

import com.dot.gallery.core.logging.printWarn
import com.dot.gallery.core.metadata.MetadataCategory.DESCRIPTION_AUTHORSHIP
import com.dot.gallery.core.metadata.MetadataCategory.EDIT_HISTORY
import com.dot.gallery.core.metadata.MetadataCategory.LOCATION
import com.dot.gallery.core.metadata.MetadataCategory.THUMBNAIL_PREVIEW
import com.dot.gallery.core.metadata.MetadataCategory.TIMESTAMPS

/**
 * Photoshop image resource blocks (JPEG APP13) and the IPTC-IIM record they carry. Embedded
 * EXIF and XMP resources are inspected for reporting but only ever removed whole.
 */
internal object IptcMetadata {
    private const val IPTC = 0x0404
    private const val IPTC_DIGEST = 0x0425
    private const val MIN_RESOURCE_HEADER = 12

    private class Resource(val id: Int, val start: Int, val end: Int, val dataStart: Int, val dataLength: Int)

    private val locationDatasets = setOf(26, 27, 90, 92, 95, 100, 101)
    private val timeDatasets = setOf(30, 35, 37, 38, 55, 60, 62, 63)

    private fun classifyDataset(record: Int, dataset: Int): MetadataClass = when (record) {
        1 -> when (dataset) {
            70, 80 -> MetadataClass.of(TIMESTAMPS)
            0, 90 -> MetadataClass.Keep
            else -> MetadataClass.Other
        }
        2 -> when (dataset) {
            0 -> MetadataClass.Keep
            in locationDatasets -> MetadataClass.of(LOCATION)
            in timeDatasets -> MetadataClass.of(TIMESTAMPS)
            65, 70 -> MetadataClass.of(EDIT_HISTORY)
            else -> MetadataClass.of(DESCRIPTION_AUTHORSHIP)
        }
        else -> MetadataClass.Other
    }

    private fun classifyResource(id: Int): MetadataClass = when (id) {
        0x0409, 0x040C -> MetadataClass.of(THUMBNAIL_PREVIEW)
        0x0422, 0x0423, 0x0424 -> MetadataClass.Opaque
        0x03ED, 0x040F -> MetadataClass.Keep
        else -> MetadataClass.Other
    }

    private fun resources(irb: ByteArray): List<Resource> {
        val resources = mutableListOf<Resource>()
        var offset = 0
        while (offset + MIN_RESOURCE_HEADER <= irb.size) {
            if (!irb.startsWithAscii("8BIM", offset)) {
                if (irb.isBlankBytes(offset)) break
                malformed("Image resource signature missing at $offset")
            }
            val id = irb.u16(offset + 4)
            val nameLength = irb.u8(offset + 6)
            var cursor = offset + 7 + nameLength
            if ((1 + nameLength) % 2 == 1) cursor++
            val length = irb.u32(cursor)
            cursor += 4
            if (length > irb.size - cursor) malformed("Image resource overruns its block")
            val end = cursor + length.toInt() + (length.toInt() and 1)
            resources += Resource(id, offset, minOf(end, irb.size), cursor, length.toInt())
            offset = end
        }
        return resources
    }

    private class Dataset(val record: Int, val number: Int, val start: Int, val end: Int, val dataStart: Int)

    private fun datasets(iim: ByteArray): List<Dataset> {
        val datasets = mutableListOf<Dataset>()
        var offset = 0
        while (offset < iim.size) {
            if (iim[offset] != 0x1C.toByte()) {
                if (iim.isBlankBytes(offset)) break
                malformed("IPTC tag marker missing at $offset")
            }
            val record = iim.u8(offset + 1)
            val number = iim.u8(offset + 2)
            var length = iim.u16(offset + 3).toLong()
            var dataStart = offset + 5
            if (length and 0x8000 != 0L) {
                val lengthBytes = (length and 0x7FFF).toInt()
                if (lengthBytes !in 1..4) malformed("IPTC extended length is invalid")
                length = 0
                repeat(lengthBytes) { length = (length shl 8) or iim.u8(dataStart + it).toLong() }
                dataStart += lengthBytes
            }
            if (length > iim.size - dataStart) malformed("IPTC dataset overruns its record")
            val end = dataStart + length.toInt()
            datasets += Dataset(record, number, offset, end, dataStart)
            offset = end
        }
        return datasets
    }

    fun inspectIrb(irb: ByteArray, inventory: MetadataInventory) {
        resources(irb).forEach { resource ->
            val data = irb.copyOfRange(resource.dataStart, resource.dataStart + resource.dataLength)
            when (resource.id) {
                IPTC -> inspectIim(data, inventory)
                IPTC_DIGEST -> Unit
                else -> {
                    val metadataClass = classifyResource(resource.id)
                    inventory.record(metadataClass)
                    // Nested blobs are reported by what they carry; removal still drops them whole.
                    runCatching {
                        when (resource.id) {
                            0x0422 -> TiffMetadata.inspect(data, inventory)
                            0x0424 -> XmpMetadata.inspect(data, inventory)
                        }
                    }.onFailure {
                        printWarn("media-ops.metadata", "Nested image resource could not be inspected: ${it.message}")
                    }
                }
            }
        }
    }

    fun inspectIim(iim: ByteArray, inventory: MetadataInventory) {
        datasets(iim).forEach { dataset ->
            val metadataClass = classifyDataset(dataset.record, dataset.number)
            inventory.record(metadataClass, iim.cleanText(dataset.dataStart, dataset.end - dataset.dataStart))
        }
    }

    /** Returns the scrubbed resource block, the input when unchanged, or null when nothing is left. */
    fun scrubIrb(irb: ByteArray, selection: MetadataSelection): ByteArray? {
        val resources = resources(irb)
        var changed = false
        var iptcChanged = false
        val out = ByteSink(irb.size)
        val kept = mutableListOf<Resource>()
        resources.forEach { resource ->
            if (resource.id == IPTC) {
                val data = irb.copyOfRange(resource.dataStart, resource.dataStart + resource.dataLength)
                val scrubbed = scrubIim(data, selection)
                if (scrubbed !== data) {
                    changed = true
                    iptcChanged = true
                    if (scrubbed != null) {
                        writeResource(out, irb, resource, scrubbed)
                        kept += resource
                    }
                    return@forEach
                }
            } else if (resource.id != IPTC_DIGEST && selection.removes(classifyResource(resource.id))) {
                changed = true
                return@forEach
            }
            kept += resource
            if (resource.id != IPTC_DIGEST) out.write(irb, resource.start, resource.end - resource.start)
        }
        // The digest guards the IPTC record; a stale digest makes readers ignore the record.
        if (iptcChanged) {
            changed = true
        } else {
            resources.filter { it.id == IPTC_DIGEST }.forEach { out.write(irb, it.start, it.end - it.start) }
        }
        if (!changed) return irb
        if (kept.none { it.id != IPTC_DIGEST }) return null
        return out.toByteArray()
    }

    private fun writeResource(out: ByteSink, irb: ByteArray, resource: Resource, data: ByteArray) {
        val headerEnd = resource.dataStart - 4
        out.write(irb, resource.start, headerEnd - resource.start)
        out.writeU32(data.size.toLong())
        out.write(data)
        out.align2()
    }

    /** Returns the scrubbed record, the input when unchanged, or null when only envelope data is left. */
    fun scrubIim(iim: ByteArray, selection: MetadataSelection): ByteArray? {
        val datasets = datasets(iim)
        val kept = datasets.filterNot { selection.removes(classifyDataset(it.record, it.number)) }
        if (kept.size == datasets.size) return iim
        if (kept.all { classifyDataset(it.record, it.number) == MetadataClass.Keep }) return null
        val out = ByteSink(iim.size)
        kept.forEach { out.write(iim, it.start, it.end - it.start) }
        return out.toByteArray()
    }
}
